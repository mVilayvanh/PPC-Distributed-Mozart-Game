package upmc.akka.ppc

import akka.actor._
import upmc.akka.ppc.Conductor.{StartGame, runtwoDice}
import upmc.akka.ppc.DataBaseActor.Measure
import upmc.akka.ppc.Provider.GetMeasure
import math.random
import scala.concurrent.duration.DurationInt

object Conductor{
  case class StartGame()

  def runtwoDice(): Int = {
    val dice1 = (random * 6).toInt + 1
    val dice2 = (random * 6).toInt + 1
    return dice1 + dice2;
  }

}

class Conductor(provider: ActorRef,player: ActorRef) extends Actor {
import Conductor._
  import context.dispatcher

  def receive = {
    case StartGame =>
      println("=================-------------=================")
      val num = runtwoDice()
      println("Conductor: I have rolled the dice and the number is " + num)
      provider ! GetMeasure(num);


    case measure: Measure =>
      println("Conductor: I have sent the measure to the player")
      player ! measure
      context.system.scheduler.scheduleOnce(1800.milliseconds, self, StartGame)
  }

}


