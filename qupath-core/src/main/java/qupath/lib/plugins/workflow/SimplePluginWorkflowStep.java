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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Map;

import qupath.lib.common.GeneralTools;
import qupath.lib.plugins.PathPlugin;
import qupath.lib.plugins.workflow.ScriptableWorkflowStep;

/**
 * Updated version of DefaultPluginWorkflowStep, adapted to use Externalizable and to avoid storing the plugin class as a class object 
 * (preferring a String instead).
 * 
 * @author Pete Bankhead
 *
 */
public class SimplePluginWorkflowStep implements ScriptableWorkflowStep, Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	private String name;
	private String pluginClass;
	private String arg;
	private String scriptBefore; // Script to insert before plugin is called (including any newlines etc)
	private String scriptAfter; // Script to insert after plugin is called

	/**
	 * Default public constructor, required for Externalizable.
	 * Shouldn't be used directly!  Doing so would give an ineffective script...
	 */
	public SimplePluginWorkflowStep() {}
	
	/**
	 * Constructor for a workflow step that calls a plugin.
	 * @param name
	 * @param pluginClass
	 * @param arg
	 */
	public SimplePluginWorkflowStep(final String name, final Class<? extends PathPlugin<?>> pluginClass, final String arg) {
		this(name, pluginClass, arg, null, null);
	}
	
	/**
	 * Constructor for a workflow step that calls a plugin, which optionally should include additional scripting lines before or afterwards.
	 * @param name
	 * @param pluginClass
	 * @param arg
	 * @param scriptBefore
	 * @param scriptAfter
	 */
	public SimplePluginWorkflowStep(final String name, final Class<? extends PathPlugin<?>> pluginClass, final String arg, final String scriptBefore, final String scriptAfter) {
		this.name = name;
		this.pluginClass = pluginClass.getName();
		this.arg = arg;
		this.scriptBefore = scriptBefore;
		this.scriptAfter = scriptAfter;
	}
	
	@Override
	public String getName() {
		return name;
	}
	
	/**
	 * Get the full name of the plugin class.
	 * 
	 * @return
	 */
	public String getPluginClass() {
		return pluginClass;
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
	

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n");
		sb.append(pluginClass).append("  ")
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
			append(pluginClass).
			append("', ").
			append("'").
			append(arg).
			append("'").
			append(");");
		if (scriptAfter != null)
			sb.append(scriptAfter);
		return sb.toString();
	}

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(1); // Version
		out.writeObject(name);
		out.writeObject(pluginClass);
		out.writeObject(arg);
		out.writeObject(scriptBefore);
		out.writeObject(scriptAfter);
	}

	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int v = in.readInt();
		if (v != 1)
			throw new IOException("Invalid version number " + v);
		name = (String)in.readObject();
		pluginClass = (String)in.readObject();
		arg = (String)in.readObject();
		scriptBefore = (String)in.readObject();
		scriptAfter = (String)in.readObject();
	}
	
	

}
