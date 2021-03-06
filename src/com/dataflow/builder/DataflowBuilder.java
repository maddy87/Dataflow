package com.dataflow.builder;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.lang.reflect.Constructor;

import com.dataflow.io.Collector;
import com.dataflow.io.InputFormat;
import com.dataflow.utils.ConnectorType;
import com.dataflow.vertex.AbstractVertex;
import com.dataflow.vertex.InputVertex;
import com.dataflow.vertex.VertexList;

public final class DataflowBuilder {

	public void mapPointWise(VertexList source, VertexList destination, ConnectorType type) 
			throws BuilderException, IOException {
		
		final int nbrInputs = source.size();
		final int nbrOutputs = destination.size();
		
		if (nbrOutputs == 1) {
			AbstractVertex outputVertex = destination.get(0);
			final int inputs = outputVertex.getInput().size();
			outputVertex.getInput().addEdges(inputs + nbrInputs);
			
			for (int i = 0; i < nbrInputs; i++) {
				AbstractVertex inputVertex = source.get(i);
				final int outputs = inputVertex.getOutput().size();
				inputVertex.getOutput().addEdges(1);
				inputVertex.connectOutput(outputs, outputVertex, inputs + i, type);
			}
			
		} else if (nbrInputs == 1) {
			AbstractVertex inputVertex = source.get(0);
			final int outputs = inputVertex.getOutput().size();
			inputVertex.getOutput().addEdges(outputs + nbrOutputs);
			for (int i = 0; i < nbrOutputs; i++) {
				AbstractVertex outputVertex = destination.get(i);
				final int inputs = outputVertex.getInput().size();
				outputVertex.getInput().addEdges(inputs + 1);
				inputVertex.connectOutput(outputs + i, outputVertex, inputs, type);
			}

		} else if (nbrInputs == nbrOutputs) {
			for (int i = 0; i < nbrInputs; i++) {
				AbstractVertex inputVertex = source.get(i);
				AbstractVertex outputVertex = destination.get(i);
				final int outputs = inputVertex.getOutput().size();
				final int inputs = outputVertex.getInput().size();
				inputVertex.getOutput().addEdges(outputs + 1);
				outputVertex.getInput().addEdges(inputs + 1);
				inputVertex.connectOutput(outputs, outputVertex, inputs, type);
			}
		} else {
			throw new BuilderException("Incorrect Mapping of source and destination");
		}
	}

	public void crossProduct(VertexList source, VertexList destination, ConnectorType type) {
		final int nbrSrc = source.size();
		final int nbrDest = destination.size();

		for (int i = 0; i < nbrSrc; i++) {
			source.get(i).getOutput().setNumberOfEdges(nbrDest);
		}

		for (int i = 0; i < nbrDest; i++) {
			AbstractVertex outputVertex = destination.get(i);
			final int inputs = outputVertex.getInput().size();
			outputVertex.getInput().addEdges(nbrSrc);
			for (int j = 0; j < nbrSrc; j++) {
				AbstractVertex inp = source.get(j);
				inp.connectOutput(i, outputVertex, i, type);
			}
		}
	}

	public VertexList createVertexSet(AbstractVertex prototype, final int copies) {
		VertexList output = new VertexList();
		for (int i = 0; i < copies; i++) {
			AbstractVertex vertex = prototype.makeClone();
			output.add(vertex);
		}
		return output;
	}
	
	public VertexList createVertexSet(Class<? extends AbstractVertex> prototype, final int copies) {
		
		VertexList output = new VertexList();
		for (int i = 0; i < copies; i++) {
			AbstractVertex vertex=null;
			try {
				vertex = prototype.newInstance();
			} catch (InstantiationException e) {
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				e.printStackTrace();
			}
			output.add(vertex);
		}
		return output;
	}

	
	public VertexList createInputVertex(int n, File file, Class<? extends InputFormat<?>> format) {
		Constructor<? extends InputFormat<?>> cons;
		try {
			cons = format.getConstructor(File.class);
		} catch (NoSuchMethodException | SecurityException err) {
			throw new RuntimeException("Error while creating an input constructor");
		}
		InputFormat inputFormat = null;
		try {
			inputFormat = (InputFormat) cons.newInstance(file);
		} catch (Exception e) {
			
		}
		AbstractVertex prototype = new InputVertex(inputFormat);
		return createVertexSet(prototype, n);
	}	
}
