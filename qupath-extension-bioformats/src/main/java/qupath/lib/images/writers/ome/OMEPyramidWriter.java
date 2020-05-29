/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import loci.formats.FormatException;
import loci.formats.MetadataTools;
import loci.formats.meta.IMetadata;
import loci.formats.meta.IPyramidStore;
import loci.formats.out.OMETiffWriter;
import loci.formats.out.PyramidOMETiffWriter;
import loci.formats.tiff.IFD;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.model.enums.DimensionOrder;
import ome.xml.model.enums.PixelType;
import ome.xml.model.primitives.Color;
import ome.xml.model.primitives.PositiveInteger;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.servers.ImageServerMetadata.ChannelType;
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
	 * Preferred compression type when using Bio-Formats.
	 */
	public static enum CompressionType {
		/**
		 * No compression (faster to write, no loss of information, but large file sizes).
		 */
		UNCOMPRESSED,
		/**
		 * Default (QuPath will select compression option based on image size and type, may be lossless or lossy).
		 */
		DEFAULT,
		/**
		 * JPEG compression (only for single channel or RGB 8-bit images).
		 */
		JPEG,
		/**
		 * Lossless JPEG-2000 compression.
		 */
		J2K,
		/**
		 * Lossy JPEG-2000 compression.
		 */
		J2K_LOSSY,
		/**
		 * LZW compression.
		 */
		LZW,
		/**
		 * ZLIB compression.
		 */
		ZLIB;
		
		/**
		 * Get the String representation understood by OMETiffWriter.
		 * @param server server that may be used if the type is {@link CompressionType#DEFAULT}
		 * @return
		 */
		public String getOMEString(ImageServer<?> server) {
			switch(this) {
			case J2K:
				return OMETiffWriter.COMPRESSION_J2K;
			case J2K_LOSSY:
				return OMETiffWriter.COMPRESSION_J2K_LOSSY;
			case JPEG:
				return OMETiffWriter.COMPRESSION_JPEG;
			case UNCOMPRESSED:
				return OMETiffWriter.COMPRESSION_UNCOMPRESSED;
			case LZW:
				return OMETiffWriter.COMPRESSION_LZW;
			case ZLIB:
				return OMETiffWriter.COMPRESSION_ZLIB;
			case DEFAULT:
			default:
				if (server.isRGB() && server.nResolutions() > 1)
					return OMETiffWriter.COMPRESSION_JPEG;
				if (server.getPixelType() == qupath.lib.images.servers.PixelType.UINT8)
					// LZW is apparently bad for 16-bit (can increase file sizes?)
					return OMETiffWriter.COMPRESSION_LZW;
				else
					return OMETiffWriter.COMPRESSION_ZLIB;
			}
		}
		
		/**
		 * Returns true if the compression type supports a specific image server, or false 
		 * if it is incompatible. This may be due to bit-depth, number of channels etc.
		 * @param server
		 * @return
		 */
		public boolean supportsImage(ImageServer<?> server) {
			switch(this) {
			case JPEG:
				return server.isRGB() || 
						(server.nChannels() == 1 && server.getPixelType() == qupath.lib.images.servers.PixelType.UINT8);
			case J2K:
			case J2K_LOSSY:
				// It seems OME-TIFF can only write 8-bit or 16-bit J2K?
				return server.getPixelType().getBytesPerPixel() <= 2;
			case LZW:
			case DEFAULT:
			case UNCOMPRESSED:
			case ZLIB:
				return true;
			default:
				return false;
			}
		}
		
		/**
		 * Get a friendlier string representation
		 * @return
		 */
		public String toFriendlyString() {
			switch(this) {
			case DEFAULT:
				return "Default (lossless or lossy)";
			case J2K:
				return "JPEG-2000 (lossless)";
			case J2K_LOSSY:
				return "JPEG-2000 (lossy)";
			case JPEG:
				return "JPEG (lossy)";
			case UNCOMPRESSED:
				return "Uncompressed";
			case LZW:
				return "LZW (lossless)";
			case ZLIB:
				return "ZLIB (lossless)";
			default:
				throw new IllegalArgumentException("Unknown compression type: " + this);
			}
		}
		
		/**
		 * Get the CompressionType corresponding to the given input
		 * @param friendlyCompression
		 * @return
		 */
		public static CompressionType fromFriendlyString(String friendlyCompression) {
			for (var compression: CompressionType.values()) {
				if (friendlyCompression.equals(compression.toFriendlyString()))
					return compression;
			}
			throw new IllegalArgumentException("Unknown compression type: " + friendlyCompression);
		}
	}

	
	
	private static int DEFAULT_TILE_SIZE = 512;
	private static int MIN_SIZE_FOR_TILING = DEFAULT_TILE_SIZE * 8;
	private static int MIN_SIZE_FOR_PYRAMID = MIN_SIZE_FOR_TILING * 2;
	
		
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
		IMAGES
	}
	
	
	private boolean keepExisting = false;
	
	private List<OMEPyramidSeries> series = new ArrayList<>();
	
	private OMEPyramidWriter(Collection<OMEPyramidSeries> series) {
		this.series.addAll(series);
	}
	
	/**
	 * Write an image consisting of one or more series to the specified path.
	 * @param path
	 * @throws FormatException
	 * @throws IOException
	 * @see #createWriter(Collection)
	 * @see #createWriter(OMEPyramidSeries...)
	 */
	public void writeImage(final String path) throws FormatException, IOException {
		IMetadata meta = MetadataTools.createOMEXMLMetadata();
		
		File file = new File(path);
		if (file.exists() && !keepExisting) {
			logger.warn("Deleting existing file {}", path);
			if (!file.delete())
				throw new IOException("Unable to delete " + file.getAbsolutePath());
		}
				
		try (PyramidOMETiffWriter writer = new PyramidOMETiffWriter()) {
			boolean doBigTiff = false;
			long nPixelBytes = 0L;
			for (int s = 0; s < series.size(); s++) {
				var temp = series.get(s);
				doBigTiff = Boolean.TRUE.equals(temp.bigTiff) | doBigTiff;
				nPixelBytes += ((long)temp.width * temp.height * temp.channels.length * temp.server.getPixelType().getBytesPerPixel() *
						(temp.tEnd - temp.tStart) * (temp.zEnd - temp.zStart));
				temp.initializeMetadata(meta, s);
			}
			
			writer.setWriteSequentially(true); // Setting this to false can be problematic!
			// Switch automatically to bigtiff is we have a large image or it has already been requested
			if (doBigTiff || nPixelBytes >= Integer.MAX_VALUE)
				writer.setBigTiff(doBigTiff);
			
			writer.setMetadataRetrieve(meta);
			writer.setId(path);
			for (int s = 0; s < series.size(); s++) {
				var temp = series.get(s);
				logger.info("Writing {} to {} (series {}/{})", ServerTools.getDisplayableImageName(temp.server), path, s+1, series.size());
				temp.writePyramid(writer, meta, s);
			}
		}
		
	}
	
	
	static class OMEPyramidSeries {
		
		private OMEPyramidSeries() {}
		
		private ImageServer<BufferedImage> server;
		
		private String name; // Series name
	
		private double[] downsamples;
		private int tileWidth, tileHeight;
	
		private int x, y, width, height;
		private int zStart = 0;
		private int zEnd = 0;
		private int tStart = 0;
		private int tEnd = 0;
		private int[] channels;
		
		private ByteOrder endian = ByteOrder.BIG_ENDIAN;
		
		private boolean parallelExport = false;
		
		private Boolean bigTiff;
		private ChannelExportType channelExportType = ChannelExportType.DEFAULT;
	
		private CompressionType compression = CompressionType.DEFAULT;
		
		public void initializeMetadata(IMetadata meta, int series) throws IOException {
			
			meta.setImageID("Image:"+series, series);
			meta.setPixelsID("Pixels:"+series, series);
			if (name != null)
				meta.setImageName(name, series);
			
			meta.setPixelsBigEndian(ByteOrder.BIG_ENDIAN.equals(endian), series);
			
			meta.setPixelsDimensionOrder(DimensionOrder.XYCZT, series);
			switch (server.getPixelType()) {
			case INT8:
				meta.setPixelsType(PixelType.INT8, series);
				break;
			case UINT8:
				meta.setPixelsType(PixelType.UINT8, series);
				break;
			case INT16:
				meta.setPixelsType(PixelType.INT16, series);
				break;
			case UINT16:
				meta.setPixelsType(PixelType.UINT16, series);
				break;
			case INT32:
				meta.setPixelsType(PixelType.INT32, series);
				break;
			case UINT32:
				meta.setPixelsType(PixelType.UINT32, series);
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
	
		}
		
		/**
		 * Write an OME-TIFF pyramidal image to the given file.
		 * 
		 * @param path file path for output
		 * @throws FormatException
		 * @throws IOException
		 */
		public void writePyramid(final String path) throws FormatException, IOException {
			var writer = new OMEPyramidWriter();
			writer.series.add(this);
			writer.writeImage(path);
		}
		
		/**
		 * Append an image as a specific series.
		 * 
		 * @param writer the current writer; it should already be initialized, with metadata and ID set
		 * @param meta the metadata, which should already have been initialized and set in the writer before writing any pixels
		 * @param series number of series to be written (starting with 0; assumes previous series already written)
		 * @throws FormatException
		 * @throws IOException
		 * 
		 * @see Builder
		 * @see #initializeMetadata(IMetadata, int)
		 */
		public void writePyramid(final PyramidOMETiffWriter writer, IMetadata meta, final int series) throws FormatException, IOException {
	
			boolean isRGB = server.isRGB() && Arrays.equals(channels, new int[] {0, 1, 2});
			int nChannels = meta.getPixelsSizeC(series).getValue();
			int nSamples = meta.getChannelSamplesPerPixel(series, 0).getValue();
			int sizeZ = meta.getPixelsSizeZ(series).getValue();
			int sizeT = meta.getPixelsSizeT(series).getValue();
			int width = meta.getPixelsSizeX(series).getValue();
			int height = meta.getPixelsSizeY(series).getValue();
			int nPlanes = (nChannels / nSamples) * sizeZ * sizeT;
			
			if (compression.supportsImage(server))
				writer.setCompression(compression.getOMEString(server));
			else {
				String compressionString = CompressionType.DEFAULT.getOMEString(server);
				logger.warn("Requested compression {} incompatible with current image, will use {} instead",
						compression.getOMEString(server),
						compressionString);
				writer.setCompression(compressionString);
			}
			writer.setInterleaved(meta.getPixelsInterleaved(series));
						
			int tileWidth = this.tileWidth;
			int tileHeight = this.tileHeight;
			boolean isTiled = tileWidth > 0 && tileHeight > 0;
			if (isTiled) {
				tileWidth = writer.setTileSizeX(tileWidth);
				tileHeight = writer.setTileSizeY(tileHeight);				
			}
			
			// If the image represents classifications, set the color model accordingly
			if (server.getMetadata().getChannelType() == ChannelType.CLASSIFICATION) {
				// Try to set color model, but continue if this fails (e.g. if there are too many classifications)
				try {
					writer.setColorModel(ColorModelFactory.getIndexedClassificationColorModel(server.getMetadata().getClassificationLabels()));
				} catch (Exception e) {
					logger.warn("Error setting classification color model: {}", e.getLocalizedMessage());
				}
			}
	
			writer.setSeries(series);
			
			Map<Integer, IFD> map = new HashMap<>();
	
			writer.setSeries(series);
			for (int level = 0; level < downsamples.length; level++) {
				
				writer.setResolution(level);
				
				// Preallocate any IFD
				map.clear();
				for (int i = 0; i < nPlanes; i++) {
					IFD ifd = new IFD();
					if (isTiled) {
						ifd.put(IFD.TILE_WIDTH, tileWidth);
						ifd.put(IFD.TILE_LENGTH, tileHeight);
					}
					if (nSamples > 1 && !isRGB)
						ifd.put(IFD.EXTRA_SAMPLES, new short[nSamples-1]);
					map.put(Integer.valueOf(i), ifd);
				}
	
				double d = downsamples[level];
								
				int w = (int)(width * downsamples[0] / d);
				int h = (int)(height * downsamples[0] / d);
	
				int tInc = tEnd >= tStart ? 1 : -1;
				int zInc = zEnd >= zStart ? 1 : -1;
				int effectiveSizeC = nChannels / nSamples;
				
				AtomicInteger count = new AtomicInteger(0);
								
				int ti = 0;
				for (int t = tStart; t < tEnd; t += tInc) {
					int zi = 0;
					for (int z = zStart; z < zEnd; z += zInc) {
						
						/*
						 *  It appears we can use parallelization for tile writing (thanks to synchronization in the writer),
						 *  provided we write the (0,0) tile first.
						 */
						long planeStartTime = System.currentTimeMillis();
						
						// Create a list of all required requests, extracting the first
						List<ImageRegion> regions = new ArrayList<>();
						for (int yy = 0; yy < h; yy += tileHeight) {
							int hh = Math.min(h - yy, tileHeight);
							for (int xx = 0; xx < w; xx += tileWidth) {
								int ww = Math.min(w - xx, tileWidth);
								regions.add(ImageRegion.createInstance(xx, yy, ww, hh, z, t));
							}
						}
						
						int total = regions.size() * (tEnd - tStart) * (zEnd - zStart);
						if (z == zStart && t == tStart)
							logger.info("Writing resolution {} of {} (downsample={}, {} tiles)", level+1, downsamples.length, d, total);

						ImageRegion firstRegion = regions.remove(0);
						
						// Show progress at key moments
						int inc = total > 1000 ? 20 : 10;
						Set<Integer> keyCounts = IntStream.range(1, inc).mapToObj(i -> (int)Math.round((double)total / inc * i)).collect(Collectors.toCollection(() -> new HashSet<>()));
						keyCounts.add(total-1);
						
						// Loop through effective channels (which is 1 if we are writing interleaved)
						for (int ci = 0; ci < effectiveSizeC; ci++) {
							
							int plane = ti * sizeZ * effectiveSizeC + zi * effectiveSizeC + ci;
							IFD ifd = map.get(Integer.valueOf(plane));
							int[] localChannels = effectiveSizeC == channels.length ? new int[] {channels[ci]} : channels;
						
							logger.info("Writing plane {}/{}", plane+1, nPlanes);
								
							// We *must* write the first region first
							writeRegion(writer, plane, ifd, firstRegion, d, isRGB, localChannels);
							if (!regions.isEmpty()) {
								var tasks = regions.stream().map(region -> new Runnable() {
									@Override
									public void run() {
										try {
											writeRegion(writer, plane, ifd, region, d, isRGB, localChannels);
										} catch (Exception e) {
											logger.error(String.format(
													"Error writing %s (downsample=%.2f)",
													region.toString(), d),
													e);
										} finally {
											int localCount = count.incrementAndGet();
											if (total > 20 && keyCounts.size() > 1 && keyCounts.contains(localCount)) {
												double percentage = localCount*100.0/total;
												logger.info("Written {}% tiles", Math.round(percentage));
											}
										}
									}
								}).collect(Collectors.toList());
								
								if (parallelExport) {
									var pool = Executors.newWorkStealingPool(4);
									for (var task : tasks) {
										pool.submit(task);
									}
									pool.shutdown();
									try {
										pool.awaitTermination(regions.size(), TimeUnit.MINUTES);
										logger.info("Plane written in {} ms", System.currentTimeMillis() - planeStartTime);
									} catch (InterruptedException e) {
										throw new IOException("Timeout writing regions", e);
									}
								} else {
									for (var task : tasks)
										task.run();
								}
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
			case INT8:
			case UINT8:
			case INT16:
			case UINT16:
			case INT32:
			case UINT32:
				int[] pixelsInt = pixelBuffer instanceof int[] ? (int[])pixelBuffer : null;
				if (pixelsInt == null || pixelsInt.length < n)
					pixelsInt = new int[n];
				pixelsInt = raster.getSamples(0, 0, ww, hh, c, pixelsInt);
				if (server.getPixelType().getBitsPerPixel() == 8) {
					for (int i = 0; i < n; i++) {
						buf.put(ind, (byte)pixelsInt[i]);
						ind += inc;
					}
				} else if (server.getPixelType().getBitsPerPixel() == 16) {
					for (int i = 0; i < n; i++) {
						buf.putShort(ind, (short)pixelsInt[i]);
						ind += inc;
					}
				} else if (server.getPixelType().getBitsPerPixel() == 32) {
					for (int i = 0; i < n; i++) {
						buf.putInt(ind, (int)pixelsInt[i]);
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
			default:
				logger.warn("Cannot convert to buffer - unknown pixel type {}", server.getPixelType());
				return false;
			}
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
			switch (server.getPixelType()) {
			case FLOAT32:
				updatedBuffer = ensureFloatArray(originalBuffer, length);
				break;
			case FLOAT64:
				updatedBuffer = ensureDoubleArray(originalBuffer, length);
				break;
			default:
				// Everything else uses ints (including 8-bit RGB)
				updatedBuffer = ensureIntArray(originalBuffer, length);
			}
			if (updatedBuffer != originalBuffer)
				pixelBuffer.set(updatedBuffer);
			return updatedBuffer;
		}
		
		
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
	

//	public static class Builder {
//		
//		private List<SeriesBuilder> series = new ArrayList<>();
//		
//		
//		
//	}
	
	/**
	 * Create a writer capable of writing an image with one or more series.
	 * @param series
	 * @return
	 */
	public static OMEPyramidWriter createWriter(OMEPyramidSeries... series) {
		return new OMEPyramidWriter(Arrays.asList(series));
	}
	
	/**
	 * Create a writer capable of writing an image with a collection ofseries.
	 * @param series
	 * @return
	 */
	public static OMEPyramidWriter createWriter(Collection<OMEPyramidSeries> series) {
		return new OMEPyramidWriter(series);
	}

	/**
	 * Builder class to define parameters when exporting an image region as OME-TIFF,
	 * possibly as an image pyramid.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	public static class Builder {

		private OMEPyramidSeries series = new OMEPyramidSeries();

		/**
		 * Constructor.
		 * @param server the ImageServer from which pixels will be requested and written to the OME-TIFF.
		 */
		public Builder(ImageServer<BufferedImage> server) {
			series.server = server;
			series.server = server;
			series.x = 0;
			series.y = 0;
			series.width = server.getWidth();
			series.height = server.getHeight();
			
			series.downsamples = server.getPreferredDownsamples();
			if (server.getMetadata().getPreferredTileWidth() >= server.getWidth() && server.getMetadata().getPreferredTileHeight() >= server.getHeight()) {
				series.tileWidth = server.getMetadata().getPreferredTileWidth();
				series.tileHeight = server.getMetadata().getPreferredTileHeight();
			} else {
				series.tileWidth = 256;
				series.tileHeight = 256;
			}
			series.zStart = 0;
			series.zEnd = server.nZSlices();
			series.tStart = 0;
			series.tEnd = server.nTimepoints();
			if (server.getMetadata().getChannelType() == ImageServerMetadata.ChannelType.CLASSIFICATION)
				series.channels = new int[] {0};
			else
				series.channels = IntStream.range(0, server.nChannels()).toArray();
		}
		
//		/**
//		 * Request that any existing file with the same path is kept, rather than being deleted.
//		 * @return
//		 */
//		public Builder keepExistingFile() {
//			writer.keepExisting = true;
//			return this;
//		}
		
		/**
		 * Request that channels are written as separate image planes.
		 * @return this builder
		 */
		public Builder channelsPlanar() {
			series.channelExportType = ChannelExportType.PLANAR;
			return this;
		}

		/**
		 * Request that channels are written interleaved within a single image plane.
		 * @return this builder
		 */
		public Builder channelsInterleaved() {
			series.channelExportType = ChannelExportType.INTERLEAVED;
			return this;
		}

		/**
		 * Request that channels are written as separate images.
		 * @return this builder
		 */
		public Builder channelsImages() {
			series.channelExportType = ChannelExportType.IMAGES;
			return this;
		}

		/**
		 * Request that the image is written in BigTIFF format.
		 * @return this builder
		 */
		public Builder bigTiff() {
			series.bigTiff = Boolean.TRUE;
			return this;
		}

		/**
		 * Specify whether the image should be written in BigTIFF format.
		 * @param doBigTiff if true, request that big-tiff is written
		 * @return this builder
		 */
		public Builder bigTiff(boolean doBigTiff) {
			series.bigTiff = doBigTiff;
			return this;
		}

		/**
		 * Request the output compression type.
		 * @param compression
		 * @return this builder
		 */
		public Builder compression(final CompressionType compression) {
			series.compression = compression;
			return this;
		}
		
		/**
		 * Request the default lossy compression method. Not all servers support lossy compression 
		 * (e.g. non-RGB servers).
		 * @return this builder
		 */
		public Builder lossyCompression() {
			series.compression = getDefaultLossyCompressionType(series.server);
			return this;
		}
		
		/**
		 * Request the default lossless compression method.
		 * @return
		 */
		public Builder losslessCompression() {
			series.compression = getDefaultLosslessCompressionType(series.server);
			return this;
		}
		
		/**
		 * Parallelize tile export, if possible.
		 * 
		 * @return this builder
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
			series.parallelExport = doParallel;
			return this;
		}

		/**
		 * Request that all z-slices are exported.
		 * @return this builder
		 */
		public Builder allZSlices() {
			return this.zSlices(0, series.server.nZSlices());
		}

		/**
		 * Specify the z-slice to export.
		 * @param z
		 * @return this builder
		 */
		public Builder zSlice(int z) {
			return this.zSlices(z, z+1);
		}

		/**
		 * Specify the start (inclusive) and end (exclusive) z-slices.
		 * @param zStart
		 * @param zEnd
		 * @return this builder
		 */
		public Builder zSlices(int zStart, int zEnd) {
			if (zStart < 0) {
				logger.warn("First z-slice (" + zStart + ") is out of bounds. Will use " + 0 + " instead.");
				zStart = 0;
			}
			if (zEnd > series.server.nZSlices()) {
				logger.warn("Last z-slice (" + zEnd + ") is out of bounds. Will use " + series.server.nZSlices() + " instead.");
				zEnd = series.server.nZSlices();
			}
			series.zStart = zStart;
			series.zEnd = zEnd;
			return this;
		}

		/**
		 * Specify a single timepoint to be written from a time series.
		 * @param t the index identifying the requested timepoint
		 * @return this builder
		 */
		public Builder timePoint(int t) {
			return this.timePoints(t, t+1);
		}
		
		/**
		 * Request that all timepoints of a time series will be written.
		 * @return this builder
		 */
		public Builder allTimePoints() {
			return this.timePoints(0, series.server.nTimepoints());
		}

		/**
		 * Specify a range of timepoints to be written from a time series.
		 * @param tStart first timepoint (inclusive)
		 * @param tEnd last timepoint (exclusive)
		 * @return this builder
		 */
		public Builder timePoints(int tStart, int tEnd) {
			if (tStart < 0) {
				logger.warn("First timepoint (" + tStart + ") is out of bounds. Will use " + 0 + " instead.");
				tStart = 0;
			}
			if (tEnd > series.server.nTimepoints()) {
				logger.warn("Last timepoint (" + tEnd + ") is out of bounds. Will use " + series.server.nTimepoints() + " instead.");
				tEnd = series.server.nTimepoints();
			}
			series.tStart = tStart;
			series.tEnd = tEnd;
			return this;
		}
		
		/**
		 * Specify a series name
		 * @param name
		 * @return this builder
		 */
		public Builder name(String name) {
			series.name = name;
			return this;
		}

		/**
		 * Define the region to export based on a bounding box.
		 * 
		 * @param x
		 * @param y
		 * @param width
		 * @param height
		 * @return this builder
		 */
		public Builder region(int x, int y, int width, int height) {
			series.x = x;
			series.y = y;
			series.width = width;
			series.height = height;
			return this;
		}

		/**
		 * Define the region to export, including the z-slice and time point.
		 * 
		 * @param region
		 * @return this builder
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
		 * @return this builder
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
		 * @return this builder
		 */
		public Builder tileSize(int tileWidth, int tileHeight) {
			series.tileWidth = tileWidth;
			series.tileHeight = tileHeight;
			return this;
		}

		/**
		 * Specify downsample factors to use in the final pyramid.
		 * <p>
		 * Note that the downsample values provided will be sorted in ascending order.
		 * @param downsamples
		 * @return this builder
		 */
		public Builder downsamples(double... downsamples) {
			series.downsamples = downsamples;
			return this;
		}
		
		/**
		 * Downsample by factors of 2.
		 * <p>
		 * Note that the highest downsample value will depend on the tile size, 
		 * so the tile size should be set first.
		 * 
		 * @return this builder
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
		 * @param scale 
		 * 
		 * @return this builder
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
		 * @param minDownsample the starting downsample, defining the highest-resolution image (usually 1.0)
		 * @param scale the scale used to define successive downsamples
		 * @return this builder
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
				} while ((int)(series.width / nextDownsample) > series.tileWidth && (int)(series.height / nextDownsample) > series.tileHeight);
				series.downsamples = downsampleList.stream().mapToDouble(d -> d).toArray();
//			}
			return this;
		}

		/**
		 * Override default of writing all channels in their original order to be able to specify which 
		 * channels are output, and in which order.
		 * @param channels zero-based channel indices for all channels that should be exported, in the desired export order.
		 * @return this builder
		 */
		public Builder channels(int... channels) {
			series.channels = channels;
			return this;
		}

		/**
		 * Create an {@link OMEPyramidSeries} to write the OME-TIFF.
		 * @return the series
		 */
		public OMEPyramidSeries build() {
			// Sanity check downsamples, and remove those that are too much
			Arrays.sort(series.downsamples);
			int lastDownsample = 1;
			while (lastDownsample < series.downsamples.length && 
					series.width / series.downsamples[lastDownsample] > 16 &&
					series.height / series.downsamples[lastDownsample] > 16) {
				lastDownsample++;
			}
			if (lastDownsample < series.downsamples.length)
				series.downsamples = Arrays.copyOf(series.downsamples, lastDownsample);
			
			return series;
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
	static CompressionType getDefaultLosslessCompressionType(final ImageServer<BufferedImage> server) {
		if (server.getPixelType() == qupath.lib.images.servers.PixelType.UINT8)
			return CompressionType.LZW;
		else
			return CompressionType.ZLIB;
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
	static CompressionType getDefaultLossyCompressionType(final ImageServer<BufferedImage> server) {
		if (server.isRGB())
			return CompressionType.JPEG;
		if (server.getPixelType().getBitsPerPixel() <= 16)
			return CompressionType.J2K_LOSSY;
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
	public static void writeImage(ImageServer<BufferedImage> server, String path, CompressionType compression) throws FormatException, IOException {
		writeImage(server, path, compression, RegionRequest.createInstance(server), true, true);
	}
	
	/**
	 * Static helper method to write an OME-TIFF pyramidal image for a defined region with the specified compression.
	 * If region is null, the entire image will be written. If region is not null, it defines the bounding box of the exported 
	 * pixels in addition to the z-slice and timepoint.
	 * 
	 * @param server image to write
	 * @param path path to output file
	 * @param compression image compression method
	 * @param region the region to export. If this is a RegionRequest that defines a downsample other than the default for the server, this downsample will be used.
	 * 
	 * @throws FormatException
	 * @throws IOException
	 */
	public static void writeImage(ImageServer<BufferedImage> server, String path, CompressionType compression, ImageRegion region) throws FormatException, IOException {
		if (region == null) {
			writeImage(server, path, compression);
			return;
		}
		writeImage(server, path, compression, region, false, false);
	}
	
	/**
	 * Static helper method to write an OME-TIFF pyramidal image for a defined region with the specified compression, optionally including all 
	 * z-slices or timepoints.
	 * 
	 * @param server image to write
	 * @param path path to output file
	 * @param compression image compression method
	 * @param region the region to export. If this is a RegionRequest that defines a downsample other than the default for the server, this downsample will be used.
	 * @param allZ if true, export all z-slices otherwise export slice defined by region (ignored if image is not a z-stack)
	 * @param allT if true, export all timepoints otherwise export timepoint defined by region (ignored if image is not a timeseries)
	 * 
	 * @throws FormatException
	 * @throws IOException
	 */
	public static void writeImage(ImageServer<BufferedImage> server, String path, CompressionType compression, ImageRegion region, boolean allZ, boolean allT) throws FormatException, IOException {
		if (region == null) {
			writeImage(server, path, compression);
			return;
		}
		
		var builder = new Builder(server)
				.compression(compression == null ? CompressionType.DEFAULT : compression)
				.parallelize()
				.region(region);
		
		double downsample = server.getDownsampleForResolution(0);
		if (region instanceof RegionRequest) {
			downsample = ((RegionRequest)region).getDownsample();
		}
		
		int maxDimension = (int)(Math.max(region.getWidth(), region.getHeight())/downsample);
		if (maxDimension > MIN_SIZE_FOR_PYRAMID) {
			builder.scaledDownsampling(downsample, 4);
		} else
			builder.downsamples(downsample);
		if (maxDimension > MIN_SIZE_FOR_TILING)
			builder.tileSize(DEFAULT_TILE_SIZE);
		
		if (allZ)
			builder.allZSlices();
		if (allT)
			builder.allTimePoints();

		builder.build().writePyramid(path);
	}

}