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

package qupath.lib.plugins.workflow;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A collection of steps that relate to how an image has been - or should be - processed.
 * <p>
 * This can also be used to implement a 'command history'.
 * <p>
 * Where the WorkflowSteps contained in the Workflow are scriptable, then a 
 * script can be created automatically.
 * 
 * @author Pete Bankhead
 *
 */
public class Workflow implements Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	final private static Logger logger = LoggerFactory.getLogger(Workflow.class);
	
	static int version = 1;
	
	transient private List<WorkflowListener> listeners = Collections.synchronizedList(new ArrayList<>());
	
	private List<WorkflowStep> steps = new ArrayList<>();
	
	/**
	 * Get an unmodifiable list of the steps.
	 * 
	 * @return
	 */
	public List<WorkflowStep> getSteps() {
		return Collections.unmodifiableList(steps);
	}
	
	/**
	 * Get the last WorkflowStep in the workflow, or null if no steps are available.
	 * 
	 * @return
	 */
	public WorkflowStep getLastStep() {
		return steps.isEmpty() ? null : steps.get(steps.size()-1);
	}
	
	// Version that checks if the new step is different from the previous one
//	public synchronized void addStep(final WorkflowStep step) {
//		WorkflowStep lastStep = getLastStep();
//		if (lastStep != null && lastStep.equals(step))
//			steps.remove(steps.size() - 1);
//		steps.add(step);
//		fireWorkflowUpdatedEvent();
//	}
	
	/**
	 * Append a new step to the end of the workflow, firing an update event.
	 * @param step
	 */
	public synchronized void addStep(final WorkflowStep step) {
		steps.add(step);
		fireWorkflowUpdatedEvent();
	}
	
	/**
	 * Append multiple steps to the end of the workflow, firing a single update event.
	 * @param steps
	 */
	public synchronized void addSteps(final Collection<WorkflowStep> steps) {
		this.steps.addAll(steps);
		fireWorkflowUpdatedEvent();
	}
	
	/**
	 * Remove a single step, identified by its list index.
	 * @param ind
	 */
	public synchronized void removeStep(final int ind) {
		if (steps.remove(ind) != null)
			fireWorkflowUpdatedEvent();
	}

	/**
	 * Remove a single step, firing an update event if the step was successfully removed.
	 * @param step
	 */
	public synchronized void removeStep(final WorkflowStep step) {
		if (steps.remove(step))
			fireWorkflowUpdatedEvent();
	}
	
	/**
	 * Remove a collection of steps, firing an update event if the workflow was changed.
	 * @param steps
	 */
	public synchronized void removeSteps(final Collection<WorkflowStep> steps) {
		if (this.steps.removeAll(steps))
			fireWorkflowUpdatedEvent();
	}
	
	/**
	 * Replace the most recently added step with this one.
	 * @param step
	 */
	public synchronized void replaceLastStep(final WorkflowStep step) {
		if (!steps.isEmpty())
			steps.remove(steps.size()-1);
		addStep(step);
	}
	
	/**
	 * Total number of steps in the workflow.
	 * @return
	 */
	public int size() {
		return steps.size();
	}
	
	/**
	 * Returns true if the workflow does not contain any steps.
	 * @return
	 */
	public boolean isEmpty() {
		return steps.isEmpty();
	}
	
	/**
	 * Remove all steps, firing an update event if the workflow was not previously empty.
	 */
	public void clear() {
		if (isEmpty())
			return;
		steps.clear();
		fireWorkflowUpdatedEvent();
	}
	
	protected void fireWorkflowUpdatedEvent() {
		for (WorkflowListener listener : listeners)
			listener.workflowUpdated(this);
	}
	
	/**
	 * Add a listener for changes to the workflow.
	 * @param listener
	 */
	public void addWorkflowListener(WorkflowListener listener) {
		if (listeners == null)
			listeners = new Vector<>();
		this.listeners.add(listener);
	}
	
	/**
	 * Remove a listener for changes to the workflow.
	 * @param listener
	 */
	public void removeWorkflowListener(WorkflowListener listener) {
		this.listeners.remove(listener);
	}
	
	/**
	 * Generate a script from the current workflow steps.
	 * @return
	 */
	public String createScript() {
		StringBuilder sb = new StringBuilder();
		
		// TODO: REIMPORT INITIALIZATION SCRIPT!!!!!!
//		// Import scripting class
//		sb.append("// Initialization\n");
//		sb.append("var ").append(QP.class.getSimpleName()).append(" = ").
//			append("Packages.").
//			append(QP.class.getName()).
//			append(";");
//		sb.append("\n\n");
//		sb.append("// Processing\n");
		
		// Add all scriptable steps, and put comments in where a step isn't scriptable
		for (WorkflowStep step : steps) {
			if (step instanceof ScriptableWorkflowStep)
				sb.append(((ScriptableWorkflowStep)step).getScript());
			else
				sb.append("// ").append(step.getName()).append(" is not scriptable");
			sb.append("\n");
		}
		return sb.toString();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(version);
		out.writeInt(steps.size());
		for (WorkflowStep step : steps)
			out.writeObject(step);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int version = in.readInt();
		if (version != 1)
			logger.error(getClass().getSimpleName() + " unsupported version number " + version);
		
		int nSteps = in.readInt();
		steps = new ArrayList<>(nSteps * 2);
		for (int i = 0; i < nSteps; i++) {
			// Due to refactoring, it may be that some steps can't be read... in this case, try to continue
			try {
				WorkflowStep step = (WorkflowStep)in.readObject();
				steps.add(step);
				logger.trace(step.toString());
			} catch (ClassNotFoundException e) {
				logger.error("Cannot load workflow step - {}", e.getMessage());
			}
		}
	}

	
}
