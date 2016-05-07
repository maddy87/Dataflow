package com.dataflow.workers;

import org.apache.commons.lang3.reflect.MethodUtils;

import com.dataflow.messages.ConnectionComplete;
import com.dataflow.messages.RegisterWorker;
import com.dataflow.messages.WorkIsReady;
import com.dataflow.messages.WorkRequest;
import com.dataflow.messages.WorkToBeDone;
import com.dataflow.workers.WorkerActor.WorkerState;

import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.SupervisorStrategy.Directive;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.japi.Function;
import akka.remote.RemoteActorRef;
import scala.concurrent.duration.Duration;

public class WorkerManager extends UntypedActor{

	private RemoteActorRef nameServer;
	private ActorRef workerActor;
	
	public WorkerManager() {
		this.workerActor = createWorkerActor();
	}
	
	private ActorRef createWorkerActor(){
		ActorRef worker = getContext().actorOf(Props.create(WorkerActor.class)
				.withDispatcher("pool-dispatcher"));
	    getContext().watch(worker);
	    return worker;
	}
	
	private SupervisorStrategy strategy = new OneForOneStrategy(10, 
			Duration.create(5, "seconds"), 
			new Function<Throwable, Directive>() {
	
				@Override
				public Directive apply(Throwable throwable) throws Exception {
					return SupervisorStrategy.restart();
				}
			});
	
	@Override
	public SupervisorStrategy supervisorStrategy() {
		return strategy;
	}
	
	@Override
	public void onReceive(Object msg) throws Exception {
		 MethodUtils.invokeExactMethod(this, "handle", msg);
	}
	
	public void handle(ConnectionComplete complete){
		nameServer = complete.getNameServer();
		RegisterWorker register = new RegisterWorker(getContext().parent());
		nameServer.tell(register, getSelf());
	}
	
	public void handle(WorkIsReady workReady){
		WorkRequest workReq = new WorkRequest(getContext().parent());
		getSender().tell(workReq, getSelf());
	}

	public void handle(WorkToBeDone workToDo){
		workerActor.forward(workToDo, getContext());
	}
	
	public void handle(WorkerState state){
		if(state == WorkerState.IDLE){
			WorkRequest workReq = new WorkRequest(getContext().parent());
			nameServer.tell(workReq, getSelf());
		}
	}
	
	public void handle(Terminated terminated){
		workerActor = createWorkerActor();
	}
}