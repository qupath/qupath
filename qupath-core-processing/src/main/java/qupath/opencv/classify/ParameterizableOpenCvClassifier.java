/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.opencv.classify;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import org.bytedeco.opencv.opencv_ml.StatModel;
import org.bytedeco.opencv.opencv_core.TermCriteria;

import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.parameters.Parameterizable;

/**
 * Abstract base class useful for creating OpenCV classifiers that can be modified depending 
 * on the values of several parameters.
 * <p>
 * This takes care of some of the effort involved in representing and storing the parameters.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public abstract class ParameterizableOpenCvClassifier<T extends StatModel> extends OpenCvClassifier<T> implements Parameterizable {
	
	private static final long serialVersionUID = -2835804394727597290L;
	
	transient private ParameterList paramsPrevious; // Cached parameters - used to help assess whether the classifier has already been trained on identical data
	private ParameterList params; // Parameters currently in use
	
	/**
	 * Create a ParameterList containing the parameters that can be adjusted for the classifier.
	 * 
	 * @return
	 */
	protected abstract ParameterList createParameterList();
	
	@Override
	public ParameterList getParameterList() {
		if (params == null)
			params = createParameterList();
		return params;
	}
	
	
	/**
	 * Use cached parameters from last training to assess whether parameters have changed at all.
	 */
	@Override
	protected boolean classifierOptionsChanged() {
		return ParameterList.equalParameters(paramsPrevious, params);
	}
	
	@Override
	protected void createAndTrainClassifier() {
		this.paramsPrevious = params == null ? null : params.duplicate();
		super.createAndTrainClassifier();
	}
	

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		// Store parameters first, as these will be used during classifier creation
		out.writeLong(2); // Version number (may serialize differently in the future)
		out.writeObject(params);
		super.writeExternal(out);
	}


	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		long v = in.readLong();
		if (v == 2)
			params = (ParameterList)in.readObject();
		super.readExternal(in);
	}
	
	
	
	/**
	 * Create TermCriteria using either or both of specified maxIterations and termEPS, or return null if both <= 0.
	 * 
	 * @param maxIterations
	 * @param termEPS
	 * @return
	 */
	static TermCriteria createTerminationCriteria(final int maxIterations, final double termEPS) {
		if (maxIterations > 0) {
			if (termEPS > 0)
				return new TermCriteria(TermCriteria.COUNT + TermCriteria.EPS, maxIterations, termEPS);
			else
				return new TermCriteria(TermCriteria.COUNT, maxIterations, 0);
		} else if (termEPS > 0)
			return new TermCriteria(TermCriteria.EPS, 50, termEPS);
		return null;
	}
	
}
