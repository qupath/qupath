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

package qupath.lib.gui.scripting.richtextfx;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.fxmisc.flowless.VirtualizedScrollPane;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.concurrent.Task;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.IndexRange;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Region;
import qupath.lib.common.ThreadTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.scripting.DefaultScriptEditor;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.tools.MenuTools;
import qupath.lib.scripting.QP;



/*
 * 
 * The rich text script editor makes use of RichTextFX, including aspects of the demo code JavaKeywordsAsyncDemo.java, both from https://github.com/TomasMikula/RichTextFX
 * License for these components:
 * 
 * Copyright (c) 2013-2017, Tomas Mikula and contributors
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 */

/**
 * 
 * Rich text script editor for QuPath.
 * <p>
 * Makes use of RichTextFX, Copyright (c) 2013-2017, Tomas Mikula and contributors (BSD 2-clause license).
 * 
 * @author Pete Bankhead
 * 
 */
public class RichScriptEditor extends DefaultScriptEditor {
	
	final private static Logger logger = LoggerFactory.getLogger(RichScriptEditor.class);
	
	private static final String[] KEYWORDS = new String[] {
            "abstract", "assert", "boolean", "break", "byte",
            "case", "catch", "char", "class", "const",
            "continue", "default", "do", "double", "else",
            "enum", "extends", "final", "finally", "float",
            "for", "goto", "if", "implements", "import",
            "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super",
            "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "void", "volatile", "while",
            "def", "in", "with", "trait", "true", "false", "var" // Groovy
    };
	
	// Delay for async formatting, in milliseconds
	private static int delayMillis = 100;
	
	private static Pattern PATTERN;
	private static Pattern PATTERN_CONSOLE;
	private static final Set<String> METHOD_NAMES = new HashSet<>();
	static {
		for (Method method : QPEx.class.getMethods()) {
			// Exclude deprecated methods (don't want to encourage them...)
			if (method.getAnnotation(Deprecated.class) == null)
				METHOD_NAMES.add(method.getName());
		}
		
		// Remove the methods that come from the Object class...
		// they tend to be quite confusing
		for (Method method : Object.class.getMethods()) {
			if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers()))
				METHOD_NAMES.remove(method.getName());
		}
		
		for (Field field : QPEx.class.getFields()) {
			if (Modifier.isStatic(field.getModifiers()) && Modifier.isPublic(field.getModifiers()))
				METHOD_NAMES.add(field.getName());
		}
		
		for (Class<?> cls : QP.getCoreClasses()) {
			int countStatic = 0;
			for (Method method : cls.getMethods()) {
				if (Modifier.isStatic(method.getModifiers()) && Modifier.isPublic(method.getModifiers())) {
					METHOD_NAMES.add(cls.getSimpleName() + "." + method.getName());
					countStatic++;
				}
			}
			if (countStatic > 0)
				METHOD_NAMES.add(cls.getSimpleName() + ".");
		}
		
//		for (Method method : ImageData.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : PathObjectHierarchy.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : PathObject.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : TMACoreObject.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : PathCellObject.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
//		for (Method method : PathClassFactory.class.getMethods()) {
//			METHOD_NAMES.add(method.getName());
//		}
		METHOD_NAMES.add("print");
		METHOD_NAMES.add("println");
		
		final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
//		final String METHOD_PATTERN = "[a-zA-Z]+\\(";
//		final String METHOD_PATTERN = "\\b(" + String.join("|", METHOD_NAMES) + ")\\b";
	    final String PAREN_PATTERN = "\\(|\\)";
	    final String BRACE_PATTERN = "\\{|\\}";
	    final String BRACKET_PATTERN = "\\[|\\]";
	    final String SEMICOLON_PATTERN = "\\;";
	    final String TRIPLE_QUOTE_PATTERN = "\"\"\"([^\"\"\"\\\\]|\\\\.)*\"\"\"";
	    final String DOUBLE_QUOTE_PATTERN = "\"([^\"\\\\]|\\\\.)*\"";
	    final String SINGLE_QUOTE_PATTERN = "'([^'\\\\]|\\\\.)*\'";
	    final String COMMENT_PATTERN = "//[^\n]*" + "|" + "/\\*(.|\\R)*?\\*/";
	    
	    PATTERN = Pattern.compile(
	            "(?<KEYWORD>" + KEYWORD_PATTERN + ")"
//	            + "|(?<METHOD>" + METHOD_PATTERN + ")"
	            + "|(?<PAREN>" + PAREN_PATTERN + ")"
	            + "|(?<BRACE>" + BRACE_PATTERN + ")"
	            + "|(?<BRACKET>" + BRACKET_PATTERN + ")"
	            + "|(?<SEMICOLON>" + SEMICOLON_PATTERN + ")"
	            + "|(?<TRIPLEQUOTES>" + TRIPLE_QUOTE_PATTERN + ")"
	            + "|(?<DOUBLEQUOTES>" + DOUBLE_QUOTE_PATTERN + ")"
	            + "|(?<SINGLEQUOTES>" + SINGLE_QUOTE_PATTERN + ")"
	            + "|(?<COMMENT>" + COMMENT_PATTERN + ")"
	    );
	    
	    
	    final String WARNING_PATTERN = "WARN[^\n]*";
	    final String ERROR_PATTERN = "ERROR:[^\n]*";
	    
	    PATTERN_CONSOLE = Pattern.compile(
	            "(?<ERROR>" + ERROR_PATTERN + ")"
	            + "|(?<WARN>" + WARNING_PATTERN + ")"
	    );
	}
	
	private ExecutorService executor = Executors.newSingleThreadExecutor(ThreadTools.createThreadFactory("rich-text-highlighting", true));
	
	private ContextMenu menu;
	
	/**
	 * Constructor.
	 * @param qupath the current QuPath instance.
	 */
	public RichScriptEditor(QuPathGUI qupath) {
		super(qupath);
		
		menu = new ContextMenu();
		MenuTools.addMenuItems(menu.getItems(),
				MenuTools.createMenu("Run...",
						runScriptAction,
						runSelectedAction,
						runProjectScriptAction,
						runProjectScriptNoSaveAction
						),
				MenuTools.createMenu("Undo/Redo...",
					undoAction,
					redoAction
					),
				null,
				copyAction,
				pasteAction,
				pasteAndEscapeAction
				);
	}

	@Override
	protected ScriptEditorControl getNewEditor() {
		try {
			CodeArea codeArea = new CustomCodeArea();
			
			/*
			 * Using LineNumberFactory.get(codeArea) gives errors related to the new paragraph folding introduced in RichTextFX 0.10.6.
			 *  java.lang.IllegalArgumentException: Visible paragraphs' last index is [-1] but visibleParIndex was [0]
			 *  
			 * To replicate
			 *  - Run using codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
			 *  - Add some code (including line breaks) to the code area
			 *  - Select all text (Ctrl/Cmd + A)
			 *  - Delete text (backspace)
			 *  - Add more text
			 *  
			 * The change below avoids code folding being used.
			 */
			codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea, digits -> "%1$" + digits + "s", null, null));
//			codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
			
			codeArea.setStyle("-fx-background-color: -fx-control-inner-background;");
			
			
			codeArea.setOnContextMenuRequested(e -> {
				menu.show(codeArea.getScene().getWindow(), e.getScreenX(), e.getScreenY());
//				menu.show(codeArea, e.getScreenX(), e.getScreenY());
			});

			
			CodeAreaControl control = new CodeAreaControl(codeArea);
			
			@SuppressWarnings("unused")
			var cleanup = codeArea
					.multiPlainChanges()
					.successionEnds(Duration.ofMillis(delayMillis))
					.supplyTask(() -> computeHighlightingAsync(codeArea.getText()))
					.awaitLatest(codeArea.multiPlainChanges())
					.filterMap(t -> {
						if (t.isSuccess()) {
							return Optional.of(t.get());
						} else {
							var exception = t.getFailure();
							String message = exception.getLocalizedMessage() == null ? exception.getClass().getSimpleName() : exception.getLocalizedMessage();
							logger.error("Error applying syntax highlighting: {}", message);
							logger.debug("{}", t);
							return Optional.empty();
						}
					})
					.subscribe(change -> codeArea.setStyleSpans(0, change));
			
			
			codeArea.getStylesheets().add(getClass().getClassLoader().getResource("scripting_styles.css").toExternalForm());
						
			codeArea.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
				if (e.isConsumed())
					return;
				if (e.getCode() == KeyCode.TAB) {
					handleTabPress(control, e.isShiftDown());
					e.consume();
				} else if (e.isShortcutDown() && e.getCode() == KeyCode.SLASH) {
					handleLineComment(control);
					e.consume();
				} else if (e.getCode() == KeyCode.ENTER && control.getSelectedText().length() == 0) {
					handleNewLine(control);
					e.consume();
				}
				if (!e.isConsumed())
					matchMethodName(control, e);
			});

			return control;
		} catch (Exception e) {
			// Default to superclass implementation
			logger.error("Unable to create code area", e);
			return super.getNewEditor();
		}
	}
	
	private static KeyCodeCombination completionCode = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);
	
	/**
	 * Try to match and auto-complete a method name.
	 * 
	 * @param control
	 * @param e
	 */
	private void matchMethodName(final ScriptEditorControl control, final KeyEvent e) {
		if (!completionCode.match(e)) {
			if (!e.isControlDown())
				completor = null;
			return;
		}
		e.consume();
		if (completor == null)
			completor = new AutoCompletor(control);
		completor.applyNextCompletion();
	}
	
	
	private AutoCompletor completor;
	
	/**
	 * Helper class for toggling through completions.
	 * 
	 * @author Pete Bankhead
	 */
	static class AutoCompletor {
		
		private final ScriptEditorControl control;
		private int pos;
		private List<String> completions;
		private int idx = 0;
		private String start; // Starting text
		private String lastInsertion = null;
		
		AutoCompletor(final ScriptEditorControl control) {
			this.control = control;
			String text = control.getText();
			this.pos = control.getCaretPosition();
			String[] split = text.substring(0, pos).split("(\\s+)|(\\()|(\\))|(\\{)|(\\})|(\\[)|(\\])");
			if (split.length == 0)
				start = "";
			else
				start = split[split.length-1].trim();
//			if (start.length() == 0)
//				return;
			
			// Use all available completions if we have a dot included
			if (text.contains("."))
				completions = METHOD_NAMES.stream()
						.filter(s -> s.startsWith(start))
						.sorted()
						.collect(Collectors.toList());
			else
				// Use only partial completions (methods, classes) if no dot
				completions = METHOD_NAMES.stream()
				.filter(s -> s.startsWith(start) && (!s.contains(".") || s.lastIndexOf(".") == s.length()-1))
				.sorted()
				.collect(Collectors.toList());				
		}
		
		public void applyNextCompletion() {
			if (completions.isEmpty())
				return;
			if (completions.size() == 0 && lastInsertion != null)
				return;
			if (lastInsertion != null && lastInsertion.length() > 0)
				control.deleteText(pos, pos + lastInsertion.length());
			lastInsertion = completions.get(idx).substring(start.length());// + "(";
			control.insertText(pos, lastInsertion);
			idx++;
			idx = idx % completions.size();
		}
		
	}
	
	
	
	@Override
	protected ScriptEditorControl getNewConsole() {
		try {
			CodeArea codeArea = new CodeArea();
			codeArea.setStyle("-fx-background-color: -fx-control-inner-background;");
			
//			var cleanup = codeArea
//					.multiPlainChanges()
//					.successionEnds(Duration.ofMillis(500))
//					.supplyTask(() -> computeConsoleHighlightingAsync(codeArea.getText()))
//					.awaitLatest(codeArea.multiPlainChanges())
//					.filterMap(t -> {
//						if (t.isSuccess()) {
//							return Optional.of(t.get());
//						} else {
//							var exception = t.getFailure();
//							String message = exception.getLocalizedMessage() == null ? exception.getClass().getSimpleName() : exception.getLocalizedMessage();
//							logger.error("Error applying syntax highlighting: {}", message);
//							logger.debug("{}", t);
//							return Optional.empty();
//						}
//					})
//					.subscribe(change -> codeArea.setStyleSpans(0, change));
			
			codeArea.richChanges()
				.successionEnds(Duration.ofMillis(delayMillis))
				.subscribe(change -> {
					// If anything was removed, do full reformatting
					if (change.getRemoved().length() != 0 || change.getPosition() == 0) {
						if (!change.getRemoved().getText().equals(change.getInserted().getText()))
							codeArea.setStyleSpans(0, computeConsoleHighlighting(codeArea.getText()));					
					} else {
						// Otherwise format only from changed position onwards
						codeArea.setStyleSpans(change.getPosition(), computeConsoleHighlighting(change.getInserted().getText()));
					}
		        });
			codeArea.getStylesheets().add(getClass().getClassLoader().getResource("scripting_styles.css").toExternalForm());
			return new CodeAreaControl(codeArea);
		} catch (Exception e) {
			// Default to superclass implementation
			logger.error("Unable to create console area", e);
			return super.getNewEditor();
		}
	}
	
	private Task<StyleSpans<Collection<String>>> computeHighlightingAsync(final String text) {
		var task = new Task<StyleSpans<Collection<String>>>() {
			@Override
			protected StyleSpans<Collection<String>> call() {
				return computeHighlighting(text);
			}
		};
		executor.execute(task);
		return task;
	}
	
//	private Task<StyleSpans<Collection<String>>> computeConsoleHighlightingAsync(final String text) {
//		var task = new Task<StyleSpans<Collection<String>>>() {
//			@Override
//			protected StyleSpans<Collection<String>> call() {
//				return computeConsoleHighlighting(text);
//			}
//		};
//		executor.execute(task);
//		return task;
//	}
	
	private static StyleSpans<Collection<String>> computeHighlighting(final String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
//                    matcher.group("METHOD") != null ? "method" :
                    matcher.group("PAREN") != null ? "paren" :
                    matcher.group("BRACE") != null ? "brace" :
                    matcher.group("BRACKET") != null ? "bracket" :
                    matcher.group("SEMICOLON") != null ? "semicolon" :
                    matcher.group("TRIPLEQUOTES") != null ? "string" :
                    matcher.group("DOUBLEQUOTES") != null ? "string" :
                    matcher.group("SINGLEQUOTES") != null ? "string" :
                    matcher.group("COMMENT") != null ? "comment" :
                    null; /* never happens */
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
	
	
	private static StyleSpans<Collection<String>> computeConsoleHighlighting(final String text) {
        Matcher matcher = PATTERN_CONSOLE.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("ERROR") != null ? "error" :
                    matcher.group("WARN") != null ? "warning" :
                    null; /* never happens */
            assert styleClass != null;
            spansBuilder.add(Collections.emptyList(), matcher.start() - lastKwEnd);
            spansBuilder.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            lastKwEnd = matcher.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastKwEnd);
        return spansBuilder.create();
    }
	
	
	
	
	static class CodeAreaControl implements ScriptEditorControl {
		
		private VirtualizedScrollPane<CodeArea> scrollpane;
		private CodeArea textArea;
		private StringProperty textProperty = new SimpleStringProperty();
		
		CodeAreaControl(final CodeArea codeArea) {
			this.textArea = codeArea;
			textArea.textProperty().addListener((o, v, n) -> textProperty.set(n));
			textProperty.addListener((o, v, n) -> {
				if (n.equals(textArea.getText()))
					return;
				textArea.clear();
				textArea.insertText(0, n);
			});
			scrollpane = new VirtualizedScrollPane<>(textArea);
		}
		
		@Override
		public StringProperty textProperty() {
//			return textArea.textProperty();
			return textProperty;
		}

		@Override
		public void setText(String text) {
			textArea.clear();
			textArea.insertText(0, text);
		}

		@Override
		public String getText() {
			return textArea.getText();
		}

		@Override
		public ObservableValue<String> selectedTextProperty() {
			return textArea.selectedTextProperty();
		}

		@Override
		public String getSelectedText() {
			return textArea.getSelectedText();
		}

		@Override
		public Region getControl() {
			return scrollpane;
		}

		@Override
		public boolean isUndoable() {
			return textArea.isUndoAvailable();
		}

		@Override
		public boolean isRedoable() {
			return textArea.isRedoAvailable();
		}

		@Override
		public void undo() {
			textArea.undo();
		}

		@Override
		public void redo() {
			textArea.redo();
		}

		@Override
		public void copy() {
			textArea.copy();
		}

		@Override
		public void cut() {
			textArea.cut();
		}

		@Override
		public void paste(String text) {
			if (text != null)
				textArea.replaceSelection(text);
//			textArea.paste();
		}
		
		@Override
		public void appendText(final String text) {
			textArea.appendText(text);
		}

		@Override
		public void clear() {
			textArea.clear();
		}
		
		@Override
		public int getCaretPosition() {
			return textArea.getCaretPosition();
		}
		
		@Override
		public void insertText(int pos, String text) {
			textArea.insertText(pos, text);
		}
		
		@Override
		public void deleteText(int startIdx, int endIdx) {
			textArea.deleteText(startIdx, endIdx);
		}
		
		@Override
		public ReadOnlyBooleanProperty focusedProperty() {
			return textArea.focusedProperty();
		}

		@Override
		public void deselect() {
			textArea.deselect();
		}

		@Override
		public IndexRange getSelection() {
			return textArea.getSelection();
		}

		@Override
		public void selectRange(int anchor, int caretPosition) {
			textArea.selectRange(anchor, caretPosition);
		}

		@Override
		public void setPopup(ContextMenu menu) {
			textArea.setContextMenu(menu);
		}
		
	}
	
	
	static class CustomCodeArea extends CodeArea {
		
		/**
		 * We need to override the default Paste command to handle escaping
		 */
		@Override
		public void paste() {
			var text = getClipboardText(false);
			if (text != null)
				replaceSelection(text);
		}
		
	}
	
	
}
