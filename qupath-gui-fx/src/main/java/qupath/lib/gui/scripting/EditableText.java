/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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


/**
 * Interface for objects that manage editable text, including a caret position and selection.
 * This is used to define some useful scripting functionality independently of any particular user interface.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public interface EditableText {
	
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
	 * Append the specified text.
	 * @param text the text to be appended
	 */
	public void appendText(final String text);
	
	/**
	 * Insert the specified text, replacing any existing selection.
	 * @param text the text to insert
	 */
	public void replaceSelection(String text);
		
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
	 * Set the caret position to the specified index
	 * @param index
	 */
	public void positionCaret(int index);
	
	/**
	 * Deselect any currently-selected text.
	 */
	public default void deselect() {
		selectRange(0, 0);
	}
	
	/**
	 * Get the selected text as a string.
	 * @return
	 */
	public String getSelectedText();
	
	/**
	 * Set the range of the selected text.
	 * @param startIdx
	 * @param endIdx
	 */
	public void selectRange(int startIdx, int endIdx);

	/**
	 * Get the starting position for any selection (inclusive).
	 * @return
	 */
	public int getSelectionStart();
	
	/**
	 * Get the ending position for any selection (exclusive).
	 * If this is equal to or less than {@link #getSelectionStart()} this means there is 
	 * no selection.
	 * @return
	 */
	public int getSelectionEnd();
	
	/**
	 * Get the selection length, or 0 if there is no selection.
	 * @return
	 */
	public default int getSelectionLength() {
		return Math.max(0, getSelectionEnd() - getSelectionStart());
	}
	
}
