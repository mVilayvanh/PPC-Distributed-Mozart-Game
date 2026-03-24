
package upmc.akka.leader

import math._

import javax.sound.midi._
import javax.sound.midi.ShortMessage._

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import ExecutionContext.Implicits.global

import akka.actor.{Props, Actor, ActorRef, ActorSystem}


object PlayerActor {
  case class MidiNote (pitch:Int, vel:Int, dur:Int, at:Int)
  val synth = MidiSystem.getSynthesizer()
  synth.open()
  val rcvr = synth.getReceiver()


/////////////////////////////////////////////////
def note_on (pitch:Int, vel:Int, chan:Int): Unit = {
    val msg = new ShortMessage
    msg.setMessage(NOTE_ON, chan, pitch, vel)
    rcvr.send(msg, -1)
}

def note_off (pitch:Int, chan:Int): Unit = {
    val msg = new ShortMessage
    msg.setMessage(NOTE_ON, chan, pitch, 0)
    rcvr.send(msg, -1)
}

}

//////////////////////////////////////////////////

class PlayerActor () extends Actor{
  import DataBaseActor._
  import PlayerActor._

  def receive = {
    case Measure (l:List[Chord]) => {
      println("Player : received measure (normal mode)")
      l.foreach( n => {
        n.notes.foreach(note => {
          self ! MidiNote(note.pitch, note.vol, note.dur, n.date)
        }
        )


      })
    }
    case MidiNote(p,v, d, at) => {
      context.system.scheduler.scheduleOnce ((at) milliseconds) (note_on (p,v,10))
      context.system.scheduler.scheduleOnce ((at+d) milliseconds) (note_off (p,10))
    }
  }
}

//////////////////////////////////////////////////
// Mode 2 : transposition d'une octave (+12 demi-tons)

class OctavePlayerActor () extends Actor{
  import DataBaseActor._
  import PlayerActor._

  val OCTAVE_SHIFT = 12

  def receive = {
    case Measure (l:List[Chord]) => {
      println("Player : received measure (octave transposition mode)")
      l.foreach( n => {
        n.notes.foreach(note => {
          val transposedPitch = math.min(note.pitch + OCTAVE_SHIFT, 127)
          self ! MidiNote(transposedPitch, note.vol, note.dur, n.date)
        })
      })
    }
    case MidiNote(p,v, d, at) => {
      context.system.scheduler.scheduleOnce ((at) milliseconds) (note_on (p,v,10))
      context.system.scheduler.scheduleOnce ((at+d) milliseconds) (note_off (p,10))
    }
  }
}
