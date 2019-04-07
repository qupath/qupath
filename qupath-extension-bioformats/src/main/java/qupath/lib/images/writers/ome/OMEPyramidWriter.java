package qupath.lib.images.writers.ome;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
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
import qupath.lib.images.servers.ImageServer;
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
	
	private ImageServer<BufferedImage> server;

	private int x, y, width, height;
	private double[] downsamples;
	private int tileWidth, tileHeight;

	private int zStart = 0;
	private int zEnd = 0;
	private int tStart = 0;
	private int tEnd = 0;
	private int[] channels;

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

		meta.setPixelsBigEndian(Boolean.TRUE, series);
		
		meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, series);
		switch (server.getBitsPerPixel()) {
		case 8:
			meta.setPixelsType(PixelType.UINT8, series);
			break;
		case 16:
			meta.setPixelsType(PixelType.UINT16, series);
			break;
		case 32:
			meta.setPixelsType(PixelType.FLOAT, series);
			break;
		case 64:
			meta.setPixelsType(PixelType.DOUBLE, series);
			break;
		default:
			throw new IOException("Cannot convert bits-per-pixel value of " + server.getBitsPerPixel() + " into a valid PixelType");
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
		int[] channel = this.channels;
		if (isRGB)
			channel = new int[] {0};

		if (channels.length <= 0)
			throw new IllegalArgumentException("No channels specified for export!");

		//		nChannels = 2;

		// Set channel colors
		meta.setPixelsSizeC(new PositiveInteger(nChannels), series);
		if (isRGB) {
			nSamples = 3;
//			nChannels = 1;
			meta.setChannelID("Channel:0", series, 0);			
			meta.setChannelSamplesPerPixel(new PositiveInteger(nSamples), series, 0);			
		} else {
			for (int c = 0; c < nChannels; c++) {
				meta.setChannelID("Channel:0:" + c, series, c);			
				meta.setChannelSamplesPerPixel(new PositiveInteger(nSamples), series, c);
				Integer color = server.getDefaultChannelColor(c);
				meta.setChannelColor(new Color(
						ColorTools.red(color),
						ColorTools.green(color),
						ColorTools.blue(color),
						0
						), series, c);
				meta.setChannelName(server.getChannelName(c), series, c);
			}			
		}

		// Set physical units, if we have them
		if (server.hasPixelSizeMicrons()) {
			meta.setPixelsPhysicalSizeX(new Length(server.getPixelWidthMicrons() * downsamples[0], UNITS.MICROMETER), series);
			meta.setPixelsPhysicalSizeY(new Length(server.getPixelHeightMicrons() * downsamples[0], UNITS.MICROMETER), series);
		}
		if (!Double.isNaN(server.getZSpacingMicrons()))
			meta.setPixelsPhysicalSizeZ(new Length(server.getZSpacingMicrons(), UNITS.MICROMETER), series);

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
			
			logger.info("Writing {} to {} with compression {}", server.getDisplayedImageName(), path, compression);
			
			writer.setCompression(compression);
			writer.setWriteSequentially(true);
			writer.setMetadataRetrieve(meta);
			
			int tileWidth = writer.setTileSizeX(this.tileWidth);
			int tileHeight = writer.setTileSizeY(this.tileHeight);

			File file = new File(path);
			if (file.exists()) {
				logger.warn("Deleting file {}", path);
				file.delete();
			}
			
			writer.setId(path);
			writer.setSeries(series);

			writer.setInterleaved(isRGB);

			writer.setSeries(series);
			for (int level = 0; level < downsamples.length; level++) {
				
				//				writer.setTileSizeX(tileWidth);
				//				writer.setTileSizeY(tileHeight);
				writer.setResolution(level);

				double d = downsamples[level];
				
				logger.info("Writing resolution {} of {} (downsample={})", level, downsamples.length, d);
				
				int w = (int)(this.width / d);
				int h = (int)(this.height / d);
				int bpp = server.getBitsPerPixel() / 8;

				int[] pixelsInt = null;
				float[] pixelsFloat = null;
				double[] pixelsDouble = null;

//				int sizeZ = zEnd - zStart + 1;
//				int sizeT = tEnd - tStart + 1;
				int nPlanes = (nChannels / nSamples) * sizeZ * sizeT;

				int plane = 0;
				int tInc = tEnd >= tStart ? 1 : -1;
				int zInc = zEnd >= zStart ? 1 : -1;

				for (int t = tStart; t < tEnd; t += tInc) {
					for (int z = zStart; z < zEnd; z += zInc) {
						for (int c : channel) {
							
							logger.info("Writing plane {} of {}", plane, nPlanes);

							IFD ifd = new IFD();
							ifd.put(IFD.TILE_WIDTH, tileWidth);
							ifd.put(IFD.TILE_LENGTH, tileHeight);

							/*
							 *  TODO: Consider parallelization for tile writing (synchronized anyway in writer?) 
							 *  or at the very least reusing int/byte arrays.
							 */
							for (int yy = 0; yy < h; yy += tileHeight) {
								int hh = Math.min(h - yy, tileHeight);
								for (int xx = 0; xx < w; xx += tileWidth) {
									int ww = Math.min(w - xx, tileWidth);

									/* 
									 * TODO: Note that this is horribly inefficient for multichannel images,
									 * because it requests all channels when it can only use one per loop
									 */
									RegionRequest request = RegionRequest.createInstance(
											server.getPath(), d, 
											(int)(xx * d) + x, 
											(int)(yy * d) + y, 
											(int)(ww * d), 
											(int)(hh * d),
											z,
											t);
									BufferedImage img = server.readBufferedImage(request);

									ByteBuffer buf = null;
									ww = img.getWidth();
									hh = img.getHeight();
									if (isRGB) {
										int[] rgba = img.getRGB(0, 0, ww, hh, null, 0, ww);
										buf = ByteBuffer.allocate(ww * hh * 3);						
										for (int val : rgba) {
											buf.put((byte)ColorTools.red(val));
											buf.put((byte)ColorTools.green(val));
											buf.put((byte)ColorTools.blue(val));
											//										buf.putInt(val);
										}
										// Plane is 0 for RGB
										writer.saveBytes(plane, buf.array(), ifd, xx, yy, ww, hh);
									} else {
										// TODO: IMPLEMENT THIS!
										buf = ByteBuffer.allocate(ww * hh * bpp);
										buf.order(ByteOrder.BIG_ENDIAN);
										buf.rewind();
										switch (server.getBitsPerPixel()) {
										case 8:
										case 16:
											if (pixelsInt == null || pixelsInt.length != ww*hh)
												pixelsInt = new int[ww*hh];
											pixelsInt = img.getRaster().getSamples(0, 0, ww, hh, c, pixelsInt);
											if (server.getBitsPerPixel() == 8) {
												for (int val : pixelsInt) {
													buf.put((byte)val);
												}
											} else {
												for (int val : pixelsInt) {
													buf.putShort((short)val);
												}
											}
											break;
										case 32:
											if (pixelsFloat == null || pixelsFloat.length != ww*hh)
												pixelsFloat = new float[ww*hh];
											pixelsFloat = img.getRaster().getSamples(0, 0, ww, hh, c, pixelsFloat);
											for (float val : pixelsFloat) {
												buf.putFloat(val);
											}
											break;
										case 64:
											if (pixelsDouble == null || pixelsDouble.length != ww*hh)
												pixelsDouble = new double[ww*hh];
											pixelsDouble = img.getRaster().getSamples(0, 0, ww, hh, c, pixelsDouble);
											for (double val : pixelsDouble) {
												buf.putDouble(val);
											}
											break;										
										}
										writer.saveBytes(plane, buf.array(), ifd, xx, yy, ww, hh);
									}
								}
							}
							// Next plane
							plane++;
						}
					}
				}
			}
			logger.trace("Image count: {}", meta.getImageCount());
			logger.trace("Plane count: {}", writer.getPlaneCount());
			logger.trace("Resolution count: {}", writer.getResolutionCount());
		}
	}

	/**
	 * Builder class to define parameters when exporting an image region as OME-TIFF 
	 * (possibly as an image pyramid).
	 * 
	 * @author Pete Bankhead
	 *
	 */
	public static class Builder {

		private OMEPyramidWriter writer = new OMEPyramidWriter();

		public Builder(ImageServer<BufferedImage> server) {
			writer.server = server;
			writer.x = 0;
			writer.y = 0;
			writer.width = server.getWidth();
			writer.height = server.getHeight();
			writer.downsamples = server.getPreferredDownsamples();
			if (server.getPreferredTileWidth() == server.getWidth() && server.getPreferredTileHeight() == server.getHeight()) {
				writer.tileWidth = server.getPreferredTileWidth();
				writer.tileHeight = server.getPreferredTileHeight();
			} else {
				writer.tileWidth = 256;
				writer.tileHeight = 256;
			}
			writer.zStart = 0;
			writer.zEnd = server.nZSlices();
			writer.tStart = 0;
			writer.tEnd = server.nTimepoints();
			writer.channels = IntStream.range(0, server.nChannels()).toArray();
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

		public Builder zSlices(int zStart, int zEnd) {
			writer.zStart = zStart;
			writer.zEnd = zEnd;
			return this;
		}

		public Builder timePoint(int t) {
			return this.timePoints(t, t+1);
		}
		
		private Builder allTimePoints() {
			return this.timePoints(0, writer.server.nTimepoints());
		}

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
					writer.width / writer.downsamples[lastDownsample] > writer.tileWidth &&
					writer.height / writer.downsamples[lastDownsample] > writer.tileHeight) {
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
		if (server.getBitsPerPixel() > 16) {
			set.remove(PyramidOMETiffWriter.COMPRESSION_JPEG);
			set.remove(PyramidOMETiffWriter.COMPRESSION_J2K);
			set.remove(PyramidOMETiffWriter.COMPRESSION_J2K_LOSSY);
		} else if (server.getBitsPerPixel() != 8 || (server.nChannels() > 1 && !server.isRGB())) {
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
		if (server.getBitsPerPixel() <= 16)
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
