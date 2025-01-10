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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Binding;
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
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.roi.PolygonROI;
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
	
	private ImageData<?> imageData;
	
	private ObservableList<PathObject> list = FXCollections.observableArrayList();
	private FilteredList<PathObject> filterList = new FilteredList<>(list);

	private ObservableList<String> metadataList = FXCollections.observableArrayList();
	private ObservableList<String> measurementList = FXCollections.observableArrayList();
	private ObservableList<String> fullList = FXCollections.observableArrayList();
	
	private DerivedMeasurementManager manager;
	private Map<String, MeasurementBuilder<?>> builderMap = new LinkedHashMap<>();
	
	private static final String KEY_PIXEL_LAYER = "PIXEL_LAYER";
	
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
	
	
	private ImageData<?> getImageData() {
		return imageData;
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
				
		// Check if we have any annotations / TMA cores
		boolean containsDetections = false;
		boolean containsAnnotations = false;
		boolean containsTMACores = false;
		boolean containsRoot = false;
		boolean containsMultiZ = false;
		boolean containsMultiT = false;
		boolean containsROIs = false;
		List<PathObject> pathObjectListCopy = new ArrayList<>(list);
		for (PathObject temp : pathObjectListCopy) {
			containsROIs = containsROIs || temp.hasROI();
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
		boolean detectionsAnywhere = imageData == null ? containsDetections : !imageData.getHierarchy().getDetectionObjects().isEmpty();

		// Include object ID if we have anything other than root objects
		if (containsAnnotations || containsDetections || containsTMACores)
			builderMap.put("Object ID", new ObjectIdMeasurementBuilder());

		// Include the object type
		builderMap.put("Object type", new ObjectTypeMeasurementBuilder());

		// Include the object displayed name
		builderMap.put("Name", new ObjectNameMeasurementBuilder());

		// Include the class
		if (containsAnnotations || containsDetections || containsTMACores) {
			builderMap.put("Classification", new PathClassMeasurementBuilder());
			// Get the name of the containing TMA core if we have anything other than cores
			if (imageData != null && imageData.getHierarchy().getTMAGrid() != null) {
				builderMap.put("TMA core", new TMACoreNameMeasurementBuilder());
			}
			// Get the name of the first parent object
			builderMap.put("Parent", new ParentNameMeasurementBuilder());
		}

		// Include the TMA missing status, if appropriate
		if (containsTMACores) {
			builderMap.put("Missing", new MissingTMACoreMeasurementBuilder());
		}
		
		if (containsAnnotations || containsDetections) {
			builderMap.put("ROI", new ROINameMeasurementBuilder());
		}
		
		// Add centroids
		if (containsAnnotations || containsDetections || containsTMACores) {
			ROICentroidMeasurementBuilder builder = new ROICentroidMeasurementBuilder(imageData, CentroidType.X);
			builderMap.put(builder.getName(), builder);
			builder = new ROICentroidMeasurementBuilder(imageData, CentroidType.Y);
			builderMap.put(builder.getName(), builder);
		}

		// New v0.4.0: include z and time indices
		var serverMetadata = imageData == null ? null : imageData.getServerMetadata();
		if (containsMultiZ || (containsROIs && serverMetadata != null && serverMetadata.getSizeZ() > 1)) {
			builderMap.put("Z index", new ZSliceMeasurementBuilder());
		}

		if (containsMultiT || (containsROIs && serverMetadata != null && serverMetadata.getSizeT() > 1)) {
			builderMap.put("Time index", new TimepointMeasurementBuilder());
		}

		// If we have metadata, store it
        Set<String> metadataNames = new LinkedHashSet<>(builderMap.keySet());
		for (PathObject pathObject : pathObjectListCopy) {
			// Don't show metadata keys that start with "_"
			pathObject.getMetadata()
					.keySet()
							.stream()
									.filter(key -> key != null && !key.startsWith("_"))
											.forEach(metadataNames::add);
		}
		// Ensure we have suitable builders
		for (String name : metadataNames) {
			if (!builderMap.containsKey(name))
				builderMap.put(name, new StringMetadataMeasurementBuilder(name));
		}
		
		// Get all the 'built-in' feature measurements, stored in the measurement list
		Collection<String> features = PathObjectTools.getAvailableFeatures(pathObjectListCopy);
		
		// Add derived measurements if we don't have only detections
		if (containsAnnotations || containsTMACores || containsRoot) {
			
			if (detectionsAnywhere) {
				var builder = new ObjectTypeCountMeasurementBuilder(imageData, PathDetectionObject.class);
				builderMap.put(builder.getName(), builder);
				features.add(builder.getName());
			}
			
			// Here, we allow TMA cores to act like annotations
			manager = new DerivedMeasurementManager(getImageData(), containsAnnotations || containsTMACores);
			for (MeasurementBuilder<?> builder2 : manager.getMeasurementBuilders()) {
				builderMap.put(builder2.getName(), builder2);
				features.add(builder2.getName());
			}
			
		}
		
		// If we have an annotation, add shape features
		if (containsAnnotations) {
			boolean anyPoints = false;
			boolean anyAreas = false;
			boolean anyLines = false;
			@SuppressWarnings("unused")
			boolean anyPolygons = false;
			for (PathObject pathObject : pathObjectListCopy) {
				if (!pathObject.isAnnotation())
					continue;
				ROI roi = pathObject.getROI();
				if (roi == null)
					continue;
				if (roi.isPoint())
					anyPoints = true;
				if (roi.isArea())
					anyAreas = true;
				if (roi.isLine())
					anyLines = true;
				if (pathObject.getROI() instanceof PolygonROI)
					anyPolygons = true;
			}
			// Add point count, if needed
			if (anyPoints) {
				MeasurementBuilder<?> builder = new NumPointsMeasurementBuilder();
				builderMap.put(builder.getName(), builder);
				features.add(builder.getName());
			}
			// Add spatial measurements, if needed
			if (anyAreas) {
				MeasurementBuilder<?> builder = new AreaMeasurementBuilder(imageData);
				builderMap.put(builder.getName(), builder);
				features.add(builder.getName());
				builder = new PerimeterMeasurementBuilder(imageData);
				builderMap.put(builder.getName(), builder);
				features.add(builder.getName());
			}
			if (anyLines) {
				MeasurementBuilder<?> builder = new LineLengthMeasurementBuilder(imageData);
				builderMap.put(builder.getName(), builder);
				features.add(builder.getName());
			}
		}
		
		if (containsAnnotations || containsTMACores || containsRoot) {
			var pixelClassifier = getPixelLayer(imageData);
			if (pixelClassifier != null) {
				if (pixelClassifier.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.CLASSIFICATION || pixelClassifier.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.PROBABILITY) {
					var pixelManager = new PixelClassificationMeasurementManager(pixelClassifier);
					for (String name : pixelManager.getMeasurementNames()) {
//						String nameLive = name + " (live)";
						String nameLive = "(Live) " + name;
						builderMap.put(nameLive, new PixelClassifierMeasurementBuilder(pixelManager, name));
						features.add(nameLive);
					}
				}
			}
		}

		// Update all the lists, if necessary
		boolean changes = false;
		if (metadataNames.size() != metadataList.size() || !metadataNames.containsAll(metadataList)) {
			changes = metadataList.setAll(metadataNames);
		}
		if (features.size() != measurementList.size() || !features.containsAll(measurementList))
			changes = measurementList.setAll(features);
		if (changes) {
			if (metadataList.isEmpty())
				fullList.setAll(measurementList);
			else {
				fullList.setAll(metadataList);
				fullList.addAll(measurementList);
			}
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
	 * Refresh the measurement values.
	 */
	public void refreshEntries() {
		// Clear the cached map to force updates
		if (manager != null)
			manager.clearMap();
	}
	
	/**
	 * Create a specific numeric measurement.
	 * <p>
	 * Warning! This binding is not guaranteed to update its value automatically upon changes to the 
	 * underlying object or data.
	 * 
	 * @param pathObject
	 * @param column
	 * @return
	 */
	@Deprecated
	public Binding<Number> createNumericMeasurement(final PathObject pathObject, final String column) {
		MeasurementBuilder<?> builder = builderMap.get(column);
		if (builder == null)
			return new ObservableMeasurement(pathObject, column);
		else if (builder instanceof AbstractNumericMeasurementBuilder numericMeasurementBuilder)
			return numericMeasurementBuilder.createMeasurement(pathObject);
		else
			throw new IllegalArgumentException(column + " does not represent a numeric measurement!");
	}
	
	
	/**
	 * Create a specific String measurement.
	 * <p>
	 * Warning! This binding is not guaranteed to update its value automatically upon changes to the 
	 * underlying object or data.
	 * 
	 * @param pathObject
	 * @param column
	 * @return
	 */
	@Deprecated
	public Binding<String> createStringMeasurement(final PathObject pathObject, final String column) {
		MeasurementBuilder<?> builder = builderMap.get(column);
		if (builder instanceof AbstractStringMeasurementBuilder stringMeasurementBuilder)
			return stringMeasurementBuilder.createMeasurement(pathObject);
		else
			throw new IllegalArgumentException(column + " does not represent a String measurement!");
	}

	/**
	 * Query whether a named measurement returns a {@link String} value only.
	 * @param name the measurement name
	 * @return true if the measurement returns a String (only), false otherwise
	 */
	public boolean isStringMeasurement(final String name) {
		return builderMap.get(name) instanceof AbstractStringMeasurementBuilder;
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
			if (builder instanceof AbstractNumericMeasurementBuilder)
				return ((AbstractNumericMeasurementBuilder)builder).createMeasurement(pathObject).getValue().doubleValue();
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
		if (builder instanceof AbstractStringMeasurementBuilder stringMeasurementBuilder) {
			return stringMeasurementBuilder.getMeasurementValue(pathObject);
		}
		else if (builder instanceof AbstractNumericMeasurementBuilder numericMeasurementBuilder)
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
