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

package qupath.lib.gui.commands;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.commands.interfaces.PathCommand;

/**
 * Command to display some basic system info - useful to see memory etc.
 * 
 * @author Pete Bankhead
 *
 */
public class ShowSystemInfoCommand implements PathCommand {
	
	final private static Logger logger = LoggerFactory.getLogger(ShowSystemInfoCommand.class);
	
	private QuPathGUI qupath;
	
	private Stage dialog;
	private TextArea textArea;
	
	public ShowSystemInfoCommand(final QuPathGUI qupath) {
		this.qupath = qupath;
	}
	
	@Override
	public void run() {
		logger.trace("Running system info command");
//		if (dialog == null)
			createDialog();
		updateText();
		if (dialog.isShowing())
			dialog.toFront();
		else
			dialog.show();
	}
	
	
	private void createDialog() {
		dialog = new Stage();
		dialog.initOwner(qupath.getStage());
		dialog.initModality(Modality.APPLICATION_MODAL);
		dialog.setTitle("System info");
		
		dialog.focusedProperty().addListener((v, o, n) -> {
			if (n)
				updateText();
		});
		
		textArea = new TextArea();
		textArea.setPrefColumnCount(40);
		textArea.setPrefRowCount(25);
		textArea.setEditable(false);
		
		BorderPane pane = new BorderPane();
		pane.setPadding(new Insets(10, 10, 10, 10));
		pane.setCenter(textArea);
		
		dialog.setScene(new Scene(pane));
	}
	
	
	/**
	 * Get a multi-line string containing some system info, created mostly by querying system properties &amp; the current Runtime.
	 * 
	 * @return
	 */
	public static String getInfoString() {
		StringBuilder sb = new StringBuilder();

		Runtime rt = Runtime.getRuntime();
		rt.gc();
		
		// Java info
		sb.append("Java version:\t\t").append(System.getProperty("java.version")).append("\n");
//		sb.append("Java vendor:\t\t").append(System.getProperty("java.vendor")).append("\n");
		sb.append("Java vendor: \t\t").append(System.getProperty("java.vendor")).append("  -  ").
			append(System.getProperty("java.vendor.url")).append("\n");
		sb.append("Java home:   \t\t").append(System.getProperty("java.home")).append("\n");

		
		// Operating system info
		sb.append("\n");
		sb.append("Operating system:\t\t").append(System.getProperty("os.name")).append("  -  ").append(System.getProperty("os.version")).append("\n");
//		sb.append("Version:         \t\t").append(System.getProperty("os.version")).append("\n");
		sb.append("Architecture:    \t\t").append(System.getProperty("os.arch")).append("\n");
		
		// System info
		sb.append("\n");
		sb.append("Number of available processors:\t\t").append(rt.availableProcessors()).append("\n");
		
		sb.append("\n");
		
		// Memory info
		int toMB = (1024*1024);
		sb.append("Memory already used by JVM:  \t\t").append((rt.totalMemory() - rt.freeMemory())/toMB).append(" MB").append("\n");
//		sb.append("Free memory available to JVM: \t\t").append(rt.freeMemory()/toMB).append(" MB").append("\n");
		sb.append("Total memory available to JVM:\t\t").append(rt.totalMemory()/toMB).append(" MB").append("\n");
		sb.append("Max memory JVM may try to use:\t\t").append(rt.maxMemory()/toMB).append(" MB").append("\n");
		if (rt.maxMemory()/toMB < 4096)
			sb.append("--- WARNING: Max memory is quite low (< 4GB)  - may not be enough to run full whole slide analysis.").append("\n");
		if ((rt.maxMemory() - rt.freeMemory())/toMB < 1024)
			sb.append("--- WARNING: Memory almost all in use (< 1GB remaining).").append("\n");
		
		
//		// File system info - excluded (can be very slow to access!)
//		sb.append("\n");
//	    for (File root : File.listRoots()) {
//	      sb.append("File system: \t\t" + root.getAbsolutePath()).append("\n");
//	      sb.append("Total space: \t\t" + root.getTotalSpace()/toMB).append(" MB").append("\n");
//	      sb.append("Free space:  \t\t" + root.getFreeSpace()/toMB).append(" MB").append("\n");
//	      sb.append("Usable space:\t\t" + root.getUsableSpace()/toMB).append(" MB").append("\n");
//	    }
	    
	    // Show paths (at the end, since they may be rather long)
		sb.append("\n");
		sb.append("Library path:");
		for (String p : System.getProperty("java.library.path").split(File.pathSeparator))
			sb.append("\n      ").append(p);
		sb.append("\n\n");
		sb.append("Class path:");
		for (String p : System.getProperty("java.class.path").split(File.pathSeparator))
			sb.append("\n      ").append(p);
		
		return sb.toString();
	}
	
	private void updateText() {
		
		String buildString = qupath.getBuildString();
		if (buildString == null)
			textArea.setText(getInfoString());
		else
			textArea.setText(buildString + "\n\n" + getInfoString());
	    
//	    
//		int row = 0;
//		int inc = 1;
//		for (QuPathExtension extension : qupath.getLoadedExtensions()) {
//			addEntry(pane, new QuPathExtensionEntry(extension), row);
//			row += inc;
//		}
//		for (ImageServerBuilder<?> builder : ImageServerProvider.getInstalledImageServerBuilders()) {
//			addEntry(pane, new QuPathExtensionEntry(builder), row);
//			row += inc;
//		}
	}

}
