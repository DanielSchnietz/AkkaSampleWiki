package eventsourcedbehavior.actors

import akka.Done
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit
import akka.persistence.typed.PersistenceId
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, LogCapturing, ScalaTestWithActorTestKit}
import akka.pattern.StatusReply
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.wordspec.AnyWordSpecLike
import eventsourcedbehavior.actors.UserManager

class UserManagerSpec extends ScalaTestWithActorTestKit(ConfigFactory.parseString("akka.actor.allow-java-serialization = on")
  .withFallback(EventSourcedBehaviorTestKit.config))
  with AnyWordSpecLike
  with BeforeAndAfterEach {

  private val eventSourcedTestKit =
    EventSourcedBehaviorTestKit[UserManager.Command, UserManager.Event, UserManager.State](
      system,
      UserManager())

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    eventSourcedTestKit.clear()
  }

  "UserManager" must {

    "spawn a User child Actor" in {
      val result = eventSourcedTestKit.runCommand[UserManager.Response](replyTo => UserManager.RegisterUserToManager("id1", replyTo))
      result.event shouldBe a [UserManager.UserRegisteredToManager]
      result.reply shouldBe a [UserManager.UserRegisteredResponse]
      result.stateOfType[UserManager.State].registeredUsers.size shouldBe 1
    }
  }
}
