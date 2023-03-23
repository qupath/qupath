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

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;

import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;
import org.controlsfx.control.PropertySheet.Mode;
import org.controlsfx.control.SearchableComboBox;
import org.controlsfx.property.editor.AbstractPropertyEditor;
import org.controlsfx.property.editor.DefaultPropertyEditorFactory;
import org.controlsfx.property.editor.PropertyEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.Property;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.util.Duration;
import javafx.util.StringConverter;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.localization.QuPathResources;
import qupath.lib.gui.logging.LogManager;
import qupath.lib.gui.logging.LogManager.LogLevel;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.AutoUpdateType;
import qupath.lib.gui.prefs.PathPrefs.DetectionTreeDisplayModes;
import qupath.lib.gui.prefs.PathPrefs.FontSize;
import qupath.lib.gui.prefs.PathPrefs.ImageTypeSetting;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.gui.tools.CommandFinderTools.CommandBarDisplay;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.LocaleListener;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.prefs.QuPathStyleManager.StyleOption;
import qupath.lib.gui.prefs.annotations.BooleanPref;
import qupath.lib.gui.prefs.annotations.ColorPref;
import qupath.lib.gui.prefs.annotations.DirectoryPref;
import qupath.lib.gui.prefs.annotations.DoublePref;
import qupath.lib.gui.prefs.annotations.IntegerPref;
import qupath.lib.gui.prefs.annotations.LocalePref;
import qupath.lib.gui.prefs.annotations.Pref;
import qupath.lib.gui.prefs.annotations.PrefCategory;

/**
 * QuPath's preference pane, giving a means to modify many of the properties within PathPrefs.
 * 
 * @author Pete Bankhead
 *
 */
public class PreferencePane {

	private static final Logger logger = LoggerFactory.getLogger(PreferencePane.class);

	private PropertySheet propSheet;
	
	private static LocaleManager localeManager = new LocaleManager();
	
	private boolean localeChangedSinceRefresh = false;
	
	private BorderPane pane;
	
	private StringProperty localeChangedText = LocaleListener.createProperty("Prefs.localeChanged");
	private BooleanProperty localeChanged = new SimpleBooleanProperty(false);
	
	@SuppressWarnings("javadoc")
	public PreferencePane() {
		initializePane();
	}
	
	private void initializePane() {
		pane = new BorderPane();
		propSheet = createPropertySheet();
		populatePropertySheet();
		
		var label = createLocaleChangedLabel();
		listenForLocaleChanges();
		
		pane.setCenter(propSheet);
		pane.setBottom(label);
	}
	
	private PropertySheet createPropertySheet() {
		var propSheet = new PropertySheet();
		propSheet.setMode(Mode.CATEGORY);
		propSheet.setPropertyEditorFactory(new PropertyEditorFactory());
		return propSheet;
	}
	
	private void populatePropertySheet() {
		addAnnotatedProperties(new AppearancePreferences());
		addAnnotatedProperties(new GeneralPreferences());
		addAnnotatedProperties(new UndoRedoPreferences());
		addAnnotatedProperties(new LocalePreferences());
		addAnnotatedProperties(new InputOutputPreferences());

		addAnnotatedProperties(new ViewerPreferences());
		addAnnotatedProperties(new ExtensionPreferences());
		addAnnotatedProperties(new MeasurementPreferences());
		addAnnotatedProperties(new ScriptingPreferences());
		
		addAnnotatedProperties(new DrawingPreferences());
		addAnnotatedProperties(new ObjectPreferences());
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
	
	private void listenForLocaleChanges() {
		PathPrefs.defaultLocaleProperty().addListener((v, o, n) -> setLocaleChanged());
		PathPrefs.defaultLocaleDisplayProperty().addListener((v, o, n) -> setLocaleChanged());
		PathPrefs.defaultLocaleFormatProperty().addListener((v, o, n) -> setLocaleChanged());		
	}
	
	private void setLocaleChanged() {
		localeChanged.set(true);
		localeChangedSinceRefresh = true;
//		for (var title : propSheet.lookupAll(".titled-pane")) {
//			if (title instanceof TitledPane titledPane) {
//				if (!titledPane.textProperty().isBound()) {
//					System.err.println(title);
//				}
//			}
//		}
	}
	
	
	
	/**
	 * Install properties that are the public fields of an object, configured using annotations.
	 * The properties themselves are accessed using reflection.
	 * <p>
	 * If the provided object has a {@link PrefCategory} annotation, this defines the category 
	 * for all the identified properties.
	 * Each property should then have a {@link Pref} annotation, or an alternative 
	 * such as {@link DoublePref}, {@link ColorPref}, {@link DirectoryPref}, {@link IntegerPref}.
	 * @param object
	 * @since v0.5.0
	 */
	public void addAnnotatedProperties(Object object) {
		var items = parseItems(object);
		propSheet.getItems().addAll(items);		
	}

	
		
	@PrefCategory("Prefs.Appearance")
	static class AppearancePreferences {
		
		@Pref(value = "Prefs.Appearance.theme", type = StyleOption.class, choiceMethod = "getStyles")
		public final ObjectProperty<StyleOption> theme = QuPathStyleManager.selectedStyleProperty();
		
		@Pref(value = "Prefs.Appearance.font", type = QuPathStyleManager.Fonts.class)
		public final ObjectProperty<QuPathStyleManager.Fonts> autoUpdate = QuPathStyleManager.fontProperty();
		
		public final ObservableList<StyleOption> getStyles() {
			return QuPathStyleManager.availableStylesProperty();
		}

	}
	

	@PrefCategory("Prefs.General")
	static class GeneralPreferences {
				
		@BooleanPref("Prefs.General.showStartupMessage")
		public final BooleanProperty startupMessage = PathPrefs.showStartupMessageProperty();

		@Pref(value = "Prefs.General.checkForUpdates", type = AutoUpdateType.class)
		public final ObjectProperty<AutoUpdateType> autoUpdate = PathPrefs.autoUpdateCheckProperty();

		@BooleanPref("Prefs.General.systemMenubar")
		public final BooleanProperty systemMenubar = PathPrefs.useSystemMenubarProperty();
		
		@DoublePref("Prefs.General.tileCache")
		public final DoubleProperty maxMemoryGB = PathPrefs.hasJavaPreferences() ? createMaxMemoryProperty() : null;

		@DoublePref("Prefs.General.tileCache")
		public final DoubleProperty tileCache = PathPrefs.tileCachePercentageProperty();

		@BooleanPref("Prefs.General.showImageNameInTitle")
		public final BooleanProperty showImageNameInTitle = PathPrefs.showImageNameInTitleProperty();

		@BooleanPref("Prefs.General.maskImageNames")
		public final BooleanProperty maskImageNames = PathPrefs.maskImageNamesProperty();
		
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
	static class LocalePreferences {
		
		@LocalePref(value = "Prefs.Locale.default", availableLanguagesOnly = true)
		public final ObjectProperty<Locale> localeDefault = PathPrefs.defaultLocaleProperty();

		@LocalePref(value = "Prefs.Locale.display", availableLanguagesOnly = true)
		public final ObjectProperty<Locale> localeDisplay = PathPrefs.defaultLocaleDisplayProperty();

		@LocalePref("Prefs.Locale.format")
		public final ObjectProperty<Locale> localeFormat = PathPrefs.defaultLocaleFormatProperty();

	}
	
	
		
	@PrefCategory("Prefs.Undo")
	static class UndoRedoPreferences {
		
		@IntegerPref("Prefs.Undo.maxUndoLevels")
		public final IntegerProperty maxUndoLevels = PathPrefs.maxUndoLevelsProperty();
		
		@IntegerPref("Prefs.Undo.maxUndoHierarchySize")
		public final IntegerProperty maxUndoHierarchySize = PathPrefs.maxUndoHierarchySizeProperty();
		
	}
	
	
	@PrefCategory("Prefs.InputOutput")
	static class InputOutputPreferences {
		
		@IntegerPref("Prefs.InputOutput.minPyramidDimension")
		public final IntegerProperty minimumPyramidDimension = PathPrefs.minPyramidDimensionProperty();
		
		@DoublePref("Prefs.InputOutput.tmaExportDownsample")
		public final DoubleProperty tmaExportDownsample = PathPrefs.tmaExportDownsampleProperty();
		
	}
	
	
	@PrefCategory("Prefs.Viewer")
	static class ViewerPreferences {
		
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
	static class ExtensionPreferences {
		
		@DirectoryPref("Prefs.Extensions.userPath")
		public final Property<String> scriptsPath = PathPrefs.userPathProperty();

	}
	
	
	@PrefCategory("Prefs.Measurements")
	static class MeasurementPreferences {
		
		@BooleanPref("Prefs.Measurements.thumbnails")
		public final BooleanProperty showMeasurementTableThumbnails = PathPrefs.showMeasurementTableThumbnailsProperty();

		@BooleanPref("Prefs.Measurements.ids")
		public final BooleanProperty showMeasurementTableObjectIDs = PathPrefs.showMeasurementTableObjectIDsProperty();

	}
	
	
	@PrefCategory("Prefs.Scripting")
	static class ScriptingPreferences {
		
		@DirectoryPref("Prefs.Scripting.scriptsPath")
		public final StringProperty scriptsPath = PathPrefs.scriptsPathProperty();

	}

	
	@PrefCategory("Prefs.Drawing")
	static class DrawingPreferences {
		
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
	static class ObjectPreferences {
		
		@IntegerPref("Prefs.Objects.clipboard")
		public final IntegerProperty maxClipboardObjects = PathPrefs.maxObjectsToClipboardProperty();

		@DoublePref("Prefs.Objects.annotationLineThickness")
		public final DoubleProperty annotationStrokeThickness = PathPrefs.annotationStrokeThicknessProperty();

		@DoublePref("Prefs.Objects.detectionLineThickness")
		public final DoubleProperty detectonStrokeThickness = PathPrefs.detectionStrokeThicknessProperty();

		@BooleanPref("Prefs.Objects.useSelectedColor")
		public final BooleanProperty useSelectedColor = PathPrefs.useSelectedColorProperty();

		@ColorPref("Prefs.Objects.selectedColor")
		public final IntegerProperty selectedColor = PathPrefs.colorSelectedObjectProperty();

		@ColorPref("Prefs.Objects.defaultColor")
		public final IntegerProperty defaultColor = PathPrefs.colorDefaultObjectsProperty();

		@ColorPref("Prefs.Objects.tmaCoreColor")
		public final IntegerProperty tmaColor = PathPrefs.colorTMAProperty();

		@ColorPref("Prefs.Objects.tmaCoreMissingColor")
		public final IntegerProperty tmaMissingColor = PathPrefs.colorTMAMissingProperty();

	}
	

	/**
	 * Add a new preference based on a specified Property.
	 * 
	 * @param prop
	 * @param cls
	 * @param name
	 * @param category
	 * @param description
	 */
	public <T> void addPropertyPreference(final Property<T> prop, final Class<? extends T> cls, final String name, final String category, final String description) {
		PropertySheet.Item item = new DefaultPropertyItem<>(prop, cls)
				.name(name)
				.category(category)
				.description(description);
		propSheet.getItems().add(item);
	}
	
	
	/**
	 * Add a new color preference based on a specified IntegerProperty (storing a packed RGBA value).
	 * 
	 * @param prop
	 * @param name
	 * @param category
	 * @param description
	 */
	public void addColorPropertyPreference(final IntegerProperty prop, final String name, final String category, final String description) {
		PropertySheet.Item item = new ColorPropertyItem(prop)
				.name(name)
				.category(category)
				.description(description);
		propSheet.getItems().add(item);
	}
	
	
	/**
	 * Add a new directory preference based on a specified StrongProperty.
	 * 
	 * @param prop
	 * @param name
	 * @param category
	 * @param description
	 */
	@Deprecated
	public void addDirectoryPropertyPreference(final Property<String> prop, final String name, final String category, final String description) {
		PropertySheet.Item item = new DirectoryPropertyItem(prop)
				.name(name)
				.category(category)
				.description(description);
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
	 */
	@Deprecated
	public <T> void addChoicePropertyPreference(final Property<T> prop, final ObservableList<T> choices, final Class<? extends T> cls, 
			final String name, final String category, final String description, boolean makeSearchable) {
		PropertySheet.Item item = new ChoicePropertyItem<>(prop, choices, cls, makeSearchable)
				.name(name)
				.category(category)
				.description(description);
		propSheet.getItems().add(item);
	}
	
	
	/**
	 * Request that all the property editors are regenerated.
	 * This is useful if the Locale has changed, and so the text may need to be updated.
	 */
	public void refreshAllEditors() {
		// Attempt to force a property sheet refresh if the locale may have changed
		if (localeChangedSinceRefresh) {
//			var comp = propSheet.getCategoryComparator();
//			propSheet.setCategoryComparator(String::compareTo);
//			propSheet.setCategoryComparator(comp);
//			propSheet.setModeSwitcherVisible(localeChangedSinceRefresh);
			
			// Alternative code to rebuild the editors
			logger.info("Refreshing preferences because of locale change");
			var items = new ArrayList<>(propSheet.getItems());
			propSheet.getItems().clear();
			propSheet.getItems().addAll(items);
			localeChangedSinceRefresh = false;
		}
		localeManager.refreshAvailableLanguages();
	}

	
	/**
	 * Create a default {@link Item} for a generic property.
	 * @param <T> type of the property
	 * @param property the property
	 * @param cls the property type
	 * @return a new {@link PropertyItem}
	 */
	public static <T> PropertyItem createPropertySheetItem(Property<T> property, Class<? extends T> cls) {
		return new DefaultPropertyItem<>(property, cls);
	}
	
	
	/**
	 * Base implementation of {@link Item}.
	 */
	public abstract static class PropertyItem implements PropertySheet.Item {

		private StringProperty name = new SimpleStringProperty();
		private StringProperty category = new SimpleStringProperty();
		private StringProperty description = new SimpleStringProperty();

		/**
		 * Support fluent interface to define a category.
		 * @param category
		 * @return
		 */
		public PropertyItem category(final String category) {
			this.category.set(category);
			return this;
		}

		/**
		 * Support fluent interface to set the description.
		 * @param description
		 * @return
		 */
		public PropertyItem description(final String description) {
			this.description.set(description);
			return this;
		}

		/**
		 * Support fluent interface to set the name.
		 * @param name
		 * @return
		 */
		public PropertyItem name(String name) {
			this.name.set(name);
			return this;
		}
		
		public PropertyItem key(String bundle, String key) {
			if (bundle.isBlank())
				bundle = null;
			LocaleListener.registerProperty(name, bundle, key);
			if (QuPathResources.hasString(bundle, key + ".description"))
				LocaleListener.registerProperty(description, bundle, key + ".description");			
			return this;
		}

		public PropertyItem categoryKey(final String bundle, final String key) {
			LocaleListener.registerProperty(category, bundle, key);			
			return this;
		}


		@Override
		public String getCategory() {
			return category.get();
		}

		@Override
		public String getName() {
			return name.get();
		}

		@Override
		public String getDescription() {
			return description.get();
		}

	}


	private static class DefaultPropertyItem<T> extends PropertyItem {

		private Property<T> prop;
		private Class<? extends T> cls;

		DefaultPropertyItem(final Property<T> prop, final Class<? extends T> cls) {
			this.prop = prop;
			this.cls = cls;
		}

		@Override
		public Class<?> getType() {
			return cls;
		}

		@Override
		public Object getValue() {
			return prop.getValue();
		}

		@SuppressWarnings("unchecked")
		@Override
		public void setValue(Object value) {
			prop.setValue((T)value);
		}

		@Override
		public Optional<ObservableValue<?>> getObservableValue() {
			return Optional.of(prop);
		}

	}


	/**
	 * Create a property item that handles directories based on String paths.
	 */
	private static class DirectoryPropertyItem extends PropertyItem {

		private Property<String> prop;
		private ObservableValue<File> fileValue;

		DirectoryPropertyItem(final Property<String> prop) {
			this.prop = prop;
			fileValue = Bindings.createObjectBinding(() -> prop.getValue() == null || prop.getValue().isEmpty() ? null : new File(prop.getValue()), prop);
		}

		@Override
		public Class<?> getType() {
			return File.class;
		}

		@Override
		public Object getValue() {
			return fileValue.getValue();
		}

		@Override
		public void setValue(Object value) {
			if (value instanceof String) {
				prop.setValue((String)value);
			} else if (value instanceof File)
				prop.setValue(((File)value).getAbsolutePath());
			else if (value == null)
				prop.setValue(null);
			else
				logger.error("Cannot set property {} with value {}", prop, value);
		}

		@Override
		public Optional<ObservableValue<?>> getObservableValue() {
			return Optional.of(fileValue);
		}

	}


	private static class ColorPropertyItem extends PropertyItem {

		private IntegerProperty prop;
		private ObservableValue<Color> value;

		ColorPropertyItem(final IntegerProperty prop) {
			this.prop = prop;
			this.value = Bindings.createObjectBinding(() -> ColorToolsFX.getCachedColor(prop.getValue()), prop);
		}

		@Override
		public Class<?> getType() {
			return Color.class;
		}

		@Override
		public Object getValue() {
			return value.getValue();
		}

		@Override
		public void setValue(Object value) {
			if (value instanceof Color)
				value = ColorToolsFX.getARGB((Color)value);
			if (value instanceof Integer)
				prop.setValue((Integer)value);
		}

		@Override
		public Optional<ObservableValue<?>> getObservableValue() {
			return Optional.of(value);
		}

	}
	
	
	private static class ChoicePropertyItem<T> extends DefaultPropertyItem<T> {

		private final ObservableList<T> choices;
		private final boolean makeSearchable;
		
		private ChoicePropertyItem(final Property<T> prop, final ObservableList<T> choices, final Class<? extends T> cls) {
			this(prop, choices, cls, false);
		}

		private ChoicePropertyItem(final Property<T> prop, final ObservableList<T> choices, final Class<? extends T> cls, boolean makeSearchable) {
			super(prop, cls);
			this.choices = choices;
			this.makeSearchable = makeSearchable;
		}
		
		public ObservableList<T> getChoices() {
			return choices;
		}
		
		public boolean makeSearchable() {
			return makeSearchable;
		}

	}



	/**
	 * Editor for selecting directory paths.
	 * 
	 * Appears as a text field that can be double-clicked to launch a directory chooser.
	 */
	private static class DirectoryEditor extends AbstractPropertyEditor<File, TextField> {

		private ObservableValue<File> value;

		private DirectoryEditor(Item property, TextField control) {
			super(property, control, true);
			control.setOnMouseClicked(e -> {
				if (e.getClickCount() > 1) {
					e.consume();
					File dirNew = Dialogs.getChooser(control.getScene().getWindow()).promptForDirectory(getValue());
					if (dirNew != null)
						setValue(dirNew);
				}
			});
			if (property.getDescription() != null) {
				var description = property.getDescription();
				var tooltip = new Tooltip(description);
				tooltip.setShowDuration(Duration.millis(10_000));
				control.setTooltip(tooltip);
			}
			
			// Bind to the text property
			if (property instanceof DirectoryPropertyItem) {
				control.textProperty().bindBidirectional(((DirectoryPropertyItem)property).prop);
			}
			value = Bindings.createObjectBinding(() -> {
				String text = control.getText();
				if (text == null || text.trim().isEmpty() || !new File(text).isDirectory())
					return null;
				else
					return new File(text);
				}, control.textProperty());
		}

		@Override
		public void setValue(File value) {
			getEditor().setText(value == null ? null : value.getAbsolutePath());
		}

		@Override
		protected ObservableValue<File> getObservableValue() {
			return value;
		}

	}
	
	/**
	 * Manage available locales, with consistent display and string conversion.
	 * This is needed to support presenting locales in a searchable combo box.
	 * <p>
	 * The price of this is that the language/locale names are always shown in English.
	 */
	private static class LocaleManager {
		
		private Map<String, Locale> localeMap = new TreeMap<>();
		private StringConverter<Locale> converter;
		
		private Predicate<Locale> availableLanguagePredicate = QuPathResources::hasDefaultBundleForLocale;
		private ObjectProperty<Predicate<Locale>> availableLanguagePredicateProperty = new SimpleObjectProperty<>(availableLanguagePredicate);
		
		private LocaleManager() {
			initializeLocaleMap();
			converter = new LocaleConverter();
		}
		
		private void initializeLocaleMap() {
			for (var locale : Locale.getAvailableLocales()) {
				if (!localeFilter(locale))
					continue;
				var name = locale.getDisplayName(Locale.US);
				localeMap.putIfAbsent(name, locale);
			}
		}
		
		private static boolean localeFilter(Locale locale) {
			if (locale == Locale.US)
				return true;
			return !locale.getLanguage().isBlank() && locale.getCountry().isEmpty() && locale != Locale.ENGLISH;
		}
		
		private static String getDisplayName(Locale locale) {
			// We use the English US display name, because we're guaranteed that Java supports it 
			// - and also it avoids needing to worry about non-unique names being generated 
			// in different locales, which could mess up the searchable combo box & string converter
			return locale.getDisplayName(Locale.US);
		}
		
		public ObservableList<Locale> createLocaleList() {
			return FXCollections.observableArrayList(localeMap.values());
		}
		
		public FilteredList<Locale> createAvailableLanguagesList() {
			
			var filtered = createLocaleList().filtered(availableLanguagePredicate);
			filtered.predicateProperty().bind(availableLanguagePredicateProperty);
			return filtered;
		}
		
		public void refreshAvailableLanguages() {
			// Awkward... but refresh list
			availableLanguagePredicateProperty.set(null);
			availableLanguagePredicateProperty.set(availableLanguagePredicate);
		}
		
		public StringConverter<Locale> getStringConverter() {
			return converter;
		}
		
		class LocaleConverter extends StringConverter<Locale> {

			@Override
			public String toString(Locale locale) {
				if (locale == null)
					return "";
				return getDisplayName(locale);
			}

			@Override
			public Locale fromString(String string) {
				return localeMap.getOrDefault(string, null);
			}
			
		}
		
	}
	
	
	private abstract static class AbstractChoiceEditor<T, S extends ComboBox<T>> extends AbstractPropertyEditor<T, S> implements ListChangeListener<T> {
		
		private ObservableList<T> choices;
		
		public AbstractChoiceEditor(S combo, Item property, ObservableList<T> choices) {
			super(property, combo);
			if (property.getType().equals(Locale.class)) {
				combo.setConverter((StringConverter<T>)localeManager.getStringConverter());
			}
			this.choices = choices;
			combo.getItems().setAll(choices);
			this.choices.addListener(this);
		}

		@Override
		public void setValue(T value) {
//			System.err.println("SETTING: " + hashCode() + " - " + getProperty().getName());
			// Only set the value if it's available as a choice
			var combo = getEditor();
			if (combo.getItems().contains(value))
				combo.getSelectionModel().select(value);
			else
				combo.getSelectionModel().clearSelection();
		}

		@Override
		protected ObservableValue<T> getObservableValue() {
			return getEditor().getSelectionModel().selectedItemProperty();
		}

		@Override
		public void onChanged(Change<? extends T> c) {
			syncComboItemsToChoices();
		}
		
		private void syncComboItemsToChoices() {
			// We need to clear the existing selection
			var selected = getProperty().getValue();
			var comboItems = getEditor().getItems();
			getEditor().getSelectionModel().clearSelection();
			comboItems.setAll(choices);
			setValue((T)selected);
		}
		
	}
	
	/**
	 * Editor for choosing from a longer list of items, aided by a searchable combo box.
	 * @param <T> 
	 */
	static class SearchableChoiceEditor<T> extends AbstractChoiceEditor<T, SearchableComboBox<T>> {

		public SearchableChoiceEditor(Item property, Collection<? extends T> choices) {
			this(property, FXCollections.observableArrayList(choices));
		}

		public SearchableChoiceEditor(Item property, ObservableList<T> choices) {
			super(new SearchableComboBox<>(), property, choices);
		}
		
	}
	
	
	/**
	 * Editor for choosing from a combo box, which will use an observable list directly if it can 
	 * (which differs from ControlsFX's default behavior).
	 *
	 * @param <T>
	 */
	static class ChoiceEditor<T> extends AbstractChoiceEditor<T, ComboBox<T>> {

		public ChoiceEditor(Item property, Collection<? extends T> choices) {
			this(property, FXCollections.observableArrayList(choices));
		}

		public ChoiceEditor(Item property, ObservableList<T> choices) {
			super(new ComboBox<>(), property, choices);
		}
		
	}
	
	
	// We want to reformat the display of these to avoid using all uppercase
	private static Map<Class<?>, Function<?, String>> reformatTypes = Map.of(
			FontWeight.class, PreferencePane::simpleFormatter,
			LogLevel.class, PreferencePane::simpleFormatter
			);
	
	private static String simpleFormatter(Object obj) {
		var s = Objects.toString(obj);
		s = s.replaceAll("_", " ");
		if (Objects.equals(s, s.toUpperCase()))
			return s.substring(0, 1) + s.substring(1).toLowerCase();
		return s;
	}
	
	/**
	 * Extends {@link DefaultPropertyEditorFactory} to handle setting directories and creating choice editors.
	 */
	public static class PropertyEditorFactory extends DefaultPropertyEditorFactory {
		
		// Set this to true to automatically update labels & tooltips
		// (but not categories, unfortunately, so it can look odd)
		private boolean bindLabelText = false;
		
		// Need to cache editors, since the property sheet is rebuilt often 
		// (but isn't smart enough to detach the editor listeners, so old ones hang around 
		// and respond to 'setValue()' calls)
		private Map<Item, PropertyEditor<?>> cache = new ConcurrentHashMap<>();
		
		@SuppressWarnings("unchecked")
		@Override
		public PropertyEditor<?> call(Item item) {
			PropertyEditor<?> editor = cache.getOrDefault(item, null);
			if (editor != null)
				return editor;
			
			if (item.getType() == File.class) {
				editor = new DirectoryEditor(item, new TextField());
			} else if (item instanceof ChoicePropertyItem) {
				var choiceItem = ((ChoicePropertyItem<?>)item);
				if (choiceItem.makeSearchable()) {
					editor = new SearchableChoiceEditor<>(choiceItem, choiceItem.getChoices());
				} else
					// Use this rather than Editors.createChoiceEditor() because it wraps an existing ObservableList where available
					editor = new ChoiceEditor<>(choiceItem, choiceItem.getChoices());
			} else
				editor = super.call(item);
			
			if (reformatTypes.containsKey(item.getType()) && editor.getEditor() instanceof ComboBox) {
				@SuppressWarnings("rawtypes")
				var combo = (ComboBox)editor.getEditor();
				var formatter = reformatTypes.get(item.getType());
				combo.setCellFactory(obj -> GuiTools.createCustomListCell(formatter));
				combo.setButtonCell(GuiTools.createCustomListCell(formatter));
			}
			
			// Make it easier to reset default locale
			if (Locale.class.equals(item.getType())) {
				editor.getEditor().addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
					if (e.getClickCount() == 2) {
						if (Dialogs.showConfirmDialog(
								QuPathResources.getString("Prefs.localeReset"),
								QuPathResources.getString("Prefs.localeResetMessage"))) {
							item.setValue(Locale.US);
						}
					}
				});
			}
			
			if (bindLabelText && item instanceof PropertyItem) {
				var listener = new ParentChangeListener((PropertyItem)item, editor.getEditor());
				editor.getEditor().parentProperty().addListener(listener);
			}
			
			cache.put(item, editor);
			
			return editor;
		}
		
		/**
		 * Listener to bind the label & tooltip text (since these aren't accessible via the PropertySheet)
		 */
		private static class ParentChangeListener implements ChangeListener<Parent> {

			private PropertyItem item;
			private Node node;
			
			private ParentChangeListener(PropertyItem item, Node node) {
				this.item = item;
				this.node = node;
			}
			
			@Override
			public void changed(ObservableValue<? extends Parent> observable, Parent oldValue, Parent newValue) {
				if (newValue == null)
					return;
				
				for (var labelLookup : newValue.lookupAll(".label")) {
					if (labelLookup instanceof Label label) {
						if (label.getLabelFor() == node) {
							if (!label.textProperty().isBound())
								label.textProperty().bind(item.name);
							var tooltip = label.getTooltip();
							if (tooltip != null && !tooltip.textProperty().isBound())
								tooltip.textProperty().bind(item.description);
							break;
						}
					}
				}
			}
			
		}
		
		
	}
	
	
	private static <T> PropertyItemBuilder<T> buildItem(Property<T> prop, final Class<? extends T> cls) {
		return new PropertyItemBuilder(prop, cls);
	}

	
	private static enum PropertyType { GENERAL, DIRECTORY, COLOR, CHOICE, SEARCHABLE_CHOICE }
	
	
	private static class PropertyItemBuilder<T> {
		
		private Property<T> property;
		private Class<? extends T> cls;
		
		private PropertyType propertyType = PropertyType.GENERAL;
		private ObservableList<T> choices;
		
		private String bundle;
		private String key;
		private String categoryKey;
		
		private PropertyItemBuilder(Property<T> prop, final Class<? extends T> cls) {
			this.property = prop;
			this.cls = cls;
		}
		
		public PropertyItemBuilder<T> key(String key) {
			this.key = key;
			return this;
		}

		public PropertyItemBuilder<T> propertyType(PropertyType type) {
			this.propertyType = type;
			return this;
		}
		
		public PropertyItemBuilder<T> choices(Collection<T> choices) {
			return choices(FXCollections.observableArrayList(choices));
		}
		
		public PropertyItemBuilder<T> choices(ObservableList<T> choices) {
			this.choices = choices;
			this.propertyType = PropertyType.CHOICE;
			return this;
		}
		
		public PropertyItemBuilder<T> bundle(String name) {
			this.bundle = name;
			return this;
		}

		public PropertyItem build() {
			PropertyItem item;
			switch (propertyType) {
			case DIRECTORY:
				item = new DirectoryPropertyItem((Property<String>)property);
				break;
			case COLOR:
				item = new ColorPropertyItem((IntegerProperty)property);
				break;
			case CHOICE:
				item = new ChoicePropertyItem<>(property, choices, cls, false);
				break;
			case SEARCHABLE_CHOICE:
				item = new ChoicePropertyItem<>(property, choices, cls, true);
				break;
			case GENERAL:
			default:
				item = new DefaultPropertyItem<>(property, cls);
				break;
			}
			if (key != null)
				item.key(bundle, key);
			if (categoryKey != null) {
				item.categoryKey(bundle, categoryKey);
			}
			return item;
		}
		
	}
	
	
	
	public static List<PropertyItem> parseItems(Object obj) {
		
		var cls = obj instanceof Class<?> ? (Class<?>)obj : obj.getClass();
		List<PropertyItem> items = new ArrayList<>();
		
		String categoryBundle = null;
		String categoryKey = "Prefs.General";
		if (cls.isAnnotationPresent(PrefCategory.class)) {
			var annotation = cls.getAnnotation(PrefCategory.class);
			categoryBundle = annotation.bundle().isBlank() ? null : annotation.bundle();
			categoryKey = annotation.value();
		}
		
		for (var field : cls.getDeclaredFields()) {
			if (!field.canAccess(obj) || !Property.class.isAssignableFrom(field.getType()))
				continue;
			PropertyItem item = null;
			try {
				// Skip null fields
				if (field.get(obj) == null)
					continue;
				
				if (field.isAnnotationPresent(Pref.class)) {
					item = parseItem((Property)field.get(obj), field.getAnnotation(Pref.class), obj);
				} else if (field.isAnnotationPresent(BooleanPref.class)) {
					item = parseItem((BooleanProperty)field.get(obj), field.getAnnotation(BooleanPref.class));
				} else if (field.isAnnotationPresent(IntegerPref.class)) {
					item = parseItem((IntegerProperty)field.get(obj), field.getAnnotation(IntegerPref.class));
				} else if (field.isAnnotationPresent(DoublePref.class)) {
					item = parseItem((DoubleProperty)field.get(obj), field.getAnnotation(DoublePref.class));
				} else if (field.isAnnotationPresent(StringPref.class)) {
					item = parseItem((Property<String>)field.get(obj), field.getAnnotation(StringPref.class));
				} else if (field.isAnnotationPresent(LocalePref.class)) {
					item = parseItem((Property<Locale>)field.get(obj), field.getAnnotation(LocalePref.class));
				} else if (field.isAnnotationPresent(ColorPref.class)) {
					item = parseItem((Property<Integer>)field.get(obj), field.getAnnotation(ColorPref.class));
				} else if (field.isAnnotationPresent(DirectoryPref.class)) {
					item = parseItem((Property<String>)field.get(obj), field.getAnnotation(DirectoryPref.class));
				}
			} catch (Exception e) {
				logger.error(e.getLocalizedMessage(), e);
			}
			if (item != null) {
				item.categoryKey(categoryBundle, categoryKey);
				items.add(item);
			}
		}
		
		return items;
		
	}
	
	private static PropertyItem parseItem(Property property, Pref annotation, Object parent) {

		var builder = buildItem(property, annotation.type())
				.key(annotation.value())
				.bundle(annotation.bundle());

		var choiceMethod = annotation.choiceMethod();
		if (!choiceMethod.isBlank() && parent != null) {
			var cls = parent.getClass();
			try {
				var method = cls.getDeclaredMethod(choiceMethod);
				var result = method.invoke(parent);
				if (result instanceof ObservableList) {
					builder.choices((ObservableList)result);
				} else if (result instanceof Collection) {
					builder.choices((Collection)result);
				}
			} catch (Exception e) {
				logger.error("Unable to parse choices from " + annotation + ": " + e.getLocalizedMessage(), e);
			}
		}
		
		return builder.build();
	}
	
	private static PropertyItem parseItem(BooleanProperty property, BooleanPref annotation) {
		return buildItem(property, Boolean.class)
				.key(annotation.value())
				.bundle(annotation.bundle())
				.build();
	}
	
	private static PropertyItem parseItem(IntegerProperty property, IntegerPref annotation) {
		return buildItem(property, Integer.class)
				.key(annotation.value())
				.bundle(annotation.bundle())
				.build();
	}
	
	private static PropertyItem parseItem(DoubleProperty property, DoublePref annotation) {
		return buildItem(property, Double.class)
				.key(annotation.value())
				.bundle(annotation.bundle())
				.build();
	}
	
	private static PropertyItem parseItem(Property<String> property, StringPref annotation) {
		return buildItem(property, String.class)
				.key(annotation.value())
				.bundle(annotation.bundle())
				.build();
	}
	
	private static PropertyItem parseItem(Property<Locale> property, LocalePref annotation) {
		return buildItem(property, Locale.class)
				.key(annotation.value())
				.bundle(annotation.bundle())
				.choices(annotation.availableLanguagesOnly() ? localeManager.createAvailableLanguagesList() : localeManager.createLocaleList())
				.propertyType(PropertyType.SEARCHABLE_CHOICE)
				.build();
	}
	
	private static PropertyItem parseItem(Property<Integer> property, ColorPref annotation) {
		return buildItem(property, Integer.class)
				.key(annotation.value())
				.bundle(annotation.bundle())
				.propertyType(PropertyType.COLOR)
				.build();
	}
	
	private static PropertyItem parseItem(Property<String> property, DirectoryPref annotation) {
		return buildItem(property, String.class)
				.key(annotation.value())
				.bundle(annotation.bundle())
				.propertyType(PropertyType.DIRECTORY)
				.build();
	}
		
	
}
