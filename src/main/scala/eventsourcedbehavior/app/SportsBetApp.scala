package eventsourcedbehavior.app

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.pattern.StatusReply
import akka.util.Timeout
import eventsourcedbehavior.actors.{BettingSlip, UserManager}
import eventsourcedbehavior.actors.UserManager.GetSlipByRef

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Failure


object SportsBetApp {
  def main(args: Array[String]): Unit = {
    val id = "someId"
    // implicit ActorSystem in scope
    implicit val system: ActorSystem[UserManager.Command] = ActorSystem(UserManager(), "userManager")
    implicit val timeout: Timeout = 3.seconds


    val userManagerFuture: Future[StatusReply[UserManager.Response]] = system.ask(ref =>
      UserManager.RegisterUserToManager(id, ref))

    // the response callback will be executed on this execution context
    implicit val ec: ExecutionContextExecutor = system.executionContext

    //TODO: Change this avoid nested calls
    userManagerFuture.onComplete {
      case scala.util.Success(akka.pattern.StatusReply.Success(UserManager.UserRegisteredResponse(msg))) =>
        println(msg)
        val betFuture: Future[StatusReply[BettingSlip.Response]] = system.ask(ref => GetSlipByRef("someId", ref))

        betFuture.onComplete {
          case scala.util.Success(akka.pattern.StatusReply.Success(BettingSlip.GetSlipResponse(slip))) =>
            println(slip)

          case Failure(ex) => println(s"Something went wrong! ${ex.getMessage}")
      }
      case Failure(ex) => println(s"Something went wrong! ${ex.getMessage}")
    }
  }
}