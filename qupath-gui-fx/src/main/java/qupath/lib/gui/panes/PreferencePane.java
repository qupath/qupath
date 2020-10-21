/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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
import java.util.Optional;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;
import org.controlsfx.control.PropertySheet.Mode;
import org.controlsfx.property.editor.AbstractPropertyEditor;
import org.controlsfx.property.editor.DefaultPropertyEditorFactory;
import org.controlsfx.property.editor.Editors;
import org.controlsfx.property.editor.PropertyEditor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.binding.Bindings;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.Property;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.dialogs.Dialogs;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.FontSize;
import qupath.lib.gui.prefs.PathPrefs.ImageTypeSetting;
import qupath.lib.gui.tools.ColorToolsFX;
import qupath.lib.gui.tools.CommandFinderTools;
import qupath.lib.gui.tools.CommandFinderTools.CommandBarDisplay;
import qupath.lib.gui.prefs.QuPathStyleManager;

/**
 * Basic preference panel, giving a means to modify some of the properties within PathPrefs.
 * 
 * @author Pete Bankhead
 *
 */
public class PreferencePane {

	final private static Logger logger = LoggerFactory.getLogger(PreferencePane.class);

	private PropertySheet propSheet = new PropertySheet();

	@SuppressWarnings("javadoc")
	public PreferencePane() {
		setupPanel();
	}


	private void setupPanel() {
		//		propSheet.setMode(Mode.CATEGORY);
		propSheet.setMode(Mode.CATEGORY);
		propSheet.setPropertyEditorFactory(new PropertyEditorFactory());

		String category;

		
		/*
		 * Appearance
		 */
		category = "Appearance";
		addChoicePropertyPreference(QuPathStyleManager.selectedStyleProperty(),
				QuPathStyleManager.availableStylesProperty(),
				QuPathStyleManager.StyleOption.class,
				"Theme",
				category,
				"Theme for the QuPath user interface");
		
		addChoicePropertyPreference(QuPathStyleManager.fontProperty(),
				QuPathStyleManager.availableFontsProperty(),
				QuPathStyleManager.Fonts.class,
				"Font",
				category,
				"Main font for the QuPath user interface");
		
		/*
		 * General
		 */
		category = "General";
		
		addPropertyPreference(PathPrefs.doAutoUpdateCheckProperty(), Boolean.class,
				"Check for updates on startup",
				category,
				"Automatically check for updated when QuPath is started, and show a message if a new version is available.");

		addPropertyPreference(PathPrefs.runStartupScriptProperty(), Boolean.class,
				"Run startup script (if available)",
				category,
				"If a script is added to the user directory called 'startup.groovy', try to execute this script whenever QuPath is launched.");

		addPropertyPreference(PathPrefs.useSystemMenubarProperty(), Boolean.class,
				"Use system menubar",
				category,
				"Use the system menubar, rather than custom application menubars (default is true).");
				
		addPropertyPreference(PathPrefs.tileCachePercentageProperty(),
				Double.class,
				"Percentage memory for tile caching",
				category,
				"Percentage of maximum memory to use for caching image tiles (must be >10% and <90%; suggested value is 25%)." +
				"\nA high value can improve performance (especially for multichannel images), but increases risk of out-of-memory errors." +
				"\nChanges take effect when QuPath is restarted.");
		
		addPropertyPreference(PathPrefs.showImageNameInTitleProperty(), Boolean.class,
				"Show image name in window title",
				category,
				"Show the name of the current image in the main QuPath title bar (turn this off if the name shouldn't be seen).");
		
		addPropertyPreference(PathPrefs.maskImageNamesProperty(), Boolean.class,
				"Mask image names in projects",
				category,
				"Mask the image names when using projects, to help reduce the potential for user bias during analysis.");
		
		
		addPropertyPreference(PathPrefs.doCreateLogFilesProperty(), Boolean.class,
				"Create log files",
				category,
				"Create log files when using QuPath inside the QuPath user directory (useful for debugging & reporting errors)");
		
		addPropertyPreference(PathPrefs.numCommandThreadsProperty(), Integer.class,
				"Number of processors for parallel commands",
				category,
				"Set limit on number of processors to use for parallelization."
						+ "\nThis should be > 0 and <= the available processors on the computer."
						+ "\nIf outside this range, it will default to the available processors (here, " + Runtime.getRuntime().availableProcessors() + ")"
						+ "\nIt's usually fine to use the default, but it may help to decrease it if you encounter out-of-memory errors.");

		addPropertyPreference(PathPrefs.imageTypeSettingProperty(), ImageTypeSetting.class,
				"Set image type",
				category,
				"Automatically estimate & set the image type on first opening (e.g. H&E, H-DAB, fluorescence), prompt or leave unset." +
						"\nEstimating can be handy, but be aware it might not always be correct - and you should always check!" + 
						"\nThe image type influences some available commands, e.g. how stains are separated for display or cell detections.");

		addPropertyPreference(CommandFinderTools.commandBarDisplayProperty(), CommandBarDisplay.class,
				"Command bar display mode",
				category,
				"Mode used to display command finder text field on top of the viewer");

		addPropertyPreference(PathPrefs.showExperimentalOptionsProperty(), Boolean.class,
				"Show experimental menu items",
				category,
				"Include experimental commands within the menus - these are likely to be especially buggy and incomplete, but may occasionally be useful");

		addPropertyPreference(PathPrefs.showTMAOptionsProperty(), Boolean.class,
				"Show TMA menu",
				category,
				"Include menu items related to Tissue Microarrays");
		
		addPropertyPreference(PathPrefs.showLegacyOptionsProperty(), Boolean.class,
				"Show legacy menu items",
				category,
				"Include menu items related to legacy commands (no longer intended for use)");


		/*
		 * Export
		 */
		category = "Input/Output";
		
		addPropertyPreference(PathPrefs.minPyramidDimensionProperty(), Integer.class,
				"Minimize image dimension for pyramidalizing",
				category,
				"Allow an image pyramid to be calculated for a single-resolution image if either the width or height is greater than this size");

		
		addPropertyPreference(PathPrefs.tmaExportDownsampleProperty(), Double.class,
			"TMA export downsample factor",
			category,
			"Amount to downsample TMA core images when exporting; higher downsample values give smaller image, choosing 1 exports cores at full-resolution (which may be slow)");


		/*
		 * Viewer
		 */
		category = "Viewer";
		
		addColorPropertyPreference(PathPrefs.viewerBackgroundColorProperty(),
				"Viewer background color",
				category,
				"Set the color to show behind any image in the viewer (i.e. beyond the image bounds)");
		
		addPropertyPreference(PathPrefs.alwaysPaintSelectedObjectsProperty(), Boolean.class,
				"Always paint selected objects", category, 
				"Always paint selected objects, even if the overlay opacity is set to 0");
		
		addPropertyPreference(PathPrefs.keepDisplaySettingsProperty(), Boolean.class,
				"Keep display settings where possible", category, 
				"Keep display settings (channel colors, brightness/contrast) when opening similar images");
		
		addPropertyPreference(PathPrefs.viewerInterpolateBilinearProperty(), Boolean.class,
				"Use bilinear interpolation", category, 
				"Use bilinear interpolation for displaying image in the viewer (default is nearest-neighbor)");
		
		addPropertyPreference(PathPrefs.autoBrightnessContrastSaturationPercentProperty(), Double.class,
				"Auto Brightness/Contrast saturation %", category, 
				"Set % bright and % dark pixels that should be saturated when applying 'Auto' brightness/contrast settings");
		
		addPropertyPreference(PathPrefs.viewerGammaProperty(), Double.class,
				"Gamma value (display only)", category, 
				"Set the gamma value applied to the image in the viewer for display - recommended to leave at default value of 1");
		
		addPropertyPreference(PathPrefs.invertZSliderProperty(), Boolean.class,
				"Invert z-position slider",
				category,
				"Invert the vertical slider used to scroll between z-slices (useful for anyone for whom the default orientation seems counterintuitive)");
		
		addPropertyPreference(PathPrefs.scrollSpeedProperty(), Integer.class,
				"Scroll speed %", category, 
				"Adjust the scrolling speed - 100% is 'normal', while lower values lead to slower scrolling");
		
		addPropertyPreference(PathPrefs.navigationSpeedProperty(), Integer.class,
				"Navigation speed %", category, 
				"Adjust the navigation speed - 100% is 'normal', while lower values lead to slower navigation");
		
		addPropertyPreference(PathPrefs.navigationAccelerationProperty(), Boolean.class,
				"Navigation acceleration effects", category, 
				"Apply acceleration/deceleration effects when holding and releasing a navigation key");
		
		addPropertyPreference(PathPrefs.skipMissingCoresProperty(), Boolean.class,
				"Skip missing TMA cores", category, 
				"Jumps over missing TMA cores when navigating TMA grids using the arrow keys.");
		
		addPropertyPreference(PathPrefs.useScrollGesturesProperty(), Boolean.class,
				"Use scroll touch gestures",
				category,
				"Use scroll gestures with touchscreens or touchpads to navigate the slide");

		addPropertyPreference(PathPrefs.invertScrollingProperty(), Boolean.class,
				"Invert scrolling",
				category,
				"Invert the effect of scrolling - may counteract system settings that don't play nicely with QuPath");

		addPropertyPreference(PathPrefs.useZoomGesturesProperty(), Boolean.class,
				"Use zoom touch gestures",
				category,
				"Use 'pinch-to-zoom' gestures with touchscreens or touchpads");

		addPropertyPreference(PathPrefs.useRotateGesturesProperty(), Boolean.class,
				"Use rotate touch gestures",
				category,
				"Use rotation gestures with touchscreens or touchpads to navigate the slide");
		
		addPropertyPreference(PathPrefs.enableFreehandToolsProperty(), Boolean.class,
				"Enable freehand mode for polygon & polyline tools",
				category,
				"When starting to draw a polygon/polyline by clicking & dragging, optionally end ROI by releasing mouse button (rather than double-clicking)");
		
		addPropertyPreference(PathPrefs.doubleClickToZoomProperty(), Boolean.class,
				"Use double-click to zoom",
				category,
				"Zoom in when double-clicking on image (if not inside an object) with move tool; zoom out if Alt or Ctrl/Cmd is held down");

		addPropertyPreference(PathPrefs.viewerFontSizeProperty(),
				FontSize.class,
				"Viewer font size",
				category,
				"Adjust font size for scalebar and location text");

		addPropertyPreference(PathPrefs.useCalibratedLocationStringProperty(), Boolean.class,
				"Use calibrated location text",
				category,
				"Show pixel locations on the viewer in " + GeneralTools.micrometerSymbol() + " where possible");
		
		
		addPropertyPreference(PathPrefs.gridSpacingXProperty(), Double.class,
				"Grid spacing X",
				category,
				"Horizontal grid spacing when displaying a grid on the viewer");

		addPropertyPreference(PathPrefs.gridSpacingYProperty(), Double.class,
				"Grid spacing Y",
				category,
				"Vertical grid spacing when displaying a grid on the viewer");

		addPropertyPreference(PathPrefs.gridScaleMicronsProperty(), Boolean.class,
				"Grid spacing in " + GeneralTools.micrometerSymbol(),
				category,
				"Use " + GeneralTools.micrometerSymbol() + " units where possible when defining grid spacing");

		

		/*
		 * Extensions
		 */
		category = "Extensions";
		addDirectoryPropertyPreference(PathPrefs.userPathProperty(),
				"QuPath user directory",
				category,
				"Set the QuPath user directory - after setting you should restart QuPath");


		/*
		 * Automation
		 */
		category = "Automation";
		addDirectoryPropertyPreference(PathPrefs.scriptsPathProperty(),
				"Script directory",
				category,
				"Set the script directory");

		/*
		 * Drawing tools
		 */
		category = "Drawing tools";
		addPropertyPreference(PathPrefs.returnToMoveModeProperty(), Boolean.class,
				"Return to Move Tool automatically",
				category,
				"Return selected tool to 'Move' automatically after drawing a ROI (applies to all drawing tools except brush & wand)");
		
		addPropertyPreference(PathPrefs.usePixelSnappingProperty(), Boolean.class,
				"Use pixel snapping",
				category,
				"Automatically snap pixels to integer coordinates when using drawing tools (some tools, e.g. line, points may override this)");
		
		addPropertyPreference(PathPrefs.clipROIsForHierarchyProperty(), Boolean.class,
				"Clip ROIs to hierarchy",
				category,
				"Automatically clip ROIs so that they don't extend beyond a parent annotation, or encroach on a child annotation - this helps keep the hierarchy easier to interpret, without overlaps. " + 
				"The setting can be overridden by pressing the 'shift' key");

		addPropertyPreference(PathPrefs.brushDiameterProperty(), Integer.class,
				"Brush diameter",
				category,
				"Set the default brush diameter");
		
		addPropertyPreference(PathPrefs.useTileBrushProperty(), Boolean.class,
				"Use tile brush",
				category,
				"Adapt brush tool to select tiles, where available");

		addPropertyPreference(PathPrefs.brushScaleByMagProperty(), Boolean.class,
				"Scale brush by magnification",
				category,
				"Adapt brush size by magnification, so higher magnification gives a finer brush");
		
		addPropertyPreference(PathPrefs.multipointToolProperty(), Boolean.class,
				"Use multipoint tool",
				category,
				"With the Counting tool, add points to an existing object if possible");

		addPropertyPreference(PathPrefs.pointRadiusProperty(), Integer.class,
				"Point radius",
				category,
				"Set the default point radius");


		/*
		 * Object colors
		 */
		category = "Objects";

		addPropertyPreference(PathPrefs.annotationStrokeThicknessProperty(), Float.class,
				"Annotation line thickness",
				category,
				"Thickness (in display pixels) for annotation/TMA core object outlines (default = 2)");

		addPropertyPreference(PathPrefs.detectionStrokeThicknessProperty(), Float.class,
				"Detection line thickness",
				category,
				"Thickness (in image pixels) for detection object outlines (default = 2)");

		addPropertyPreference(PathPrefs.useSelectedColorProperty(), Boolean.class,
				"Use selected color",
				category,
				"Highlight selected objects by recoloring them; otherwise, a slightly thicker line thickness will be used");

		addColorPropertyPreference(PathPrefs.colorSelectedObjectProperty(),
				"Selected object color",
				category,
				"Set the color used to highly the selected object");

		addColorPropertyPreference(PathPrefs.colorDefaultObjectsProperty(),
				"Default object color",
				category,
				"Set the default color for objects");

		addColorPropertyPreference(PathPrefs.colorTMAProperty(),
				"TMA core color",
				category,
				"Set the default color for TMA core objects");

		addColorPropertyPreference(PathPrefs.colorTMAMissingProperty(),
				"TMA missing core color",
				category,
				"Set the default color for missing TMA core objects");

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
	public <T> void addChoicePropertyPreference(final Property<T> prop, final ObservableList<T> choices, final Class<? extends T> cls, final String name, final String category, final String description) {
		PropertySheet.Item item = new ChoicePropertyItem<>(prop, choices, cls)
				.name(name)
				.category(category)
				.description(description);
		propSheet.getItems().add(item);
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
	public static abstract class PropertyItem implements PropertySheet.Item {

		private String name;
		private String category;
		private String description;

		/**
		 * Support fluent interface to define a category.
		 * @param category
		 * @return
		 */
		public PropertyItem category(final String category) {
			this.category = category;
			return this;
		}

		/**
		 * Support fluent interface to set the description.
		 * @param description
		 * @return
		 */
		public PropertyItem description(final String description) {
			this.description = description;
			return this;
		}

		/**
		 * Support fluent interface to set the name.
		 * @param name
		 * @return
		 */
		public PropertyItem name(final String name) {
			this.name = name;
			return this;
		}

		@Override
		public String getCategory() {
			return category;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getDescription() {
			return description;
		}

	}


	static class DefaultPropertyItem<T> extends PropertyItem {

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
		public Optional<ObservableValue<? extends Object>> getObservableValue() {
			return Optional.of(prop);
		}

	}


	/**
	 * Create a property item that handles directories based on String paths.
	 */
	static class DirectoryPropertyItem extends PropertyItem {

		private Property<String> prop;
		private ObservableValue<File> fileValue;

		DirectoryPropertyItem(final Property<String> prop) {
			this.prop = prop;
			fileValue = Bindings.createObjectBinding(() -> prop.getValue() == null ? null : new File(prop.getValue()), prop);
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
			if (value instanceof String)
				prop.setValue((String)value);
			else if (value instanceof File)
				prop.setValue(((File)value).getAbsolutePath());
			else if (value == null)
				prop.setValue(null);
			else
				logger.error("Cannot set property {} with value {}", prop, value);
		}

		@Override
		public Optional<ObservableValue<? extends Object>> getObservableValue() {
			return Optional.of(fileValue);
		}

	}


	static class ColorPropertyItem extends PropertyItem {

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
		public Optional<ObservableValue<? extends Object>> getObservableValue() {
			return Optional.of(value);
		}

	}
	
	
	static class ChoicePropertyItem<T> extends DefaultPropertyItem<T> {

		private final ObservableList<T> choices;

		ChoicePropertyItem(final Property<T> prop, final ObservableList<T> choices, final Class<? extends T> cls) {
			super(prop, cls);
			this.choices = choices;
		}
		
		public ObservableList<T> getChoices() {
			return choices;
		}

	}



	/**
	 * Editor for selecting directory paths.
	 * 
	 * Appears as a text field that can be double-clicked to launch a directory chooser.
	 */
	static class DirectoryEditor extends AbstractPropertyEditor<File, TextField> {

		private ObservableValue<File> value;

		public DirectoryEditor(Item property, TextField control) {
			super(property, control, true);
			control.setOnMouseClicked(e -> {
				if (e.getClickCount() > 1) {
					e.consume();
					File dirNew = Dialogs.getChooser(control.getScene().getWindow()).promptForDirectory(getValue());
					if (dirNew != null)
						setValue(dirNew);
				}
			});
			if (property.getDescription() != null)
				control.setTooltip(new Tooltip(property.getDescription()));
			
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
	 * Extends {@link DefaultPropertyEditorFactory} to handle setting directories and creating choice editors.
	 */
	public static class PropertyEditorFactory extends DefaultPropertyEditorFactory {

		@Override
		public PropertyEditor<?> call(Item item) {
			if (item.getType() == File.class) {
				return new DirectoryEditor(item, new TextField());
			}
			if (item instanceof ChoicePropertyItem) {
				return Editors.createChoiceEditor(item, ((ChoicePropertyItem<?>)item).getChoices());
			}
			return super.call(item);
		}
	}
	
}
