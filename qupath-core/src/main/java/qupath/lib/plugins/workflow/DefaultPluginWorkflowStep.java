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

import java.io.ObjectStreamException;
import java.util.Collections;
import java.util.Map;

import qupath.lib.common.GeneralTools;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.workflow.ScriptableWorkflowStep;


/**
 * Old plugin workflow step... unnecessarily stored (and serialized) Class object - which was susceptible to ClassNotFoundExceptions on
 * refactoring, which compromised loading the entire workflow.
 * <p>
 * Replaced by (similarly non-excellent, but better) SimplePluginWorkflowStep.
 * <p>
 * This is only kept for backwards compatibility - should not be used in new code!
 * 
 * @author Pete Bankhead
 *
 */
@Deprecated
class DefaultPluginWorkflowStep implements ScriptableWorkflowStep {
	
	private static final long serialVersionUID = 1L;
	
	private String name;
	private Class<? extends PathPlugin<?>> pluginClass;
	private String arg;
	private String scriptBefore; // Script to insert before plugin is called (including any newlines etc)
	private String scriptAfter; // Script to insert after plugin is called

	
	DefaultPluginWorkflowStep(final String name, final Class<? extends PathPlugin<?>> pluginClass, final String arg) {
		this(name, pluginClass, arg, null, null);
	}
	
	DefaultPluginWorkflowStep(final String name, final Class<? extends PathPlugin<?>> pluginClass, final String arg, final String scriptBefore, final String scriptAfter) {
		this.name = name;
		this.pluginClass = pluginClass;
		this.arg = arg;
		this.scriptBefore = scriptBefore;
		this.scriptAfter = scriptAfter;
	}
	
	@Override
	public String getName() {
		return name;
	}


	@Override
	public Map<String, ?> getParameterMap() {
		if (arg == null || arg.trim().length() == 0)
			return Collections.emptyMap();
		
		// Try to parse as an argument string
		try {
			return GeneralTools.parseArgStringValues(arg);
		} catch (Exception e) {
			return Collections.singletonMap("Argument", arg);
		}
	}
	
	// Magic method to update to new form
	private Object readResolve() throws ObjectStreamException {
		return new SimplePluginWorkflowStep(name, pluginClass, arg, scriptBefore, scriptAfter);
	}
	

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n");
		sb.append(pluginClass.getName()).append("  ")
		.append(arg);
		return sb.toString();
	}
	

	@Override
	public String getScript() {
		StringBuilder sb = new StringBuilder();
		if (scriptBefore != null)
			sb.append(scriptBefore);
		sb.append("runPlugin(").
			append("'").
			append(pluginClass.getName()).
			append("', ").
			append("'").
			append(arg).
			append("'").
			append(");");
		if (scriptAfter != null)
			sb.append(scriptAfter);
		return sb.toString();
	}
	
}