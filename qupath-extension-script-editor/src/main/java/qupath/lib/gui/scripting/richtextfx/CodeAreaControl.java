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

import java.util.Objects;

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
import qupath.lib.gui.scripting.ScriptEditorControl;

/**
 * Code area control using RichTextFX.
 * 
 * @author Pete Bankhead
 */
public class CodeAreaControl implements ScriptEditorControl<VirtualizedScrollPane<CodeArea>> {
	
	private VirtualizedScrollPane<CodeArea> scrollpane;
	private CodeArea textArea;
	private StringProperty textProperty = new SimpleStringProperty();
	
	private ContextMenu contextMenu;
	
	CodeAreaControl(boolean isEditable) {
		this(new CodeArea(), isEditable);
	}
	
	CodeAreaControl(final CodeArea codeArea, boolean isEditable) {
		Objects.requireNonNull(codeArea);
		this.textArea = codeArea;
		this.textArea.setEditable(isEditable);
		textArea.textProperty().addListener((o, v, n) -> textProperty.set(n));
		textProperty.addListener((o, v, n) -> {
			if (n.equals(textArea.getText()))
				return;
			textArea.clear();
			textArea.insertText(0, n);
		});
		scrollpane = new VirtualizedScrollPane<>(textArea);
	}
	
	@Override
	public StringProperty textProperty() {
		return textProperty;
	}

	@Override
	public void setText(String text) {
		textArea.replaceText(text);
		requestFollowCaret();
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
	public VirtualizedScrollPane<CodeArea> getRegion() {
		return scrollpane;
	}

	@Override
	public boolean isUndoable() {
		return textArea.isUndoAvailable();
	}

	@Override
	public boolean isRedoable() {
		return textArea.isRedoAvailable();
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
	public void appendText(final String text) {
		textArea.appendText(text);
		requestFollowCaret();
	}

	@Override
	public void clear() {
		textArea.clear();
	}
	
	@Override
	public int getCaretPosition() {
		return textArea.getCaretPosition();
	}
	
	@Override
	public void insertText(int pos, String text) {
		textArea.insertText(pos, text);
		requestFollowCaret();
	}
	
	@Override
	public void deleteText(int startIdx, int endIdx) {
		textArea.deleteText(startIdx, endIdx);
		requestFollowCaret();
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
		textArea.moveTo(index);
}

	@Override
	public void requestFollowCaret() {
		textArea.requestFollowCaret();
	}
	
	@Override
	public void replaceSelection(String text) {
		textArea.replaceSelection(text);
		requestFollowCaret();
	}
	
	@Override
	public void setContextMenu(ContextMenu menu) {
		// Try this approach, because otherwise some styling feeds through to the context menu 
		// & can look weird (e.g. making it use a monospaced font)
		this.contextMenu = menu;
		textArea.setContextMenu(null);
		textArea.setOnContextMenuRequested(e -> {
			var popup = textArea.getContextMenu() == null ? contextMenu : textArea.getContextMenu();
			if (popup != null)
				popup.show(textArea.getScene().getWindow(), e.getScreenX(), e.getScreenY());
		});
	}

	@Override
	public ContextMenu getContextMenu() {
		var popup = textArea.getContextMenu();
		if (popup != null)
			return popup;
		return contextMenu;
	}

	private ReadOnlyIntegerProperty caretReadOnly;
	
	@Override
	public ReadOnlyIntegerProperty caretPositionProperty() {
		if (caretReadOnly == null) {
			var caret = new SimpleIntegerProperty();
			caret.bind(textArea.caretPositionProperty());
			caretReadOnly = IntegerProperty.readOnlyIntegerProperty(caret);
		}
		return caretReadOnly;
	}

}
