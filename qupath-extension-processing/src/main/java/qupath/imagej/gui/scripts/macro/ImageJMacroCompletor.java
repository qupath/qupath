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


package qupath.imagej.gui.scripts.macro;

import ij.macro.MacroConstants;
import qupath.lib.gui.scripting.completors.DefaultAutoCompletor;
import qupath.lib.scripting.languages.AutoCompletions;

/**
 * And autocompletor for the ImageJ macro language.
 */
public class ImageJMacroCompletor extends DefaultAutoCompletor  {

    /**
     * Constructor.
     */
    public ImageJMacroCompletor() {
        super(false);

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
