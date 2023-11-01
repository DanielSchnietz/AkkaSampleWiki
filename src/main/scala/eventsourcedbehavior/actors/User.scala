package eventsourcedbehavior.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior}
import eventsourcedbehavior.app.CborSerializable


object User {

  /** Creates the supervised User actor with an EventSourcedBehavior.
   *
   * @see https://doc.akka.io/docs/akka/2.8.5/typed/fault-tolerance.html#supervision
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#event-sourcing
   */
  def apply(userId: String): Behavior[Command] = {
    Behaviors.setup { context =>
    EventSourcedBehavior[Command, Event, State](
      PersistenceId("User", userId),
      State.empty,
      (state, command) => handleCommand(context, userId, state, command),
      (state, event) => handleEvent(state, event)
    )
      /*.withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
      .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))*/
    }
  }

  /** Commandhandler which handles all incoming messages of type Command.
   *
   * @note These handlers DO NOT return the next Behavior wanted for the specific case matched like in FSM or "normal" typed akka actors.
   *       These handlers return the current an Effect / the current state
   * @param context You can pass context to it like done in this example if you need to. This is optional to do so.
   * @param userId The id of the user.
   * @param state   The current state of this actor.
   * @param command The message of type Command which is recieved.
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#command-handler
   * @return Returns an Effect[Event, State].
   */
  private def handleCommand(context: ActorContext[Command], userId: String, state: State, command: Command): Effect[Event, State] = {
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

  /** Eventhandler which handles all incoming messages of type Event.
   *
   * @param state The current state of this actor.
   * @param event The message of type Event which is recieved.
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#event-handler
   * @return Returns the State of this actor.
   */
  private def handleEvent(state: State, event: Event): State = {
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

  private case class SlipAddedToUser(ref: ActorRef[BettingSlip.Command]) extends Event

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
    val empty: State = State(null)
  }
}
