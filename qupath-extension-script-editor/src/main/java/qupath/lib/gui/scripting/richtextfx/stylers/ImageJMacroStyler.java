package qupath.lib.gui.scripting.richtextfx.stylers;

import ij.macro.MacroConstants;
import qupath.lib.gui.scripting.languages.ImageJMacroLanguage;

import java.util.Set;

public class ImageJMacroStyler extends JavaStyler {

    private static Set<String> ijKeywords = Set.of(MacroConstants.keywords);

    public ImageJMacroStyler() {
        super(ImageJMacroLanguage.NAME, false,
                ijKeywords, false);
    }

}
