package upmc.akka.leader

import akka.actor._

case class Alive(id : Integer)
case class HowManyMusiciansAreAlive()
case class MusicianCount(n : Integer)

class HealthMonitor extends Actor {
  private var musicians = List(-1, -1, -1, -1)

  def receive = {
    case Alive(id) =>
      musicians = musicians.updated(id, 1)
    case HowManyMusiciansAreAlive =>
      sender ! MusicianCount(musicians.count(m => m == 1))
  }

}
