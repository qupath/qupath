/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2023 QuPath developers, The University of Edinburgh
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

package qupath.ext.openslide;

import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.localization.LocalizedResourceManager;
import qupath.fx.prefs.annotations.DirectoryPref;
import qupath.fx.prefs.annotations.PrefCategory;
import qupath.fx.prefs.annotations.StringPref;
import qupath.fx.prefs.controlsfx.PropertySheetUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.servers.openslide.jna.OpenSlideLoader;

import java.io.File;

@PrefCategory("category.openslide")
public class OpenSlideExtension implements QuPathExtension {

    private static final Logger logger = LoggerFactory.getLogger(OpenSlideExtension.class);

    // TODO: Check why this fails if I use @DirectoryPref!
    @StringPref("pref.openslide.path")
    public StringProperty openslidePathProperty =
            PathPrefs.createPersistentPreference("openslide.path", "");

    private ChangeListener<String> openslidePathListener = this::handleOpenSlideDirectoryChange;

    @Override
    public void installExtension(QuPathGUI qupath) {
        installPreferences(qupath);
        openslidePathProperty.addListener(openslidePathListener);
        if (OpenSlideLoader.tryToLoadQuietly(openslidePathProperty.get())) {
            logger.warn("OpenSlide not found! Please specify the directory containing the OpenSlide library in the preferences.");
        } else {
            logger.info("OpenSlide loaded successfully: {}", OpenSlideLoader.getLibraryVersion());
        }
    }

    private void installPreferences(QuPathGUI qupath) {
        var prefs = PropertySheetUtils.parseAnnotatedItemsWithResources(
                LocalizedResourceManager.createInstance("qupath.ext.openslide.strings"),
                this);
        qupath.getPreferencePane().getPropertySheet().getItems().addAll(prefs);
    }

    private void handleOpenSlideDirectoryChange(ObservableValue<? extends String> value, String oldValue, String newValue) {
        if (!OpenSlideLoader.isOpenSlideAvailable() && newValue != null) {
            if (new File(newValue).isDirectory()) {
                try {
                    if (OpenSlideLoader.tryToLoad(newValue))
                        logger.info("OpenSlide loaded successfully: {}", OpenSlideLoader.getLibraryVersion());
                } catch (Throwable t) {
                    logger.debug("OpenSlide loading failed", t);
                }
            }
        }
    }

    @Override
    public String getName() {
        return "OpenSlide extension";
    }

    @Override
    public String getDescription() {
        return "Provides support for OpenSlide images.\nThis includes specifying a path to an OpenSlide installation.";
    }

}
