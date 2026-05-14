package qupath.lib.gui.scripting.richtextfx.stylers;

import java.util.Set;

public class GroovyStyler extends JavaStyler {

    /**
     * Additional keywords in Groovy
     */
    public static final Set<String> GROOVY_KEYWORDS = Set.of(
            "def", "in", "with", "trait"
    );

    public GroovyStyler() {
        super("groovy", true, GROOVY_KEYWORDS, true);
    }

}
