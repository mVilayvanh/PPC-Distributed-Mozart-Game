package upmc.akka.leader

import scala.language.postfixOps
import akka.actor._
import upmc.akka.leader.DataBaseActor.Measure

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.math.random

object Musician {
  case object Start

  case object Play

  // Messages sent between remote musicians
  // conductorId = -1 means "no conductor known yet"
  case class Heartbeat(id: Int, conductorId: Int)

  case class PlayMeasure(measure: Measure)

  // Internal scheduling messages
  case object SendHeartbeats

  case object CheckHealth

  // Delayed self-check: become conductor only if no one else has claimed the role
  case object ConductorCheck

  def runtwoDice(): Int = {
    val dice1 = (random * 6).toInt + 1
    val dice2 = (random * 6).toInt + 1
    dice1 + dice2
  }
}

class Musician(val id: Int, val terminaux: List[Terminal]) extends Actor {

  import Musician._

  val displayActor: ActorRef = context.actorOf(Props[DisplayActor], name = "displayActor")
  val provider: ActorRef = context.actorOf(Props(new Provider()), "Provider")

  val OCTAVE_SHIFT = 12
  // player type attribution
  val player: ActorRef = id match {
    case 1 => context.actorOf(Props(new PlayerActor()), "Player")
    case 2 => context.actorOf(Props(new PlayerActor(p => p + OCTAVE_SHIFT)), "Player")
    case 3 => context.actorOf(Props(new PdActor("127.0.0.1")), "Player")
    case _ => context.actorOf(Props(new PlayerActor()), "Player")
  }

  // Timing constants
  val HEARTBEAT_INTERVAL = 1.second
  val HEALTH_CHECK_INTERVAL = 2.seconds
  val PLAY_INTERVAL = 1800.milliseconds
  val MAX_WAIT_SECONDS = 30
  val MAX_MISSED_PINGS = 5

  // How long to wait for existing conductor heartbeats before claiming the role
  val CONDUCTOR_CHECK_DELAY = 3.seconds

  // Peer health tracking: id -> missed heartbeat count
  private var peerHealth: Map[Int, Int] = Map.empty
  // Musicians we've ever heard from (for leader election)
  private var knownMusicians: Set[Int] = Set.empty

  // State — -1 means "no conductor known yet"
  private var isConductor: Boolean = false
  private var currentConductorId: Int = -1
  private var waitingSeconds: Int = 0
  private var roundRobinIndex: Int = 0

  // Scheduled tasks
  private var heartbeatTask: Option[Cancellable] = None
  private var healthCheckTask: Option[Cancellable] = None

  // Remote actor selections for all other musicians
  private lazy val remoteMusicians: Map[Int, ActorSelection] = {
    terminaux.filterNot(_.id == id).map { t =>
      val ip = t.ip.replace("\"", "") // strip quotes from config render()
      t.id -> context.actorSelection(
        s"akka.tcp://MozartSystem${t.id}@$ip:${t.port}/user/Musician${t.id}"
      )
    }.toMap
  }

  override def postStop(): Unit = {
    heartbeatTask.foreach(_.cancel())
    healthCheckTask.foreach(_.cancel())
  }

  // === Helpers ===

  private def aliveOthers: List[Int] = peerHealth.keys.toList.sorted

  private def updateHealth(): Unit = {
    peerHealth = peerHealth.map { case (pid, missed) => pid -> (missed + 1) }
    val dead = peerHealth.filter(_._2 >= MAX_MISSED_PINGS).keys.toList
    dead.foreach { pid =>
      peerHealth -= pid
      displayActor ! Message(s"Musician $id: Musician $pid seems dead")
    }
  }

  // Update peer health and learn conductor from the sender's point of view.
  private def handleHeartbeat(fromId: Int, reportedConductorId: Int): Unit = {
    peerHealth = peerHealth.updated(fromId, 0)
    knownMusicians += fromId
    // Accept the reported conductor only if we don't know one yet
    if (currentConductorId == -1 && reportedConductorId >= 0) {
      currentConductorId = reportedConductorId
      displayActor ! Message(s"Musician $id: Learned that Musician $reportedConductorId is conductor")
    }
  }

  private def checkLeaderElection(): Unit = {
    if (!isConductor) {
      val conductorKnownAndDead =
        currentConductorId >= 0 &&
          knownMusicians.contains(currentConductorId) &&
          !aliveOthers.contains(currentConductorId)
      if (conductorKnownAndDead) {
        val candidates = (aliveOthers :+ id).sorted
        val newConductor = candidates.head
        currentConductorId = newConductor
        if (newConductor == id) {
          becomeConductor()
        } else {
          displayActor ! Message(s"Musician $id: Musician $newConductor is the new conductor")
        }
      }
    }
  }

  private def becomeConductor(): Unit = {
    isConductor = true
    currentConductorId = id
    displayActor ! Message(s"Musician $id: I am now the conductor!")

    val others = aliveOthers
    if (others.nonEmpty) {
      displayActor ! Message(s"Conductor $id: Musicians available: $others. Let the music begin!")
      context.become(conductorPlaying)
      self ! Play
    } else {
      waitingSeconds = 0
      context.become(conductorWaiting)
    }
  }

  // === BEHAVIORS ===

  // --- Initial: handle Start ---
  def receive: Receive = {
    case Start =>
      displayActor ! Message(s"Musician $id is created")

      heartbeatTask = Some(context.system.scheduler.schedule(
        Duration.Zero, HEARTBEAT_INTERVAL, self, SendHeartbeats
      ))

      healthCheckTask = Some(context.system.scheduler.schedule(
        2.seconds, HEALTH_CHECK_INTERVAL, self, CheckHealth
      ))

      // All musicians (including id=0) enter musicianBehavior and wait
      // CONDUCTOR_CHECK_DELAY before claiming conductor. During this window,
      // heartbeats from an already-running conductor propagate conductorId,
      // preventing a restarted musician 0 from creating a duplicate conductor.
      context.system.scheduler.scheduleOnce(CONDUCTOR_CHECK_DELAY, self, ConductorCheck)
      displayActor ! Message(s"Musician $id: Waiting for conductor discovery...")
      context.become(musicianBehavior)
  }

  // --- Conductor: waiting for at least 1 musician ---
  def conductorWaiting: Receive = {
    case SendHeartbeats =>
      remoteMusicians.values.foreach(_ ! Heartbeat(id, currentConductorId))

    case Heartbeat(fromId, reportedConductorId) =>
      handleHeartbeat(fromId, reportedConductorId)

    case CheckHealth =>
      updateHealth()
      val others = aliveOthers
      if (others.nonEmpty) {
        displayActor ! Message(s"Conductor $id: Musicians found: $others. Let the music begin!")
        context.become(conductorPlaying)
        self ! Play
      } else {
        waitingSeconds += HEALTH_CHECK_INTERVAL.toSeconds.toInt
        if (waitingSeconds >= MAX_WAIT_SECONDS) {
          displayActor ! Message(s"Conductor $id: No musician arrived in 30s. The show is over.")
          context.system.terminate()
        } else {
          displayActor ! Message(s"Conductor $id: Waiting... (${waitingSeconds}s / ${MAX_WAIT_SECONDS}s)")
        }
      }

    case ConductorCheck => // already conductor, ignore
    case Play => // ignore (stale schedule from previous state)
    case _: Measure => // ignore
    case _: PlayMeasure => // ignore
  }

  // --- Conductor: actively dispatching measures ---
  def conductorPlaying: Receive = {
    case SendHeartbeats =>
      remoteMusicians.values.foreach(_ ! Heartbeat(id, currentConductorId))

    case Heartbeat(fromId, reportedConductorId) =>
      handleHeartbeat(fromId, reportedConductorId)

    case CheckHealth =>
      updateHealth()

    case Play =>
      val others = aliveOthers
      if (others.nonEmpty) {
        val num = Musician.runtwoDice()
        displayActor ! Message(s"Conductor $id: Rolled dice = $num")
        provider ! Provider.GetMeasure(num)
      } else {
        displayActor ! Message(s"Conductor $id: All musicians left! Waiting for new ones...")
        waitingSeconds = 0
        context.become(conductorWaiting)
      }

    case measure: Measure =>
      val others = aliveOthers
      if (others.nonEmpty) {
        val targetId = others(roundRobinIndex % others.size)
        roundRobinIndex += 1
        displayActor ! Message(s"Conductor $id: Sending measure to Musician $targetId")
        remoteMusicians.get(targetId).foreach(_ ! PlayMeasure(measure))
      }
      context.system.scheduler.scheduleOnce(PLAY_INTERVAL, self, Play)

    case ConductorCheck => // already conductor, ignore
    case _: PlayMeasure => // ignore
  }

  // --- Regular musician: receives and plays measures ---
  def musicianBehavior: Receive = {
    case SendHeartbeats =>
      remoteMusicians.values.foreach(_ ! Heartbeat(id, currentConductorId))

    case Heartbeat(fromId, reportedConductorId) =>
      handleHeartbeat(fromId, reportedConductorId)

    case CheckHealth =>
      updateHealth()
      checkLeaderElection()

    case ConductorCheck =>
      // No conductor discovered during the startup window: elect the lowest
      // alive ID (or self if alone).
      if (currentConductorId == -1) {
        val candidates = (aliveOthers :+ id).sorted
        val newConductor = candidates.head
        currentConductorId = newConductor
        if (newConductor == id) {
          displayActor ! Message(s"Musician $id: No conductor found after ${CONDUCTOR_CHECK_DELAY.toSeconds}s, becoming conductor.")
          becomeConductor()
        } else {
          displayActor ! Message(s"Musician $id: No conductor found, Musician $newConductor should be conductor")
        }
      }

    case PlayMeasure(measure) =>
      displayActor ! Message(s"Musician $id: Playing a measure!")
      player ! measure

    case Play => // ignore
    case _: Measure => // ignore
  }
}
