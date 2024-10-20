/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.scripting.completors;

import ij.macro.MacroConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.scripting.languages.AutoCompletions;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;

/**
 * And autocompletor for the ImageJ macro language.
 */
public class ImageJMacroCompletor extends DefaultAutoCompletor  {

    private static final Logger logger = LoggerFactory.getLogger(ImageJMacroCompletor.class);

    /**
     * Constructor.
     */
    public ImageJMacroCompletor() {
        super(Collections.emptyList());
        try {
            addCompletions(readCompletionsFromResource());
        } catch (Exception e) {
            logger.debug("Unable to read ImageJ macro autocompletions: {}", e.getMessage(), e);
            addCompletionsFromCode();
        }
    }

    /**
     * Attempt to read autocompletions from a resource file containing built-in macro functions.
     * @return
     * @throws IOException
     */
    private List<AutoCompletions.Completion> readCompletionsFromResource() throws IOException {
        try (var stream = ImageJMacroCompletor.class.getResourceAsStream("ij-macro-functions.txt")) {
            try (var reader = new BufferedReader(new InputStreamReader(stream))) {
                return reader.lines()
                        .map(String::strip)
                        .filter(l -> !l.isBlank() && !l.startsWith("//") && !l.startsWith("#"))
                        .map(ImageJMacroCompletor::resourceCompletion)
                        .toList();
            }
        }
    }

    /**
     * Add autocompletions by querying MacroConstants.
     * These aren't as informative as using a resource that contains more information.
     */
    private void addCompletionsFromCode() {
        for (var keyword : MacroConstants.keywords) {
            addCompletion(keywordCompletion(keyword, null));
        }

        // Variable function completions should be displayed more like keywords
        for (var keyword : MacroConstants.variableFunctions) {
            addCompletion(keywordCompletion(keyword, "variable function"));
        }

        for (var fun : MacroConstants.functions) {
            addCompletion(functionCompletion(fun, null));
        }
        for (var fun : MacroConstants.numericFunctions) {
            addCompletion(functionCompletion(fun, "numeric function"));
        }
        for (var fun : MacroConstants.arrayFunctions) {
            addCompletion(functionCompletion(fun, "array function"));
        }
        for (var fun : MacroConstants.stringFunctions) {
            addCompletion(functionCompletion(fun, "string function"));
        }
    }

    private static AutoCompletions.Completion resourceCompletion(String text) {
        return AutoCompletions.createJavaCompletion(null, text, textToCompletion(text));
    }

    private static String textToCompletion(String text) {
        int indParen = text.indexOf("(");
        if (indParen <= 0) {
            // No parentheses needed
            return text;
        }
        int indParenClose = text.indexOf(")");
        if (indParenClose <= indParen+1) {
            // No arguments - use unchanged
            return text;
        }
        String functionName = text.substring(0, indParen);
        String arg = text.substring(indParen+1, indParenClose).strip();
        if (arg.startsWith("\"") && arg.endsWith("\"") &&
                arg.replaceAll("\"", "").length() == arg.length() - 2) {
            // We have a function taking a single constant text argument - so we use that unchanged
            return text;
        } else {
            // We have a function taking some other arguments - the user will need to figure out which
            return functionName + "()";
        }
    }

    private static AutoCompletions.Completion functionCompletion(String fun, String category) {
        if (Character.isUpperCase(fun.charAt(0)))
            return keywordCompletion(fun, category);

        return AutoCompletions.createJavaCompletion(null, appendCategory(fun + "(...)", category), fun + "()");
    }

    private static AutoCompletions.Completion keywordCompletion(String keyword, String category) {
        return AutoCompletions.createJavaCompletion(null, appendCategory(keyword, category), keyword);
    }

    private static String appendCategory(String text, String category) {
        if (category == null)
            return text;
        else
            return text + "    \t[" + category + "]";
    }

}
