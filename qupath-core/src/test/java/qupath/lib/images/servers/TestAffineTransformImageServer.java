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

package qupath.lib.images.servers;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.geom.AffineTransform;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.geom.Point2;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;

@SuppressWarnings("javadoc")
public class TestAffineTransformImageServer {
	
	private final static Logger logger = LoggerFactory.getLogger(TestAffineTransformImageServer.class);

	@Test
	public void test() {
		
		logger.debug("Testing Affine transforms for pixel calibration (expect warnings!)");
		
		// Test for a range of pixel sizes, scalings and translations
		double[] pixelSizes = new double[] {0.5, 1.0, 2.2};
		double[] translate = new double[] {-10, 0, 10};
		double[] scales = new double[] {0.5, 1.0, 2.0};
		
//		translate = new double[] {0};
//		pixelSizes = new double[] {0.5};
//		scales = new double[] {2};
		
		double eps = 0.001;
		
		// Create a ROI to test measurements
		// Calibrated measurements should be the same before and after transformation
//		var roi = ROIs.createRectangleROI(100, 200, 150, 1000, ImagePlane.getDefaultPlane());
		var roi = ROIs.createPolygonROI(Arrays.asList(
				new Point2(100, 150),
				new Point2(400, 200),
				new Point2(55, 400)
				), ImagePlane.getDefaultPlane());
		
		
		for (double pixelWidth : pixelSizes) {
			for (double pixelHeight : pixelSizes) {
				
				PixelCalibration cal = new PixelCalibration.Builder()
						.pixelSizeMicrons(pixelWidth, pixelHeight)
						.build();
				
				double area = roi.getScaledArea(cal.getPixelWidth().doubleValue(), cal.getPixelHeight().doubleValue());
				
				PixelCalibration calUncalibrated = PixelCalibration.getDefaultInstance();
		
				for (double tx : translate) {
					for (double ty : translate) {
						for (double sx : scales) {
							for (double sy : scales) {
								
								var transform = new AffineTransform();
								transform.scale(sx, sy);
								transform.translate(tx, ty);
								
								var calTransformed = AffineTransformImageServer.updatePixelCalibration(cal, transform);
								assertEquals(calTransformed.getPixelWidthMicrons(), pixelWidth/sx, eps);
								assertEquals(calTransformed.getPixelHeightMicrons(), pixelHeight/sy, eps);

								// Apply 90 degree rotation
								var transform2 = new AffineTransform(transform);
								transform2.quadrantRotate(1);
								var calTransformed2 = AffineTransformImageServer.updatePixelCalibration(cal, transform2);
								assertEquals(calTransformed2.getPixelWidthMicrons(), calTransformed.getPixelHeightMicrons(), eps);
								assertEquals(calTransformed2.getPixelHeightMicrons(), calTransformed.getPixelWidthMicrons(), eps);
								var roi2 = RoiTools.transformROI(roi, transform2);
								var area2 = roi2.getScaledArea(calTransformed2.getPixelWidth().doubleValue(), calTransformed2.getPixelHeight().doubleValue());
								assertEquals(area, area2, eps);

								
								// Apply a non-quadrant rotation, checking impact on area if we have a non-default pixel calibration
								var transform3 = new AffineTransform();
								transform3.concatenate(transform);
								transform3.rotate(Math.PI/3);
								var calTransformed3 = AffineTransformImageServer.updatePixelCalibration(cal, transform3);
								// We expect the default pixel calibration if we have non-square pixels
								if (!PixelCalibration.getDefaultInstance().equals(calTransformed3)) {
									assertFalse(pixelWidth != pixelHeight);
									var roi3 = RoiTools.transformROI(roi, transform3);
									var area3 = roi3.getScaledArea(calTransformed3.getPixelWidth().doubleValue(), calTransformed3.getPixelHeight().doubleValue());
									assertEquals(area, area3, 0.01);
								} else {
									assertTrue(pixelWidth != pixelHeight);
								}
							
								// Check this doesn't impact the uncalibrated values
								var calUncalibratedTransformed = AffineTransformImageServer.updatePixelCalibration(calUncalibrated, transform);
								assertEquals(calUncalibratedTransformed, calUncalibrated);
								
								
								// Apply rotation again, but this time change the order of operations
								var transform4 = AffineTransform.getQuadrantRotateInstance(1);
								transform4.translate(tx, ty);
								transform4.scale(sx, sy);
								var calTransformed4 = AffineTransformImageServer.updatePixelCalibration(cal, transform4);
								assertEquals(calTransformed4.getPixelWidthMicrons(), pixelHeight/sx, eps);
								assertEquals(calTransformed4.getPixelHeightMicrons(), pixelWidth/sy, eps);
								
								var roi4 = RoiTools.transformROI(roi, transform4);
								var area4 = roi4.getScaledArea(calTransformed4.getPixelWidth().doubleValue(), calTransformed4.getPixelHeight().doubleValue());
								assertEquals(area, area4, eps);
								
								// Apply 180 degree rotation (expect same calibration as original transform)
								var transform5 = AffineTransform.getQuadrantRotateInstance(2);
								transform5.translate(tx, ty);
								transform5.scale(sx, sy);
								var calTransformed5 = AffineTransformImageServer.updatePixelCalibration(cal, transform5);
								assertEquals(calTransformed5, calTransformed);
								
								// Apply flip (expect same calibration as original transform)
								var transform6 = new AffineTransform(-sx, 0, 0, sy, tx, ty);
								var calTransformed6 = AffineTransformImageServer.updatePixelCalibration(cal, transform6);
								assertEquals(calTransformed6, calTransformed);
								
								// Apply shearing and check for consistent result
								var transform7 = new AffineTransform(transform6);
								transform7.shear(sx, sy);
								var calTransformed7 = AffineTransformImageServer.updatePixelCalibration(cal, transform7);
								// We expect the default pixel calibration if we have non-square pixels
								if (!PixelCalibration.getDefaultInstance().equals(calTransformed7)) {
									var roi7 = RoiTools.transformROI(roi, transform7);
									var area7 = roi7.getScaledArea(calTransformed7.getPixelWidth().doubleValue(), calTransformed7.getPixelHeight().doubleValue());
									assertEquals(area, area7, 0.01);
								} else {
									assertTrue((transform7.getType() & AffineTransform.TYPE_GENERAL_TRANSFORM) != 0);
								}
							}
						}
					}
				}
			}
		}
	}

}