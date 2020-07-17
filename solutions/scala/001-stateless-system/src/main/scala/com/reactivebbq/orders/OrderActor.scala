package com.reactivebbq.orders

import akka.actor.{Actor, ActorLogging, Props}
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

  def props(repository: OrderRepository): Props = {
    Props(new OrderActor(repository))
  }
}

class OrderActor(repository: OrderRepository) extends Actor with ActorLogging {
  import OrderActor._
  import context.dispatcher

  override def receive: Receive = {
    case Envelope(orderId, OpenOrder(server, table)) =>
      log.info(s"[$orderId] OpenOrder($server, $table)")

      repository.find(orderId).flatMap {
        case Some(_) => duplicateOrder(orderId)
        case None => openOrder(orderId, server, table)
      }.pipeTo(sender())

    case Envelope(orderId, AddItemToOrder(item)) =>
      log.info(s"[$orderId] AddItemToOrder($item)")

      repository.find(orderId).flatMap {
        case Some(order) => addItem(order, item)
        case None => orderNotFound(orderId)
      }.pipeTo(sender())

    case Envelope(orderId, GetOrder()) =>
      log.info(s"[$orderId] GetOrder()")

      repository.find(orderId).flatMap {
        case Some(order) => Future.successful(order)
        case None => orderNotFound(orderId)
      }.pipeTo(sender())
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
