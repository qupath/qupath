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

import javafx.beans.value.ObservableValue;
import org.fxmisc.richtext.CodeArea;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableObjectValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.Clipboard;
import javafx.scene.layout.Region;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.ScriptEditorControl;
import qupath.lib.gui.scripting.richtextfx.stylers.ScriptStyler;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.scripting.languages.ScriptLanguage;

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

	private final ObjectProperty<ScriptStyler> scriptStyler = new SimpleObjectProperty<>();

	private ContextMenu menu;
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance.
	 */
	public RichScriptEditor(QuPathGUI qupath) {
		super(qupath);
		menu = new ContextMenu();
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

	private void handleLanguageChange(ObservableValue<? extends ScriptLanguage> value, ScriptLanguage oldValue,
									  ScriptLanguage newValue) {
		var tab = getCurrentScriptTab();

	}

	@Override
	protected ScriptEditorControl<? extends Region> getNewEditor() {
		try {
			var editor = createEditor();
			var codeArea = editor.getRegion().getContent();
			codeArea.setOnContextMenuRequested(e -> menu.show(codeArea.getScene().getWindow(), e.getScreenX(), e.getScreenY()));
			return editor;
		} catch (Exception e) {
			// Default to superclass implementation
			logger.error("Unable to create code area", e);
			return super.getNewEditor();
		}
	}

	private static CodeAreaControl createEditor() {
		CodeArea codeArea = createCodeArea();
		return new CodeAreaControl(codeArea, true);
	}
	
	
	static CodeAreaControl createLogConsole(ObservableObjectValue<ScriptStyler> scriptStyler, ObservableBooleanValue useLogHighlighting) {
		
		CodeArea codeArea = createCodeArea();
		codeArea.plainTextChanges()
			.subscribe(c -> {
			// If anything was removed, do full reformatting
			// Otherwise, format from the position of the edit
			int start = Integer.MAX_VALUE;
			if (!c.getRemoved().isEmpty()) {
				start = 0;
			} else
				start = Math.min(start, c.getPosition());
			if (start < Integer.MAX_VALUE) {
				String text = codeArea.getText();
				// Make sure we return to the last newline
				while (start > 0 && text.charAt(start) != '\n')
					start--;
				
				if (start > 0) {
					text = text.substring(start);
				}
				codeArea.setStyleSpans(start, scriptStyler.get().computeConsoleStyles(text, useLogHighlighting.get()));
			}
		});
		codeArea.setEditable(false);
		return new CodeAreaControl(codeArea, false);

	}
	
	
	/**
	 * Create a code area with some 'standard' customizations (e.g. style sheet).
	 * @return 
	 */
	static CodeArea createCodeArea() {
		var codeArea = new CustomCodeArea();
		// Turned off by default in CodeArea... but I think it helps by retaining the most recent style
		// Particularly noticeable with markdown
		codeArea.setUseInitialStyleForInsertion(false);
		// Be sure to add stylesheet
		codeArea.getStylesheets().add(RichScriptEditor.class.getClassLoader().getResource("scripting_styles.css").toExternalForm());
		return codeArea;
	}
	
	
	
	@Override
	protected ScriptEditorControl<? extends Region> getNewConsole() {
		try {
			return createLogConsole(scriptStyler, sendLogToConsoleProperty());
		} catch (Exception e) {
			// Default to superclass implementation
			logger.error("Unable to create console area", e);
			return super.getNewEditor();
		}
	}
	
	static class CustomCodeArea extends CodeArea {
		
		/**
		 * We need to override the default Paste command to handle escaping
		 */
		@Override
		public void paste() {
			var text = getClipboardText(false);
			if (text != null) {
				if (text.equals(Clipboard.getSystemClipboard().getString()))
					super.paste();
				else
					replaceSelection(text);
			}
		}
	}
}
