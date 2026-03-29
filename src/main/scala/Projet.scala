package upmc.akka.leader

import com.typesafe.config.ConfigFactory
import akka.actor._
import Musician._

case class Terminal(id: Int, ip: String, port: Int)

object Projet {


  def main(args: Array[String]) {
    // Gestion des erreurs
    if (args.size != 1) {
      println("Erreur de syntaxe : run <num>")
      sys.exit(1)
    }

    val id: Int = args(0).toInt

    if (id < 0 || id > 3) {
      println("Erreur : <num> doit etre compris entre 0 et 3")
      sys.exit(1)
    }

    var musicienlist = List[Terminal]()

    // recuperation des adresses de tous les musiciens
    // hardcoded path name
    for (i <- 3 to 0 by -1) {
      val config = ConfigFactory.load().getConfig("system" + i)
      val address = config.getString("akka.remote.netty.tcp.hostname")
      val port = config.getInt("akka.remote.netty.tcp.port")
      musicienlist = Terminal(i, address, port) :: musicienlist
    }

    println(musicienlist)

    val system = ActorSystem("MozartSystem" + id, ConfigFactory.load().getConfig("system" + id))
    val healthMonitor = system.actorOf(Props[HealthMonitor])
    val musicien = system.actorOf(Props(new Musician(id, musicienlist, healthMonitor)), "Musician" + id)
    musicien ! Start
  }
}
