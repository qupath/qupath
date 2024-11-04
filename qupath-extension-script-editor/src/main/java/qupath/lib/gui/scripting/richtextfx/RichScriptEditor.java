/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.scene.control.ContextMenu;
import javafx.scene.layout.Region;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.ScriptEditorControl;
import qupath.lib.gui.tools.MenuTools;

/*
 * 
 * The rich text script editor makes use of RichTextFX, including aspects of the demo code JavaKeywordsAsyncDemo.java, both from https://github.com/TomasMikula/RichTextFX
 * License for these components:
 * 
 * Copyright (c) 2013-2017, Tomas Mikula and contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 */

/**
 * 
 * Rich script editor for QuPath, which can be used for handling different languages.
 * <p>
 * Makes use of RichTextFX, Copyright (c) 2013-2017, Tomas Mikula and contributors (BSD 2-clause license).
 * 
 * @author Pete Bankhead
 * 
 */
public class RichScriptEditor extends DefaultScriptEditor {
	
	private static final Logger logger = LoggerFactory.getLogger(RichScriptEditor.class);

	private ContextMenu menu;
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance.
	 */
	public RichScriptEditor(QuPathGUI qupath) {
		super(qupath);
		menu = new ContextMenu();
		this.smartEditing.addListener((v, o, n) -> updateSmartEditing());
		this.selectedScriptProperty().addListener((v, o, n) -> updateSmartEditing());
		MenuTools.addMenuItems(menu.getItems(),
				MenuTools.createMenu("Run...",
						runScriptAction,
						runSelectedAction,
						runProjectScriptAction,
						runProjectScriptNoSaveAction
						),
				MenuTools.createMenu("Undo/Redo...",
					undoAction,
					redoAction
					),
				null,
				copyAction,
				pasteAction,
				pasteAndEscapeAction
				);

	}

	@Override
	protected ScriptEditorControl<? extends Region> getNewEditor() {
		try {
			var editor = CodeAreaControl.createCodeEditor();
			editor.setSmartEditing(smartEditing.get());
			editor.setLanguage(getCurrentLanguage());
			var codeArea = editor.getRegion().getContent();
			codeArea.setOnContextMenuRequested(e -> menu.show(codeArea.getScene().getWindow(), e.getScreenX(), e.getScreenY()));
			return editor;
		} catch (Exception e) {
			// Default to superclass implementation
			logger.error("Unable to create code area", e);
			return super.getNewEditor();
		}
	}

	private void updateSmartEditing() {
		if (getCurrentEditorControl() instanceof CodeAreaControl control)
			control.setSmartEditing(smartEditing.get());
	}

	static CodeAreaControl createLogConsole() {
		return CodeAreaControl.createLog();
	}

	
	@Override
	protected ScriptEditorControl<? extends Region> getNewConsole() {
		try {
			return createLogConsole();
		} catch (Exception e) {
			// Default to superclass implementation
			logger.error("Unable to create console area", e);
			return super.getNewEditor();
		}
	}

}
