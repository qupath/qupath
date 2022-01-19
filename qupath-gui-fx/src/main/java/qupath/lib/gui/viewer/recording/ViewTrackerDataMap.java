package qupath.lib.gui.viewer.recording;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferFloat;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.Arrays;
import java.util.stream.DoubleStream;
import java.util.stream.LongStream;

import qupath.lib.color.ColorModels;
import qupath.lib.color.ColorModels.ColorModelBuilder;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.gui.viewer.recording.ViewTrackerDataMaps.Feature;
import qupath.lib.regions.ImageRegion;

/**
 * Class for storing the values of a data map as well as generating a proper {@link BufferedImage} with the appropriate pixel type.
 * @author Melvin Gelbard
 */
public class ViewTrackerDataMap {
	
	/**
	 * Separating longArray and doubleArray because using e.g. Number slows down the process
	 */
	long[] longArray;
	double[] doubleArray;
	
	final ImageRegion region;
	final Feature feature;
	final ViewRecordingFrame[] relevantFrames;
	final int targetWidth;
	final int targetHeight; 
	final double downsample;
	
	ViewTrackerDataMap(ImageRegion region, Feature feature, ViewRecordingFrame[] relevantFrames, double downsample, int targetWidth, int targetHeight) {
		this.region = region;
		this.feature = feature;
		this.relevantFrames = relevantFrames;
		this.targetWidth = targetWidth;
		this.targetHeight = targetHeight;
		this.downsample = downsample;
		calculateMapValues();
	}
	
	/**
	 * Calculate the map values for each pixel, returned in a long array whose size is {@code targetWidth * targetHeight}.
	 */
	private void calculateMapValues() {
		int arrayLength = targetHeight * targetWidth;
		if (feature == Feature.TIMESTAMP) {
			longArray = new long[arrayLength];
			Arrays.fill(longArray, 0L);
		} else {
			doubleArray = new double[arrayLength];
			Arrays.fill(doubleArray, 0.0);
		}
		
		if (relevantFrames.length <= 1)
			return;
		
		for (int nFrame = relevantFrames.length - 1; nFrame >= 0 ; nFrame--) {
			var frame = relevantFrames[nFrame];
			Rectangle downsampledBounds = getDownsampledBounds(frame.getImageBounds(), downsample);
			if (nFrame == 0 || frame.getTimestamp() == 0)
				continue;
	        
	        long timestampValue = relevantFrames[nFrame-1].getTimestamp() - frame.getTimestamp();
	        double downsampleValue = frame.getDownsampleFactor();
	        
	        downsampledBounds = getCroppedBounds(downsampledBounds, targetWidth, targetHeight);
	        Shape rotated = null;	 // if rotation != 0, this variable will be initialised
	        if (frame.getRotation() != 0) {
	        	AffineTransform transform = new AffineTransform();
				Point2D center = frame.getFrameCentre();
				transform.rotate(-frame.getRotation(), center.getX()/downsample, center.getY()/downsample);
				rotated = transform.createTransformedShape(downsampledBounds);
				downsampledBounds = rotated.getBounds();
	        }
	        
	        // Iterate through all the pixel in the bounding box of the rotated rectangle
	        for (int y = (int) downsampledBounds.getY(); y < downsampledBounds.getY() + downsampledBounds.getHeight(); y++) {
        		for (int x = (int) downsampledBounds.getX(); x < downsampledBounds.getX() + downsampledBounds.getWidth(); x++) {
        			int index =  y * targetWidth+ x;
        			if (rotated == null || (rotated.contains(new Point2D.Double(x, y)) && index > 0 && index < arrayLength)) {
        				if (feature == Feature.TIMESTAMP)
        					longArray[index] = longArray[index] + timestampValue;
        				else if (doubleArray[index] > downsampleValue || doubleArray[index] == 0)
        					doubleArray[index] = downsampleValue;
        			}
        		}
        	}
		}
	}
	
	/**
	 * Scale the coordinates of the given rectangle according 
	 * to the given {@code downsample}.
	 * @param bounds
	 * @param downsample
	 * @return downsampled rectangle
	 */
	private static Rectangle getDownsampledBounds(Rectangle bounds, double downsample) {
		int x = (int)Math.round(bounds.getX()/downsample);
		int y = (int)Math.round(bounds.getY()/downsample);
		int width = (int)Math.round(bounds.getWidth()/downsample);
		int height = (int)Math.round(bounds.getHeight()/downsample);
		return new Rectangle(x, y, width, height);
	}
	
	/**
	 * Ensure that the coordinates of the given rectangle are within the
	 * bounds specified by {@code width} & {@code height}.
	 * <p>
	 * Note: bounds.x is used instead of bounds.getX() to avoid type casting.
	 * @param bounds
	 * @param width 
	 * @param height 
	 * @return cropped rectangle
	 */
	private static Rectangle getCroppedBounds(Rectangle bounds, int width, int height) {
		int x = bounds.x < 0 ? 0 : bounds.x < width ? bounds.x : width;
		int y = bounds.y < 0 ? 0 : bounds.y < height ? bounds.y : height;
		int newWidth = bounds.width < 0 ? 0 : (bounds.width + x > width ? width - x : bounds.width);
		int newHeight = bounds.height < 0 ? 0 : (bounds.height + y > height ? height - y : bounds.height);
		return new Rectangle(x, y, newWidth, newHeight);
	}
	
	Number getMaxValue() {
		if (feature == Feature.TIMESTAMP) 
			return LongStream.of(longArray).max().getAsLong();
		return DoubleStream.of(doubleArray).max().getAsDouble();
	}
	
	Number getCalculatedValue(int x, int y) {
		if (feature == Feature.TIMESTAMP)
			return longArray[(int)Math.floor(x/downsample) + targetWidth * (int)Math.floor(y/downsample)];
		return doubleArray[(int)Math.floor(x/downsample) + targetWidth * (int)Math.floor(y/downsample)];
	}

	BufferedImage getBufferedImage(ColorMap colorMap) {
        var dataBuffer = new DataBufferFloat(feature == Feature.TIMESTAMP ? longArray.length : doubleArray.length);
		for (int i = 0; i < dataBuffer.getSize(); i++) {
			dataBuffer.setElemDouble(i, feature == Feature.TIMESTAMP ? longArray[i] : doubleArray[i]);
		}
		
		String colorMapName = colorMap.getName();
		ColorModelBuilder colorModelBuilder;
		Number maxValue = getMaxValue();		// TODO: Should each dataMap (with different z & t) have a different scales (because of a different maxValue)? Now yes.
		colorModelBuilder = ColorModels.createColorModelBuilder(
				ColorModels.createBand(colorMapName, 0, 0, feature == Feature.TIMESTAMP ? maxValue.longValue() : maxValue.doubleValue()),
				ColorModels.createBand(colorMapName, 0, 0, feature == Feature.TIMESTAMP ? maxValue.longValue() : maxValue.doubleValue(), 0.8)
				);
		
        var cm = colorModelBuilder.build();
        var sampleModel = new BandedSampleModel(dataBuffer.getDataType(), targetWidth, targetHeight, 1);
		WritableRaster raster = Raster.createWritableRaster(sampleModel , dataBuffer, null);
		BufferedImage img = new BufferedImage(cm, raster, false, null);
		
		return img;
	}
}
