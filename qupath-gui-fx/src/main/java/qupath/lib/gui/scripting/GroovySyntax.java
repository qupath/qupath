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

package qupath.lib.gui.scripting;

/**
 * Class to take care of the Groovy syntax formatting.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
class GroovySyntax extends GeneralCodeSyntax {
	
	GroovySyntax() {
		// Empty constructor
	}
	
	@Override
	public String getLineCommentString() {
		return "//";
	}
	
	@Override
	public void handleLeftParenthesis(final ScriptEditorControl control, final boolean smartEditing) {
		control.insertText(control.getCaretPosition(), "MELVIN");
	}
}