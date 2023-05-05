/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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


package qupath.lib.gui;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javafx.scene.control.ButtonType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.fx.dialogs.FileChoosers;
import qupath.lib.gui.commands.Commands;
import qupath.fx.dialogs.Dialogs;

/**
 * Copy files to an output directory, prompting the user if necessary.
 * <p>
 * This is intended for interactive use; if there is an exception, the 
 * user will be notified by an error message dialog.
 * <p>
 * 
 * @author Pete Bankhead
 * @since v0.5.0
 */
public class FileCopier {
	
	private static final Logger logger = LoggerFactory.getLogger(FileCopier.class);
	
	private static enum OverwriteType { YES, NO, PROMPT }
	
	private FileCopier.OverwriteType overwrite = OverwriteType.PROMPT;

	private boolean promptForUserDirectoryIfMissing = false;
	private List<Path> paths = new ArrayList<>();
	
	private String title = "Copy files";
	
	private Path outputPath;
	
	/**
	 * Title for any dialog.
	 * @param title
	 * @return
	 */
	public FileCopier title(String title) {
		this.title = title;
		return this;
	}
	
	/**
	 * Specify that the copying is relative to the QuPath user directory.
	 * This is useful if copying files to the user directory itself, or a subdirectory.
	 * If this method is called, then the user will be prompted to create a user directory 
	 * if one does not already exist.
	 * @return
	 */
	public FileCopier relativeToUserDirectory() {
		this.promptForUserDirectoryIfMissing = true;
		return this;
	}
	
	/**
	 * Name of the output directory.
	 * If {@link #relativeToUserDirectory()} is selected, this should be given 
	 * relative to the QuPath user directory; otherwise, it should be an absolute path.
	 * @param name
	 * @return
	 * @see #outputPath(Path)
	 */
	public FileCopier outputPath(String name) {
		return outputPath(Paths.get(name));
	}
	
	/**
	 * Path representing the output directory.
	 * If {@link #relativeToUserDirectory()} is selected, this should be given 
	 * relative to the QuPath user directory; otherwise, it should be an absolute path.
	 * @param name
	 * @return
	 * @see #outputPath(String)
	 */
	public FileCopier outputPath(Path name) {
		this.outputPath = name;
		return this;
	}
	
	/**
	 * Collection of files to copy.
	 * @param files
	 * @return
	 * @see #inputPaths(Collection)
	 */
	public FileCopier inputFiles(Collection<File> files) {
		for (var f : files) {
			this.paths.add(f.toPath());				
		}
		return this;
	}
	
	/**
	 * Collection of paths representing files to copy.
	 * @param paths
	 * @return
	 * @see #inputFiles(Collection)
	 */
	public FileCopier inputPaths(Collection<Path> paths) {
		this.paths.addAll(paths);
		return this;
	}
	
	
	private Path getOutputPathOrNull() throws IOException {
		if (promptForUserDirectoryIfMissing) {
			var dir = Commands.requestUserDirectory(true);
			if (dir == null)
				return null;
			if (outputPath == null)
				return dir.toPath();
			var path = dir.toPath().resolve(outputPath);
			// We are free to create user subdirectories without prompts
			if (!Files.exists(path))
				Files.createDirectories(path);
			return path;
		} else if (outputPath != null){
			if (!Files.exists(outputPath)) {
				if (Dialogs.showYesNoDialog(title, "Create directory\n" + outputPath))
					Files.createDirectories(outputPath);
				else
					return null;
			}
			return outputPath;
		} else {
			var dir = FileChoosers.promptForDirectory(title, null);
			if (dir == null)
				return null;
			else
				return dir.toPath();
		}
	}
	
			
	/**
	 * Perform the copying.
	 * Note that any exceptions will be caught and displayed to the user in an error dialog.
	 */
	public void doCopy() {
		if (paths.isEmpty()) {
			logger.debug("No files to copy!");
			return;
		}

		Path dir;
		try {
			dir = getOutputPathOrNull();
			if (dir == null) {
				logger.warn("Cannot copy files - no output directory available");
				return;
			}
			if (!Files.isDirectory(dir)) {
				Dialogs.showErrorMessage(title, dir + "\nis not a directory!");
				return;
			}
		} catch (Exception e) {
			Dialogs.showErrorMessage(title, e.getLocalizedMessage());
			return;
		}

		// Copy all files into extensions directory
		boolean overwriteExisting;
		switch (overwrite) {
		case NO:
			overwriteExisting = false;
			break;
		case YES:
			overwriteExisting = true;
			break;
		case PROMPT:
		default:
			long nExisting = paths.stream()
				.map(p -> dir.resolve(p.getFileName()))
				.filter(p -> Files.exists(p))
				.count();
			if (nExisting == 0) {
				overwriteExisting = true;
				break;
			} else {
				var response = Dialogs.showYesNoCancelDialog(title, "Overwrite existing files?");
				if (response == ButtonType.YES)
					overwriteExisting = true;
				else if (response == ButtonType.NO)
					overwriteExisting = false;
				else {
					logger.warn("Files will not be copied (user cancelled at overwrite prompt)");
					return;
				}
			}
			break;
		}
		
		int nCopied = 0;
		for (Path source : paths) {
			Path destination = dir.resolve(source.getFileName());
			if (source.equals(destination)) {
				logger.info("Skipping {} (source and destination are the same)", source);
				continue;
			}
			if (overwriteExisting || !Files.exists(destination)) {
				try {
					logger.info("Copying {} -> {}", source, destination);
					Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
					nCopied++;
				} catch (IOException e) {
					Dialogs.showErrorMessage("Copy error", source + "\ncould not be copied, sorry");
					logger.error("Could not copy file {}", source, e);
					return;
				}
			} else {
				logger.warn("Skipping copy {}", source);
			}
		}
		if (nCopied == 1)
			Dialogs.showInfoNotification(title, "Copied 1 file");
		else if (nCopied > 1)
			Dialogs.showInfoNotification(title, String.format("Copied %d files", nCopied));
	}
	
	
}