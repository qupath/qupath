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

import java.util.Collection;

import javafx.scene.image.Image;
import qupath.lib.gui.models.ObservableMeasurementTableData;
import qupath.lib.objects.TMACoreObject;

/**
 * A TMAEntry acting as a wrapper for a TMACoreObject.
 * 
 * @author Pete Bankhead
 *
 */
public class TMAObjectEntry implements TMAEntry {
		
		private ObservableMeasurementTableData data;
		private TMACoreObject core;

		public TMAObjectEntry(ObservableMeasurementTableData data, String serverPath, TMACoreObject core) {
//			super(serverPath, null, null, null, false);
			this.core = core;
			this.data = data;			
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
			return data.getStringValue(core, name);
		}
		
		@Override
		public void putMetadata(String name, String value) {
			core.putMetadataValue(name, value);
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
		}

		@Override
		public String getName() {
			return core.getName();
		}

		@Override
		public Image getImage(int maxWidth) {
//			if (imagePath != null) {
//				Image img = imageMap.get(imagePath);
//				if (img != null)
//					return img;
//				try {
//					img = new Image(new File(imagePath).toURI().toURL().toString(), false);
//					imageMap.put(imagePath, img);
//					return img;
//				} catch (MalformedURLException e) {
//					logger.error("Cannot show image: {}", e);
//				}
//			}
			return null;
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
		}


		@Override
		public String getImageName() {
			// TODO Auto-generated method stub
			return null;
		}


		@Override
		public boolean hasImage() {
			return false;
		}


		@Override
		public boolean hasOverlay() {
			return false;
		}
		
		
	}