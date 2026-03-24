package upmc.akka.leader

import javax.sound.midi._
import javax.sound.midi.ShortMessage._
import akka.actor.Actor
import scala.concurrent.duration._

object PlayerActor {
  case class MidiNote(pitch: Int, vel: Int, dur: Int, at: Int)

  val synth: Synthesizer = MidiSystem.getSynthesizer()
  synth.open()
  val rcvr: Receiver = synth.getReceiver()

  def note_on(pitch: Int, vel: Int, chan: Int): Unit = {
    val msg = new ShortMessage
    msg.setMessage(NOTE_ON, chan, pitch, vel)
    rcvr.send(msg, -1)
  }

  def note_off(pitch: Int, chan: Int): Unit = {
    val msg = new ShortMessage
    msg.setMessage(NOTE_OFF, chan, pitch, 0)
    rcvr.send(msg, -1)
  }
}

class PlayerActor(pitchTransform: Int => Int = identity) extends Actor {
  import DataBaseActor._
  import PlayerActor._
  import context.dispatcher

  private val channel = 10

  def receive: Receive = {
    case Measure(chords) =>
      println("Player : received measure")
      chords.foreach { chord =>
        chord.notes.foreach { note =>
          val newPitch = pitchTransform(note.pitch).max(0).min(127)
          self ! MidiNote(newPitch, note.vol, note.dur, chord.date)
        }
      }

    case MidiNote(p, v, d, at) =>
      context.system.scheduler.scheduleOnce(at.milliseconds) {
        note_on(p, v, channel)
      }
      context.system.scheduler.scheduleOnce((at + d).milliseconds) {
        note_off(p, channel)
      }
  }
}