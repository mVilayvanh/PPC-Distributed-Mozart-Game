package upmc.akka.leader

import scala.language.postfixOps
import akka.actor._
import upmc.akka.leader.Musicien._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

object Musicien {
  case class Start()

  case class WaitingForMusicians()

  case class WaitingToPlay()
}

class Musicien(val id: Int, val terminaux: List[Terminal], val monitor: ActorRef) extends Actor {

  val displayActor = context.actorOf(Props[DisplayActor], name = "displayActor")
  val INTERVAL = 1000;
  var WAITING_TIME = 0;

  def receive = {
    case Start =>
      displayActor ! Message("Musicien " + this.id + " is created")
      monitor ! Alive(id)
      if (id == 0) monitor ! HowManyMusiciansAreAlive
      else self ! WaitingToPlay
    case MusicianCount(n) =>
      if (n == 1) self ! WaitingForMusicians
      else println("send measure to play")
    case WaitingForMusicians =>
      displayActor ! Message ("Waiting...")
      WAITING_TIME += 1
      if (WAITING_TIME == 10) {
        displayActor ! Message("No musician came... I leave...")
      } else {
        context.system.scheduler.scheduleOnce(INTERVAL milliseconds)(monitor ! HowManyMusiciansAreAlive)
      }
  }
}
