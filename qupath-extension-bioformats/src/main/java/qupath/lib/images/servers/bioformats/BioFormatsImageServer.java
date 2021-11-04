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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
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
import loci.formats.gui.AWTImageTools;
import loci.formats.in.DynamicMetadataOptions;
import loci.formats.in.MetadataOptions;
import loci.formats.meta.DummyMetadata;
import loci.formats.meta.IMetadata;
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
import qupath.lib.images.servers.bioformats.BioFormatsImageServer.BioFormatsReaderManager.LocalReaderWrapper;

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
	 * Path to the base image file - will be the same as path, unless the path encodes the name of a specific series, in which case this refers to the file without the series included
	 */
	private String filePath;
		
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
	
	/**
	 * QuPath-specific options for how the image server should behave, such as using parallelization or memoization.
	 */
	private BioFormatsServerOptions options;
		
	/**
	 * Manager to help keep multithreading under control.
	 */
	private static BioFormatsReaderManager manager = new BioFormatsReaderManager();
	
	/**
	 * ColorModel to use with all BufferedImage requests.
	 */
	private ColorModel colorModel;
	
	/**
	 * Primary reader for the current server.
	 */
	private LocalReaderWrapper readerWrapper;
	
	/**
	 * Primary metadata store.
	 */
	private OMEPyramidStore meta;
	
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
	
	
	BioFormatsImageServer(URI uri, final BioFormatsServerOptions options, String...args) throws FormatException, IOException, DependencyException, ServiceException, URISyntaxException {
		super();
		
		long startTime = System.currentTimeMillis();

		this.options = options;
		
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
			if (path != null)
				filePath = path.toString();
//			filePath = Paths.get(uri).toString();
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		} finally {
			if (filePath == null) {
				logger.debug("Using URI as file path: {}", uri);
				filePath = uri.toString();
			}
		}

		// Create a reader & extract the metadata
		readerWrapper = manager.getPrimaryReaderWrapper(options, filePath, bfArgs);
		IFormatReader reader = readerWrapper.getReader();
		meta = (OMEPyramidStore)reader.getMetadataStore();

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
				String originalImageName = getImageName(s);
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
						} else if (requestedSeriesName.equals(name) || requestedSeriesName.equals(getImageName(s)) || requestedSeriesName.contentEquals(meta.getImageName(s))) {
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
				for (int c = 0; c < nChannels; c++) {
					ome.xml.model.primitives.Color color = null;
					String channelName = null;
					try {
						channelName = meta.getChannelName(series, c);
						color = meta.getChannelColor(series, c);
					} catch (Exception e) {
						logger.warn("Unable to parse color", e);
					}
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
					logger.debug("PLANE COUNT: " + meta.getPlaneCount(series));
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
					logger.warn("Error attempting to extract resolution " + i + " for " + getImageName(series), e);					
					break;
				}
			}
			
			// Generate a suitable name for this image
			String imageName = getFile().getName();
			String shortName = getImageName(seriesIndex);
			if (shortName == null || shortName.isBlank()) {
				if (imageMap.size() > 1)
					imageName = imageName + " - Series " + seriesIndex;
			} else if (!imageName.equals(shortName))
				imageName = imageName + " - " + shortName;

			this.args = args;
			
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
					levels(resolutionBuilder.build()).
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
	private String getImageName(int series) {
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
	
	/**
	 * Returns true if the reader accepts parallel tile requests, without synchronization.
	 * <p>
	 * This is true if parallelization is requested, and any memoization file is 'small enough'.
	 * The idea is that larger memoization files indicate more heavyweight readers, and these need 
	 * to be kept restricted to reduce the risk of memory errors.
	 * 
	 * @return
	 */
	public boolean willParallelize() {
		return options.requestParallelization() && (getWidth() > getPreferredTileWidth() || getHeight() > getPreferredTileHeight()) && manager.getMemoizationFileSize(filePath) <= MAX_PARALLELIZATION_MEMO_SIZE;
	}
	
	int getPreferredTileWidth() {
		return getMetadata().getPreferredTileWidth();
	}

	int getPreferredTileHeight() {
		return getMetadata().getPreferredTileHeight();
	}

	
	/**
	 * Get a IFormatReader for use by the current thread.
	 * <p>
	 * If willParallelize() returns false, then the global reader will be provided.
	 * 
	 * @return
	 */
	private IFormatReader getReader() {
		try {
			if (willParallelize())
				return manager.getReaderForThread(options, filePath, bfArgs);
			else
				return readerWrapper.getReader();
		} catch (Exception e) {
			logger.error("Error requesting image reader", e);
			return null;
		}
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
		int level = tileRequest.getLevel();

		int tileX = tileRequest.getTileX();
		int tileY = tileRequest.getTileY();
		int tileWidth = tileRequest.getTileWidth();
		int tileHeight = tileRequest.getTileHeight();
		int z = tileRequest.getZ();
		int t = tileRequest.getT();

		IFormatReader ipReader = getReader();
		if (ipReader == null) {
			throw new IOException("Reader is null - was the image already closed? " + filePath);
		}

		// Check if this is non-zero
		if (tileWidth <= 0 || tileHeight <= 0) {
			throw new IOException("Unable to request pixels for region with downsampled size " + tileWidth + " x " + tileHeight);
		}

		byte[][] bytes = null;
		int effectiveC;
		int sizeC = nChannels();
		int length = 0;
		ByteOrder order = ByteOrder.BIG_ENDIAN;
		boolean interleaved;
		int pixelType;
		boolean normalizeFloats = false;

		synchronized(ipReader) {
			ipReader.setSeries(series);
			ipReader.setResolution(level);
			order = ipReader.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
			interleaved = ipReader.isInterleaved();
			pixelType = ipReader.getPixelType();
			normalizeFloats = ipReader.isNormalized();

			// Single-channel & RGB images are straightforward... nothing more to do
			if ((ipReader.isRGB() && isRGB()) || nChannels() == 1) {
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
		} else {
			// Merge channels on different planes
			sampleModel = new BandedSampleModel(dataBuffer.getDataType(), tileWidth, tileHeight, sizeC);
		}

		WritableRaster raster = WritableRaster.createWritableRaster(sampleModel, dataBuffer, null);
		return new BufferedImage(colorModel, raster, false, null);
	}
	
	
	@Override
	public String getServerType() {
		return "Bio-Formats";
	}
	
	@Override
	public synchronized void close() throws Exception {
		super.close();
	}

	boolean containsSubImages() {
		return imageMap != null && !imageMap.isEmpty();
	}

	/**
	 * Get the MetadataStore, as used by Bio-Formats. This can be used to query metadata values not otherwise accessible.
	 * @return
	 */
	public OMEPyramidStore getMetadataStore() {
		return meta;
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
		IFormatReader reader = getReader();
		synchronized (reader) {
			int series = reader.getSeries();
			try {
				reader.setSeries(associatedImageMap.get(name));
				int nResolutions = reader.getResolutionCount();
				if (nResolutions > 0) {
					reader.setResolution(0);
				}
				// TODO: Handle color transforms here, or in the display of labels/macro images - in case this isn't RGB
				byte[] bytesSimple = reader.openBytes(reader.getIndex(0, 0, 0));
				return AWTImageTools.openImage(bytesSimple, reader, reader.getSizeX(), reader.getSizeY());
//				return AWTImageTools.autoscale(img);
			} catch (Exception e) {
				logger.error("Error reading associated image" + name, e);
			} finally {
				reader.setSeries(series);
			}
		}
		return null;
	}
	
	
	/**
	 * Get the underlying file.
	 * 
	 * @return
	 */
	public File getFile() {
		return filePath == null ? null : new File(filePath);
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
	 * Helper class to manage multiple Bio-Formats image readers.
	 * <p>
	 * This has two purposes:
	 * <ol>
	 *   <li>To construct IFormatReaders in a standardized way (e.g. with/without memoization).</li>
	 *   <li>To track the size of any memoization files for particular readers.</li>
	 *   <li>To allow BioFormatsImageServers to request separate Bio-Formats image readers for different threads.</li>
	 * </ol> 
	 * The memoization file size can be relevant because some readers are very memory-hungry, and may need to be created rarely.
	 * On the other side, some readers are very lightweight - and having multiple such readers active at a time can help rapidly 
	 * respond to tile requests.
	 * <p>
	 * It's up to any consumers to ensure that heavyweight readers aren't called for each thread. Additionally, each thread 
	 * will have only one reader active at any time. This should be explicitly closed by the calling thread if it knows 
	 * the reader will not be used again, but in practice this is often not the case and therefore a Cleaner is registered to 
	 * provide some additional support for when a thread is no longer reachable.
	 * <p>
	 * Note that this does mean one should be sparing when creating threads, and not keep them around too long.
	 */
	static class BioFormatsReaderManager {
		
		private static Cleaner cleaner = Cleaner.create();
		
		/**
		 * Map of reads for each calling thread.  Care should be taking by the calling code to ensure requests are only made for 'lightweight' readers to avoid memory problems.
		 */
		private static ThreadLocal<LocalReaderWrapper> localReader = new ThreadLocal<>();
		
		/**
		 * Map of memoization file sizes.
		 */
		private static Map<String, Long> memoizationSizeMap = new HashMap<>();
		
		/**
		 * Temporary directory for storing memoization files
		 */
		private static File dirMemoTemp = null;
		
		/**
		 * A set of primary readers, to avoid needing to regenerate these for all servers.
		 */
		private static Set<LocalReaderWrapper> primaryReaders = Collections.newSetFromMap(new WeakHashMap<>());
		
		/**
		 * Set of created temp memo files
		 */
		private static Set<File> tempMemoFiles = new HashSet<>();
		
		/**
		 * Request a IFormatReader for a specified path that is unique for the calling thread.
		 * <p>
		 * Note that the state of the reader is not specified; setSeries should be called before use.
		 * 
		 * @param options
		 * @param path
		 * @param args 
		 * @return
		 * @throws DependencyException
		 * @throws ServiceException
		 * @throws FormatException
		 * @throws IOException
		 */
		public synchronized IFormatReader getReaderForThread(final BioFormatsServerOptions options, final String path, BioFormatsArgs args) throws DependencyException, ServiceException, FormatException, IOException {
			
			LocalReaderWrapper wrapper = localReader.get();
			
			// Check if we already have the correct reader
			IFormatReader reader = wrapper == null ? null : wrapper.getReader();
			if (reader != null) {
				if (path.equals(reader.getCurrentFile()) && wrapper.argsMatch(args))
					return reader;		
				else
					reader.close(false);
			}
			
			// Create a new reader
			reader = createReader(options, path, null, args);
			
			// Store wrapped reference with associated cleaner
			wrapper = wrapReader(reader, args);
			localReader.set(wrapper);
			
			return reader;
		}
		
		
		private static LocalReaderWrapper wrapReader(IFormatReader reader, BioFormatsArgs args) {
			LocalReaderWrapper wrapper = new LocalReaderWrapper(reader, args);
			logger.debug("Constructing reader for {}", Thread.currentThread());
			cleaner.register(
					wrapper,
					new ReaderCleaner(Thread.currentThread().toString(), reader));
			return wrapper;
		}
		
		
		/**
		 * Request a IFormatReader for the specified path.
		 * <p>
		 * This reader will have OME metadata populated in an accessible form, but will *not* be unique for the calling thread.
		 * Therefore care needs to be taken with regard to synchronization.
		 * <p>
		 * Note that the state of the reader is not specified; setSeries should be called before use.
		 * 
		 * @param options
		 * @param path
		 * @param args
		 * @return
		 * @throws DependencyException
		 * @throws ServiceException
		 * @throws FormatException
		 * @throws IOException
		 */
		synchronized IFormatReader createPrimaryReader(final BioFormatsServerOptions options, final String path, IMetadata metadata, BioFormatsArgs args) throws DependencyException, ServiceException, FormatException, IOException {
			return createReader(options, path, metadata == null ? MetadataTools.createOMEXMLMetadata() : metadata, args);
		}
		
		
		/**
		 * Get a wrapper for the primary reader for a particular path. This can be reused across ImageServers, but 
		 * one must be careful to synchronize the actual use of the reader.
		 * @param options
		 * @param path
		 * @param args
		 * @return
		 * @throws DependencyException
		 * @throws ServiceException
		 * @throws FormatException
		 * @throws IOException
		 */
		synchronized LocalReaderWrapper getPrimaryReaderWrapper(final BioFormatsServerOptions options, final String path, BioFormatsArgs args) throws DependencyException, ServiceException, FormatException, IOException {
//			for (LocalReaderWrapper wrapper : primaryReaders) {
//				if (path.equals(wrapper.getReader().getCurrentFile()) && wrapper.argsMatch(args))
//					return wrapper;
//			}
			LocalReaderWrapper wrapper = wrapReader(createPrimaryReader(options, path, null, args), args);
			primaryReaders.add(wrapper);
			return wrapper;
		}
		
		
		/**
		 * Create a new IFormatReader, with memoization if necessary.
		 * 
		 * @param options 
		 * @param id File path for the image
		 * @param store optional MetadataStore; this will be set in the reader if needed
		 * @param args
		 * @return the IFormatReader
		 * @throws FormatException
		 * @throws IOException
		 */
		static IFormatReader createReader(final BioFormatsServerOptions options, final String id, final MetadataStore store, BioFormatsArgs args) throws FormatException, IOException {
			return createReader(options, null, id, store, args);
		}
		
		/**
		 * Request the file size of any known memoization file for a specific ID.
		 * 
		 * @param id
		 * @return
		 */
		public long getMemoizationFileSize(String id) {
			return memoizationSizeMap.getOrDefault(id, Long.valueOf(0L));
		}
		
		/**
		 * Create a new {@code IFormatReader}, with memoization if necessary.
		 * 
		 * @param options 	options used to control the reader generation
		 * @param cls 		optionally specify a IFormatReader class if it is already known, to avoid a search.
		 * @param id 		file path for the image.
		 * @param store 	optional MetadataStore; this will be set in the reader if needed.
		 * @param args      optional args to customize reading
		 * @return the {@code IFormatReader}
		 * @throws FormatException
		 * @throws IOException
		 */
		@SuppressWarnings("resource")
		private static synchronized IFormatReader createReader(final BioFormatsServerOptions options, final Class<? extends IFormatReader> cls, 
				final String id, final MetadataStore store, BioFormatsArgs args) throws FormatException, IOException {
			IFormatReader imageReader;
			if (cls != null) {
				ClassList<IFormatReader> list = new ClassList<>(IFormatReader.class);
				list.addClass(cls);
				imageReader = new ImageReader(list);
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
			// We can only use memoization if we don't have an illegal character
			if (memoizationTimeMillis >= 0 && !id.contains(":")) {
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
					if (dirMemoTemp == null) {
						Path path = Files.createTempDirectory("qupath-memo-");
						dirMemoTemp = path.toFile();
						Runtime.getRuntime().addShutdownHook(new Thread() {
							@Override
							public void run() {
								deleteTempMemoFiles();
							}
						});
//						path.toFile().deleteOnExit(); // Won't work recursively!
						logger.warn("Temp memoization directory created at {}", dirMemoTemp);
						logger.warn("If you want to avoid this warning, either disable Bio-Formats memoization in the preferences or specify a directory to use");
					}
					dir = dirMemoTemp;
				}
				if (dir != null) {
					memoizer = new Memoizer(imageReader, memoizationTimeMillis, dir);
					imageReader = memoizer;
				}
			}
			
			
			if (store != null) {
				imageReader.setMetadataStore(store);
			}
			else
				imageReader.setMetadataStore(new DummyMetadata());
			
			var swapDimensions = args.getSwapDimensions();
			if (swapDimensions != null)
				logger.debug("Creating DimensionSwapper for {}", swapDimensions);
			
			
			if (id != null) {
				if (memoizer != null) {
					File fileMemo = ((Memoizer)imageReader).getMemoFile(id);
					// If we're using a temporary directory, delete the memo file
					if (dir == dirMemoTemp)
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

			return imageReader;
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
		 * @return
		 */
		private static boolean deleteEmptyDirectories(File dir) {
			if (!dir.isDirectory())
				return false;
			int nFiles = 0;
			for (File f : dir.listFiles()) {
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
		 * Simple wrapper for a reader to help with cleanup.
		 */
		static class LocalReaderWrapper {
			
			private IFormatReader reader;
			private BioFormatsArgs args;
			
			LocalReaderWrapper(IFormatReader reader, BioFormatsArgs args) {
				this.reader = reader;
				this.args = args;
			}
			
			public IFormatReader getReader() {
				return reader;
			}

			public boolean argsMatch(BioFormatsArgs args) {
				return Objects.equals(this.args, args);
			}

		}
		
		/**
		 * Helper class that helps ensure readers are closed when a thread is no longer reachable.
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
				args.add("--swap");
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
