package eventsourcedbehavior.app

import akka.actor.typed.ActorSystem
import akka.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import akka.util.Timeout
import eventsourcedbehavior.actors.{BettingSlip, UserManager}
import eventsourcedbehavior.actors.UserManager.GetSlipByRef

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.DurationInt
import scala.util.Failure

/** Entrypoint for this App. */
object SportsBetApp {
  def main(args: Array[String]): Unit = {
    val id = "someId"
    // implicit ActorSystem in scope
    implicit val system: ActorSystem[UserManager.Command] = ActorSystem(UserManager(), "userManager")
    implicit val timeout: Timeout = 3.seconds

    implicit val ec: ExecutionContextExecutor = system.executionContext

    //Using Future, to be able to use ask from outside the actor
    val userManagerFuture: Future[UserManager.Response] = system.ask(ref =>
      UserManager.RegisterUserToManager(id, ref))

    val betFuture: Future[BettingSlip.Response] = userManagerFuture.flatMap(_ =>
      system.ask(ref => GetSlipByRef("someId", ref)
      ))
    betFuture.onComplete {
      case scala.util.Success(BettingSlip.GetSlipResponse(slip)) =>
        println(s"Current BettingSlip $slip")
      case scala.util.Success(BettingSlip.BettingSlipUpdatedResponse(state)) =>
        println(s"New BettingSLip State: $state")
      case Failure(ex) => println(s"Something went wrong! ${ex.getMessage}")
    }
  }
}