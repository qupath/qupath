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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineFactory;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.imagej.tools.IJTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.scripting.QPEx;
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
import qupath.lib.scripting.languages.ScriptSyntax;

/**
 * Default implementation for a {@link ScriptLanguage}, based on a {@link ScriptEngine}.
 * @author Melvin Gelbard
 * @since v0.4.0
 */
public class DefaultScriptLanguage extends ScriptLanguage implements ExecutableLanguage {
	
	private static final Logger logger = LoggerFactory.getLogger(DefaultScriptLanguage.class);
	
	private ScriptSyntax syntax;
	private ScriptAutoCompletor completor;
	
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
		this.syntax = PlainSyntax.getInstance();
	}
	
	/**
	 * Constructor for a {@link ExecutableLanguage}.
	 * <p>
	 * Note: the scriptEngine is not stored within this class. It is always fetched via {@link ScriptLanguageProvider}.
	 * @param name		the language name
	 * @param exts			the possible extensions for this language
	 * @param syntax		the syntax object for this language
	 * @param completor	the auto-completion object for this language
	 */
	public DefaultScriptLanguage(String name, Collection<String> exts, ScriptSyntax syntax, ScriptAutoCompletor completor) {
		super(name, exts);
		this.syntax = syntax == null ? PlainSyntax.getInstance() : syntax;
		this.completor = completor;
	}

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
			
		// Supply default bindings
		String importsString = getImportStatements(params.getDefaultImports()) + getStaticImportStatments(params.getDefaultStaticImports());
		int extraLines = importsString.replaceAll("[^\\n]", "").length() + 1; // TODO: Check this
		script2 = importsString + System.lineSeparator() + script;
		
		var context = createContext(params);
		
		try {
			var engine =  ScriptLanguageProvider.getEngineByName(getName());
			if (engine == null)
				throw new ScriptException("Unable to find ScriptEngine for " + getName());
			
			engine.put(ScriptEngine.ARGV, params.getArgs());
			var file = params.getFile();
			if (file != null)
				engine.put(ScriptEngine.FILENAME, file.getAbsolutePath());
			
			result = engine.eval(script2, context);
		} catch (ScriptException e) {
			try {
				int line = e.getLineNumber();
				Throwable cause = e;
				// Try to get to the root of the problem
				while (cause.getCause() != null && cause.getCause() != cause)
					cause = cause.getCause();
				
				// Sometimes we can still get the line number for a Groovy exception in this awkward way...
				if (line < 0) {
					for (StackTraceElement element : cause.getStackTrace()) {
						if ("run".equals(element.getMethodName()) && element.getClassName() != null && element.getClassName().startsWith("Script")) {
							line = element.getLineNumber();
							break;
						}
					}
				}
				
				Writer errorWriter = context.getErrorWriter();
				
				StringBuilder sb = new StringBuilder();
				String message = cause.getLocalizedMessage();
				if (message != null && line < 0) {
					var lineMatcher = Pattern.compile("@ line ([\\d]+)").matcher(message);
					if (lineMatcher.find())
						line = Integer.parseInt(lineMatcher.group(1));
				}
				
				// Check if the error was to do with an import statement
				if (message != null && !message.isBlank()) {
					var matcher = Pattern.compile("unable to resolve class ([A-Za-z_.-]+)").matcher(message);
					if (matcher.find()) {
						String missingClass = matcher.group(1).strip();
						sb.append("It looks like you have tried to import a class '" + missingClass + "' that doesn't exist!\n");
						int ind = missingClass.lastIndexOf(".");
						if (ind >= 0)
							missingClass = missingClass.substring(ind+1);
						Class<?> suggestedClass = CONFUSED_CLASSES.get(missingClass);
						if (suggestedClass != null) {
							sb.append("You should probably remove the broken import statement in your script (around line " + line + ").\n");
							sb.append("Then you may want to check 'Run -> Include default imports' is selected, or alternatively add ");
							sb.append("\n    import " + suggestedClass.getName() + "\nat the start of the script. Full error message below.\n");
						}
					}
	
					// Check if the error was to do with a missing property... which can again be thanks to an import statement
					var matcherProperty = Pattern.compile("No such property: ([A-Za-z_.-]+)").matcher(message);
					if (matcherProperty.find()) {
						String missingClass = matcherProperty.group(1).strip();
						sb.append("I cannot find '" + missingClass + "'!\n");
						int ind = missingClass.lastIndexOf(".");
						if (ind >= 0)
							missingClass = missingClass.substring(ind+1);
						Class<?> suggestedClass = CONFUSED_CLASSES.get(missingClass);
						if (suggestedClass != null) {
							if (!suggestedClass.getSimpleName().equals(missingClass)) {
								sb.append("You can try replacing ").append(missingClass).append(" with ").append(suggestedClass.getSimpleName()).append("\n");
							}
							sb.append("You might want to check 'Run -> Include default imports' is selected, or alternatively add ");
							sb.append("\n    import " + suggestedClass.getName() + "\nat the start of the script. Full error message below.\n");
						}
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
				}
				if (sb.length() > 0)
					errorWriter.append(sb.toString());
				
				if (line >= 0) {
					line = line - extraLines;
					if (cause instanceof InterruptedException)
						errorWriter.append("Script interrupted at line " + line + ": " + message + "\n");
					else
						errorWriter.append(cause.getClass().getSimpleName() + " at line " + line + ": " + message + "\n");
				} else {
					if (cause instanceof InterruptedException)
						errorWriter.append("Script interrupted: " + message + "\n");
					else
						errorWriter.append(cause.getClass().getSimpleName() + ": " + message + "\n");
				}
				var stackTrace = Arrays.stream(cause.getStackTrace()).filter(s -> s != null).map(s -> s.toString())
						.collect(Collectors.joining("\n" + "    "));
				if (stackTrace != null)
					stackTrace += "\n";
				errorWriter.append(stackTrace);
//								logger.error("Script error (" + cause.getClass().getSimpleName() + ")", cause);
			} catch (IOException e1) {
				logger.error("Script IO error: {}", e1);
			} catch (Exception e1) {
				logger.error("Script error: {}", e1.getLocalizedMessage(), e1);
//								e1.printStackTrace();
			}
			throw e;
		} finally {
			QP.resetBatchProjectAndImage();
		}
		return result;
	}
	
	
	/**
	 * Create a {@link ScriptContext} containing information from the {@link ScriptParameters}.
	 * @param params
	 * @return
	 */
	protected ScriptContext createContext(ScriptParameters params) {
		var context = new SimpleScriptContext();
		
		String filePath = params.getFile() == null ? null : params.getFile().getAbsolutePath();
		context.setAttribute(ScriptAttributes.ARGS, params.getArgs(), ScriptContext.ENGINE_SCOPE);
		context.setAttribute(ScriptAttributes.FILE_PATH, filePath, ScriptContext.ENGINE_SCOPE);
		
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
	public ScriptSyntax getSyntax() {
		return syntax;
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
	public String getStaticImportStatments(Collection<Class<?>> classes) {
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
