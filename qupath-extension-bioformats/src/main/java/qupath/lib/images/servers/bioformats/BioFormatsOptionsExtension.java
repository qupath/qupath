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

package qupath.lib.images.servers.bioformats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.StringTokenizer;

import org.controlsfx.control.action.Action;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.StringProperty;
import qupath.fx.prefs.controlsfx.PropertySheetUtils;
import qupath.lib.common.Version;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.actions.ActionTools;
import qupath.lib.gui.actions.annotations.ActionConfig;
import qupath.lib.gui.actions.annotations.ActionMenu;
import qupath.fx.dialogs.Dialogs;
import qupath.lib.gui.extensions.QuPathExtension;
import qupath.lib.gui.localization.QuPathResources;
import qupath.fx.prefs.annotations.StringPref;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.fx.prefs.annotations.BooleanPref;
import qupath.fx.prefs.annotations.DirectoryPref;
import qupath.fx.prefs.annotations.IntegerPref;
import qupath.fx.prefs.annotations.PrefCategory;
import qupath.lib.images.writers.ome.OMEPyramidWriterCommand;
import qupath.lib.images.writers.ome.zarr.OMEZarrWriterCommand;

/**
 * A QuPath extension that adds options relating to the BioFormatsImageServer to the main QuPath preference pane.
 * 
 * @author Pete Bankhead
 */
public class BioFormatsOptionsExtension implements QuPathExtension {
	
	private static final Logger logger = LoggerFactory.getLogger(BioFormatsOptionsExtension.class);
	
	private String bfVersion = null;

	@Override
	public void installExtension(QuPathGUI qupath) {
		
		// Request Bio-Formats version - if null, Bio-Formats is missing & we can't install the extension
		bfVersion = BioFormatsServerBuilder.getBioFormatsVersion();
		if (bfVersion == null) {
			Dialogs.showErrorMessage(getName(),
						"The Bio-Formats extension is installed, but 'bioformats_package.jar' is missing!\n\n" + 
						"Please make sure both .jar files are copied to the QuPath extensions folder.");
			return;
		} else {
			logger.info("Bio-Formats version {}", bfVersion);
		}
		
		var actions = new OmeTiffWriterAction(qupath);
		qupath.installActions(ActionTools.getAnnotatedActions(actions));
		qupath.installActions(ActionTools.getAnnotatedActions(new OmeZarrWriterAction(qupath)));

		var prefs = new BioFormatsPreferences();
		qupath.getPreferencePane()
				.getPropertySheet()
				.getItems()
				.addAll(PropertySheetUtils.parseAnnotatedItemsWithResources(QuPathResources.getLocalizedResourceManager(), prefs));

		if (BioFormatsServerOptions.allowMemoization()) {
			logMemoizationStatus(prefs.options);
		}
	}
	
	
	private void logMemoizationStatus(BioFormatsServerOptions options) {
		if (BioFormatsServerOptions.allowMemoization()) {
			int millis = options.getMemoizationTimeMillis();
			if (millis < 0) {
				logger.info("If Bio-Formats is slow to load images, setting the Bio-Formats memoization time in QuPath's preferences may help (this will create temp files)");
			} else {
				logger.info("Bio-Formats memoization time limit: {} ms (temp files may be created to speed up image loading)", millis);
			}
		}
	}
	

	private static void fillCollectionWithTokens(String text, Collection<String> collection) {
		fillCollectionWithTokens(new StringTokenizer(text), collection);
	}

	private static void fillCollectionWithTokens(StringTokenizer tokenizer, Collection<String> collection) {
		List<String> list = new ArrayList<>();
		while (tokenizer.hasMoreTokens())
			list.add(tokenizer.nextToken());
		collection.clear();
		collection.addAll(list);
	}

	@Override
	public String getName() {
		if (bfVersion == null)
			return QuPathResources.getString("Extension.BioFormats") + " (Bio-Formats library is missing!)";
		else
			return QuPathResources.getString("Extension.BioFormats") + " (Bio-Formats " + bfVersion + ")";
	}

	@Override
	public String getDescription() {
		String text = QuPathResources.getString("Extension.BioFormats.description");
		if (bfVersion == null)
			text = text + "\n" + QuPathResources.getString("Extension.BioFormats.missing.description");
		return text;
	}
	
	/**
	 * Returns the version stored within this jar, because it is matched to the QuPath version.
	 */
	@Override
	public Version getQuPathVersion() {
		return getVersion();
	}
	
	@PrefCategory("Prefs.BioFormats")
	public static class BioFormatsPreferences {
		
		private BioFormatsServerOptions options = BioFormatsServerOptions.getInstance();

		@BooleanPref("Prefs.BioFormats.enable")
		public final BooleanProperty enableBioformats = PathPrefs.createPersistentPreference("bfEnableBioformats", options.bioformatsEnabled());
		@BooleanPref("Prefs.BioFormats.localOnly")
		public final BooleanProperty filesOnly = PathPrefs.createPersistentPreference("bfFilesOnly", options.getFilesOnly());
		@BooleanPref("Prefs.BioFormats.useParallelization")
		public final BooleanProperty useParallelization = PathPrefs.createPersistentPreference("bfUseParallelization", options.requestParallelization());
		@IntegerPref("Prefs.BioFormats.memoizationTimeMillis")
		public final IntegerProperty memoizationTimeMillis = PathPrefs.createPersistentPreference("bfMemoizationTimeMillis", options.getMemoizationTimeMillis());

		@DirectoryPref("Prefs.BioFormats.pathMemoization")
		public final StringProperty pathMemoization = PathPrefs.createPersistentPreference("bfPathMemoization", options.getPathMemoization());
		@StringPref("Prefs.BioFormats.alwaysUseExtensions")
		public final StringProperty useExtensions = PathPrefs.createPersistentPreference("bfUseAlwaysExtensions", String.join(" ", options.getUseAlwaysExtensions()));
		@StringPref("Prefs.BioFormats.skipExtensions")
		public final StringProperty skipExtensions = PathPrefs.createPersistentPreference("bfSkipAlwaysExtensions", String.join(" ", options.getSkipAlwaysExtensions()));

		
		private BioFormatsPreferences() {
						
			// Set options using any values previously stored
			options.setFilesOnly(filesOnly.get());
			options.setPathMemoization(pathMemoization.get());
			options.setBioformatsEnabled(enableBioformats.get());
			options.setRequestParallelization(useParallelization.get());
			options.setMemoizationTimeMillis(memoizationTimeMillis.get());
			fillCollectionWithTokens(useExtensions.get(), options.getUseAlwaysExtensions());
			fillCollectionWithTokens(skipExtensions.get(), options.getSkipAlwaysExtensions());

			// Listen for property changes
			enableBioformats.addListener((v, o, n) -> options.setBioformatsEnabled(n));
			filesOnly.addListener((v, o, n) -> options.setFilesOnly(n));
			useParallelization.addListener((v, o, n) -> options.setRequestParallelization(n));
			memoizationTimeMillis.addListener((v, o, n) -> options.setMemoizationTimeMillis(n.intValue()));

			pathMemoization.addListener((v, o, n) -> options.setPathMemoization(n));
			useExtensions.addListener((v, o, n) -> fillCollectionWithTokens(n, options.getUseAlwaysExtensions()));
			skipExtensions.addListener((v, o, n) -> fillCollectionWithTokens(n, options.getSkipAlwaysExtensions()));
		}
	}
	
	public static class OmeTiffWriterAction {
		
		@ActionMenu(value = {"Menu.File", "Menu.File.ExportImage"})
		@ActionConfig("Action.BioFormats.exportOmeTif")
		public final Action actionWriter;
		
		OmeTiffWriterAction(QuPathGUI qupath) {
			actionWriter = ActionTools.createAction(new OMEPyramidWriterCommand(qupath), "OME-TIFF");
		}
	}

	public static class OmeZarrWriterAction {

		@ActionMenu(value = {"Menu.File", "Menu.File.ExportImage"})
		@ActionConfig("Action.BioFormats.omeZarr")
		public final Action actionWriter;

		public OmeZarrWriterAction(QuPathGUI qupath) {
			actionWriter = ActionTools.createAction(new OMEZarrWriterCommand(qupath), "OME-Zarr");
		}
	}
}
