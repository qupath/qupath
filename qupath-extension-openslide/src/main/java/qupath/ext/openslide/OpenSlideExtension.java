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

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.localization.LocalizedResourceManager;
import qupath.fx.prefs.annotations.BooleanPref;
import qupath.fx.prefs.annotations.DirectoryPref;
import qupath.fx.prefs.annotations.PrefCategory;
import qupath.fx.prefs.controlsfx.PropertySheetUtils;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.servers.openslide.OpenSlideOptions;
import qupath.lib.images.servers.openslide.jna.OpenSlideLoader;

import java.io.File;
import java.util.Arrays;
import java.util.Objects;

@PrefCategory("category.openslide")
public class OpenSlideExtension implements QuPathExtension {

    private static final Logger logger = LoggerFactory.getLogger(OpenSlideExtension.class);

    @DirectoryPref("pref.openslide.path")
    public final StringProperty openslidePathProperty =
            PathPrefs.createPersistentPreference("openslide.path", "");

    @BooleanPref("pref.openslide.icc-profile")
    public final BooleanProperty openslideUseIccProfile =
            PathPrefs.createPersistentPreference("openslide.use-icc", false);

    @BooleanPref("pref.openslide.crop")
    public final BooleanProperty openslideCrop =
            PathPrefs.createPersistentPreference("openslide.crop", true);

    private final ChangeListener<String> openslidePathListener = this::handleOpenSlideDirectoryChange;
    private final ChangeListener<Boolean> iccProfileListener = this::handleUseIccProfileChange;
    private final ChangeListener<Boolean> cropListener = this::handleCropChange;

    @Override
    public void installExtension(QuPathGUI qupath) {
        installPreferences(qupath);
        openslidePathProperty.addListener(openslidePathListener);

        openslideUseIccProfile.addListener(iccProfileListener);
        handleUseIccProfileChange(openslideUseIccProfile, null, openslideUseIccProfile.get());

        openslideCrop.addListener(cropListener);
        handleCropChange(openslideCrop, null, openslideCrop.get());

        if (!OpenSlideLoader.tryToLoadQuietly(openslidePathProperty.get())) {
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
            // We don't have OpenSlide loaded, and the new value is a directory - then try
            if (isPotentialOpenSlideDirectory(newValue)) {
                try {
                    // This tries to load eagerly
                    if (OpenSlideLoader.tryToLoad(newValue)) {
                        logger.info("OpenSlide loaded successfully: {}", OpenSlideLoader.getLibraryVersion());
                        Dialogs.showInfoNotification("OpenSlide", "OpenSlide loaded successfully: " + OpenSlideLoader.getLibraryVersion());
                    } else {
                        logger.warn("OpenSlide could not be loaded from {}", newValue);
                    }
                } catch (Throwable t) {
                    logger.debug("OpenSlide loading failed", t);
                }
            }
        } else if (newValue != null && newValue.isEmpty()) {
            // We do have OpenSlide loaded, but we want to reset the directory (i.e. use the default, not a custom one)
            Dialogs.showInfoNotification("OpenSlide", "OpenSlide directory reset - please restart QuPath");
        } else if (isPotentialOpenSlideDirectory(newValue)) {
            // We do have OpenSlide loaded, and the new value is a directory - need to restart QuPath to try again
            Dialogs.showInfoNotification("OpenSlide", "OpenSlide directory updated - please restart QuPath");
        }
    }

    private void handleUseIccProfileChange(ObservableValue<? extends Boolean> value, Boolean oldValue, Boolean newValue) {
        OpenSlideOptions.getInstance().setApplyIccProfiles(newValue);
    }

    private void handleCropChange(ObservableValue<? extends Boolean> value, Boolean oldValue, Boolean newValue) {
        OpenSlideOptions.getInstance().setCropBoundingBox(newValue);
    }

    /**
     * Quick check to see whether there is any chance a path is an OpenSlide directory.
     * This is a workaround for the fact that we can potentially get a lot of false positives if
     * a user is typing a directory path.
     * @param path
     * @return
     */
    private static boolean isPotentialOpenSlideDirectory(String path) {
        if (path == null || path.isEmpty())
            return false;
        var file = new File(path);
        if (!file.isDirectory())
            return false;
        return Arrays.stream(Objects.requireNonNull(file.listFiles()))
                .filter(File::isFile)
                .map(f -> f.getName().toLowerCase())
                .anyMatch(n -> n.contains("openslide"));
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
