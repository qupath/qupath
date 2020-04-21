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
import java.util.List;
import java.util.ServiceLoader;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.BuildInfo;
import qupath.lib.gui.ExtensionClassLoader;
import qupath.lib.gui.QuPathApp;
import qupath.lib.gui.Version;
import qupath.lib.gui.extensions.Subcommand;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.logging.LogManager.LogLevel;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.tma.QuPathTMAViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.scripting.QP;

/**
 * Main QuPath launcher.
 * 
 * @author Pete Bankhead
 *
 */
@Command(name = "qupath", subcommands = {HelpCommand.class, ScriptCommand.class}, 
	descriptionHeading = "QuPath command line",
	footer = {"",
			"Copyright(c) The Queen's University Belfast (2014-2016)",
			"Copyright(c) QuPath developers (2017-2020)",
			"Copyright(c) The University of Edinburgh (2018-2020)"
			}, mixinStandardHelpOptions = true, versionProvider = QuPath.VersionProvider.class)
public class QuPath {
	
	private final static Logger logger = LoggerFactory.getLogger(QuPath.class);
	
	@Option(names = {"-r", "--reset"}, description = "Reset all QuPath preferences.")
	static boolean reset;
	
	@Option(names = {"-q", "--quiet"}, description = "Launch QuPath quietly, without setup dialogs, update checks or messages.")
	static boolean quiet;
	
	@Option(names = {"-p", "--project"}, description = "Launch QuPath and open specified project.")
	static String project;
	
	@Option(names = {"-i", "--image"}, description = {"Launch QuPath and open the specified image.",
											"This should be the image name if a project is also specified, otherwise the full image path."})
	static String image;
	
	@Option(names = {"-t", "--tma"}, description = "Launch standalone viewer for TMA summary results.")
	static boolean tma;

	@Option(names = {"-l", "--log"}, description = {"Log level (default = INFO).", "Options: ${COMPLETION-CANDIDATES}"} )
	static LogLevel logLevel = LogLevel.INFO;
		
	
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
		// Check for subcommands loaded in extensions
		for (var subcommand : ServiceLoader.load(Subcommand.class)) {
			cmd.addSubcommand(subcommand);
		}
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
		
		// Catch -r/--reset
		if (reset) {
			PathPrefs.resetPreferences();
		}
		
		// Set log level
		if (logLevel != null)
			LogManager.setRootLogLevel(logLevel);
				
		// Catch all possible Options, then launch QuPath
		if (!pr.hasSubcommand()) {

			// If no subcommand, parse the arguments and launch QuPathApp.
			List<String> CLIArgs = new ArrayList<String>();
			
			if (quiet)
				CLIArgs.add("--quiet=true");
		
			if (project != null && !project.equals("") && project.endsWith(ProjectIO.getProjectExtension()))
				CLIArgs.add("--project=" + project);
		
			if (image != null && !image.equals(""))
				CLIArgs.add("--image=" + image);
			
			QuPathApp.launch(QuPathApp.class, CLIArgs.toArray(new String[CLIArgs.size()]));

		} else {
			// Parse and execute subcommand with args
			int exitCode = cmd.execute(args);
			System.exit(exitCode);
		}
	
		return;
	}
	
	
	
	static class VersionProvider implements IVersionProvider {

		@Override
		public String[] getVersion() throws Exception {
			var version = BuildInfo.getInstance().getVersion();
			var buildString = BuildInfo.getInstance().getBuildString();
			var strings = new ArrayList<>();
			if (version != null && version != Version.UNKNOWN) {
				var temp = version.toString();
				if (!temp.startsWith("v"))
					temp = "v" + temp;
				strings.add("QuPath " + temp);
			}
			if (buildString != null)
				strings.add(buildString);
			if (strings.isEmpty())
				return new String[] {"Unknown QuPath version!"};
			else {
				strings.add("");
				return strings.toArray(String[]::new);
			}
		}
		
		
		
	}
	
}


// TODO: should script only end with .groovy or can it end with something else?
@Command(name = "script", description = {
		"Runs script for a given image or project.",
		"By default, this will not save changes to any data files."},
		footer = "\nCopyright(c) 2020")
class ScriptCommand implements Runnable {
	
	final private static Logger logger = LoggerFactory.getLogger(ScriptCommand.class);
	
	@Parameters(index = "0", description = "Path to the script file (.groovy).", arity = "0..1", paramLabel = "script")
	private String scriptFile;
	
	@Option(names = {"-c", "--cmd"}, description = "Groovy script passed a a string", paramLabel = "command")
	private String scriptCommand;
	
	@Option(names = {"-i", "--image"}, description = "Path to an image file.", paramLabel = "image")
	private String imagePath;
	
	@Option(names = {"-p", "--project"}, description = "Path to a project file (.qpproj).", paramLabel = "project")
	private String projectPath;
	
	@Option(names = {"-s", "--save"}, description = "Request that data files are updated for each image in the project.", paramLabel = "save")
	boolean save;
	
	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
	boolean usageHelpRequested;
	
	@Override
	public void run() {
		try {
			if (projectPath != null && !projectPath.toLowerCase().endsWith(ProjectIO.getProjectExtension()))
				throw new IOException("Project file must end with '.qpproj'");
			if (scriptCommand == null) {
				if (scriptFile == null || scriptFile.equals("") || !scriptFile.endsWith(".groovy"))
					throw new IOException("File must be a valid script file (.groovy): " + scriptFile);
			} else if (scriptFile != null) {
				throw new IllegalArgumentException("Either a script file or a script command may be provided, but not both!");
			}
			
			ImageData<BufferedImage> imageData;
			
			if (projectPath != null && !projectPath.equals("")) {
				Project<BufferedImage> project = ProjectIO.loadProject(new File(projectPath), BufferedImage.class);
				for (var entry: project.getImageList()) {
					imageData = entry.readImageData();
					try {
						Object result = runScript(entry.readImageData());
						if (result != null)
							System.out.println(result);
						if (save)
							entry.saveImageData(imageData);
					} catch (Exception e) {
						logger.error("Error running script for image: " + entry.getImageName(), e);
					}
					imageData.getServer().close();
				}
			} else if (imagePath != null && !imagePath.equals("")) {
				ImageServer<BufferedImage> server = ImageServerProvider.buildServer(imagePath, BufferedImage.class);
				imageData = new ImageData<>(server);
				Object result = runScript(imageData);
				if (result != null)
					System.out.println(result);
				server.close();
			} else {
				Object result = runScript(null);
				if (result != null)
					System.out.println(result);
			}
			
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage());
		}
	}
	
	private Object runScript(ImageData<BufferedImage> imageData) throws IOException, ScriptException {
		Object result = null;
		
		ClassLoader classLoader = new ExtensionClassLoader();
		ScriptEngineManager manager = new ScriptEngineManager(classLoader);
		
		String script = scriptCommand;
		ScriptEngine engine;
		if (script == null) {
			String ext = scriptFile.substring(scriptFile.lastIndexOf(".")+1);
			engine = manager.getEngineByExtension(ext);
			if (engine == null)
				throw new IllegalArgumentException("No script engine found for " + scriptFile);
			
			// Try to run the script
			// Read script
			script = GeneralTools.readFileAsString(scriptFile);
		} else
			engine = manager.getEngineByExtension("groovy");
		
		
		// Try to make sure that the standard outputs are used
		ScriptContext context = new SimpleScriptContext();
		PrintWriter outWriter = new PrintWriter(System.out, true);
		PrintWriter errWriter = new PrintWriter(System.err, true);
		context.setWriter(outWriter);
		context.setErrorWriter(errWriter);
		
		// Rather inelegant... but set batch image data for both QP classes that we know of...
		QP.setBatchImageData(imageData);
		QPEx.setBatchImageData(imageData);

		// Evaluate the script
		result = DefaultScriptEditor.executeScript(engine, script, imageData, true, context);
		
		// Ensure writers are flushed
		outWriter.flush();
		errWriter.flush();
		
		// return output, which may be null
		return result;
	}
	
}