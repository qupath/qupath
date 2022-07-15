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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.layout.Region;
import qupath.lib.gui.logging.TextAppendable;

/**
 * Basic script editor control.
 * The reason for its existence is to enable custom script editors to be implemented that provide additional functionality 
 * (e.g. syntax highlighting), but do not rely upon subclassing any specific JavaFX control.
 * <p>
 * Note: This is rather cumbersome, and may be removed in the future if the script editor design is revised.
 * 
 * @author Pete Bankhead
 */
public interface ScriptEditorControl extends TextAppendable {
	
	/**
	 * Text currently in the editor control.
	 * @return
	 */
	public StringProperty textProperty();
	
	/**
	 * Set all the text in the editor.
	 * @param text
	 */
	public void setText(final String text);

	/**
	 * Get all the text in the editor;
	 * @return
	 */
	public String getText();
	
	/**
	 * Deselect any currently-selected text.
	 */
	public void deselect();
	
	/**
	 * Get the range of the currently-selected text.
	 * @return
	 */
	public IndexRange getSelection();

	/**
	 * Set the range of the selected text.
	 * @param startIdx
	 * @param endIdx
	 */
	public void selectRange(int startIdx, int endIdx);

	/**
	 * Text currently selected in the editor control.
	 * @return
	 */
	public ObservableValue<String> selectedTextProperty();
	
	/**
	 * Get the value of {@link #selectedTextProperty()}.
	 * @return
	 */
	public String getSelectedText();
	
	/**
	 * Returns true if 'undo' can be applied to the control.
	 * @return
	 */
	public boolean isUndoable();
	
	/**
	 * Returns true if 'redo' can be applied to the control.
	 * @return
	 */
	public boolean isRedoable();
	
	/**
	 * Get the region representing this control, so it may be added to a scene.
	 * @return
	 */
	public Region getControl();
	
	/**
	 * Set the popup menu for this control.
	 * @param menu
	 */
	public void setPopup(ContextMenu menu);
	
	/**
	 * Request undo.
	 */
	public void undo();
	
	/**
	 * Request redo.
	 */
	public void redo();
	
	/**
	 * Request copy the current selection.
	 */
	public void copy();
	
	/**
	 * Request cut the current selection.
	 */
	public void cut();
	
	/**
	 * Request paste the specified text.
	 * @param text 
	 */
	public void paste(String text);
	
	@Override
	public void appendText(final String text);
	
	/**
	 * Request clear the contents of the control.
	 */
	public void clear();
	
	/**
	 * Get the current caret position.
	 * @return
	 */
	public int getCaretPosition();
	
	/**
	 * Request inserting the specified text.
	 * @param pos position to insert the text
	 * @param text the text to insert
	 */
	public void insertText(int pos, String text);

	/**
	 * Request deleting the text within the specified range.
	 * @param startIdx
	 * @param endIdx
	 */
	public void deleteText(int startIdx, int endIdx);

	/**
	 * Focused property of the control.
	 * @return
	 */
	public ReadOnlyBooleanProperty focusedProperty();
	
	/**
	 * Set the caret position to the specified index
	 * @param index
	 */
	public void positionCaret(int index);
	
	/**
	 * Request wordwrap.
	 * @return
	 */
	public BooleanProperty wrapTextProperty();

	/**
	 * Request that the X and Y scrolls are adjusted to ensure the caret is visible.
	 * <p>
	 * This method does nothing by default. 
	 * This means that a class extending this interface must specifically implement this method if a different behavior is expected.
	 */
	public default void requestFollowCaret() {
		return;
	}
}