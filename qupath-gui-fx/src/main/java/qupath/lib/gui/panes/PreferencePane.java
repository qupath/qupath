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

package qupath.lib.gui.panes;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import qupath.fx.dialogs.Dialogs;
import qupath.fx.localization.LocaleManager;
import qupath.fx.localization.LocaleSnapshot;
import qupath.fx.prefs.annotations.FilePref;
import qupath.fx.prefs.controlsfx.PropertyEditorFactory;
import qupath.fx.prefs.controlsfx.PropertyItemBuilder;
import qupath.fx.prefs.controlsfx.PropertyItemParser;
import qupath.fx.prefs.controlsfx.PropertySheetBuilder;
import qupath.fx.prefs.controlsfx.PropertySheetUtils;
import qupath.fx.prefs.annotations.BooleanPref;
import qupath.fx.prefs.annotations.ColorPref;
import qupath.fx.prefs.annotations.DirectoryPref;
import qupath.fx.prefs.annotations.DoublePref;
import qupath.fx.prefs.annotations.IntegerPref;
import qupath.fx.prefs.annotations.LocalePref;
import qupath.fx.prefs.annotations.Pref;
import qupath.fx.prefs.annotations.PrefCategory;
import qupath.lib.common.LogTools;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.logging.LogManager.LogLevel;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.AutoUpdateType;
import qupath.lib.gui.prefs.PathPrefs.DetectionTreeDisplayModes;
import qupath.lib.gui.prefs.PathPrefs.FontSize;
import qupath.lib.gui.prefs.PathPrefs.ImageTypeSetting;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.prefs.QuPathStyleManager.StyleOption;
import qupath.lib.gui.prefs.SystemMenuBar;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.gui.tools.CommandFinderTools.CommandBarDisplay;

import java.util.Locale;
import java.util.Objects;

/**
 * QuPath's preference pane, giving a means to modify many of the properties within PathPrefs.
 * 
 * @author Pete Bankhead
 *
 */
public class PreferencePane {

	private static final Logger logger = LoggerFactory.getLogger(PreferencePane.class);

	private PropertySheet propSheet;
	
	private static LocaleManager localeManager = new LocaleManager(QuPathResources::hasDefaultBundleForLocale);
	
	private BorderPane pane;

	private LocaleSnapshot localeSnapshot = new LocaleSnapshot();
	private StringProperty localeChangedText = QuPathResources.getLocalizedResourceManager().createProperty("Prefs.localeChanged");
	private BooleanBinding localeChanged;
	
	@SuppressWarnings("javadoc")
	public PreferencePane() {
		initializePane();
	}
	
	private void initializePane() {
		pane = new BorderPane();
		propSheet = createPropertySheet();

		var label = createLocaleChangedLabel();
		localeSnapshot.refresh();

		pane.setCenter(propSheet);
		pane.setBottom(label);
	}
	
	private PropertySheet createPropertySheet() {
		var factory = new PropertyEditorFactory();
		factory.setReformatEnums(
				SystemMenuBar.SystemMenuBarOption.class,
				FontWeight.class,
				FontSize.class,
				LogLevel.class,
				Level.class);
		var parser = new PropertyItemParser()
				.setResourceManager(QuPathResources.getLocalizedResourceManager())
				.setLocaleManager(new LocaleManager(QuPathResources::hasDefaultBundleForLocale));
		return new PropertySheetBuilder()
				.parser(parser)
				.editorFactory(factory)
				.addAnnotatedProperties(new AppearancePreferences())
				.addAnnotatedProperties(new GeneralPreferences())
				.addAnnotatedProperties(new UndoRedoPreferences())
				.addAnnotatedProperties(new LocalePreferences())
				.addAnnotatedProperties(new InputOutputPreferences())

				.addAnnotatedProperties(new ViewerPreferences())
				.addAnnotatedProperties(new ExtensionPreferences())
				.addAnnotatedProperties(new MeasurementPreferences())
				.addAnnotatedProperties(new ScriptingPreferences())

				.addAnnotatedProperties(new DrawingPreferences())
				.addAnnotatedProperties(new ObjectPreferences())
				.build();
	}


	/**
	 * Get the property sheet for this {@link PreferencePane}.
	 * This is a {@link Node} that may be added to a scene for display.
	 * @return
	 */
	public PropertySheet getPropertySheet() {
		return propSheet;
	}
	
	/**
	 * Get a pane to display. This includes the property sheet, and  
	 * additional information that should be displayed to the user.
	 * @return
	 * @since v0.5.0
	 */
	public Pane getPane() {
		return pane;
	}
	
	
	private Label createLocaleChangedLabel() {
		var label = new Label();
		label.textProperty().bind(localeChangedText);
		localeChanged = createLocaleChangedBinding();
		label.visibleProperty().bind(localeChanged);
		label.setStyle("-fx-text-fill: -qp-script-error-color;");
		label.setPadding(new Insets(5.0));
		label.setMaxHeight(Double.MAX_VALUE);
		label.setWrapText(true);
		label.setMaxWidth(Double.MAX_VALUE);
		label.setAlignment(Pos.CENTER);
		label.setTextAlignment(TextAlignment.CENTER);
		return label;
	}
	
	private BooleanBinding createLocaleChangedBinding() {
		var initalLocale = PathPrefs.defaultLocaleProperty().get();
		var initalLocaleDisplay = PathPrefs.defaultLocaleDisplayProperty().get();
		var initalLocaleFormat = PathPrefs.defaultLocaleFormatProperty().get();
		return Bindings.createBooleanBinding(() -> {
					return !Objects.equals(initalLocale, PathPrefs.defaultLocaleProperty().get()) ||
							!Objects.equals(initalLocaleDisplay, PathPrefs.defaultLocaleDisplayProperty().get()) ||
							!Objects.equals(initalLocaleFormat, PathPrefs.defaultLocaleFormatProperty().get());
				}, PathPrefs.defaultLocaleProperty(),
				PathPrefs.defaultLocaleDisplayProperty(),
				PathPrefs.defaultLocaleFormatProperty());
	}

	
		
	@PrefCategory("Prefs.Appearance")
	public static class AppearancePreferences {
		
		@Pref(value = "Prefs.Appearance.theme", type = StyleOption.class, choiceMethod = "getStyles")
		public final ObjectProperty<StyleOption> theme = QuPathStyleManager.selectedStyleProperty();

		@Pref(value = "Prefs.Appearance.font", type = QuPathStyleManager.Fonts.class)
		public final ObjectProperty<QuPathStyleManager.Fonts> autoUpdate = QuPathStyleManager.fontProperty();

		@BooleanPref(value = "Prefs.Appearance.badges")
		public final BooleanProperty badges = PathPrefs.showToolBarBadgesProperty();

		@Pref(value = "Prefs.Appearance.systemMenubar", type = SystemMenuBar.SystemMenuBarOption.class)
		public final ObjectProperty<SystemMenuBar.SystemMenuBarOption> systemMenubar = SystemMenuBar.supportsSystemMenubar() ?
				SystemMenuBar.systemMenubarProperty() : null;

		public final ObservableList<StyleOption> getStyles() {
			return QuPathStyleManager.availableStylesProperty();
		}

	}
	

	@PrefCategory("Prefs.General")
	public static class GeneralPreferences {
				
		@BooleanPref("Prefs.General.showStartupMessage")
		public final BooleanProperty startupMessage = PathPrefs.showStartupMessageProperty();

		@FilePref(value = "Prefs.General.startupScriptPath", extensions = "*.groovy")
		public final StringProperty startupScriptPath = PathPrefs.startupScriptProperty();

		@Pref(value = "Prefs.General.checkForUpdates", type = AutoUpdateType.class)
		public final ObjectProperty<AutoUpdateType> autoUpdate = PathPrefs.autoUpdateCheckProperty();

		@DoublePref("Prefs.General.maxMemory")
		public final DoubleProperty maxMemoryGB = PathPrefs.hasJavaPreferences() ? createMaxMemoryProperty() : null;

		@DoublePref("Prefs.General.tileCache")
		public final DoubleProperty tileCache = PathPrefs.tileCachePercentageProperty();

		@BooleanPref("Prefs.General.showImageNameInTitle")
		public final BooleanProperty showImageNameInTitle = PathPrefs.showImageNameInTitleProperty();

		@BooleanPref("Prefs.General.maskImageNames")
		public final BooleanProperty maskImageNames = PathPrefs.maskImageNamesProperty();

		@BooleanPref("Prefs.General.skipProjectUriChecks")
		public final BooleanProperty skipProjectUriChecks = PathPrefs.skipProjectUriChecksProperty();

		@BooleanPref("Prefs.General.logFiles")
		public final BooleanProperty createLogFiles = PathPrefs.doCreateLogFilesProperty();

		@Pref(value = "Prefs.General.logLevel", type = LogLevel.class)
		public final ObjectProperty<LogLevel> logLevel = LogManager.rootLogLevelProperty();

		@IntegerPref("Prefs.General.numThreads")
		public final IntegerProperty numThreads = PathPrefs.numCommandThreadsProperty();

		@Pref(value = "Prefs.General.imageType", type = ImageTypeSetting.class)
		public final ObjectProperty<ImageTypeSetting> setImageType = PathPrefs.imageTypeSettingProperty();

		@Pref(value = "Prefs.General.commandBar", type = CommandBarDisplay.class)
		public final ObjectProperty<CommandBarDisplay> commandBarDisplay = CommandFinderTools.commandBarDisplayProperty();
		
		@BooleanPref("Prefs.General.showExperimental")
		public final BooleanProperty showExperimentalCommands = PathPrefs.showExperimentalOptionsProperty();

		@BooleanPref("Prefs.General.showTMA")
		public final BooleanProperty showTMACommands = PathPrefs.showTMAOptionsProperty();

		@BooleanPref("Prefs.General.showDeprecated")
		public final BooleanProperty showDeprecatedCommands = PathPrefs.showLegacyOptionsProperty();
		
		@Pref(value = "Prefs.General.hierarchyDisplay", type = DetectionTreeDisplayModes.class)
		public final ObjectProperty<DetectionTreeDisplayModes> hierarchyDisplayMode = PathPrefs.detectionTreeDisplayModeProperty();
		
		
		private DoubleProperty createMaxMemoryProperty() {
			long maxMemoryMB = Runtime.getRuntime().maxMemory() / 1024 / 1024;
			DoubleProperty propMemoryGB = new SimpleDoubleProperty(maxMemoryMB / 1024.0);
			propMemoryGB.addListener((v, o, n) -> {
				int requestedMemoryMB = (int)Math.round(propMemoryGB.get() * 1024.0);
				if (requestedMemoryMB > 1024) {
					boolean success = false;
					try {
						PathPrefs.maxMemoryMBProperty().set(requestedMemoryMB);		
						success = requestedMemoryMB == PathPrefs.maxMemoryMBProperty().get();
					} catch (Exception e) {
						logger.error(e.getLocalizedMessage(), e);
					}
					if (success) {
						Dialogs.showInfoNotification(QuPathResources.getString("Prefs.General.maxMemory"),
								QuPathResources.getString("Prefs.maxMemoryChanged")
								);
					} else {
						Dialogs.showErrorMessage(QuPathResources.getString("Prefs.General.maxMemory"),
								QuPathResources.getString("Prefs.maxMemoryFailed"));						
					}
				}
			});	
			return propMemoryGB;
		}
				
	}	
	
	@PrefCategory("Prefs.Locale")
	public static class LocalePreferences {
		
		@LocalePref(value = "Prefs.Locale.default", availableLanguagesOnly = true)
		public final ObjectProperty<Locale> localeDefault = PathPrefs.defaultLocaleProperty();

		@LocalePref(value = "Prefs.Locale.display", availableLanguagesOnly = true)
		public final ObjectProperty<Locale> localeDisplay = PathPrefs.defaultLocaleDisplayProperty();

		@LocalePref("Prefs.Locale.format")
		public final ObjectProperty<Locale> localeFormat = PathPrefs.defaultLocaleFormatProperty();

	}
	
	
		
	@PrefCategory("Prefs.Undo")
	public static class UndoRedoPreferences {
		
		@IntegerPref("Prefs.Undo.maxUndoLevels")
		public final IntegerProperty maxUndoLevels = PathPrefs.maxUndoLevelsProperty();
		
		@IntegerPref("Prefs.Undo.maxUndoHierarchySize")
		public final IntegerProperty maxUndoHierarchySize = PathPrefs.maxUndoHierarchySizeProperty();
		
	}
	
	
	@PrefCategory("Prefs.InputOutput")
	public static class InputOutputPreferences {
		
		@IntegerPref("Prefs.InputOutput.minPyramidDimension")
		public final IntegerProperty minimumPyramidDimension = PathPrefs.minPyramidDimensionProperty();
		
		@DoublePref("Prefs.InputOutput.tmaExportDownsample")
		public final DoubleProperty tmaExportDownsample = PathPrefs.tmaExportDownsampleProperty();
		
	}
	
	
	@PrefCategory("Prefs.Viewer")
	public static class ViewerPreferences {
		
		@ColorPref("Prefs.Viewer.backgroundColor")
		public final IntegerProperty backgroundColor = PathPrefs.viewerBackgroundColorProperty();

		@BooleanPref("Prefs.Viewer.alwaysPaintSelected")
		public final BooleanProperty alwaysPaintSelected = PathPrefs.alwaysPaintSelectedObjectsProperty();

		@BooleanPref("Prefs.Viewer.keepDisplaySettings")
		public final BooleanProperty keepDisplaySettings = PathPrefs.keepDisplaySettingsProperty();

		@BooleanPref("Prefs.Viewer.interpolateBilinear")
		public final BooleanProperty interpolateBilinear = PathPrefs.viewerInterpolateBilinearProperty();

		@DoublePref("Prefs.Viewer.autoSaturationPercent")
		public final DoubleProperty autoSaturationPercent = PathPrefs.autoBrightnessContrastSaturationPercentProperty();

		@BooleanPref("Prefs.Viewer.invertZSlider")
		public final BooleanProperty invertZSlider = PathPrefs.invertZSliderProperty();

		@IntegerPref("Prefs.Viewer.scrollSpeed")
		public final IntegerProperty scrollSpeed = PathPrefs.scrollSpeedProperty();

		@IntegerPref("Prefs.Viewer.navigationSpeed")
		public final IntegerProperty navigationSpeed = PathPrefs.navigationSpeedProperty();
		

		@BooleanPref("Prefs.Viewer.navigationAcceleration")
		public final BooleanProperty navigationAcceleration = PathPrefs.navigationAccelerationProperty();

		@BooleanPref("Prefs.Viewer.skipMissingCores")
		public final BooleanProperty skipMissingCores = PathPrefs.skipMissingCoresProperty();

		@BooleanPref("Prefs.Viewer.iseScrollGestures")
		public final BooleanProperty iseScrollGestures = PathPrefs.useScrollGesturesProperty();

		@BooleanPref("Prefs.Viewer.invertScrolling")
		public final BooleanProperty invertScrolling = PathPrefs.invertScrollingProperty();

		@BooleanPref("Prefs.Viewer.useZoomGestures")
		public final BooleanProperty useZoomGestures = PathPrefs.useZoomGesturesProperty();

		@BooleanPref("Prefs.Viewer.useRotateGestures")
		public final BooleanProperty useRotateGestures = PathPrefs.useRotateGesturesProperty();

		@BooleanPref("Prefs.Viewer.enableFreehand")
		public final BooleanProperty enableFreehand = PathPrefs.enableFreehandToolsProperty();

		@BooleanPref("Prefs.Viewer.doubleClickToZoom")
		public final BooleanProperty doubleClickToZoom = PathPrefs.doubleClickToZoomProperty();
		
		
		@Pref(value = "Prefs.Viewer.scalebarFontSize", type = FontSize.class)		
		public final ObjectProperty<FontSize> scalebarFontSize = PathPrefs.scalebarFontSizeProperty();

		@Pref(value = "Prefs.Viewer.scalebarFontWeight", type = FontWeight.class)		
		public final ObjectProperty<FontWeight> scalebarFontWeight = PathPrefs.scalebarFontWeightProperty();

		@DoublePref("Prefs.Viewer.scalebarLineWidth")
		public final DoubleProperty scalebarLineWidth = PathPrefs.scalebarLineWidthProperty();

		@Pref(value = "Prefs.Viewer.locationFontSize", type = FontSize.class)		
		public final ObjectProperty<FontSize> locationFontSize = PathPrefs.locationFontSizeProperty();

		@BooleanPref("Prefs.Viewer.calibratedLocationString")
		public final BooleanProperty calibratedLocationString = PathPrefs.useCalibratedLocationStringProperty();

		@DoublePref("Prefs.Viewer.gridSpacingX")
		public final DoubleProperty gridSpacingX = PathPrefs.gridSpacingXProperty();

		@DoublePref("Prefs.Viewer.gridSpacingY")
		public final DoubleProperty gridSpacingY = PathPrefs.gridSpacingYProperty();

		@BooleanPref("Prefs.Viewer.gridScaleMicrons")
		public final BooleanProperty gridScaleMicrons = PathPrefs.gridScaleMicronsProperty();

	}
	
	
	@PrefCategory("Prefs.Extensions")
	public static class ExtensionPreferences {
		
		@DirectoryPref("Prefs.Extensions.userPath")
		public final Property<String> scriptsPath = PathPrefs.userPathProperty();

	}
	
	
	@PrefCategory("Prefs.Measurements")
	public static class MeasurementPreferences {
		
		@BooleanPref("Prefs.Measurements.thumbnails")
		public final BooleanProperty showMeasurementTableThumbnails = PathPrefs.showMeasurementTableThumbnailsProperty();

		@BooleanPref("Prefs.Measurements.ids")
		public final BooleanProperty showMeasurementTableObjectIDs = PathPrefs.showMeasurementTableObjectIDsProperty();

	}
	
	
	@PrefCategory("Prefs.Scripting")
	public static class ScriptingPreferences {
		
		@DirectoryPref("Prefs.Scripting.scriptsPath")
		public final StringProperty scriptsPath = PathPrefs.scriptsPathProperty();

	}

	
	@PrefCategory("Prefs.Drawing")
	public static class DrawingPreferences {
		
		@BooleanPref("Prefs.Drawing.returnToMove")
		public final BooleanProperty returnToMove = PathPrefs.returnToMoveModeProperty();

		@BooleanPref("Prefs.Drawing.pixelSnapping")
		public final BooleanProperty pixelSnapping = PathPrefs.usePixelSnappingProperty();

		@BooleanPref("Prefs.Drawing.clipROIsForHierarchy")
		public final BooleanProperty clipROIsForHierarchy = PathPrefs.clipROIsForHierarchyProperty();

		@IntegerPref("Prefs.Drawing.brushDiameter")
		public final IntegerProperty brushDiameter = PathPrefs.brushDiameterProperty();		
		
		@BooleanPref("Prefs.Drawing.tileBrush")
		public final BooleanProperty tileBrush = PathPrefs.useTileBrushProperty();

		@BooleanPref("Prefs.Drawing.brushScaleByMag")
		public final BooleanProperty brushScaleByMag = PathPrefs.brushScaleByMagProperty();

		@BooleanPref("Prefs.Drawing.useMultipoint")
		public final BooleanProperty useMultipoint = PathPrefs.multipointToolProperty();
		
		@IntegerPref("Prefs.Drawing.pointRadius")
		public final IntegerProperty pointRadius = PathPrefs.pointRadiusProperty();
		
	}
	
	
	@PrefCategory("Prefs.Objects")
	public static class ObjectPreferences {
		
		@IntegerPref("Prefs.Objects.clipboard")
		public final IntegerProperty maxClipboardObjects = PathPrefs.maxObjectsToClipboardProperty();

		@DoublePref("Prefs.Objects.annotationLineThickness")
		public final DoubleProperty annotationStrokeThickness = PathPrefs.annotationStrokeThicknessProperty();

		@DoublePref("Prefs.Objects.detectionLineThickness")
		public final DoubleProperty detectonStrokeThickness = PathPrefs.detectionStrokeThicknessProperty();

		@BooleanPref("Prefs.Objects.newDetectionRendering")
		public final BooleanProperty newDetectionRendering = PathPrefs.newDetectionRenderingProperty();

		@BooleanPref("Prefs.Objects.useSelectedColor")
		public final BooleanProperty useSelectedColor = PathPrefs.useSelectedColorProperty();

		@ColorPref("Prefs.Objects.selectedColor")
		public final IntegerProperty selectedColor = PathPrefs.colorSelectedObjectProperty();

		@ColorPref("Prefs.Objects.defaultColor")
		public final IntegerProperty defaultColor = PathPrefs.colorDefaultObjectsProperty();

		@ColorPref("Prefs.Objects.tmaCoreColor")
		public final IntegerProperty tmaColor = PathPrefs.colorTMAProperty();

		@DoublePref("Prefs.Objects.tmaCoreMissingOpacity")
		public final DoubleProperty tmaMissingOpacity = PathPrefs.opacityTMAMissingProperty();

	}


	/**
	 * Request that all the property editors are regenerated.
	 * This is useful if the Locale has changed, and so the text may need to be updated.
	 */
	public void refreshAllEditors() {
		// Attempt to force a property sheet refresh if the locale was changed
		if (localeSnapshot.hasChanged()) {
			// Alternative code to rebuild the editors
			logger.info("Refreshing preferences because of locale change");
			PropertySheetUtils.refreshEditors(propSheet);
			localeSnapshot.refresh();
		}
		// Maybe we have new locales to support
		localeManager.refreshAvailableLocales();
	}
	

	/**
	 * Add a new preference based on a specified Property.
	 * 
	 * @param prop
	 * @param cls
	 * @param name
	 * @param category
	 * @param description
	 * @deprecated use {@link PropertyItemBuilder} instead
	 */
	@Deprecated
	public <T> void addPropertyPreference(final Property<T> prop, final Class<? extends T> cls, final String name, final String category, final String description) {
		LogTools.warnOnce(logger, "PreferencePane.addPropertyPreference is deprecated - use PropertyItemBuilder instead");
		PropertySheet.Item item = new PropertyItemBuilder<>(prop, cls)
				.resourceManager(QuPathResources.getLocalizedResourceManager())
				.name(name)
				.category(category)
				.description(description)
				.build();
		propSheet.getItems().add(item);
	}
	
	
	/**
	 * Add a new color preference based on a specified IntegerProperty (storing a packed RGBA value).
	 * 
	 * @param prop
	 * @param name
	 * @param category
	 * @param description
	 * @deprecated use {@link PropertyItemBuilder} instead
	 */
	@Deprecated
	public void addColorPropertyPreference(final IntegerProperty prop, final String name, final String category, final String description) {
		LogTools.warnOnce(logger, "PreferencePane.addColorPropertyPreference is deprecated - use PropertyItemBuilder instead");
		var item = new PropertyItemBuilder<>(prop, Integer.class)
				.resourceManager(QuPathResources.getLocalizedResourceManager())
				.name(name)
				.category(category)
				.description(description)
				.propertyType(PropertyItemBuilder.PropertyType.COLOR)
				.build();
		propSheet.getItems().add(item);
	}
	
	
	/**
	 * Add a new directory preference based on a specified StringProperty.
	 * 
	 * @param prop
	 * @param name
	 * @param category
	 * @param description
	 * @deprecated use {@link PropertyItemBuilder} instead
	 */
	@Deprecated
	public void addDirectoryPropertyPreference(final Property<String> prop, final String name, final String category, final String description) {
		LogTools.warnOnce(logger, "PreferencePane.addDirectoryPropertyPreference is deprecated - use PropertyItemBuilder instead");
		var item = new PropertyItemBuilder<>(prop, String.class)
				.resourceManager(QuPathResources.getLocalizedResourceManager())
				.name(name)
				.category(category)
				.description(description)
				.propertyType(PropertyItemBuilder.PropertyType.DIRECTORY)
				.build();
		propSheet.getItems().add(item);
	}
	
	
	/**
	 * Add a new choice preference, to select from a list of possibilities.
	 * 
	 * @param prop
	 * @param choices
	 * @param cls
	 * @param name
	 * @param category
	 * @param description
	 * @deprecated use {@link PropertyItemBuilder} instead
	 */
	@Deprecated
	public <T> void addChoicePropertyPreference(final Property<T> prop, final ObservableList<T> choices, final Class<? extends T> cls, final String name, final String category, final String description) {
		addChoicePropertyPreference(prop, choices, cls, name, category, description, false);
	}
	

	/**
	 * Add a new choice preference, to select from an optionally searchable list of possibilities.
	 * 
	 * @param prop
	 * @param choices
	 * @param cls
	 * @param name
	 * @param category
	 * @param description
	 * @param makeSearchable make the choice item's editor searchable (useful for long lists)
	 * @deprecated use {@link PropertyItemBuilder} instead
	 */
	@Deprecated
	public <T> void addChoicePropertyPreference(final Property<T> prop, final ObservableList<T> choices, final Class<? extends T> cls, 
			final String name, final String category, final String description, boolean makeSearchable) {
		LogTools.warnOnce(logger, "PreferencePane.addChoicePropertyPreference is deprecated - use PropertyItemBuilder instead");
		var item = new PropertyItemBuilder<>(prop, cls)
				.resourceManager(QuPathResources.getLocalizedResourceManager())
				.name(name)
				.category(category)
				.description(description)
				.choices(choices)
				.propertyType(makeSearchable ? PropertyItemBuilder.PropertyType.SEARCHABLE_CHOICE : PropertyItemBuilder.PropertyType.CHOICE)
				.build();
		propSheet.getItems().add(item);
	}

	
	/**
	 * Create a default {@link Item} for a generic property.
	 * @param <T> type of the property
	 * @param property the property
	 * @param cls the property type
	 * @return a new {@link PropertySheet.Item}
	 * @deprecated use {@link PropertyItemBuilder} instead
	 */
	@Deprecated
	public static <T> PropertySheet.Item createPropertySheetItem(Property<T> property, Class<? extends T> cls) {
		LogTools.warnOnce(logger, "PreferencePane.createPropertySheetItem is deprecated - use PropertyItemBuilder instead");
		return PropertySheetUtils.createPropertySheetItem(property, cls);
	}
		
	
}
