package qupath.lib.gui.viewer.recording;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.image.DataBufferDouble;
import java.util.function.BiFunction;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorMaps.ColorMap;

// TODO: Check magnification
@SuppressWarnings("javadoc")
public class TestViewTrackerDataMaps {
	
	private static ViewTracker nonRotatedTracker;
//	private static ViewTracker rotatedTracker;
	private static ColorMap cm;
	
	private static DataBufferDouble nonRotatedGroundTruth;
//	private static DataBufferDouble rotatedGroundTruth;
	
	@BeforeAll
	public static void init() {
		
		/** ------- Non-rotated recording ------- **/
		nonRotatedTracker = new ViewTracker(null);
		ViewRecordingFrame frame1 = new ViewRecordingFrame(1000, new Rectangle(0, 0, 100, 100), new Dimension(100, 100), 1.0, 0.0, 1, 1);
		ViewRecordingFrame frame2 = new ViewRecordingFrame(2000, new Rectangle(50, 0, 200, 200), new Dimension(200, 200), 1.0, 0.0, 1, 1);
		ViewRecordingFrame frame3 = new ViewRecordingFrame(3500, new Rectangle(50, 50, 50, 50), new Dimension(50, 50), 1.0, 0.0, 1, 1);
		
		nonRotatedTracker.appendFrame(frame1);
		nonRotatedTracker.appendFrame(frame2);
		nonRotatedTracker.appendFrame(frame3);
		
		cm = ColorMaps.getColorMaps().values().iterator().next();
		
		/**
		 * Generate ground truth:
		 * +-------+---+-----------------+
		 * |			||	 |					|
		 * |		f1	+--+					|
		 * |			|| f3||					|
		 * +-------+--+		f2			|
		 * 			|						|
		 * 			|						|
		 * 			+----------------------+
		 */
		nonRotatedGroundTruth = new DataBufferDouble(200*250);
		long maxTime = nonRotatedTracker.getFrame(nonRotatedTracker.nFrames()-1).getTimestamp() - nonRotatedTracker.getFrame(0).getTimestamp();
		
		// N.B.: Last frame of tracker is always ignored, as we don't know how much time the user was viewing it for.
		long[] values = new long[] {frame2.getTimestamp() - frame1.getTimestamp(),
				frame3.getTimestamp() - frame2.getTimestamp(),
				frame3.getTimestamp() - frame2.getTimestamp(),
				frame3.getTimestamp() - frame1.getTimestamp(),
				frame3.getTimestamp() - frame1.getTimestamp()};
		
		int[] xsFrom = new int[] {0, 50, 100, 50, 50};
		int[] xsTo = new int[] {50, 100, 250, 100, 100};
		int[] ysFrom = new int[] {0, 100, 0, 0, 50};
		int[] ysTo = new int[] {100, 200, 200, 50, 100};
		BiFunction<Integer, Integer, Integer> lambda = (x, y) -> y * 250+ x;
		
		for (int i =0; i < values.length; i++) {
			long value = values[i];
			for (int x = xsFrom[i]; x < xsTo[i]; x++) {
				for (int y = ysFrom[i]; y < ysTo[i]; y++) {
					nonRotatedGroundTruth.setElemDouble(lambda.apply(x, y), (double)value/maxTime);
				}
			}
		}

	}
	
	@Test
	public void test_calculateNonRotatedMaps() {
//		ViewTrackerDataMaps dm = new ViewTrackerDataMaps(nonRotatedTracker, 250, 200, 1, 1, new double[] {1.0});
//		
//		// Request data for all the frames
//		dm.updateDataMaps(0, 2000, 0.0, 1.0, Feature.TIMESTAMP, cm);
//		
//		var actual = dm.getRegionMaps().get(ImageRegion.createInstance(0, 0, 250, 200, 0, 0)).getRaster().getDataBuffer();
	}
	
	@Test
	public void test_calculate90DegreesRotatedMaps() {
		// TODO
	}

	@Test
	public void test_calculate100DegreesRotatedMaps() {
		// TODO
	}
	
	

}
