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

import qupath.lib.gui.scripting.languages.DefaultScriptLanguage;

import java.util.Collections;
import java.util.ServiceLoader;

public class ImageJMacroLanguage extends DefaultScriptLanguage {

    /**
     * Constant representing the name of this language.
     * It may be used to look up styling or syntax.
     */
    public static final String NAME = "ImageJ macro";

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
        super(NAME, Collections.singleton(".ijm"), new ImageJMacroCompletor());
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
