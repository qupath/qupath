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

package qupath.lib.gui.scripting.richtextfx;

import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;

/**
 * Interface for classes that implement auto-completion (e.g. styling classes).
 * @author Melvin Gelbard
 *
 */
interface ScriptAutoCompletor {
	
	/**
	 * Default key code for code completion
	 */
	public final KeyCodeCombination defaultCompletionCode = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);
	
	
	/**
	 * Try to match and auto-complete a method name.
	 */
	void applyNextCompletion();
	
	/**
	 * Reset the completion process (e.g. if currently iterating through a list of methods, reset the iteration to the first element).
	 * <p>
	 * The {@link KeyEvent} parameter here should allow developers to filter out irrelevant calls to this method (e.g. CTRL/CMD + 
	 * SPACE tends to be called after CTRL/CMD on its own, as the key is usually typed slightly before).
	 * @param e the current KeyEvent
	 */
	void resetCompletion(KeyEvent e);
	
	/**
	 * Get the {@link KeyCodeCombination} that will trigger the auto-completor.
	 * @return key code combination
	 */
	default KeyCodeCombination getCodeCombination() {
		return defaultCompletionCode;
	}
}
