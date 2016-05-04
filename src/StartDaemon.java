import com.dataflow.actors.JobController;
import com.dataflow.actors.WorkerActor;
import com.dataflow.actors.WorkerExec;
import com.dataflow.messages.RegisterWorker;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorRefFactory;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Function;

public class StartDaemon {
	final static int jobPort = 5919;
	final static int workerPort = 5929;

	public static void main(String[] args) throws Exception {
		start();
		Thread.sleep(1000);
		
	}

	private static void start() {
		final Config conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + jobPort)
				.withFallback(ConfigFactory.load());
		final ActorSystem system = ActorSystem.create("JobController", conf);
		final ActorRef actorJ = system.actorOf(Props.create(JobController.class), "JobActor");
		//actorJ.tell("heelllo", ActorRef.noSender());
		
		final Function<ActorRefFactory, ActorRef> maker = new Function<ActorRefFactory, ActorRef>() {
			  @Override 
			  public ActorRef apply(ActorRefFactory f) throws Exception {
			    return f.actorOf(Props.create(WorkerActor.class));
			  }
			};
			
		final ActorSystem wsystem = ActorSystem.create("WorkerSystem");
		final Props props = Props.create(WorkerActor.class);
		final ActorRef actor = wsystem.actorOf(Props.create(WorkerExec.class, maker, actorJ)
												.withDispatcher("control-aware-dispatcher"));
		actor.tell(new RegisterWorker(actor), ActorRef.noSender());
		
	}

}
