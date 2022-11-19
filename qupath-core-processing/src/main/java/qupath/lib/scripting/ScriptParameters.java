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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.event.Level;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.projects.Project;

/**
 * Class that stores key information that is useful for running scripts.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class ScriptParameters {
	
	private List<Class<?>> defaultImports;
	
	private List<Class<?>> defaultStaticImports;
	
	private File file;
	private String script;
	
	private Project<?> project;
	private ImageData<?> imageData;
	
	private boolean requestHierarchyUpdate = true;
	
	private boolean useCompiled = false;
	
	private Writer writer;
	private Writer errorWriter;
	
	private String[] args;
	
	private int batchSize = 1;
	private int batchIndex = 0;
	private boolean batchSaveResult;
	
	private ScriptParameters() {}
	
	
	/**
	 * Get the file where the script is located.
	 * This can return null if the script is purely in-memory.
	 * <p>
	 * Note that if it is not null, the contents of the file do not have to match the result of 
	 * {@link #getScript()}, e.g. if the script has unsaved changes.
	 * It can nevertheless be helpful to know the location of the file to handle relative paths, 
	 * if required.
	 * 
	 * @return
	 * @see #getScript()
	 */
	public File getFile() {
		return file;
	}
	
	/**
	 * Get the text of the script.
	 * If this has not been set explicitly, the script is read from {@link #getFile()}.
	 * If there is no script and no valid file, a {@link RuntimeException} will be thrown.
	 * @return
	 */
	public String getScript() {
		if (script == null) {
			if (file != null && file.exists()) {
				try {
					return GeneralTools.readFileAsString(file.getAbsolutePath());
				} catch (IOException e) {
					throw new RuntimeException("Unable to read script from " + file, e);
				}
			}
			throw new RuntimeException("No file or script found!");
		}
		return script;
	}
	
	/**
	 * Get the current project that should be used with the script.
	 * This may be null.
	 * @return
	 */
	public Project<?> getProject() {
		return project;
	}
	
	/**
	 * Get the current {@link ImageData} that should be used with the script.
	 * This may be null.
	 * @return
	 */
	public ImageData<?> getImageData() {
		return imageData;
	}
	
	/**
	 * Get the number of images being batch processed, or 1 if just a single image is being processed.
	 * <p>
	 * This is useful in combination with {@link #getBatchIndex()} to write scripts that (for example)
	 * can adapt to whether they are the first or last to run in a batch.
	 * @return
	 * @see #getBatchIndex()
	 */
	public int getBatchSize() {
		return batchSize;
	}
	
	/**
	 * Get the index of the current image for batch processing, where 0 is the first and {@code getBatchSize() - 1} 
	 * is the last.
	 * <p>
	 * This is useful in combination with {@link #getBatchSize()} to write scripts that (for example)
	 * can adapt to whether they are the first or last to run in a batch.
	 * @return
	 * @see #getBatchSize()
	 */
	public int getBatchIndex() {
		return batchIndex;
	}
	
	/**
	 * Get whether changes to the image data should be saved.
	 * @return true if changes should be saved after running the script, false if they should not or if it is unknown
	 */
	public Boolean getBatchSaveResult() {
		return batchSaveResult;
	}
	
	/**
	 * Get an optional array of string arguments passed to the script.
	 * @return
	 */
	public String[] getArgs() {
		return args == null ? new String[0] : args.clone();
	}
	
	/**
	 * Get the main writer for script output.
	 * @return
	 */
	public Writer getWriter() {
		return writer;
	}

	/**
	 * Get the error writer for script output.
	 * @return
	 */
	public Writer getErrorWriter() {
		return errorWriter;
	}

	/**
	 * Get default imports that should be included with the script, where possible.
	 * Not all languages may support adding default imports.
	 * <p>
	 * The purpose of this is to make scripting easier and more intuitive, resembling a 
	 * macro language and without a need for the user to know much about QuPath's classes.
	 * @return
	 * @see #getDefaultStaticImports()
	 */
	public List<Class<?>> getDefaultImports() {
		return defaultImports;
	}
	
	/**
	 * Request that any script should be compiled before being evaluated, 
	 * and previously compiled versions reused when possible.
	 * <p>
	 * This is not always supported, but where it is it may improve performance
	 * if the same script is evaluated many times.
	 * It may also mean that any errors are caught earlier.
	 * <p>
	 * @return
	 */
	public boolean useCompiled() {
		return useCompiled;
	}
	
	/**
	 * Request whether to fire an update event for the object hierarchy, if an image data 
	 * object is available.
	 * <p>
	 * The purpose of this is to avoid requiring the caller to fire a hierarchy update as 
	 * boilerplate at the end of a script.
	 * @return
	 */
	public boolean doUpdateHierarchy() {
		return requestHierarchyUpdate;
	}

	/**
	 * Get default static imports that should be included with the script, where possible.
	 * Not all languages may support adding default imports.
	 * <p>
	 * The purpose of this is to make scripting easier and more intuitive, resembling a 
	 * macro language and without a need for the user to know much about QuPath's classes.
	 * @return
	 * @see #getDefaultImports()
	 */
	public List<Class<?>> getDefaultStaticImports() {
		return defaultStaticImports;
	}
	
	/**
	 * Create a new builder for {@link ScriptParameters}.
	 * @return
	 */
	public static Builder builder() {
		return new Builder();
	}
	
	
	/**
	 * Builder class for {@link ScriptParameters}.
	 */
	public static class Builder {
		
		private List<Class<?>> defaultImports = Collections.emptyList();
		
		private List<Class<?>> defaultStaticImports = Collections.emptyList();
		
		private File file;
		private String script;
		
		private Project<?> project;
		private ImageData<?> imageData;
		
		private boolean useCompiled = false;
		
		private Writer writer;
		private Writer errorWriter;
		
		private String[] args;
		
		private boolean requestHierarchyUpdate = true;
		
		private int batchSize = 1;
		private int batchIndex = 0;
		private boolean batchSaveResult = false;
		
		private Builder() {}
		
		public Builder setDefaultImports(Collection<Class<?>> imports) {
			this.defaultImports = imports == null ? Collections.emptyList() : new ArrayList<>(imports);
			return this;
		}
		
		public Builder setDefaultStaticImports(Collection<Class<?>> imports) {
			this.defaultStaticImports = imports == null ? Collections.emptyList() : new ArrayList<>(imports);
			return this;
		}
		
		public Builder setFile(File file) {
			this.file = file;
			return this;
		}

		public Builder setScript(String script) {
			this.script = script;
			return this;
		}
		
		/**
		 * Set the output and error writers to use {@code System.out} and {@code System.err}.
		 * @return
		 */
		public Builder setSystemWriters() {
			this.writer = new PrintWriter(System.out);
			this.errorWriter = new PrintWriter(System.err);
			return this;
		}
		
		/**
		 * Set the output and error writers to append to the specified logger.
		 * @param logger 
		 * @return
		 */
		public Builder useLogWriters(Logger logger) {
			this.writer = LoggingTools.createLogWriter(logger, Level.INFO);
			this.errorWriter = LoggingTools.createLogWriter(logger, Level.ERROR);
			return this;
		}
		
		/**
		 * Set the output and error writers to append to the default logger.
		 * @return
		 */
		public Builder useLogWriters() {
			return useLogWriters(null);
		}
		
		/**
		 * Set the main output writer.
		 * @param writer
		 * @return
		 */
		public Builder setWriter(Writer writer) {
			this.writer = writer;
			return this;
		}
		
		/**
		 * Set the main error writer.
		 * @param writer
		 * @return
		 */
		public Builder setErrorWriter(Writer writer) {
			this.errorWriter = writer;
			return this;
		}
				
		/**
		 * Set the batch size for batch processing (default is 1).
		 * @param batch
		 * @return
		 */
		public Builder setBatchSize(int batch) {
			this.batchSize = batch;
			return this;
		}
		
		/**
		 * Set the current image index for batch processing (default is 0).
		 * @param ind
		 * @return
		 */
		public Builder setBatchIndex(int ind) {
			this.batchIndex = ind;
			return this;
		}
		
		/**
		 * Specify whether the script that is running should save results or not.
		 * @param doSave
		 * @return
		 */
		public Builder setBatchSaveResult(boolean doSave) {
			this.batchSaveResult = doSave;
			return this;
		}
		
		/**
		 * Set optional string args to pass to the script.
		 * @param args
		 * @return
		 */
		public Builder setArgs(String[] args) {
			this.args = args.clone();
			return this;
		}
		
		/**
		 * Set the current project for the script.
		 * @param project
		 * @return
		 */
		public Builder setProject(Project<?> project) {
			this.project = project;
			return this;
		}
		
		/**
		 * Set the current image data for the script.
		 * @param imageData
		 * @return
		 */
		public Builder setImageData(ImageData<?> imageData) {
			this.imageData = imageData;
			return this;
		}
		
		/**
		 * Optionally request a hierarchy update event after running a script
		 * (default is true for scripts that operate on image data).
		 * @param requestUpdate
		 * @return
		 */
		public Builder doUpdateHierarchy(boolean requestUpdate) {
			this.requestHierarchyUpdate = requestUpdate;
			return this;
		}
		
		/**
		 * Request that the script is compiled before being evaluated, 
		 * or a previously compiled version is used where available.
		 * @param useCompiled
		 * @return
		 */
		public Builder useCompiled(boolean useCompiled) {
			this.useCompiled = useCompiled;
			return this;
		}
		
		/**
		 * Build the {@link ScriptParameters} with the current options.
		 * @return
		 * @throws IllegalArgumentException if neither file nor script are set
		 */
		public ScriptParameters build() throws IllegalArgumentException {
			var params = new ScriptParameters();
			
			params.defaultImports = this.defaultImports;
			params.defaultStaticImports = this.defaultStaticImports;
			params.file = this.file;
			if (this.script == null && this.file == null) {
				throw new IllegalArgumentException("Either 'script' or 'file' must not be null!");
			}
			params.script = this.script;
			
			params.project = this.project;
			params.imageData = this.imageData;
			
			params.writer = this.writer == null ? new PrintWriter(System.out) : this.writer;
			params.errorWriter = this.errorWriter == null ? new PrintWriter(System.err) : this.errorWriter;
			
			params.requestHierarchyUpdate = this.requestHierarchyUpdate;
			
			params.useCompiled = useCompiled;
			
			params.args = this.args;
			params.batchSize = this.batchSize;
			params.batchIndex = this.batchIndex;
			params.batchSaveResult = this.batchSaveResult;
			
			return params;
		}
		
	}
	

}
