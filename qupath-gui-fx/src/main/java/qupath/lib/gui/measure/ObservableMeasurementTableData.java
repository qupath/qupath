/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.measure;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.measure.ROICentroidMeasurementBuilder.CentroidType;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.roi.interfaces.ROI;
import qupath.opencv.ml.pixel.PixelClassificationMeasurementManager;

/**
 * A table data model to supply observable measurements of PathObjects.
 * <p>
 * This includes dynamically-calculated summaries.
 * 
 * @author Pete Bankhead
 *
 */
public class ObservableMeasurementTableData implements PathTableData<PathObject> {
	
	private static final Logger logger = LoggerFactory.getLogger(ObservableMeasurementTableData.class);
	
	/**
	 * The name used for the Object ID column
	 */
	public static final String NAME_OBJECT_ID = "Object ID";

	private static final String KEY_PIXEL_LAYER = "PIXEL_LAYER";

	private ImageData<?> imageData;
	private DerivedMeasurementManager manager;

	private final ObservableList<PathObject> list = FXCollections.observableArrayList();
	private final FilteredList<PathObject> filterList = new FilteredList<>(list);

	private final ObservableList<String> metadataList = FXCollections.observableArrayList();
	private final ObservableList<String> measurementList = FXCollections.observableArrayList();
	private final ObservableList<String> fullList = FXCollections.observableArrayList();
	
	private final Map<String, MeasurementBuilder<?>> builderMap = new LinkedHashMap<>();

	/**
	 * Set the {@link ImageData} and a collection of objects to measure.
	 * @param imageData the {@link ImageData}, required to determine many dynamic measurements
	 * @param pathObjects the objects to measure ('rows' in the table)
	 */
	public synchronized void setImageData(final ImageData<?> imageData, final Collection<? extends PathObject> pathObjects) {
		this.imageData = imageData;
		list.setAll(pathObjects);
		// Cannot force this to run in application thread as this can result in unexpected behavior if called from a different thread
		if (!Platform.isFxApplicationThread())
			logger.debug("Image data is being set by thread {}", Thread.currentThread());
		updateMeasurementList();
	}
	
	
	/**
	 * Set an {@link ImageServer} as a property in the {@link ImageData}.
	 * This is intended for use as a temporary (non-persistent) property, used by {@link ObservableMeasurementTableData} to create live measurements.
	 * <p>
	 * Note that this method is subject to change (in location and behavior).
	 * 
	 * @param imageData
	 * @param layerServer server to return the pixel layer data; if null, the property will be removed
	 */
	public static void setPixelLayer(ImageData<BufferedImage> imageData, ImageServer<BufferedImage> layerServer) {
		if (layerServer == null)
			imageData.removeProperty(KEY_PIXEL_LAYER);
		else
			imageData.setProperty(KEY_PIXEL_LAYER, layerServer);			
	}
	
	/**
	 * Request the pixel layer from an {@link ImageData}.
	 * <p>
	 * Note that this method is subject to change (in location and behavior).
	 * 
	 * @param imageData
	 * @return
	 */
	public static ImageServer<BufferedImage> getPixelLayer(ImageData<?> imageData) {
		var layer = imageData.getProperty(KEY_PIXEL_LAYER);
		if (layer instanceof ImageServer)
			return (ImageServer<BufferedImage>)layer;
		return null;
	}
	
	/**
	 * Update the entire measurement list for the current objects.
	 * @see #setImageData(ImageData, Collection)
	 */
	public synchronized void updateMeasurementList() {
		builderMap.clear();

		// Add the image name
		if (!PathPrefs.maskImageNamesProperty().get())
			builderMap.put("Image", new ImageNameMeasurementBuilder(imageData));

		var wrapper = new PathObjectListWrapper(imageData, list);

		for (var builder : createDynamicMeasurements(wrapper)) {
			builderMap.put(builder.getName(), builder);
		}

		for (var builder : createMetadataMeasurements(wrapper)) {
			String name = builder.getName();;
			if (!builderMap.containsKey(name)) {
				builderMap.put(name, builder);
			}
		}

		// Names for all general/string measurements
		List<String> metadataNames = new ArrayList<>(builderMap.keySet());

		List<String> featureNames = new ArrayList<>();
		for (var builder : createFeatureMeasurements(wrapper)) {
			var name = builder.getName();
			if (!builderMap.containsKey(name)) {
				builderMap.put(name, builder);
				featureNames.add(name);
			}
		}

		// Add all names from measurement list
		featureNames.addAll(wrapper.getFeatureNames());


		// Update all the lists, if necessary
		boolean changes = false;
		if (!metadataNames.equals(metadataList)) {
			changes = metadataList.setAll(metadataNames);
		}
		if (!featureNames.equals(measurementList))
			changes = measurementList.setAll(featureNames);
		if (changes) {
			if (metadataList.isEmpty())
				fullList.setAll(measurementList);
			else {
				fullList.setAll(metadataList);
				fullList.addAll(measurementList);
			}
		}
	}


	private static List<MeasurementBuilder<?>> createDynamicMeasurements(PathObjectListWrapper wrapper) {

		List<MeasurementBuilder<?>> measurements = new ArrayList<>();

		// Check if we have any annotations / TMA cores

		var imageData = wrapper.getImageData();

		// Include object ID if we have anything other than root objects
		if (!wrapper.containsRootOnly())
			measurements.add(new ObjectIdMeasurementBuilder());

		// Include the object type
		measurements.add(new ObjectTypeMeasurementBuilder());

		// Include the object displayed name
		measurements.add(new ObjectNameMeasurementBuilder());

		// Include the classification
		if (!wrapper.containsRootOnly()) {
			measurements.add(new PathClassMeasurementBuilder());
			// Get the name of the containing TMA core if we have anything other than cores
			if (imageData != null && imageData.getHierarchy().getTMAGrid() != null) {
				measurements.add(new TMACoreNameMeasurementBuilder());
			}
			// Get the name of the first parent object
			measurements.add(new ParentNameMeasurementBuilder());
		}

		// Include the TMA missing status, if appropriate
		if (wrapper.containsTMACores()) {
			measurements.add(new MissingTMACoreMeasurementBuilder());
		}

		if (wrapper.containsAnnotationsOrDetections()) {
			measurements.add(new ROINameMeasurementBuilder());
		}

		// Add centroids
		if (!wrapper.containsRootOnly()) {
			measurements.add(new ROICentroidMeasurementBuilder(imageData, CentroidType.X));
			measurements.add(new ROICentroidMeasurementBuilder(imageData, CentroidType.Y));
		}

		// New v0.4.0: include z and time indices
		var serverMetadata = imageData == null ? null : imageData.getServerMetadata();
		if (wrapper.containsMultiZ() || (wrapper.containsROIs() && serverMetadata != null && serverMetadata.getSizeZ() > 1)) {
			measurements.add(new ZSliceMeasurementBuilder());
		}

		if (wrapper.containsMultiT() || (wrapper.containsROIs() && serverMetadata != null && serverMetadata.getSizeT() > 1)) {
			measurements.add(new TimepointMeasurementBuilder());
		}
		return measurements;
	}

	private static synchronized List<? extends MeasurementBuilder<?>> createMetadataMeasurements(PathObjectListWrapper wrapper) {
		return wrapper.getMetadataNames()
				.stream()
				.map(StringMetadataMeasurementBuilder::new)
				.toList();
	}

	private static synchronized List<MeasurementBuilder<?>> createFeatureMeasurements(PathObjectListWrapper wrapper) {

		List<MeasurementBuilder<?>> measurements = new ArrayList<>();
		
		// Add derived measurements if we don't have only detections
		if (wrapper.containsRoot() || wrapper.containsAnnotationsTmaCores()) {

			var imageData = wrapper.getImageData();
			boolean detectionsAnywhere = imageData == null ? wrapper.containsDetections() : !imageData.getHierarchy().getDetectionObjects().isEmpty();
			if (detectionsAnywhere) {
				var builder = new ObjectTypeCountMeasurementBuilder(wrapper.getImageData(), PathDetectionObject.class);
				measurements.add(builder);
			}
			
			// Here, we allow TMA cores to act like annotations
			boolean includeDensity = wrapper.containsAnnotations() || wrapper.containsTMACores();
			var manager = new DerivedMeasurementManager(wrapper.getImageData(), includeDensity);
            measurements.addAll(manager.getMeasurementBuilders());
			
		}
		
		// If we have an annotation, add shape features
		if (wrapper.containsAnnotations()) {
			// Find all non-null annotation measurements
			var annotationRois = wrapper.getPathObjects().stream()
					.filter(PathObject::isAnnotation)
					.map(PathObject::getROI)
					.filter(Objects::nonNull)
					.toList();
			// Add point count, if we have any points
			if (annotationRois.stream().anyMatch(ROI::isPoint)) {
				measurements.add(new NumPointsMeasurementBuilder());
			}
			// Add area & perimeter measurements, if we have any areas
			if (annotationRois.stream().anyMatch(ROI::isArea)) {
				measurements.add(new AreaMeasurementBuilder(wrapper.getImageData()));
				measurements.add(new PerimeterMeasurementBuilder(wrapper.getImageData()));
			}
			// Add line length measurements, if we have any lines
			if (annotationRois.stream().anyMatch(ROI::isLine)) {
				measurements.add(new LineLengthMeasurementBuilder(wrapper.getImageData()));
			}
		}
		
		if (wrapper.containsAnnotations() || wrapper.containsTMACores() || wrapper.containsRoot()) {
			var pixelClassifier = getPixelLayer(wrapper.getImageData());
			if (pixelClassifier != null) {
				if (pixelClassifier.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.CLASSIFICATION || pixelClassifier.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.PROBABILITY) {
					var pixelManager = new PixelClassificationMeasurementManager(pixelClassifier);
					for (String name : pixelManager.getMeasurementNames()) {
						measurements.add(new PixelClassifierMeasurementBuilder(pixelManager, name));
					}
				}
			}
		}

		return measurements;
	}



	/**
	 * Helper class to wrap a collection of PathObjects that should be measured.
	 * <p>
	 * This provides an unmodifiable list of the objects, and performs a single pass through the objects to
	 * determine key information that is useful for determining which measurements to show.
	 */
	private static class PathObjectListWrapper {

		private final ImageData<?> imageData;
		private final List<PathObject> pathObjects;
		private final Set<String> featureNames;
		private final Set<String> metadataNames;

		private final boolean containsDetections;
		private final boolean containsAnnotations;
		private final boolean containsTMACores;
		private final boolean containsRoot;
		private final boolean containsMultiZ;
		private final boolean containsMultiT;
		private final boolean containsROIs;

		private PathObjectListWrapper(ImageData<?> imageData, Collection<? extends PathObject> pathObjects) {
			this.imageData = imageData;
			this.pathObjects = List.copyOf(pathObjects);

			boolean containsDetections = false;
			boolean containsAnnotations = false;
			boolean containsTMACores = false;
			boolean containsRoot = false;
			boolean containsMultiZ = false;
			boolean containsMultiT = false;
			boolean containsROIs = false;
			var featureNames = new LinkedHashSet<String>();
			var metadataNames = new LinkedHashSet<String>();
			List<PathObject> pathObjectListCopy = new ArrayList<>(pathObjects);
			for (PathObject temp : pathObjectListCopy) {
				// Add feature names from the measurement list
				featureNames.addAll(temp.getMeasurementList().getNames());
				// Add metadata names
				metadataNames.addAll(temp.getMetadata().keySet());
				// Update info for ROIs and types
				if (temp.hasROI())
					containsROIs = true;
				if (temp instanceof PathAnnotationObject) {
					containsAnnotations = true;
				} else if (temp instanceof TMACoreObject) {
					containsTMACores = true;
				} else if (temp instanceof PathDetectionObject) {
					containsDetections = true;
				} else if (temp.isRootObject())
					containsRoot = true;
				var roi = temp.getROI();
				if (roi != null) {
					if (roi.getZ() > 0)
						containsMultiZ = true;
					if (roi.getT() > 0)
						containsMultiT = true;
				}
			}

			this.containsDetections = containsDetections;
			this.containsAnnotations = containsAnnotations;
			this.containsTMACores = containsTMACores;
			this.containsROIs = containsROIs;
			this.containsRoot = containsRoot;
			this.containsMultiT = containsMultiT;
			this.containsMultiZ = containsMultiZ;

			this.featureNames = Collections.unmodifiableSequencedSet(featureNames);
			// Metadata keys starting with _ shouldn't be displayed to the user
			metadataNames.removeIf(m -> m.startsWith("_"));
			this.metadataNames = Collections.unmodifiableSequencedSet(metadataNames);
		}

		ImageData<?> getImageData() {
			return imageData;
		}

		List<PathObject> getPathObjects() {
			return pathObjects;
		}

		Set<String> getFeatureNames() {
			return featureNames;
		}

		Set<String> getMetadataNames() {
			return metadataNames;
		}

		boolean containsDetections() {
			return containsDetections;
		}

		boolean containsAnnotations() {
			return containsAnnotations;
		}

		boolean containsAnnotationsOrDetections() {
			return containsAnnotations || containsDetections;
		}

		boolean containsAnnotationsTmaCores() {
			return containsAnnotations || containsTMACores;
		}

		boolean containsRootOnly() {
			return containsRoot && !containsAnnotations && !containsDetections && !containsTMACores &&
					pathObjects.stream().allMatch(PathObject::isRootObject);
		}

		boolean containsTMACores() {
			return containsTMACores;
		}

		boolean containsROIs() {
			return containsROIs;
		}

		boolean containsRoot() {
			return containsRoot;
		}

		boolean containsMultiT() {
			return containsMultiT;
		}

		boolean containsMultiZ() {
			return containsMultiZ;
		}

	}


	/**
	 * Set a predicate used to filter the rows of the table.
	 * @param predicate
	 */
	public void setPredicate(Predicate<? super PathObject> predicate) {
		filterList.setPredicate(predicate);
	}

	/**
	 * Query whether a named measurement returns a {@link String} value only.
	 * @param name the measurement name
	 * @return true if the measurement returns a String (only), false otherwise
	 */
	public boolean isStringMeasurement(final String name) {
		return builderMap.get(name) instanceof StringMeasurementBuilder;
	}
	
	/**
	 * Query whether a named measurement returns a numeric value only.
	 * @param name the measurement name
	 * @return true if the measurement returns a number, false otherwise
	 */
	public boolean isNumericMeasurement(final String name) {
		return !isStringMeasurement(name);
	}
	
	
	@Override
	public ReadOnlyListWrapper<String> getMeasurementNames() {
		return new ReadOnlyListWrapper<>(measurementList);
	}
	
	@Override
	public double[] getDoubleValues(final String column) {
		double[] values = new double[filterList.size()];
		if (builderMap.containsKey(column)) {
			for (int i = 0; i < filterList.size(); i++)
				values[i] = getNumericValue(filterList.get(i), column);
			return values;
		}
		// Good news! We just need a regular measurement
		for (int i = 0; i < filterList.size(); i++)
			values[i] = filterList.get(i).getMeasurementList().get(column);
		return values;
	}
	
	@Override
	public double getNumericValue(final PathObject pathObject, final String column) {
		if (builderMap.containsKey(column)) {
			// Don't derive a measurement for a core marked as missing
			if (pathObject instanceof TMACoreObject && ((TMACoreObject)pathObject).isMissing())
				return Double.NaN;
			
			MeasurementBuilder<?> builder = builderMap.get(column);
			if (builder instanceof NumericMeasurementBuilder numericMeasurementBuilder)
				return numericMeasurementBuilder.getValue(pathObject).doubleValue();
			else
				return Double.NaN;
		}
		return pathObject.getMeasurementList().get(column);
	}
	
	@Override
	public ObservableList<PathObject> getItems() {
		return filterList;
	}
	
	/**
	 * Access the underlying entries, for which getEntries provides a filtered view.
	 * 
	 * @return
	 */
	public ObservableList<PathObject> getBackingListEntries() {
		return list;
	}

	@Override
	public List<String> getAllNames() {
		return new ArrayList<>(fullList);
	}

	@Override
	public String getStringValue(PathObject pathObject, String column) {
		return getStringValue(pathObject, column, -1);
	}

	/**
	 * Get help text for a measurement if available, or null if no help text is found.
	 * @param column
	 * @return
	 */
	public String getHelpText(String column) {
		MeasurementBuilder<?> builder = builderMap.get(column);
		if (builder != null)
			return builder.getHelpText();
		else
			return "The measurement '" + column + "' from the object's measurement list, or NaN if the measurement is not found";
	}

	@Override
	public String getStringValue(PathObject pathObject, String column, int decimalPlaces) {
		MeasurementBuilder<?> builder = builderMap.get(column);
		if (builder instanceof StringMeasurementBuilder stringMeasurementBuilder) {
			return stringMeasurementBuilder.getValue(pathObject);
		}
		else if (builder instanceof NumericMeasurementBuilder numericMeasurementBuilder)
			return numericMeasurementBuilder.getStringValue(pathObject, decimalPlaces);
		
		if (pathObject == null) {
			logger.warn("Requested measurement {} for null object! Returned empty String.", column);
			return "";
		}
		double val = pathObject.getMeasurementList().get(column);
		if (Double.isNaN(val))
			return "NaN";
		return GeneralTools.formatNumber(val, 4);
	}

	/**
	 * Get the names of all columns corresponding to metadata (String) values.
	 * @return
	 */
	public ReadOnlyListWrapper<String> getMetadataNames() {
		return new ReadOnlyListWrapper<>(metadataList);
	}


	static class ObservableMeasurement extends DoubleBinding {

		private PathObject pathObject;
		private String measurementName;

		public ObservableMeasurement(final PathObject pathObject, final String measurementName) {
			this.pathObject = pathObject;
			this.measurementName = measurementName;
		}

		@Override
		protected double computeValue() {
			return pathObject.getMeasurementList().get(measurementName);
		}

	}

}
