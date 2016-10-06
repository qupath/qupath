package qupath.lib.images.servers;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import qupath.lib.display.RegionRequest;
import qupath.lib.gui.helpers.AwtTools;
import qupath.lib.helpers.GeneralTools;
import qupath.lib.images.PathBufferedImage;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.interfaces.ImageServer;

/**
 * ImageServer implementation using OpenSlide.  Written using reflection.
 * 
 * @author Pete Bankhead
 *
 */
public class ReflectiveOpenslideImageServer extends AbstractImageServer<BufferedImage> {

	private String path;
	private int width, height;
	private double[] downsamples;
	private int nChannels;
	private double pixelWidth, pixelHeight, magnification = Double.NaN;
//	private boolean isRGB = true;
	private int tileWidth = -1;
	private int tileHeight = -1;
	
	private List<String> associatedImageList = null;
	private Map<String, Object> associatedImages = null;

	private Class<?> cOpenslide = null;
	private Class<?> cAssociatedImage = null;
	private Object osr = null;
	
	transient private Method paintMethod = null;
	
	
	private double readNumericPropertyOrDefault(Map<String, String> properties, String name, double defaultValue) {
		// Try to read a tile size
		String value = properties.get(name);
		if (value == null) {
			System.err.println("Openslide: Property not available: " + name);
			return defaultValue;
		}
		try {
			return Double.parseDouble(value);
		} catch (NumberFormatException e) {
			System.err.println("Openslide: Could not parse property " + name + " with value " + value);
			return defaultValue;
		}
	}

	@SuppressWarnings("unchecked")
	public ReflectiveOpenslideImageServer(String path, ClassLoader loader) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		this.path = path;

		// Ensure the garbage collector has run - otherwise any previous attempts to load the required native library
		// from different classloader are likely to cause an error (although upon first further investigation it seems this doesn't really solve the problem...)
		System.gc();

//		cOpenslide = IJ.getClassLoader().loadClass("org.openslide.OpenSlide");
		cOpenslide = loader.loadClass("org.openslide.OpenSlide");
		Constructor<?> cons = cOpenslide.getConstructor(File.class);
		File file = new File(path);
		System.gc();
		osr = cons.newInstance(file);

		// Parse the parameters
		Method method;
		method = cOpenslide.getMethod("getLevel0Width");
		width = ((Long)method.invoke(osr)).intValue();
		method = cOpenslide.getMethod("getLevel0Height");
		height = ((Long)method.invoke(osr)).intValue();
		// For openslide, assume 3 channels (RGB)
		nChannels = 3;

		method = cOpenslide.getMethod("getProperties");
		Map<String, String> properties = (Map<String, String>)method.invoke(osr);
//		for (Map.Entry<String, String> entry : new TreeMap<>(properties).entrySet()) {
//			System.out.println(String.format("%s:\t%s", entry.getKey(), entry.getValue()));
//		}
		
//		// Check for z-stack
//		if (properties.containsKey("hamamatsu.zFine[0]")) {
//			System.err.println("SEEMS TO BE A Z-STACK!");
//		}
		
		// Try to read a tile size
		tileWidth = (int)readNumericPropertyOrDefault(properties, "openslide.level[0].tile-width", -1);
		tileHeight = (int)readNumericPropertyOrDefault(properties, "openslide.level[0].tile-height", -1);
		// Read other properties
		pixelWidth = readNumericPropertyOrDefault(properties, "openslide.mpp-x", Double.NaN);
		pixelHeight = readNumericPropertyOrDefault(properties, "openslide.mpp-y", Double.NaN);
		magnification = readNumericPropertyOrDefault(properties, "openslide.objective-power", Double.NaN);

		// Loop through the series again & determine downsamples
		method = cOpenslide.getMethod("getLevelCount");
		int levelCount = (Integer)method.invoke(osr);
		downsamples = new double[levelCount];
		method = cOpenslide.getMethod("getLevelDownsample", int.class);
		for (int i = 0; i < levelCount; i++)
			downsamples[i] = (Double)method.invoke(osr, i);
		
		/*
		 * TODO: Determine associated image names
		 * This works, but need to come up with a better way of returning usable servers
		 * based on the associated images
		 * 
		 */
		cAssociatedImage = loader.loadClass("org.openslide.AssociatedImage");
		method = cOpenslide.getMethod("getAssociatedImages");
		associatedImages = (Map<String, Object>)method.invoke(osr);
		associatedImageList = new ArrayList<>(associatedImages.keySet());
		associatedImageList = Collections.unmodifiableList(associatedImageList);
		
		
		// Try reading a thumbnail... the point being that if this is going to fail,
		// we want it to fail quickly so that it may yet be possible to try another server
		// This can occur with corrupt .svs (.tif) files that Bioformats is able to handle better
		System.out.println("Test reading thumbnail with openslide: passed (" + getBufferedThumbnail(200, 200, 0).toString() + ")");

	}
	
	public ReflectiveOpenslideImageServer(String path) throws ClassNotFoundException, InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		this(path, ClassLoader.getSystemClassLoader());
	}

	@Override
	public String getPath() {
		return path;
	}

//	@Override
//	public String getShortServerName() {
//		try {
//			String name = new File(path).getName().replaceFirst("[.][^.]+$", "");
//			return name;
//		} catch (Exception e) {}
//		return getServerPath();
//	}

	@Override
	public double[] getPreferredDownsamples() {
//		for (double d : downsamples)
//			System.out.println(d);
//		return downsamples.clone();
		return downsamples;
	}

	@Override
	public double getMagnification() {
		return magnification;
	}

	@Override
	public int getWidth() {
		return width;
	}

	@Override
	public int getHeight() {
		return height;
	}

	@Override
	public int nChannels() {
		return nChannels;
	}

	@Override
	public double getPixelWidthMicrons() {
		return pixelWidth;
	}

	@Override
	public double getPixelHeightMicrons() {
		return pixelHeight;
	}

	@Override
	public PathImage<BufferedImage> readRegion(RegionRequest request) {
		BufferedImage img = readBufferedImage(request);
		if (img == null)
			return null;
		return new PathBufferedImage(this, request, img);
	}

	
	/*
	 * TODO: Take decision on use of tiled whole slide image servers
	 */
	public BufferedImage readBufferedImageTiled(Rectangle region, double downsampleFactor) {
		if (region == null) {
			region = new Rectangle(0, 0, getWidth(), getHeight());
		}
		System.out.println("Using NEWER function! " + region);
		
		int tileWidth = 256;
		int tileHeight = 256;
		// Often it is preferable to request multiple tiles per call to reduce calling overhead
		final int tileRequestScale = 2;
		
		int level = getClosestDownsampleIndex(getPreferredDownsamples(), downsampleFactor);
		double downsample = downsamples[level];
		
		// Create tile image
		int tileWidthLevel0 = (int)(tileWidth * downsample);
		int tileHeightLevel0 = (int)(tileHeight * downsample);
		BufferedImage img = new BufferedImage(tileWidth*tileRequestScale, tileHeight*tileRequestScale, BufferedImage.TYPE_INT_ARGB_PRE);
        int data[] = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();

        // Create output image
		int width = (int)(region.width / downsampleFactor + .5);
		int height = (int)(region.height / downsampleFactor + .5);
		BufferedImage img2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2d = img2.createGraphics();

        int xStart = (int)((region.x / (double)tileWidthLevel0) * tileWidthLevel0);
        int yStart = (int)((region.y / (double)tileHeightLevel0) * tileHeightLevel0);
        double relativeScale = downsample / downsampleFactor;
        AffineTransform transform = new AffineTransform();
        try {
        	if (paintMethod == null)
        		paintMethod = cOpenslide.getMethod("paintRegionARGB", int[].class, long.class, long.class, int.class, int.class, int.class);
        	for (int y = yStart; y < region.y + region.height; y += tileHeightLevel0*tileRequestScale) {
        		for (int x = xStart; x < region.x + region.width; x += tileWidthLevel0*tileRequestScale) {
        			paintMethod.invoke(osr, data, x, y, level, tileWidth*tileRequestScale, tileHeight*tileRequestScale);
        			transform.setTransform(relativeScale, 0, 0, relativeScale, (x - region.x)/downsampleFactor, (y - region.y)/downsampleFactor);
        			g2d.drawImage(img, transform, null);
        		}
        	}
        } catch (Exception e) {
        	e.printStackTrace();
        	return null;
        }
        g2d.dispose();
        return img2;
	}
	

	
	
//	@Override
//	public BufferedImage readBufferedImage(Rectangle region, double downsampleFactor) {
//		if (region == null) {
//			region = new Rectangle(0, 0, getWidth(), getHeight());
//		}
//		int maxSize = 0;
//		if (region.width > region.height)
//			maxSize = (int)(region.width / downsampleFactor + .5);
//		else
//			maxSize = (int)(region.height / downsampleFactor + .5);
//
//		//		System.out.println("Aiming for " + region + ", downsample: " + downsampleFactor);
//		try {
//			// Create a thumbnail for the region
//			Method method = cOpenslide.getMethod("createThumbnailImage", int.class, int.class, long.class, long.class, int.class);
//			BufferedImage img = (BufferedImage)method.invoke(osr, region.x, region.y, region.width, region.height, maxSize);
//			return img;
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		return null;
//	}


	@Override
	public void close() {
//		super.close();
		if (osr != null) {
			try {
				cOpenslide.getMethod("close").invoke(osr);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public String getServerType() {
		return "OpenSlide";
	}

	@Override
	public int getPreferredTileWidth() {
		return tileWidth;
	}

	@Override
	public int getPreferredTileHeight() {
		return tileHeight;
	}

	@Override
	public int nZSlices() {
		return 1; // Z-stacks not currently supported
	}

	@Override
	public double getZSpacingMicrons() {
		return Double.NaN; // Z-stacks not currently supported
	}

	@Override
	public boolean isRGB() {
		return true; // Only RGB currently supported
	}

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) {
		Rectangle region = AwtTools.getBounds(request);
		if (region == null) {
			region = new Rectangle(0, 0, getWidth(), getHeight());
		}
		
		double downsampleFactor = request.getDownsample();
		int level = getClosestDownsampleIndex(getPreferredDownsamples(), downsampleFactor);
		double downsample = downsamples[level];
		int levelWidth = (int)(region.width / downsample + .5);
		int levelHeight = (int)(region.height / downsample + .5);
		BufferedImage img = new BufferedImage(levelWidth, levelHeight, BufferedImage.TYPE_INT_ARGB_PRE);

        int data[] = ((DataBufferInt)img.getRaster().getDataBuffer()).getData();

        try {
			// Create a thumbnail for the region
			if (paintMethod == null)
        		paintMethod = cOpenslide.getMethod("paintRegionARGB", int[].class, long.class, long.class, int.class, int.class, int.class);
        	
//			synchronized (this) {
				paintMethod.invoke(osr, data, region.x, region.y, level, levelWidth, levelHeight);
//			}
			if (GeneralTools.almostTheSame(downsample, downsampleFactor, 0.001))
				return img;
			
			// Rescale if we have to
			int width = (int)(region.width / downsampleFactor + .5);
			int height = (int)(region.height / downsampleFactor + .5);
//			BufferedImage img2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
			BufferedImage img2 = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
			Graphics2D g2d = img2.createGraphics();
			g2d.drawImage(img, 0, 0, width, height, null);
			g2d.dispose();
			return img2;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public int nTimepoints() {
		return 1;
	}

	@Override
	public double getTimePoint(int ind) {
		return 0;
	}

	@Override
	public TimeUnit getTimeUnit() {
		return null;
	}

	@Override
	public List<String> getSubImageList() {
		return Collections.emptyList();
	}

	@Override
	public String getDisplayedImageName() {
		// TODO: Implement associated images for OpenSlide
//		System.err.println("Image names not implemented for OpenSlide yet...");
		return getShortServerName();
	}
	

	@Override
	public boolean usesBaseServer(ImageServer<?> server) {
		return this == server;
	}

	@Override
	public int getBitsPerPixel() {
		return 8; // Only 8-bit RGB images supported
	}
	
	
	@Override
	public boolean containsSubImages() {
		return false;
	}


	@Override
	public Integer getDefaultChannelColor(int channel) {
		return getDefaultRGBChannelColors(channel);
	}

	@Override
	public List<String> getAssociatedImageList() {
		if (associatedImageList == null)
			return Collections.emptyList();
		return associatedImageList;
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		Method method;
		try {
			method = cAssociatedImage.getMethod("toBufferedImage");
			Object object = associatedImages.get(name);
			return (BufferedImage)method.invoke(object);
		} catch (Exception e) {
			e.printStackTrace();
		}
		throw new IllegalArgumentException("Unable to find sub-image with the name " + name);
	}

	@Override
	public File getFile() {
		File file = new File(path);
		if (file.exists())
			return file;
		return null;
	}


}
