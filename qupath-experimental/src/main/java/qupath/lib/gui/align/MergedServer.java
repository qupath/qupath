package qupath.lib.gui.align;

import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import qupath.lib.awt.color.model.ColorModelFactory;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.ColorTools;
import qupath.lib.images.DefaultPathImage;
import qupath.lib.images.PathImage;
import qupath.lib.images.servers.AbstractImageServer;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.regions.RegionRequest;

/**
 * An ImageServer that merges separate RGB color images, deconvolving them and using the second stain
 * as a pseudo-fluorescence channel.
 *
 * One 'main' image is also used to get a counterstain.
 * 
 * @author Pete Bankhead
 */
public class MergedServer extends AbstractImageServer<BufferedImage> {

	private Logger logger = LoggerFactory.getLogger(MergedServer.class);

	/**
	 * We need one server from which to determine key parameters, e.g. width, height, downsamples
	 */
	private ServerTransformWrapper mainServer;
	private ImageServer<BufferedImage> server;
	private List<ServerTransformWrapper> wrappers = new ArrayList<>();
	
	/**
	 * Redefine the base colors, giving BGR rather than RGB.
	 * The reason is that the nuclear counterstain is added first, and the 'natural' color 
	 * choice for it is blue (rather than the red of RGB).
	 */
	private List<Integer> baseColors = Arrays.asList(
			ColorTools.makeRGB(0, 0, 255),
			ColorTools.makeRGB(0, 255, 0),
			ColorTools.makeRGB(255, 0, 0)
			);

	private String path;
	private int nChannels;

	MergedServer(List<ServerTransformWrapper> wrappers, ServerTransformWrapper serverMain) {
		logger.info("Number of wrappers: " + wrappers.size());
		this.wrappers.addAll(wrappers);
		// Take the first server if none specified
		if (serverMain == null) {
			for (ServerTransformWrapper wrapper : wrappers) {
				if (wrapper.isIdentityTransform()) {
					serverMain = wrapper;
				}
			}
		}
		if (serverMain == null) {
			throw new IllegalArgumentException("Unable to find a 'master' image - at least one needs to have an identity transform!");
		}
		// Make sure this is the first server
		this.wrappers.remove(serverMain);
		this.wrappers.add(0, serverMain);

		this.mainServer = serverMain;
		this.server = mainServer.server;
		this.nChannels = this.wrappers.size() + 1; // Got one channel per image, and then an extra one for the counterstain
		this.path = server.getPath() + '-' + (int)(Math.random() * 100000);
		logger.info("Main server: {}", server.getPath());
		logger.info("Number of channels: " + this.nChannels + " (" +wrappers.size() + ")");
	}

	public String getPath() {
		return path;
	}

	@Override
	public int nChannels() {
		return nChannels;
	}


	@Override
	public double[] getPreferredDownsamples() {
		return server.getPreferredDownsamples();
	}

	@Override
	public boolean isRGB() {
		return false;
	}

	@Override
	public double getTimePoint(int ind) {
		return server.getTimePoint(ind);
	}

	@Override
	public PathImage<BufferedImage> readRegion(RegionRequest request) {
		return new DefaultPathImage<BufferedImage>(this, request, readBufferedImage(request));
	}

	@Override
	public BufferedImage readBufferedImage(RegionRequest request) {

		double scale = 100;

		// Get pixels for the main server first
		BufferedImage img = server.readBufferedImage(request);
		int width = img.getWidth();
		int height = img.getHeight();

		// Get the color deconvolved channels
		ServerTransformWrapper wrapper = wrappers.get(0);
		int[] rgb = img.getRGB(0, 0, width, height, null, 0, width);
		WritableRaster raster = Raster.createInterleavedRaster(DataBuffer.TYPE_BYTE, width, height, nChannels, null);
		byte[] buffer = ((DataBufferByte)raster.getDataBuffer()).getData();

		float[] deconvolved = ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_1, null, wrapper.stains);
		for (int i = 0; i < width * height; i++) {
			int val = ColorTools.do8BitRangeCheck(deconvolved[i] * scale);
			buffer[i*nChannels] = (byte)val;
		}

		deconvolved = ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_2, deconvolved, wrapper.stains);
		for (int i = 0; i < width * height; i++) {
			int val = ColorTools.do8BitRangeCheck(deconvolved[i] * scale);
			buffer[i*nChannels+1] = (byte)val;
		}


		// Apply transform to the specified shape
		Rectangle rect = new Rectangle(request.getX(), request.getY(), request.getWidth(), request.getHeight());

		for (int c = 1; c < wrappers.size(); c++) {
			wrapper = wrappers.get(c);
			ImageServer<BufferedImage> server2 = wrapper.server;
			rect.setBounds(request.getX(), request.getY(), request.getWidth(), request.getHeight());
			Shape shape = wrapper.getTransform().createTransformedShape(rect);
			rect = shape.getBounds();
			// Make sure the coordinates fit within the requested image
			int x = rect.x;
			int y = rect.y;
			int x2 = rect.x + rect.width;
			int y2 = rect.y + rect.height;
			if (x < 0)
				x = 0;
			if (y < 0)
				y = 0;
			if (x2 > server2.getWidth())
				x2 = server2.getWidth();
			if (y2 > server2.getHeight())
				y2 = server2.getHeight();
			RegionRequest request2 = RegionRequest.createInstance(server2.getPath(), request.getDownsample(), x, y, x2-x, y2-y);

			// Read the image
			BufferedImage imgTemp = server2.readBufferedImage(request2);

			// Create an image of the *expected* size
			// Draw the transformed image
			BufferedImage img2 = new BufferedImage(width, height, img.getType());
			Graphics2D g2d = img2.createGraphics();
			g2d.scale(1.0/request.getDownsample(), 1.0/request.getDownsample());
			g2d.translate(-request.getX(), -request.getY());
			g2d.transform(wrapper.getInverseTransform());
			g2d.drawImage(imgTemp, request2.getX(), request2.getY(), request2.getWidth(), request2.getHeight(), null);
			g2d.dispose();

			// Add to buffer
			rgb = img2.getRGB(0, 0, width, height, rgb, 0, width);
			deconvolved = ColorTransformer.getTransformedPixels(rgb, ColorTransformMethod.Stain_2, deconvolved, wrapper.stains);
			for (int i = 0; i < width * height; i++) {
				int val = ColorTools.do8BitRangeCheck(deconvolved[i] * scale);
				buffer[i*nChannels+c+1] = (byte)val;
			}

		}

		return new BufferedImage(ColorModelFactory.getDummyColorModel(8), raster, true, null);
	}

	@Override
	public String getServerType() {
		return "Merged servers";
	}

	@Override
	public List<String> getSubImageList() {
		return Collections.emptyList();
	}

	@Override
	public List<String> getAssociatedImageList() {
		return Collections.emptyList();
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		return null;
	}

	@Override
	public  String getDisplayedImageName() {
		return null;
	}

	@Override
	public boolean containsSubImages() {
		return false;
	}

	@Override
	public boolean usesBaseServer(ImageServer<?> server) {
		return this == server;
	}

	@Override
	public File getFile() {
		return new File(server.getPath());
	}

	@Override
	public int getBitsPerPixel() {
		return server.getBitsPerPixel();
	}

	@Override
	public Integer getDefaultChannelColor(int channel) {
		if (channel < baseColors.size())
			return baseColors.get(channel);
		return super.getExtendedDefaultChannelColor(channel);
	}

	@Override
	public ImageServerMetadata getMetadata() {
		return server.getMetadata();
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		throw new UnsupportedOperationException("Can't set metadata here, sorry!");
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return server.getOriginalMetadata();
	}


	public static class ServerTransformWrapper {
		
		private ImageServer<BufferedImage> server;
		private String name;
		private AffineTransform transform;
		private AffineTransform transformInverse;
		private ColorDeconvolutionStains stains;
		
		
		public static String toJSON(final ServerTransformWrapper wrapper) {
			return new GsonBuilder().create().toJson(
					new ServerTransformJSONable(wrapper)
					);
		}
		
		public static ServerTransformWrapper fromJSON(final String json) throws JsonSyntaxException, NoninvertibleTransformException {
			return new GsonBuilder().create().fromJson(json, ServerTransformJSONable.class).toServerTransformWrapper();
		}
		
		
		public ServerTransformWrapper(final ImageServer<BufferedImage> server, final String name,
				final AffineTransform transform, ColorDeconvolutionStains stains) throws NoninvertibleTransformException {
			this.server = server;
			this.name = name;
			this.transform = transform;
			this.transformInverse = transform.createInverse();
			this.stains = stains;
		}
		
		public ImageServer<BufferedImage> getServer() {
			return server;
		}
		
		public ColorDeconvolutionStains getStains() {
			return stains;
		}
		
		public String getName() {
			return name;
		}
		
		public boolean isIdentityTransform() {
			return transform.isIdentity();
		}
		
		public AffineTransform getTransform() {
			return transform;
		}
		
		public AffineTransform getInverseTransform() {
			return transformInverse;
		}
		
		
		
		private static class ServerTransformJSONable {
			
			private String path;
			private String name;
			private double[] transform;
			private String stainsString;
			
			ServerTransformJSONable(ServerTransformWrapper wrapper) {
				this.path = wrapper.getServer().getPath();
				this.name = wrapper.getName();
				this.stainsString = ColorDeconvolutionStains.getColorDeconvolutionStainsAsString(
						wrapper.getStains(), 6);
			}
			
			
			ServerTransformWrapper toServerTransformWrapper() throws NoninvertibleTransformException {
				ImageServer<BufferedImage> server = ImageServerProvider.buildServer(path, BufferedImage.class);
				ColorDeconvolutionStains stains = ColorDeconvolutionStains.parseColorDeconvolutionStainsArg(stainsString);
				AffineTransform affine = new AffineTransform(
						transform[0], transform[2], transform[4],
						transform[1], transform[3], transform[5]
						);
				return new ServerTransformWrapper(
						server,
						name,
						affine,
						stains);
			}
			
			
		}
		
	}
	

}