package qupath.lib.images.servers;

import static org.junit.Assert.*;

import java.math.BigDecimal;

import org.junit.Test;

@SuppressWarnings("javadoc")
public class PixelCalibrationTest {

	@Test
	public void test() {
		
		// Test with non-square pixels (a harder test than square pixels...)
		double pixelWidth = 0.4;
		double pixelHeight = 0.5;
		
		PixelCalibration cal = new PixelCalibration.Builder()
				.pixelSizeMicrons(pixelWidth, pixelHeight)
				.build();
		
		double eps = 1e-6;
		assertTrue(cal.hasPixelSizeMicrons());
		assertEquals(cal.getPixelWidthMicrons(), pixelWidth, eps);
		assertEquals(cal.getPixelHeightMicrons(), pixelHeight, eps);
		assertEquals(cal.getAveragedPixelSizeMicrons(), (pixelWidth + pixelHeight)/2.0, eps);
		assertEquals(cal.getPixelWidth().doubleValue(), pixelWidth, eps);
		assertEquals(cal.getPixelHeight().doubleValue(),pixelHeight, eps);
		assertEquals(cal.getAveragedPixelSize().doubleValue(), (pixelWidth + pixelHeight)/2.0, eps);
		
		assertFalse(cal.hasZSpacingMicrons());
		assertTrue(Double.isNaN(cal.getZSpacingMicrons()));
		assertEquals(cal.getZSpacing().doubleValue(), 1.0, 0.0);
		
		assertEquals(cal.getPixelWidthUnit(), PixelCalibration.MICROMETER);
		assertEquals(cal.getPixelHeightUnit(), PixelCalibration.MICROMETER);
		assertEquals(cal.getZSpacingUnit(), PixelCalibration.Z_SLICE);
		
		
		PixelCalibration calDefault = new PixelCalibration.Builder().build();
		assertFalse(calDefault.hasPixelSizeMicrons());
		assertFalse(calDefault.hasZSpacingMicrons());
		
		assertTrue(Double.isNaN(calDefault.getPixelWidthMicrons()));
		assertTrue(Double.isNaN(calDefault.getPixelHeightMicrons()));
		assertTrue(Double.isNaN(calDefault.getAveragedPixelSizeMicrons()));
		assertTrue(Double.isNaN(calDefault.getZSpacingMicrons()));
		
		assertEquals(calDefault.getPixelWidth().doubleValue(), 1, 0.0);
		assertEquals(calDefault.getPixelHeight().doubleValue(), 1, 0.0);
		assertEquals(calDefault.getZSpacing().doubleValue(), 1, 0.0);
		
		
		double zSpacing = 1.5;
		PixelCalibration calBigDecimal = new PixelCalibration.Builder()
				.pixelSizeMicrons(BigDecimal.valueOf(pixelWidth), pixelHeight)
				.zSpacingMicrons(zSpacing)
				.build();
		
		assertTrue(calBigDecimal.hasPixelSizeMicrons());
		assertEquals(calBigDecimal.getPixelWidthMicrons(), pixelWidth, eps);
		assertEquals(calBigDecimal.getPixelHeightMicrons(), pixelHeight, eps);
		assertEquals(calBigDecimal.getAveragedPixelSizeMicrons(), (pixelWidth + pixelHeight)/2.0, eps);
		assertEquals(calBigDecimal.getPixelWidth().doubleValue(), pixelWidth, eps);
		assertEquals(calBigDecimal.getPixelHeight().doubleValue(),pixelHeight, eps);
		assertEquals(calBigDecimal.getAveragedPixelSize().doubleValue(), (pixelWidth + pixelHeight)/2.0, eps);
		
		assertTrue(calBigDecimal.hasZSpacingMicrons());
		assertEquals(calBigDecimal.getZSpacingMicrons(), zSpacing, eps);
		assertEquals(calBigDecimal.getZSpacing().doubleValue(), zSpacing, eps);
				
		
		double scale = 2.5;
		PixelCalibration calScaled = cal.createScaledInstance(scale, scale);
		
		assertTrue(calScaled.hasPixelSizeMicrons());
		assertEquals(calScaled.getPixelWidthMicrons(), pixelWidth * scale, eps);
		assertEquals(calScaled.getPixelHeightMicrons(), pixelHeight * scale, eps);
		assertEquals(calScaled.getAveragedPixelSizeMicrons(), (pixelWidth * scale + pixelHeight * scale)/2.0, eps);
		assertEquals(calScaled.getPixelWidth().doubleValue(), pixelWidth * scale, eps);
		assertEquals(calScaled.getPixelHeight().doubleValue(),pixelHeight * scale, eps);
		assertEquals(calScaled.getAveragedPixelSize().doubleValue(), (pixelWidth * scale + pixelHeight * scale)/2.0, eps);
		
		
		
		PixelCalibration calBigScaled = calBigDecimal.createScaledInstance(scale, scale);
		
		assertTrue(calBigScaled.hasPixelSizeMicrons());
		assertEquals(calBigScaled.getPixelWidthMicrons(), pixelWidth * scale, eps);
		assertEquals(calBigScaled.getPixelHeightMicrons(), pixelHeight * scale, eps);
		assertEquals(calBigScaled.getAveragedPixelSizeMicrons(), (pixelWidth * scale + pixelHeight * scale)/2.0, eps);
		assertEquals(calBigScaled.getPixelWidth().doubleValue(), pixelWidth * scale, eps);
		assertEquals(calBigScaled.getPixelHeight().doubleValue(),pixelHeight * scale, eps);
		assertEquals(calBigScaled.getAveragedPixelSize().doubleValue(), (pixelWidth * scale + pixelHeight * scale)/2.0, eps);
		
		
		PixelCalibration calDefaultScaled = calDefault.createScaledInstance(scale, scale, scale);
		
		assertFalse(calDefaultScaled.hasPixelSizeMicrons());
		assertFalse(calDefaultScaled.hasZSpacingMicrons());
		
		assertTrue(Double.isNaN(calDefaultScaled.getPixelWidthMicrons()));
		assertTrue(Double.isNaN(calDefaultScaled.getPixelHeightMicrons()));
		assertTrue(Double.isNaN(calDefaultScaled.getAveragedPixelSizeMicrons()));
		assertTrue(Double.isNaN(calDefaultScaled.getZSpacingMicrons()));
		
		assertEquals(calDefaultScaled.getPixelWidth().doubleValue(), scale, 0.0);
		assertEquals(calDefaultScaled.getPixelHeight().doubleValue(), scale, 0.0);
		assertEquals(calDefaultScaled.getZSpacing().doubleValue(), scale, 0.0);
		
		assertEquals(calDefaultScaled.getPixelHeightUnit(), PixelCalibration.PIXEL);
		assertEquals(calDefaultScaled.getPixelWidthUnit(), PixelCalibration.PIXEL);
		assertEquals(calDefaultScaled.getZSpacingUnit(), PixelCalibration.Z_SLICE);

	}

}
