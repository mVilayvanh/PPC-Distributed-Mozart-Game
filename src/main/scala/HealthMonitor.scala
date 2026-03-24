package upmc.akka.leader

import akka.actor.{Actor, Cancellable}
import scala.concurrent.duration._

object HealthMonitor {
  final case class Alive(id: Int)
  final case class RegisterMusician(id: Int)
  final case class UnregisterMusician(id: Int)

  case object HowManyMusiciansAreAlive
  final case class MusicianCount(n: Int)

  case object WhoIsAlive
  final case class AliveMusicians(ids: List[Int])

  private case object UpdateMusiciansHealth

  final case class MusicianHealth(
    isAlive: Boolean,
    missedPings: Int
  )
}

class HealthMonitor extends Actor {
  import HealthMonitor._
  import context.dispatcher

  private val periodicPingInterval = 2.seconds
  private val maxMissedPings = 4

  private var musicians: Map[Int, MusicianHealth] = Map.empty
  private var healthUpdateTask: Option[Cancellable] = None

  override def preStart(): Unit = {
    healthUpdateTask = Some(
      context.system.scheduler.schedule(
        Duration.Zero,
        periodicPingInterval,
        self,
        UpdateMusiciansHealth
      )
    )
  }

  override def postStop(): Unit = {
    healthUpdateTask.foreach(_.cancel())
    healthUpdateTask = None
  }

  def receive: Receive = {
    case RegisterMusician(id) =>
      musicians = musicians.updated(
        id,
        MusicianHealth(isAlive = true, missedPings = 0)
      )

    case Alive(id) =>
      musicians = musicians.updated(
        id,
        MusicianHealth(isAlive = true, missedPings = 0)
      )

    case UnregisterMusician(id) =>
      musicians -= id

    case HowManyMusiciansAreAlive =>
      sender() ! MusicianCount(musicians.values.count(_.isAlive))

    case WhoIsAlive =>
      sender() ! AliveMusicians(musicians.filter(_._2.isAlive).keys.toList.sorted)

    case UpdateMusiciansHealth =>
      musicians = musicians.map { case (id, musicianHealth) =>
        val nextMissedPings = musicianHealth.missedPings + 1
        id -> musicianHealth.copy(
          isAlive = nextMissedPings < maxMissedPings,
          missedPings = nextMissedPings
        )
      }
  }
}
