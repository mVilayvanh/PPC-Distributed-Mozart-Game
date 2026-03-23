package upmc.akka.leader

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object Concert extends App {



  val system  =  ActorSystem("ConcertSimulation")
  val musicien1 = system.actorOf(Props(new Musician(0, List(), null)), "Musicien1")

  musicien1 ! Musician.Play
  println("starting Mozart's game")
 }