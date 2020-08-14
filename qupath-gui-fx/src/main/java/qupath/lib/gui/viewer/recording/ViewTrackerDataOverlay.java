package qupath.lib.gui.viewer.recording;

import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.IndexColorModel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.gui.tools.MeasurementMapper;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.gui.viewer.overlays.BufferedImageOverlay;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.ImageRegion;

// TODO: What if the downsample is not rounded? (e.g. dwnsmple=1.5, then img will be rounded, and ImageRegion will be rounded again?)
public class ViewTrackerDataOverlay{
	
	private final static Logger logger = (Logger) LoggerFactory.getLogger(ViewTrackerDataOverlay.class);
	
	private ViewTracker tracker;
	private QuPathViewer viewer;
	private ImageServer<?> server;
	
	private int imgWidth;
	private int imgHeight;
	private double downsample;
	
	private long timeStart;
	private long timeStop;
	private double downMin;
	private double downMax;
	private boolean timeNormalized;	// If False, it's magnification-normalized
	
	private Map<ImageRegion, BufferedImage> regions;


	public ViewTrackerDataOverlay(ImageServer<?> server, QuPathViewer viewer, ViewTracker tracker) {
		this.tracker = tracker;
		this.viewer = viewer;
		this.server = server;
		this.imgWidth = server.getWidth();
		this.imgHeight = server.getHeight();
		this.regions = new HashMap<>();
		
		
		// Set width and height of img
		double[] preferredDownsamples = server.getPreferredDownsamples();
		int index = 0;
		double divider = preferredDownsamples[0];
		while ((long)imgWidth * imgHeight > 2000 * 2000) {
			// compute downsample to reach img within pixel limit (2k * 2k)
			index++;
			if (index >= preferredDownsamples.length)
				divider = preferredDownsamples[preferredDownsamples.length-1]*2;
			else
				divider = preferredDownsamples[index];
			imgWidth = (int)Math.round(server.getWidth() / divider);
			imgHeight = (int)Math.round(server.getHeight() / divider);
		}
		downsample = divider;
	
	}
	
	public void updateDataImage(long timeStart, long timeStop, double downMin, double downMax, boolean timeNormalised) {
		this.timeStart = timeStart;
		this.timeStop = timeStop;
		this.downMin = downMin;
		this.downMax = downMax;
		this.timeNormalized = timeNormalised;
		
		regions = getImageRegions();
		viewer.repaint();
	}
	
	private Map<ImageRegion, BufferedImage> getImageRegions() {
		var startTime = System.currentTimeMillis();
		regions.clear();
		for (int z = 0; z < server.nZSlices(); z++) {
			for (int t = 0; t < server.nTimepoints(); t++) {
//				ImageRegion region = ImageRegion.createInstance(0, 0, (int)Math.round(imgWidth*downsample), (int)Math.round(imgHeight*downsample), z, t);
				ImageRegion region = ImageRegion.createInstance(0, 0, server.getWidth(), server.getHeight(), z, t);
				BufferedImage img = getBufferedImage(z, t);
				regions.put(region, img);
			}
		}
		logger.info("Processing time for getImageRegions(): " + (System.currentTimeMillis()-startTime));
		return regions;
	}
	
	private BufferedImage getBufferedImage(int z, int t) {
		int frameStartIndex = tracker.getFrameIndexForTime(timeStart);
		int frameStopIndex = tracker.getFrameIndexForTime(timeStop);
		
		ViewRecordingFrame[] relevantFrames = tracker.getAllFrames().subList(frameStartIndex, frameStopIndex+1).parallelStream()
				.filter(frame -> frame.getZ() == z && frame.getT() == t)
				.filter(frame -> frame.getDownFactor() >= downMin && frame.getDownFactor() <= downMax)
				.toArray(ViewRecordingFrame[]::new);
		
		
		BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_BYTE_INDEXED, createColorModel());
		byte[] buffer = ((DataBufferByte)img.getRaster().getDataBuffer()).getData();
		
		if (!timeNormalized) {
			for (int i = 0; i < buffer.length; i++)
				buffer[i] = (byte)downMax;
		}

		if (timeNormalized) {
			long[] bufferTest = new long[buffer.length];
			Arrays.fill(bufferTest, 0);
			long curMax = -1;
			for (int nFrame = 0; nFrame < relevantFrames.length; nFrame++) {
				var frame = relevantFrames[nFrame];
				Rectangle downsampledBounds = getDownsampledBounds(frame.getImageBounds(frame.getRotation()));
				Rectangle downsampleBoundsCropped = getCroppedDownsampledBounds(downsampledBounds);
				for (int x = downsampleBoundsCropped.x; x < downsampleBoundsCropped.x + downsampleBoundsCropped.width; x++) {
					for (int y = downsampleBoundsCropped.y; y < downsampleBoundsCropped.y + downsampleBoundsCropped.height; y++) {
						if (x < 0 || x >= imgWidth || y < 0 || y >= imgHeight)
							continue;
						var nextTimeStamp = nFrame+1 < relevantFrames.length ? relevantFrames[nFrame+1].getTimestamp() : 0;
						long value = (bufferTest[y*imgWidth + x] + frame.getTimestamp() - nextTimeStamp);
						if (value > curMax)
							curMax = value;
						bufferTest[y*imgWidth + x] = value;
					}
				}
			}
			
			// Normalize
			for (int i = 0; i < bufferTest.length; i++)
				buffer[i] = (byte)(bufferTest[i] / curMax * 255);
		} else {
			double[] bufferTest = new double[buffer.length];
			Arrays.fill(bufferTest, 0);
			double curMin = downMax;
			for (int nFrame = 0; nFrame < relevantFrames.length; nFrame++) {
				var frame = relevantFrames[nFrame];
				Rectangle downsampledBounds = getDownsampledBounds(frame.getImageBounds(frame.getRotation()));
				Rectangle downsampleBoundsCropped = getCroppedDownsampledBounds(downsampledBounds);
				for (int x = downsampleBoundsCropped.x; x < downsampleBoundsCropped.x + downsampleBoundsCropped.width; x++) {
					for (int y = downsampleBoundsCropped.y; y < downsampleBoundsCropped.y + downsampleBoundsCropped.height; y++) {
						if (frame.getRotation() != 0) {
							var at = AffineTransform.getRotateInstance(frame.getRotation());
							Point2D myPoint = new Point2D.Double(x, y);
							var melvin = at.transform(myPoint, null);
						}
							
						
						if (x < 0 || x >= imgWidth || y < 0 || y >= imgHeight)
							continue;
						
						if (downMax == downMin)
							bufferTest[y*imgWidth + x] = 255;
						
						double value = downMax;
							value = bufferTest[y*imgWidth + x] < frame.getDownFactor() ? frame.getDownFactor() : buffer[y*imgWidth + x];
						if (value < curMin)
							curMin = value;
						bufferTest[y*imgWidth + x] = value;
					}
				}
			}
			
			// Normalize
			for (int i = 0; i < bufferTest.length; i++)
				buffer[i] = (byte)(bufferTest[i] / curMin * 255);
		}
		
		return img;
	}
	
	public BufferedImageOverlay getOverlay() {
		return new BufferedImageOverlay(viewer, regions);
	}
	
	/**
	 * Scales the coordinate of the given rectangle according to the 
	 * {@code img}'s {@code downsample}.
	 * @param bounds
	 * @return
	 */
	private Rectangle getDownsampledBounds(Rectangle bounds) {
		int x = (int)Math.round(bounds.getX()/downsample);
		int y = (int)Math.round(bounds.getY()/downsample);
		int width = (int)Math.round(bounds.getWidth()/downsample);
		int height = (int)Math.round(bounds.getHeight()/downsample);
		return new Rectangle(x, y, width, height);
	}
	
	/**
	 * Ensures that the coordinate of the given rectangle are within the
	 * bounds of {@code img}.
	 * <p>
	 * Note: bounds.x is used instead of bounds.getX() to avoid type casting.
	 * @param bounds
	 * @return cropped bounds
	 */
	private Rectangle getCroppedDownsampledBounds(Rectangle bounds) {
		int x = bounds.x < 0 ? 0 : bounds.x < imgWidth ? bounds.x : imgWidth;
		int y = bounds.y < 0 ? 0 : bounds.y < imgHeight ? bounds.y : imgHeight;
		int width = bounds.width < 0 ? 0 : (bounds.width + x > imgWidth ? imgWidth - x : bounds.width);
		int height = bounds.height < 0 ? 0 : (bounds.height + x > imgHeight ? imgHeight - y : bounds.height);
		return new Rectangle(x, y, width, height);
	}
	
	private IndexColorModel createColorModel() {
	    var mapper = MeasurementMapper.loadColorMappers().get(0);
	    int[] rgba = new int[256];
	    for (int i = 0; i < 256; i++) {
	        int rgb = mapper.getColor(i, 0, 255);
	        rgba[i] = ColorTools.makeRGBA(ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb), i);
	    }
	    return new IndexColorModel(8, 256, rgba, 0, true, 0, DataBuffer.TYPE_BYTE);
	}
	

}
