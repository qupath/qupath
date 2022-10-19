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

package qupath.lib.scripting.languages;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Abstract class to represent languages supported by the script editor.
 * @author Melvin Gelbard
 * @author Pete Bankhead
 * @since v0.4.0
 */
public abstract class ScriptLanguage {
	
	private String name;
	private Set<String> exts;
	
	/**
	 * Default constructor for a {@link ScriptLanguage}.
	 * @param name the language name
	 * @param exts all supported file extensions for this language, in the form {@code .ext} (lowercase)
	 */
	protected ScriptLanguage(String name, Collection<String> exts) {
		this.name = name;
		if (exts != null && !exts.isEmpty())
			this.exts = Collections.unmodifiableSet(new LinkedHashSet<>(exts));
		else
			this.exts = Collections.emptySet();
	}
	
	/**
	 * Default constructor for a {@link ScriptLanguage}.
	 * @param name the language name
	 * @param ext the file extensions for this language, in the form {@code .ext} (lowercase)
	 */
	protected ScriptLanguage(String name, String ext) {
		this.name = name;
		this.exts = ext == null ? Collections.emptySet() : Collections.singleton(ext);
	}

	/**
	 * Get the name of this language
	 * @return name
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Get an unmodifiable set containing the possible extensions for this language.
	 * @return extension array
	 */
	public Set<String> getExtensions() {
		return exts;
	}
	
	/**
	 * Get the {@link ScriptAutoCompletor} object that takes care of this language's auto-completion. 
	 * <p>
	 * Can return {@code null} if the script editor should not handle auto-completion for this language.
	 * @return auto-completor
	 */
	public ScriptAutoCompletor getAutoCompletor() {
		return null;
	}

	@Override
	public String toString() {
		return name;
	}
}
