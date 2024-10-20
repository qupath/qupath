package qupath.imagej.gui.scripts.macro;

import ij.macro.MacroConstants;
import qupath.lib.gui.scripting.completors.DefaultAutoCompletor;
import qupath.lib.scripting.languages.AutoCompletions;

public class ImageJMacroCompletor extends DefaultAutoCompletor  {

    /**
     * Constructor.
     */
    public ImageJMacroCompletor() {
        super(false);

        for (var fun : MacroConstants.functions) {
            addCompletion(functionCompletion(fun));
        }
        for (var fun : MacroConstants.numericFunctions) {
            addCompletion(functionCompletion(fun));
        }
        for (var fun : MacroConstants.arrayFunctions) {
            addCompletion(functionCompletion(fun));
        }
        for (var fun : MacroConstants.stringFunctions) {
            addCompletion(functionCompletion(fun));
        }
    }

    private static AutoCompletions.Completion functionCompletion(String fun) {
        return AutoCompletions.createJavaCompletion(null, fun + "(...)", fun + "()");
    }

}
