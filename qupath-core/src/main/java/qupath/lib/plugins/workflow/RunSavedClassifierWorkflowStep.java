/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.plugins.workflow;

import java.util.Collections;
import java.util.Map;

import qupath.lib.common.GeneralTools;
import qupath.lib.plugins.workflow.ScriptableWorkflowStep;

/**
 * WorkflowStep for running a saved PathObjectClassifier.
 * 
 * @author Pete Bankhead
 *
 */
public class RunSavedClassifierWorkflowStep implements ScriptableWorkflowStep {
	
	private static final long serialVersionUID = 1L;
	
	private String name;
	private String classifierPath;

	/**
	 * Create a workflow step to run a classifier.
	 * 
	 * @param name step name for display, to identify the purpose of the step (not the classifier)
	 * @param classifierPath path to the serialized classifier
	 */
	public RunSavedClassifierWorkflowStep(final String name, final String classifierPath) {
		this.name = name;
		this.classifierPath = GeneralTools.escapeFilePath(classifierPath);
	}
	
	/**
	 * Constructor, taking the path to the serialized classifier.
	 * @param classifierPath
	 */
	public RunSavedClassifierWorkflowStep(final String classifierPath) {
		this("Run object classifier", classifierPath);
	}
	
	@Override
	public String getName() {
		return name;
	}

	
	@Override
	public Map<String, ?> getParameterMap() {
		return Collections.singletonMap("Path", classifierPath);
	}
	

	@Override
	public String toString() {
		return getName() + "\t" + getScript();
	}
	
	
	@Override
	public String getScript() {
		return "runClassifier('" + classifierPath + "');";
	}
	
	

}
