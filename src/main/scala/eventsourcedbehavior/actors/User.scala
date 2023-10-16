package eventsourcedbehavior.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import eventsourcedbehavior.actors.BettingSlip. GetBettingSlip
import eventsourcedbehavior.app.CborSerializable


object User {

  def apply(userId: String): Behavior[Command] = {
    Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      PersistenceId("User", userId),
      State.empty,
      (state, command) => handleCommand(context, userId, state, command),
      (state, event) => handleEvent(state, event)
    )}
  }

  def handleCommand(context: ActorContext[Command], userId: String, state: State, command: Command): Effect[Event, State] = {
    command match {
      case _@AddBettingSlipToUser(_) =>
        val slip = context.spawn(BettingSlip(userId), s"bettingSlip$userId")
        Effect
          .persist(SlipAddedToUser(slip.ref))
          .thenRun {context.log.info("Slip added successfully")
            updatedUser => StatusReply.Success(updatedUser)
          }
      case _@GetBettingSlipByRef(replyTo) =>
        state.slipRef ! GetBettingSlip(replyTo)
        Effect.none
    }
  }

  def handleEvent(state: State, event: Event): State = {
    event match {
      case _@SlipAddedToUser(ref) =>
        state.addSlipToUser(ref)
    }
  }

  //commands
  sealed trait Command extends CborSerializable

  final case class AddBettingSlipToUser(userRef: ActorRef[Command]) extends Command
  final case class GetBettingSlipByRef(replyTo: ActorRef[StatusReply[BettingSlip.Response]]) extends Command

  //events
  sealed trait Event extends CborSerializable

  case class SlipAddedToUser(ref: ActorRef[BettingSlip.Command]) extends Event

  sealed trait Response
  final case class GetBettingSlipResponse(slip: BettingSlip.State) extends Response

  final case class BettingSlipAddedResponse(slip: ActorRef[Response]) extends Response

  //state
  final case class State(slipRef: ActorRef[BettingSlip.Command]) extends CborSerializable {
    def addSlipToUser(ref: ActorRef[BettingSlip.Command]): State = {
      copy(ref)
    }
  }

  object State {
    val empty = State(null)
  }
}
