package com.reactivebbq.orders

import java.util.UUID

import akka.actor.{Actor, ActorLogging, Props, Stash, Status}
import akka.cluster.sharding.ShardRegion
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

  private case class OrderLoaded(order: Option[Order])

  case class OrderNotFoundException(orderId: OrderId) extends IllegalStateException(s"Order Not Found: $orderId")
  case class DuplicateOrderException(orderId: OrderId) extends IllegalStateException(s"Duplicate Order: $orderId")

  case class Envelope(orderId: OrderId, command: Command) extends SerializableMessage

  val entityIdExtractor: ExtractEntityId = {
    case Envelope(id, cmd) => (id.value.toString, cmd)
  }

  val shardIdExtractor: ExtractShardId = {
    case Envelope(id, _) => Math.abs(id.value.toString.hashCode % 30).toString
    case ShardRegion.StartEntity(entityId) => Math.abs(entityId.hashCode % 30).toString
  }

  def props(repository: OrderRepository): Props = {
    Props(new OrderActor(repository))
  }
}

class OrderActor(repository: OrderRepository) extends Actor with Stash with ActorLogging {
  import OrderActor._
  import context.dispatcher

  private val orderId: OrderId = OrderId(UUID.fromString(context.self.path.name))

  private var state: Option[Order] = None

  repository.find(orderId).map(OrderLoaded.apply).pipeTo(self)

  override def receive: Receive = loading

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
      log.error(ex, s"[${orderId.value}] FAILURE: ${ex.getMessage}")
      sender() ! failure
      throw ex
    case _ =>
      stash()
  }

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
      log.info(s"[$orderId] GetOrder()")

      state match {
        case Some(order) => sender() ! order
        case None => orderNotFound(orderId).pipeTo(sender())
      }
  }

  private def loading: Receive = {
    case OrderLoaded(order) =>
      unstashAll()
      state = order
      context.become(running)
    case Status.Failure(ex) =>
      log.error(ex, s"[${orderId.value}] FAILURE: ${ex.getMessage}")
      throw ex
    case _ =>
      stash()
  }

  private def openOrder(orderId: OrderId, server: Server, table: Table): Future[OrderOpened] = {
    repository
      .update(Order(orderId, server, table, Seq.empty))
      .map(OrderOpened.apply)
  }

  private def duplicateOrder[T](orderId: OrderId): Future[T] = {
    Future.failed(DuplicateOrderException(orderId))
  }

  private def addItem(order: Order, item: OrderItem): Future[ItemAddedToOrder] = {
    repository
      .update(order.withItem(item))
      .map(ItemAddedToOrder.apply)
  }

  private def orderNotFound[T](orderId: OrderId): Future[T] = {
    Future.failed(OrderNotFoundException(orderId))
  }
}
