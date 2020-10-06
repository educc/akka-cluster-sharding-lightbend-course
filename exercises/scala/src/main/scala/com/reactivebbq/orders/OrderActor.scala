package com.reactivebbq.orders

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props, Stash, Status}
import akka.cluster.sharding.ShardRegion.{ExtractEntityId, ExtractShardId}
import akka.pattern.pipe

import scala.concurrent.Future

object OrderActor {
  sealed trait Command extends SerializableMessage

  case class OpenOrder(server: Server, table: Table) extends Command
  case class OrderOpened(order: Order) extends SerializableMessage
  case class AddItemToOrder(item: OrderItem) extends Command
  case class ItemAddedToOrder(order: Order) extends SerializableMessage
  case class GetOrder() extends Command

  case class OrderNotFoundException(orderId: OrderId) extends IllegalStateException(s"Order Not Found: $orderId")
  case class DuplicateOrderException(orderId: OrderId) extends IllegalStateException(s"Duplicate Order: $orderId")

  case class Envelope(orderId: OrderId, command: Command) extends SerializableMessage

  private case class OrderLoaded(order: Option[Order])

  def props(repository: OrderRepository): Props = Props(new OrderActor(repository))

  val entityIdExtractor: ExtractEntityId = {
    case Envelope(orderId, command) => (orderId.value.toString, command)
  }

  val shardIdExtractor: ExtractShardId = {
    case Envelope(orderId, _) => Math.abs(orderId.value.toString.hashCode % 30).toString
  }
}

class OrderActor(repository: OrderRepository) extends Actor with ActorLogging with Stash {
  import OrderActor._
  import context.dispatcher

  private var state: Option[Order] = None
  private val orderId: OrderId = OrderId(UUID.fromString(context.self.path.name))

  repository.find(orderId).map(OrderLoaded.apply).pipeTo(self)

  override def receive: Receive = loading

  private def running: Receive = {
    case OpenOrder(server, table) =>
      log.info(s"[$orderId] OpenOrder($server, $table)")

      state match {
        case Some(_) => duplicateOrder(orderId).pipeTo(sender())
        case None =>
          context.become(waiting)
          openOrder(orderId, server, table).pipeTo(self)(sender())
      }

    case AddItemToOrder(item) =>
      log.info(s"[$orderId] AddItemToOrder($item)")

      state match {
        case Some(order) =>
          context.become(waiting)
          addItem(order, item).pipeTo(self)(sender())
        case None => orderNotFound(orderId).pipeTo(sender())
      }

    case GetOrder() =>
      log.info(s"[$orderId] GetOrder")

      state match {
        case Some(order) => sender() ! order
        case None => orderNotFound(orderId).pipeTo(sender())
      }
  }

  private def waiting: Receive = {
    case evt @ OrderOpened(order) =>
      state = Some(order)
      unstashAll()
      sender() ! evt
      context.become(running)

    case evt @ ItemAddedToOrder(order) =>
      state = Some(order)
      unstashAll()
      sender() ! evt
      context.become(running)

    case failure @ Status.Failure(ex) =>
      log.error(ex, s"[$orderId] FAILURE: ${ex.getMessage}")
      sender() ! failure
      throw ex

    case _ =>
      stash()
  }

  private def loading: Receive = {
    case OrderLoaded(order) =>
      unstashAll()
      state = order
      context.become(running)

    case Status.Failure(ex) =>
      log.error(ex, s"$orderId FAILURE: ${ex.getMessage}")
      throw ex

    case _ => stash()
  }

  private def openOrder(orderId: OrderId, server: Server, table: Table): Future[OrderOpened] = {
    repository.update(Order(orderId, server, table, Seq.empty))
      .map(OrderOpened.apply)
  }

  private def duplicateOrder[T](orderId: OrderId): Future[T] = {
    Future.failed(DuplicateOrderException(orderId))
  }

  private def addItem(order: Order, orderItem: OrderItem): Future[ItemAddedToOrder] = {
    repository
      .update(order.withItem(orderItem))
      .map(ItemAddedToOrder.apply)
  }

  private def orderNotFound[T](orderId: OrderId): Future[T] = Future.failed(OrderNotFoundException(orderId))
}
