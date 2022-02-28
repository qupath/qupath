/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting.languages;

import com.google.common.base.Objects;

/**
 * Abstract class to represent languages supported by the script editor.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public abstract class ScriptLanguage {
	
	protected String name;
	protected String[] exts;
	protected ScriptSyntax syntax;
	protected ScriptAutoCompletor completor;
	
	/**
	 * Get the name of this language
	 * @return name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Get a String array with the possible extensions for this language.
	 * @return extension array
	 */
	public String[] getExtensions() {
		return exts;
	}
	
	/**
	 * Get the {@link ScriptSyntax} object that takes care of this language's syntaxing.
	 * @return syntax object
	 */
	public ScriptSyntax getSyntax() {
		return syntax;
	}
	
	/**
	 * Get the {@link ScriptAutoCompletor} object that takes care of this language's auto-completion.
	 * @return auto-completor
	 */
	public ScriptAutoCompletor getAutoCompletor() {
		return completor;
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(name);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		
		ScriptLanguage other = (ScriptLanguage) obj;
		return other.name.equals(this.name);
	}

	@Override
	public String toString() {
		return name;
	}
}
