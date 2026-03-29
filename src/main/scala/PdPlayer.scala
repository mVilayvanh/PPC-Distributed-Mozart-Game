
package upmc.akka.leader

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import math._

import java.net.{DatagramPacket, DatagramSocket, InetAddress}


import akka.actor.{Props, Actor, ActorRef, ActorSystem}


object PdActor {
  case class PdMidiNote (pitch: Int, velocity: Int, dur: Int, at: Int)
}

//////////////////////////////////////////////////
// Mode 3 : Pure Data (envoi UDP)

class PdActor (host: String) extends Actor{

  import PdActor._
  import DataBaseActor._

  val address = InetAddress.getByName(host)
  val port = 5007
  val socket = new DatagramSocket()

  def receive = {
    case Measure(l: List[Chord]) =>
      println("Player : received measure (Pure Data mode)")
      l.foreach { chord =>
        chord.notes.foreach { note =>
          self ! PdMidiNote(note.pitch, note.vol, note.dur, chord.date)
        }
      }

    case PdMidiNote (pitch, velocity, dur, at)  => {
      context.system.scheduler.scheduleOnce (at milliseconds) {
        pdnote_on(pitch, velocity, dur)
      }
  }

  def pdnote_on (pitch: Int, velocity: Int, dur: Int): Unit = {
    val message = "note " + pitch + " " + velocity + " " + dur + ";";
    val buffer = message.getBytes("UTF-8")
    val packet = new DatagramPacket(buffer, buffer.length, address, port)
    socket.send(packet)
    socket.send(packet)
  }

}
}