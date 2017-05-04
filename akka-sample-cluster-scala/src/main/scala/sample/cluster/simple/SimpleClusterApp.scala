package sample.cluster.simple

import com.typesafe.config.ConfigFactory
import akka.actor.ActorSystem
import akka.actor.Props
import akka.cluster.Cluster
import com.typesafe.conductr.bundlelib.akka.{Env, StatusService}
import com.typesafe.conductr.lib.akka.ConnectionContext

import scala.concurrent.{ Await, duration }
import scala.util.Try

object SimpleClusterApp {
  def main(args: Array[String]): Unit = {
    val config = Env.asConfig
    val bundleSystem = sys.env.getOrElse("BUNDLE_SYSTEM", "ClusterSystem")
    val bundleSystemVersion = sys.env.getOrElse("BUNDLE_SYSTEM_VERSION", "1")
    val systemName = s"${Env.mkSystemId(bundleSystem)}-${Env.mkSystemId(bundleSystemVersion)}"
    implicit val system = ActorSystem(systemName, config.withFallback(ConfigFactory.load()))

    // Create an actor that handles cluster domain events
    system.actorOf(Props[SimpleClusterListener], name = "clusterListener")

    Cluster(system).registerOnMemberRemoved {
      // exit JVM with a non-zero exit code when ActorSystem has been terminated
      system.registerOnTermination(System.exit(-1))
      // shut down ActorSystem
      system.terminate()

      // In case ActorSystem shutdown takes longer than 10 seconds,
      // exit the JVM forcefully.
      // We must spawn a separate thread to not block current thread,
      // since that would have blocked the shutdown of the ActorSystem.
      new Thread {
        override def run(): Unit = {
          import duration._
          if (Try(Await.ready(system.whenTerminated, 10.seconds)).isFailure)
            System.exit(-1)
        }
      }.start()
    }

    implicit val cc = ConnectionContext()
    StatusService.signalStartedOrExit()
  }

}

