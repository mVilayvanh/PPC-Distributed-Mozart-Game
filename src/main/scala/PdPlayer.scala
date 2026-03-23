
package upmc.akka.mozart

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global
import math._

import java.net.{DatagramPacket, DatagramSocket, InetAddress}


import akka.actor.{Props, Actor, ActorRef, ActorSystem}


object PdActor {
  case class PdMidiNote (pitch:Int, at:Int) 
}

//////////////////////////////////////////////////

class PdActor (host: String) extends Actor{
 
  import PdActor._

  val address = InetAddress.getByName(host)
  val port = 5007
  val socket = new DatagramSocket()
  //socket.close()

  def receive = {
    case PdMidiNote (pitch:Int, at:Int)  => {
      context.system.scheduler.scheduleOnce ((at) milliseconds) (pdnote_on (pitch))
      }
  }

  def pdnote_on (pitch:Int): Unit = {
    val message = "" + pitch
    val buffer = message.getBytes("UTF-8") 
    val packet = new DatagramPacket(buffer, buffer.length, address, port)
    socket.send(packet)
    socket.send(packet)
  }
}

