package qupath.lib.gui.scripting.richtextfx.stylers;

import ij.macro.MacroConstants;

import java.util.Set;

public class ImageJMacroStyler extends JavaStyler {

    private static Set<String> ijKeywords = Set.of(MacroConstants.keywords);

    public ImageJMacroStyler() {
        super("imagej macro", false,
                ijKeywords, false);
    }

}
