package upmc.akka.ppc

import akka.actor._
import akka.pattern.ask
import akka.util.Timeout
import upmc.akka.ppc.DataBaseActor._

import scala.concurrent.duration._
object Provider{
  case class GetMeasure(num:Int);
}


class Provider(dataBaseActor: ActorRef) extends Actor {
  import Provider._;
  import context.dispatcher

  var measureIndex: Int = 0
  implicit val timeout: Timeout = 2.seconds;
  val menuetTable = Array(
    Array(96,22,141,41,105,122,11,30,70,121,26,9,112,49,109,14),
    Array(32,6,128,63,146,46,134,81,117,39,126,56,174,18,116,83),
    Array(69,95,158,13,153,55,110,24,66,139,15,132,73,58,145,79),
    Array(40,17,113,85,161,2,159,100,90,176,7,34,67,160,52,170),
    Array(148,74,163,45,80,97,36,107,25,143,64,125,76,136,1,93),
    Array(104,157,27,167,154,68,118,91,138,71,150,29,101,162,23,151),
    Array(152,60,171,53,99,133,21,127,16,155,57,175,43,168,89,172),
    Array(119,84,114,50,140,86,169,94,120,88,48,166,51,115,72,111),
    Array(98,142,42,156,75,129,62,123,65,77,19,82,137,38,149,8),
    Array(3,87,165,61,135,47,147,33,102,4,31,164,144,59,173,78),
    Array(54,130,10,103,28,37,106,5,35,20,108,92,12,124,44,131)
  );

  def receive = {
    case GetMeasure(num) => {
      val requester = sender
      println("Provider: received NUMBER: " + num +
        " at INDEX : " + "ABCDEFGH"(measureIndex%8)+"("+measureIndex+")"+" sending to database")
      // dataBaseActor.forward(DataBaseActor.GetMeasure(menuetTable(num - 2)(measureIndex) - 1))
      // on peux aussi utiliser forward
      // mais dans ce cas conductor recevra la reponse de la base de donnée et pas provider
      val measureId = menuetTable(num - 2)(measureIndex) - 1
      incrMeasureIndex();
      // permet de s'assurer garder le même sender que celui qui a envoyé le message à provider
      val future = dataBaseActor ? DataBaseActor.GetMeasure(measureId)
      future.mapTo[Measure].foreach { m =>
        requester ! m
      }
    }
  }

  def incrMeasureIndex(): Unit = {
    measureIndex += 1
    if(measureIndex>=16){
      measureIndex=0;
    }
  }


}

