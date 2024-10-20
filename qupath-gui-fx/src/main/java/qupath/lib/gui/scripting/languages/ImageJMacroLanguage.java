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

package qupath.lib.gui.scripting.languages;

import qupath.lib.gui.scripting.completors.ImageJMacroCompletor;
import qupath.lib.scripting.languages.ScriptAutoCompletor;
import qupath.lib.scripting.languages.ScriptLanguage;

import java.util.Collections;

public class ImageJMacroLanguage extends ScriptLanguage {

    /**
     * Constant representing the name of this language.
     * It may be used to look up styling or syntax.
     */
    public static final String NAME = "ImageJ macro";

    private static final ImageJMacroLanguage INSTANCE = new ImageJMacroLanguage();

    private ImageJMacroCompletor completor = new ImageJMacroCompletor();

    private ImageJMacroLanguage() {
        super(NAME, Collections.singleton(".ijm"));
    }

    /**
     * Get the static instance of this class.
     * @return instance
     */
    public static ImageJMacroLanguage getInstance() {
        return INSTANCE;
    }

    @Override
    public ScriptAutoCompletor getAutoCompletor() {
        return completor;
    }

}
