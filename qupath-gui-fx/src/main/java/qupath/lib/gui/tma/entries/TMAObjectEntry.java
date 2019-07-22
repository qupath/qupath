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


package qupath.lib.gui.tma.entries;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import qupath.lib.gui.models.ObservableMeasurementTableData;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

/**
 * A TMAEntry acting as a wrapper for a TMACoreObject.
 * 
 * @author Pete Bankhead
 *
 */
public class TMAObjectEntry implements TMAEntry {
	
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
//		return data.getStringValue(core, name);
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
//	 * Get a reference to the table data - can be necessary to force a refresh
//	 * @return
//	 */
//	ObservableMeasurementTableData getTableData() {
//		
//		return data;
//	}
	
	@Override
	public void setMissing(final boolean missing) {
		core.setMissing(missing);
		if (imageData != null)
			imageData.getHierarchy().fireObjectsChangedEvent(this, Collections.singleton(core));
		data.updateMeasurementList();
	}

}