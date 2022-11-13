/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

import javax.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import picocli.AutoComplete.GenerateCompletion;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.IVersionProvider;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParseResult;
import qupath.lib.common.GeneralTools;
import qupath.lib.common.Version;
import qupath.lib.gui.BuildInfo;
import qupath.lib.gui.ExtensionClassLoader;
import qupath.lib.gui.QuPathApp;
import qupath.lib.gui.extensions.Subcommand;
import qupath.lib.gui.images.stores.ImageRegionStoreFactory;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.logging.LogManager.LogLevel;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.scripting.languages.ScriptLanguageProvider;
import qupath.lib.gui.tma.QuPathTMAViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.scripting.QP;
import qupath.lib.scripting.ScriptParameters;
import qupath.lib.scripting.languages.ExecutableLanguage;
import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Main QuPath launcher.
 * 
 * @author Pete Bankhead
 *
 */
@Command(name = "QuPath", subcommands = {HelpCommand.class, ScriptCommand.class, GenerateCompletion.class},
	footer = {"",
			"Copyright(c) The Queen's University Belfast (2014-2016)",
			"Copyright(c) QuPath developers (2017-2022)",
			"Copyright(c) The University of Edinburgh (2018-2022)"
			}, mixinStandardHelpOptions = true, versionProvider = QuPath.VersionProvider.class)
public class QuPath {
	
	private static final Logger logger = LoggerFactory.getLogger(QuPath.class);
	
	@Parameters(arity = "0..1", description = {"Path to image or project to open"}, hidden = true)
	private String path;

	@Option(names = {"-r", "--reset"}, description = "Reset all QuPath preferences.")
	private boolean reset;
	
	@Option(names = {"-q", "--quiet"}, description = "Launch QuPath quietly, without setup dialogs, update checks or messages.")
	private boolean quiet;
	
	@Option(names = {"-p", "--project"}, description = "Launch QuPath and open specified project.")
	private String project;
	
	@Option(names = {"-i", "--image"}, description = {"Launch QuPath and open the specified image.",
											"This should be the image name if a project is also specified, otherwise it should be the full image path."})
	private String image;
	
	@Option(names = {"-t", "--tma"}, description = "Launch standalone viewer for TMA summary results.")
	private boolean tma;

	@Option(names = {"-l", "--log"}, description = {"Log level (default = INFO).", "Options: ${COMPLETION-CANDIDATES}"} )
	private LogLevel logLevel = LogLevel.INFO;
			
	
	/**
	 * Main class to launch QuPath.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		
		QuPath qupath = new QuPath();
		CommandLine cmd = new CommandLine(qupath);
		cmd.setCaseInsensitiveEnumValuesAllowed(true);
//		cmd.setUnmatchedArgumentsAllowed(false);
//		cmd.setStopAtPositional(true);
		cmd.setExpandAtFiles(false);
		cmd.getSubcommands().get("generate-completion").getCommandSpec().usageMessage().hidden(true);
		// Check for subcommands loaded in extensions
		for (var subcommand : ServiceLoader.load(Subcommand.class)) {
			cmd.addSubcommand(subcommand);
		}
		cmd.setExitCodeExceptionMapper(t -> 1);
		ParseResult pr;
		try {
			pr = cmd.parseArgs(args);
		} catch (Exception e) {
			logger.error("An error has occurred, please type -h to display help message.\n" + e.getLocalizedMessage());
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
		if (qupath.tma) {
			QuPathTMAViewer.launch(QuPathTMAViewer.class);
			return;
		}
		
		// Catch -r/--reset
		if (qupath.reset) {
			PathPrefs.resetPreferences();
		}
		
		// Set log level
		if (qupath.logLevel != null)
			LogManager.setRootLogLevel(qupath.logLevel);
				
		// Catch all possible Options, then launch QuPath
		if (!pr.hasSubcommand()) {

			// If no subcommand, parse the arguments and launch QuPathApp.
			List<String> CLIArgs = new ArrayList<String>();
			
			if (qupath.path != null && !qupath.path.isBlank()) {
				CLIArgs.add(getEncodedPath(qupath.path));
			}
			
			if (qupath.quiet)
				CLIArgs.add("--quiet=true");
		
			if (qupath.project != null && !qupath.project.equals("") && qupath.project.endsWith(ProjectIO.getProjectExtension()))
				CLIArgs.add("--project=" + getEncodedPath(qupath.project));
		
			if (qupath.image != null && !qupath.image.equals(""))
				CLIArgs.add("--image=" + getEncodedPath(qupath.image));
			
			QuPathApp.launch(QuPathApp.class, CLIArgs.toArray(new String[CLIArgs.size()]));
			
		} else {
			// Parse and execute subcommand with args
			int exitCode = cmd.execute(args);
			if (exitCode != 0)
				logger.warn("Calling System.exit with exit code {}", exitCode);
			System.exit(exitCode);
		}
	
		return;
	}
	
	
	static void initializeProperties() {
		initializeJTS();
	}
	
	
	/**
	 * Use OverlayNG with Java Topology Suite by default.
	 * This can greatly reduce TopologyExceptions.
	 * Use -Djts.overlay=old to turn off this behavior.
	 */
	static void initializeJTS() {
		var prop = System.getProperty("jts.overlay");
		if (prop == null) {
			logger.debug("Setting -Djts.overlay=ng");
			System.setProperty("jts.overlay", "ng");
		}
	}
	
	
	/**
	 * Non-ASCII characters in paths can become mangled on Windows due to character encoding.
	 * Here, make a cautious attempt to correct these if necessary, or return the path unchanged.
	 * @param path
	 * @return
	 */
	static String getEncodedPath(String path) {
		// Don't do anything if we don't have a path, aren't using Windows, or have only ASCII characters
		if (path == null || !GeneralTools.isWindows() || StandardCharsets.US_ASCII.newEncoder().canEncode(path))
			return path;
		// Reencode as UTF-8 only if this means a non-existent file becomes available
		if (!new File(path).exists()) {
			var pathUTF8 = new String(path.getBytes(), StandardCharsets.UTF_8);
			if (new File(pathUTF8).exists()) {
				logger.warn("Updated path encoding to UTF-8: {}", pathUTF8);
				return pathUTF8;
			}
		}
		return path;
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
		"By default, this will not save changes to any data files."})
class ScriptCommand implements Runnable {
	
	private static final Logger logger = LoggerFactory.getLogger(ScriptCommand.class);
	
	/**
	 * Default extension for scripting (when not specified)
	 */
	private static final String DEFAULT_SCRIPT_EXTENSION = ".groovy";
	
	@Parameters(index = "0", description = "Path to the script file (.groovy).", arity = "0..1", paramLabel = "script")
	private String scriptFile;
	
	@Option(names = {"-c", "--cmd"}, description = "Groovy script passed as a string", paramLabel = "command")
	private String scriptCommand;
	
	@Option(names = {"-i", "--image"}, description = {"Apply the script to the specified image.",
			"This should be the image name if a project is also specified, otherwise it should be the full image path."}, 
			paramLabel = "image")
	private String imagePath;
	
	@Option(names = {"-p", "--project"}, description = "Path to a project file (.qpproj).", paramLabel = "project")
	private String projectPath;
	
	@Option(names = {"-s", "--save"}, description = "Request that data files are updated for each image in the project.", paramLabel = "save")
	private boolean save;
	
	@Option(names = {"-a", "--args"}, description = "Arguments to pass to the script, stored in an 'args' array variable. "
			+ "Multiple args can be passed by using --args multiple times, or by using a \"[quoted,comma,separated,list]\".", paramLabel = "arguments")
	private String[] args;

	@Option(names = {"-e", "--server"}, description = "Arguments to pass when building an ImageSever (only relevant when using --image). "
			+ "For example, --server \"[--classname,BioFormatsServerBuilder,--series,2]\" may be used to read the image with Bio-Formats and "
			+ "extract the third series within the file.", paramLabel = "server-arguments")
	private String[] serverArgs;

	@Option(names = {"-h", "--help"}, usageHelp = true, description = "Show this help message and exit.")
	private boolean usageHelpRequested;
		
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
			
			// Ensure we have a tile cache set
			createTileCache();
			
			// Set classloader to include any available extensions
			var extensionClassLoader = new ExtensionClassLoader();
			extensionClassLoader.refresh();
			ImageServerProvider.setServiceLoader(ServiceLoader.load(ImageServerBuilder.class, extensionClassLoader));
			Thread.currentThread().setContextClassLoader(extensionClassLoader);
			
			// Unfortunately necessary to force initialization (including GsonTools registration of some classes)
			QP.getCoreClasses();
			
			ImageData<BufferedImage> imageData;
			
			if (projectPath != null && !projectPath.equals("")) {
				
				String path = QuPath.getEncodedPath(projectPath);
				var project = ProjectIO.loadProject(new File(path), BufferedImage.class);
				var imageList = project.getImageList();
				// Filter out the images we need (usually all of them)
				if (imagePath != null && !imagePath.equals("")) {
					imageList = imageList.stream().filter(e -> imagePath.equals(e.getImageName())).collect(Collectors.toList());
				}
					
				int batchSize = imageList.size();
				
				for (int batchIndex = 0; batchSize < batchSize; batchIndex++) {
					var entry = imageList.get(batchIndex);
					logger.info("Running script for {} ({}/{})", entry.getImageName(), batchIndex, batchSize);
					logger.info("Running script for {} ({}/{})", entry.getImageName(), batchIndex, batchSize);
					imageData = entry.readImageData();
					try {
						Object result = runBatchScript(project, imageData, batchIndex, batchSize, save);
						if (result != null)
							logger.info("Script result: {}", result);
						if (save)
							entry.saveImageData(imageData);
					} catch (Exception e) {
						logger.error("Error running script for image: " + entry.getImageName(), e);
						// Throw an exception if we have a single image
						// Otherwise, try to recover and continue processing images
						if (imagePath != null && imagePath.equals(entry.getImageName()))
							throw new RuntimeException(e);
					} finally {
						imageData.getServer().close();						
					}
				}
			} else if (imagePath != null && !imagePath.equals("")) {
				String path = QuPath.getEncodedPath(imagePath);
				URI uri = GeneralTools.toURI(path);
				ImageServer<BufferedImage> server = ImageServers.buildServer(uri, parseArgs(serverArgs));
				imageData = new ImageData<>(server);
				Object result = runSingleScript(null, imageData);
				if (result != null)
					logger.info("Script result: {}", result);
				server.close();
			} else {
				Object result = runSingleScript(null, null);
				if (result != null)
					logger.info("Script result: {}", result);
			}
			
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Parse String arguments. If surrounded by square brackets, this is treated as a comma-separated list.
	 * Otherwise, an array is returned containing a copy of the supplied args.
	 * 
	 * @param args
	 * @return
	 */
	private static String[] parseArgs(String[] args) {
		if (args == null)
			return new String[0];
		if (args.length == 1) {
			String arg = args[0];
			if (arg.startsWith("[") && arg.endsWith("]"))
				return arg.substring(1, arg.length()-1).split(",");
		}
		return args.clone();
	}
	
	
	/**
	 * The tile cache is usually set when initializing the GUI; here, we need to create one for performance
	 */
	private void createTileCache() {
		// TODO: Refactor this to avoid replicating logic from QuPathGUI private method
		Runtime rt = Runtime.getRuntime();
		long maxAvailable = rt.maxMemory(); // Max available memory
		if (maxAvailable == Long.MAX_VALUE) {
			logger.warn("No inherent maximum memory set - for caching purposes, will assume 64 GB");
			maxAvailable = 64L * 1024L * 1024L * 1024L;
		}
		double percentage = PathPrefs.tileCachePercentageProperty().get();
		if (percentage < 10) {
			logger.warn("At least 10% of available memory needs to be used for tile caching (you requested {}%)", percentage);
			percentage = 10;
		} else if (percentage > 90) {
			logger.warn("No more than 90% of available memory can be used for tile caching (you requested {}%)", percentage);
			percentage = 90;			
		}
		long tileCacheSize = Math.round(maxAvailable * (percentage / 100.0));
		logger.info(String.format("Setting tile cache size to %.2f MB (%.1f%% max memory)", tileCacheSize/(1024.*1024.), percentage));
		
		var imageRegionStore = ImageRegionStoreFactory.createImageRegionStore(tileCacheSize);
		ImageServerProvider.setCache(imageRegionStore.getCache(), BufferedImage.class);
	}
	
	
	private Object runSingleScript(Project<BufferedImage> project, ImageData<BufferedImage> imageData) throws IOException, ScriptException {
		return runBatchScript(project, imageData, 0, 1, false);
	}
	
	private Object runBatchScript(Project<BufferedImage> project, ImageData<BufferedImage> imageData, int batchIndex, int batchSize, boolean batchSave) throws IOException, ScriptException {
		Object result = null;
		String script = scriptCommand;
		ExecutableLanguage language;
		
		String ext = DEFAULT_SCRIPT_EXTENSION;
		if (scriptFile != null)
			ext = GeneralTools.getExtension(scriptFile).orElse(DEFAULT_SCRIPT_EXTENSION);
		
		ScriptLanguage scriptLanguage = ScriptLanguageProvider.getLanguageFromExtension(ext);
		if (scriptLanguage == null || !(scriptLanguage instanceof ExecutableLanguage))
			throw new IllegalArgumentException("No runnable script language found for " + scriptFile);
			
		language = (ExecutableLanguage)scriptLanguage;
			
			// Read & try to run the script
		File file = null;
		if (script == null) {
			var filePath = QuPath.getEncodedPath(scriptFile);
			file = new File(filePath);
			script = GeneralTools.readFileAsString(filePath);
		} else {
			if (GeneralTools.isWindows() && !StandardCharsets.US_ASCII.newEncoder().canEncode(script))
				logger.warn("Non-ASCII characters detected in the specified script! If you experience encoding issues, try passing a script file instead.");
		}
		
		// Try to make sure that the standard outputs are used
		PrintWriter outWriter = new PrintWriter(System.out, true);
		PrintWriter errWriter = new PrintWriter(System.err, true);
		var params = ScriptParameters.builder()
				.setArgs(parseArgs(args))
				.setProject(project)
				.setImageData(imageData)
				.setDefaultImports(QPEx.getCoreClasses()) // TODO: Consider adding QP rather than QPEx
				.setDefaultStaticImports(Collections.singletonList(QPEx.class))
				.setFile(file)
				.setScript(script)
				.setBatchSize(batchSize)
				.setBatchIndex(batchIndex)
				.setBatchSaveResult(batchSave)
				.setWriter(outWriter)
				.setErrorWriter(errWriter)
				.build();
		
		// Evaluate the script
		try {
			result = language.execute(params);
		} finally {
			// Ensure writers are flushed
			outWriter.flush();
			errWriter.flush();
		}
		
		// return output, which may be null
		return result;
	}
	
}
