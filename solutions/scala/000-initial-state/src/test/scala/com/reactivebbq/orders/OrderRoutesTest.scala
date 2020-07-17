package com.reactivebbq.orders

import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{MessageEntity, StatusCodes}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.testkit.TestProbe
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures

class OrderRoutesTest
  extends WordSpec
    with ScalatestRouteTest
    with OrderHelpers
    with OrderJsonFormats
    with ScalaFutures {

  val orders = TestProbe()
  val http = new OrderRoutes(orders.ref)

  "POST to /order" should {
    "fail" in {
      val order = generateOrder()
      val request = OrderActor.OpenOrder(order.server, order.table)

      val result = Post("/order")
        .withEntity(Marshal(request).to[MessageEntity].futureValue) ~>
        http.routes ~>
        runRoute

      orders.expectNoMessage()

      check {
        assert(status === StatusCodes.InternalServerError)
      } (result)
    }
  }

  "GET to /order/{id}" should {
    "fail" in {
      val order = generateOrder()
      val result = Get(s"/order/${order.id.value.toString}") ~>
        http.routes ~>
        runRoute

      orders.expectNoMessage()

      check {
        assert(status === StatusCodes.InternalServerError)
      }(result)
    }
  }

  "POST to /order/{id}/items" should {
    "fail" in {
      val item = generateOrderItem()
      val order = generateOrder().withItem(item)
      val request = OrderActor.AddItemToOrder(item)

      val result = Post(s"/order/${order.id.value.toString}/items")
        .withEntity(Marshal(request).to[MessageEntity].futureValue) ~>
        http.routes ~>
        runRoute

      orders.expectNoMessage()

      check {
        assert(status === StatusCodes.InternalServerError)
      }(result)
    }
  }

}
