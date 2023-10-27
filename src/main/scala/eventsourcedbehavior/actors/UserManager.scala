package eventsourcedbehavior.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, ReplyEffect, RetentionCriteria}
//import eventsourcedbehavior.actors.User.{AddBettingSlipToUser, GetBettingSlipByRef}
import eventsourcedbehavior.app.CborSerializable

import scala.concurrent.duration.DurationInt


object UserManager {
//TODO:Change/add exception type for supervision in guardian
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

  case class GetSlipByRef(userSessionid: String, ref: ActorRef[BettingSlip.Response]) extends Command
  private final case class WrappedUserResponse(response: User.Response) extends Command
}