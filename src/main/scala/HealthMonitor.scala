package upmc.akka.leader

import akka.actor._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

case class Alive(id : Integer)
case class HowManyMusiciansAreAlive()
case class MusicianCount(n : Integer)
case class UpdateMusiciansHealth()

class HealthMonitor extends Actor {
  var musicians = List(-1, -1, -1, -1)
  var musiciansPingCounter = List(0, 0, 0, 0)
  val MusiciansN = 4

  val PERIODIC_PING_INTERVAL = 2000

  context.system.scheduler.schedule(0 milliseconds,PERIODIC_PING_INTERVAL milliseconds, self, UpdateMusiciansHealth)

  def receive: Receive = {
    case Alive(id) =>
      musicians = musicians.updated(id, 1)
      musiciansPingCounter = musiciansPingCounter.updated(id, 0)
    case HowManyMusiciansAreAlive =>
      sender ! MusicianCount(musicians.count(m => m == 1))
    case UpdateMusiciansHealth =>
      for (i <- 0 to MusiciansN) {
        musiciansPingCounter = musiciansPingCounter.updated(i, musiciansPingCounter(i) + 1)
        if (musiciansPingCounter(i) >= 4)
          musicians = musicians.updated(i, -1)
      }
  }

}
