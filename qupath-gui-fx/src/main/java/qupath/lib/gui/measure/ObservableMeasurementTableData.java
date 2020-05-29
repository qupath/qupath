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

package qupath.lib.gui.measure;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.binding.Binding;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.DoubleBinding;
import javafx.beans.binding.IntegerBinding;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.beans.value.ObservableDoubleValue;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import qupath.lib.classifiers.PathClassifierTools;
import qupath.lib.classifiers.pixel.PixelClassificationImageServer;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.measure.ObservableMeasurementTableData.ROICentroidMeasurementBuilder.CentroidType;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.objects.MetadataStore;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.classes.PathClassTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.ROIs;
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
	
	final static Logger logger = LoggerFactory.getLogger(ObservableMeasurementTableData.class);
	
	private ImageData<?> imageData;
	
	private ObservableList<PathObject> list = FXCollections.observableArrayList();
	private FilteredList<PathObject> filterList = new FilteredList<>(list);

	private ObservableList<String> metadataList = FXCollections.observableArrayList();
	private ObservableList<String> measurementList = FXCollections.observableArrayList();
	private ObservableList<String> fullList = FXCollections.observableArrayList();
	
	private DerivedMeasurementManager manager;
	private Map<String, MeasurementBuilder<?>> builderMap = new LinkedHashMap<>();
	
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
//		if (Platform.isFxApplicationThread())
		updateMeasurementList();
//		else
//			Platform.runLater(() -> updateMeasurementList());
	}
	
	
	private ImageData<?> getImageData() {
		return imageData;
	}
	
	/**
	 * Update the entire measurement list for the current objects.
	 * @see #setImageData(ImageData, Collection)
	 */
	public synchronized void updateMeasurementList() {
		
//		PathPrefs.setAllredMinPercentagePositive(0);
		
		builderMap.clear();
		
		// Add the image name
		builderMap.put("Image", new ImageNameMeasurementBuilder(imageData));
				
		// Check if we have any annotations / TMA cores
		boolean containsDetections = false;
		boolean containsAnnotations = false;
//		boolean containsParentAnnotations = false;
		boolean containsTMACores = false;
		boolean containsRoot = false;
		List<PathObject> pathObjectListCopy = new ArrayList<>(list);
		for (PathObject temp : pathObjectListCopy) {
			if (temp instanceof PathAnnotationObject) {
//				if (temp.hasChildren())
//					containsParentAnnotations = true;
				containsAnnotations = true;
			} else if (temp instanceof TMACoreObject) {
				containsTMACores = true;
			} else if (temp instanceof PathDetectionObject) {
				containsDetections = true;
			} else if (temp.isRootObject())
				containsRoot = true;
		}
		boolean detectionsAnywhere = imageData == null ? containsDetections : !imageData.getHierarchy().getDetectionObjects().isEmpty();
		
		// Include the object displayed name
//		if (containsDetections || containsAnnotations || containsTMACores)
		builderMap.put("Name", new ObjectNameMeasurementBuilder());
		
		// Include the class
		if (containsAnnotations || containsDetections) {
			builderMap.put("Class", new PathClassMeasurementBuilder());
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
//			ROICentroidMeasurementBuilder builder = new ROICentroidMeasurementBuilder(imageData, CentroidType.X);
//			builderMap.put("Centroid X", builder);
//			builder = new ROICentroidMeasurementBuilder(imageData, CentroidType.Y);
//			builderMap.put("Centroid Y", builder);
			ROICentroidMeasurementBuilder builder = new ROICentroidMeasurementBuilder(imageData, CentroidType.X);
			builderMap.put(builder.getName(), builder);
			builder = new ROICentroidMeasurementBuilder(imageData, CentroidType.Y);
			builderMap.put(builder.getName(), builder);
		}

		// If we have metadata, store it
		Set<String> metadataNames = new LinkedHashSet<>();
		metadataNames.addAll(builderMap.keySet());
		for (PathObject pathObject : pathObjectListCopy) {
			if (pathObject instanceof MetadataStore) {
				metadataNames.addAll(((MetadataStore)pathObject).getMetadataKeys());
			}
		}
		// Ensure we have suitable builders
		for (String name : metadataNames) {
			if (!builderMap.containsKey(name))
				builderMap.put(name, new StringMetadataMeasurementBuilder(name));
		}
		
		
		// Get all the 'built-in' feature measurements, stored in the measurement list
		Collection<String> features = PathClassifierTools.getAvailableFeatures(pathObjectListCopy);
		
		// Add derived measurements if we don't have only detections
		if (containsAnnotations || containsTMACores || containsRoot) {
//		if (containsParentAnnotations || containsTMACores) {
			// Omit annotations, mostly because it's can be very slow to compute
//			var builderAnnotations = new ObjectTypeCountMeasurementBuilder(PathAnnotationObject.class);
//			builderMap.put(builderAnnotations.getName(), builderAnnotations);
//			features.add(builderAnnotations.getName());
			
			if (detectionsAnywhere) {
				var builder = new ObjectTypeCountMeasurementBuilder(PathDetectionObject.class);
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
//			if (anyPolygons) {
//				MeasurementBuilder<?> builder = new MaxDiameterMeasurementBuilder(imageData);
//				builderMap.put(builder.getName(), builder);
//				features.add(builder.getName());
//				
//				builder = new MinDiameterMeasurementBuilder(imageData);
//				builderMap.put(builder.getName(), builder);
//				features.add(builder.getName());

//			}
		}
		
		if (containsAnnotations || containsTMACores || containsRoot) {
			var pixelClassifier = PixelClassificationImageServer.getPixelLayer(imageData);
			if (pixelClassifier instanceof ImageServer<?>) {
				ImageServer<BufferedImage> server = (ImageServer<BufferedImage>)pixelClassifier;
				if (server.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.CLASSIFICATION || server.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.PROBABILITY) {
					var pixelManager = new PixelClassificationMeasurementManager(server);
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
			manager.map.clear();
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
		else if (builder instanceof NumericMeasurementBuilder)
			return ((NumericMeasurementBuilder)builder).createMeasurement(pathObject);
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
		if (builder instanceof StringMeasurementBuilder)
			return ((StringMeasurementBuilder)builder).createMeasurement(pathObject);
		else
			throw new IllegalArgumentException(column + " does not represent a String measurement!");
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
			values[i] = filterList.get(i).getMeasurementList().getMeasurementValue(column);
		return values;
	}
	
	@Override
	public double getNumericValue(final PathObject pathObject, final String column) {
		if (builderMap.containsKey(column)) {
			// Don't derive a measurement for a core marked as missing
			if (pathObject instanceof TMACoreObject && ((TMACoreObject)pathObject).isMissing())
				return Double.NaN;
			
			MeasurementBuilder<?> builder = builderMap.get(column);
			if (builder instanceof NumericMeasurementBuilder)
				return ((NumericMeasurementBuilder)builder).createMeasurement(pathObject).getValue().doubleValue();
			else
				return Double.NaN;
		}
		return pathObject.getMeasurementList().getMeasurementValue(column);
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
	
	static ObservableValue<Number> createObservableMeasurement(final PathObject pathObject, final String name) {
		return new ObservableMeasurement(pathObject, name);
	}
	
	static ObservableValue<Number> createObservableClassProbability(final PathObject pathObject, final String name) {
		return new ObservableClassProbability(pathObject);
	}
	
	
	static class ObservableMeasurement extends DoubleBinding {
		
		private PathObject pathObject;
		private String name;
		
		public ObservableMeasurement(final PathObject pathObject, final String name) {
			this.pathObject = pathObject;
			this.name = name;
		}

		@Override
		protected double computeValue() {
			return pathObject.getMeasurementList().getMeasurementValue(name);
		}
		
	}
	
	static class ObservableClassProbability extends DoubleBinding {
		
		private PathObject pathObject;
		
		public ObservableClassProbability(final PathObject pathObject) {
			this.pathObject = pathObject;
		}

		@Override
		protected double computeValue() {
			return pathObject.getClassProbability();
		}
		
	}
	
	
	
	class ObjectTypeCountMeasurement extends IntegerBinding {
		
		private Class<? extends PathObject> cls;
		private PathObject pathObject;
		
		public ObjectTypeCountMeasurement(final PathObject pathObject, final Class<? extends PathObject> cls) {
			this.pathObject = pathObject;
			this.cls = cls;
		}
		
		@Override
		protected int computeValue() {
			Collection<PathObject> pathObjects;
			if (pathObject.isRootObject())
				pathObjects = imageData.getHierarchy().getObjects(null, cls);
			else
				pathObjects = imageData.getHierarchy().getObjectsForROI(cls, pathObject.getROI());
			pathObjects.remove(pathObject);
			return pathObjects.size();
//			return PathObjectTools.countChildren(pathObject, cls, true);
		}
		
	}
	
	
	class ObjectTypeCountMeasurementBuilder extends NumericMeasurementBuilder {
		
		private Class<? extends PathObject> cls;
		
		public ObjectTypeCountMeasurementBuilder(final Class<? extends PathObject> cls) {
			this.cls = cls;
		}
		
		@Override
		public String getName() {
			return "Num " + PathObjectTools.getSuitableName(cls, true);
		}
		
		@Override
		public Binding<Number> createMeasurement(final PathObject pathObject) {
			return new ObjectTypeCountMeasurement(pathObject, cls);
		}
		
		@Override
		public String toString() {
			return getName();
		}
		
	}
	
	
	
	static class DerivedMeasurementManager {
		
		private ImageData<?> imageData;
		
		private boolean valid = false;
//		private Set<PathClass> parentIntensityClasses = new LinkedHashSet<>();
//		private Set<PathClass> parentPositiveNegativeClasses = new LinkedHashSet<>();
		
		private List<MeasurementBuilder<?>> builders = new ArrayList<>();
		
		// Map to store cached counts, will be reset when the hierarchy changes (in any way)
		private Map<PathObject, DetectionPathClassCounts> map = new WeakHashMap<>();
		
		private boolean containsAnnotations;
		
		DerivedMeasurementManager(final ImageData<?> imageData, final boolean containsAnnotations) {
			this.imageData = imageData;
			this.containsAnnotations = containsAnnotations;
			updateAvailableMeasurements();
		}
		
		void updateAvailableMeasurements() {
//			parentIntensityClasses.clear();
//			parentPositiveNegativeClasses.clear();
			map.clear();
			builders.clear();
			if (imageData == null || imageData.getHierarchy() == null)
				return;
			
			Set<PathClass> pathClasses = PathClassifierTools.getRepresentedPathClasses(imageData.getHierarchy(), PathDetectionObject.class);

//			// Ensure that any base classes are present
//			Set<PathClass> basePathClasses = new LinkedHashSet<>();
//			for (PathClass pathClass : pathClasses.toArray(new PathClass[0])) {
//				basePathClasses.add(pathClass.getBaseClass());
//			}
//			pathClasses.addAll(basePathClasses);

			pathClasses.remove(null);
			pathClasses.remove(PathClassFactory.getPathClassUnclassified());

			Set<PathClass> parentIntensityClasses = new LinkedHashSet<>();
			Set<PathClass> parentPositiveNegativeClasses = new LinkedHashSet<>();
			for (PathClass pathClass : pathClasses) {
				if (PathClassTools.isGradedIntensityClass(pathClass)) {
					parentIntensityClasses.add(pathClass.getParentClass());
					parentPositiveNegativeClasses.add(pathClass.getParentClass());
				}
				else if (PathClassTools.isPositiveClass(pathClass) || PathClassTools.isNegativeClass(pathClass))
					parentPositiveNegativeClasses.add(pathClass.getParentClass());
			}
			
			// Store intensity parent classes, if required
			if (!parentPositiveNegativeClasses.isEmpty()) {
				List<PathClass> pathClassList = new ArrayList<>(parentPositiveNegativeClasses);
				pathClassList.remove(null);
				pathClassList.remove(PathClassFactory.getPathClassUnclassified());
				Collections.sort(pathClassList);
				for (PathClass pathClass : pathClassList) {
					builders.add(new ClassCountMeasurementBuilder(pathClass, true));
				}				
			}
//			// Store the base classifications, if different
//			for (PathClass pathClass : basePathClasses) {
//				builders.add(new ClassCountMeasurementBuilder(pathClass, true));
//			}
			
			// We can compute counts for any PathClass that is represented
			List<PathClass> pathClassList = new ArrayList<>(pathClasses);
			Collections.sort(pathClassList);
			for (PathClass pathClass : pathClassList) {
				builders.add(new ClassCountMeasurementBuilder(pathClass, false));
			}

			// We can compute positive percentages if we have anything in ParentPositiveNegativeClasses
			for (PathClass pathClass : parentPositiveNegativeClasses) {
				builders.add(new PositivePercentageMeasurementBuilder(pathClass));
			}
			if (parentPositiveNegativeClasses.size() > 1)
				builders.add(new PositivePercentageMeasurementBuilder(parentPositiveNegativeClasses.toArray(new PathClass[0])));

			// We can compute H-scores and Allred scores if we have anything in ParentIntensityClasses
			for (PathClass pathClass : parentIntensityClasses) {
				builders.add(new HScoreMeasurementBuilder(pathClass));
				builders.add(new AllredProportionMeasurementBuilder(pathClass));
				builders.add(new AllredIntensityMeasurementBuilder(pathClass));
				builders.add(new AllredMeasurementBuilder(pathClass));
			}
			if (parentIntensityClasses.size() > 1) {
				PathClass[] parentIntensityClassesArray = parentIntensityClasses.toArray(PathClass[]::new);
				builders.add(new HScoreMeasurementBuilder(parentIntensityClassesArray));
				builders.add(new AllredProportionMeasurementBuilder(parentIntensityClassesArray));
				builders.add(new AllredIntensityMeasurementBuilder(parentIntensityClassesArray));
				builders.add(new AllredMeasurementBuilder(parentIntensityClassesArray));
			}
			
			// Add density measurements
			// These are only added if we have a (non-derived) positive class
			// Additionally, these are only non-NaN if we have an annotation, or a TMA core containing a single annotation
			if (containsAnnotations) {
				for (PathClass pathClass : pathClassList) {
					if (PathClassTools.isPositiveClass(pathClass) && pathClass.getBaseClass() == pathClass)
	//				if (!(PathClassFactory.isDefaultIntensityClass(pathClass) || PathClassFactory.isNegativeClass(pathClass)))
						builders.add(new ClassDensityMeasurementBuilder(imageData.getServer(), pathClass));
				}
			}

			valid = true;
		}
		
		private List<MeasurementBuilder<?>> getMeasurementBuilders() {
			if (!valid)
				updateAvailableMeasurements();
			return builders;
		}

		
		
		class ClassCountMeasurement extends IntegerBinding {
			
			private PathObject pathObject;
			private PathClass pathClass;
			private boolean baseClassification;
			
			public ClassCountMeasurement(final PathObject pathObject, final PathClass pathClass, final boolean baseClassification) {
				this.pathObject = pathObject;
				this.pathClass = pathClass;
				this.baseClassification = baseClassification;
			}

			@Override
			protected int computeValue() {
				DetectionPathClassCounts counts = map.get(pathObject);
				if (counts == null) {
					counts = new DetectionPathClassCounts(imageData.getHierarchy(), pathObject);
					map.put(pathObject, counts);
				}
				if (baseClassification)
					return counts.getCountForAncestor(pathClass);
				else
					return counts.getDirectCount(pathClass);
			}
			
		}
		
		
		class ClassDensityMeasurementPerMM extends DoubleBinding {
			
			private ImageServer<?> server;
			private PathObject pathObject;
			private PathClass pathClass;
			
			public ClassDensityMeasurementPerMM(final ImageServer<?> server, final PathObject pathObject, final PathClass pathClass) {
				this.server = server;
				this.pathObject = pathObject;
				this.pathClass = pathClass;
			}

			@Override
			protected double computeValue() {
				// If we have a TMA core, look for a single annotation inside
				// If we don't have that, we can't return counts since it's ambiguous where the 
				// area should be coming from
				PathObject pathObjectTemp = pathObject;
				if (pathObject instanceof TMACoreObject) {
					var children = pathObject.getChildObjectsAsArray();
					if (children.length != 1)
						return Double.NaN;
					pathObjectTemp = children[0];
				}
				// We need an annotation to get a meaningful area
				if (pathObjectTemp == null || !(pathObjectTemp.isAnnotation() || pathObjectTemp.isRootObject()))
					return Double.NaN;
				
				DetectionPathClassCounts counts = map.get(pathObjectTemp);
				if (counts == null) {
					counts = new DetectionPathClassCounts(imageData.getHierarchy(), pathObjectTemp);
					map.put(pathObject, counts);
				}
				int n = counts.getCountForAncestor(pathClass);
				ROI roi = pathObjectTemp.getROI();
				// For the root, we can measure density only for 2D images of a single time-point
				if (pathObjectTemp.isRootObject() && server.nZSlices() == 1 && server.nTimepoints() == 1)
					roi = ROIs.createRectangleROI(0, 0, server.getWidth(), server.getHeight(), ImagePlane.getDefaultPlane());
				
				if (roi != null && roi.isArea()) {
					double pixelWidth = 1;
					double pixelHeight = 1;
					PixelCalibration cal = server == null ? null : server.getPixelCalibration();
					if (cal != null && cal.hasPixelSizeMicrons()) {
						pixelWidth = cal.getPixelWidthMicrons() / 1000;
						pixelHeight = cal.getPixelHeightMicrons() / 1000;
					}
					return n / roi.getScaledArea(pixelWidth, pixelHeight);
				}
				return Double.NaN;
			}
			
		}
		
		
		class HScore extends DoubleBinding {
			
			private PathObject pathObject;
			private PathClass[] pathClasses;
			
			public HScore(final PathObject pathObject, final PathClass... pathClasses) {
				this.pathObject = pathObject;
				this.pathClasses = pathClasses;
			}

			@Override
			protected double computeValue() {
				DetectionPathClassCounts counts = map.get(pathObject);
				if (counts == null) {
					counts = new DetectionPathClassCounts(imageData.getHierarchy(), pathObject);
					map.put(pathObject, counts);
				}
				return counts.getHScore(pathClasses);
			}
			
		}
		
		
		class AllredIntensityScore extends DoubleBinding {
			
			private PathObject pathObject;
			private PathClass[] pathClasses;
			private ObservableDoubleValue minPositivePercentage;
			
			public AllredIntensityScore(final PathObject pathObject, final ObservableDoubleValue minPositivePercentage, final PathClass... pathClasses) {
				this.pathObject = pathObject;
				this.pathClasses = pathClasses;
				this.minPositivePercentage = minPositivePercentage;
			}

			@Override
			protected double computeValue() {
				DetectionPathClassCounts counts = map.get(pathObject);
				if (counts == null) {
					counts = new DetectionPathClassCounts(imageData.getHierarchy(), pathObject);
					map.put(pathObject, counts);
				}
				return counts.getAllredIntensity(minPositivePercentage.doubleValue() / 100, pathClasses);
			}
			
		}
		
		
		class AllredProportionScore extends DoubleBinding {
			
			private PathObject pathObject;
			private PathClass[] pathClasses;
			private ObservableDoubleValue minPositivePercentage;
			
			public AllredProportionScore(final PathObject pathObject, final ObservableDoubleValue minPositivePercentage, final PathClass... pathClasses) {
				this.pathObject = pathObject;
				this.pathClasses = pathClasses;
				this.minPositivePercentage = minPositivePercentage;
			}

			@Override
			protected double computeValue() {
				DetectionPathClassCounts counts = map.get(pathObject);
				if (counts == null) {
					counts = new DetectionPathClassCounts(imageData.getHierarchy(), pathObject);
					map.put(pathObject, counts);
				}
				return counts.getAllredProportion(minPositivePercentage.doubleValue() / 100, pathClasses);
			}
			
		}
		
		class AllredScore extends DoubleBinding {
			
			private PathObject pathObject;
			private PathClass[] pathClasses;
			private ObservableDoubleValue minPositivePercentage;
			
			public AllredScore(final PathObject pathObject, final ObservableDoubleValue minPositivePercentage, final PathClass... pathClasses) {
				this.pathObject = pathObject;
				this.pathClasses = pathClasses;
				this.minPositivePercentage = minPositivePercentage;
			}

			@Override
			protected double computeValue() {
				DetectionPathClassCounts counts = map.get(pathObject);
				if (counts == null) {
					counts = new DetectionPathClassCounts(imageData.getHierarchy(), pathObject);
					map.put(pathObject, counts);
				}
				return counts.getAllredScore(minPositivePercentage.doubleValue() / 100, pathClasses);
			}
			
		}
		
		
		class PositivePercentage extends DoubleBinding {
			
			private PathObject pathObject;
			private PathClass[] pathClasses;
			
			public PositivePercentage(final PathObject pathObject, final PathClass... pathClasses) {
				this.pathObject = pathObject;
				this.pathClasses = pathClasses;
			}

			@Override
			protected double computeValue() {
				DetectionPathClassCounts counts = map.get(pathObject);
				if (counts == null) {
					counts = new DetectionPathClassCounts(imageData.getHierarchy(), pathObject);
					map.put(pathObject, counts);
				}
				return counts.getPositivePercentage(pathClasses);
			}
			
		}
		
		
		class ClassCountMeasurementBuilder extends NumericMeasurementBuilder {
			
			private PathClass pathClass;
			private boolean baseClassification;
			
			/**
			 * Count objects with specific classifications.
			 * 
			 * @param pathClass
			 * @param baseClassification if {@code true}, also count objects with classifications derived from the specified classification, 
			 * if {@code false} count objects with <i>only</i> the exact classification given.
			 */
			ClassCountMeasurementBuilder(final PathClass pathClass, final boolean baseClassification) {
				this.pathClass = pathClass;
				this.baseClassification = baseClassification;
			}
			
			@Override
			public String getName() {
				if (baseClassification)
					return "Num " + pathClass.toString() + " (base)";
				else
					return "Num " + pathClass.toString();
			}
			
			@Override
			public Binding<Number> createMeasurement(final PathObject pathObject) {
				return new ClassCountMeasurement(pathObject, pathClass, baseClassification);
			}
			
			@Override
			public String toString() {
				return getName();
			}
			
		}
		
		
		class ClassDensityMeasurementBuilder extends NumericMeasurementBuilder {
			
			private ImageServer<?> server;
			private PathClass pathClass;
			
			ClassDensityMeasurementBuilder(final ImageServer<?> server, final PathClass pathClass) {
				this.server = server;
				this.pathClass = pathClass;
			}
			
			@Override
			public String getName() {
				if (server != null && server.getPixelCalibration().hasPixelSizeMicrons())
					return String.format("Num %s per mm^2", pathClass.toString());
//					return String.format("Num %s per %s^2", pathClass.toString(), GeneralTools.micrometerSymbol());
				else
					return String.format("Num %s per px^2", pathClass.toString());
			}
			
			@Override
			public Binding<Number> createMeasurement(final PathObject pathObject) {
				// Only return density measurements for annotations
				if (pathObject.isAnnotation() || (pathObject.isTMACore() && pathObject.nChildObjects() == 1))
					return new ClassDensityMeasurementPerMM(server, pathObject, pathClass);
				return Bindings.createDoubleBinding(() -> Double.NaN);
			}
			
			@Override
			public String toString() {
				return getName();
			}
			
		}
		
		
		class PositivePercentageMeasurementBuilder extends NumericMeasurementBuilder {
			
			private PathClass[] parentClasses;
			
			PositivePercentageMeasurementBuilder(final PathClass... parentClasses) {
				this.parentClasses = parentClasses;
			}
			
			@Override
			public String getName() {
				return getNameForClasses("Positive %", parentClasses);
			}
			
			@Override
			public Binding<Number> createMeasurement(final PathObject pathObject) {
				return new PositivePercentage(pathObject, parentClasses);
			}
			
		}
		
		
		/**
		 * Get a suitable name for a measurement that reflects the parent PathClasses used in its calculation, e.g.
		 * to get the positive % measurement name for both tumor & stroma classes, the input would be
		 *   getNameForClasses("Positive %", tumorClass, stromaClass);
		 * and the output would be "Stroma + Tumor: Positive %"
		 * 
		 * @param measurementName
		 * @param parentClasses
		 * @return
		 */
		static String getNameForClasses(final String measurementName, final PathClass...parentClasses) {
			if (parentClasses == null || parentClasses.length == 0)
				return measurementName;
			if (parentClasses.length == 1) {
				PathClass parent = parentClasses[0];
				if (parent == null)
					return measurementName;
				else
					return parent.getBaseClass().toString() + ": " + measurementName;
			}
			String[] names = new String[parentClasses.length];
			for (int i = 0; i < names.length; i++) {
				PathClass parent = parentClasses[i];
				names[i] = parent == null ? "" : parent.getName();
			}
			Arrays.sort(names);
			return String.join(" + ", names) + ": " + measurementName;
		}
		
		
		
		class HScoreMeasurementBuilder extends NumericMeasurementBuilder {
			
			private PathClass[] pathClasses;
			
			HScoreMeasurementBuilder(final PathClass... pathClasses) {
				this.pathClasses = pathClasses;
			}
			
			@Override
			public String getName() {
				return getNameForClasses("H-score", pathClasses);
			}
			
			@Override
			public Binding<Number> createMeasurement(final PathObject pathObject) {
				return new HScore(pathObject, pathClasses);
			}
			
		}
		
		
		class AllredIntensityMeasurementBuilder extends NumericMeasurementBuilder {
			
			private PathClass[] pathClasses;
			
			AllredIntensityMeasurementBuilder(final PathClass... pathClasses) {
				this.pathClasses = pathClasses;
			}
			
			@Override
			public String getName() {
				double minPercentage = PathPrefs.allredMinPercentagePositiveProperty().get();
				String name;
				if (minPercentage > 0)
					name = String.format("Allred intensity (min %.1f%%)", minPercentage);
				else
					name = "Allred intensity";
				return getNameForClasses(name, pathClasses);
			}
			
			@Override
			public Binding<Number> createMeasurement(final PathObject pathObject) {
				return new AllredIntensityScore(pathObject, PathPrefs.allredMinPercentagePositiveProperty(), pathClasses);
			}
			
		}
		
		class AllredProportionMeasurementBuilder extends NumericMeasurementBuilder {
			
			private PathClass[] pathClasses;
			
			AllredProportionMeasurementBuilder(final PathClass... pathClasses) {
				this.pathClasses = pathClasses;
			}
			
			@Override
			public String getName() {
				double minPercentage = PathPrefs.allredMinPercentagePositiveProperty().get();
				String name;
				if (minPercentage > 0)
					name = String.format("Allred proportion (min %.1f%%)", minPercentage);
				else
					name = "Allred proportion";
				return getNameForClasses(name, pathClasses);
			}
			
			@Override
			public Binding<Number> createMeasurement(final PathObject pathObject) {
				return new AllredProportionScore(pathObject, PathPrefs.allredMinPercentagePositiveProperty(), pathClasses);
			}
			
		}
		
		class AllredMeasurementBuilder extends NumericMeasurementBuilder {
			
			private PathClass[] pathClasses;
			
			AllredMeasurementBuilder(final PathClass... pathClasses) {
				this.pathClasses = pathClasses;
			}
			
			@Override
			public String getName() {
				double minPercentage = PathPrefs.allredMinPercentagePositiveProperty().get();
				String name;
				if (minPercentage > 0)
					name = String.format("Allred score (min %.1f%%)", minPercentage);
				else
					name = "Allred score";
				return getNameForClasses(name, pathClasses);
			}
			
			@Override
			public Binding<Number> createMeasurement(final PathObject pathObject) {
				return new AllredScore(pathObject, PathPrefs.allredMinPercentagePositiveProperty(), pathClasses);
			}
			
		}
		
		
	}
	
	
	static interface MeasurementBuilder<T> {
		
		public String getName();
		
		public Binding<T> createMeasurement(final PathObject pathObject);
		
	}
	
	
	
	static abstract class StringMeasurementBuilder implements MeasurementBuilder<String> {
		
		protected abstract String getMeasurementValue(final PathObject pathObject);
		
		@Override
		public Binding<String> createMeasurement(final PathObject pathObject) {
			return new StringBinding() {
				@Override
				protected String computeValue() {
					return getMeasurementValue(pathObject);
				}
			};
		}
		
	}
	
	
	
	
	static class ObjectNameMeasurementBuilder extends StringMeasurementBuilder {

		@Override
		public String getName() {
			return "Name";
		}

		@Override
		protected String getMeasurementValue(PathObject pathObject) {
			return pathObject == null ? null : pathObject.getDisplayedName();
		}
		
	}
	
	
	static class PathClassMeasurementBuilder extends StringMeasurementBuilder {

		@Override
		public String getName() {
			return "Class";
		}

		@Override
		protected String getMeasurementValue(PathObject pathObject) {
			return pathObject.getPathClass() == null ? null : pathObject.getPathClass().toString();
		}
		
	}
	
	
	static class ROINameMeasurementBuilder extends StringMeasurementBuilder {

		@Override
		public String getName() {
			return "ROI";
		}

		@Override
		protected String getMeasurementValue(PathObject pathObject) {
			return pathObject.hasROI() ? pathObject.getROI().getRoiName() : null;
		}
		
	}
	
	
	static class ROICentroidMeasurementBuilder extends RoiMeasurementBuilder {
		
		enum CentroidType {X, Y};
		private CentroidType type;

		ROICentroidMeasurementBuilder(ImageData<?> imageData, final CentroidType type) {
			super(imageData);
			this.type = type;
		}

		@Override
		public String getName() {
			return String.format("Centroid %s %s", type, hasPixelSizeMicrons() ? GeneralTools.micrometerSymbol() : "px");
		}

		public double getCentroid(ROI roi) {
			if (roi == null || type == null)
				return Double.NaN;
			if (hasPixelSizeMicrons()) {
				return type == CentroidType.X
						? roi.getCentroidX() * pixelWidthMicrons()
						: roi.getCentroidY() * pixelHeightMicrons();
			} else {
				return type == CentroidType.X
						? roi.getCentroidX()
						: roi.getCentroidY();
			}
		}

		@Override
		public Binding<Number> createMeasurement(PathObject pathObject) {
			return new DoubleBinding() {
				@Override
				protected double computeValue() {
					return getCentroid(pathObject.getROI());
				}
				
			};
		}
		
	}
	
	
	static class MissingTMACoreMeasurementBuilder extends StringMeasurementBuilder {

		@Override
		public String getName() {
			return "Missing core";
		}

		@Override
		protected String getMeasurementValue(PathObject pathObject) {
			if (pathObject instanceof TMACoreObject)
				return ((TMACoreObject)pathObject).isMissing() ? "True" : "False";
			return null;
		}
		
	}
	
	
	
	static class StringMetadataMeasurementBuilder extends StringMeasurementBuilder {
		
		private String name;
		
		StringMetadataMeasurementBuilder(final String name) {
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getMeasurementValue(PathObject pathObject) {
			if (pathObject instanceof MetadataStore) {
				MetadataStore store = (MetadataStore)pathObject;
				return store.getMetadataString(name);
			}
			return null;
		}
		
	}
	
	/**
	 * Get the displayed name of the first TMACoreObject that is an ancestor of the supplied object.
	 */
	static class TMACoreNameMeasurementBuilder extends StringMeasurementBuilder {
		
		@Override
		public String getName() {
			return "TMA Core";
		}
		
		private TMACoreObject getAncestorTMACore(PathObject pathObject) {
			if (pathObject == null)
				return null;
			if (pathObject instanceof TMACoreObject)
				return (TMACoreObject)pathObject;
			return getAncestorTMACore(pathObject.getParent());
		}

		@Override
		public String getMeasurementValue(PathObject pathObject) {
			TMACoreObject core = getAncestorTMACore(pathObject);
			if (core == null)
				return null;
			return core.getDisplayedName();
		}
		
	}
	
	
	/**
	 * Get the displayed name of the parent of this object.
	 */
	static class ImageNameMeasurementBuilder extends StringMeasurementBuilder {
		
		private ImageData<?> imageData;
		
		ImageNameMeasurementBuilder(final ImageData<?> imageData) {
			this.imageData = imageData;
		}
		
		@Override
		public String getName() {
			return "Image";
		}
		
		@Override
		public String getMeasurementValue(PathObject pathObject) {
			if (imageData == null)
				return null;
			var hierarchy = imageData.getHierarchy();
			if (PathObjectTools.hierarchyContainsObject(hierarchy, pathObject))
				return imageData.getServer().getMetadata().getName();
			return null;
		}
		
	}
	
	
	/**
	 * Get the displayed name of the parent of this object.
	 */
	static class ParentNameMeasurementBuilder extends StringMeasurementBuilder {
		
		@Override
		public String getName() {
			return "Parent";
		}
		
		@Override
		public String getMeasurementValue(PathObject pathObject) {
			PathObject parent = pathObject == null ? null : pathObject.getParent();
			if (parent == null)
				return null;
			return parent.getDisplayedName();
		}
		
	}
	
	
	
	static abstract class NumericMeasurementBuilder implements MeasurementBuilder<Number> {
		
		public double computeValue(final PathObject pathObject) {
			// TODO: Flip this around!  Create binding from value, not value from binding...
			try {
				var val = createMeasurement(pathObject).getValue();
				if (val == null)
					return Double.NaN;
				else
					return val.doubleValue();
			} catch (NullPointerException e) {
				return Double.NaN;
			}
		}
		
		public String getStringValue(final PathObject pathObject, final int decimalPlaces) {
			double val = computeValue(pathObject);
			if (Double.isNaN(val))
				return "NaN";
			if (decimalPlaces == 0)
				return Integer.toString((int)(val + 0.5));
			int dp = decimalPlaces;
			// Format in some sensible way
			if (decimalPlaces < 0) {
				if (val > 1000)
					dp = 1;
				else if (val > 10)
					dp = 2;
				else if (val > 1)
					dp = 3;
				else
					dp = 4;
			}
			return GeneralTools.formatNumber(val, dp);
		}
		
	}
	
	
	
	
	static abstract class RoiMeasurementBuilder extends NumericMeasurementBuilder {
		
		private ImageData<?> imageData;
		
		RoiMeasurementBuilder(final ImageData<?> imageData) {
			this.imageData = imageData;
		}
		
		boolean hasPixelSizeMicrons() {
			return imageData != null && imageData.getServer() != null && imageData.getServer().getPixelCalibration().hasPixelSizeMicrons();
		}

		double pixelWidthMicrons() {
			if (hasPixelSizeMicrons())
				return imageData.getServer().getPixelCalibration().getPixelWidthMicrons();
			return Double.NaN;
		}

		double pixelHeightMicrons() {
			if (hasPixelSizeMicrons())
				return imageData.getServer().getPixelCalibration().getPixelHeightMicrons();
			return Double.NaN;
		}
		
	}
	
	
	static class AreaMeasurementBuilder extends RoiMeasurementBuilder {
		
		AreaMeasurementBuilder(final ImageData<?> imageData) {
			super(imageData);
		}
		
		@Override
		public String getName() {
			return hasPixelSizeMicrons() ? "Area " + GeneralTools.micrometerSymbol() + "^2" : "Area px^2";
		}
		
		@Override
		public Binding<Number> createMeasurement(final PathObject pathObject) {
			return new DoubleBinding() {
				@Override
				protected double computeValue() {
					ROI roi = pathObject.getROI();
					if (roi == null || !roi.isArea())
						return Double.NaN;
					if (hasPixelSizeMicrons())
						return roi.getScaledArea(pixelWidthMicrons(), pixelHeightMicrons());
					return roi.getArea();
				}
				
			};
		}
		
	}
	
	
	static class PerimeterMeasurementBuilder extends RoiMeasurementBuilder {
		
		PerimeterMeasurementBuilder(final ImageData<?> imageData) {
			super(imageData);
		}
		
		@Override
		public String getName() {
			return hasPixelSizeMicrons() ? "Perimeter " + GeneralTools.micrometerSymbol() : "Perimeter px";
		}
		
		@Override
		public Binding<Number> createMeasurement(final PathObject pathObject) {
			return new DoubleBinding() {
				@Override
				protected double computeValue() {
					ROI roi = pathObject.getROI();
					if (roi == null || !roi.isArea())
						return Double.NaN;
					if (hasPixelSizeMicrons())
						return roi.getScaledLength(pixelWidthMicrons(), pixelHeightMicrons());
					return roi.getLength();
				}
				
			};
		}
		
	}
	
	
//	static class MaxDiameterMeasurementBuilder extends RoiMeasurementBuilder {
//		
//		MaxDiameterMeasurementBuilder(final ImageData<?> imageData) {
//			super(imageData);
//		}
//		
//		@Override
//		public String getName() {
//			return hasPixelSizeMicrons() ? "Max diameter " + GeneralTools.micrometerSymbol() : "Max diameter px";
//		}
//		
//		@Override
//		public Binding<Number> createMeasurement(final PathObject pathObject) {
//			return new DoubleBinding() {
//				@Override
//				protected double computeValue() {
//					ROI roi = pathObject.getROI();
//					if (hasPixelSizeMicrons())
//						return roi.getMaxDiameter();
//					else
//						return roi.getScaledMaxDiameter(pixelWidthMicrons(), pixelHeightMicrons());
//				}
//				
//			};
//		}
//		
//	}
//	
//	
//	static class MinDiameterMeasurementBuilder extends RoiMeasurementBuilder {
//		
//		MinDiameterMeasurementBuilder(final ImageData<?> imageData) {
//			super(imageData);
//		}
//		
//		@Override
//		public String getName() {
//			return hasPixelSizeMicrons() ? "Min diameter " + GeneralTools.micrometerSymbol() : "Min diameter px";
//		}
//		
//		@Override
//		public Binding<Number> createMeasurement(final PathObject pathObject) {
//			return new DoubleBinding() {
//				@Override
//				protected double computeValue() {
//					ROI roi = pathObject.getROI();
//					if (hasPixelSizeMicrons())
//						return roi.getMinDiameter();
//					else
//						return roi.getScaledMinDiameter(pixelWidthMicrons(), pixelHeightMicrons());
//				}
//				
//			};
//		}
//		
//	}
	
	
	static class LineLengthMeasurementBuilder extends RoiMeasurementBuilder {
		
		LineLengthMeasurementBuilder(final ImageData<?> imageData) {
			super(imageData);
		}
		
		@Override
		public String getName() {
			return hasPixelSizeMicrons() ? "Length " + GeneralTools.micrometerSymbol() : "Length px";
		}
		
		@Override
		public Binding<Number> createMeasurement(final PathObject pathObject) {
			return new DoubleBinding() {
				@Override
				protected double computeValue() {
					ROI roi = pathObject.getROI();
					if (roi == null || !roi.isLine())
						return Double.NaN;
					if (hasPixelSizeMicrons())
						return roi.getScaledLength(pixelWidthMicrons(), pixelHeightMicrons());
					return roi.getLength();
				}
				
			};
		}
		
	}
	
	
	static class NumPointsMeasurementBuilder extends NumericMeasurementBuilder {
		
		@Override
		public String getName() {
			return "Num points";
		}
		
		@Override
		public Binding<Number> createMeasurement(final PathObject pathObject) {
			return new DoubleBinding() {
				@Override
				protected double computeValue() {
					ROI roi = pathObject.getROI();
					if (roi == null || !roi.isPoint())
						return Double.NaN;
					return roi.getNumPoints();
				}
				
			};
		}
		
	}
	
	
	
	
	
	/**
	 * Cache to store the number of descendant detection objects with a particular PathClass.
	 * <p>
	 * (The parent is included in any count if it's a detection object... but it's expected not to be.
	 * Rather, this is intended for counting the descendants of annotations or TMA cores.)
	 *
	 */
	static class DetectionPathClassCounts {
		
		private Map<PathClass, Integer> counts = new HashMap<>();
		
		/**
		 * Create a structure to count detections inside a specified parent.
		 * 
		 * @param parentObject the parent object.
		 */
		DetectionPathClassCounts(final PathObjectHierarchy hierarchy, final PathObject parentObject) {
//			for (PathObject child : PathObjectTools.getFlattenedObjectList(parentObject, null, true)) {
			Collection<PathObject> pathObjects;
			if (parentObject.isRootObject())
				pathObjects = hierarchy.getDetectionObjects();
			else
				pathObjects = hierarchy.getObjectsForROI(PathDetectionObject.class, parentObject.getROI());
			
			for (PathObject child : pathObjects) {
				if (child == parentObject || !child.isDetection())
					continue;
				PathClass pathClass = child.getPathClass();
//				if (pathClass == null)
//					continue;
				Integer count = counts.get(pathClass);
				if (count == null)
					counts.put(pathClass, Integer.valueOf(1));
				else
					counts.put(pathClass, Integer.valueOf(count.intValue() + 1));
			}
		}
		
		public int getDirectCount(final PathClass pathClass) {
			return counts.getOrDefault(pathClass, Integer.valueOf(0));
		}
		
		public int getCountForAncestor(final Predicate<PathClass> predicate, final PathClass ancestor) {
			int count = 0;
			for (Entry<PathClass, Integer> entry : counts.entrySet()) {
				if (ancestor == null) {
					if (predicate.test(entry.getKey()) && entry.getKey().getParentClass() == null)
						count += entry.getValue();
				} else if (ancestor.isAncestorOf(entry.getKey()) && predicate.test(entry.getKey()))
					count += entry.getValue();
			}
			return count;
		}
		
		public int getCountForAncestor(final Predicate<PathClass> predicate, final PathClass... ancestors) {
			int count = 0;
			for (PathClass ancestor : ancestors)
				count += getCountForAncestor(predicate, ancestor);
			return count;
		}
		
		public int getCountForAncestor(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> true, ancestors);
		}
		
		public int getOnePlus(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> PathClassTools.isOnePlus(pathClass), ancestors);
		}
		
		public int getTwoPlus(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> PathClassTools.isTwoPlus(pathClass), ancestors);
		}

		public int getThreePlus(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> PathClassTools.isThreePlus(pathClass), ancestors);
		}

		public int getNegative(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> PathClassTools.isNegativeClass(pathClass), ancestors);
		}

		public int getPositive(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> PathClassTools.isPositiveOrGradedIntensityClass(pathClass), ancestors);
		}
		
		public double getHScore(final PathClass... ancestors) {
			double plus1 = 0;
			double plus2 = 0;
			double plus3 = 0;
			double negative = 0;
			for (PathClass ancestor : ancestors) {
				plus1 += getOnePlus(ancestor);
				plus2 += getTwoPlus(ancestor);
				plus3 += getThreePlus(ancestor);
				negative += getNegative(ancestor);
			}
			return (plus1 * 1 + plus2 * 2 + plus3 * 3) / (plus1 + plus2 + plus3 + negative) * 100;
		}
		
		public int getAllredIntensity(final double minProportion, final PathClass... ancestors) {
			int proportionScore = getAllredProportion(minProportion, ancestors);
			int intensityScore = 0;
			if (proportionScore > 0) {
				int nPositive = getPositive(ancestors);
				double meanIntensity = (getOnePlus(ancestors) + getTwoPlus(ancestors)*2. + getThreePlus(ancestors)*3.) / nPositive;
				if (meanIntensity > 7./3.)
					intensityScore = 3;
				else if (meanIntensity > 5./3.)
					intensityScore = 2;
				else
					intensityScore = 1;
			}			
			return intensityScore;
		}
		
		public int getAllredProportion(final double minProportion, final PathClass... ancestors) {
			// Compute Allred score
			double proportion = getPositivePercentage(ancestors)/100.0;
			if (proportion < minProportion)
				return 0;
			int proportionScore;
			if (proportion >= 2./3.)
				proportionScore = 5;
			else if (proportion >= 1./3.)
				proportionScore = 4;
			else if (proportion >= 0.1)
				proportionScore = 3;
			else if (proportion >= 0.01)
				proportionScore = 2;
			else if (proportion > 0) // 'Strict' Allred scores accepts anything above 0 as positive... but minProportion may already have kicked in
					proportionScore = 1;
			else
				proportionScore = 0;
			return proportionScore;
		}
		
		public int getAllredScore(final double minProportion, final PathClass... ancestors) {
			return getAllredIntensity(minProportion, ancestors) + getAllredProportion(minProportion, ancestors);
		}
		
		/**
		 * Get the percentage of positive detections, considering only descendants of one or more
		 * specified classes.
		 * 
		 * @param ancestors
		 * @return
		 */
		public double getPositivePercentage(final PathClass... ancestors) {
			double positive = 0;
			double negative = 0;
			for (PathClass ancestor : ancestors) {
				positive += getPositive(ancestor);
				negative += getNegative(ancestor);
			}
			return positive / (positive + negative) * 100;
		}

	}

	
	
	static class PixelClassifierMeasurementBuilder extends NumericMeasurementBuilder {
		
		private PixelClassificationMeasurementManager manager;
		private String name;
		
		PixelClassifierMeasurementBuilder(PixelClassificationMeasurementManager manager, String name) {
			this.manager = manager;
			this.name = name;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public Binding<Number> createMeasurement(PathObject pathObject) {
			// Return only measurements that can be generated rapidly from cached tiles
			return Bindings.createObjectBinding(() -> manager.getMeasurementValue(pathObject, name, true));
		}
		
	}




	@Override
	public List<String> getAllNames() {
		return new ArrayList<>(fullList);
	}

	@Override
	public String getStringValue(PathObject pathObject, String column) {
		return getStringValue(pathObject, column, -1);
	}

	@Override
	public String getStringValue(PathObject pathObject, String column, int decimalPlaces) {
		MeasurementBuilder<?> builder = builderMap.get(column);
		if (builder instanceof StringMeasurementBuilder) {
			return ((StringMeasurementBuilder)builder).getMeasurementValue(pathObject);
		}
		else if (builder instanceof NumericMeasurementBuilder)
			return ((NumericMeasurementBuilder)builder).getStringValue(pathObject, decimalPlaces);
		
		if (pathObject == null) {
			logger.warn("Requested measurement {} for null object! Returned empty String.", column);
			return "";
		}
		double val = pathObject.getMeasurementList().getMeasurementValue(column);
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
