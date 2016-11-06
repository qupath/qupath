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

package qupath.lib.gui.models;

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
import qupath.lib.classifiers.PathClassificationLabellingHelper;
import qupath.lib.common.GeneralTools;
import qupath.lib.geom.Point2;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.MetadataStore;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassFactory;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.roi.AreaROI;
import qupath.lib.roi.PolygonROI;
import qupath.lib.roi.interfaces.PathArea;
import qupath.lib.roi.interfaces.PathLine;
import qupath.lib.roi.interfaces.PathPoints;
import qupath.lib.roi.interfaces.ROI;

/**
 * A table data model to supply observable measurements of PathObjects.
 * 
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
	
	
	public void setImageData(final ImageData<?> imageData, final Collection<? extends PathObject> pathObjects) {
		this.imageData = imageData;
		list.setAll(pathObjects);
		updateMeasurementList();
	}
	
	
	private ImageData<?> getImageData() {
		return imageData;
	}
	
	
	public void updateMeasurementList() {
		
//		PathPrefs.setAllredMinPercentagePositive(0);
		
		builderMap.clear();
		
		// Include the object displayed name
		builderMap.put("Name", new ObjectNameMeasurementBuilder());
		
		// Check if we have any annotations / TMA cores
		boolean containsDetections = false;
		boolean containsAnnotations = false;
		boolean containsParentAnnotations = false;
		boolean containsTMACores = false;
		for (PathObject temp : list) {
			if (temp instanceof PathAnnotationObject) {
				if (temp.hasChildren())
					containsParentAnnotations = true;
				containsAnnotations = true;
			} else if (temp instanceof TMACoreObject) {
				containsTMACores = true;
			} else if (temp instanceof PathDetectionObject) {
				containsDetections = true;
			}
		}
		
		// Include the class
		if (containsAnnotations || containsDetections) {
			builderMap.put("Class", new PathClassMeasurementBuilder());
		}

		// Include the TMA missing status, if appropriate
		if (containsTMACores) {
			builderMap.put("Missing", new MissingTMACoreMeasurementBuilder());
		}
		
		if (containsAnnotations || containsDetections) {
			builderMap.put("ROI", new ROINameMeasurementBuilder());
		}

		// If we have metadata, store it
		Set<String> metadataNames = new LinkedHashSet<>();
		metadataNames.addAll(builderMap.keySet());
		for (PathObject pathObject : list) {
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
		Collection<String> features = PathClassificationLabellingHelper.getAvailableFeatures(list);
		
		// Add derived measurements if we don't have only detections
		if (containsParentAnnotations || containsTMACores) {
			manager = new DerivedMeasurementManager(getImageData(), containsAnnotations);
			for (MeasurementBuilder<?> builder : manager.getMeasurementBuilders()) {
				builderMap.put(builder.getName(), builder);
				features.add(builder.getName());
			}
			
		}
		
		// If we have an annotation, add shape features
		if (containsAnnotations) {
			boolean anyPoints = false;
			boolean anyAreas = false;
			boolean anyLines = false;
			boolean anyPolygons = false;
			for (PathObject pathObject : list) {
				if (!pathObject.isAnnotation())
					continue;
				if (pathObject.isPoint())
					anyPoints = true;
				if (pathObject.getROI() instanceof PathArea)
					anyAreas = true;
				if (pathObject.getROI() instanceof PathLine)
					anyLines = true;
				if (pathObject.getROI() instanceof PolygonROI)
					anyPolygons = true;
			}
			// Add point count, if needed
			if (anyPoints) {
				MeasurementBuilder<?> builder = new NumPointsMeasurementBuilder();
				builderMap.put(builder.getName(), builder);
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
			if (anyPolygons) {
				MeasurementBuilder<?> builder = new PolygonMaxLengthMeasurementBuilder(imageData);
				builderMap.put(builder.getName(), builder);
				features.add(builder.getName());
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
	
	
	public void setPredicate(Predicate<? super PathObject> predicate) {
		filterList.setPredicate(predicate);
	}
	
	
	public void refreshEntries() {
		// Clear the cached map to force updates
		if (manager != null)
			manager.map.clear();
	}
	
	
	public Binding<Number> createNumericMeasurement(final PathObject pathObject, final String column) {
		MeasurementBuilder<?> builder = builderMap.get(column);
		if (builder == null)
			return new ObservableMeasurement(pathObject, column);
		else if (builder instanceof NumericMeasurementBuilder)
			return ((NumericMeasurementBuilder)builder).createMeasurement(pathObject);
		else
			throw new IllegalArgumentException(column + " does not represent a numeric measurement!");
	}
	
	
	public Binding<String> createStringMeasurement(final PathObject pathObject, final String column) {
		MeasurementBuilder<?> builder = builderMap.get(column);
		if (builder instanceof StringMeasurementBuilder)
			return ((StringMeasurementBuilder)builder).createMeasurement(pathObject);
		else
			throw new IllegalArgumentException(column + " does not represent a String measurement!");
	}

	
	public boolean isStringMeasurement(final String name) {
		return builderMap.get(name) instanceof StringMeasurementBuilder;
	}
	
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
	public ObservableList<PathObject> getEntries() {
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
			
			Set<PathClass> pathClasses = PathClassificationLabellingHelper.getRepresentedPathClasses(imageData.getHierarchy(), PathDetectionObject.class);
			
			// Ensure that any base classes are present
			for (PathClass pathClass : pathClasses.toArray(new PathClass[0]))
				pathClasses.add(pathClass.getBaseClass());

			Set<PathClass> parentIntensityClasses = new LinkedHashSet<>();
			Set<PathClass> parentPositiveNegativeClasses = new LinkedHashSet<>();
			for (PathClass pathClass : pathClasses) {
				if (PathClassFactory.isDefaultIntensityClass(pathClass)) {
					parentIntensityClasses.add(pathClass.getParentClass());
					parentPositiveNegativeClasses.add(pathClass.getParentClass());
				}
				else if (PathClassFactory.isPositiveClass(pathClass) || PathClassFactory.isNegativeClass(pathClass))
					parentPositiveNegativeClasses.add(pathClass.getParentClass());
			}
			
			// We can compute counts for any PathClass that is represented
			List<PathClass> pathClassList = new ArrayList<>(pathClasses);
			Collections.sort(pathClassList);
			for (PathClass pathClass : pathClassList) {
				builders.add(new ClassCountMeasurementBuilder(pathClass));
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
				PathClass[] parentIntensityClassesArray = parentIntensityClasses.toArray(new PathClass[0]);
				builders.add(new HScoreMeasurementBuilder(parentIntensityClassesArray));
				builders.add(new AllredProportionMeasurementBuilder(parentIntensityClassesArray));
				builders.add(new AllredIntensityMeasurementBuilder(parentIntensityClassesArray));
				builders.add(new AllredMeasurementBuilder(parentIntensityClassesArray));
			}
			
			// Add density measurements
			if (containsAnnotations) {
				for (PathClass pathClass : pathClassList) {
					if (PathClassFactory.isPositiveClass(pathClass))
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
			
			public ClassCountMeasurement(final PathObject pathObject, final PathClass pathClass) {
				this.pathObject = pathObject;
				this.pathClass = pathClass;
			}

			@Override
			protected int computeValue() {
				DetectionPathClassCounts counts = map.get(pathObject);
				if (counts == null) {
					counts = new DetectionPathClassCounts(pathObject);
					map.put(pathObject, counts);
				}
				return counts.getCountForAncestor(pathClass);
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
				DetectionPathClassCounts counts = map.get(pathObject);
				if (counts == null) {
					counts = new DetectionPathClassCounts(pathObject);
					map.put(pathObject, counts);
				}
				int n = counts.getCountForAncestor(pathClass);
				ROI roi = pathObject.getROI();
				if (roi instanceof PathArea) {
					double pixelWidth = 1;
					double pixelHeight = 1;
					if (server != null && server.hasPixelSizeMicrons()) {
						pixelWidth = server.getPixelWidthMicrons() / 1000;
						pixelHeight = server.getPixelHeightMicrons() / 1000;
					}
					return n / (((PathArea)roi).getScaledArea(pixelWidth, pixelHeight));
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
					counts = new DetectionPathClassCounts(pathObject);
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
					counts = new DetectionPathClassCounts(pathObject);
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
					counts = new DetectionPathClassCounts(pathObject);
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
					counts = new DetectionPathClassCounts(pathObject);
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
					counts = new DetectionPathClassCounts(pathObject);
					map.put(pathObject, counts);
				}
				return counts.getPositivePercentage(pathClasses);
			}
			
		}
		
		
		
		
		class ClassCountMeasurementBuilder extends NumericMeasurementBuilder {
			
			private PathClass pathClass;
			
			ClassCountMeasurementBuilder(final PathClass pathClass) {
				this.pathClass = pathClass;
			}
			
			@Override
			public String getName() {
				return "Num " + pathClass.toString();
			}
			
			@Override
			public Binding<Number> createMeasurement(final PathObject pathObject) {
				return new ClassCountMeasurement(pathObject, pathClass);
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
				if (server != null && server.hasPixelSizeMicrons())
					return String.format("Num %s per mm^2", pathClass.toString());
//					return String.format("Num %s per %s^2", pathClass.toString(), GeneralTools.micrometerSymbol());
				else
					return String.format("Num %s per px^2", pathClass.toString());
			}
			
			@Override
			public Binding<Number> createMeasurement(final PathObject pathObject) {
				// Only return density measurements for annotations
				if (!pathObject.isAnnotation())
					return Bindings.createDoubleBinding(() -> Double.NaN);
				return new ClassDensityMeasurementPerMM(server, pathObject, pathClass);
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
				double minPercentage = PathPrefs.getAllredMinPercentagePositive();
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
				double minPercentage = PathPrefs.getAllredMinPercentagePositive();
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
				double minPercentage = PathPrefs.getAllredMinPercentagePositive();
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
			return pathObject.hasROI() ? pathObject.getROI().getROIType() : null;
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
	
	
	static abstract class NumericMeasurementBuilder implements MeasurementBuilder<Number> {
		
		public double computeValue(final PathObject pathObject) {
			// TODO: Flip this around!  Create binding from value, not value from binding...
			return createMeasurement(pathObject).getValue().doubleValue();
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
			return GeneralTools.getFormatter(dp).format(val);
		}
		
	}
	
	
	
	
	static abstract class RoiMeasurementBuilder extends NumericMeasurementBuilder {
		
		private ImageData<?> imageData;
		
		RoiMeasurementBuilder(final ImageData<?> imageData) {
			this.imageData = imageData;
		}
		
		boolean hasPixelSizeMicrons() {
			return imageData != null && imageData.getServer() != null && imageData.getServer().hasPixelSizeMicrons();
		}

		double pixelWidthMicrons() {
			if (hasPixelSizeMicrons())
				return imageData.getServer().getPixelWidthMicrons();
			return Double.NaN;
		}

		double pixelHeightMicrons() {
			if (hasPixelSizeMicrons())
				return imageData.getServer().getPixelHeightMicrons();
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
					if (!(roi instanceof PathArea))
						return Double.NaN;
					if (hasPixelSizeMicrons())
						return ((PathArea)roi).getScaledArea(pixelWidthMicrons(), pixelHeightMicrons());
					return ((PathArea)roi).getArea();
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
					if (!(roi instanceof PathArea))
						return Double.NaN;
					if (hasPixelSizeMicrons())
						return ((PathArea)roi).getScaledPerimeter(pixelWidthMicrons(), pixelHeightMicrons());
					return ((PathArea)roi).getPerimeter();
				}
				
			};
		}
		
	}
	
	
	static class PolygonMaxLengthMeasurementBuilder extends RoiMeasurementBuilder {
		
		PolygonMaxLengthMeasurementBuilder(final ImageData<?> imageData) {
			super(imageData);
		}
		
		@Override
		public String getName() {
			return hasPixelSizeMicrons() ? "Max length " + GeneralTools.micrometerSymbol() : "Max length px";
		}
		
		@Override
		public Binding<Number> createMeasurement(final PathObject pathObject) {
			return new DoubleBinding() {
				@Override
				protected double computeValue() {
					ROI roi = pathObject.getROI();
					List<Point2> points;
					if (roi instanceof PolygonROI)
						points = ((PolygonROI)roi).getPolygonPoints();
					else if (roi instanceof AreaROI)
						points = ((AreaROI)roi).getPolygonPoints();
					else
						return Double.NaN;
					double xScale = hasPixelSizeMicrons() ? pixelWidthMicrons() : 1;
					double yScale = hasPixelSizeMicrons() ? pixelHeightMicrons() : 1;
					double maxLengthSq = 0;
					for (int i = 0; i < points.size(); i++) {
						Point2 pi = points.get(i);
						for (int j = i+1; j < points.size(); j++) {
							Point2 pj = points.get(j);
							double dx = (pi.getX() - pj.getX()) * xScale;
							double dy = (pi.getY() - pj.getY()) * yScale;
							maxLengthSq = Math.max(maxLengthSq, dx*dx + dy*dy);
						}
					}
					return Math.sqrt(maxLengthSq);
				}
				
			};
		}
		
	}
	
	
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
					if (!(roi instanceof PathLine))
						return Double.NaN;
					if (hasPixelSizeMicrons())
						return ((PathLine)roi).getScaledLength(pixelWidthMicrons(), pixelHeightMicrons());
					return ((PathLine)roi).getLength();
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
					if (!(roi instanceof PathPoints))
						return Double.NaN;
					return ((PathPoints)roi).getNPoints();
				}
				
			};
		}
		
	}
	
	
	
	
	
	/**
	 * Cache to store the number of descendant detection objects with a particular PathClass.
	 * 
	 * (The parent is included in any count if it's a detection object... but it's expected not to be.
	 * Rather, this is intended for counting the descendants of annotations or TMA cores.)
	 *
	 */
	static class DetectionPathClassCounts {
		
		private Map<PathClass, Integer> counts = new HashMap<>();
		
		/**
		 * @param pathObject The parent object.  PathClasses will be counted for descendant detection objects only.
		 */
		DetectionPathClassCounts(final PathObject parentObject) {
			for (PathObject child : PathObjectTools.getFlattenedObjectList(parentObject, null, true)) {
				if (!(child instanceof PathDetectionObject))
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
			return getCountForAncestor(pathClass -> PathClassFactory.isOnePlus(pathClass), ancestors);
		}
		
		public int getTwoPlus(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> PathClassFactory.isTwoPlus(pathClass), ancestors);
		}

		public int getThreePlus(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> PathClassFactory.isThreePlus(pathClass), ancestors);
		}

		public int getNegative(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> PathClassFactory.isNegativeClass(pathClass), ancestors);
		}

		public int getPositive(final PathClass... ancestors) {
			return getCountForAncestor(pathClass -> PathClassFactory.isPositiveOrPositiveIntensityClass(pathClass), ancestors);
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
		 * Get the percentage of positive detections, considering only descendents of one or more
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





	@Override
	public ReadOnlyListWrapper<String> getAllNames() {
		return new ReadOnlyListWrapper<>(fullList);
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
		
		double val = pathObject.getMeasurementList().getMeasurementValue(column);
		if (Double.isNaN(val))
			return "NaN";
		return GeneralTools.getFormatter(4).format(val);
	}

	public ReadOnlyListWrapper<String> getMetadataNames() {
		return new ReadOnlyListWrapper<>(metadataList);
	}	
	
	
}