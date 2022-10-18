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

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javafx.event.EventHandler;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.languages.ScriptLanguageProvider;

/**
 * Drag and drop support for QuPath's script editor, which can support a range of different files (Plain text, JSON, Groovy,..).
 * @author Melvin Gelbard
 * @since v0.4.0
 */
class ScriptEditorDragDropListener implements EventHandler<DragEvent> {

	private final QuPathGUI qupath;

	/**
	 * Constructor.
	 * @param qupath the current QuPath instance
	 */
	public ScriptEditorDragDropListener(final QuPathGUI qupath) {
		this.qupath = qupath;
	}

	@Override
	public void handle(DragEvent event) {
		Dragboard dragboard = event.getDragboard();
        
		if (dragboard.hasFiles()) {
			var list = dragboard.getFiles();
			if (list == null)
				return;
			
			List<File> jars = list.stream().filter(f -> f.getName().toLowerCase().endsWith(".jar")).collect(Collectors.toList());
			if (!jars.isEmpty())
				qupath.installExtensions(list);
			
			List<File> remainingFiles = list.stream().filter(f -> !f.getName().toLowerCase().endsWith(".jar")).collect(Collectors.toList());
			var supported = ScriptLanguageProvider.getAvailableLanguages().stream()
					.flatMap(l -> l.getExtensions().stream())
					.collect(Collectors.toCollection(HashSet::new));
			supported.add(".qpproj");	// TODO: Maybe add this as a JsonLanguage ext? so the highlighting is automatically set
			supported.add(".qpdata");
			for (File file: remainingFiles) {
				for (var supportedExt: supported) {
					if (file.getName().toLowerCase().endsWith(supportedExt)) {
						qupath.getScriptEditor().showScript(file);
						break;
					}
				}
			}
		}
	}
}
