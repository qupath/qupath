/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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

/**
 * Abstract class to represent languages supported by the script editor.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public abstract class ScriptLanguage {
	
	private String name;
	private String[] exts;
	
	/**
	 * Default constructor for a {@link ScriptLanguage}.
	 * @param name		the language name
	 * @param exts			the possible extensions for this language
	 */
	protected ScriptLanguage(String name, String[] exts) {
		this.name = name;
		this.exts = exts;
	}
	
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
	 * <p>
	 * Can return {@code null} if the script language should not handle syntax formatting for this language.
	 * @return syntax object
	 */
	public abstract ScriptSyntax getSyntax();
	
	/**
	 * Get the {@link ScriptAutoCompletor} object that takes care of this language's auto-completion. 
	 * <p>
	 * Can return {@code null} if the script editor should not handle auto-completion for this language.
	 * @return auto-completor
	 */
	public abstract ScriptAutoCompletor getAutoCompletor();

	@Override
	public String toString() {
		return name;
	}
}
