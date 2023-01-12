/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting.languages;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.script.Compilable;
import javax.script.CompiledScript;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import qupath.imagej.tools.IJTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.scripting.completors.DefaultAutoCompletor;
import qupath.lib.gui.scripting.completors.GroovyAutoCompletor;
import qupath.lib.gui.scripting.completors.PythonAutoCompletor;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObjects;
import qupath.lib.projects.Project;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.ShapeSimplifier;
import qupath.lib.scripting.QP;
import qupath.lib.scripting.ScriptAttributes;
import qupath.lib.scripting.ScriptParameters;
import qupath.lib.scripting.languages.ExecutableLanguage;
import qupath.lib.scripting.languages.ScriptAutoCompletor;
import qupath.lib.scripting.languages.ScriptLanguage;

/**
 * Default implementation for a {@link ScriptLanguage}, based on a {@link ScriptEngine}.
 * @author Melvin Gelbard
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class DefaultScriptLanguage extends ScriptLanguage implements ExecutableLanguage {
	
	private static final Logger logger = LoggerFactory.getLogger(DefaultScriptLanguage.class);
	
	private ScriptAutoCompletor completor;
	
	private Cache<String, CompiledScript> compiledMap = CacheBuilder.newBuilder()
			.maximumSize(100) // Maximum number of compiled scripts to retain
			.build();
	
	/**
	 * Create a map of classes that have changed, and therefore old scripts may use out-of-date import statements.
	 * This allows us to be a bit more helpful in handling the error message.
	 */
	private static Map<String, Class<?>> CONFUSED_CLASSES;
	
	static {	
		CONFUSED_CLASSES = new HashMap<>();
		for (Class<?> cls : QP.getCoreClasses()) {
			CONFUSED_CLASSES.put(cls.getSimpleName(), cls);
		}
		CONFUSED_CLASSES.put("PathRoiToolsAwt", RoiTools.class);
		CONFUSED_CLASSES.put("PathDetectionObject", PathObjects.class);
		CONFUSED_CLASSES.put("PathAnnotationObject", PathObjects.class);
		CONFUSED_CLASSES.put("PathCellObject", PathObjects.class);
		CONFUSED_CLASSES.put("RoiConverterIJ", IJTools.class);
		CONFUSED_CLASSES.put("QP", QP.class);
		CONFUSED_CLASSES.put("QPEx", QPEx.class);
		CONFUSED_CLASSES.put("ShapeSimplifierAwt", ShapeSimplifier.class);
		CONFUSED_CLASSES.put("ImagePlusServerBuilder", IJTools.class);
		CONFUSED_CLASSES.put("DisplayHelpers", Dialogs.class);
		CONFUSED_CLASSES = Collections.unmodifiableMap(CONFUSED_CLASSES);
	}
	
	/**
	 * Constructor for a {@link ExecutableLanguage} based on a {@link ScriptEngineFactory}.
	 * <p>
	 * Note: the scriptEngine is not stored within this class. It is always fetched via {@link ScriptLanguageProvider}.
	 * @param factory
	 */
	public DefaultScriptLanguage(ScriptEngineFactory factory) {
		super(factory.getEngineName(), factory.getExtensions());
		String name = factory.getLanguageName().toLowerCase();
		completor = getDefaultAutoCompletor(name);
	}

	
	/**
	 * Default method to get a suitable auto completor for the given language name.
	 * @param languageName
	 * @return
	 */
	protected ScriptAutoCompletor getDefaultAutoCompletor(String languageName) {
		String name = languageName.toLowerCase();
		
		if ("groovy".equals(name))
			return new GroovyAutoCompletor(true);
		else if ("java".equals(name))
			return new DefaultAutoCompletor(true);
		else if (Set.of("python", "cpython", "python py4j", "jython").contains(name))
			return new PythonAutoCompletor(true);
		
		return null;
	}
	
	/**
	 * Constructor for a {@link ExecutableLanguage}.
	 * <p>
	 * Note: the scriptEngine is not stored within this class. It is always fetched via {@link ScriptLanguageProvider}.
	 * @param name		the language name
	 * @param exts			the possible extensions for this language
	 * @param completor	the auto-completion object for this language
	 */
	public DefaultScriptLanguage(String name, Collection<String> exts, ScriptAutoCompletor completor) {
		super(name, exts);
		this.completor = completor;
	}

	@SuppressWarnings("unchecked")
	@Override
	public Object execute(ScriptParameters params) throws ScriptException {
		// Set the current ImageData if we can
		QP.setBatchProjectAndImage((Project<BufferedImage>)params.getProject(), (ImageData<BufferedImage>)params.getImageData());
		
		// We'll actually use script2... which may or may not be the same
		String script = params.getScript();
		String script2 = script;
		
		// Prepare to return a result
		Object result = null;

		// Record if any extra lines are added to the script, to help match line numbers of any exceptions
			
		// Prepend default bindings if we need them
		String importsString = getImportStatements(params.getDefaultImports()) + getStaticImportStatements(params.getDefaultStaticImports());
		int extraLines = 0;
		boolean defaultImportsAvailable = false;
		if (importsString.isBlank())
			script2 = script;
		else {
			extraLines = importsString.replaceAll("[^\\n]", "").length() + 1; // TODO: Check this
			script2 = importsString + System.lineSeparator() + script;
			defaultImportsAvailable = true;
		}
		
		var context = createContext(params);
		
		var file = params.getFile();
		String filename;
		if (file != null)
			filename = file.getName();
		else
			filename = getDefaultScriptName();
		
		try {
			ScriptEngine engine = null;
			
			// Get a compiled version of the script if we can, and want to
			boolean useCompiled = params.useCompiled();
			CompiledScript compiled = useCompiled ? compiledMap.getIfPresent(script2) : null;
			
			// Try to compile the script if required
			if (useCompiled && compiled == null) {
				engine = ScriptLanguageProvider.getEngineByName(getName());
				if (engine == null)
					throw new ScriptException("Unable to find ScriptEngine for " + getName());
				if (engine instanceof Compilable) {
					var compilable = (Compilable)engine;
					synchronized (compiledMap) {
						compiled = compiledMap.getIfPresent(script2);
						// Compile if we don't have the script, or it is somehow associated with a different engine
						if (compiled == null || !Objects.equals(compiled.getEngine().getClass(), engine.getClass())) {
							compiled = compiledMap.getIfPresent(script2);						
							logger.debug("Compiling script");
							compiled = compilable.compile(script2);
							compiledMap.put(script2, compiled);
						}	
					}
				} else
					logger.debug("Script engine does not support compilation: {}", engine);
			}
			
			// Ensure we have set the attributes
			context.setAttribute(ScriptEngine.ARGV, params.getArgs(), ScriptContext.ENGINE_SCOPE);
			context.setAttribute(ScriptEngine.FILENAME, filename, ScriptContext.ENGINE_SCOPE);
			
			if (compiled != null) {
				logger.debug("Evaluating compiled script: {}", compiled);
				result = compiled.eval(context);
			} else {
				if (engine == null) {
					engine = ScriptLanguageProvider.getEngineByName(getName());
					if (engine == null)
						throw new ScriptException("Unable to find ScriptEngine for " + getName());
				}
				logger.debug("Evaluating script engine: {}", engine);
				result = engine.eval(script2, context);
			}
			
			if (params.doUpdateHierarchy() && params.getImageData() != null)
				params.getImageData().getHierarchy().fireHierarchyChangedEvent(params);
			
		} catch (ScriptException e) {
			
//			// If we have no extra lines, just propagate the exception
//			if (extraLines == 0)
//				throw e;
			
			// If we have extra lines, we'd ideally like to correct the line number in the exception and stack trace
			try {
				int line = e.getLineNumber();
				Throwable cause = e;
				// Try to get to the root of the problem
				while (cause.getCause() != null && cause.getCause() != cause)
					cause = cause.getCause();
				
				// Attempt to get the line number from the message
				String message = cause.getLocalizedMessage();
				if (message != null && line < 0) {
					var lineMatcher = Pattern.compile("@ line ([\\d]+)").matcher(message);
					if (lineMatcher.find()) {
						line = Integer.parseInt(lineMatcher.group(1));
						
						message = message.substring(0, lineMatcher.start(1)) + 
								(line - extraLines) + 
								message.substring(lineMatcher.end(1));
					}
				}
				
				// Sometimes we can still get the line number for a Groovy exception in this awkward way...
				var stackTraceTemp = cause.getStackTrace();
				for (int i = 0; i < stackTraceTemp.length; i++) {
					var element = stackTraceTemp[i];
					String elementFileName = element.getFileName();
					if (elementFileName != null && elementFileName.equals(filename)) {
						boolean updateStackTrace = (line - extraLines) != element.getLineNumber();
						if (line < 0)
							line = element.getLineNumber();
						
						if (updateStackTrace) {
							// Update the line
							var element2 = new StackTraceElement(
									element.getClassName(),
									element.getMethodName(),
									element.getFileName(),
									line - extraLines);
							stackTraceTemp[i] = element2;
							cause.setStackTrace(stackTraceTemp);
						}
						break;
					}
				}
								
				// Try to interpret the message in a user-friendly way
				Writer errorWriter = context.getErrorWriter();
				String extra = tryToInterpretMessage(cause, line - extraLines, defaultImportsAvailable);
				if (!extra.isBlank())
					errorWriter.append(extra);
				
				// Throw a new exception with a more readable message
				String newMessage = message;
//				if (line >= 0) {
//					line = line - extraLines;
//					if (cause instanceof InterruptedException)
//						newMessage = "Script interrupted at line " + line + ": " + message;
//					else
//						newMessage = "Exception at line " + line + ": " + message;
//				} else {
//					if (cause instanceof InterruptedException)
//						newMessage = "Script interrupted: " + message;
//					else
//						newMessage = message;
//				}
				
				var updatedException = new ScriptException(newMessage, filename, line - extraLines, e.getColumnNumber());
				updatedException.initCause(cause);
//				updatedException.setStackTrace(cause.getStackTrace());
				throw updatedException;
			} catch (IOException | RuntimeException e2) {
				logger.debug("Error fixing script exception: " + e2.getLocalizedMessage(), e2);
				throw e;
			}
		} finally {
			QP.resetBatchProjectAndImage();
		}
		return result;
	}
	
	
	protected String tryToInterpretMessage(Throwable cause, int line, boolean defaultImportsAvailable) {
		
		String message = cause.getLocalizedMessage();
		if (message == null)
			message = "";

		var sb = new StringBuilder();
		
		if (cause instanceof ConcurrentModificationException) {
			sb.append("ERROR: ConcurrentModificationException! "
					+ "This usually happen when two threads try to modify a collection (e.g. a list) at the same time.\n"
					+ "It might indicate a QuPath bug (or just something wrong in the script).\n");
		}
		
		var matcher = Pattern.compile("unable to resolve class ([A-Za-z0-9_.-]+)").matcher(message);
		
		if (matcher.find()) {
			String missingClass = matcher.group(1).strip();
			sb.append("ERROR: It looks like you've tried to import a class '" + missingClass + "' that couldn't be found\n");
			if (!defaultImportsAvailable) {
				sb.append("Turning on 'Run -> Include default imports' *may* help fix this.\n");
			}
			int ind = missingClass.lastIndexOf(".");
			if (ind >= 0)
				missingClass = missingClass.substring(ind+1);
			Class<?> suggestedClass = CONFUSED_CLASSES.get(missingClass);
			if (suggestedClass != null) {
				if (line >= 0)
					sb.append("You should probably remove the broken import statement in your script (around line " + line + "), and include\n");
				else {
					sb.append("You should probably remove the broken import statement in your script, and include \n");
				}
				sb.append("\n    import " + suggestedClass.getName() + "\nat the start of the script.");
			}
		}

		// Check if the error was to do with a missing property... which can again be thanks to an import statement
		var matcherProperty = Pattern.compile("No such property: ([A-Za-z_.-]+)").matcher(message);
		if (matcherProperty.find()) {
			String missingProperty = matcherProperty.group(1).strip();
			sb.append("ERROR: It looks like you've tried to access a property '" + missingProperty + "' that doesn't exist\n");
			if (!defaultImportsAvailable) {
				sb.append("This error can sometimes by fixed by turning on 'Run -> Include default imports'.\n");
			}
		}
		
		// Check if the error was to do with a missing property... which can again be thanks to an import statement
		var matcherMethod = Pattern.compile("No signature of method").matcher(message);
		if (matcherMethod.find()) {
			sb.append("ERROR: It looks like you've tried to access a method that doesn't exist.\n");
			if (!defaultImportsAvailable) {
				sb.append("This error can sometimes by fixed by turning on 'Run -> Include default imports'.\n");
			}
		}
		
		// Check for JavaFX thread
		if (message.contains("Not on FX application thread")) {
			sb.append("ERROR: The script involves interacting with JavaFX, and should be called on the JavaFX Application thread.\n");			
			sb.append("You can often fix this by passing your code to 'Platform.runLater()', e.g. in Groovy use \n\n"
					+ "    Platform.runLater {\n"
					+ "        // your code\n"
					+ "    }\n");			
		}
		
		
		// Check if the error was to do with a special left quote character
		var matcherQuotationMarks = Pattern.compile("Unexpected input: .*([\\x{2018}|\\x{201c}|\\x{2019}|\\x{201D}]+)' @ line (\\d+), column (\\d+).").matcher(message);
		if (matcherQuotationMarks.find()) {
			int nLine = Integer.parseInt(matcherQuotationMarks.group(2));
			String quotationMark = matcherQuotationMarks.group(1);
			String suggestion = quotationMark.equals("‘") || quotationMark.equals("’") ? "'" : "\"";
			sb.append(String.format("At least one invalid quotation mark (%s) was found @ line %s column %s! ", quotationMark, nLine-1, matcherQuotationMarks.group(3)));
			sb.append(String.format("You can try replacing it with a straight quotation mark (%s).%n", suggestion));
		}
		
		if (sb.length() > 0)
			sb.append("\n");
		
		return sb.toString();
	}
	
	
	protected String getDefaultScriptName() {
		return "QuPathScript";
	}
	
	
	/**
	 * Create a {@link ScriptContext} containing information from the {@link ScriptParameters}.
	 * @param params
	 * @return
	 */
	protected ScriptContext createContext(ScriptParameters params) {
		var context = new SimpleScriptContext();

		context.setAttribute(ScriptAttributes.ARGS, params.getArgs(), ScriptContext.ENGINE_SCOPE);

		var file = params.getFile();
		if (file != null) {
			context.setAttribute(ScriptAttributes.FILE_PATH, file.getAbsolutePath(), ScriptContext.ENGINE_SCOPE);
		}

		context.setAttribute(ScriptAttributes.BATCH_SIZE, params.getBatchSize(), ScriptContext.ENGINE_SCOPE);
		context.setAttribute(ScriptAttributes.BATCH_INDEX, params.getBatchIndex(), ScriptContext.ENGINE_SCOPE);
		context.setAttribute(ScriptAttributes.BATCH_LAST, params.getBatchIndex() == params.getBatchSize()-1, ScriptContext.ENGINE_SCOPE);
		context.setAttribute(ScriptAttributes.BATCH_SAVE, params.getBatchSaveResult(), ScriptContext.ENGINE_SCOPE);
		
		// Remove for now because of fears of memory leaks
//		context.setAttribute(ScriptAttributes.PROJECT, params.getProject(), ScriptContext.ENGINE_SCOPE);
//		context.setAttribute(ScriptAttributes.IMAGE_DATA, params.getImageData(), ScriptContext.ENGINE_SCOPE);
		
		context.setWriter(params.getWriter());
		context.setErrorWriter(params.getErrorWriter());
		
		return context;
	}

	@Override
	public ScriptAutoCompletor getAutoCompletor() {
		return completor;
	}

	/**
	 * Get the import statements as a String, to add at the beginning of the executed script.
	 * @param classes a collection of the classes to import 
	 * @return import string
	 */
	public String getImportStatements(Collection<Class<?>> classes) {
		if (classes != null && !classes.isEmpty()) {
			var generator = getImportStatementGenerator();
			if (generator != null)
				return generator.getImportStatments(classes);
		}
		return "";
	}
	
	
	/**
	 * Get the static import statements as a String, to add at the beginning of the executed script.
	 * @param classes	a collection of classes to import as static classes
	 * @return import string
	 */
	public String getStaticImportStatements(Collection<Class<?>> classes) {
		if (classes != null && !classes.isEmpty()) {
			var generator = getImportStatementGenerator();
			if (generator != null)
				return generator.getStaticImportStatments(classes);
		}
		return "";
	}
	
	/**
	 * Get an {@link ImportStatementGenerator}.
	 * This attempts to make an educated guess, returning JAVA_IMPORTER or PYTHON_IMPORTER based on the 
	 * name
	 * @return
	 */
	protected ImportStatementGenerator getImportStatementGenerator() {
		String name = getName().toLowerCase();
		
		if (Set.of("java", "groovy", "kotlin", "scala").contains(name))
			return JAVA_IMPORTER;
		
		if (Set.of("jython", "python", "ipython", "cpython").contains(name))
			return PYTHON_IMPORTER;
		
		return null;
	}
		
	/**
	 * Java-like import statements
	 */
	protected static final ImportStatementGenerator JAVA_IMPORTER = new JavaImportStatementGenerator();

	/**
	 * Pythonic import statements
	 */
	protected static final ImportStatementGenerator PYTHON_IMPORTER = new PythonImportStatementGenerator();

	
	/**
	 * Interface defining how the import statements should be generated for the language.
	 * The purpose of this is to enable standard methods for common languages (currently Java and Python).
	 */
	protected static interface ImportStatementGenerator {
		
		public String getImportStatments(Collection<Class<?>> classes);
		
		public String getStaticImportStatments(Collection<Class<?>> classes);
		
	}
	
	static class PythonImportStatementGenerator implements ImportStatementGenerator {

		@Override
		public String getImportStatments(Collection<Class<?>> classes) {
			return classes.stream().map(c -> "import " + c.getName() + ";").collect(Collectors.joining(" "));
		}

		@Override
		public String getStaticImportStatments(Collection<Class<?>> classes) {
			return classes.stream().map(c -> "from " + c.getName() + " import *;").collect(Collectors.joining(" "));	
		}
		
	}
	
	static class JavaImportStatementGenerator implements ImportStatementGenerator {

		@Override
		public String getImportStatments(Collection<Class<?>> classes) {
			return classes.stream().map(c -> "import " + c.getName() + ";").collect(Collectors.joining(" "));
		}

		@Override
		public String getStaticImportStatments(Collection<Class<?>> classes) {
			return classes.stream().map(c -> "import static " + c.getName() + ".*").collect(Collectors.joining(" "));
		}
		
		
		
	}
	
}
