package eventsourcedbehavior.actors

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, SupervisorStrategy}
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.{Effect, EventSourcedBehavior,RetentionCriteria}
import eventsourcedbehavior.app.CborSerializable

import scala.concurrent.duration.DurationInt

/** Factory for [[eventsourcedbehavior.actors.UserManager]] */
object UserManager {
//TODO:Change/add exception type for supervision in guardian

  /** Creates the supervised UserManager actor with an EventSourcedBehavior.
   *
   * @see https://doc.akka.io/docs/akka/2.8.5/typed/fault-tolerance.html#supervision
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#event-sourcing
   */
  def apply(): Behavior[Command] = {
    Behaviors.supervise[Command] {
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
    }.onFailure[IllegalStateException](SupervisorStrategy.restart)
  }

  /** Commandhandler which handles all incoming messages of type Command.
   *
   * @note These handlers DO NOT return the next Behavior wanted for the specific case matched like in FSM or "normal" typed akka actors.
   *       These handlers return the current an Effect / the current state
   * @param context You can pass context to it like done in this example if you need to. This is optional to do so.
   * @param state The current state of this actor.
   * @param command The message of type Command which is recieved.
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#command-handler
   * @return Returns an Effect[Event, State].
   */
  private def handleCommand(context: ActorContext[Command], state: State, command: Command): Effect[Event, State] = {
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
          //Whatching the child actor because "Terminated" message is not handled inside of it
          //and is therefore bubbled up to this actor ( it's parent )
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
   * @param state   The current state of this actor.
   * @param event The message of type Event which is recieved.
   * @see https://doc.akka.io/docs/akka/current/typed/persistence.html#event-handler
   * @return Returns the State of this actor.
   */
  private def handleEvent(state: State, event: Event): State = {
    event match {
      case _@UserRegisteredToManager(id, actorRef) =>
        state.updateUsers(id, actorRef)
    }
  }

  //commands
  //Because traits of Commands, Events and Responses are sealed, they can:
  //1: Not the inherited outside of this Actor
  //2: Produce a warning if we forget to match a type inside our pattern matching
  sealed trait Command extends CborSerializable

  //events
  sealed trait Event extends CborSerializable

  //response
  sealed trait Response

  //state

  /** State of this actor.
   *
   * You can also implement command- and eventhandlers inside of the state if you want to.
   * This is a common way to implement them but depends on personal taste / guidelines inside the team.
   * For this example, as domain model, the state also includes the main business logic.
   *
   * @param registeredUsers The currently registered users to this actor.
   */
  final case class State(registeredUsers: Map[String, ActorRef[User.Command]]) extends CborSerializable {
    def updateUsers(userSessionId: String, actorRef: ActorRef[User.Command]): State = {
      copy(registeredUsers = registeredUsers + (userSessionId -> actorRef))
    }
  }

  final case class RegisterUserToManager(userSessionId: String, replyTo: ActorRef[Response]) extends Command

  case class GetSlipByRef(userSessionid: String, ref: ActorRef[BettingSlip.Response]) extends Command

  private final case class WrappedUserResponse(response: User.Response) extends Command

  final case class UserRegisteredToManager(userSessionId: String, createdActorRef: ActorRef[User.Command]) extends Event

  final case class UserRegisteredResponse(message: String) extends Response

  /** State companion Object.
   *
   */
  object State {
    val empty: State = State(Map.empty)
  }
}
