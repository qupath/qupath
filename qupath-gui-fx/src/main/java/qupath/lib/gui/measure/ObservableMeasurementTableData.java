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
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import javafx.collections.ListChangeListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyListWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import qupath.lib.common.GeneralTools;
import qupath.lib.lazy.objects.MeasurementListValue;
import qupath.lib.lazy.objects.PathObjectLazyValues;
import qupath.lib.lazy.interfaces.LazyValue;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;

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
	public static final String NAME_OBJECT_ID = PathObjectLazyValues.OBJECT_ID.getName();

	private static final String KEY_PIXEL_LAYER = "PIXEL_LAYER";

	private ImageData<?> imageData;

	private final ObservableList<PathObject> list = FXCollections.observableArrayList();
	private final FilteredList<PathObject> filterList = new FilteredList<>(list);

	private final ObservableList<String> fullList = FXCollections.observableArrayList();
	private final ObservableList<String> metadataList = FXCollections.observableArrayList();
	private final ObservableList<String> measurementList = FXCollections.observableArrayList();

	private final Map<String, LazyValue<PathObject, ?>> builderMap = new LinkedHashMap<>();

	private PathObjectValueFactory factory;

	private static final ObservableList<PathObjectValueFactory> extraFactories = FXCollections.observableArrayList();

	public ObservableMeasurementTableData() {
		// TODO: Figure out how to handle extra factories
		// Don't add as a listener here - it can cause a massive memory leak!
		// Especially when scatterplots get involved...
//		extraFactories.addListener(
//				(ListChangeListener.Change<? extends PathObjectValueFactory> c) -> updateMeasurementList());
	}

	/**
	 * Add a custom value factory.
	 * This can append different custom measurements to the table.
	 * @param factory
	 * @see #removeFactory(PathObjectValueFactory)
	 */
	static void addFactory(PathObjectValueFactory factory) {
		extraFactories.add(factory);
	}

	/**
	 * Remove a custom value factory.
	 * @param factory
	 * @return true if a factory was removed, false if it had not previously been added
	 * @see #addFactory(PathObjectValueFactory)
	 */
	static boolean removeFactory(PathObjectValueFactory factory) {
		return extraFactories.remove(factory);
	}

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

		// We *might* have a factory set already (but we probably don't)
		PathObjectValueFactory factory = this.factory;

		if (factory == null) {
			// Create the default factory, adding any extras that are requested
			factory = new DefaultPathObjectValueFactoryBuilder()
					.includeImage(!PathPrefs.maskImageNamesProperty().get())
					.append(extraFactories)
					.build();
		} else if (!extraFactories.isEmpty()) {
			// Join any extra factories with the one we have set
			var allFactories = new ArrayList<>(extraFactories);
			allFactories.addFirst(factory);
			factory = PathObjectValueFactory.join(allFactories);
		}

		var wrapper = PathObjectListWrapper.create(imageData, list);

		List<String> metadataNames = new ArrayList<>();
		List<String> featureNames = new ArrayList<>();
		List<String> allNames = new ArrayList<>();

		for (var measurement : factory.createValues(wrapper)) {
			String name = measurement.getName();
			if (builderMap.containsKey(name)) {
				logger.warn("Duplicate measurement {} - entries will be dropped", name);
			} else {
				builderMap.put(measurement.getName(), measurement);
				// Before v0.6.0, we used a different approach & avoided adding ROI z/t/centroid
				// values here
				if (measurement.isNumeric())
					featureNames.add(name);
				else
					metadataNames.add(name);
				allNames.add(name);
			}
		}

		// Update all the lists, if necessary
		boolean changes = false;
		if (!metadataNames.equals(metadataList)) {
			changes = metadataList.setAll(metadataNames);
		}
		if (!featureNames.equals(measurementList))
			changes = measurementList.setAll(featureNames);
		if (changes) {
			fullList.setAll(allNames);
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
		return measurement != null && measurement.isString();
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
		return measurement == null || measurement.isNumeric();
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
			
			LazyValue<PathObject, ?> builder = builderMap.get(column);
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
		return getStringValue(pathObject, column, PathTableData.DEFAULT_DECIMAL_PLACES);
	}

	/**
	 * Get help text for a measurement if available, or null if no help text is found.
	 * @param column
	 * @return
	 */
	public String getHelpText(String column) {
		LazyValue<PathObject, ?> builder = builderMap.get(column);
		if (builder != null)
			return builder.getHelpText();
		else
			return "The measurement '" + column + "' from the object's measurement list, or NaN if the measurement is not found";
	}

	@Override
	public String getStringValue(PathObject pathObject, String column, int decimalPlaces) {
		LazyValue<PathObject, ?> builder = builderMap.get(column);
		// Temporary hack! This restores v0.6.0 behavior to be similar to previous versions, but it would be better
		// to suppose specifying the number of decimal places through a preference & apply it consistently
		if (decimalPlaces == PathTableData.DEFAULT_DECIMAL_PLACES && builder instanceof MeasurementListValue)
			decimalPlaces = -4;
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
