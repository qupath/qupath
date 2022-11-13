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

import java.util.Objects;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;

/**
 * Simple text area control (JavaFX) with basic operations and no styling support.
 * 
 * @author Pete Bankhead
 */
public class TextAreaControl implements ScriptEditorControl<TextArea> {
	
	private TextArea textArea;
	
	/**
	 * Constructor to create a new text area and wrap it in a {@link TextAreaControl}.
	 * @param isEditable whether the text area should be editable or not
	 */
	public TextAreaControl(boolean isEditable) {
		this(new TextArea(), isEditable);
	}
	
	/**
	 * Constructor to wrap an existing text area and wrap it in a {@link TextAreaControl}.
	 * @param textArea the text area to wrap
	 * @param isEditable whether the text area should be editable or not
	 */
	public TextAreaControl(final TextArea textArea, boolean isEditable) {
		Objects.requireNonNull(textArea);
		this.textArea = textArea;
		this.textArea.setEditable(isEditable);
	}

	@Override
	public StringProperty textProperty() {
		return textArea.textProperty();
	}

	@Override
	public void setText(String text) {
		textArea.setText(text);
	}

	@Override
	public String getText() {
		return textArea.getText();
	}

	@Override
	public ObservableValue<String> selectedTextProperty() {
		return textArea.selectedTextProperty();
	}

	@Override
	public String getSelectedText() {
		return textArea.getSelectedText();
	}

	@Override
	public TextArea getRegion() {
		return textArea;
	}

	@Override
	public boolean isUndoable() {
		return textArea.isUndoable();
	}

	@Override
	public boolean isRedoable() {
		return textArea.isRedoable();
	}

	@Override
	public void undo() {
		textArea.undo();
	}

	@Override
	public void redo() {
		textArea.redo();
	}

	@Override
	public void copy() {
		textArea.copy();
	}

	@Override
	public void cut() {
		textArea.cut();
	}

	@Override
	public void paste() {
		textArea.paste();
	}

	@Override
	public void clear() {
		textArea.clear();
	}

	@Override
	public void appendText(final String text) {
		textArea.appendText(text);
	}
	
	@Override
	public int getCaretPosition() {
		return textArea.getCaretPosition();
	}
	
	@Override
	public void insertText(int pos, String text) {
		textArea.insertText(pos, text);
	}
	
	@Override
	public void deleteText(int startIdx, int endIdx) {
		textArea.deleteText(startIdx, endIdx);
	}

	@Override
	public void deselect() {
		textArea.deselect();
	}

	@Override
	public IndexRange getSelection() {
		return textArea.getSelection();
	}

	@Override
	public void selectRange(int startIdx, int endIdx) {
		textArea.selectRange(startIdx, endIdx);
	}
	
	@Override
	public BooleanProperty wrapTextProperty() {
		return textArea.wrapTextProperty();
	}

	@Override
	public void positionCaret(int index) {
		textArea.positionCaret(index);
	}

	@Override
	public void replaceSelection(String text) {
		textArea.replaceSelection(text);
	}
	
	@Override
	public void setContextMenu(ContextMenu menu) {
		textArea.setContextMenu(menu);
	}

	@Override
	public ContextMenu getContextMenu() {
		return textArea.getContextMenu();
	}
	
	@Override
	public ReadOnlyIntegerProperty caretPositionProperty() {
		return textArea.caretPositionProperty();
	}
	
}
