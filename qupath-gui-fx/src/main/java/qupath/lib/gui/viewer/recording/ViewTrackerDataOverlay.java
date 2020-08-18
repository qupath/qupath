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
import java.util.stream.IntStream;

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
		byte[] imgBuffer = ((DataBufferByte)img.getRaster().getDataBuffer()).getData();
		float[] buffer = new float[imgBuffer.length];
		

		// Get max time (for normalization)
		double maxValue;
		if (timeNormalized) {
			maxValue = IntStream.range(0, relevantFrames.length-1)
					.map(index -> index < relevantFrames.length ? (int)(relevantFrames[index+1].getTimestamp() - relevantFrames[index].getTimestamp()) : 0)
					.max()
					.getAsInt();
		} else {
			maxValue = Arrays.asList(relevantFrames).stream()
					.mapToDouble(e -> e.getDownFactor())
					.max()
					.getAsDouble();
		}
		
		Arrays.fill(buffer, 0);
		for (int nFrame = 0; nFrame < relevantFrames.length; nFrame++) {
			var frame = relevantFrames[nFrame];
			Rectangle downsampledBounds = getDownsampledBounds(frame.getImageBounds());
			if (frame.getRotation() == 0) {
				Rectangle downsampleBoundsCropped = getCroppedDownsampledBounds(downsampledBounds);
				for (int x = downsampleBoundsCropped.x; x < downsampleBoundsCropped.x + downsampleBoundsCropped.width; x++) {
					for (int y = downsampleBoundsCropped.y; y < downsampleBoundsCropped.y + downsampleBoundsCropped.height; y++) {
						if (x < 0 || x >= imgWidth || y < 0 || y >= imgHeight)
							continue;
						if (nFrame < relevantFrames.length-1) {
							if (timeNormalized)
								buffer[y*imgWidth + x] += (int)(frame.getTimestamp() - relevantFrames[nFrame+1].getTimestamp());
							else
								buffer[y*imgWidth + x] = buffer[y*imgWidth + x] < maxValue-frame.getDownFactor() ? (float)(maxValue-frame.getDownFactor()) : buffer[y*imgWidth + x];
						}
						
					}
				}
			} else {
//				Shape shape = frame.getImageShape();
//				PathIterator it = shape.getPathIterator(null);
//				double[] segment = new double[6];
//				int[] xs = new int[4];
//				int[] ys = new int[4];
//				for (int i = 0; i < 4; i++) {
//					if (it.isDone())
//						return null;
//					
//			        it.currentSegment(segment);
//			        xs[i] = (int)Math.round(segment[0]/downsample);
//			        ys[i] = (int)Math.round(segment[1]/downsample);
//			        
//			        it.next();
//				}
//				
//				Polygon poly = new Polygon(xs, ys, 4);
//				for (int x = 0; x < imgWidth; x++) {
//					for (int y = 0; y < imgHeight; y++) {
//						Point2D p = new Point2D.Double(x, y);
//						if (poly.contains(p)) {
//							if (timeNormalized && nFrame < relevantFrames.length-1)
//								buffer[y*imgWidth + x] += (int)(frame.getTimestamp() - relevantFrames[nFrame+1].getTimestamp());
//							else if (!timeNormalized)
//								buffer[y*imgWidth + x] = buffer[y*imgWidth + x] < maxValue-frame.getDownFactor() ? (float)(maxValue-frame.getDownFactor()) : buffer[y*imgWidth + x];
//						}
//					}
//				}
				
				// Iterating through x and y, checking if they're included in frame.getImageBounds() when rotated
				AffineTransform transform = new AffineTransform();
				Point2D center = frame.getFrameCentre();
				transform.rotate(-frame.getRotation(), center.getX()/downsample, center.getY()/downsample);

				for (int x = 0; x < imgWidth; x++) {
					for (int y = 0; y < imgHeight; y++) {
						Point2D[] pts = new Point2D[] {new Point2D.Double(x, y)};
						transform.transform(pts, 0, pts, 0, 1);
						if (downsampledBounds.contains(new Point2D.Double(x, y))) {
							if (nFrame < relevantFrames.length-1 && new Rectangle(0, 0, imgWidth, imgHeight).contains(pts[0])) {
								// Index of the rotated point in the buffer (flatten)
								int index = ((int)pts[0].getY()*imgWidth + (int)pts[0].getX());
								
								// Update buffer
								if (timeNormalized)
									buffer[index] += (int)(frame.getTimestamp() - relevantFrames[nFrame+1].getTimestamp());
								else
									buffer[index] = buffer[index] < maxValue-frame.getDownFactor() ? (float)(maxValue-frame.getDownFactor()) : buffer[index];
							}
						}
					}
				}
			}
		}
		// Normalize
	    for (int i = 0; i < buffer.length; i++)
	    	imgBuffer[i] = (byte)(buffer[i] / maxValue * 255);
		return img;
	}
	
	public BufferedImageOverlay getOverlay() {
		return new BufferedImageOverlay(viewer, regions);
	}
	
	/**
	 * Scales the coordinates of the given rectangle according to the 
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
	 * Ensures that the coordinates of the given rectangle are within the
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
