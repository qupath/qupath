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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
import picocli.CommandLine.ParseResult;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.ExtensionClassLoader;
import qupath.lib.gui.QuPathApp;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.tma.QuPathTMAViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
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
public class QuPath {
	
	private final static Logger logger = LoggerFactory.getLogger(QuPath.class);
	
	@Option(names = {"-r", "--reset"}, description = "Reset all preferences.")
	static boolean reset;
	
	@Option(names = {"-k", "--skip-setup"}, description = "Skip setup.")
	static boolean skipSetup;
	
	@Option(names = {"-p", "--project"}, description = "Launch QuPath and open given project.")
	static String project;
	
	@Option(names = {"-i", "--image"}, description = "Launch QuPath and open given image.")
	static String image;
	
	@Option(names = {"-t", "--tma"}, description = "Launch standalone viewer for looking at TMA summary results.")
	static boolean tma;
	
	
	/**
	 * Main class to launch QuPath.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		CommandLine cmd = new CommandLine(new QuPath());
		cmd.setCaseInsensitiveEnumValuesAllowed(true);
		cmd.setUnmatchedArgumentsAllowed(false);
		cmd.setStopAtPositional(true);
		cmd.setExpandAtFiles(false);
		ParseResult pr;
		try {
			pr = cmd.parseArgs(args);
		} catch (Exception e) {
			logger.error("An error has occured, please type -h to display help message.\n" + e.getLocalizedMessage());
			return;
		}
		
		// Catch -h/--help and -V/--version
		if (cmd.isUsageHelpRequested()) {
			   cmd.usage(System.out);
			   return;
			} else if (cmd.isVersionHelpRequested()) {
			   cmd.printVersionHelp(System.out);
			   return;
			}
		
		// Catch -t/--tma
		if (tma) {
			QuPathTMAViewer.launch(QuPathTMAViewer.class);
			return;
		}
		
		// Catch all possible Options, then launch QuPath
		if (!pr.hasSubcommand()) {
			// If no subcommand, parse the arguments and launch QuPathApp.
			List<String> CLIArgs = new ArrayList<String>();
			if (reset)
				PathPrefs.resetPreferences();
			
			if (skipSetup)
				CLIArgs.add("skip-setup");
		
			if (project != null && !project.equals("") && project.endsWith(ProjectIO.getProjectExtension()))
				CLIArgs.addAll(Arrays.asList("project", project));
		
			if (image != null && !image.equals("") && !CLIArgs.contains("project"))
				CLIArgs.addAll(Arrays.asList("image", image));

			QuPathApp.launch(QuPathApp.class, CLIArgs.toArray(new String[CLIArgs.size()]));

		} else {
			// Parse and execute subcommand with args
			int exitCode = cmd.execute(args);
			System.exit(exitCode);
		}
	
		return;
	}
}


// TODO: should script only end with .groovy or can it end with something else?
@Command(name = "script", description = "Runs script for a given image or project (without saving).", footer = "\nCopyright(c) 2020")
class ScriptCommand implements Runnable {
	
	final private static Logger logger = LoggerFactory.getLogger(ScriptCommand.class);
	
	@Parameters(index = "0", description = "Path to the script file (.groovy).", paramLabel = "script")
	private String scriptFile;
	
	@Option(names = {"-i", "--image"}, description = "Path to an image file.", paramLabel = "image")
	private String imagePath;
	
	@Option(names = {"-p", "--project"}, description = "Path to a project file (.qpproj).", paramLabel = "project")
	private String projectPath;
	
	@Option(names = {"-s", "-save"}, description = "Flag to save the data after running script for entire project.", paramLabel = "save")
	boolean save;
	
	@Override
	public void run() {
		try {
			if (projectPath != null && !projectPath.toLowerCase().endsWith(ProjectIO.getProjectExtension()))
				throw new IOException("Project file must end with '.qpproj'");
			if (scriptFile == null || scriptFile.equals("") || !scriptFile.endsWith(".groovy"))
				throw new IOException("File must be a valid script file (.groovy): " + scriptFile);
			
			ImageData<BufferedImage> imageData;
			
			if (projectPath != null && !projectPath.equals("")) {
				Project<BufferedImage> project = ProjectIO.loadProject(new File(projectPath), BufferedImage.class);
				for (var entry: project.getImageList()) {
					imageData = entry.readImageData();
					try {
						
						System.out.println(runScript(entry.readImageData()));
						if (save)
							entry.saveImageData(imageData);
					} catch (Exception e) {
						logger.error("Error running script for image:", entry.getImageName(),": ", e);
					}
					imageData.getServer().close();
				}
			} else if (imagePath != null && !imagePath.equals("")) {
				imageData = PathIO.readImageData(new File(imagePath), null, null, BufferedImage.class);
				System.out.println(runScript(imageData));
			} else {
				System.out.println(runScript(null));
			}
			
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		}
	}
	
	private Object runScript(ImageData<BufferedImage> imageData) throws IOException, ScriptException {
		Object result = null;
		
		ClassLoader classLoader = new ExtensionClassLoader();
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
		// TODO: Check if it crashes in case imageData == null (i.e. if script command is ran without image/project)
		Bindings bindings = new SimpleBindings();
		bindings.put("imageData", imageData);
		
		// Rather inelegant... but set batch image data for both QP classes that we know of...
		QP.setBatchImageData(imageData);
		QPEx.setBatchImageData(imageData);

		// Evaluate the script
		// TODO: find out why it sometimes print "null" at the end of the result
		result = DefaultScriptEditor.executeScript(engine, script, imageData, true, context);
		
		// Ensure writers are flushed
		outWriter.flush();
		errWriter.flush();
		
		// return output, which may be null
		return result;
	}
	
}