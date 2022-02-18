package qupath.lib.gui.scripting;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Control;
import javafx.scene.control.IndexRange;
import javafx.scene.control.TextArea;

/**
 * SImple text area control (JavafX) with basic operations.
 * @author Pete Bankhead
 *
 */
class TextAreaControl implements ScriptEditorControl {
	
	private TextArea textArea;
	
	TextAreaControl(final TextArea textArea) {
		this.textArea = textArea;
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
	public Control getControl() {
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
	public void paste(String text) {
		if (text != null)
			textArea.replaceSelection(text);
//		textArea.paste();
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
	public ReadOnlyBooleanProperty focusedProperty() {
		return textArea.focusedProperty();
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
	public void setPopup(ContextMenu menu) {
		textArea.setContextMenu(menu);
	}

	@Override
	public void positionCaret(int index) {
		textArea.positionCaret(index);
	}
}
