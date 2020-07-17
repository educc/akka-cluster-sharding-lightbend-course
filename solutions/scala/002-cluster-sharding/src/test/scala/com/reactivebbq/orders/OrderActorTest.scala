package com.reactivebbq.orders

import akka.actor.Status
import akka.testkit.TestProbe
import com.reactivebbq.orders.OrderActor._
import org.scalatest.WordSpec

import scala.collection.mutable
import scala.concurrent.Future

class OrderActorTest extends WordSpec with AkkaSpec with OrderHelpers {

  class MockRepo extends InMemoryOrderRepository {
    private val updates: mutable.Queue[Function1[Order, Future[Order]]] = mutable
      .Queue()
    private val finds: mutable.Queue[Function1[OrderId, Future[Option[Order]]]] = mutable.Queue()

    override def update(order: Order) = {
      if(updates.nonEmpty)
        updates.dequeue()(order)
      else
        super.update(order)
    }

    override def find(orderId: OrderId) = {
      if(finds.nonEmpty)
        finds.dequeue()(orderId)
      else
        super.find(orderId)
    }

    def mockUpdate(u: Order => Future[Order]) = {
      updates.enqueue(u)
      this
    }

    def mockFind(f: OrderId => Future[Option[Order]]) = {
      finds.enqueue(f)
      this
    }
  }

  class TestContext() {
    val repo = new MockRepo()
    val orderId = generateOrderId()
    val sender = TestProbe()
    val parent = TestProbe()

    val orderActor = parent.childActorOf(
      OrderActor.props(repo),
      orderId.value.toString
    )

    def openOrder(): Order = {
      val server = generateServer()
      val table = generateTable()

      sender.send(orderActor, OpenOrder(server, table))
      sender.expectMsgType[OrderOpened].order
    }
  }

  "idExtractor" should {
    "return the expected id and message" in {
      val orderId = generateOrderId()
      val message = GetOrder()
      val envelope = Envelope(orderId, message)

      val result = entityIdExtractor(envelope)

      assert(result === (orderId.value.toString, message))
    }
  }

  "shardIdExtractor" should {
    "return the expected shard id" in {
      val orderId = generateOrderId()
      val message = GetOrder()
      val envelope = Envelope(orderId, message)

      val envelopeShard = shardIdExtractor(envelope)

      assert(envelopeShard === Math.abs(orderId.value.toString.hashCode % 30).toString)
    }
  }

  "OpenOrder" should {
    "initialize the Order" in new TestContext {
      val server = generateServer()
      val table = generateTable()

      sender.send(orderActor, OpenOrder(server, table))
      val order = sender.expectMsgType[OrderOpened].order

      assert(repo.find(order.id).futureValue === Some(order))

      assert(order.server === server)
      assert(order.table === table)
    }
    "return an error if the Order is already opened" in new TestContext {
      val server = generateServer()
      val table = generateTable()

      sender.send(orderActor, OpenOrder(server, table))
      sender.expectMsgType[OrderOpened].order

      sender.send(orderActor, OpenOrder(server, table))
      sender.expectMsg(Status.Failure(DuplicateOrderException(orderId)))
    }
    "return the repository failure if the repository fails" in new TestContext() {
      val server = generateServer()
      val table = generateTable()

      val expectedException = new RuntimeException("Repository Failure")
      repo.mockUpdate(_ => Future.failed(expectedException))

      sender.send(orderActor, OpenOrder(server, table))
      val result = sender.expectMsg(Status.Failure(expectedException))
    }
  }

  "AddItemToOrder" should {
    "return an OrderNotFoundException if the order hasn't been Opened." in new TestContext {
      val item = generateOrderItem()

      sender.send(orderActor, AddItemToOrder(item))
      sender.expectMsg(Status.Failure(OrderNotFoundException(orderId)))
    }
    "add the item to the order" in new TestContext {
      val order = openOrder()

      val item = generateOrderItem()

      sender.send(orderActor, AddItemToOrder(item))
      sender.expectMsg(ItemAddedToOrder(order.withItem(item)))
    }
    "add multiple items to the order" in new TestContext {
      val order = openOrder()

      val items = generateOrderItems(10)

      items.foldLeft(order) {
        case (prevOrder, item) =>
          val updated = prevOrder.withItem(item)

          sender.send(orderActor, AddItemToOrder(item))
          sender.expectMsg(ItemAddedToOrder(updated))

          updated
      }
    }
    "return the repository failure if the repository fails" in new TestContext() {
      val order = openOrder()

      val item = generateOrderItem()

      val expectedException = new Exception("Repository Failure")
      repo.mockUpdate(_ => Future.failed(expectedException))

      sender.send(orderActor, AddItemToOrder(item))
      sender.expectMsg(Status.Failure(expectedException))
    }
  }

  "GetOrder" should {
    "return an OrderNotFoundException if the order hasn't been Opened." in new TestContext {
      sender.send(orderActor, GetOrder())
      sender.expectMsg(Status.Failure(OrderNotFoundException(orderId)))
    }
    "return an open order" in new TestContext {
      val order = openOrder()

      sender.send(orderActor, GetOrder())
      sender.expectMsg(order)
    }
    "return an updated order" in new TestContext {
      val order = openOrder()
      val item = generateOrderItem()

      sender.send(orderActor, AddItemToOrder(item))
      sender.expectMsgType[ItemAddedToOrder].order

      sender.send(orderActor, GetOrder())
      sender.expectMsg(order.withItem(item))
    }
  }
}
