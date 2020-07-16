package qupath.lib.gui.viewer.recording;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private boolean timeNormalised;
	
	private Map<ImageRegion, BufferedImage> regions;
	
	private BufferedImageOverlay overlay;


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
		while ((long)imgWidth * imgHeight > 2000 * 2000) {
			// compute downsample to reach img within pixel limit (2k * 2k)
			index++;
			imgWidth = (int)Math.round(server.getWidth() / preferredDownsamples[index]);
			imgHeight = (int)Math.round(server.getHeight() / preferredDownsamples[index]);
		}
		this.downsample = preferredDownsamples[index];
	
	}
	
	public void updateDataImage(long timeStart, long timeStop, double downMin, double downMax, boolean timeNormalised) {
		this.timeStart = timeStart;
		this.timeStop = timeStop;
		this.downMin = downMin;
		this.downMax = downMax;
		this.timeNormalised = timeNormalised;
		
		regions = getImageRegions();
		viewer.repaint();
	}
	
	private Map<ImageRegion, BufferedImage> getImageRegions() {
		regions.clear();
		for (int z = 0; z < server.nZSlices(); z++) {
			for (int t = 0; t < server.nTimepoints(); t++) {
				ImageRegion region = ImageRegion.createInstance(0, 0, (int)Math.round(imgWidth*downsample), (int)Math.round(imgHeight*downsample), z, t);
				BufferedImage img = getBufferedImage(z, t);
				regions.put(region, img);
			}
		}
		return regions;
	}
	
	private BufferedImage getBufferedImage(int z, int t) {
		int frameStartIndex = tracker.getFrameIndexForTime(timeStart);
		int frameStopIndex = tracker.getFrameIndexForTime(timeStop);
		
		ViewRecordingFrame[] relevantFrames = tracker.getAllFrames().subList(frameStartIndex, frameStopIndex+1).parallelStream()
				.filter(e -> e.getDownFactor() >= downMin && e.getDownFactor() <= downMax)
				.toArray(ViewRecordingFrame[]::new);
		
		
		if (!timeNormalised) {
			BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_ARGB);
			var g2d = img.createGraphics();
			
			for (var frame: relevantFrames) {
				if (frame.getZ() != z || frame.getT() != t)
					continue;
				var downsampledBounds = getDownsampledBounds(frame.getImageBounds());
				g2d.setColor(java.awt.Color.GREEN);
				g2d.fillRect(downsampledBounds.x, downsampledBounds.y, downsampledBounds.width, downsampledBounds.height);	
			}
			return img;
		} else {
			// TODO: Find a color model
			BufferedImage img = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_BYTE_INDEXED);
			byte[] buffer = ((DataBufferByte)img.getRaster().getDataBuffer()).getData();
			
			for (var frame: relevantFrames) {
				Rectangle downsampledBounds = getDownsampledBounds(frame.getImageBounds());
				Rectangle downsampleBoundsCropped = getCroppedDownsampledBounds(downsampledBounds);
				var startIndex = imgWidth*downsampleBoundsCropped.y + downsampleBoundsCropped.x;
				var stopIndex = imgWidth*(downsampleBoundsCropped.y+downsampleBoundsCropped.height) + downsampleBoundsCropped.x + downsampleBoundsCropped.width;
				for (int x = downsampleBoundsCropped.x; x < downsampleBoundsCropped.x + downsampleBoundsCropped.width; x++) {
					for (int y = downsampleBoundsCropped.y; y < downsampleBoundsCropped.y + downsampleBoundsCropped.height; y++) {
						buffer[y*imgWidth + x] = (byte)(buffer[y*imgWidth + x] + 1);
					}
				}		
			}
			return img;
		}
	}
	
	public BufferedImageOverlay getOverlay() {
		return new BufferedImageOverlay(viewer, getImageRegions());
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
	

}
