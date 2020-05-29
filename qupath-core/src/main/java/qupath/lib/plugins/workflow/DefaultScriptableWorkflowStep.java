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

/**
 * A scriptable workflow step where the script is provided directly as an argument.
 * 
 * @author Pete Bankhead
 *
 */
public class DefaultScriptableWorkflowStep implements ScriptableWorkflowStep {
	
	
	private static final long serialVersionUID = 1L;
	
	private String name;
	private String script;
	private Map<String, ?> map;

	/**
	 * Constructor that takes a parameter map for display.
	 * 
	 * The parameter map isn't embedded in the script by default - this script that is passed should be complete.
	 * 
	 * @param name
	 * @param parameterMap
	 * @param script
	 */
	public DefaultScriptableWorkflowStep(final String name, final Map<String, ?> parameterMap, final String script) {
		this.name = name;
		this.map = parameterMap;
		this.script = script;
	}
	
	/**
	 * Create a workflow step using a provided script string that will be called as-is.
	 * 
	 * @param name
	 * @param script
	 */
	public DefaultScriptableWorkflowStep(final String name, final String script) {
		this(name, null, script);
	}
	
	@Override
	public String getName() {
		return name;
	}

	
	@Override
	public Map<String, ?> getParameterMap() {
		if (map == null)
			return Collections.emptyMap();
		return Collections.unmodifiableMap(map);
	}
	

	@Override
	public String toString() {
		return getName() + "\t" + script;
	}
	
	
	@Override
	public String getScript() {
		return script;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((map == null) ? 0 : map.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((script == null) ? 0 : script.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		DefaultScriptableWorkflowStep other = (DefaultScriptableWorkflowStep) obj;
		if (map == null) {
			if (other.map != null)
				return false;
		} else if (!map.equals(other.map))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (script == null) {
			if (other.script != null)
				return false;
		} else if (!script.equals(other.script))
			return false;
		return true;
	}

}
