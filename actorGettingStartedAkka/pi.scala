// we will have a master actor initiating the computation
// creating a set of worker actors
// then split the work among the worker actors
// in a round robin fashion

// import actor files
import akka.actor._
import akka.routing.RoundRobinRouter
import scala.concurrent.duration._

// create the singleton that will run from command line
object Pi extends App {
  calculate(nrOfWorkers = 4, nrOfElements = 10000, nrOfMessages = 10000)

// create the messages
// we need four different types of messages:
// Calculate - sent to Master to start the calculation
// Work - sent from Master actor to the Worker actors containing the work assignment
// Result - send from the Worker actors to the Master actor
// PiApproximation - sent from Master actor to the Listener actor containing the final pi result and how long it took to calculate
// Messages should be immutable so we use scala case classes

sealed trait PiMessage
case object Calculate extends PiMessage
case class Work(start: Int, nrOfElements: Int) extends PiMessage
case class Result(value: Double) extends PiMessage
case class PiApproximation(pi: Double, duration: Duration)

// creating the worker
class Worker extends Actor {
  def receive = {
    case Work(start, nrOfElements) =>
      sender ! Result(calculatePiFor(start, nrOfElements)) // perform the work
  }

  def calculatePiFor(start: Int, nrOfElements: Int): Double = {
    var acc = 0.0
    for(i <- start until (start + nrOfElements))
      acc += 4.0 * (1-(i % 2) * 2) / (2 * i + 1)
    acc
  }
}

// creating the master
class Master(nrOfWorkers: Int, nrOfMessages: Int, nrOfElements: Int, listener: ActorRef) extends Actor {
  var pi: Double = _
  var nrOfResults: Int = _
  val start: Long = System.currentTimeMillis

  // create the router
  // this router is representing all workers in a single abstraction
  val workerRouter = context.actorOf(
    Props[Worker].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workerRouter")

  def receive = {
  // handle the messages received
  case Calculate =>
    for(i <- 0 until nrOfMessages) workerRouter ! Work(i * nrOfElements, nrOfElements)

  case Result(value) =>
    pi += value
    nrOfResults += 1
    if(nrOfResults == nrOfMessages) {
      // send the results to the listener
      listener ! PiApproximation(pi, duration = (System.currentTimeMillis - start).millis)
      // stop this actor and all its supervised children
      context.stop(self)
    }
  }
}

// creating the result listener
class Listener extends Actor {
  def receive = {
    case PiApproximation(pi, duration) =>
      println("\n\tPi approximation: \t\t%s\n\tCalculation time: \t%s".format(pi, duration))
      context.system.shutdown()
  }
}

  ////////////////////////////////////////////////
  // calculate function will start it all
  def calculate(nrOfWorkers: Int, nrOfElements: Int, nrOfMessages: Int) {

    // create an akka system
    val system = ActorSystem("PiSystem")

    // create the result listener
    val listener = system.actorOf(Props[Listener], name = "listener")

    // create the master
    val master = system.actorOf(Props(new Master(
        nrOfWorkers, nrOfMessages, nrOfElements, listener)),
        name = "master")

    // send master the calculate message
    master ! Calculate
  }
  ////////////////////////////////////////////////

}
