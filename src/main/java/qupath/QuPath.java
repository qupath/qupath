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

package qupath;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Callable;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathApp;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.tma.QuPathTMAViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.writers.ome.OMEPyramidWriter;
import qupath.lib.io.PathIO;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.scripting.QP;

/**
 * Main QuPath launcher.
 * 
 * @author Pete Bankhead
 *
 */
@Command(name = "qupath", subcommands = {HelpCommand.class, OMEPyramidWriter.ConvertCommand.class, ScriptCommand.class}, footer = "\nCopyright(c) 2020", mixinStandardHelpOptions = true, version = "qupath v0.2.0")
public class QuPath implements Callable {
	
	private final static Logger logger = LoggerFactory.getLogger(QuPath.class);
	
	@Option(names = {"-t", "--tma"}, description = "Launch standalone viewer for looking at TMA summary results.")
	boolean tma;
	
	@Parameters()
	String[] unmatched;
	
	/**
	 * Main class to launch QuPath.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		logger.info("Launching QuPath with args: {}", String.join(", ", args));
		int exitCode = new CommandLine(new QuPath()).execute(args);
		System.exit(exitCode);
	}
	
	@Override
	public Object call() throws Exception {
		if (tma)
			QuPathTMAViewer.launch(QuPathTMAViewer.class, unmatched);
		else
			QuPathApp.launch(QuPathApp.class, unmatched);
		return null;
	}

}


// TODO: should script only end with .groovy or can it end with something else?
@Command(name = "script", description = "Runs script for a given image or project.", footer = "\nCopyright(c) 2020", mixinStandardHelpOptions = true)
class ScriptCommand implements Runnable {
	
	final private static Logger logger = LoggerFactory.getLogger(ScriptCommand.class);
	
	@Parameters(index = "0", description = "Path to the input file (image/project).", paramLabel = "input")
	private String inputFile;
	
	@Parameters(index = "1", description = "Path to the script file (.groovy).", paramLabel = "script")
	private String scriptFile;

	@Override
	public void run() {
		try {
			if (inputFile == null)
				throw new IOException("File must be a valid image/project file: " + inputFile);
			if (scriptFile == null || scriptFile.equals("") || !scriptFile.endsWith(".groovy"))
				throw new IOException("File must be a valid script file (.groovy): " + scriptFile);
			
			ImageData<BufferedImage> imageData;
			
			if (inputFile.toLowerCase().endsWith(ProjectIO.getProjectExtension())) {
				Project<BufferedImage> project = ProjectIO.loadProject(new File(inputFile), null);
				for (var entry: project.getImageList()) {
					ImageServer<BufferedImage> server = entry.getServerBuilder().build();
					imageData = new ImageData<>(server);
					
					try {
						System.out.println(runScript(imageData));
					} catch (Exception e) {
						logger.error("Error running script for image:", entry.getImageName(),":", e);
					}
					server.close();
				}
			} else if (inputFile.toLowerCase().endsWith(PathPrefs.getSerializationExtension())) {
				imageData = PathIO.readImageData(new File(inputFile), null, null, BufferedImage.class);
				System.out.println(runScript(imageData));
			} else {
				ImageServer<BufferedImage> server = ImageServerProvider.buildServer(inputFile, BufferedImage.class);
				imageData = new ImageData<>(server);
				System.out.println(runScript(imageData));
				server.close();
			}
			
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		}
	}
	
	private Object runScript(ImageData<BufferedImage> imageData) throws IOException, ScriptException {
		Object result = null;
		
		ClassLoader classLoader = new QuPathGUI.ExtensionClassLoader();
		ScriptEngineManager manager = new ScriptEngineManager(classLoader);
		
		String ext = scriptFile.substring(scriptFile.lastIndexOf(".")+1);
		ScriptEngine engine = manager.getEngineByExtension(ext);
		if (engine == null)
			throw new NullPointerException("No script engine found for " + scriptFile);
		
		// Try to run the script
		// Read script
		String script = GeneralTools.readFileAsString(scriptFile);
		
		// Try to make sure that the standard outputs are used
		ScriptContext context = new SimpleScriptContext();
		PrintWriter outWriter = new PrintWriter(System.out, true);
		PrintWriter errWriter = new PrintWriter(System.err, true);
		context.setWriter(outWriter);
		context.setErrorWriter(errWriter);
		
		// Create bindings, if necessary
		Bindings bindings = new SimpleBindings();
		bindings.put("imageData", imageData);
		
		// Rather inelegant... but set batch image data for both QP classes that we know of...
		QP.setBatchImageData(imageData);
		QPEx.setBatchImageData(imageData);

		// Evaluate the script
		// TODO: discover why it sometimes print "null" at the end of the result
		result = DefaultScriptEditor.executeScript(engine, script, imageData, true, context);
		
		// Ensure writers are flushed
		outWriter.flush();
		errWriter.flush();
		
		// return output, which may be null
		return result;
	}
	
}
