package eventsourcedbehavior.actors

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy, scaladsl}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import eventsourcedbehavior.actors.UserManager.Command
import eventsourcedbehavior.app.CborSerializable

import scala.concurrent.duration.DurationInt

//TODO: Implement logic in handlers and add commands/events/responses
object BettingSlip {


  def apply(userId: String): Behavior[Command] = {
    Behaviors.supervise[Command] {
      EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
          PersistenceId("BettingSlip", userId),
          State.empty,
          (state, command) => handleCommand(userId, state, command),
          (state, event) => handleEvent(state, event)
        )
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
    }.onFailure[Exception](SupervisorStrategy.restart)
  }

  def handleCommand(userId: String, state: State, command: Command): ReplyEffect[Event, State] = {
    command match {
      case _@GetBettingSlip(replyTo) =>
        Effect.reply(replyTo)(GetSlipResponse(state))
        //Effect.none
      case _@UpdateBettingSlip(replyTo, betMap, sum) =>
        Effect
          .persist(BettingSlipUpdated(betMap, sum))
          .thenReply(replyTo)(
            _ => BettingSlipUpdatedResponse(state))
    }
  }

  def handleEvent(state: State, event: Event): State = {
    event match {
      case _@BettingSlipUpdated(betMap, sum) =>
        state.copy(betMap = betMap, sum = sum)
    }
  }

  //commands
  sealed trait Command extends CborSerializable

  //events
  sealed trait Event extends CborSerializable

  sealed trait Response

  final case class GetBettingSlip(replyTo: ActorRef[Response]) extends Command

  case class GetSlipResponse(state: State) extends Response

  //state
  final case class State(userRef: ActorRef[Command], betMap: Map[String, Float], sum: Int) extends CborSerializable

  object State {
    val empty = State(null, Map.empty, 0)
  }

  final case class BettingSlipUpdatedResponse(updatedBet: State) extends Response

  case class BettingSlipUpdated(betMap: Map[String, Float], sum: Int) extends Event

  case class UpdateBettingSlip(replyTo: ActorRef[Response], betMap: Map[String, Float], sum: Int) extends Command
}
