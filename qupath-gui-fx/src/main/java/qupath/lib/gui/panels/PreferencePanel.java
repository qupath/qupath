/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.panels;

import java.io.File;
import java.util.Optional;
import org.controlsfx.control.PropertySheet;
import org.controlsfx.control.PropertySheet.Item;
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
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.ColorToolsFX;
import qupath.lib.gui.helpers.CommandFinderTools.CommandBarDisplay;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.QuPathStyleManager;

/**
 * Basic preference panel, giving a means to modify some of the properties within PathPrefs.
 * 
 * @author Pete Bankhead
 *
 */
public class PreferencePanel {

	final private static Logger logger = LoggerFactory.getLogger(PreferencePanel.class);

//	private QuPathGUI qupath;
	private PropertySheet propSheet = new PropertySheet();

	public PreferencePanel(final QuPathGUI qupath) {
//		this.qupath = qupath;
		setupPanel();
	}


	private void setupPanel() {
		//		propSheet.setMode(Mode.CATEGORY);
		propSheet.setPropertyEditorFactory(new PropertyEditorFactory());

		String category;

		
		/*
		 * Appearance
		 */
		category = "Appearance";
		addChoicePropertyPreference(QuPathStyleManager.selectedStyleProperty(),
				QuPathStyleManager.stylesProperty(),
				QuPathStyleManager.StylesheetOption.class,
				"Theme",
				category,
				"Theme for the QuPath user interface");
		
		/*
		 * General
		 */
		category = "General";
		
//		if (PathPrefs.hasJavaPreferences()) {
//			addPropertyPreference(PathPrefs.maxMemoryMBProperty(), Integer.class,
//					"Max memory (MB)",
//					category,
//					"Maxmimum memory (in MB) available for QuPath.\n" +
//								"Note: changing this value only has an effect after restarting QuPath.\n" +
//								"Set to a value <= 0 to reset to default."
//							);
//		}
		
		
		addPropertyPreference(PathPrefs.doAutoUpdateCheckProperty(), Boolean.class,
				"Check for updates on startup",
				category,
				"Automatically check for updated when QuPath is started, and show a message if a new version is available.");
		
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

		addPropertyPreference(PathPrefs.autoEstimateImageTypeProperty(), Boolean.class,
				"Auto-estimate image type on opening",
				category,
				"Automatically estimate & set the image type on first opening (e.g. H&E, H-DAB, fluorescence)." + 
						"\nThis can be handy, but be aware it might not always be correct - and you should always check!" + 
						"\nThe image type influences some available commands, e.g. how stains are separated for display or cell detections.");

		addPropertyPreference(PathPrefs.commandBarDisplayProperty(), CommandBarDisplay.class,
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
		
		addPropertyPreference(PathPrefs.useProjectImageCacheProperty(), Boolean.class,
			"Use project image cache",
			category,
			"Store image tiles for hosted images of each project in a local cache.\nThis avoids requiring lengthy HTTP requests every time an image is (re)analysed or viewed, at the cost of needing more local storage space.");
		
		addPropertyPreference(PathPrefs.tmaExportDownsampleProperty(), Double.class,
			"TMA export downsample factor",
			category,
			"Amount to downsample TMA core images when exporting; higher downsample values give smaller image, choosing 1 exports cores at full-resolution (which may be slow)");


		/*
		 * Viewer
		 */
		category = "Viewer";
		
		addPropertyPreference(PathPrefs.viewerInterpolateBilinearProperty(), Boolean.class,
				"Use bilinear interpolation", category, 
				"Use bilinear interpolation for displaying image in the viewer (default is nearest-neighbor)");
		
		addPropertyPreference(PathPrefs.viewerGammaProperty(), Double.class,
				"Gamma value (display only)", category, 
				"Set the gamma value applied to the image in the viewer for display - recommended to leave at default value of 1");
		
		addPropertyPreference(PathPrefs.scrollSpeedProperty(), Integer.class,
				"Scroll speed %", category, 
				"Adjust the scrolling speed - 100% is 'normal', while lower values lead to slower scrolling");
		
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
		
		addPropertyPreference(PathPrefs.doubleClickToZoomProperty(), Boolean.class,
				"Use double-click to zoom",
				category,
				"Zoom in when double-clicking on image (if not inside an object) with move tool; zoom out if Alt or Ctrl/Cmd is held down");


		addPropertyPreference(PathPrefs.useCalibratedLocationStringProperty(), Boolean.class,
				"Use calibrated location text",
				category,
				"Show pixel locations on the viewer in " + GeneralTools.micrometerSymbol() + " where possible");

		
//		// Add support for 3D mice only if required class if available
//		// (Ideally this wouldn't be hard-coded... should switch to being a proper extension)
//		try {
//			Class<?> cls = Class.forName("qupath.lib.gui.input.AdvancedControllerActionFactory");
//			if (cls != null) {
//				PropertySheet.Item itAdvancedControllers = new PropertyItem<>(PathPrefs.requestAdvancedControllersProperty(), Boolean.class)
//						.name("3D mouse support")
//						.category(category)
//						.description("Try to add support for 3D mice - requires QuPath to be restarted to have an effect");
//				propSheet.getItems().add(itAdvancedControllers);
//			}
//		} catch (ClassNotFoundException e) {
//			logger.debug("No 3D mouse support available.");
//		}
		

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

		addPropertyPreference(PathPrefs.defaultPointRadiusProperty(), Integer.class,
				"Point radius",
				category,
				"Set the default point radius");


		/*
		 * Object colors
		 */
		category = "Objects";

		addPropertyPreference(PathPrefs.strokeThickThicknessProperty(), Float.class,
				"Annotation line thickness",
				category,
				"Thickness (in display pixels) for annotation/TMA core object outlines (default = 2)");

		addPropertyPreference(PathPrefs.strokeThinThicknessProperty(), Float.class,
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

		addColorPropertyPreference(PathPrefs.colorDefaultAnnotationsProperty(),
				"Default annotation color",
				category,
				"Set the default color for annotations");

		addColorPropertyPreference(PathPrefs.colorTMAProperty(),
				"TMA core color",
				category,
				"Set the default color for TMA core objects");

		addColorPropertyPreference(PathPrefs.colorTMAMissingProperty(),
				"TMA missing core color",
				category,
				"Set the default color for missing TMA core objects");

	}


	public Node getNode() {
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
		PropertySheet.Item item = new PropertyItem<>(prop, cls)
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
	
	


	static abstract class AbstractPropertyItem implements PropertySheet.Item {

		private String name;
		private String category;
		private String description;

		public AbstractPropertyItem category(final String category) {
			this.category = category;
			return this;
		}

		public AbstractPropertyItem description(final String description) {
			this.description = description;
			return this;
		}

		public AbstractPropertyItem name(final String name) {
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


	static class PropertyItem<T> extends AbstractPropertyItem {

		private Property<T> prop;
		private Class<? extends T> cls;

		PropertyItem(final Property<T> prop, final Class<? extends T> cls) {
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
	static class DirectoryPropertyItem extends AbstractPropertyItem {

		private Property<String> prop;
		private ObservableValue<File> fileValue;
		
//		private ObjectProperty<File> fileValue;
//
//		DirectoryPropertyItem(final Property<String> prop) {
//			this.prop = prop;
//			fileValue = new SimpleObjectProperty<>();
//			updateFileProperty();
//			prop.addListener((v, o, n) -> updateFileProperty());
//		}
//		
//		private void updateFileProperty() {
//			fileValue.set(prop.getValue() == null ? null : new File(prop.getValue()));
//		}

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


	static class ColorPropertyItem extends AbstractPropertyItem {

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
				value = ColorToolsFX.getRGBA((Color)value);
			if (value instanceof Integer)
				prop.setValue((Integer)value);
		}

		@Override
		public Optional<ObservableValue<? extends Object>> getObservableValue() {
			return Optional.of(value);
		}

	}
	
	
	static class ChoicePropertyItem<T> extends PropertyItem<T> {

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
					File dirNew = QuPathGUI.getDialogHelperForParent(control).promptForDirectory(getValue());
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
	
	
//	static class DoubleEditor extends AbstractPropertyEditor<Number, TextField> {
//		
//		private DoubleProperty value = new SimpleDoubleProperty();
//
//		public DoubleEditor(Item property, TextField control, boolean isEditable) {
//			super(property, control, isEditable);
//			control.textProperty().addListener((v, o, n) -> {
//				try {
//					value.set(Double.parseDouble(n));
//				} catch (Exception e) {}
//			});
//			if (property.getDescription() != null)
//				control.setTooltip(new Tooltip(property.getDescription()));
//		}
//
//		@Override
//		public void setValue(Number value) {
//			getEditor().setText(value.toString());
//		}
//
//		@Override
//		protected ObservableValue<Number> getObservableValue() {
//			return value;
//		}
//		
//	}


	static class PropertyEditorFactory extends DefaultPropertyEditorFactory {

		@Override
		public PropertyEditor<?> call(Item item) {
			if (item.getType() == File.class) {
				return new DirectoryEditor(item, new TextField());
			}
			if (item instanceof ChoicePropertyItem) {
				return Editors.createChoiceEditor(item, ((ChoicePropertyItem<?>)item).getChoices());
			}
//			// This doesn't work...
//			if (item.getType() == Double.class) {
//				return new DoubleEditor(item, new TextField(), true);
//			}
			return super.call(item);
		}
	}
	
}
