/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.helpers.dialogs;

import java.io.File;
import java.util.List;


/**
 * Interface to help with prompting users to select files or directories.
 * The intension is that sub-classes might use different kinds of (possibly platform-specific) file choosers.
 * 
 * In truth, having this as an interface made most sense back in the Swing days - whenever it was often the 
 * case that the Swing and AWT dialogs were quite different in appearance (and pleasantness).
 * 
 * With JavaFX that's not so important - however it is maintained as an interface in case alternative dialogs 
 * might be introduced in the future.
 * 
 * @author Pete Bankhead
 *
 */
public interface DialogHelper {

	/**
	 * Set the last directory, which will be used as the base directory in future file choosers.
	 * If a file (not directory) is given, the parent directory will be used.
	 * If the file does not exist, the last directory will be set to null.
	 * 
	 * @param dir
	 */
	public void setLastDirectory(File dir);

	public File getLastDirectory();
	
	/**
	 * Prompt to open a list of files.
	 * 
	 * @param title
	 * @param dirBase
	 * @param filterDescription
	 * @param exts
	 * @return
	 */
	public List<File> promptForMultipleFiles(String title, File dirBase, String filterDescription, String[] exts);
	
	/**
	 * Prompt user to select a directory.
	 * 
	 * @param dirBase - Base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @return
	 */
	public abstract File promptForDirectory(File dirBase);

	/**
	 * Prompt the user for a file with some kind of file dialog.
	 * @param title - The title to display for the dialog (may be null to use default)
	 * @param dirBase - Base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @param filterDescription - description to (possibly) show for the file name filter (may be null)
	 * @param exts - array of file extensions, not including . - (may be null if no filter should be used)
	 * 
	 * @return the File selected by the user, or 'null' if the dialog was cancelled
	 */
	public abstract File promptForFile(String title, File dirBase, String filterDescription, String[] exts);

	/**
	 * Prompt user to select a file.
	 * 
	 * @param dirBase - Base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @return
	 */
	public File promptForFile(File dirBase);
	
	/**
	 * Prompt user to select a file path to save.
	 * @param title - The title to display for the dialog (may be null)
	 * @param dirBase - The base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * 
	 * @return
	 */
	public File promptToSaveFile(String title, File dirBase, String defaultName, String filterName, String ext);
	
	/**
	 * Prompt user to select a file or input a URL.
	 * 
	 * @param defaultPath - Default path to display - may be null
	 * @param dirBase - Base directory to display; if null or not an existing directory, the value under getLastDirectory() should be used
	 * @param filterDescription - description to (possibly) show for the file name filter (may be null)
	 * @param exts - array of file extensions, not including . - (may be null if no filter should be used)
	 * @return The path to the file or URL, or null if no path was provided.
	 */
	public String promptForFilePathOrURL(String defaultPath, File dirBase, String filterDescription, String[] exts);

}
