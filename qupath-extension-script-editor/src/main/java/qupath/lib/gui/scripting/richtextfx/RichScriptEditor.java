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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.concurrent.Task;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.ScriptEditorControl;
import qupath.lib.gui.scripting.highlighters.ScriptHighlighter;
import qupath.lib.gui.scripting.highlighters.ScriptHighlighterProvider;
import qupath.lib.gui.scripting.languages.ScriptAutoCompletor;
import qupath.lib.gui.tools.GuiTools;
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
	
	private ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("rich-text-styling", true));
	
	private final ObjectProperty<ScriptHighlighter> scriptHighlighter = new SimpleObjectProperty<>();

	// Delay for async formatting, in milliseconds
	private static int delayMillis = 100;

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

	@Override
	protected ScriptEditorControl getNewEditor() {
		try {
			CodeArea codeArea = new CustomCodeArea();
			CodeAreaControl control = new CodeAreaControl(codeArea);
			
			/*
			 * Using LineNumberFactory.get(codeArea) gives errors related to the new paragraph folding introduced in RichTextFX 0.10.6.
			 *  java.lang.IllegalArgumentException: Visible paragraphs' last index is [-1] but visibleParIndex was [0]
			 *  
			 * To replicate
			 *  - Run using codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
			 *  - Add some code (including line breaks) to the code area
			 *  - Select all text (Ctrl/Cmd + A)
			 *  - Delete text (backspace)
			 *  - Add more text
			 *  
			 * The change below avoids code folding being used.
			 */
			codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea, digits -> "%1$" + digits + "s", null, null));
//			codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
			
			codeArea.setStyle("-fx-background-color: -fx-control-inner-background;");
			
			// Catch key typed events for special character handling (which should be platform-agnostic)
			codeArea.addEventFilter(KeyEvent.KEY_TYPED, e -> {
				if (e.isConsumed())
					return;
				
				var scriptSyntax = getCurrentLanguage().getSyntax();
				if ("(".equals(e.getCharacter())) {
					scriptSyntax.handleLeftParenthesis(control, smartEditing.get());
					e.consume();
				} else if (")".equals(e.getCharacter())) {
					scriptSyntax.handleRightParenthesis(control, smartEditing.get());
					e.consume();
				} else if ("\"".equals(e.getCharacter())) {
					scriptSyntax.handleQuotes(control, true, smartEditing.get());
					e.consume();
				} else if ("\'".equals(e.getCharacter())) {
					scriptSyntax.handleQuotes(control, false, smartEditing.get());
					e.consume();
				}
			});
			
			// TODO: Check if DefaultScriptEditor does any of these? It should be able to at least do syntaxing/auto-completion
			var popup = new Popup();
			var listCompletions = new ListView<ScriptAutoCompletor.Completion>();
			
			listCompletions.setCellFactory(c -> GuiTools.createCustomListCell(c2 -> c2.getDisplayText()));
			
			listCompletions.setPrefSize(350, 400);
			popup.getContent().add(listCompletions);
			listCompletions.setStyle("-fx-font-size: smaller; -fx-font-family: Courier;");
			var completionsMap = new HashSet<ScriptAutoCompletor.Completion>();
			Runnable completionFun = () -> {
				var selected = listCompletions.getSelectionModel().getSelectedItem();
				if (selected != null) {
					var scriptAutoCompletor = getCurrentLanguage().getAutoCompletor();
					if (scriptAutoCompletor != null)
						scriptAutoCompletor.applyCompletion(control, selected);
				}
				popup.hide();
			};
			listCompletions.setOnKeyReleased(e -> {
				if (e.getCode() == KeyCode.ENTER) {
					completionFun.run();
					e.consume();
				}
			});
			listCompletions.setOnMouseClicked(e -> {
				if (e.getClickCount() == 2) {
					completionFun.run();
					e.consume();
				}
			});
			listCompletions.setMaxHeight(200);
			popup.focusedProperty().addListener((v, o, n) -> {
				if (!n)
					popup.hide();
			});
			
			
			codeArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
				if (e.isConsumed())
					return;
				
				var scriptSyntax = getCurrentLanguage().getSyntax();
				if (scriptSyntax != null) {
					if (e.getCode() == KeyCode.TAB) {
						scriptSyntax.handleTabPress(control, e.isShiftDown());
						e.consume();
					} else if (e.isShortcutDown() && e.getCode() == KeyCode.SLASH) {
						scriptSyntax.handleLineComment(control);
						e.consume();
					} else if (e.getCode() == KeyCode.ENTER && codeArea.getSelectedText().length() == 0) {
						scriptSyntax.handleNewLine(control, smartEditing.get());
						e.consume();
					} else if (e.getCode() == KeyCode.BACK_SPACE) {
						if (scriptSyntax.handleBackspace(control, smartEditing.get()) && !e.isShortcutDown() && !e.isShiftDown())
							e.consume();
					}
				}
				
				var scriptAutoCompletor = getCurrentLanguage().getAutoCompletor();
				if (scriptAutoCompletor != null) {
					if (completionCodeCombination.match(e)) {
						var completions = scriptAutoCompletor.getCompletions(control);
						completionsMap.clear();
						if (!completions.isEmpty()) {
							completionsMap.addAll(completions);
							var bounds = codeArea.getCaretBounds().orElse(null);
							if (bounds != null) {
								var list = new ArrayList<>(completions);
								Collections.sort(list);
								listCompletions.getItems().setAll(list);
								popup.show(codeArea, bounds.getMaxX(), bounds.getMaxY());
								e.consume();
							}
						}
						if (!e.isConsumed() && popup.isShowing()) {
							popup.hide();
							e.consume();
						}
					} else if (!e.isControlDown()) {
						listCompletions.getItems().clear();
						if (popup.isShowing())
							popup.hide();
					}
				}
			});

			codeArea.setOnContextMenuRequested(e -> menu.show(codeArea.getScene().getWindow(), e.getScreenX(), e.getScreenY()));

			@SuppressWarnings("unused")
			var cleanup = codeArea
					.multiPlainChanges()
					.successionEnds(Duration.ofMillis(delayMillis))
					.supplyTask(() -> {
						Task<StyleSpans<Collection<String>>> task = new Task<>() {
							@Override
							protected StyleSpans<Collection<String>> call() {
								return scriptHighlighter.get().computeEditorHighlighting(codeArea.getText());
							}
						};
						executor.execute(task);
						return task;
					})
					.awaitLatest(codeArea.multiPlainChanges())
					.filterMap(t -> {
						if (t.isSuccess())
							return Optional.of(t.get());
						
						var exception = t.getFailure();
						String message = exception.getLocalizedMessage() == null ? exception.getClass().getSimpleName() : exception.getLocalizedMessage();
						logger.error("Error applying syntax highlighting: {}", message);
						logger.debug("{}", t);
						return Optional.empty();
					})
					.subscribe(change -> codeArea.setStyleSpans(0, change));

			codeArea.getStylesheets().add(getClass().getClassLoader().getResource("scripting_styles.css").toExternalForm());
			
			scriptHighlighter.bind(Bindings.createObjectBinding(() -> ScriptHighlighterProvider.getHighlighterFromLanguage(getCurrentLanguage()), currentLanguageProperty()));
			
			// Triggered whenever the script styling changes (e.g. change of language)
			scriptHighlighter.addListener((v, o, n) -> {
				if (n == null) {
					codeArea.setStyle(styleBackground);
					return;
				}
				String baseStyle = n.getBaseStyle();
				if (baseStyle == null || baseStyle.isBlank())
					codeArea.setStyle(styleBackground);
				else
					codeArea.setStyle(styleBackground + " " + baseStyle);
				StyleSpans<Collection<String>> changes = n.computeEditorHighlighting(codeArea.getText());
				codeArea.setStyleSpans(0, changes);
				codeArea.requestFocus(); // Seems necessary to trigger the update when switching between scripts
			});

			return control;
		} catch (Exception e) {
			// Default to superclass implementation
			logger.error("Unable to create code area", e);
			return super.getNewEditor();
		}
	}
	
	private static String styleBackground = "-fx-background-color: -fx-control-inner-background;";
	
	
	@Override
	protected ScriptEditorControl getNewConsole() {
		try {
			CodeArea codeArea = new CodeArea();
			codeArea.setStyle(styleBackground);
			
			codeArea.richChanges()
			.successionEnds(Duration.ofMillis(delayMillis))
			.subscribe(change -> {
				// If anything was removed, do full reformatting
				if (change.getRemoved().length() != 0 || change.getPosition() == 0) {
					if (!change.getRemoved().getText().equals(change.getInserted().getText()))
						codeArea.setStyleSpans(0, scriptHighlighter.get().computeConsoleHighlighting(codeArea.getText()));
				} else {
					// Otherwise format only from changed position onwards
					codeArea.setStyleSpans(change.getPosition(), scriptHighlighter.get().computeConsoleHighlighting(change.getInserted().getText()));
				}
			});
			codeArea.getStylesheets().add(getClass().getClassLoader().getResource("scripting_styles.css").toExternalForm());
			return new CodeAreaControl(codeArea);
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
			if (text != null)
				replaceSelection(text);
		}
	}
}
