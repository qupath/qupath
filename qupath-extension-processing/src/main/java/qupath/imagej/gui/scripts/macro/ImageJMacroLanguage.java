package qupath.imagej.gui.scripts.macro;

import qupath.lib.gui.scripting.languages.DefaultScriptLanguage;

import java.util.Collections;
import java.util.ServiceLoader;

public class ImageJMacroLanguage extends DefaultScriptLanguage {

    /**
     * Instance of this language. Can't be final because of {@link ServiceLoader}.
     */
    private static final ImageJMacroLanguage INSTANCE = new ImageJMacroLanguage();

    /**
     * Constructor for ImageJ macro language. This constructor should never be
     * called. Instead, use the static {@link #getInstance()} method.
     * <p>
     * Note: this has to be public for the {@link ServiceLoader} to work.
     */
    private ImageJMacroLanguage() {
        super("ImageJ macro", Collections.singleton(".ijm"), new ImageJMacroCompletor());

        if (INSTANCE != null)
            throw new UnsupportedOperationException("Language classes cannot be instantiated more than once!");
    }

    /**
     * Get the static instance of this class.
     * @return instance
     */
    public static ImageJMacroLanguage getInstance() {
        return INSTANCE;
    }

}
