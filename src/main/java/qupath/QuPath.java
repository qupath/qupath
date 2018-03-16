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
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.SimpleBindings;
import javax.script.SimpleScriptContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.gui.QuPathApp;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.tma.QuPathTMAViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.io.PathIO;
import qupath.lib.scripting.QP;
import qupath.lib.scripting.QPEx;

/**
 * Main QuPath launcher.
 * 
 * @author Pete Bankhead
 *
 */
public class QuPath {
	
	private final static Logger logger = LoggerFactory.getLogger(QuPath.class);
	
	public static void main(String[] args) {
		
		logger.info("Launching QuPath with args: {}", String.join(", ", args));
		
		if (args != null && args.length > 0 && "tma".equals(args[0].toLowerCase()))
				QuPathTMAViewer.launch(QuPathTMAViewer.class, args);
		else {
//			// This was an attempt to register a file listener at an early stage, so as to be able to launch QuPath
			// by double-clicking on an associated file.
			// Kind of worked, but somehow ended up thwarting attempts to set the system menubar.
//			if (GeneralTools.isMac()) {
//				try {
//					Class<?> osx = Class.forName("qupath.extensions.osx.OSXExtensions", true, QuPathGUI.getClassLoader());
//					if (osx != null) {
//						Method method = osx.getMethod("setOSXFileHandler");
//						method.invoke(null);
//					}
//				} catch (Exception e) {
//					logger.debug("Error setting OSX extensions!", e);
//				}
//			}
			
			// Parse command line arguments
			Map<String, String> map = new HashMap<>();
			int i = 0;
			while (i < args.length) {
				String arg = args[i];
				if (arg.startsWith("-")) {
					if (i < args.length-1 && !args[i+1].startsWith("-")) {
						map.put(arg, args[i+1]);
						i++;
					} else {
						map.put(arg, null);
					}
				}
				i++;
			}
			
			// Run a script (& then exit) if required
			String SCRIPT_KEY = "-script";
			String IMAGE_KEY = "-image";
			if (map.containsKey("-script")) {
				
				String scriptName = map.get(SCRIPT_KEY);
				
//				// Try to load OpenCV native library
//				if (!OpenCVExtension.loadNativeLibrary())
//					logger.warn("Unable to load OpenCV native library!");
				
				// 
				ClassLoader classLoader = new QuPathGUI.ExtensionClassLoader();
				ScriptEngineManager manager = new ScriptEngineManager(classLoader);
				if (scriptName == null || !scriptName.contains(".")) {
					logger.error("Invalid path to script: " + scriptName);
					return;
				}
				
				String ext = scriptName.substring(scriptName.lastIndexOf(".")+1);
				ScriptEngine engine = manager.getEngineByExtension(ext);
				if (engine == null) {
					logger.error("No script engine found for " + scriptName);
					return;
				}
				
				// Try to run the script
				try {
//				try (FileReader reader = new FileReader(scriptName)) {
					
					// Read script
					String script = GeneralTools.readFileAsString(scriptName);
					
					// Try to make sure that the standard outputs are used
					ScriptContext context = new SimpleScriptContext();
					PrintWriter outWriter = new PrintWriter(System.out, true);
					PrintWriter errWriter = new PrintWriter(System.err, true);
					context.setWriter(outWriter);
					context.setErrorWriter(errWriter);
					
					// Create bindings, if necessary
					String imagePath = map.get(IMAGE_KEY);
					Bindings bindings = new SimpleBindings();
					ImageData<BufferedImage> imageData = null;
					if (imagePath != null) {
						// Load data, if required
						if (imagePath.toLowerCase().endsWith(".qpdata")) {
							imageData = PathIO.readImageData(new File(imagePath), null, null, BufferedImage.class);
						} else {
							ImageServer<BufferedImage> server = ImageServerProvider.buildServer(imagePath, BufferedImage.class);
							imageData = new ImageData<>(server);
						}
					}
					bindings.put("imageData", imageData);
					
					// Rather inelegant... but set batch image data for both QP classes that we know of...
					QP.setBatchImageData(imageData);
					QPEx.setBatchImageData(imageData);

					// Evaluate the script
					Object result = engine.eval(script, context);
					
					// Ensure writers are flushed
					outWriter.flush();
					errWriter.flush();
					
					// Print any output, if needed
					if (result != null) {
						System.out.println("Script result: " + result);
					}
				} catch (Exception e) {
					logger.error("Error running script!", e);
				}
				return;
			}
			
			// Launch main GUI
			QuPathApp.launch(QuPathApp.class, args);				
		}
	}

}
