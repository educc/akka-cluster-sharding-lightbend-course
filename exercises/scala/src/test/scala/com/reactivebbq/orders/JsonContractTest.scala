package com.reactivebbq.orders

import akka.http.scaladsl.model.ContentTypes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.WordSpec
import spray.json.JsonParser

class JsonContractTest extends WordSpec with ScalatestRouteTest with OrderHelpers {
  val orderRepo = new InMemoryOrderRepository()
  val orderActors = system.actorOf(OrderActor.props(orderRepo))
  val orderRoutes = new OrderRoutes(orderActors)(system.dispatcher)

  "Creating an Order" should {
    "Adhere to the Json Contract" in {
      val server = generateServer()
      val table = generateTable()

      val request =
        s"""
          {
            "server":{
              "name": "${server.name}"
            },
            "table":{
              "number": ${table.number}
            }
          }
        """

      val response =
        s"""
          {
            "id":"ID",
            "items":[],
            "server":{
              "name":"${server.name}"
            },
            "table":{
              "number":${table.number}
            }
          }
         """

      val result = Post("/order")
        .withEntity(ContentTypes.`application/json`, request) ~> orderRoutes.routes ~> runRoute

      check {
        val json = JsonParser(
          entityAs[String].replaceFirst(
            """"id":".*?"""",
            """"id":"ID""""
          )
        )
        val expected = JsonParser(response)

        assert(json === expected)
      } (result)
    }
  }

  "Retrieving the Order" should {
    "Adhere to the Json Contract" in {
      val item1 = generateOrderItem()
      val item2 = generateOrderItem()
      val order = generateOrder(items = Seq(item1, item2))
      orderRepo.update(order)

      val response =
        s"""
          {
            "id":"${order.id.value.toString}",
            "items":[
              {
                "name":"${item1.name}",
                "specialInstructions":"${item1.specialInstructions}"
              },
              {
                "name":"${item2.name}",
                "specialInstructions":"${item2.specialInstructions}"
              }
            ],
            "server":{
              "name":"${order.server.name}"
            },
            "table":{
              "number":${order.table.number}
            }
          }
         """

      val result = Get(s"/order/${order.id.value.toString}") ~> orderRoutes.routes ~> runRoute

      check {
        val json = JsonParser(entityAs[String])
        val expected = JsonParser(response)

        assert(json === expected)
      } (result)
    }
  }

  "Adding to an Order" should {
    "Adhere to the Json Contract" in {
      val item = generateOrderItem()
      val order = generateOrder(items = Seq.empty)
      orderRepo.update(order)

      val request =
        s"""
          {
            "item":{
              "name":"${item.name}",
              "specialInstructions":"${item.specialInstructions}"
            }
          }
        """

      val response =
        s"""
          {
            "id":"${order.id.value.toString}",
            "items":[
              {
                "name":"${item.name}",
                "specialInstructions":"${item.specialInstructions}"
              }
            ],
            "server":{
              "name":"${order.server.name}"
            },
            "table":{
              "number":${order.table.number}
            }
          }
         """

      val result = Post(s"/order/${order.id.value.toString}/items")
        .withEntity(ContentTypes.`application/json`, request) ~> orderRoutes.routes ~> runRoute

      check {
        val json = JsonParser(entityAs[String])
        val expected = JsonParser(response)

        assert(json === expected)
      } (result)
    }
  }
}
