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


package qupath.lib.scripting;

/**
 * Default attributes that can be set when running scripts.
 * Script implementations should try to set these where possible using {@link ScriptParameters}.
 * <p>
 * The intention is that, where supported, scripts can then access the information, 
 * e.g. for Groovy
 * <pre>{@code 
 * var filePath = getProperty(ScriptAttributes.FILE_PATH)
 * }</pre>
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class ScriptAttributes {
	
	/**
	 * Optional string args passed to the script.
	 */
	public static final String ARGS = "args";
	
	/**
	 * File path of the running script file.
	 */
	public static final String FILE_PATH = "qupath.script.file";
	
	/**
	 * Size of the current batch processing batch.
	 * Running a single script in isolation should be seen as batch processing 
	 * with a batch size of 1.
	 */
	public static final String BATCH_SIZE = "qupath.script.batchSize";
	
	/**
	 * Index of the current run when batch processing (starting at 0).
	 */
	public static final String BATCH_INDEX = "qupath.script.batchIndex";
	
	/**
	 * Boolean flag to indicate if the current script is the last in a batch.
	 */
	public static final String BATCH_LAST = "qupath.script.batchLast";

	/**
	 * Boolean flag to indicate whether changes to the image data should automatically be saved.
	 */
	public static final String BATCH_SAVE = "qupath.script.batchSave";

	
	// TODO: Consider adding in the future... but concern is they could cause memory leaks
	// for scripts that create UIs.
//	/**
//	 * Current project for the script.
//	 */
//	public static final String PROJECT = "qupath.script.project";
//	
//	/**
//	 * Current image data for the script.
//	 */
//	public static final String IMAGE_DATA = "qupath.script.imageData";
	
}