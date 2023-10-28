package eventsourcedbehavior.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import eventsourcedbehavior.app.CborSerializable

import scala.concurrent.duration.DurationInt


object User {

  def apply(userId: String): Behavior[Command] = {
    Behaviors.setup { context =>
    EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
      PersistenceId("User", userId),
      State.empty,
      (state, command) => handleCommand(context, userId, state, command),
      (state, event) => handleEvent(state, event)
    )
      .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
    }
  }

  def handleCommand(context: ActorContext[Command], userId: String, state: State, command: Command): ReplyEffect[Event, State] = {
    command match {
      case _@AddBettingSlipToUser(_, replyTo) =>
        val slip = context.spawn(BettingSlip(userId), s"bettingSlip$userId")
        Effect
          .persist(SlipAddedToUser(slip.ref))
          .thenReply(replyTo)(
            st => BettingSlipAddedResponse(s"Successfully added slip with ref: ${st.slipRef} to user with id: $userId")
    )
      case _@GetBettingSlipByRef(replyTo) =>
        state.slipRef ! BettingSlip.GetBettingSlip(replyTo)
        Effect.noReply
    }
  }

  def handleEvent(state: State, event: Event): State = {
    event match {
      case _@SlipAddedToUser(ref) =>
        state.addSlipToUser(ref)
    }
  }

  //commands
  //Because traits of Commands, Events and Responses are sealed, they can:
  //1: Not the inherited outside of this Actor
  //2: Produce a warning if we forget to match a type inside our pattern matching
  sealed trait Command extends CborSerializable

  final case class AddBettingSlipToUser(userRef: ActorRef[Command], replyTo: ActorRef[Response]) extends Command
  final case class GetBettingSlipByRef(replyTo: ActorRef[BettingSlip.Response]) extends Command

  //private final case class WrappedBettingSlipResponse(response: BettingSlip.Response) extends Command

  //events
  sealed trait Event extends CborSerializable

  case class SlipAddedToUser(ref: ActorRef[BettingSlip.Command]) extends Event

  sealed trait Response

  final case class BettingSlipAddedResponse(responseMsg: String) extends Response

  //state
  //For this example, as the state is represents the domain model, business logic is handled inside the state.
  //Depending on taste you can even move command- and / or eventhandlers inside the state.
  final case class State(slipRef: ActorRef[BettingSlip.Command]) extends CborSerializable {
    def addSlipToUser(ref: ActorRef[BettingSlip.Command]): State = {
      copy(ref)
    }
  }

  //create companion object to be able to create an empty state like we know it from collections
  object State {
    val empty = State(null)
  }
}
