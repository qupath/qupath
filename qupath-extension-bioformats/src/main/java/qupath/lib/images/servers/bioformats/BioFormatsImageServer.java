/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.images.servers.bioformats;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ComponentSampleModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferShort;
import java.awt.image.DataBufferUShort;
import java.awt.image.PixelInterleavedSampleModel;
import java.awt.image.SampleModel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.lang.ref.Cleaner.Cleanable;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ome.units.UNITS;
import ome.units.quantity.Length;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Unmatched;
import loci.common.DataTools;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.ClassList;
import loci.formats.DimensionSwapper;
import loci.formats.FormatException;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.Memoizer;
import loci.formats.MetadataTools;
import loci.formats.ReaderWrapper;
import loci.formats.gui.AWTImageTools;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.MetadataOptions;
import loci.formats.meta.DummyMetadata;
import loci.formats.meta.MetadataStore;
import loci.formats.ome.OMEPyramidStore;
import loci.formats.ome.OMEXMLMetadata;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerBuilder.DefaultImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ImageResolutionLevel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;

/**
 * QuPath ImageServer that uses the Bio-Formats library to read image data.
 * <p>
 * See http://www.openmicroscopy.org/site/products/bio-formats
 * <p>
 * See also https://docs.openmicroscopy.org/bio-formats/6.5.1/developers/matlab-dev.html#improving-reading-performance
 * 
 * @author Pete Bankhead
 *
 */
public class BioFormatsImageServer extends AbstractTileableImageServer {
	
	private static final Logger logger = LoggerFactory.getLogger(BioFormatsImageServer.class);
		
	/**
	 * Define a maximum memoization file size above which parallelization is disabled. 
	 * This is necessary to avoid creating multiple readers that are too large (e.g. sometimes 
	 * a memoization file can be over 1GB...)
	 */
	private static long MAX_PARALLELIZATION_MEMO_SIZE = 1024L * 1024L * 16L;
	
	/**
	 * The original URI requested for this server.
	 */
	private URI uri;
	
	/**
	 * Minimum tile size - smaller values will be ignored.
	 */
	private static int MIN_TILE_SIZE = 32;

	/**
	 * Default tile size - when no other value is available.
	 */
	private static int DEFAULT_TILE_SIZE = 512;

//	/**
//	 * Maximum tile size - larger values will be ignored.
//	 */
//	private static int MAX_TILE_SIZE = 4096;
	
	/**
	 * Image names (in lower case) normally associated with 'extra' images, but probably not representing the main image in the file.
	 */
	private static Collection<String> extraImageNames = new HashSet<>(
			Arrays.asList("overview", "label", "thumbnail", "macro", "macro image", "macro mask image", "label image", "overview image", "thumbnail image"));
	
	/**
	 * Original metadata, populated when reading the file.
	 */
	private ImageServerMetadata originalMetadata;
	
	/**
	 * Arguments passed to constructor.
	 */
	private String[] args;
	
	/**
	 * File path if possible, or a URI otherwise.
	 */
	private String filePathOrUrl;
	
	/**
	 * Fix issue related to VSI images having (wrong) z-slices
	 */
//	private boolean doChannelZCorrectionVSI = false;
	
	/**
	 * A map linking an identifier (image name) to series number for 'full' images.
	 */
	private Map<String, ServerBuilder<BufferedImage>> imageMap = null;
	
	/**
	 * A map linking an identifier (image name) to series number for additional images, e.g. thumbnails or macro images.
	 */
	private Map<String, Integer> associatedImageMap = null;
	
	/**
	 * Numeric identifier for the image (there might be more than one in the file)
	 */
	private int series = 0;
	
	/**
	 * Format for the current reader.
	 */
	private String format;
	
//	/**
//	 * QuPath-specific options for how the image server should behave, such as using parallelization or memoization.
//	 */
//	private BioFormatsServerOptions options;
		
//	/**
//	 * Manager to help keep multithreading under control.
//	 */
//	private static BioFormatsReaderManager manager = new BioFormatsReaderManager();
	
	/**
	 * ColorModel to use with all BufferedImage requests.
	 */
	private ColorModel colorModel;
	
	/**
	 * Pool of readers for use with this server.
	 */
	private ReaderPool readerPool;
	
	/**
	 * Primary metadata store.
	 */
//	private OMEPyramidStore meta;
	
	/**
	 * Cached path
	 */
	private String path;
	
	/**
	 * Wrapper to the args passed to the reader, after parsing.
	 */
	private BioFormatsArgs bfArgs;
	
//	/**
//	 * Try to parallelize multichannel requests (experimental!)
//	 */
//	private boolean parallelizeMultichannel = true;

	
	/**
	 * Create an ImageServer using the Bio-Formats library.
	 * <p>
	 * This requires an <i>absolute</i> URI, where an integer fragment can be used to define the series number.
	 * 
	 * @param uri for the image that should be opened; this might include a sub-image as a query or fragment.
	 * @param args optional arguments
	 * @throws FormatException
	 * @throws IOException
	 * @throws DependencyException
	 * @throws ServiceException
	 * @throws URISyntaxException 
	 */
	public BioFormatsImageServer(final URI uri, String...args) throws FormatException, IOException, DependencyException, ServiceException, URISyntaxException {
		this(uri, BioFormatsServerOptions.getInstance(), args);
	}
	
	/**
	 * Create a minimal BioFormatsImageServer without additional readers, which can be used to query server builders.
	 * @param uri
	 * @param options
	 * @param args
	 * @return
	 * @throws FormatException
	 * @throws IOException
	 * @throws DependencyException
	 * @throws ServiceException
	 * @throws URISyntaxException
	 */
	static BioFormatsImageServer checkSupport(URI uri, final BioFormatsServerOptions options, String...args) throws FormatException, IOException, DependencyException, ServiceException, URISyntaxException {
		return new BioFormatsImageServer(uri, options, args);
	}
	
	BioFormatsImageServer(URI uri, final BioFormatsServerOptions options, String...args) throws FormatException, IOException, DependencyException, ServiceException, URISyntaxException {
		super();

		long startTime = System.currentTimeMillis();

//		this.options = options;
		
		// Create variables for metadata
		int width = 0, height = 0, nChannels = 1, nZSlices = 1, nTimepoints = 1, tileWidth = 0, tileHeight = 0;
		double pixelWidth = Double.NaN, pixelHeight = Double.NaN, zSpacing = Double.NaN, magnification = Double.NaN;
		TimeUnit timeUnit = null;
		
		// See if there is a series name embedded in the path (temporarily the way things were done in v0.2.0-m1 and v0.2.0-m2)
		// Add it to the args if so
		if (args.length == 0) {
			if (uri.getFragment() != null) {
				args = new String[] {"--series", uri.getFragment()};
			} else if (uri.getQuery() != null) {
				// Queries supported name=image-name or series=series-number... only one or the other!
				String query = uri.getQuery();
				String seriesQuery = "series=";
				String nameQuery = "name=";
				if (query.startsWith(seriesQuery)) {
					args = new String[] {"--series", query.substring(seriesQuery.length())};
				} else if (query.startsWith(nameQuery)) {
					args = new String[] {"--name", query.substring(nameQuery.length())};
				}
			}
			uri = new URI(uri.getScheme(), uri.getHost(), uri.getPath(), null);
		}
		this.uri = uri;
		
		// Parse the arguments
		bfArgs = BioFormatsArgs.parse(args);
		
		// Try to parse args, extracting the series if present
		int seriesIndex = bfArgs.series;
		String requestedSeriesName = bfArgs.seriesName;
		if (requestedSeriesName.isBlank())
			requestedSeriesName = null;
		
		// Try to get a local file path, but accept something else (since Bio-Formats handles other URIs)
		try {
			var path = GeneralTools.toPath(uri);
			if (path != null) {
				filePathOrUrl = path.toString();
			}
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		} finally {
			if (filePathOrUrl == null) {
				logger.debug("Using URI as file path: {}", uri);
				filePathOrUrl = uri.toString();
			}
		}

		// Create a reader & extract the metadata
		readerPool = new ReaderPool(options, filePathOrUrl, bfArgs);
		IFormatReader reader = readerPool.getMainReader();
		var meta = (OMEPyramidStore)reader.getMetadataStore();

		// Populate the image server list if we have more than one image
		int largestSeries = -1;
		int firstSeries = -1;
		long mostPixels = -1L;
		long firstPixels = -1L;

		// If we have more than one series, we need to construct maps of 'analyzable' & associated images
		synchronized(reader) {
			int nImages = meta.getImageCount();
			imageMap = new LinkedHashMap<>(nImages);
			associatedImageMap = new LinkedHashMap<>(nImages);

			// Loop through series to find out whether we have multiresolution images, or associated images (e.g. thumbnails)
			for (int s = 0; s < nImages; s++) {
				String name = "Series " + s;
				String originalImageName = getImageName(meta, s);
				if (originalImageName == null)
					originalImageName = "";
				
				String imageName = originalImageName;
				try {
					if (!imageName.isEmpty())
						name += " (" + imageName + ")";
					
					// Set this to be the series, if necessary
					long sizeX = meta.getPixelsSizeX(s).getNumberValue().longValue();
					long sizeY = meta.getPixelsSizeY(s).getNumberValue().longValue();
					long sizeC = meta.getPixelsSizeC(s).getNumberValue().longValue();
					long sizeZ = meta.getPixelsSizeZ(s).getNumberValue().longValue();
					long sizeT = meta.getPixelsSizeT(s).getNumberValue().longValue();
					
					// Check the resolutions
					//						int nResolutions = meta.getResolutionCount(s);
					//						for (int r = 1; r < nResolutions; r++) {
					//							int sizeXR = meta.getResolutionSizeX(s, r).getValue();
					//							int sizeYR = meta.getResolutionSizeY(s, r).getValue();
					//							if (sizeXR <= 0 || sizeYR <= 0 || sizeXR > sizeX || sizeYR > sizeY)
					//								throw new IllegalArgumentException("Resolution " + r + " size " + sizeXR + " x " + sizeYR + " invalid!");
					//						}
					// It seems we can't get the resolutions from the metadata object... instead we need to set the series of the reader
					reader.setSeries(s);
					assert reader.getSizeX() == sizeX;
					assert reader.getSizeY() == sizeY;
					int nResolutions = reader.getResolutionCount();
					for (int r = 1; r < nResolutions; r++) {
						reader.setResolution(r);
						int sizeXR = reader.getSizeX();
						int sizeYR = reader.getSizeY();
						if (sizeXR <= 0 || sizeYR <= 0 || sizeXR > sizeX || sizeYR > sizeY)
							throw new IllegalArgumentException("Resolution " + r + " size " + sizeXR + " x " + sizeYR + " invalid!");
					}

					// If we got this far, we have an image we can add
					if (reader.getResolutionCount() == 1 && (
							extraImageNames.contains(originalImageName.toLowerCase()) || extraImageNames.contains(name.toLowerCase().trim()))) {
						logger.debug("Adding associated image {} (thumbnail={})", name, reader.isThumbnailSeries());
						associatedImageMap.put(name, s);
					} else {
						if (imageMap.containsKey(name))
							logger.warn("Duplicate image called {} - only the first will be used", name);
						else {
							if (firstSeries < 0) {
								firstSeries = s;
								firstPixels = sizeX * sizeY * sizeZ * sizeT;
							}
							imageMap.put(name, DefaultImageServerBuilder.createInstance(
									BioFormatsServerBuilder.class, null, uri,
									bfArgs.backToArgs(s)
									));
						}
					}

					if (seriesIndex < 0) {
						if (requestedSeriesName == null) {
							long nPixels = sizeX * sizeY * sizeZ * sizeT;
							if (nPixels > mostPixels) {
								largestSeries = s;
								mostPixels = nPixels;
							}
						} else if (requestedSeriesName.equals(name) || requestedSeriesName.equals(getImageName(meta, s)) || requestedSeriesName.contentEquals(meta.getImageName(s))) {
							seriesIndex = s;
						}
					}
					logger.debug("Found image '{}', size: {} x {} x {} x {} x {} (xyczt)", imageName, sizeX, sizeY, sizeC, sizeZ, sizeT);
				} catch (Exception e) {
					// We don't want to log this prominently if we're requesting a different series anyway
					if ((seriesIndex < 0 || seriesIndex == s) && (requestedSeriesName == null || requestedSeriesName.equals(imageName)))
						logger.warn("Error attempting to read series " + s + " (" + imageName + ") - will be skipped", e);
					else
						logger.trace("Error attempting to read series " + s + " (" + imageName + ") - will be skipped", e);
				}
			}

			// If we have just one image in the image list, then reset to none - we can't switch
			if (imageMap.size() == 1 && seriesIndex < 0) {
				seriesIndex = firstSeries;
//					imageMap.clear();
			} else if (imageMap.size() > 1) {
				// Set default series index, if we need to
				if (seriesIndex < 0) {
					// Choose the first series unless it is substantially smaller than the largest series (e.g. it's a label or macro image)
					if (mostPixels > firstPixels * 4L)
						seriesIndex = largestSeries; // imageMap.values().iterator().next();
					else
						seriesIndex = firstSeries;
				}
				// If we have more than one image, ensure that we have the image name correctly encoded in the path
				uri = new URI(uri.getScheme(), uri.getHost(), uri.getPath(), Integer.toString(seriesIndex));
			}

			if (seriesIndex < 0)
				throw new IOException("Unable to find any valid images within " + uri);

			// Store the series we are actually using
			this.series = seriesIndex;
			reader.setSeries(series);

			// Get the format in case we need it
			format = reader.getFormat();
			logger.debug("Reading format: {}", format);

			// Try getting the magnification
			try {
				String objectiveID = meta.getObjectiveSettingsID(series);
				int objectiveIndex = -1;
				int instrumentIndex = -1;
				int nInstruments = meta.getInstrumentCount();
				for (int i = 0; i < nInstruments; i++) {
					int nObjectives = meta.getObjectiveCount(i);
					for (int o = 0; 0 < nObjectives; o++) {
						if (objectiveID.equals(meta.getObjectiveID(i, o))) {
							instrumentIndex = i;
							objectiveIndex = o;
							break;
						}
					}	    		
				}
				if (instrumentIndex < 0) {
					logger.warn("Cannot find objective for ref {}", objectiveID);
				} else {
					Double magnificationObject = meta.getObjectiveNominalMagnification(instrumentIndex, objectiveIndex);
					if (magnificationObject == null) {
						logger.warn("Nominal objective magnification missing for {}:{}", instrumentIndex, objectiveIndex);
					} else
						magnification = magnificationObject;		    		
				}
			} catch (Exception e) {
				logger.debug("Unable to parse magnification: {}", e.getLocalizedMessage());
			}

			// Get the dimensions for the requested series
			// The first resolution is the highest, i.e. the largest image
			width = reader.getSizeX();
			height = reader.getSizeY();
			tileWidth = reader.getOptimalTileWidth();
			tileHeight = reader.getOptimalTileHeight();
			nChannels = reader.getSizeC();

			// Make sure tile sizes are within range
			if (tileWidth != width)
				tileWidth = getDefaultTileLength(tileWidth, width);
			if (tileHeight != height)
				tileHeight = getDefaultTileLength(tileHeight, height);

			// Prepared to set channel colors
			List<ImageChannel> channels = new ArrayList<>();

			nZSlices = reader.getSizeZ();
//			// Workaround bug whereby VSI channels can also be replicated as z-slices
//			if (options.requestChannelZCorrectionVSI() && nZSlices == nChannels && nChannels > 1 && "CellSens VSI".equals(format)) {
//				doChannelZCorrectionVSI = true;
//				nZSlices = 1;
//			}
			nTimepoints = reader.getSizeT();

			PixelType pixelType;
			switch (reader.getPixelType()) {
				case FormatTools.BIT:
					logger.warn("Pixel type is BIT! This is not currently supported by QuPath.");
					pixelType = PixelType.UINT8;
					break;
				case FormatTools.INT8:
					logger.warn("Pixel type is INT8! This is not currently supported by QuPath.");
					pixelType = PixelType.INT8;
					break;
				case FormatTools.UINT8:
					pixelType = PixelType.UINT8;
					break;
				case FormatTools.INT16:
					pixelType = PixelType.INT16;
					break;
				case FormatTools.UINT16:
					pixelType = PixelType.UINT16;
					break;
				case FormatTools.INT32:
					pixelType = PixelType.INT32;
					break;
				case FormatTools.UINT32:
					logger.warn("Pixel type is UINT32! This is not currently supported by QuPath.");
					pixelType = PixelType.UINT32;
					break;
				case FormatTools.FLOAT:
					pixelType = PixelType.FLOAT32;
					break;
				case FormatTools.DOUBLE:
					pixelType = PixelType.FLOAT64;
					break;
				default:
					throw new IllegalArgumentException("Unsupported pixel type " + reader.getPixelType());
			}
			
			// Determine min/max values if we can
			int bpp = reader.getBitsPerPixel();
			Number minValue = null;
			Number maxValue = null;
			if (bpp < pixelType.getBitsPerPixel()) {
				if (pixelType.isSignedInteger()) {
					minValue = -(int)Math.pow(2, bpp-1);
					maxValue = (int)(Math.pow(2, bpp-1) - 1);
				} else if (pixelType.isUnsignedInteger()) {
					maxValue = (int)(Math.pow(2, bpp) - 1);
				}
			}
			
			boolean isRGB = reader.isRGB() && pixelType == PixelType.UINT8;
			// Remove alpha channel
			if (isRGB && nChannels == 4) {
				logger.warn("Removing alpha channel");
				nChannels = 3;
			} else if (nChannels != 3)
				isRGB = false;

			// Try to read the default display colors for each channel from the file
			if (isRGB) {
				channels.addAll(ImageChannel.getDefaultRGBChannels());
			}
			else {
				// Get channel colors and names
				var tempColors = new ArrayList<ome.xml.model.primitives.Color>(nChannels);
				var tempNames = new ArrayList<String>(nChannels);
				// Be prepared to use default channels if something goes wrong
				try {
					int metaChannelCount = meta.getChannelCount(series);
					// Handle the easy case where the number of channels matches our expectations
					if (metaChannelCount == nChannels) {
						for (int c = 0; c < nChannels; c++) {
							try {
								// try/catch from old code, before we explicitly checked channel count
								// No exception should occur now
								var channelName = meta.getChannelName(series, c);
								var color = meta.getChannelColor(series, c);
								tempNames.add(channelName);
								tempColors.add(color);
							} catch (Exception e) {
								logger.warn("Unable to parse name or color for channel {}", c);
								logger.debug("Unable to parse color", e);
							}
						}
					} else {
						// Handle the harder case, where we have a different number of channels
						// I've seen this with a polarized light CZI image, with a channel count of 2 
						// but in which each of these had 3 samples (resulting in a total of 6 channels)
						logger.debug("Attempting to parse {} channels with metadata channel count", nChannels, metaChannelCount);
						int ind = 0;
						for (int cInd = 0; cInd < metaChannelCount; cInd++) {
							int nSamples = meta.getChannelSamplesPerPixel(series, cInd).getValue();
							var baseChannelName = meta.getChannelName(series, cInd);
							if (baseChannelName != null && baseChannelName.isBlank())
								baseChannelName = null;
							// I *expect* this to be null for interleaved channels, in which case it will be filled in later
							var color = meta.getChannelColor(series, cInd);
							for (int sampleInd = 0; sampleInd < nSamples; sampleInd++) {
								String channelName;
								if (baseChannelName == null)
									channelName = "Channel " + (ind + 1);
								else
									channelName = baseChannelName.strip() + " " + (sampleInd + 1);
								
								tempNames.add(channelName);
								tempColors.add(color);
								
								ind++;
							}
						}
					}
				} catch (Exception e) {
					logger.warn("Exception parsing channels " + e.getLocalizedMessage(), e);
				}
				if (nChannels != tempNames.size() || tempNames.size() != tempColors.size()) {
					logger.warn("The channel names and colors read from the metadata don't match the expected number of channels!");
					logger.warn("Be very cautious working with channels, since the names and colors may be misaligned, incorrect or default values.");
					long nNames = tempNames.stream().filter(n -> n != null && !n.isBlank()).count();
					long nColors = tempColors.stream().filter(n -> n != null).count();
					logger.warn("(I expected {} channels, but found {} names and {} colors)", nChannels, nNames, nColors);
					// Could reset them, but may help to use what we can
//					tempNames.clear();
//					tempColors.clear();
				}
				
					
				// Now loop through whatever we could parse and add QuPath ImageChannel objects
				for (int c = 0; c < nChannels; c++) {
					String channelName = c < tempNames.size() ? tempNames.get(c) : null;
					var color = c < tempColors.size() ? tempColors.get(c) : null; 
					Integer channelColor = null;
					if (color != null)
						channelColor = ColorTools.packARGB(color.getAlpha(), color.getRed(), color.getGreen(), color.getBlue());
					else {
						// Select next available default color, or white (for grayscale) if only one channel
						if (nChannels == 1)
							channelColor = ColorTools.packRGB(255, 255, 255);
						else
							channelColor = ImageChannel.getDefaultChannelColor(c);
					}
					if (channelName == null || channelName.isBlank())
						channelName = "Channel " + (c + 1);
					channels.add(ImageChannel.getInstance(channelName, channelColor));
				}
				assert nChannels == channels.size();
				// Update RGB status if needed - sometimes we might really have an RGB image, but the Bio-Formats flag doesn't show this - 
				// and we want to take advantage of the optimizations where we can
				if (nChannels == 3 && 
						pixelType == PixelType.UINT8 &&
						channels.equals(ImageChannel.getDefaultRGBChannels())
						) {
					isRGB = true;
					colorModel = ColorModel.getRGBdefault();
				} else {
					colorModel = ColorModelFactory.createColorModel(pixelType, channels);
				}
			}

			// Try parsing pixel sizes in micrometers
			double[] timepoints;
			try {
				Length xSize = meta.getPixelsPhysicalSizeX(series);
				Length ySize = meta.getPixelsPhysicalSizeY(series);
				if (xSize != null && ySize != null) {
					pixelWidth = xSize.value(UNITS.MICROMETER).doubleValue();
					pixelHeight = ySize.value(UNITS.MICROMETER).doubleValue();
				} else {
					pixelWidth = Double.NaN;
					pixelHeight = Double.NaN;			    		
				}
				// If we have multiple z-slices, parse the spacing
				if (nZSlices > 1) {
					Length zSize = meta.getPixelsPhysicalSizeZ(series);
					if (zSize != null)
						zSpacing = zSize.value(UNITS.MICROMETER).doubleValue();
					else
						zSpacing = Double.NaN;
				}
				// TODO: Check the Bioformats TimeStamps
				if (nTimepoints > 1) {
					logger.warn("Time stamps read from Bioformats have not been fully verified & should not be relied upon");
					// Here, we don't try to separate timings by z-slice & channel...
					int lastTimepoint = -1;
					int count = 0;
					timepoints = new double[nTimepoints];
					logger.debug("Plane count: " + meta.getPlaneCount(series));
					for (int plane = 0; plane < meta.getPlaneCount(series); plane++) {
						int timePoint = meta.getPlaneTheT(series, plane).getValue();
						logger.debug("Checking " + timePoint);
						if (timePoint != lastTimepoint) {
							timepoints[count] = meta.getPlaneDeltaT(series, plane).value(UNITS.SECOND).doubleValue();
							logger.debug(String.format("Timepoint %d: %.3f seconds", count, timepoints[count]));
							lastTimepoint = timePoint;
							count++;
						}
					}
					timeUnit = TimeUnit.SECONDS;
				} else {
					timepoints = new double[0];
				}
			} catch (Exception e) {
				logger.error("Error parsing metadata", e);
				pixelWidth = Double.NaN;
				pixelHeight = Double.NaN;
				zSpacing = Double.NaN;
				timepoints = null;
				timeUnit = null;
			}

			// Loop through the series & determine downsamples
			int nResolutions = reader.getResolutionCount();
			var resolutionBuilder = new ImageResolutionLevel.Builder(width, height)
					.addFullResolutionLevel();

			// I have seen czi files where the resolutions are not read correctly & this results in an IndexOutOfBoundsException
			for (int i = 1; i < nResolutions; i++) {
				reader.setResolution(i);
				try {
					int w = reader.getSizeX();
					int h = reader.getSizeY();
					if (w <= 0 || h <= 0) {
						logger.warn("Invalid resolution size {} x {}! Will skip this level, but something seems wrong...", w, h);
						continue;
					}
					// In some VSI images, the calculated downsamples for width & height can be wildly discordant, 
					// and we are better off using defaults
					if ("CellSens VSI".equals(format)) {
						double downsampleX = (double)width / w;
						double downsampleY = (double)height / h;
						double downsample = Math.pow(2, i);
						if (!GeneralTools.almostTheSame(downsampleX, downsampleY, 0.01)) {
							logger.warn("Non-matching downsamples calculated for level {} ({} and {}); will use {} instead", i, downsampleX, downsampleY, downsample);
							resolutionBuilder.addLevel(downsample, w, h);
							continue;
						}
					}
					resolutionBuilder.addLevel(w, h);
				} catch (Exception e) {
					logger.warn("Error attempting to extract resolution " + i + " for " + getImageName(meta, series), e);					
					break;
				}
			}
			
			// Generate a suitable name for this image
			String imageName = getFile().getName();
			String shortName = getImageName(meta, seriesIndex);
			if (shortName == null || shortName.isBlank()) {
				if (imageMap.size() > 1)
					imageName = imageName + " - Series " + seriesIndex;
			} else if (!imageName.equals(shortName))
				imageName = imageName + " - " + shortName;

			this.args = args;
			
			// Build resolutions
			var resolutions = resolutionBuilder.build();
			// Unused code to check if resolutions seem to be correct
//			var iter = resolutions.iterator();
//			int r = 0;
//			while (iter.hasNext()) {
//				var resolution = iter.next();
//				double widthDifference = Math.abs(resolution.getWidth() - width/resolution.getDownsample());
//				double heightDifference = Math.abs(resolution.getHeight() - height/resolution.getDownsample());
//				if (widthDifference > Math.max(2.0, resolution.getWidth()*0.01) || heightDifference > Math.max(2.0, resolution.getHeight()*0.01)) {
//					logger.warn("Aspect ratio of resolution level {} differs from", r);
//					iter.remove();
//					while (iter.hasNext()) {
//						iter.next();
//						iter.remove();
//					}
//				}
//				r++;
//			}
			
			// Set metadata
			path = createID();
			ImageServerMetadata.Builder builder = new ImageServerMetadata.Builder(
					getClass(), path, width, height).
//					args(args).
					minValue(minValue).
					maxValue(maxValue).
					name(imageName).
					channels(channels).
					sizeZ(nZSlices).
					sizeT(nTimepoints).
					levels(resolutions).
					pixelType(pixelType).
					rgb(isRGB);

			if (Double.isFinite(magnification))
				builder = builder.magnification(magnification);

			if (timeUnit != null)
				builder = builder.timepoints(timeUnit, timepoints);

			if (Double.isFinite(pixelWidth + pixelHeight))
				builder = builder.pixelSizeMicrons(pixelWidth, pixelHeight);

			if (Double.isFinite(zSpacing))
				builder = builder.zSpacingMicrons(zSpacing);

			// Check the tile size if it is reasonable
			if ((long)tileWidth * (long)tileHeight * (long)nChannels * (bpp/8) >= Integer.MAX_VALUE) {
				builder.preferredTileSize(Math.min(DEFAULT_TILE_SIZE, width), Math.min(DEFAULT_TILE_SIZE, height));
			} else
				builder.preferredTileSize(tileWidth, tileHeight);

			originalMetadata = builder.build();
		}

		// Bioformats can use ImageIO for JPEG decoding, and permitting the disk-based cache can slow it down... so here we turn it off
		// TODO: Document - or improve - the setting of ImageIO disk cache
		ImageIO.setUseCache(false);

		long endTime = System.currentTimeMillis();
		logger.debug(String.format("Initialization time: %d ms", endTime-startTime));
	}
	
	
	/**
	 * Get a sensible default tile size for a specified dimension.
	 * @param tileLength tile width or height
	 * @param imageLength corresponding image width or height
	 * @return a sensible tile length, bounded by the image width or height
	 */
	static int getDefaultTileLength(int tileLength, int imageLength) {
		if (tileLength <= 0) {
			tileLength = DEFAULT_TILE_SIZE;
		} else if (tileLength < MIN_TILE_SIZE) {
			tileLength = (int)Math.ceil((double)MIN_TILE_SIZE / tileLength) * tileLength;
		}
		return Math.min(tileLength, imageLength);
	}
	
	
	
	/**
	 * Get the image name for a series, making sure to remove any trailing null terminators.
	 * <p>
	 * See https://github.com/qupath/qupath/issues/573
	 * @param series
	 * @return
	 */
	private String getImageName(OMEXMLMetadata meta, int series) {
		 String name = meta.getImageName(series);
		 if (name == null)
			 return null;
		 while (name.endsWith("\0"))
			 name = name.substring(0, name.length()-1);
		 return name;
	}
	
	/**
	 * Get the format String, as returned by Bio-Formats {@code IFormatReader.getFormat()}.
	 * @return
	 */
	public String getFormat() {
		return format;
	}
	
	@Override
	public Collection<URI> getURIs() {
		return Collections.singletonList(uri);
	}
	
	@Override
	public String createID() {
		String id = getClass().getSimpleName() + ": " + uri.toString();
		if (args.length > 0) {
			id += "[" + String.join(", ", args) + "]";
		}
		return id;
	}
	
	/**
	 * Returns a builder capable of creating a server like this one.
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return DefaultImageServerBuilder.createInstance(BioFormatsServerBuilder.class, getMetadata(), uri, args);
	}
	
	int getPreferredTileWidth() {
		return getMetadata().getPreferredTileWidth();
	}

	int getPreferredTileHeight() {
		return getMetadata().getPreferredTileHeight();
	}

	
//	IFormatReader getPrimaryReader() throws DependencyException, ServiceException, FormatException, IOException {
//		return manager.getPrimaryReader(this, this.filePath);
//	}
	
	/**
	 * Get the series index, as used by Bio-Formats.
	 * @return
	 */
	public int getSeries() {
		return series;
	}
	
	
	@Override
	public BufferedImage readTile(TileRequest tileRequest) throws IOException {
		try {
			return readerPool.openImage(tileRequest, series, nChannels(), isRGB(), colorModel);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}
	
	
	@Override
	public String getServerType() {
		return "Bio-Formats";
	}
	
	@Override
	public synchronized void close() throws Exception {
		super.close();
		readerPool.close();
	}

	boolean containsSubImages() {
		return imageMap != null && !imageMap.isEmpty();
	}

	/**
	 * Get the MetadataStore, as used by Bio-Formats. This can be used to query metadata values not otherwise accessible.
	 * @return
	 */
	public OMEPyramidStore getMetadataStore() {
		return readerPool.metadata;
	}
	
	/**
	 * Retrieve a string representation of the metadata OME-XML.
	 * 
	 * @return
	 */
	public String dumpMetadata() {
		try {
			OMEXMLMetadata metadata = (OMEXMLMetadata)getMetadataStore();
			return metadata.dumpXML();
		} catch (Exception e) {
			logger.error("Unable to dump metadata", e);
		}
		return null;
	}

	
	@Override
	public List<String> getAssociatedImageList() {
		if (associatedImageMap == null || associatedImageMap.isEmpty())
			return Collections.emptyList();
		return new ArrayList<>(associatedImageMap.keySet());
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		if (associatedImageMap == null || !associatedImageMap.containsKey(name))
			throw new IllegalArgumentException("No associated image with name '" + name + "' for " + getPath());
		
		int series = associatedImageMap.get(name);
		try {
			return readerPool.openSeries(series);
		} catch (Exception e) {
			logger.error("Error reading associated image " + name + ": " + e.getLocalizedMessage(), e);
			return null;
		}
	}
	
	
	/**
	 * Get the underlying file.
	 * 
	 * @return
	 */
	public File getFile() {
		return filePathOrUrl == null ? null : new File(filePathOrUrl);
	}


	Map<String, ServerBuilder<BufferedImage>> getImageBuilders() {
		return Collections.unmodifiableMap(imageMap);
	}


	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}
	
	/**
	 * Get the class name of the first reader that potentially supports the file type, or null if no reader can be found.
	 * <p>
	 * This method only uses the path and file extensions, generously returning the first potential 
	 * reader based on the extension. Its purpose is to help filter out hopeless cases, not to establish 
	 * the 'correct' reader.
	 * 
	 * @param path
	 * @return
	 * @throws InstantiationException
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 * @throws InvocationTargetException
	 * @throws NoSuchMethodException
	 * @throws SecurityException
	 */
	static String getSupportedReaderClass(String path) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException, NoSuchMethodException, SecurityException {
		path = path.toLowerCase();
		for (var cls : ImageReader.getDefaultReaderClasses().getClasses()) {
			var reader = cls.getConstructor().newInstance();
			if (reader.isThisType(path, false))
				return cls.getName();
			for (String s : reader.getSuffixes()) {
				if (s != null && !s.isBlank() && path.endsWith(s.toLowerCase()))
					return cls.getName();
			}
		}
		return null;
	}
	
	
	
	/**
	 * Helper class that manages a pool of readers.
	 * The purpose is to allow multiple threads to take the next available reader, without
	 */
	static class ReaderPool implements AutoCloseable {
		
		private static final Logger logger = LoggerFactory.getLogger(ReaderPool.class);
		
		/**
		 * Absolute maximum number of permitted readers (queue capacity)
		 */
		private static final int MAX_QUEUE_CAPACITY = 128;
		
		private String id;
		private BioFormatsServerOptions options;
		private BioFormatsArgs args;
		private ClassList<IFormatReader> classList;
		
		private volatile boolean isClosed = false;
		
		private AtomicInteger totalReaders = new AtomicInteger(0);
		private List<IFormatReader> additionalReaders = Collections.synchronizedList(new ArrayList<>());
		private ArrayBlockingQueue<IFormatReader> queue;
		
		private OMEPyramidStore metadata;
		private IFormatReader mainReader;
		
		private ForkJoinTask<?> task;
		
		
		ReaderPool(BioFormatsServerOptions options, String id, BioFormatsArgs args) throws FormatException, IOException {
			this.id = id;
			this.options = options;
			this.args = args;
			
			queue = new ArrayBlockingQueue<>(MAX_QUEUE_CAPACITY); // Set a reasonably large capacity (don't want to block when trying to add)
			metadata = (OMEPyramidStore)MetadataTools.createOMEXMLMetadata();
			
			// Create the main reader
			long startTime = System.currentTimeMillis();
			mainReader = createReader(options, null, id, metadata, args);
			
			long endTime = System.currentTimeMillis();
			logger.debug("Reader {} created in {} ms", mainReader, endTime - startTime);
			
			// Make the main reader available
			queue.add(mainReader);
			
			// Store the class so we don't need to go hunting later
			classList = unwrapClasslist(mainReader);
		}
		
		IFormatReader getMainReader() {
			return mainReader;
		}
		
		private void createAdditionalReader(BioFormatsServerOptions options, final ClassList<IFormatReader> classList, 
				final String id, BioFormatsArgs args) {
			try {
				if (isClosed)
					return;
				logger.debug("Requesting new reader for thread {}", Thread.currentThread());
				var newReader = createReader(options, classList, id, null, args);
				if (newReader != null) {
					additionalReaders.add(newReader);
					queue.add(newReader);
					logger.debug("Created new reader (total={})", additionalReaders.size());
				} else
					logger.warn("New Bio-Formats reader could not be created (returned null)");
			} catch (Exception e) {
				logger.error("Error creating additional readers: " + e.getLocalizedMessage(), e);
			}
		}
		
		
		private int getMaxReaders() {
			int max = options == null ? Runtime.getRuntime().availableProcessors() : options.getMaxReaders();
			return Math.min(MAX_QUEUE_CAPACITY, Math.max(1, max));
		}
		

		/**
		 * Create a new {@code IFormatReader}, with memoization if necessary.
		 * 
		 * @param options 	options used to control the reader generation
		 * @param classList optionally specify a list of potential reader classes, if known (to avoid a more lengthy search)
		 * @param id 		file path for the image.
		 * @param store 	optional MetadataStore; this will be set in the reader if needed. If it is unspecified, a dummy store will be created a minimal metadata requested.
		 * @param args      optional args to customize reading
		 * @return the {@code IFormatReader}
		 * @throws FormatException
		 * @throws IOException
		 */
		@SuppressWarnings("resource")
		private IFormatReader createReader(final BioFormatsServerOptions options, final ClassList<IFormatReader> classList, 
				final String id, final MetadataStore store, BioFormatsArgs args) throws FormatException, IOException {
			
			int maxReaders = getMaxReaders();
			int nReaders = totalReaders.getAndIncrement();
			if (mainReader != null && nReaders > maxReaders) {
				logger.warn("No new reader will be created (already created {}, max readers {})", nReaders, maxReaders);
				totalReaders.decrementAndGet();
				return null;
			}
			
			IFormatReader imageReader;
			if (classList != null) {
				imageReader = new ImageReader(classList);
			} else
				imageReader = new ImageReader();
						
			imageReader.setFlattenedResolutions(false);
			
			// Try to set any reader options that we have
			MetadataOptions metadataOptions = imageReader.getMetadataOptions();
			var readerOptions = args.readerOptions;
			if (!readerOptions.isEmpty() && metadataOptions instanceof DynamicMetadataOptions) {
				for (var option : readerOptions.entrySet()) {
					((DynamicMetadataOptions)metadataOptions).set(option.getKey(), option.getValue());
				}
			}
			
			// TODO: Warning! Memoization does not play nicely with options like 
			// --bfOptions zeissczi.autostitch=false
			// in a way that options don't have an effect unless QuPath is restarted.
			Memoizer memoizer = null;
			int memoizationTimeMillis = options.getMemoizationTimeMillis();
			File dir = null;
			File fileMemo = null;
			boolean useTempMemoDirectory = false;
			// Check if we want to (and can) use memoization
			if (BioFormatsServerOptions.allowMemoization() && memoizationTimeMillis >= 0) {
				// Try to use a specified directory
				String pathMemoization = options.getPathMemoization();
				if (pathMemoization != null && !pathMemoization.trim().isEmpty()) {
					dir = new File(pathMemoization);
					if (!dir.isDirectory()) {
						logger.warn("Memoization path does not refer to a valid directory, will be ignored: {}", dir.getAbsolutePath());
						dir = null;
					}
				}
				if (dir == null) {
					dir = getTempMemoDir(true);
					useTempMemoDirectory = dir != null;
				}
				if (dir != null) {
					try {
						memoizer = new Memoizer(imageReader, memoizationTimeMillis, dir);
						fileMemo = memoizer.getMemoFile(id);
						// The call to .toPath() should throw an InvalidPathException if there are illegal characters
						// If so, we want to know that now before committing to the memoizer
						if (fileMemo != null && fileMemo.toPath() != null)
							imageReader = memoizer;
					} catch (Exception e) {
						logger.warn("Unable to use memoization: {}", e.getLocalizedMessage());
						logger.debug(e.getLocalizedMessage(), e);
						fileMemo = null;
						memoizer = null;
					}
				}
			}
			
			
			if (store != null)
				imageReader.setMetadataStore(store);
			else {
				imageReader.setMetadataStore(new DummyMetadata());
				imageReader.setOriginalMetadataPopulated(false);
			}
			
			var swapDimensions = args.getSwapDimensions();
			if (swapDimensions != null)
				logger.debug("Creating DimensionSwapper for {}", swapDimensions);
			
			
			if (id != null) {
				if (fileMemo != null) {
					// If we're using a temporary directory, delete the memo file when app closes
					if (useTempMemoDirectory)
						tempMemoFiles.add(fileMemo);
					
					long memoizationFileSize = fileMemo == null ? 0L : fileMemo.length();
					boolean memoFileExists = fileMemo != null && fileMemo.exists();
					try {
						if (swapDimensions != null)
							imageReader = DimensionSwapper.makeDimensionSwapper(imageReader);
						imageReader.setId(id);
					} catch (Exception e) {
						if (memoFileExists) {
							logger.warn("Problem with memoization file {} ({}), will try to delete it", fileMemo.getName(), e.getLocalizedMessage());
							fileMemo.delete();
						}
						imageReader.close();
						if (swapDimensions != null)
							imageReader = DimensionSwapper.makeDimensionSwapper(imageReader);
						imageReader.setId(id);
					}
					memoizationFileSize = fileMemo == null ? 0L : fileMemo.length();
					if (memoizationFileSize > 0L) {
						if (memoizationFileSize > MAX_PARALLELIZATION_MEMO_SIZE) {
							logger.warn(String.format("The memoization file is very large (%.1f MB) - parallelization may be turned off to save memory",
									memoizationFileSize/(1024.0*1024.0)));
						}
						memoizationSizeMap.put(id, memoizationFileSize);
					} if (memoizationFileSize == 0L)
						logger.debug("No memoization file generated for {}", id);
					else if (!memoFileExists)
						logger.debug(String.format("Generating memoization file %s (%.2f MB)", fileMemo.getAbsolutePath(), memoizationFileSize/1024.0/1024.0));
					else
						logger.debug("Memoization file exists at {}", fileMemo.getAbsolutePath());
				} else {
					if (swapDimensions != null)
						imageReader = DimensionSwapper.makeDimensionSwapper(imageReader);
					imageReader.setId(id);
				}
			}
						
			if (swapDimensions != null) {
				// The series needs to be set before swapping dimensions
				if (args.series >= 0)
					imageReader.setSeries(args.series);
				((DimensionSwapper)imageReader).swapDimensions(swapDimensions);
			}
			
			
			cleanables.add(cleaner.register(this, new ReaderCleaner(Integer.toString(cleanables.size()+1), imageReader)));
			
			return imageReader;
		}
		
				
		
		private IFormatReader nextQueuedReader() throws InterruptedException {
			var nextReader = queue.poll();
			if (nextReader != null)
				return nextReader;
			synchronized (this) {
				if (!isClosed && (task == null || task.isDone()) && totalReaders.get() < getMaxReaders()) {
					logger.debug("Requesting reader for {}", id);
					task = ForkJoinPool.commonPool().submit(() -> createAdditionalReader(options, classList, id, args));				
				}
			}
			if (isClosed)
				return null;
			try {
				return queue.poll(60, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				logger.warn("Interrupted exception when awaiting next queued reader: {}", e.getLocalizedMessage());
				return isClosed ? null : mainReader;
			}
		}
		
		
		BufferedImage openImage(TileRequest tileRequest, int series, int nChannels, boolean isRGB, ColorModel colorModel) throws IOException, InterruptedException {
			
			int level = tileRequest.getLevel();
			int tileX = tileRequest.getTileX();
			int tileY = tileRequest.getTileY();
			int tileWidth = tileRequest.getTileWidth();
			int tileHeight = tileRequest.getTileHeight();
			int z = tileRequest.getZ();
			int t = tileRequest.getT();
	
			byte[][] bytes = null;
			int effectiveC;
			int sizeC = nChannels;
			int length = 0;
			ByteOrder order = ByteOrder.BIG_ENDIAN;
			boolean interleaved;
			int pixelType;
			boolean normalizeFloats = false;

			
			IFormatReader ipReader = null;
			try {
				ipReader = nextQueuedReader();
				if (ipReader == null) {
					throw new IOException("Reader is null - was the image already closed? " + id);
				}
	
				// Check if this is non-zero
				if (tileWidth <= 0 || tileHeight <= 0) {
					throw new IOException("Unable to request pixels for region with downsampled size " + tileWidth + " x " + tileHeight);
				}
		
				synchronized(ipReader) {
					ipReader.setSeries(series);
					ipReader.setResolution(level);
					order = ipReader.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
					interleaved = ipReader.isInterleaved();
					pixelType = ipReader.getPixelType();
					normalizeFloats = ipReader.isNormalized();
	
					// Single-channel & RGB images are straightforward... nothing more to do
					if ((ipReader.isRGB() && isRGB) || nChannels == 1) {
						// Read the image - or at least the first channel
						int ind = ipReader.getIndex(z, 0, t);
						try {
							byte[] bytesSimple = ipReader.openBytes(ind, tileX, tileY, tileWidth, tileHeight);
							return AWTImageTools.openImage(bytesSimple, ipReader, tileWidth, tileHeight);
						} catch (Exception e) {
							logger.error("Error opening image " + ind + " for " + tileRequest.getRegionRequest(), e);
						}
					}
					// Read bytes for all the required channels
					effectiveC = ipReader.getEffectiveSizeC();
					bytes = new byte[effectiveC][];
					try {
						for (int c = 0; c < effectiveC; c++) {
							int ind = ipReader.getIndex(z, c, t);
							bytes[c] = ipReader.openBytes(ind, tileX, tileY, tileWidth, tileHeight);
							length = bytes[c].length;
						}
					} catch (FormatException e) {
						throw new IOException(e);
					}
				}
			} finally {
				if (ipReader != null)
					queue.put(ipReader);
			}
			
			DataBuffer dataBuffer;
			switch (pixelType) {
			case (FormatTools.UINT8):
				dataBuffer = new DataBufferByte(bytes, length);
			break;
			case (FormatTools.UINT16):
				length /= 2;
				short[][] array = new short[bytes.length][length];
				for (int i = 0; i < bytes.length; i++) {
					ShortBuffer buffer = ByteBuffer.wrap(bytes[i]).order(order).asShortBuffer();
					array[i] = new short[buffer.limit()];
					buffer.get(array[i]);
				}
				dataBuffer = new DataBufferUShort(array, length);
				break;
			case (FormatTools.INT16):
				length /= 2;
				short[][] shortArray = new short[bytes.length][length];
				for (int i = 0; i < bytes.length; i++) {
					ShortBuffer buffer = ByteBuffer.wrap(bytes[i]).order(order).asShortBuffer();
					shortArray[i] = new short[buffer.limit()];
					buffer.get(shortArray[i]);
				}
				dataBuffer = new DataBufferShort(shortArray, length);
				break;
			case (FormatTools.INT32):
				length /= 4;
				int[][] intArray = new int[bytes.length][length];
					for (int i = 0; i < bytes.length; i++) {
						IntBuffer buffer = ByteBuffer.wrap(bytes[i]).order(order).asIntBuffer();
						intArray[i] = new int[buffer.limit()];
						buffer.get(intArray[i]);
					}
				dataBuffer = new DataBufferInt(intArray, length);
				break;
			case (FormatTools.FLOAT):
				length /= 4;
				float[][] floatArray = new float[bytes.length][length];
				for (int i = 0; i < bytes.length; i++) {
					FloatBuffer buffer = ByteBuffer.wrap(bytes[i]).order(order).asFloatBuffer();
					floatArray[i] = new float[buffer.limit()];
					buffer.get(floatArray[i]);
					if (normalizeFloats)
						floatArray[i] = DataTools.normalizeFloats(floatArray[i]);
				}
				dataBuffer = new DataBufferFloat(floatArray, length);
				break;
			case (FormatTools.DOUBLE):
				length /= 8;
				double[][] doubleArray = new double[bytes.length][length];
				for (int i = 0; i < bytes.length; i++) {
					DoubleBuffer buffer = ByteBuffer.wrap(bytes[i]).order(order).asDoubleBuffer();
					doubleArray[i] = new double[buffer.limit()];
					buffer.get(doubleArray[i]);
					if (normalizeFloats)
						doubleArray[i] = DataTools.normalizeDoubles(doubleArray[i]);
				}
				dataBuffer = new DataBufferDouble(doubleArray, length);
				break;
			// TODO: Consider conversion to closest supported pixel type
			case FormatTools.BIT:
			case FormatTools.INT8:
			case FormatTools.UINT32:
			default:
				throw new UnsupportedOperationException("Unsupported pixel type " + pixelType);
			}

			SampleModel sampleModel;

			if (effectiveC == 1 && sizeC > 1) {
				// Handle channels stored in the same plane
				int[] offsets = new int[sizeC];
				if (interleaved) {
					for (int b = 0; b < sizeC; b++)
						offsets[b] = b;
					sampleModel = new PixelInterleavedSampleModel(dataBuffer.getDataType(), tileWidth, tileHeight, sizeC, sizeC*tileWidth, offsets);
				} else {
					for (int b = 0; b < sizeC; b++)
						offsets[b] = b * tileWidth * tileHeight;
					sampleModel = new ComponentSampleModel(dataBuffer.getDataType(), tileWidth, tileHeight, 1, tileWidth, offsets);
				}
			} else if (sizeC > effectiveC) {
				// Handle multiple bands, but still interleaved
				// See https://forum.image.sc/t/qupath-cant-open-polarized-light-scans/65951
				int[] offsets = new int[sizeC];
				int[] bandInds = new int[sizeC];
				int ind = 0;
				
				int channelCount = metadata.getChannelCount(series);
				for (int cInd = 0; cInd < channelCount; cInd++) {
					int nSamples = metadata.getChannelSamplesPerPixel(series, cInd).getValue();
					for (int s = 0; s < nSamples; s++) {
						bandInds[ind] = cInd;
						if (interleaved) {
							offsets[ind] = s;
						} else {
							offsets[ind] = s * tileWidth * tileHeight;							
						}
						ind++;
					}
				}
				// TODO: Check this! It works for the only test image I have... (2 channels with 3 samples each)
				// I would guess it fails if pixelStride does not equal nSamples, and if nSamples is different for different 'channels' - 
				// but I don't know if this occurs in practice.
				// If it does, I don't see a way to use a ComponentSampleModel... which could complicate things quite a bit
				int pixelStride = sizeC / effectiveC;
				int scanlineStride = pixelStride*tileWidth;
				sampleModel = new ComponentSampleModel(dataBuffer.getDataType(), tileWidth, tileHeight, pixelStride, scanlineStride, bandInds, offsets);
			} else {
				// Merge channels on different planes
				sampleModel = new BandedSampleModel(dataBuffer.getDataType(), tileWidth, tileHeight, sizeC);
			}

			WritableRaster raster = WritableRaster.createWritableRaster(sampleModel, dataBuffer, null);
			return new BufferedImage(colorModel, raster, false, null);
			
		}
		
		
		public BufferedImage openSeries(int series) throws InterruptedException, FormatException, IOException {
			IFormatReader reader = null;
			try {
				reader = nextQueuedReader();
				synchronized (reader) {
					int previousSeries = reader.getSeries();
					try {
						reader.setSeries(series);
						int nResolutions = reader.getResolutionCount();
						if (nResolutions > 0) {
							reader.setResolution(0);
						}
						// TODO: Handle color transforms here, or in the display of labels/macro images - in case this isn't RGB
						byte[] bytesSimple = reader.openBytes(reader.getIndex(0, 0, 0));
						return AWTImageTools.openImage(bytesSimple, reader, reader.getSizeX(), reader.getSizeY());
					} finally {
						reader.setSeries(previousSeries);
					}
				}
			} finally {
				if (reader != null)
					queue.put(reader);
			}
		}
		
		
		
		private static ClassList<IFormatReader> unwrapClasslist(IFormatReader reader) {
			while (reader instanceof ReaderWrapper)
				reader = ((ReaderWrapper)reader).getReader();
			var classlist = new ClassList<>(IFormatReader.class);
			classlist.addClass(reader.getClass());
			return classlist;
		}
		
		
		
		@Override
		public void close() throws Exception {
			isClosed = true;
			if (task != null && !task.isDone())
				task.cancel(true);
			for (var c : cleanables) {
				try {
					c.clean();
				} catch (Exception e) {
					logger.error("Exception during cleanup: " + e.getLocalizedMessage());
					logger.debug(e.getLocalizedMessage(), e);
				}
			}
			// Allow the queue to be garbage collected - clearing could result in a queue.poll()
			// lingering far too long
//			queue.clear();
		}
		
		
		
		private static Cleaner cleaner = Cleaner.create();
		private List<Cleanable> cleanables = new ArrayList<>();


		/**
		 * Map of memoization file sizes.
		 */
		private static Map<String, Long> memoizationSizeMap = new ConcurrentHashMap<>();

		/**
		 * Temporary directory for storing memoization files
		 */
		private static File dirMemoTemp = null;

		/**
		 * Set of created temp memo files
		 */
		private static Set<File> tempMemoFiles = new HashSet<>();


		/**
		 * Request the file size of any known memoization file for a specific ID.
		 * 
		 * @param id
		 * @return
		 */
		public long getMemoizationFileSize(String id) {
			return memoizationSizeMap.getOrDefault(id, Long.valueOf(0L));
		}

		private static File getTempMemoDir(boolean create) throws IOException {
			if (create && dirMemoTemp == null) {
				synchronized (ReaderPool.class) {
					if (dirMemoTemp == null) {
						Path path = Files.createTempDirectory("qupath-memo-");
						dirMemoTemp = path.toFile();
						Runtime.getRuntime().addShutdownHook(new Thread() {
							@Override
							public void run() {
								deleteTempMemoFiles();
							}
						});
						logger.warn("Temp memoization directory created at {}", dirMemoTemp);
						logger.warn("If you want to avoid this warning, either specify a memoization directory in the preferences or turn off memoization by setting the time to < 0");
					}
				}
			}
			return dirMemoTemp;
		}

		/**
		 * Delete any memoization files registered as being temporary, and also the 
		 * temporary memoization directory (if it exists).
		 * Note that this acts both recursively and rather conservatively, stopping if a file is 
		 * encountered that is not expected.
		 */
		private static void deleteTempMemoFiles() {
			for (File f : tempMemoFiles) {
				// Be extra-careful not to delete too much...
				if (!f.exists())
					continue;
				if (!f.isFile() || !f.getName().endsWith(".bfmemo")) {
					logger.warn("Unexpected memoization file, will not delete {}", f.getAbsolutePath());
					return;
				}
				if (f.delete())
					logger.debug("Deleted temp memoization file {}", f.getAbsolutePath());
				else
					logger.warn("Could not delete temp memoization file {}", f.getAbsolutePath());
			}
			if (dirMemoTemp == null)
				return;
			deleteEmptyDirectories(dirMemoTemp);
		}

		/**
		 * Delete a directory and all sub-directories, assuming each contains only empty directories.
		 * This is applied recursively, stopping at the first failure (i.e. any directory containing files).
		 * @param dir
		 * @return true if the directory could be deleted, false otherwise
		 */
		private static boolean deleteEmptyDirectories(File dir) {
			if (!dir.isDirectory())
				return false;
			int nFiles = 0;
			var files = dir.listFiles();
			if (files == null) {
				logger.debug("Unable to list files for {}", dir);
				return false;
			}
			for (File f : files) {
				if (f.isDirectory()) {
					if (!deleteEmptyDirectories(f))
						return false;
				} else if (f.isFile())
					nFiles++;
			}
			if (nFiles == 0) {
				if (dir.delete()) {
					logger.debug("Deleting empty memoization directory {}", dir.getAbsolutePath());
					return true;
				} else {
					logger.warn("Could not delete temp memoization directory {}", dir.getAbsolutePath());
					return false;
				}
			} else {
				logger.warn("Temp memoization directory contains files, will not delete {}", dir.getAbsolutePath());
				return false;
			}
		}


		/**
		 * Helper class that helps ensure readers are closed when a reader pool is no longer reachable.
		 */
		static class ReaderCleaner implements Runnable {

			private String name;
			private IFormatReader reader;

			ReaderCleaner(String name, IFormatReader reader) {
				this.name = name;
				this.reader = reader;
			}

			@Override
			public void run() {
				logger.debug("Cleaner " + name + " called for " + reader + " (" + reader.getCurrentFile() + ")");
				try {
					this.reader.close(false);
				} catch (IOException e) {
					logger.warn("Error when calling cleaner for " + name, e);
				}
			}

		}
		
		
	}
	
	
	
	
	
	
	static class BioFormatsArgs {
		
		@Option(names = {"--series", "-s"}, defaultValue = "-1", description = "Series number (0-based, must be < image count for the file)")
		int series = -1;

		@Option(names = {"--name", "-n"}, defaultValue = "", description = "Series name (legacy option, please use --series instead)")
		String seriesName = "";
		
		@Option(names = {"--dims"}, defaultValue = "", description = "Swap dimensions. "
				+ "This should be a String of the form XYCZT, ordered according to how the image plans should be interpreted.")
		String swapDimensions = null;

		// Specific options used by some Bio-Formats readers, e.g. Map.of("zeissczi.autostitch", "false")
		@Option(names = {"--bfOptions"}, description = "Bio-Formats reader options")
		Map<String, String> readerOptions = new LinkedHashMap<>();
		
		@Unmatched
		List<String> unmatched = new ArrayList<>();
		
		BioFormatsArgs() {}
		
		/**
		 * Return to an array of String args.
		 * @param series 
		 * @return
		 */
		String[] backToArgs(int series) {
			var args = new ArrayList<String>();
			if (series >= 0) {
				args.add("--series");
				args.add(Integer.toString(series));
			} else if (this.series >= 0) {
				args.add("--series");
				args.add(Integer.toString(this.series));				
			} else if (seriesName != null && !seriesName.isBlank()) {
				args.add("--name");
				args.add(seriesName);				
			}
			if (swapDimensions != null && !swapDimensions.isBlank()) {
				args.add("--dims");
				args.add(swapDimensions);
			}
			for (var option : readerOptions.entrySet()) {
				// Note: this assumes that options & values contain no awkwardness (e.g. quotes, spaces)
				args.add("--bfOptions");
				args.add(option.getKey()+"="+option.getValue());
			}
			args.addAll(unmatched);
			return args.toArray(String[]::new);
		}
		
		String getSwapDimensions() {
			return swapDimensions == null || swapDimensions.isBlank() ? null : swapDimensions.toUpperCase();
		}
		
		static BioFormatsArgs parse(String[] args) {
			var bfArgs = new BioFormatsArgs();
			new CommandLine(bfArgs).parseArgs(args);
			return bfArgs;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((readerOptions == null) ? 0 : readerOptions.hashCode());
			result = prime * result + series;
			result = prime * result + ((seriesName == null) ? 0 : seriesName.hashCode());
			result = prime * result + ((swapDimensions == null) ? 0 : swapDimensions.hashCode());
			result = prime * result + ((unmatched == null) ? 0 : unmatched.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			BioFormatsArgs other = (BioFormatsArgs) obj;
			if (readerOptions == null) {
				if (other.readerOptions != null)
					return false;
			} else if (!readerOptions.equals(other.readerOptions))
				return false;
			if (series != other.series)
				return false;
			if (seriesName == null) {
				if (other.seriesName != null)
					return false;
			} else if (!seriesName.equals(other.seriesName))
				return false;
			if (swapDimensions == null) {
				if (other.swapDimensions != null)
					return false;
			} else if (!swapDimensions.equals(other.swapDimensions))
				return false;
			if (unmatched == null) {
				if (other.unmatched != null)
					return false;
			} else if (!unmatched.equals(other.unmatched))
				return false;
			return true;
		}
		
	}
	
}
