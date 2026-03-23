package upmc.akka.ppc

import akka.actor.{Actor, ActorRef, ActorSystem, Props}

object Concert extends App {



  val system  =  ActorSystem("ConcertSimulation")
  val player = system.actorOf(Props(new PlayerActor), "Player")
  val dataBase = system.actorOf(Props(new DataBaseActor), "DataBase")
  val provider = system.actorOf(Props(new Provider(dataBase)), "Provider")
  val conductor = system.actorOf(Props(new Conductor(provider, player)), "Conductor")

  conductor ! Conductor.StartGame
  println("starting Mozart's game")
 }
