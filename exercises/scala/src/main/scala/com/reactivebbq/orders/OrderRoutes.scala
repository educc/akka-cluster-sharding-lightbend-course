package com.reactivebbq.orders

import java.util.UUID

import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{ExceptionHandler, Route}
import akka.util.Timeout

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class OrderRoutes(orderActors: ActorRef)(implicit ec: ExecutionContext)
  extends OrderJsonFormats {

  private implicit val timeout: Timeout = Timeout(5.seconds)

  private def exceptionHandler: ExceptionHandler = ExceptionHandler {
    case ex =>
      complete(HttpResponse(StatusCodes.InternalServerError, entity = ex.getMessage))
  }

  lazy val routes: Route =
    handleExceptions(exceptionHandler) {
      pathPrefix("order") {
        post {
          entity(as[OrderActor.OpenOrder]) { openOrder =>
            complete {
              ???
            }
          }
        } ~
        pathPrefix(Segment) { id =>

          val orderId = OrderId(UUID.fromString(id))

          get {
            complete {
              ???
            }
          } ~
          path("items") {
            post {
              entity(as[OrderActor.AddItemToOrder]) { addItemToOrder =>
                complete {
                  ???
                }
              }
            }
          }
        }
      }
    }
}
