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


package qupath.lib.gui.scripting.syntax;

import java.util.Set;

import qupath.lib.gui.scripting.EditableText;

class PythonSyntax extends GeneralCodeSyntax {
	
	// Empty constructor
	PythonSyntax() {}
		
	@Override
	public Set<String> getLanguageNames() {
		return Set.of("python", "jython");
	}
	
	@Override
	public String getLineCommentString() {
		return "# ";
	}
	
	
	@Override
	public void handleNewLine(final EditableText control, final boolean smartEditing) {
		// Indent once more if the last character is a colon and we're using smart editing
		int caretPos = control.getCaretPosition();
		String text = control.getText();
		boolean lastColon = caretPos > 0 && text.charAt(caretPos-1) == ':';
		super.handleNewLine(control, smartEditing);
		if (!lastColon || !smartEditing) {
			return;
		}
		control.insertText(control.getCaretPosition(), getTabString());
	}
	
}
