package eventsourcedbehavior.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.pattern.StatusReply
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior, RetentionCriteria}
import eventsourcedbehavior.actors.User.{AddBettingSlipToUser, GetBettingSlipByRef}
import eventsourcedbehavior.app.CborSerializable

import scala.concurrent.duration.DurationInt


object UserManager {

  def apply(): Behavior[Command] = {
    Behaviors.setup { context =>
      EventSourcedBehavior[Command, Event, State](
        PersistenceId.ofUniqueId("UserManager"),
        State.empty,
        (state, command) => handleCommand(context, state, command),
        (state, event) => handleEvent(state, event)
      )
        .withRetention(RetentionCriteria.snapshotEvery(numberOfEvents = 100, keepNSnapshots = 3))
        .onPersistFailure(SupervisorStrategy.restartWithBackoff(200.millis, 5.seconds, 0.1))
    }
  }

  def handleCommand(context: ActorContext[Command], state: State, command: Command): Effect[Event, State] = {
    command match {
      case _@RegisterUserToManager(userSessionId, replyTo) =>
        if(state.registeredUsers.exists(_._1 == userSessionId)) {
          Effect.reply(replyTo)(
            StatusReply.error(
              s"User with id $userSessionId already registered to manager."))
        }
        else {
          val user = context.spawn(User(userSessionId), userSessionId)
          Effect
            .persist(UserRegisteredToManager(userSessionId, user.ref))
            .thenRun {
              updatedUserManager =>
                user ! AddBettingSlipToUser(user.ref)
                replyTo ! StatusReply.Success(
                  UserRegisteredResponse(
                    s"User with id $userSessionId registered to manager with actorRef ${updatedUserManager.registeredUsers(userSessionId)}"))
                Behaviors.same
            }
        }
      case _@GetSlipByRef(userSessionId, replyTo) =>
         state.registeredUsers(userSessionId) ! GetBettingSlipByRef(replyTo)
        Effect.none
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

  final case class RegisterUserToManager(userSessionId: String, replyTo: ActorRef[StatusReply[Response]]) extends Command

  final case class UserRegisteredToManager(userSessionId: String, createdActorRef: ActorRef[User.Command]) extends Event

  final case class UserRegisteredResponse(message: String) extends Response

  object State {
    val empty: State = State(Map.empty)
  }

  sealed trait Response

  case class GetSlipByRef(userSessionid: String, ref: ActorRef[StatusReply[BettingSlip.Response]]) extends Command
}