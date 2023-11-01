package eventsourcedbehavior.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
import eventsourcedbehavior.app.CborSerializable

import scala.concurrent.duration.DurationInt

/** Factory for [[eventsourcedbehavior.actors.UserManager]] */
object UserManager {
//TODO:Change/add exception type for supervision in guardian

  /** Creates the supervised UserManager actor with an EventSourcedBehavior with enforced replies.
   *
   * @see https://doc.akka.io/docs/akka/2.8.5/typed/fault-tolerance.html#supervision
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#event-sourcing
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#replies
   */
  def apply(): Behavior[Command] = {
    Behaviors.supervise[Command] {
      Behaviors.setup { context =>
        EventSourcedBehavior.withEnforcedReplies[Command, Event, State](
          PersistenceId.ofUniqueId("UserManager"),
          State.empty,
          (state, command) => handleCommand(context, state, command),
          (state, event) => handleEvent(state, event)
        )
          .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
          .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
      }
    }.onFailure[IllegalStateException](SupervisorStrategy.restart)
  }

  /** Commandhandler which handles all incoming messages of type Command.
   *
   * @note These handlers DO NOT return the next Behavior wanted for the specific case matched like in FSM or "normal" typed akka actors.
   *       These handlers return the current an ReplyEffect / the current state.
   *       You need to:
   *       Add an ActorRef ( replyTo ) to every command.
   *       Return at least NoReply ( if you don't want to reply ) or an actual reply.
   * @param context You can pass context to it like done in this example if you need to. This is optional to do so.
   * @param state   The current state of this actor.
   * @param command The message of type Command which is recieved.
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#command-handler
   * @return Returns an ReplyEffect[Event, State].
   */
  def handleCommand(context: ActorContext[Command], state: State, command: Command): ReplyEffect[Event, State] = {
    val userResponseMapper: ActorRef[User.Response] =
      context.messageAdapter(rsp => WrappedUserResponse(rsp))
    command match {
      case _@RegisterUserToManager(userSessionId, replyTo) =>
        if(state.registeredUsers.exists(_._1 == userSessionId)) {
          Effect.reply(replyTo)(
            throw new IllegalStateException(
              s"User with id $userSessionId already registered to manager."))
        }
        else {
          val user = context.spawn(User(userSessionId), userSessionId)
          context.watch(user)
          Effect
            .persist(UserRegisteredToManager(userSessionId, user.ref))
            .thenRun {
                _: State => user ! User.AddBettingSlipToUser(user.ref, userResponseMapper)
            }
            .thenReply(replyTo)(st =>
              UserRegisteredResponse(
                s"User with id $userSessionId registered to manager with actorRef ${st.registeredUsers(userSessionId)}"))
        }
      case _@GetSlipByRef(userSessionId, replyTo) =>
         state.registeredUsers(userSessionId) ! User.GetBettingSlipByRef(replyTo)
        Effect.noReply
      case wrapped: WrappedUserResponse =>
        wrapped.response match {
          case User.BettingSlipAddedResponse(msg) =>
            context.log.info(msg)
            Effect.noReply
        }
    }
  }

  /** Eventhandler which handles all incoming messages of type Event.
   *
   * @param state The current state of this actor.
   * @param event The message of type Event which is received.
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#event-handler
   * @return Returns the State of this actor.
   */
  def handleEvent(state: State, event: Event): State = {
    event match {
      case _@UserRegisteredToManager(id, actorRef) =>
        state.updateUsers(id, actorRef)
    }
  }

  //commands
  sealed trait Command extends CborSerializable

  //events
  sealed trait Event extends CborSerializable

  //state
  final case class State(registeredUsers: Map[String, ActorRef[User.Command]]) extends CborSerializable {
    def updateUsers(userSessionId: String, actorRef: ActorRef[User.Command]): State = {
      copy(registeredUsers = registeredUsers + (userSessionId -> actorRef))
    }
  }

  final case class RegisterUserToManager(userSessionId: String, replyTo: ActorRef[Response]) extends Command

  final case class UserRegisteredToManager(userSessionId: String, createdActorRef: ActorRef[User.Command]) extends Event

  final case class UserRegisteredResponse(message: String) extends Response

  object State {
    val empty: State = State(Map.empty)
  }

  sealed trait Response

  case class GetSlipByRef(userSessionId: String, ref: ActorRef[BettingSlip.Response]) extends Command
  private final case class WrappedUserResponse(response: User.Response) extends Command
}