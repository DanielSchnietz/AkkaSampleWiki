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


    /*val userManagerFuture: Future[StatusReply[UserManager.Response]] = system.ask(ref =>
      UserManager.RegisterUserToManager(id, ref))

    // the response callback will be executed on this execution context
    implicit val ec: ExecutionContextExecutor = system.executionContext

    //TODO: Change this avoid nested calls
    //If this would be an actor, you could've used the following pattern:
    // https://doc.akka.io/docs/akka/current/typed/interaction-patterns.html#send-future-result-to-self
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
    }*/

    //This is an implementation to avoid the nested onComplete methods.
    implicit val ec: ExecutionContextExecutor = system.executionContext
    val userManagerFuture: Future[StatusReply[UserManager.Response]] = system.ask(ref =>
      UserManager.RegisterUserToManager(id, ref))

    val betFuture: Future[StatusReply[BettingSlip.Response]] = userManagerFuture.flatMap(_ =>
      system.ask(ref => GetSlipByRef("someId", ref)
      ))
    betFuture.onComplete {
      case scala.util.Success(akka.pattern.StatusReply.Success(BettingSlip.GetSlipResponse(slip))) =>
        println(slip)
      case Failure(ex) => println(s"Something went wrong! ${ex.getMessage}")
    }
  }
}