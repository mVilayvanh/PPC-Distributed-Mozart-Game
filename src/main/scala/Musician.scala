package upmc.akka.leader

import scala.language.postfixOps
import akka.actor._
import akka.util.Timeout
import upmc.akka.leader.HealthMonitor.{Alive, HowManyMusiciansAreAlive, MusicianCount}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.TimeUnit
import scala.math.random


// Importing necessary classes from other packages
import upmc.akka.leader.DataBaseActor.Measure

object Musician {
  case object Start
  case object WaitingForMusicians
  case object WaitingToPlay
  case object Play

  def runtwoDice(): Int = {
    val dice1 = (random * 6).toInt + 1
    val dice2 = (random * 6).toInt + 1
    dice1 + dice2
  }
}

class Musician(val id: Int, val terminaux: List[Terminal], val monitor: ActorRef) extends Actor {
  import Musician._

  val displayActor = context.actorOf(Props[DisplayActor], name = "displayActor")
  val player = context.actorOf(Props(new PlayerActor()), "Player")
  val provider = context.actorOf(Props(new Provider()), "Provider")

  val INTERVAL = 1000
  var WAITING_TIME = 0

  def receive = {
    case Start =>
      displayActor ! Message("Musician " + this.id + " is created")
      context.system.scheduler.schedule(
        Duration.Zero,
        INTERVAL.milliseconds,
        monitor,
        Alive(id)
      )
      if (id == 0) monitor ! HowManyMusiciansAreAlive
      else self ! WaitingToPlay


    case MusicianCount(n) =>
      if (n == 1) self ! WaitingForMusicians
      else println("send measure to play")


    case WaitingForMusicians =>
      displayActor ! Message("Waiting...")
      WAITING_TIME += 1
      if (WAITING_TIME == 10) {
        displayActor ! Message("No musician came... I leave...")
      } else {
        context.system.scheduler.scheduleOnce(INTERVAL.milliseconds)(monitor ! HowManyMusiciansAreAlive)
      }

    case Play =>
      displayActor ! Message("I will play a measure")
      val num = Musician.runtwoDice()
      println("Conductor: I have rolled the dice and the number is " + num)
      provider ! Provider.GetMeasure(num)


    case measure: Measure =>
      println("Conductor: I have sent the measure to the player")
      player ! measure
      context.system.scheduler.scheduleOnce(1800.milliseconds, self, Play)
  }
}
