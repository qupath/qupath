/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.gui.tma;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.beans.value.ObservableBooleanValue;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import qupath.lib.gui.measure.ObservableMeasurementTableData;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

class TMAEntries {
	
	/**
	 * Interface to define a TMAEntry for display in the summary viewer.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	public static interface TMAEntry {

		/**
		 * Get a measurement value.
		 * 
		 * If isMissing() returns true, this always returns NaN.
		 * 
		 * Otherwise it returns whichever value is stored (which may or may not be NaN).
		 * 
		 * @param name
		 * @return
		 */
		Number getMeasurement(String name);

		/**
		 * Get a measurement as a double value.
		 * 
		 * If getMeasurement returns null, this will give NaN.
		 * Otherwise, it will return getMeasurement(name).doubleValue();
		 * @param name 
		 * @return 
		 */
		double getMeasurementAsDouble(String name);

		Collection<String> getMeasurementNames();

		void putMeasurement(String name, Number number);

		Collection<String> getMetadataNames();

		String getMetadataValue(String name);
		
		void putMetadata(String name, String value);

		boolean isMissing();
		
		void setMissing(boolean missing);

		String getComment();

		void setComment(String comment);

		String getName();

		String getImageName();

		/**
		 * Returns true if this entry has (or thinks it has) an image.
		 * It doesn't actually try to load the image, which may be expensive - 
		 * and therefore there can be no guarantee the loading will succeed when getImage() is called.
		 * @return
		 */
		boolean hasImage();

		/**
		 * Returns true if this entry has (or thinks it has) an overlay image.
		 * It doesn't actually try to load the image, which may be expensive - 
		 * and therefore there can be no guarantee the loading will succeed when getOverlay() is called.
		 * @return
		 */
		boolean hasOverlay();

		Image getImage(int maxWidth);

		Image getOverlay(int maxWidth);

		@Override
		String toString();

	}
	
	
	public static TMAEntry createDefaultTMAEntry(String imageName, String imagePath, String overlayPath, String coreName, boolean isMissing) {
		return new DefaultTMAEntry(imageName, imagePath, overlayPath, coreName, isMissing);
	}
	
	
	public static TMAEntry createTMAObjectEntry(ImageData<BufferedImage> imageData, ObservableMeasurementTableData data, TMACoreObject core) {
		return new TMAObjectEntry(imageData, data, core);
	}
	
	
	public static TMAEntry createTMASummaryEntry(final ObservableValue<TMAEntries.MeasurementCombinationMethod> method, final ObservableBooleanValue skipMissing, final ObservableValue<Predicate<TMAEntry>> predicate) {
		return new TMASummaryEntry(method, skipMissing, predicate);
	}
	
	
	/**
	 * Default implementation of TMAEntry.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class DefaultTMAEntry implements TMAEntry {

		private static Logger logger = LoggerFactory.getLogger(DefaultTMAEntry.class);

		private String imageName;
		private String name;
		private String imagePath;
		private String overlayPath;
		private String comment;
		private boolean isMissing;
		private Map<String, String> metadata = new LinkedHashMap<>();
		private Map<String, Number> measurements = new LinkedHashMap<>();

		public DefaultTMAEntry(String imageName, String imagePath, String overlayPath, String coreName, boolean isMissing) {
			this.imageName = imageName;
			this.name = coreName;
			this.isMissing = isMissing;
			// Only store paths if they actually work...
			this.imagePath = imagePath != null && new File(imagePath).isFile() ? imagePath : null;
			this.overlayPath = overlayPath != null && new File(overlayPath).isFile() ? overlayPath : null;
		}

		@Override
		public Number getMeasurement(String name) {
			// There's an argument for not returning any measurement for a missing core...
			// but this can be problematic if 'valid' measurements are later imported
			//			if (isMissing())
			//				return Double.NaN;
			return measurements.get(name);
		}

		@Override
		public double getMeasurementAsDouble(String name) {
			Number measurement = getMeasurement(name);
			if (measurement == null)
				return Double.NaN;
			return measurement.doubleValue();
		}

		@Override
		public Collection<String> getMetadataNames() {
			return metadata.keySet();
		}

		@Override
		public String getMetadataValue(final String name) {
			return metadata.get(name);
		}

		@Override
		public void putMetadata(String name, String value) {
			metadata.put(name, value);
		}

		@Override
		public boolean isMissing() {
			return isMissing;
		}

		@Override
		public Collection<String> getMeasurementNames() {
			return measurements.keySet();
		}

		@Override
		public void putMeasurement(String name, Number number) {
			if (number == null)
				measurements.remove(name);
			else
				measurements.put(name, number);
		}

		@Override
		public String getComment() {
			return comment;
		}

		@Override
		public void setComment(String comment) {
			this.comment = comment.replace("\t", "  ").replace("\n", "  ");
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public String getImageName() {
			return imageName;
		}

		@Override
		public boolean hasImage() {
			return imagePath != null;
		}

		@Override
		public boolean hasOverlay() {
			return overlayPath != null;
		}

		@Override
		public Image getImage(int maxWidth) {
			if (imagePath == null)
				return null;
			try {
				return new Image(new File(imagePath).toURI().toURL().toString(), maxWidth, -1, true, false);
			} catch (MalformedURLException e) {
				logger.error("Cannot show image: {}", e);
			}
			return null;
		}

		@Override
		public Image getOverlay(int maxWidth) {
			if (overlayPath == null)
				return null;
			try {
				return new Image(new File(overlayPath).toURI().toURL().toString(), maxWidth, -1, true, false);
			} catch (MalformedURLException e) {
				logger.error("Cannot show overlay image: {}", e);
			}
			return null;
		}


		@Override
		public String toString() {
			return "TMA Entry: " + getName();
		}
		
		@Override
		public void setMissing(final boolean missing) {
			this.isMissing = missing;
		}

	}



	/**
	 * Methods that may be used to combine measurements when multiple cores are available.
	 */
	public static enum MeasurementCombinationMethod {
		MEAN, MEDIAN, MIN, MAX, RANGE;
	
		public double calculate(final List<TMAEntry> entries, final String measurementName, final boolean skipMissing) {
			switch (this) {
			case MAX:
				return TMASummaryEntry.getMaxMeasurement(entries, measurementName, skipMissing);
			case MEAN:
				return TMASummaryEntry.getMeanMeasurement(entries, measurementName, skipMissing);
			case MEDIAN:
				return TMASummaryEntry.getMedianMeasurement(entries, measurementName, skipMissing);
			case MIN:
				return TMASummaryEntry.getMinMeasurement(entries, measurementName, skipMissing);
			case RANGE:
				return TMASummaryEntry.getRangeMeasurement(entries, measurementName, skipMissing);
			default:
				return Double.NaN;
			}
		}
	
		@Override
		public String toString() {
			switch (this) {
			case MAX:
				return "Maximum";
			case MEAN:
				return "Mean";
			case MEDIAN:
				return "Median";
			case MIN:
				return "Minimum";
			case RANGE:
				return "Range";
			default:
				return null;
			}
		}
	
	}



	public static Set<String> survivalSet = new HashSet<>(
	Arrays.asList(
		TMACoreObject.KEY_OVERALL_SURVIVAL,
		TMACoreObject.KEY_OS_CENSORED,
		TMACoreObject.KEY_RECURRENCE_FREE_SURVIVAL,
		TMACoreObject.KEY_RFS_CENSORED,
		"Censored"
		)
	);
	
	
	
	
	/**
	 * A TMAEntry acting as a wrapper for a TMACoreObject.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class TMAObjectEntry implements TMAEntry {
		
		private static final Logger logger = LoggerFactory.getLogger(TMAObjectEntry.class);

		private ImageData<BufferedImage> imageData;

		private ObservableMeasurementTableData data;
		private TMACoreObject core;
		private double preferredDownsample;

		/**
		 * Create a TMAObjectEntry with a default preferredDownsample of 4.
		 * 
		 * @param imageData
		 * @param data
		 * @param core
		 */
		public TMAObjectEntry(ImageData<BufferedImage> imageData, ObservableMeasurementTableData data, TMACoreObject core) {
			this(imageData, data, core, 4);
		}
		
		/**
		 * Create a TMAObjectEntry.
		 * 
		 * @param imageData
		 * @param data
		 * @param core
		 * @param preferredDownsample the preferred amount to downsample any region requests used to return an image.  This is useful to limit 
		 * requests to avoid looking for an excessively-high resolution image.
		 */
		public TMAObjectEntry(ImageData<BufferedImage> imageData, ObservableMeasurementTableData data, TMACoreObject core, final double preferredDownsample) {
			this.imageData = imageData;
			this.core = core;
			this.data = data;
			this.preferredDownsample = preferredDownsample;
		}


		@Override
		public Number getMeasurement(String name) {
			return data.getNumericValue(core, name);
		}

		@Override
		public Collection<String> getMetadataNames() {
			return data.getMetadataNames();
		}

		@Override
		public String getMetadataValue(final String name) {
			return (String)core.getMetadataValue(name);
//			return data.getStringValue(core, name);
		}

		@Override
		public void putMetadata(String name, String value) {
			core.putMetadataValue(name, value);
			if (imageData != null)
				imageData.getHierarchy().fireObjectMeasurementsChangedEvent(this, Collections.singletonList(core));
			data.updateMeasurementList();
		}

		@Override
		public boolean isMissing() {
			return core.isMissing();
		}

		@Override
		public Collection<String> getMeasurementNames() {
			return data.getMeasurementNames();
		}

		@Override
		public void putMeasurement(String name, Number number) {
			core.getMeasurementList().putMeasurement(name, number == null ? Double.NaN : number.doubleValue());
			if (imageData != null)
				imageData.getHierarchy().fireObjectMeasurementsChangedEvent(this, Collections.singletonList(core));
			data.updateMeasurementList();
		}

		@Override
		public String getName() {
			return core.getName();
		}

		@Override
		public Image getImage(int maxWidth) {
			if (imageData == null)
				return null;
			
			ROI roi = core.getROI();
			
			// Don't request full resolution, necessarily
			double downsample = preferredDownsample;
			if (maxWidth > 0) {
				downsample = Math.max(roi.getBoundsWidth() / maxWidth, preferredDownsample);
			}
			
			try {
				BufferedImage img = imageData.getServer().readBufferedImage(
						RegionRequest.createInstance(imageData.getServerPath(), downsample, roi));
				return SwingFXUtils.toFXImage(img, null);
			} catch (IOException e) {
				logger.warn("Unable to return TMA core image for " + this, e);
				return null;
			}
		}

		@Override
		public Image getOverlay(int maxWidth) {
			//			if (overlayPath != null) {
			//				Image img = imageMap.get(overlayPath);
			//				if (img != null)
			//					return img;
			//				try {
			//					img = new Image(new File(overlayPath).toURI().toURL().toString(), false);
			//					imageMap.put(overlayPath, img);
			//					return img;
			//				} catch (MalformedURLException e) {
			//					logger.error("Cannot show image: {}", e);
			//				}
			//			}
			return null;
			//			if (overlayPath != null)
			//				return new Image(overlayPath, false);
			//			return null;
		}


		@Override
		public double getMeasurementAsDouble(String name) {
			Number measurement = getMeasurement(name);
			return measurement == null ? Double.NaN : measurement.doubleValue();
		}


		@Override
		public String getComment() {
			return core.getMetadataString("Comment");
		}


		@Override
		public void setComment(String comment) {
			core.putMetadataValue("Comment", comment);
			if (imageData != null)
				imageData.getHierarchy().fireObjectMeasurementsChangedEvent(this, Collections.singletonList(core));
			data.updateMeasurementList();
		}


		@Override
		public String getImageName() {
			return imageData == null ? null : ServerTools.getDisplayableImageName(imageData.getServer());
		}


		@Override
		public boolean hasImage() {
			return imageData != null;
		}


		@Override
		public boolean hasOverlay() {
			return false;
		}
		
		/**
//		 * Get a reference to the table data - can be necessary to force a refresh
//		 * @return
//		 */
//		ObservableMeasurementTableData getTableData() {
//			
//			return data;
//		}
		
		@Override
		public void setMissing(final boolean missing) {
			core.setMissing(missing);
			if (imageData != null)
				imageData.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(core));
			data.updateMeasurementList();
		}

	}

}