package eventsourcedbehavior.actors

import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import eventsourcedbehavior.app.CborSerializable

//TODO: Implement logic in handlers and add commands/events/responses
object BettingSlip {


  def apply(userId: String): Behavior[Command] = {
    EventSourcedBehavior[Command, Event, State](
      PersistenceId("BettingSlip", userId),
      State.empty,
      (state, command) => handleCommand(userId, state, command),
      (state, event) => handleEvent(state, event)
    )
  }

  def handleCommand(userId: String, state: State, command: Command): Effect[Event, State] = {
    command match {
      case _@GetBettingSlip(replyTo) =>
        replyTo ! StatusReply.Success(GetSlipResponse(state))
        Effect.none
    }
  }

  def handleEvent(state: State, event: Event): State = {
    ???
  }

  //commands
  sealed trait Command extends CborSerializable

  //events
  sealed trait Event extends CborSerializable

  sealed trait Response

  final case class GetBettingSlip(replyTo: ActorRef[StatusReply[Response]]) extends Command

  case class GetSlipResponse(state: State) extends Response

  //state
  final case class State(userRef: ActorRef[Command], betMap: Map[String, Float], sum: Int) extends CborSerializable

  object State {
    val empty = State(null, Map.empty, 0)
  }
}
