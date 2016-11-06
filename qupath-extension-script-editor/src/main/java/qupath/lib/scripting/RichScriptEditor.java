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

package qupath.lib.scripting;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.StyleSpans;
import org.fxmisc.richtext.StyleSpansBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.scene.control.Control;
import javafx.scene.control.IndexRange;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.scripting.DefaultScriptEditor;



/*
 * 
 * The rich text script editor makes use of RichTextFX, including aspects of the demo code JavaKeywords.java, both from https://github.com/TomasMikula/RichTextFX
 * License for these components:
 * 
 * Copyright (c) 2013-2014, Tomas Mikula
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
 * 
 * Makes use of RichTextFX, Copyright (c) 2013-2014, Tomas Mikula (BSD 2-clause license).
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
            "def", "in", "with", "trait", "true", "false" // Groovy
    };
	
	private static Pattern PATTERN;
	private static Pattern PATTERN_CONSOLE;
	private static final Set<String> METHOD_NAMES = new HashSet<>();
	static {
		for (Method method : QPEx.class.getMethods()) {
			METHOD_NAMES.add(method.getName());
		}
		
		// Remove the methods that come from the Object class...
		// they tend to be quite confusing
		for (Method method : Object.class.getMethods()) {
			METHOD_NAMES.remove(method.getName());
		}
		
		for (Field field : QPEx.class.getFields()) {
			METHOD_NAMES.add(field.getName());
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
		
		final String KEYWORD_PATTERN = "\\b(" + String.join("|", KEYWORDS) + ")\\b";
		final String METHOD_PATTERN = "\\b(" + String.join("|", METHOD_NAMES) + ")\\b";
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
	            + "|(?<METHOD>" + METHOD_PATTERN + ")"
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
	
	public RichScriptEditor(QuPathGUI qupath) {
		super(qupath);
	}

	@Override
	protected ScriptEditorControl getNewEditor() {
		try {
			CodeArea codeArea = new CodeArea();
			codeArea.setParagraphGraphicFactory(LineNumberFactory.get(codeArea));
			codeArea.richChanges().subscribe(change -> {
	            codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText()));
	        });
			codeArea.getStylesheets().add(getClass().getClassLoader().getResource("scripting_styles.css").toExternalForm());
			
			CodeAreaControl control = new CodeAreaControl(codeArea);
			
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
	
	/**
	 * Try to match and auto-complete a method name.
	 * 
	 * @param codeArea
	 * @param e
	 */
	private void matchMethodName(final ScriptEditorControl control, final KeyEvent e) {
		KeyCodeCombination completionCode = new KeyCodeCombination(KeyCode.SPACE, KeyCombination.CONTROL_DOWN);
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
			completions = METHOD_NAMES.stream().filter(s -> s.startsWith(start)).sorted().collect(Collectors.toList());
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
			codeArea.richChanges().subscribe(change -> {
	            codeArea.setStyleSpans(0, computeConsoleHighlighting(codeArea.getText()));
	        });
			codeArea.getStylesheets().add(getClass().getClassLoader().getResource("scripting_styles.css").toExternalForm());
			return new CodeAreaControl(codeArea);
		} catch (Exception e) {
			// Default to superclass implementation
			logger.error("Unable to create console area", e);
			return super.getNewEditor();
		}
	}
	
	
	private static StyleSpans<Collection<String>> computeHighlighting(final String text) {
        Matcher matcher = PATTERN.matcher(text);
        int lastKwEnd = 0;
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        while (matcher.find()) {
            String styleClass =
                    matcher.group("KEYWORD") != null ? "keyword" :
                    matcher.group("METHOD") != null ? "method" :
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
		public Control getControl() {
			return textArea;
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
		public void paste() {
			textArea.paste();
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
		
	}
	
	
}
