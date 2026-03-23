package upmc.akka.ppc

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object Concert extends App {



  val system  =  ActorSystem("ConcertSimulation")
  val conductor = system.actorOf(Props(new Conductor), "Conductor")

  conductor ! Conductor.StartGame
  println("starting Mozart's game")
 }