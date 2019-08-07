package qupath.lib.images.writers.ome;

import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import loci.formats.FormatException;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.meta.IPyramidStore;
import loci.formats.out.PyramidOMETiffWriter;
import loci.formats.tiff.IFD;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.Color;
import ome.xml.model.primitives.PositiveInteger;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.regions.ImageRegion;
import qupath.lib.regions.RegionRequest;

/**
 * Write OME-TIFF files based on QuPath ImageServers.
 * <p>
 * The files may optionally be pyramidal TIFFs.  Some key metadata is set (e.g. channel names/colors, pixel size), 
 * and an effort is made to support multidimensional images - but this has not been extensively checked.  
 * <p>
 * Currently the magnification is <i>not</i> written, nor is the full OME XML metadata, and images written using this class 
 * should <i>not</i> be assumed to contain all the necessary information, correctly or at all (i.e. always keep your original data files!).
 * <p>
 * Note this requires Bio-Formats v6.0.0 or later.
 * 
 * @author Pete Bankhead
 *
 */
public class OMEPyramidWriter {
	
	private OMEPyramidWriter() {}
	
	private static Logger logger = LoggerFactory.getLogger(OMEPyramidWriter.class);
	
	/**
	 * Enum representing different ways in which channels may be written to a file.
	 */
	public static enum ChannelExportType {
		/**
		 * Leave it up to the writer to choose the appropriate method.
		 */
		DEFAULT,
		/**
		 * Channels are interleaved ('PLANARCONFIG_CONTIG').
		 */
		INTERLEAVED,
		/**
		 * Channels are stored as separate image planes ('PLANARCONFIG_SEPARATE').
		 */
		PLANAR,
		/**
		 * Channels are stored as separate images (this is not yet supported!).
		 */
		IMAGES}
	
	private ImageServer<BufferedImage> server;

	private int x, y, width, height;
	private double[] downsamples;
	private int tileWidth, tileHeight;

	private int zStart = 0;
	private int zEnd = 0;
	private int tStart = 0;
	private int tEnd = 0;
	private int[] channels;
	
	private ByteOrder endian = ByteOrder.BIG_ENDIAN;
	
	private boolean parallelExport = false;
	private boolean keepExisting = false;
	
	private Boolean bigTiff;
	private ChannelExportType channelExportType = ChannelExportType.DEFAULT;

	private String compression = PyramidOMETiffWriter.COMPRESSION_UNCOMPRESSED;
	
	
	/**
	 * Write an OME-TIFF image with the settings defined using the Builder.
	 * 
	 * @param path
	 * @throws FormatException
	 * @throws IOException
	 * 
	 * @see Builder
	 */
	public void writePyramid(final String path) throws FormatException, IOException {

		IMetadata meta = MetadataTools.createOMEXMLMetadata();
		
		int series = 0;

		meta.setImageID("Image:"+series, series);
		meta.setPixelsID("Pixels:"+series, series);
		
		meta.setPixelsBigEndian(ByteOrder.BIG_ENDIAN.equals(endian), series);
		
		meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, series);
		switch (server.getPixelType()) {
		case UINT8:
			meta.setPixelsType(PixelType.UINT8, series);
			break;
		case UINT16:
			meta.setPixelsType(PixelType.UINT16, series);
			break;
		case FLOAT32:
			meta.setPixelsType(PixelType.FLOAT, series);
			break;
		case FLOAT64:
			meta.setPixelsType(PixelType.DOUBLE, series);
			break;
		default:
			throw new IOException("Cannot convert pixel type value of " + server.getPixelType() + " into a valid OME PixelType");
		}
		meta.setPixelsSizeX(new PositiveInteger((int)(width / downsamples[0])), series);
		meta.setPixelsSizeY(new PositiveInteger((int)(height / downsamples[0])), series);

		// Currently, only support single plane
		int sizeZ = zEnd - zStart;
		int sizeT = tEnd - tStart;
		if (sizeZ <= 0)
			throw new IllegalArgumentException("Need to specify positive z-slice range (non-inclusive): requested start " + zStart + " and end " + zEnd);
		if (sizeT <= 0)
			throw new IllegalArgumentException("Need to specify positive time point range (non-inclusive): requested start " + tStart + " and end " + tEnd);
		meta.setPixelsSizeZ(new PositiveInteger(sizeZ), series);
		meta.setPixelsSizeT(new PositiveInteger(sizeT), series);
		
		int nSamples = 1;
		int nChannels = this.channels.length;
		boolean isRGB = server.isRGB() && Arrays.equals(channels, new int[] {0, 1, 2});
		boolean isInterleaved = false;
		if (channelExportType == ChannelExportType.DEFAULT) {
			if (isRGB)
				channelExportType = ChannelExportType.INTERLEAVED;
			else
				channelExportType = ChannelExportType.PLANAR;				
		}

		switch (channelExportType) {
		case IMAGES:
			if (nChannels > 1) {
				logger.warn("Exporting channels to individual images not yet supported! Will use the default...");
			}
		case PLANAR:
			break;
		case INTERLEAVED:
		case DEFAULT:
		default:
			isInterleaved = nChannels > 1;
			nSamples = nChannels;
			break;
		}
		
		if (channels.length <= 0)
			throw new IllegalArgumentException("No channels specified for export!");

		//		nChannels = 2;

		// Set channel colors
		meta.setPixelsSizeC(new PositiveInteger(nChannels), series);
		if (isRGB) {
			meta.setChannelID("Channel:0", series, 0);			
			meta.setPixelsInterleaved(isInterleaved, series);
			meta.setChannelSamplesPerPixel(new PositiveInteger(nSamples), series, 0);	
		} else {
			meta.setChannelSamplesPerPixel(new PositiveInteger(nSamples), series, 0);
			meta.setPixelsInterleaved(isInterleaved, series);
			for (int c = 0; c < nChannels; c++) {
				meta.setChannelID("Channel:0:" + c, series, c);			
//				meta.setChannelSamplesPerPixel(new PositiveInteger(nSamples), series, c);
//				Integer color = server.getChannels().get(c).getColor();
				ImageChannel channel = server.getChannel(c);
				Integer color = channel.getColor();
				meta.setChannelColor(new Color(
						ColorTools.red(color),
						ColorTools.green(color),
						ColorTools.blue(color),
						0
						), series, c);
				meta.setChannelName(channel.getName(), series, c);
			}			
		}

		// Set physical units, if we have them
		PixelCalibration cal = server.getPixelCalibration();
		if (cal.hasPixelSizeMicrons()) {
			meta.setPixelsPhysicalSizeX(new Length(cal.getPixelWidthMicrons() * downsamples[0], UNITS.MICROMETER), series);
			meta.setPixelsPhysicalSizeY(new Length(cal.getPixelHeightMicrons() * downsamples[0], UNITS.MICROMETER), series);
		}
		if (!Double.isNaN(cal.getZSpacingMicrons()))
			meta.setPixelsPhysicalSizeZ(new Length(cal.getZSpacingMicrons(), UNITS.MICROMETER), series);

		// TODO: Consider setting the magnification

		// Set resolutions
		for (int level = 0; level < downsamples.length; level++) {
			double d = downsamples[level];
			int w = (int)(width / d);
			int h = (int)(height / d);
			((IPyramidStore)meta).setResolutionSizeX(new PositiveInteger(w), series, level);
			((IPyramidStore)meta).setResolutionSizeY(new PositiveInteger(h), series, level);
		}

		try (PyramidOMETiffWriter writer = new PyramidOMETiffWriter()) {
			
			logger.info("Writing {} to {} with compression {}", ServerTools.getDisplayableImageName(server), path, compression);
			
			int nPlanes = (nChannels / nSamples) * sizeZ * sizeT;
			long nPixels = (long)width * (long)height * nSamples * nPlanes;

			writer.setCompression(compression);
			writer.setWriteSequentially(true); // Setting this to false can be problematic!
			writer.setMetadataRetrieve(meta);
			
			// Switch automatically to bigtiff is we have a large image & it isn't otherwise specified what to do
			if (bigTiff == null) {
				logger.debug("Setting 'Big TIFF' to true...");
				bigTiff = nPixels * (server.getPixelType().getBytesPerPixel()) > Integer.MAX_VALUE/2;
			}
			if (Boolean.TRUE.equals(bigTiff)) {
				writer.setBigTiff(true);				
			}
			
			int tileWidth = writer.setTileSizeX(this.tileWidth);
			int tileHeight = writer.setTileSizeY(this.tileHeight);

			File file = new File(path);
			if (file.exists() && !keepExisting) {
				logger.warn("Deleting existing file {}", path);
				file.delete();
			}
			
			writer.setInterleaved(isInterleaved);

			writer.setId(path);
			writer.setSeries(series);
			
			Map<Integer, IFD> map = new HashMap<>();

			writer.setSeries(series);
			for (int level = 0; level < downsamples.length; level++) {
				
				writer.setResolution(level);
				
				// Preallocate any IFD
				map.clear();
				for (int i = 0; i < nPlanes; i++) {
					IFD ifd = new IFD();
					ifd.put(IFD.TILE_WIDTH, tileWidth);
					ifd.put(IFD.TILE_LENGTH, tileHeight);
					if (nSamples > 1 && !isRGB)
						ifd.put(IFD.EXTRA_SAMPLES, new short[nSamples-1]);
					map.put(Integer.valueOf(i), ifd);
				}

				double d = downsamples[level];
				
				logger.info("Writing resolution {} of {} (downsample={})", level, downsamples.length, d);
				
				int w = (int)(this.width / d);
				int h = (int)(this.height / d);

				int tInc = tEnd >= tStart ? 1 : -1;
				int zInc = zEnd >= zStart ? 1 : -1;
				int effectiveSizeC = nChannels / nSamples;
								
				int ti = 0;
				for (int t = tStart; t < tEnd; t += tInc) {
					int zi = 0;
					for (int z = zStart; z < zEnd; z += zInc) {
						
						/*
						 *  It appears we can use parallelization for tile writing (thanks to synchronization in the writer),
						 *  provided we write the (0,0) tile first.
						 */
						
						// Create a list of all required requests, extracting the first
						List<ImageRegion> regions = new ArrayList<>();
						for (int yy = 0; yy < h; yy += tileHeight) {
							int hh = Math.min(h - yy, tileHeight);
							for (int xx = 0; xx < w; xx += tileWidth) {
								int ww = Math.min(w - xx, tileWidth);
								regions.add(ImageRegion.createInstance(xx, yy, ww, hh, z, t));
							}
						}
						ImageRegion firstRegion = regions.remove(0);
						
						// Loop through effective channels (which is 1 if we are writing interleaved)
						for (int ci = 0; ci < effectiveSizeC; ci++) {
							
							int plane = ti * sizeZ * effectiveSizeC + zi * effectiveSizeC + ci;
							IFD ifd = map.get(Integer.valueOf(plane));
							int[] localChannels = effectiveSizeC == channels.length ? new int[] {channels[ci]} : channels;
						
							logger.info("Writing plane {}/{}", plane+1, nPlanes);
								
							// We *must* write the first region first
							writeRegion(writer, plane, ifd, firstRegion, d, isRGB, localChannels);
							if (!regions.isEmpty()) {
								var stream = parallelExport ? regions.parallelStream() : regions.stream();
								stream.forEach(region -> {
									try {
										writeRegion(writer, plane, ifd, region, d, isRGB, localChannels);
									} catch (Exception e) {
										logger.warn(String.format(
												"Error writing %s (downsample=%.2f)",
												region.toString(), d),
												e);
									}
								});
							}
						}
						zi++;
					}
					ti++;
				}
			}
			logger.trace("Image count: {}", meta.getImageCount());
			logger.trace("Plane count: {}", writer.getPlaneCount());
			logger.trace("Resolution count: {}", writer.getResolutionCount());
		}
	}

	
	/**
	 * Convert a region in the export coordinate space for a specific plane 
	 * into a RegionRequest for the original ImageServer.
	 * 
	 * @param region
	 * @return
	 */
	RegionRequest downsampledRegionToRequest(ImageRegion region, double downsample) {
		return RegionRequest.createInstance(
				server.getPath(), downsample, 
				(int)(region.getX() * downsample) + x, 
				(int)(region.getY() * downsample) + y, 
				(int)(region.getWidth() * downsample), 
				(int)(region.getHeight() * downsample),
				region.getZ(),
				region.getT());
	}
	
	
	private void writeRegion(PyramidOMETiffWriter writer, int plane, IFD ifd, ImageRegion region, double downsample, boolean isRGB, int[] channels) throws FormatException, IOException {
		RegionRequest request = downsampledRegionToRequest(region, downsample);
		BufferedImage img = server.readBufferedImage(request);
		
		int bytesPerPixel = server.getPixelType().getBytesPerPixel();
		int nChannels = channels.length;
		if (img == null) {
			byte[] zeros = new byte[region.getWidth() * region.getHeight() * bytesPerPixel * nChannels];
			writer.saveBytes(plane, zeros, ifd, region.getX(), region.getY(), region.getWidth(), region.getHeight());
			return;
		}
		
		int ww = img.getWidth();
		int hh = img.getHeight();
		ByteBuffer buf = ByteBuffer.allocate(ww * hh * bytesPerPixel * nChannels)
				.order(endian);
		
		if (isRGB) {
			Object pixelBuffer = getPixelBuffer(ww*hh);
			if (!(pixelBuffer instanceof int[]))
				pixelBuffer = null;
			int[] rgba = img.getRGB(0, 0, ww, hh, (int[])pixelBuffer, 0, ww);
			for (int val : rgba) {
				buf.put((byte)ColorTools.red(val));
				buf.put((byte)ColorTools.green(val));
				buf.put((byte)ColorTools.blue(val));
			}
			writer.saveBytes(plane, buf.array(), ifd, region.getX(), region.getY(), ww, hh);
		} else {
			for (int ci = 0; ci < channels.length; ci++) {
				int c = channels[ci];
				int ind = ci * bytesPerPixel;
				channelToBuffer(img.getRaster(), c, buf, ind, channels.length * bytesPerPixel);
			}
			writer.saveBytes(plane, buf.array(), ifd, region.getX(), region.getY(), ww, hh);
		}
	}
	
	/**
	 * Extract pixels to a ByteBuffer.
	 * 
	 * @param raster the WritableRaster containing the pixel data
	 * @param c channel (band) number
	 * @param buf the buffer to which the pixels should be extracted
	 * @param startInd the starting index in the buffer, where the first pixel should be written
	 * @param inc the increment (in bytes) between each pixel that is written
	 */
	boolean channelToBuffer(WritableRaster raster, int c, ByteBuffer buf, int startInd, int inc) {
		int ind = startInd;
		int ww = raster.getWidth();
		int hh = raster.getHeight();
		int n = ww*hh;
		Object pixelBuffer = getPixelBuffer(n);
		switch (server.getPixelType()) {
		case UINT8:
		case UINT16:
			int[] pixelsInt = pixelBuffer instanceof int[] ? (int[])pixelBuffer : null;
			if (pixelsInt == null || pixelsInt.length < n)
				pixelsInt = new int[n];
			pixelsInt = raster.getSamples(0, 0, ww, hh, c, pixelsInt);
			if (server.getPixelType().bitsPerPixel() == 8) {
				for (int i = 0; i < n; i++) {
					buf.put(ind, (byte)pixelsInt[i]);
					ind += inc;
				}
			} else {
				for (int i = 0; i < n; i++) {
					buf.putShort(ind, (short)pixelsInt[i]);
					ind += inc;
				}
			}
			return true;
		case FLOAT32:
			float[] pixelsFloat = pixelBuffer instanceof float[] ? (float[])pixelBuffer : null;
			if (pixelsFloat == null || pixelsFloat.length < n)
				pixelsFloat = new float[n];
			pixelsFloat = raster.getSamples(0, 0, ww, hh, c, pixelsFloat);
			for (int i = 0; i < n; i++) {
				buf.putFloat(ind, pixelsFloat[i]);
				ind += inc;
			}
			return true;
		case FLOAT64:
			double[] pixelsDouble = pixelBuffer instanceof double[] ? (double[])pixelBuffer : null;
			if (pixelsDouble == null || pixelsDouble.length < n)
				pixelsDouble = new double[n];
			pixelsDouble = raster.getSamples(0, 0, ww, hh, c, pixelsDouble);
			for (int i = 0; i < n; i++) {
				buf.putDouble(ind, pixelsDouble[i]);
				ind += inc;
			}
			return true;
		}
		return false;
	}
	
	private ThreadLocal<Object> pixelBuffer = new ThreadLocal<>();
	
	/**
	 * Get a primitive array of the specified length for extracting pixels from the current server.
	 * 
	 * @param length
	 * @return
	 */
	Object getPixelBuffer(int length) {
		Object originalBuffer = this.pixelBuffer.get();
		Object updatedBuffer = null;
		int bpp = server.getPixelType().bitsPerPixel();
		if (server.isRGB() || bpp == 8 || bpp == 16) {
			updatedBuffer = ensureIntArray(originalBuffer, length);
		} else if (bpp == 32) {
			updatedBuffer = ensureFloatArray(originalBuffer, length);
		} else if (bpp == 64) {
			updatedBuffer = ensureDoubleArray(originalBuffer, length);
		}
		if (updatedBuffer != originalBuffer)
			pixelBuffer.set(updatedBuffer);
		return updatedBuffer;
	}
	
	static int[] ensureIntArray(Object array, int length) {
		if (!(array instanceof int[]) || ((int[])array).length != length)
			return new int[length];
		return (int[])array;
	}
	
	static float[] ensureFloatArray(Object array, int length) {
		if (!(array instanceof float[]) || ((float[])array).length != length)
			return new float[length];
		return (float[])array;
	}
	
	static double[] ensureDoubleArray(Object array, int length) {
		if (!(array instanceof double[]) || ((double[])array).length != length)
			return new double[length];
		return (double[])array;
	}
	
	

	/**
	 * Builder class to define parameters when exporting an image region as OME-TIFF,
	 * possibly as an image pyramid.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	public static class Builder {

		private OMEPyramidWriter writer = new OMEPyramidWriter();

		/**
		 * Constructor.
		 * @param server the ImageServer from which pixels will be requested and written to the OME-TIFF.
		 */
		public Builder(ImageServer<BufferedImage> server) {
			writer.server = server;
			writer.x = 0;
			writer.y = 0;
			writer.width = server.getWidth();
			writer.height = server.getHeight();
			writer.downsamples = server.getPreferredDownsamples();
			if (server.getMetadata().getPreferredTileWidth() == server.getWidth() && server.getMetadata().getPreferredTileHeight() == server.getHeight()) {
				writer.tileWidth = server.getMetadata().getPreferredTileWidth();
				writer.tileHeight = server.getMetadata().getPreferredTileHeight();
			} else {
				writer.tileWidth = 256;
				writer.tileHeight = 256;
			}
			writer.zStart = 0;
			writer.zEnd = server.nZSlices();
			writer.tStart = 0;
			writer.tEnd = server.nTimepoints();
			if (server.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.CLASSIFICATION)
				writer.channels = new int[] {0};
			else
				writer.channels = IntStream.range(0, server.nChannels()).toArray();
		}
		
		/**
		 * Request that any existing file with the same path is kept, rather than being deleted.
		 * @return
		 */
		public Builder keepExistingFile() {
			writer.keepExisting = true;
			return this;
		}
		
		/**
		 * Request that channels are written as separate image planes.
		 * @return
		 */
		public Builder channelsPlanar() {
			writer.channelExportType = ChannelExportType.PLANAR;
			return this;
		}

		/**
		 * Request that channels are written interleaved within a single image plane.
		 * @return
		 */
		public Builder channelsInterleaved() {
			writer.channelExportType = ChannelExportType.INTERLEAVED;
			return this;
		}

		/**
		 * Request that channels are written as separate images.
		 * @return
		 */
		public Builder channelsImages() {
			writer.channelExportType = ChannelExportType.IMAGES;
			return this;
		}

		/**
		 * Request that the image is written in BigTIFF format.
		 * @return
		 */
		public Builder bigTiff() {
			writer.bigTiff = Boolean.TRUE;
			return this;
		}

		/**
		 * Specifiy whether the image should be written in BigTIFF format.
		 * @return
		 */
		public Builder bigTiff(boolean doBigTiff) {
			writer.bigTiff = doBigTiff;
			return this;
		}

		/**
		 * Request the output compression type.
		 * @param compression
		 * @return
		 */
		public Builder compression(final String compression) {
			writer.compression = compression;
			return this;
		}
		
		/**
		 * Request the default lossy compression method. Not all servers support lossy compression 
		 * (e.g. non-RGB servers).
		 * @return
		 */
		public Builder lossyCompression() {
			writer.compression = getDefaultLossyCompressionType(writer.server);
			return this;
		}
		
		/**
		 * Request the default lossless compression method.
		 * @return
		 */
		public Builder losslessCompression() {
			writer.compression = getDefaultLosslessCompressionType(writer.server);
			return this;
		}
		
		/**
		 * Parallelize tile export, if possible.
		 * 
		 * @return
		 */
		public Builder parallelize() {
			return parallelize(true);
		}

		/**
		 * Specify if tile export should be parallelized if possible.
		 * 
		 * @param doParallel
		 * @return
		 */
		public Builder parallelize(boolean doParallel) {
			writer.parallelExport = doParallel;
			return this;
		}

		/**
		 * Request that all z-slices are exported.
		 * @return
		 */
		public Builder allZSlices() {
			return this.zSlices(0, writer.server.nZSlices());
		}

		/**
		 * Specify the z-slice to export.
		 * @param z
		 * @return
		 */
		public Builder zSlice(int z) {
			return this.zSlices(z, z+1);
		}

		/**
		 * Specify the start (inclusive) and end (exclusive) z-slices.
		 * @param zStart
		 * @param zEnd
		 * @return
		 */
		public Builder zSlices(int zStart, int zEnd) {
			writer.zStart = zStart;
			writer.zEnd = zEnd;
			return this;
		}

		/**
		 * Specify a single timepoint to be written from a time series.
		 * @param t the index identifying the requested timepoint
		 * @return
		 */
		public Builder timePoint(int t) {
			return this.timePoints(t, t+1);
		}
		
		/**
		 * Request that all timepoints of a time series will be written.
		 * @return
		 */
		public Builder allTimePoints() {
			return this.timePoints(0, writer.server.nTimepoints());
		}

		/**
		 * Specify a range of timepoints to be written from a time series.
		 * @param tStart first timepoint (inclusive)
		 * @param tEnd last timepoint (exclusive)
		 * @return
		 */
		private Builder timePoints(int tStart, int tEnd) {
			writer.tStart = tStart;
			writer.tEnd = tEnd;
			return this;
		}

		/**
		 * Define the region to export based on a bounding box.
		 * 
		 * @param x
		 * @param y
		 * @param width
		 * @param height
		 * @return
		 */
		public Builder region(int x, int y, int width, int height) {
			writer.x = x;
			writer.y = y;
			writer.width = width;
			writer.height = height;
			return this;
		}

		/**
		 * Define the region to export, including the z-slice and time point.
		 * 
		 * @param region
		 * @return
		 * 
		 * @see #region(int, int, int, int)
		 * @see #timePoint(int)
		 * @see #zSlice(int)
		 * @see #zSlices(int, int)
		 */
		public Builder region(ImageRegion region) {
			return this.region(region.getX(), region.getY(), region.getWidth(), region.getHeight()).zSlice(region.getZ()).timePoint(region.getT());
		}

		/**
		 * Define the requested tile size (width == height).
		 * <p>
		 * This is only a suggestion, and the OME reader may override it if the value is unsupported.
		 * 
		 * @param tileSize
		 * @return
		 */
		public Builder tileSize(int tileSize) {
			return this.tileSize(tileSize, tileSize);
		}

		/**
		 * Define the requested tile width and height.
		 * <p>
		 * This is only a suggestion, and the OME reader may override it if the value is unsupported.
		 * 
		 * @param tileWidth
		 * @param tileHeight
		 * @return
		 */
		public Builder tileSize(int tileWidth, int tileHeight) {
			writer.tileWidth = tileWidth;
			writer.tileHeight = tileHeight;
			return this;
		}

		/**
		 * Specify downsample factors to use in the final pyramid.
		 * <p>
		 * Note that the downsample values provided will be sorted in ascending order.
		 * @param downsamples
		 * @return
		 */
		public Builder downsamples(double... downsamples) {
			writer.downsamples = downsamples;
			return this;
		}
		
		/**
		 * Downsample by factors of 2.
		 * <p>
		 * Note that the highest downsample value will depend on the tile size, 
		 * so the tile size should be set first.
		 * 
		 * @return
		 * @see #scaledDownsampling(double)
		 */
		public Builder dyadicDownsampling() {
			return this.scaledDownsampling(1.0, 2.0);
		}
		
		/**
		 * Downsample by specific increasing factor each time (e.g. if scale == 2, then downsamples will be 1, 2, 4, 8...).
		 * <p>
		 * Note that the highest downsample value will depend on the tile size, 
		 * so the tile size should be set first.
		 * 
		 * @return
		 * @see #scaledDownsampling(double)
		 */
		public Builder scaledDownsampling(double scale) {
			return this.scaledDownsampling(1.0, scale);
		}

		/**
		 * Downsample by specific increasing factor each time, with a specified initial downsample value 
		 * (e.g. if scale == 2, then downsamples will be minDownsample, minDownsample*2, minDownsample*4...).
		 * <p>
		 * Note that the highest downsample value will depend on the tile size, 
		 * so the tile size should be set first.
		 * 
		 * @return
		 * @see #scaledDownsampling(double)
		 */
		public Builder scaledDownsampling(double minDownsample, double scale) {
			// Calculate default downsamples, if we need to
//			if (writer.downsamples == null) {
				List<Double> downsampleList = new ArrayList<>();
				double nextDownsample = minDownsample;
				do {
					downsampleList.add(nextDownsample);
					nextDownsample *= scale;
				} while ((int)(writer.width / nextDownsample) > writer.tileWidth && (int)(writer.height / nextDownsample) > writer.tileHeight);
				writer.downsamples = downsampleList.stream().mapToDouble(d -> d).toArray();
//			}
			return this;
		}

		/**
		 * Override default of writing all channels in their original order to be able to specify which 
		 * channels are output, and in which order.
		 * @param channels zero-based channel indices for all channels that should be exported, in the desired export order.
		 * @return
		 */
		public Builder channels(int... channels) {
			writer.channels = channels;
			return this;
		}

		/**
		 * Create an OMEPyramidWriter to write the OME-TIFF.
		 * @return
		 */
		public OMEPyramidWriter build() {
			// Sanity check downsamples, and remove those that are too much
			Arrays.sort(writer.downsamples);
			int lastDownsample = 1;
			while (lastDownsample < writer.downsamples.length && 
					writer.width / writer.downsamples[lastDownsample] > 16 &&
					writer.height / writer.downsamples[lastDownsample] > 16) {
				lastDownsample++;
			}
			if (lastDownsample < writer.downsamples.length)
				writer.downsamples = Arrays.copyOf(writer.downsamples, lastDownsample);
			
			return writer;
		}

	}



	/**
	 * Get all the available compression types (that we know of) for the OME TIFF.
	 * @return
	 */
	static Collection<String> getAvailableCompressionTypes() {
		return Arrays.asList(
				PyramidOMETiffWriter.COMPRESSION_UNCOMPRESSED,
				PyramidOMETiffWriter.COMPRESSION_JPEG,
				PyramidOMETiffWriter.COMPRESSION_J2K,
				PyramidOMETiffWriter.COMPRESSION_J2K_LOSSY,
				PyramidOMETiffWriter.COMPRESSION_LZW,
				PyramidOMETiffWriter.COMPRESSION_ZLIB
				);
	}

	/**
	 * Get all available compression types compatible with a specific ImageServer.
	 * @param server
	 * @return
	 */
	static Set<String> getCompatibleCompressionTypes(final ImageServer<BufferedImage> server) {

		Set<String> set = new LinkedHashSet<>(getAvailableCompressionTypes());
		// Remove some types of compression that aren't expected to work
		if (server.getPixelType().getBytesPerPixel() > 2) {
			set.remove(PyramidOMETiffWriter.COMPRESSION_JPEG);
			set.remove(PyramidOMETiffWriter.COMPRESSION_J2K);
			set.remove(PyramidOMETiffWriter.COMPRESSION_J2K_LOSSY);
		} else if (server.getPixelType().bitsPerPixel() != 8 || (server.nChannels() > 1 && !server.isRGB())) {
			set.remove(PyramidOMETiffWriter.COMPRESSION_JPEG);
		}

		return Collections.unmodifiableSet(set);
	}

	/**
	 * Get a String representing the 'Uncompressed' compression type.
	 * @return
	 */
	static String getUncompressedType() {
		return PyramidOMETiffWriter.COMPRESSION_UNCOMPRESSED;
	}

	/**
	 * Check if a compression type is known to be lossy.
	 * @param type
	 * @return
	 */
	static boolean isLossyCompressionType(final String type) {
		return Arrays.asList(
				PyramidOMETiffWriter.COMPRESSION_JPEG, 
				PyramidOMETiffWriter.COMPRESSION_J2K_LOSSY
				).contains(type);
	}

	/**
	 * Get the default lossless compression type for an ImageServer.
	 * <p>
	 * Currently, this always returns LZW because it is well-supported for TIFF.  
	 * However this may change in the future.
	 * 
	 * @param server
	 * @return
	 */
	static String getDefaultLosslessCompressionType(final ImageServer<BufferedImage> server) {
		return PyramidOMETiffWriter.COMPRESSION_LZW;
	}
	
	/**
	 * Get the default (possibly) lossy compression type for an ImageServer.
	 * <p>
	 * Currently, for an RGB server this will return JPEG while for another 8-bit or 16-bit 
	 * image this will return J2K lossy compression.  Otherwise it returns {@link #getDefaultLosslessCompressionType(ImageServer)}. 
	 * However this may change in the future.
	 * 
	 * @param server
	 * @return
	 */
	static String getDefaultLossyCompressionType(final ImageServer<BufferedImage> server) {
		if (server.isRGB())
			return PyramidOMETiffWriter.COMPRESSION_JPEG;
		if (server.getPixelType().bitsPerPixel() <= 16)
			return PyramidOMETiffWriter.COMPRESSION_J2K_LOSSY;
		// Don't try another lossy compression method...
		return getDefaultLosslessCompressionType(server);
	}
	
	/**
	 * Static helper method to write an OME-TIFF pyramidal image for a whole image with the specified compression.
	 * 
	 * @param server
	 * @param path
	 * @param compression
	 * @throws FormatException
	 * @throws IOException
	 */
	public static void writePyramid(ImageServer<BufferedImage> server, String path, String compression) throws FormatException, IOException {
		new Builder(server).compression(compression).build().writePyramid(path);
	}
	
	/**
	 * Static helper method to write an OME-TIFF pyramidal image for a defined region with the specified compression.
	 * 
	 * @param server
	 * @param path
	 * @param compression
	 * @throws FormatException
	 * @throws IOException
	 */
	public static void writePyramid(ImageServer<BufferedImage> server, String path, String compression, ImageRegion region) throws FormatException, IOException {
		new Builder(server).compression(compression).region(region).build().writePyramid(path);
	}

}
