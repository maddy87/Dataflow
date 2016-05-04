package com.dataflow.scheduler;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.UUID;
import java.util.stream.Collectors;

import com.dataflow.edges.Edge;
import com.dataflow.io.InputFormat;
import com.dataflow.io.OutputFormat;
import com.dataflow.vertex.AbstractVertex;
import com.dataflow.vertex.AbstractVertex.VertexType;
import com.dataflow.vertex.VertexList;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;

/**
 * Define the job class. If the job
 * 
 * @author sumanbharadwaj
 *
 */
@SuppressWarnings("rawtypes")
public class DataFlowJob {

	final StageList stageList;
	private Class<? extends InputFormat> inputFormat;
	private File file;
	private File outFile;
	private Class<? extends OutputFormat> outputFormat;
	private InputFormat instanceOfInputFormat;
	private OutputFormat instanceOfOutputFormat;
	protected final String jobId = UUID.randomUUID().toString();

	public DataFlowJob() {
		stageList = new StageList();
	}

	public String getJobId() {
		return jobId;
	}

	/**
	 * Set Input Format class name
	 * 
	 * @return InputFormat
	 */
	public void setInputFormat(Class<? extends InputFormat> inf) {
		this.inputFormat = inf;
	}

	/**
	 * Input Format for the file that needs to be read.
	 * 
	 * @return InputFormat
	 */
	public InputFormat getInputFormat() {
		return instanceOfInputFormat;
	}

	/**
	 * Output Format for a file that needs to be written
	 * 
	 * @return
	 */
	public OutputFormat getOutputFormat() {
		return instanceOfOutputFormat;
	}

	/**
	 * TODO: If time permits change it to a CompletableFuture.
	 * 
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public void run() throws IOException {

		final Config conf = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + 5920)
				.withFallback(ConfigFactory.load());
		ActorSystem actorSystem = ActorSystem.create("ClientSystem", conf);
		ActorSelection actor = actorSystem.actorSelection("akka.tcp://JobController@127.0.0.1:5919/user/JobActor");
		actor.tell(stageList, ActorRef.noSender());
		System.out.println("Finished..");
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
		}
		// system.shutdown();
	}

	Queue<VertexList> queue = new ArrayDeque<>();

	/**
	 * Set up stages
	 * 
	 * @param io
	 *            IO Vertex list
	 */
	public void setRoot(VertexList io) {
		Constructor<? extends InputFormat> cons;
		Constructor<? extends OutputFormat> oCons;
		try {
			cons = inputFormat.getConstructor(File.class);
			oCons = outputFormat.getConstructor(File.class);
		} catch (NoSuchMethodException | SecurityException err) {
			throw new RuntimeException("Error while creating an input constructor");
		}
		try {
			instanceOfInputFormat = (InputFormat) cons.newInstance(file);
			instanceOfOutputFormat = (OutputFormat) oCons.newInstance(outFile);
		} catch (Exception e) {

		}
		queue.add(io);
		while (!queue.isEmpty()) {
			for (AbstractVertex v : queue.poll()) {
				if (v.getVertexType() == VertexType.POINT_WISE)
					stageList.add(getStage(v, new VertexList(), new PointWiseStage(getInputFormat(), jobId)));
				else
					stageList.add(getStage(v, new VertexList(), new CrossProductStage(getInputFormat(), jobId)));
			}
		}
		System.out.println(stageList.size());
	}

	/**
	 * BAD:
	 * 
	 * Stage is effectively final. Do not change it
	 * 
	 * @param rootVertex
	 * @param vList
	 * @param stage
	 * @return
	 */
	private Stage getStage(final AbstractVertex rootVertex, final VertexList vList, final Stage stage) {
		if (rootVertex.getVertexType() == VertexType.SHUFFLE)
			manageShuffle(rootVertex, vList, stage);
		vList.add(rootVertex);

		for (Edge e : rootVertex.getOutput()) {
			getStage(e.getRemoteVertex(), vList, stage);
		}
		return stage;
	}

	private boolean visited = false;

	private Stage manageShuffle(AbstractVertex rootVertex, VertexList vList, Stage stage) {
		vList.add(rootVertex);
		for (AbstractVertex stageVertex : vList) {
			stage.addVertexList(stageVertex);
		}
		if (!visited) {
			createVertexListAndToQueue(rootVertex);
		}
		vList.remove(rootVertex);
		return stage;
	}

	private void createVertexListAndToQueue(AbstractVertex rootVertex) {
		VertexList v = new VertexList();
		rootVertex.getOutput().stream().forEach(e -> v.add(e.getRemoteVertex()));
		queue.add(v);
		visited = true;
	}

	/**
	 * Set Input Path of the file in the Job
	 * 
	 * @param filePath
	 */
	public void setInputPath(String filePath) {
		this.file = new File(filePath);

	}

	public void setOutputPath(String outPath) {
		this.outFile = new File(outPath);
	}

	/**
	 * configure the user specified output format.
	 * 
	 * @param of
	 */
	public void setOutputFormat(Class<? extends OutputFormat> of) {
		this.outputFormat = of;
	}

}
