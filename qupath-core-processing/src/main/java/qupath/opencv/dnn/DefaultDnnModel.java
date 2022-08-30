/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */


package qupath.opencv.dnn;

import java.io.Closeable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Placeholder for the future, in case a default DnnModel implementation is useful.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
class DefaultDnnModel<T> implements DnnModel<T> {
	
	private static final Logger logger = LoggerFactory.getLogger(DefaultDnnModel.class);
	
	private static final Set<String> DEFAULT_NAMES = Set.of(DEFAULT_INPUT_NAME, DEFAULT_OUTPUT_NAME);
	
	private Map<String, BlobFunction<T>> blobFun;
	private PredictionFunction<T> predictFun;
	
	public DefaultDnnModel(BlobFunction<T> blobFun, PredictionFunction<T> predictFun) {
		this(Map.of("default", blobFun), predictFun);
	}
	
	public DefaultDnnModel(Map<String, BlobFunction<T>> blobFun, PredictionFunction<T> predictFun) {
		this.blobFun = new LinkedHashMap<>(blobFun);
		this.predictFun = predictFun;
	}

	
	@Override
	public BlobFunction<T> getBlobFunction() {
		return blobFun.values().iterator().next();
	}
	
	@Override
	public BlobFunction<T> getBlobFunction(String name) {
		if (name == null)
			return getBlobFunction();
		return blobFun.getOrDefault(name, null);
	}
	
	@Override
	public PredictionFunction<T> getPredictionFunction() {
		return predictFun;
	}
	
	/**
	 * Calls {@code close()} on the blob and prediction functions, if they are instances of 
	 * {@link Closeable} or {@link AutoCloseable}.
	 */
	@Override
	public void close() throws Exception {
		logger.debug("Closing {}", this);
		tryToClose(blobFun);
		tryToClose(predictFun);
	}
	
	private void tryToClose(Object o) throws Exception {
		if (o instanceof AutoCloseable)
			((AutoCloseable)o).close();
		else if (o instanceof Closeable)
			((Closeable)o).close();
	}

}
