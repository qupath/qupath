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

import java.util.regex.Pattern;

import com.sun.javafx.css.PseudoClassState;

import javafx.beans.binding.Bindings;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.gui.tools.PaneTools;

/**
 * Create a Find/Replace command window for a {@link ScriptEditorControl}.
 * @author Pete Bankhead
 * @author Melvin Gelbard
 */
class ScriptFindCommand implements Runnable {
	
	private Stage stage;
	private final DefaultScriptEditor scriptEditor;
	private final TextField tfFind = new TextField();
	private final TextField tfReplace = new TextField();
	private final Button btNext = new Button("Next");
	private final Label lbReplacedOccurrences = new Label();
	private final Label lbFoundOccurrences = new Label();
	private final CheckBox cbIgnoreCase = new CheckBox("Ignore case");
	private double xPos = -1;
	private double yPos = -1;
	
	private EventHandler<KeyEvent> eventHandler = e -> {
		if (e.getCode() == KeyCode.ENTER) {
			findNextAction(true);
			e.consume();
		}
	};
	
	ScriptFindCommand(DefaultScriptEditor scriptEditor) {
		this.scriptEditor = scriptEditor;
	}
	
	@Override
	public void run() {
		if (stage != null)
			stage.hide();		// Only way to request focus to stage when it's not hidden
		
		// If some text is selected in the main text component, use it as search query
		var selectedText = scriptEditor.getSelectedText();
		if (!selectedText.isEmpty()) {
			// StringIndexOutOfBoundsException can occur if selectedText == a tab (\t)
			if (selectedText.replace("\t", "").length() != 0) {
				tfFind.setText(selectedText);
				btNext.requestFocus();
			} else
				tfFind.setText("");
		} else
			tfFind.selectAll();
		
		createFindStage();
		lbReplacedOccurrences.setText("");
		lbFoundOccurrences.setText("");
		stage.show();
		tfFind.requestFocus();
		
		tfFind.addEventFilter(KeyEvent.KEY_PRESSED, eventHandler);
		tfReplace.addEventFilter(KeyEvent.KEY_PRESSED, eventHandler);
	}
	
	private void createFindStage() {
		stage = new Stage();
		stage.setTitle("Find/Replace");
		stage.initOwner(scriptEditor.getStage());
		stage.initModality(Modality.NONE);
		stage.setOnHiding(e -> {
			xPos = stage.getX();
			yPos = stage.getY();
			stage = null;
		});
		
		var control = scriptEditor.getCurrentEditorControl();
		
		Button btPrevious = new Button("Previous");
		Button btClose = new Button("Close");
		Button btReplaceNext = new Button("Replace/Next");
		Button btReplaceAll = new Button("Replace all");
		
		GridPane pane = new GridPane();
		pane.setVgap(10);
		pane.setHgap(10);
		tfFind.setMinWidth(350.0);
		tfReplace.setMinWidth(350.0);
		lbFoundOccurrences.setMinWidth(150);
	    HBox.setHgrow(lbFoundOccurrences, Priority.ALWAYS);
		
		int row = 0;
		PaneTools.addGridRow(pane, row++, 0, "Enter the text to find", new Label("Find: "), tfFind, tfFind, tfFind);
		PaneTools.addGridRow(pane, row++, 0, "Replace instance of query with the specified word", new Label("Replace with: "), tfReplace, tfReplace, tfReplace);
		PaneTools.addGridRow(pane, row++, 0, "Ignore case when searching query", cbIgnoreCase, cbIgnoreCase, cbIgnoreCase, cbIgnoreCase);
		PaneTools.addGridRow(pane, row++, 0, null, btReplaceNext, btReplaceAll, lbReplacedOccurrences, lbReplacedOccurrences);
		PaneTools.addGridRow(pane, row++, 0, null, btPrevious, btNext, lbFoundOccurrences, btClose);
		
		btPrevious.setMinWidth(100.0);
		btNext.setMinWidth(100.0);
		btReplaceNext.setMinWidth(100.0);
		btReplaceAll.setMinWidth(100.0);
		btClose.setMinWidth(100.0);
		
		// Make the 'Next' button appear as if it's in focus, except when other buttons are pressed
		btNext.pseudoClassStateChanged(PseudoClassState.getPseudoClass("focused"), true);
		
		tfFind.focusedProperty().addListener((v, o, n) -> {
			if (n)
				btNext.pseudoClassStateChanged(PseudoClassState.getPseudoClass("focused"), true);
		});
		tfReplace.focusedProperty().addListener((v, o, n) -> {
			if (n)
				btNext.pseudoClassStateChanged(PseudoClassState.getPseudoClass("focused"), true);
		});

//		actionNext.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.SHORTCUT_DOWN));
//		actionNext.setAccelerator(new KeyCodeCombination(KeyCode.P, KeyCombination.SHORTCUT_DOWN));
		
		stage.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
			if (e.getCode() == KeyCode.ESCAPE) {
				btClose.fire();
				e.consume();
			}
		});
		
		btNext.setOnAction(e -> findNextAction(true));
		btPrevious.setOnAction(e -> findPrevious(scriptEditor.getCurrentEditorControl(), tfFind.getText(), cbIgnoreCase.isSelected()));
		btNext.disableProperty().bind(tfFind.textProperty().isEmpty());
		btPrevious.disableProperty().bind(tfFind.textProperty().isEmpty());
		btReplaceNext.disableProperty().bind(tfFind.textProperty().isEmpty()
				.or(Bindings.createBooleanBinding(() -> {
					if (cbIgnoreCase.isSelected())
						return !tfFind.getText().toLowerCase().equals(scriptEditor.getCurrentEditorControl().selectedTextProperty().getValue().toLowerCase());
					return !tfFind.getText().equals(scriptEditor.getCurrentEditorControl().selectedTextProperty().getValue());
				}, control.selectedTextProperty(), cbIgnoreCase.selectedProperty(), tfFind.textProperty())));
		btReplaceAll.disableProperty().bind(tfFind.textProperty().isEmpty());
		
		btReplaceNext.setOnAction(e -> {
			var controlTemp = scriptEditor.getCurrentEditorControl();
			replaceFind(controlTemp, tfFind.getText(), cbIgnoreCase.isSelected());
			if (!controlTemp.getText().contains(tfFind.getText())) {
				// Remove focus-looking effect on 'Next' button
				tfFind.requestFocus();
				btNext.pseudoClassStateChanged(PseudoClassState.getPseudoClass("focused"), true);
			}
		});
		btReplaceAll.setOnAction(e -> replaceAll(scriptEditor.getCurrentEditorControl(), tfFind.getText(), cbIgnoreCase.isSelected()));
		btClose.setOnAction(e -> stage.hide());
		
		pane.setPadding(new Insets(10.0, 10.0, 10.0, 10.0));
		stage.setScene(new Scene(pane));
		
		// The previous position of the stage is lost at each run() call, so store it
		if (xPos != -1 && yPos != -1) {
			stage.setX(xPos);
			stage.setY(yPos);
		}
	}
	
	private void findNextAction(boolean btNextFocus) {
		int found = findNext(scriptEditor.getCurrentEditorControl(), tfFind.getText(), cbIgnoreCase.isSelected());
		lbFoundOccurrences.setText(found == -1 ? "String not found" : "");
		lbReplacedOccurrences.setText("");
		btNext.pseudoClassStateChanged(PseudoClassState.getPseudoClass("focused"), btNextFocus);
	}
	
	/**
	 * Replace the current selection and selects the next matching query.
	 * @param control
	 * @param text
	 * @param ignoreCase
	 */
	private void replaceFind(ScriptEditorControl control, String text, boolean ignoreCase) {
		// Remove focus-looking effect on 'Next' button
		btNext.pseudoClassStateChanged(PseudoClassState.getPseudoClass("focused"), false);
		
		lbReplacedOccurrences.setText("");
		lbFoundOccurrences.setText("");
		
		var selected = control.getSelectedText();
		var range = control.getSelection();
		
		// Replace selection
		control.deleteText(range.getStart(), range.getEnd());
		control.insertText(range.getStart(), tfReplace.getText());
		
		// Select next matching query
		findNext(control, selected, ignoreCase);
	}
	
	private void replaceAll(ScriptEditorControl control, String text, boolean ignoreCase) {
		// Remove focus-looking effect on 'Next' button
		btNext.pseudoClassStateChanged(PseudoClassState.getPseudoClass("focused"), false);
		
		var controlText = control.getText();
		var initialCaretPos = control.getSelection().getStart();	// Prefer this to getCaretPosition() because it deals better with selections
		
		// Using pattern here because replaceAll() on its own MIGHT match regex as well (e.g. "." would replace all chars)
		Pattern pattern = Pattern.compile(text, Pattern.LITERAL | (ignoreCase ? Pattern.CASE_INSENSITIVE : 0) | Pattern.UNICODE_CASE);
		String subTextTemp = pattern.matcher(controlText.substring(0, initialCaretPos)).replaceAll(tfReplace.getText());
		int finalCaretPos = initialCaretPos + subTextTemp.length() - controlText.substring(0, initialCaretPos).length();
		
		var matcher = pattern.matcher(controlText);
		var count = matcher.results().count();
		if (count != 0) {
			control.setText(matcher.replaceAll(tfReplace.getText()));
			control.positionCaret(finalCaretPos);
			control.requestFollowCaret();
		}
		
		// Update labels
		lbFoundOccurrences.setText("");
		lbReplacedOccurrences.setText(count == 0 ? "String not found" : count + " match" + (count > 1 ? "es" : "") + " replaced");
	}

	/**
	 * Return the index of the query''s first character if present in the text, -1 otherwise.
	 * @param control
	 * @param findText
	 * @param ignoreCase
	 * @return index of first char if found, -1 otherwise
	 */
	private int findNext(final ScriptEditorControl control, final String findText, final boolean ignoreCase) {
		if (control == null || findText == null || findText.isEmpty())
			return -1;
		
		String text = control.getText();
		String toFind = null;
		if (ignoreCase) {
			toFind = findText.toLowerCase();
			text = text.toLowerCase();
		} else
			toFind = findText;
		if (!text.contains(toFind))
			return -1;
		
		int pos = control.getSelection().getEnd();
		int ind = text.substring(pos).indexOf(toFind);
		// If not found, loop around
		if (ind < 0)
			ind = text.indexOf(toFind);
		else
			ind = ind + pos;
		control.selectRange(ind, ind + toFind.length());
		control.requestFollowCaret();
		return ind;
		
	}
	
	/**
	 * Return the index of the query's first character if present in the text, -1 otherwise.
	 * @param control
	 * @param findText
	 * @param ignoreCase
	 * @return index of first char if found, -1 otherwise
	 */
	private int findPrevious(final ScriptEditorControl control, final String findText, final boolean ignoreCase) {
		lbFoundOccurrences.setText("String not found");
		if (control == null || findText == null || findText.isEmpty())
			return -1;
		
		// Remove focus-looking effect on 'Next' button
		btNext.pseudoClassStateChanged(PseudoClassState.getPseudoClass("focused"), false);
		
		lbReplacedOccurrences.setText("");
		
		String text = control.getText();
		String toFind = null;
		if (ignoreCase) {
			toFind = findText.toLowerCase();
			text = text.toLowerCase();
		} else
			toFind = findText;
		if (!text.contains(toFind))
			return -1;
		
		int pos = control.getSelection().getStart();
		int ind = pos == 0 ? text.lastIndexOf(toFind) : text.substring(0, pos).lastIndexOf(toFind);
		// If not found, loop around
		if (ind < 0)
			ind = text.lastIndexOf(toFind);
		control.selectRange(ind, ind + toFind.length());
		control.requestFollowCaret();
		
		lbFoundOccurrences.setText(ind == -1 ? "String not found" : "");
		return ind;
	}
}