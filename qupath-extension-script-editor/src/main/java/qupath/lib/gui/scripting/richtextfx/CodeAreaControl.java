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

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.utils.FXUtils;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.scripting.EditableText;
import qupath.lib.gui.scripting.ScriptEditorControl;
import qupath.lib.gui.scripting.richtextfx.stylers.ScriptStyler;
import qupath.lib.gui.scripting.richtextfx.stylers.ScriptStylerProvider;
import qupath.lib.gui.scripting.syntax.ScriptSyntax;
import qupath.lib.gui.scripting.syntax.ScriptSyntaxProvider;
import qupath.lib.scripting.languages.AutoCompletions;
import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Code area control using RichTextFX.
 * 
 * @author Pete Bankhead
 */
public class CodeAreaControl implements ScriptEditorControl<VirtualizedScrollPane<CodeArea>> {

	private static final Logger logger = LoggerFactory.getLogger(CodeAreaControl.class);

	private static final KeyCodeCombination completionCodeCombination = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);

	private static ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("rich-text-styling", true));

	// Delay for async formatting, in milliseconds
	private static int delayMillis = 20;

	private VirtualizedScrollPane<CodeArea> scrollpane;
	private CodeArea codeArea;
	private StringProperty textProperty = new SimpleStringProperty();
	
	private ContextMenu contextMenu;

	private boolean smartEditing = true;
	private ScriptSyntax syntax;
	private ScriptLanguage language;
	private ScriptStyler styler = ScriptStylerProvider.PLAIN;
	private Popup popup;
	private ListView<AutoCompletions.Completion> listCompletions;

	private CodeAreaControl(boolean isEditable) {
		this.codeArea = createCodeArea();
		this.codeArea.setEditable(isEditable);
		this.codeArea.textProperty().addListener((o, v, n) -> textProperty.set(n));
		textProperty.addListener((o, v, n) -> {
			if (n.equals(this.codeArea.getText()))
				return;
			this.codeArea.clear();
			this.codeArea.insertText(0, n);
		});
		scrollpane = new VirtualizedScrollPane<>(this.codeArea);
		if (isEditable) {
			initializeEditable();
		} else {
			initializeLog();
		}
	}

	/**
	 * Create a code area with some 'standard' customizations (e.g. style sheet).
	 * @return
	 */
	private static CodeArea createCodeArea() {
		var codeArea = new CustomCodeArea();
		// Turned off by default in CodeArea... but I think it helps by retaining the most recent style
		// Particularly noticeable with markdown
		codeArea.setUseInitialStyleForInsertion(false);
		// Be sure to add stylesheet
		var url = RichScriptEditor.class.getClassLoader().getResource("scripting_styles.css");
		if (url != null)
			codeArea.getStylesheets().add(url.toExternalForm());
		return codeArea;
	}

	/**
	 * Create an editable control for writing code.
	 * @return
	 */
	public static CodeAreaControl createCodeEditor() {
		return new CodeAreaControl(true);
	}

	/**
	 * Create a non-editable control for showing log messages.
	 * @return
	 */
	public static CodeAreaControl createLog() {
		return new CodeAreaControl(false);
	}


	private void initializeEditable() {
		this.initializeCompletions();
		this.initEditableStyle();
		this.initEditableCleanup();
		this.codeArea.addEventFilter(KeyEvent.KEY_TYPED, this::handleKeyTyped);
		this.codeArea.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
	}

	private void initializeLog() {
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
						var styler = this.styler;
						if (styler == null)
							styler = ScriptStylerProvider.PLAIN;
						codeArea.setStyleSpans(start, styler.computeConsoleStyles(text, true));
					}
				});
		codeArea.setEditable(false);
	}
	
	@Override
	public StringProperty textProperty() {
		return textProperty;
	}

	@Override
	public void setText(String text) {
		codeArea.replaceText(text);
		requestFollowCaret();
	}

	@Override
	public String getText() {
		return codeArea.getText();
	}

	@Override
	public ObservableValue<String> selectedTextProperty() {
		return codeArea.selectedTextProperty();
	}

	@Override
	public String getSelectedText() {
		return codeArea.getSelectedText();
	}

	@Override
	public VirtualizedScrollPane<CodeArea> getRegion() {
		return scrollpane;
	}

	@Override
	public boolean isUndoable() {
		return codeArea.isUndoAvailable();
	}

	@Override
	public boolean isRedoable() {
		return codeArea.isRedoAvailable();
	}

	@Override
	public void undo() {
		codeArea.undo();
	}

	@Override
	public void redo() {
		codeArea.redo();
	}

	@Override
	public void copy() {
		codeArea.copy();
	}

	@Override
	public void cut() {
		codeArea.cut();
	}

	@Override
	public void paste() {
		codeArea.paste();
	}
	
	@Override
	public void appendText(final String text) {
		codeArea.appendText(text);
		requestFollowCaret();
	}

	@Override
	public void clear() {
		codeArea.clear();
	}
	
	@Override
	public int getCaretPosition() {
		return codeArea.getCaretPosition();
	}
	
	@Override
	public void insertText(int pos, String text) {
		codeArea.insertText(pos, text);
		requestFollowCaret();
	}
	
	@Override
	public void deleteText(int startIdx, int endIdx) {
		codeArea.deleteText(startIdx, endIdx);
		requestFollowCaret();
	}

	@Override
	public void deselect() {
		codeArea.deselect();
	}

	@Override
	public IndexRange getSelection() {
		return codeArea.getSelection();
	}

	@Override
	public void selectRange(int startIdx, int endIdx) {
		codeArea.selectRange(startIdx, endIdx);
	}
	
	@Override
	public BooleanProperty wrapTextProperty() {
		return codeArea.wrapTextProperty();
	}

	@Override
	public void positionCaret(int index) {
		codeArea.moveTo(index);
}

	@Override
	public void requestFollowCaret() {
		codeArea.requestFollowCaret();
	}
	
	@Override
	public void replaceSelection(String text) {
		codeArea.replaceSelection(text);
		requestFollowCaret();
	}
	
	@Override
	public void setContextMenu(ContextMenu menu) {
		// Try this approach, because otherwise some styling feeds through to the context menu 
		// & can look weird (e.g. making it use a monospaced font)
		this.contextMenu = menu;
		codeArea.setContextMenu(null);
		codeArea.setOnContextMenuRequested(e -> {
			var popup = codeArea.getContextMenu() == null ? contextMenu : codeArea.getContextMenu();
			if (popup != null)
				popup.show(codeArea.getScene().getWindow(), e.getScreenX(), e.getScreenY());
		});
	}

	@Override
	public ContextMenu getContextMenu() {
		var popup = codeArea.getContextMenu();
		if (popup != null)
			return popup;
		return contextMenu;
	}

	@Override
	public void requestFocus() {
		codeArea.requestFocus();
	}

	private ReadOnlyIntegerProperty caretReadOnly;
	
	@Override
	public ReadOnlyIntegerProperty caretPositionProperty() {
		if (caretReadOnly == null) {
			var caret = new SimpleIntegerProperty();
			caret.bind(codeArea.caretPositionProperty());
			caretReadOnly = IntegerProperty.readOnlyIntegerProperty(caret);
		}
		return caretReadOnly;
	}

	@Override
	public void setLanguage(ScriptLanguage language) {
		if (Objects.equals(this.language, language))
			return;
		this.language = language;
		updateSyntax();
		updateStyler();
	}

	@Override
	public ScriptLanguage getLanguage() {
		return language;
	}

	/**
	 * Request smart editing, e.g. to insert closing parentheses.
	 * @param smartEditing
	 */
	public void setSmartEditing(boolean smartEditing) {
		this.smartEditing = smartEditing;
	}

	/**
	 * Check whether smart editing is requested.
	 * @return
	 */
	public boolean getSmartEditing() {
		return smartEditing;
	}

	private void updateSyntax() {
		String name = language == null ? null : language.getName();
		this.syntax = name == null ? ScriptSyntaxProvider.PLAIN : ScriptSyntaxProvider.getSyntaxFromName(name);
	}

	private void updateStyler() {
		this.styler = language == null ? ScriptStylerProvider.PLAIN : ScriptStylerProvider.getStylerFromLanguage(language);
		String baseStyle = styler.getBaseStyle();
		if (baseStyle != null && !baseStyle.isBlank())
			codeArea.setStyle(baseStyle);
		else
			codeArea.setStyle(null);
		StyleSpans<Collection<String>> changes = styler.computeEditorStyles(codeArea.getText());
		codeArea.setStyleSpans(0, changes);
		codeArea.requestFocus(); // Seems necessary to trigger the update when switching between scripts
	}

	private void initEditableStyle() {
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

		codeArea.getStylesheets().add(RichScriptEditor.class.getClassLoader().getResource("scripting_styles.css").toExternalForm());
	}

	private void initializeCompletions() {
		// TODO: Check if DefaultScriptEditor does any of these? It should be able to at least do syntaxing/auto-completion
		popup = new Popup();
		listCompletions = new ListView<AutoCompletions.Completion>();

		listCompletions.setCellFactory(c -> FXUtils.createCustomListCell(AutoCompletions.Completion::getDisplayText));

		listCompletions.setPrefSize(350, 400);
		popup.getContent().add(listCompletions);
		listCompletions.setStyle("-fx-font-size: smaller; -fx-font-family: Courier;");
		Runnable completionFun = () -> {
			// Get the selection completion - or the focused one if there is no selection
			var selected = listCompletions.getSelectionModel().getSelectedItem();
			if (selected == null)
				selected = listCompletions.getFocusModel().getFocusedItem();
			if (selected != null) {
				applyCompletion(this, selected);
			}
			popup.hide();
		};
		listCompletions.setOnKeyReleased(e -> {
			if (e.getCode() == KeyCode.TAB) {
				completionFun.run();
			}
			if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.TAB) {
				completionFun.run();
				e.consume();
			} else if (e.getCode() == KeyCode.ESCAPE) {
				popup.hide();
				listCompletions.getItems().clear();
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
	}

	private void initEditableCleanup() {
		codeArea.multiPlainChanges()
				.successionEnds(Duration.ofMillis(delayMillis))
				.retainLatestUntilLater(executor)
				.supplyTask(() -> {
					Task<StyleSpans<Collection<String>>> task = new Task<>() {
						@Override
						protected StyleSpans<Collection<String>> call() {
							return styler.computeEditorStyles(codeArea.getText());
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
					logger.debug("", exception);
					return Optional.empty();
				})
				.subscribe(styles -> codeArea.setStyleSpans(0, styles));
	}

	// Catch key typed events for special character handling (which should be platform-agnostic)
	private void handleKeyTyped(KeyEvent e) {
		if (e.isConsumed() || syntax == null)
			return;

		if ("(".equals(e.getCharacter())) {
			syntax.handleLeftParenthesis(this, smartEditing);
			e.consume();
		} else if (")".equals(e.getCharacter())) {
			syntax.handleRightParenthesis(this, smartEditing);
			e.consume();
		} else if ("\"".equals(e.getCharacter())) {
			syntax.handleQuotes(this, true, smartEditing);
			e.consume();
		} else if ("'".equals(e.getCharacter())) {
			syntax.handleQuotes(this, false, smartEditing);
			e.consume();
		}
	}

	private void handleKeyPressed(KeyEvent e) {
		if (e.isConsumed())
			return;

		var scriptSyntax = this.syntax;
		if (scriptSyntax != null) {
			if (e.getCode() == KeyCode.TAB) {
				scriptSyntax.handleTabPress(this, e.isShiftDown());
				e.consume();
			} else if (e.isShortcutDown() && e.getCode() == KeyCode.SLASH) {
				scriptSyntax.handleLineComment(this);
				e.consume();
			} else if (e.getCode() == KeyCode.ENTER && codeArea.getSelectedText().isEmpty()) {
				scriptSyntax.handleNewLine(this, smartEditing);
				e.consume();
			} else if (e.getCode() == KeyCode.BACK_SPACE) {
				if (scriptSyntax.handleBackspace(this, smartEditing) && !e.isShortcutDown() && !e.isShiftDown())
					e.consume();
			}
		}
		var scriptAutoCompletor = language == null ? null : language.getAutoCompletor();
		if (scriptAutoCompletor != null) {
			if (completionCodeCombination.match(e)) {
				// Starting to show auto-completions
				var completions = getCurrentAutoCompletions(this);
				if (!completions.isEmpty()) {
					var bounds = codeArea.getCaretBounds().orElse(null);
					if (bounds != null) {
						listCompletions.getItems().setAll(completions);
						popup.show(codeArea, bounds.getMaxX(), bounds.getMaxY());
						e.consume();
					}
				}
				// No auto-completions or no caret bounds, so hide
				if (!e.isConsumed() && popup.isShowing()) {
					popup.hide();
					e.consume();
				}
			} else if (popup.isShowing()) {
				// A bit ugly... but we can't get the completions in the filter, or else we'll miss
				// the current key press
				Platform.runLater(() -> {
					if (!popup.isShowing())
						return;
					var completions = getCurrentAutoCompletions(this);
					listCompletions.getItems().setAll(completions);
					if (completions.isEmpty())
						popup.hide();
				});
			}
		}
	}

	private List<AutoCompletions.Completion> getCurrentAutoCompletions(ScriptEditorControl<?> control) {
		var completor = language == null ? null : language.getAutoCompletor();
		if (completor == null)
			return Collections.emptyList();
		var completions = completor.getCompletions(control.getText(), control.getCaretPosition());
		if (completions.isEmpty() || completions.size() == 1)
			return completions;
		return completions.stream()
				.sorted(AutoCompletions.getComparator())
				.distinct()
				.toList();
	}

	/**
	 * Insert the text from the completion to the editable text.
	 * @param control
	 * @param completion
	 */
	private static void applyCompletion(EditableText control, AutoCompletions.Completion completion) {
		String text = control.getText();
		int pos = control.getCaretPosition();

		var insertion = completion.getInsertion(text, pos, null);
		// Avoid inserting if caret is already between parentheses
		if (insertion == null || insertion.isEmpty() || insertion.startsWith("("))
			return;
		control.insertText(pos, insertion);
		// If we have a method that includes arguments,
		// then we want to position the caret within the parentheses
		// (whereas for a method without arguments, we want the caret outside)
		if (insertion.endsWith("()") && control.getCaretPosition() > 0 && !completion.getDisplayText().endsWith("()"))
			control.positionCaret(control.getCaretPosition()-1);
	}

}
