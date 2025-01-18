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
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import qupath.lib.common.GeneralTools;
import qupath.lib.measurements.dynamic.DefaultMeasurements;
import qupath.lib.measurements.dynamic.MeasurementBuilder;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;
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
	public static final String NAME_OBJECT_ID = DefaultMeasurements.OBJECT_ID.getName();

	private static final String KEY_PIXEL_LAYER = "PIXEL_LAYER";

	private ImageData<?> imageData;

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
			builderMap.put("Image", DefaultMeasurements.createImageNameMeasurement(imageData));

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
			measurements.add(DefaultMeasurements.OBJECT_ID);

		// Include the object type
		measurements.add(DefaultMeasurements.OBJECT_TYPE);

		// Include the object displayed name
		measurements.add(DefaultMeasurements.OBJECT_NAME);

		// Include the classification
		if (!wrapper.containsRootOnly()) {
			measurements.add(DefaultMeasurements.CLASSIFICATION);
			// Get the name of the containing TMA core if we have anything other than cores
			if (imageData != null && imageData.getHierarchy().getTMAGrid() != null) {
				measurements.add(DefaultMeasurements.TMA_CORE_NAME);
			}
			// Get the name of the first parent object
			measurements.add(DefaultMeasurements.PARENT_DISPLAYED_NAME);
		}

		// Include the TMA missing status, if appropriate
		if (wrapper.containsTMACores()) {
			measurements.add(DefaultMeasurements.TMA_CORE_MISSING);
		}

		if (wrapper.containsAnnotationsOrDetections()) {
			measurements.add(DefaultMeasurements.ROI_TYPE);
		}

		// Add centroids
		if (!wrapper.containsRootOnly()) {
			measurements.add(DefaultMeasurements.createROICentroidX(imageData));
			measurements.add(DefaultMeasurements.createROICentroidY(imageData));
		}

		// New v0.4.0: include z and time indices
		var serverMetadata = imageData == null ? null : imageData.getServerMetadata();
		if (wrapper.containsMultiZ() || (wrapper.containsROIs() && serverMetadata != null && serverMetadata.getSizeZ() > 1)) {
			measurements.add(DefaultMeasurements.ROI_Z_SLICE);
		}

		if (wrapper.containsMultiT() || (wrapper.containsROIs() && serverMetadata != null && serverMetadata.getSizeT() > 1)) {
			measurements.add(DefaultMeasurements.ROI_TIMEPOINT);
		}
		return measurements;
	}

	private static synchronized List<? extends MeasurementBuilder<?>> createMetadataMeasurements(PathObjectListWrapper wrapper) {
		return wrapper.getMetadataNames()
				.stream()
				.map(DefaultMeasurements::createMetadataMeasurement)
				.toList();
	}

	private static synchronized List<MeasurementBuilder<?>> createFeatureMeasurements(PathObjectListWrapper wrapper) {

		List<MeasurementBuilder<?>> measurements = new ArrayList<>();
		
		// Add derived measurements if we don't have only detections
		if (wrapper.containsRoot() || wrapper.containsAnnotationsTmaCores()) {

			var imageData = wrapper.getImageData();
			boolean detectionsAnywhere = imageData == null ? wrapper.containsDetections() : !imageData.getHierarchy().getDetectionObjects().isEmpty();
			if (detectionsAnywhere) {
				var builder = DefaultMeasurements.createDetectionCountMeasurement(wrapper.getImageData());
				measurements.add(builder);
			}
			
			// Here, we allow TMA cores to act like annotations
			boolean includeDensity = wrapper.containsAnnotations() || wrapper.containsTMACores();
			measurements.addAll(getDetectionCountsMeasurements(wrapper.getImageData(), includeDensity));
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
				measurements.add(DefaultMeasurements.ROI_NUM_POINTS);
			}
			// Add area & perimeter measurements, if we have any areas
			if (annotationRois.stream().anyMatch(ROI::isArea)) {
				measurements.add(DefaultMeasurements.createROIAreaMeasurement(wrapper.getImageData()));
				measurements.add(DefaultMeasurements.createROIPerimeterMeasurement(wrapper.getImageData()));
			}
			// Add line length measurements, if we have any lines
			if (annotationRois.stream().anyMatch(ROI::isLine)) {
				measurements.add(DefaultMeasurements.createROILengthMeasurement(wrapper.getImageData()));
			}
		}
		
		if (wrapper.containsAnnotations() || wrapper.containsTMACores() || wrapper.containsRoot()) {
			var pixelClassifier = getPixelLayer(wrapper.getImageData());
			if (pixelClassifier != null) {
				if (pixelClassifier.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.CLASSIFICATION || pixelClassifier.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.PROBABILITY) {
					var pixelManager = new PixelClassificationMeasurementManager(pixelClassifier);
					for (String name : pixelManager.getMeasurementNames()) {
						measurements.add(DefaultMeasurements.createLivePixelClassificationMeasurement(pixelManager, name));
					}
				}
			}
		}

		return measurements;
	}



	private static List<MeasurementBuilder<?>> getDetectionCountsMeasurements(ImageData<?> imageData, boolean includeDensityMeasurements) {
		List<MeasurementBuilder<?>> builders = new ArrayList<>();
		if (imageData == null || imageData.getHierarchy() == null)
			return builders;

		Set<PathClass> pathClasses = PathObjectTools.getRepresentedPathClasses(imageData.getHierarchy(), PathDetectionObject.class);

		pathClasses.remove(null);
		pathClasses.remove(PathClass.NULL_CLASS);

		Set<PathClass> parentIntensityClasses = new LinkedHashSet<>();
		Set<PathClass> parentPositiveNegativeClasses = new LinkedHashSet<>();
		for (PathClass pathClass : pathClasses) {
			if (PathClassTools.isGradedIntensityClass(pathClass)) {
				parentIntensityClasses.add(pathClass.getParentClass());
				parentPositiveNegativeClasses.add(pathClass.getParentClass());
			} else if (PathClassTools.isPositiveClass(pathClass) || PathClassTools.isNegativeClass(pathClass))
				parentPositiveNegativeClasses.add(pathClass.getParentClass());
		}

		// Store intensity parent classes, if required
		if (!parentPositiveNegativeClasses.isEmpty()) {
			List<PathClass> pathClassList = new ArrayList<>(parentPositiveNegativeClasses);
			pathClassList.remove(null);
			pathClassList.remove(PathClass.NULL_CLASS);
			Collections.sort(pathClassList);
			for (PathClass pathClass : pathClassList) {
				builders.add(DefaultMeasurements.createBaseClassCountsMeasurement(imageData, pathClass));
			}
		}

		// We can compute counts for any PathClass that is represented
		List<PathClass> pathClassList = new ArrayList<>(pathClasses);
		Collections.sort(pathClassList);
		for (PathClass pathClass : pathClassList) {
			builders.add(DefaultMeasurements.createExactClassCountsMeasurement(imageData, pathClass));
		}

		// We can compute positive percentages if we have anything in ParentPositiveNegativeClasses
		for (PathClass pathClass : parentPositiveNegativeClasses) {
			builders.add(DefaultMeasurements.createPositivePercentageMeasurement(imageData, pathClass));
		}
		if (parentPositiveNegativeClasses.size() > 1)
			builders.add(DefaultMeasurements.createPositivePercentageMeasurement(imageData, parentPositiveNegativeClasses.toArray(new PathClass[0])));

		// We can compute H-scores and Allred scores if we have anything in ParentIntensityClasses
		Supplier<Double> allredMinPercentage = PathPrefs.allredMinPercentagePositiveProperty()::get;
		for (PathClass pathClass : parentIntensityClasses) {
			builders.add(DefaultMeasurements.createHScoreMeasurement(imageData, pathClass));
			builders.add(DefaultMeasurements.createAllredProportionMeasurement(imageData, allredMinPercentage, pathClass));
			builders.add(DefaultMeasurements.createAllredIntensityMeasurement(imageData, allredMinPercentage, pathClass));
			builders.add(DefaultMeasurements.createAllredMeasurement(imageData, allredMinPercentage, pathClass));
		}
		if (parentIntensityClasses.size() > 1) {
			PathClass[] parentIntensityClassesArray = parentIntensityClasses.toArray(PathClass[]::new);
			builders.add(DefaultMeasurements.createHScoreMeasurement(imageData, parentIntensityClassesArray));
			builders.add(DefaultMeasurements.createAllredProportionMeasurement(imageData,allredMinPercentage,  parentIntensityClassesArray));
			builders.add(DefaultMeasurements.createAllredIntensityMeasurement(imageData, allredMinPercentage, parentIntensityClassesArray));
			builders.add(DefaultMeasurements.createAllredMeasurement(imageData, allredMinPercentage, parentIntensityClassesArray));
		}

		// Add density measurements
		// These are only added if we have a (non-derived) positive class
		// Additionally, these are only non-NaN if we have an annotation, or a TMA core containing a single annotation
		if (includeDensityMeasurements) {
			for (PathClass pathClass : pathClassList) {
				if (PathClassTools.isPositiveClass(pathClass) && pathClass.getBaseClass() == pathClass) {
					builders.add(DefaultMeasurements.createDetectionClassDensityMeasurement(imageData, pathClass));
				}
			}
		}
		return builders;
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
		var measurement = builderMap.getOrDefault(name, null);
		return measurement != null && measurement.isStringMeasurement();
	}
	
	/**
	 * Query whether a named measurement returns a numeric value only.
	 * @param name the measurement name
	 * @return true if the measurement returns a number, false otherwise
	 */
	public boolean isNumericMeasurement(final String name) {
		var measurement = builderMap.getOrDefault(name, null);
		// TODO: For now, we allow null because we default to requesting from the measurement list -
		//       but this behavior is likely to change
		return measurement == null || measurement.isNumericMeasurement();
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
			if (pathObject instanceof TMACoreObject core) {
				if (core.isMissing())
					return Double.NaN;
			}
			
			MeasurementBuilder<?> builder = builderMap.get(column);
			var val = builder.getValue(pathObject);
			if (val instanceof Number num)
				return num.doubleValue();
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
		if (builder != null)
			return builder.getStringValue(pathObject, decimalPlaces);
		
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

}
