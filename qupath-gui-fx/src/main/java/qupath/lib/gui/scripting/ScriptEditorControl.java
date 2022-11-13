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
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.layout.Region;
import qupath.lib.gui.logging.TextAppendable;

/**
 * Basic script editor control using JavaFX.
 * The reason for its existence is to enable custom script editors to be implemented that provide additional functionality 
 * (e.g. syntax highlighting), but do not rely upon subclassing any specific JavaFX control.
 * <p>
 * Note: This is rather cumbersome, and may be removed in the future if the script editor design is revised.
 * 
 * @author Pete Bankhead
 * @param <T> the tile of component used for display
 */
public interface ScriptEditorControl<T extends Region>  extends TextAppendable, EditableText {
	
	/**
	 * Text currently in the editor control.
	 * @return
	 */
	public StringProperty textProperty();
		
	/**
	 * Get the range of the currently-selected text.
	 * @return
	 */
	public IndexRange getSelection();
	
	@Override
	public default int getSelectionStart() {
		return getSelection().getStart();
	}

	@Override
	public default int getSelectionEnd() {
		return getSelection().getEnd();
	}
	
	/**
	 * Request paste from the system clipboard.
	 */
	public void paste();


	/**
	 * Text currently selected in the editor control.
	 * @return
	 */
	public ObservableValue<String> selectedTextProperty();
		
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
	public T getRegion();
	
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
	
	/**
	 * Property for the current caret position.
	 * @return
	 * @see #getCaretPosition()
	 * @see #positionCaret(int)
	 */
	public ReadOnlyIntegerProperty caretPositionProperty();
	
	/**
	 * Set the context menu for the control.
	 * @param menu
	 */
	public void setContextMenu(ContextMenu menu);
	
	/**
	 * Get the context menu for the control.
	 * @return
	 */
	public ContextMenu getContextMenu();
	
}