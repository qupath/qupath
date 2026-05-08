/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

import java.util.OptionalDouble;
import java.util.function.Predicate;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.in.ZarrReader;
import loci.formats.ome.OMEPyramidStore;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.meta.MetadataRetrieve;
import ome.xml.meta.OMEXMLMetadataRoot;
import ome.xml.model.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerBuilder.DefaultImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerMetadata.ImageResolutionLevel;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

/**
 * QuPath ImageServer that uses the Bio-Formats library to read image data.
 * <p>
 * See <a href="https://www.openmicroscopy.org/bio-formats/">Bio-Formats homepage</a>.
 * <p>
 * See also <a href="https://bio-formats.readthedocs.io/en/stable/developers/matlab-dev.html#improving-reading-performance">Improving reading performance.</a>
 *
 * @author Pete Bankhead
 *
 */
public class BioFormatsImageServer extends AbstractTileableImageServer implements PathObjectReader {
	
	private static final Logger logger = LoggerFactory.getLogger(BioFormatsImageServer.class);

	/**
	 * Minimum tile size - smaller values will be ignored.
	 */
	private static final int MIN_TILE_SIZE = 32;

	/**
	 * Default tile size - when no other value is available.
	 */
	private static final int DEFAULT_TILE_SIZE = 512;

	/**
	 * The original URI requested for this server.
	 */
	private final URI uri;

	/**
	 * Arguments passed to constructor.
	 */
	private final String[] args;

	/**
	 * Original metadata, populated when reading the file.
	 */
	private final ImageServerMetadata originalMetadata;

	/**
	 * File path if possible, or a URI otherwise.
	 */
	private final String filePathOrUrl;
	
	/**
	 * A map linking an identifier (image name) to series number for 'full' images.
	 */
	private final Map<String, ServerBuilder<BufferedImage>> imageMap = new LinkedHashMap<>();
	
	/**
	 * A map linking an identifier (image name) to series for additional images, e.g. thumbnails or macro images.
	 */
	private final Map<String, Series> associatedImageMap = new LinkedHashMap<>();
	
	/**
	 * Numeric identifier for the image (there might be more than one in the file)
	 */
	private final int series;

	/**
	 * ColorModel to use with all BufferedImage requests.
	 */
	private final ColorModel colorModel;
	
	/**
	 * Pool of readers for use with this server.
	 */
	private final ReaderPool readerPool;
	
	/**
	 * Create a minimal BioFormatsImageServer that can be used to query server builders.
	 * This is checked to ensure it can return at least one pixel, to avoid returning a server that will fail later.
	 * @param uri the URI of the image to open
	 * @param options standard Bio-Formats options
	 * @param args optional arguments
	 * @return a server containing an image that can return pixels
	 * @throws IOException if the image cannot be read
	 */
	static BioFormatsImageServer checkSupport(URI uri, final BioFormatsServerOptions options, String...args) throws IOException {
		try {
			var server = new BioFormatsImageServer(uri, options, args);
			// Attempt to read one pixel from the image.
			// This is expected to throw an exception if it fails; without this check, Bio-Formats can appear to work
			// but then fail when trying to read the first tile.
			// The risk is that this will be slow for large, non-tiled images.
			if (!server.readerPool.checkCanRead())
				throw new IOException("Unable to read bytes from " + uri);
			return server;
		} catch (Throwable t) {
			throw ReaderUtils.convertToIOException(t);
		}
	}

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


    private BioFormatsImageServer(URI uri, final BioFormatsServerOptions options, String...args) throws FormatException, IOException, DependencyException, ServiceException, URISyntaxException {
		super();

		long startTime = System.currentTimeMillis();

		this.uri = cleanUri(uri);
		
		// Parse the arguments
		this.args = args.clone();
		var bfArgs = BioFormatsArgs.parse(args);

		// Try to get a local file path, but accept something else (since Bio-Formats handles other URIs)
		this.filePathOrUrl = convertToFilePathOrUrl(uri);

		// Create a reader & extract the metadata
		this.readerPool = new ReaderPool(options, filePathOrUrl, bfArgs);
		IFormatReader reader = readerPool.getMainReader();
		var meta = (OMEPyramidStore)reader.getMetadataStore();

		// If we have more than one series, we need to construct maps of 'analyzable' & associated images
		synchronized (reader) {
			List<Series> allSeries = Series.parseFromReader(reader);
			if (allSeries.isEmpty())
				throw new IOException("No series found for " + filePathOrUrl);

			// TODO: Consider making below method static.
			//       Could use Map<String, Series>, but delayed creation of ServerBuilder
			//       may fail if the URI is no longer 'clean', i.e., not encoding the series
			populateImageMaps(allSeries, bfArgs, uri);

			this.series = findSeriesToOpen(allSeries, bfArgs);
			this.originalMetadata = buildOriginalMetadata(
					reader,
					series,
					getImageName(meta, getFile().getName(), series, imageMap.size() > 1),
					getPath());
		}

		colorModel = createColorModel(originalMetadata);
		ensureImageIoCacheOff();
		long endTime = System.currentTimeMillis();
		logger.debug("Initialization time: {} ms", endTime - startTime);
	}


	private static int findSeriesToOpen(final List<Series> allSeries, final BioFormatsArgs bfArgs) throws IOException {
		// Use the series index from the args whenever possible
		int seriesIndex = bfArgs.series;
		if (seriesIndex >= 0 && allSeries.stream().anyMatch(s -> seriesIndex == s.getSeries())) {
			return seriesIndex;
		}

		// Use the series name from the args, if present
		String requestedSeriesName = bfArgs.seriesName.isBlank() ? null : bfArgs.seriesName;
		if (requestedSeriesName != null) {
			var matchingSeries = findMatchingSeries(requestedSeriesName, allSeries);
			if (matchingSeries.isPresent()) {
				return matchingSeries.get().getSeries();
			} else {
				logger.warn("No series found with name {}", requestedSeriesName);
			}
		}

		// Get the non-associated images
		var imageSeries = allSeries.stream().filter(Predicate.not(Series::isAssociatedImage)).toList();
		if (!imageSeries.isEmpty()) {
			var firstSeries = imageSeries.getFirst();
			if (imageSeries.size() == 1)
				return firstSeries.getSeries();
			// Take the first series unless it's substantially smaller than the others.
			// If it's a lot smaller, it's probably a thumbnail.
			// But if it's a *bit* smaller, we might simply have images of different sizes -
			// and the user will most likely expect we pick the first image than one somewhere else in the list.
			var maxPixels = imageSeries.stream().mapToLong(Series::totalPixelsXYZT).max().orElse(0L);
			if (firstSeries.totalPixelsXYZT() * 4 > maxPixels)
				return firstSeries.getSeries();
			else
				return findLargestSeries(imageSeries).map(Series::getSeries).orElseThrow(IOException::new);
		}
		throw new IOException("No suitable series found");
	}


	private void populateImageMaps(List<Series> allSeries,
										  BioFormatsArgs bfArgs,
										  URI uri) {
		for (var series : allSeries) {
			if (series.isAssociatedImage()) {
				associatedImageMap.put(series.getUniqueSeriesName(), series);
			} else {
				imageMap.put(series.getUniqueSeriesName(),
						DefaultImageServerBuilder.createInstance(
								BioFormatsServerBuilder.class, null, uri,
								bfArgs.backToArgs(series.getSeries())
						));
			}
		}
	}


	private static Optional<Series> findLargestSeries(Collection<Series> allSeries) {
		long nPixels = -1;
		Series maxSeries = null;
		for (var series : allSeries) {
			long n = series.getResolutions().getFirst().totalPixelsXYZT();
			if (n > nPixels) {
				maxSeries = series;
				nPixels = n;
			}
		}
		return Optional.ofNullable(maxSeries);
	}

	private static Optional<Series> findMatchingSeries(String requestedSeriesName, Collection<Series> allSeries) {
		return allSeries.stream()
				.filter(series -> isRequestedSeries(requestedSeriesName, series))
				.findFirst();
	}

	private static boolean isRequestedSeries(String requestedSeriesName, Series series) {
		return requestedSeriesName.equals(series.getUniqueSeriesName()) ||
				requestedSeriesName.equals(series.getCleanSeriesName()) ||
				requestedSeriesName.contentEquals(series.getOriginalSeriesName());
	}


	/**
	 * Ensure the URI is valid for use with Bio-Formats, or throw an exception if invalid elements
	 * (e.g., query, fragment) are found.
	 * @param uri the input URI
	 * @return the clean URI, or original URI if no cleaning is required
	 * @throws IOException
	 * @throws URISyntaxException
	 */
	private static URI cleanUri(URI uri) throws IOException, URISyntaxException {
		// Zarr images can be opened by selecting the .zattrs or .zgroup file
		// In that case, the parent directory contains the whole image
		if (uri.toString().endsWith(".zattrs") || uri.toString().endsWith(".zgroup")) {
			return new File(uri).getParentFile().toURI();
		}

		// See if there is a series name embedded in the path (temporarily the way things were done in v0.2.0-m1 and v0.2.0-m2)
		// Add it to the args if so
		if (uri.getFragment() != null) {
			throw new IOException("Series as fragment no longer supported");
		} else if (uri.getQuery() != null) {
			// Queries supported name=image-name or series=series-number... only one or the other!
			String query = uri.getQuery();
			String seriesQuery = "series=";
			String nameQuery = "name=";
			if (query.startsWith(seriesQuery)) {
				throw new IOException("Series as query no longer supported");
			} else if (query.startsWith(nameQuery)) {
				throw new IOException("Series name as query no longer supported");
			}
		}
		return new URI(uri.getScheme(), uri.getHost(), uri.getPath(), null);
	}


	private static ImageServerMetadata buildOriginalMetadata(IFormatReader reader, int series, String imageName, String path) {
		reader.setSeries(series);
		MetadataRetrieve meta = (MetadataRetrieve)reader.getMetadataStore();

		// Get the format in case we need it
		logger.debug("Reading format: {}", reader.getFormat());

		// Get the dimensions for the requested series
		// The first resolution is the highest, i.e. the largest image
		int width = reader.getSizeX();
		int height = reader.getSizeY();

		int nChannels = reader.getSizeC();

		int nZSlices = reader.getSizeZ();
		int nTimepoints = reader.getSizeT();

		PixelType pixelType = ReaderUtils.formatToPixelType(reader.getPixelType());
		if (Set.of(PixelType.INT8, PixelType.UINT32).contains(pixelType)) {
			logger.warn("Pixel type {} is not currently supported", pixelType);
		}

		boolean isRGB = reader.isRGB() && pixelType == PixelType.UINT8;
		// Remove alpha channel
		if (isRGB && nChannels == 4) {
			logger.warn("Removing alpha channel");
			nChannels = 3;
		} else if (nChannels != 3)
			isRGB = false;

		// Try to read the default display colors for each channel from the file
		List<ImageChannel> channels;
		if (isRGB) {
			channels = List.copyOf(ImageChannel.getDefaultRGBChannels());
		} else {
			channels = parseChannels(meta, series, nChannels);
			// Update RGB status if needed - sometimes we might really have an RGB image, but the Bio-Formats flag
			// doesn't show this - and we want to take advantage of the packed int optimizations where we can
			isRGB = nChannels == 3 &&
					pixelType == PixelType.UINT8 &&
					channels.equals(ImageChannel.getDefaultRGBChannels());
		}

		var resolutions = buildResolutions(reader, width, height);
		int[] tileSizes = getTileWidthAndHeight(reader);

		// Set metadata
		ImageServerMetadata.Builder builder = new ImageServerMetadata.Builder(
				BioFormatsImageServer.class, path, width, height).
				name(imageName).
				channels(channels).
				sizeZ(nZSlices).
				sizeT(nTimepoints).
				levels(resolutions).
				pixelType(pixelType).
				rgb(isRGB).
				preferredTileSize(tileSizes[0], tileSizes[1]);

		// Determine min/max values if we can
		int bpp = reader.getBitsPerPixel();
		if (bpp < pixelType.getBitsPerPixel()) {
			if (pixelType.isSignedInteger()) {
				builder.minValue(Math.pow(2, bpp-1));
				builder.maxValue(Math.pow(2, bpp-1) - 1);
			} else if (pixelType.isUnsignedInteger()) {
				builder.minValue(0);
				builder.maxValue(Math.pow(2, bpp) - 1);
			}
		}

		try {
			builder.pixelCalibration(parsePixelCalibration(meta, series, nZSlices, nTimepoints));
		} catch (Exception e) {
			logger.error("Error parsing pixel calibration", e);
		}

		double magnification = tryToGetMagnification(meta, series).orElse(Double.NaN);
		if (Double.isFinite(magnification))
			builder = builder.magnification(magnification);

		return builder.build();
	}


	/**
	 * Bio-Formats can use ImageIO for JPEG decoding, and permitting the disk-based cache can slow it down (a lot).
	 * Here we turn it off; unfortunately, it can only be done globally.
	 */
	private static void ensureImageIoCacheOff() {
		if (ImageIO.getUseCache()) {
			logger.debug("Setting ImageIO.setUseCache(false)");
			ImageIO.setUseCache(false);
		}
	}


	private static ColorModel createColorModel(final ImageServerMetadata metadata) {
		if (metadata.isRGB())
			return ColorModel.getRGBdefault();
		else
			return ColorModelFactory.createColorModel(metadata.getPixelType(), metadata.getChannels());
	}


	private static int[] getTileWidthAndHeight(IFormatReader reader) {
		// When opening Zarr images, reader.getOptimalTileWidth/Height() returns by default
		// the chunk width/height of the lowest resolution image. See
		// https://github.com/qupath/qupath/pull/1645#issue-2533834067 for why it may be a problem.
		// A workaround to get the chunk size of the full resolution image is to set the resolution
		// to 0 with the Zarr reader
		if (reader instanceof ZarrReader zarrReader) {
			zarrReader.setResolution(0, true);
		} else {
			reader.setResolution(0);
		}

		// Dimensions
		int width = reader.getSizeX();
		int height = reader.getSizeY();
		int nChannels = reader.getSizeC();
		int bpp = reader.getBitsPerPixel() / 8;

		// Make sure tile sizes are within range
		int tileWidth = reader.getOptimalTileWidth();
		int tileHeight = reader.getOptimalTileHeight();
		if (tileWidth != width)
			tileWidth = getDefaultTileLength(tileWidth, width);
		if (tileHeight != height)
			tileHeight = getDefaultTileLength(tileHeight, height);

		// Ensure the tile sizes aren't too large
		if ((long)tileWidth * (long)tileHeight * (long)nChannels * bpp >= Integer.MAX_VALUE) {
			return new int[] {Math.min(DEFAULT_TILE_SIZE, width), Math.min(DEFAULT_TILE_SIZE, height)};
		} else {
			return new int[] {tileWidth, tileHeight};
		}
	}


	private static PixelCalibration parsePixelCalibration(MetadataRetrieve meta, int series, int nZSlices, int nTimepoints) {

		var builder = new PixelCalibration.Builder();

		Length xSize = meta.getPixelsPhysicalSizeX(series);
		Length ySize = meta.getPixelsPhysicalSizeY(series);
		if (xSize != null && ySize != null) {
			builder.pixelSizeMicrons(
					xSize.value(UNITS.MICROMETER),
					ySize.value(UNITS.MICROMETER)
			);
		}
		// If we have multiple z-slices, parse the spacing
		if (nZSlices > 1) {
			Length zSize = meta.getPixelsPhysicalSizeZ(series);
			if (zSize != null) {
				builder.zSpacingMicrons(
						zSize.value(UNITS.MICROMETER)
				);
			}
		}
		// TODO: Check the Bioformats TimeStamps
		if (nTimepoints > 1) {
			logger.warn("Time stamps read from Bio-Formats have not been fully verified & should not be relied upon (values updated in v0.6.0)");
			var timeIncrement = meta.getPixelsTimeIncrement(series);
			if (timeIncrement != null) {
				double[] timepoints = new double[nTimepoints];
				double timeIncrementSeconds = timeIncrement.value(UNITS.SECOND).doubleValue();
				for (int t = 0; t < nTimepoints; t++) {
					timepoints[t] = t * timeIncrementSeconds;
				}
				builder.timepoints(TimeUnit.SECONDS, timepoints);
			}
		}

		return builder.build();
	}

	private static List<ImageChannel> parseChannels(final MetadataRetrieve meta, final int series, final int nChannels) {
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
				logger.debug("Attempting to parse {} channels with metadata channel count {}", nChannels, metaChannelCount);
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
			logger.warn("Exception parsing channels {}", e.getMessage(), e);
		}
		if (nChannels != tempNames.size() || tempNames.size() != tempColors.size()) {
			logger.warn("The channel names and colors read from the metadata don't match the expected number of channels!");
			logger.warn("Be very cautious working with channels, since the names and colors may be misaligned, incorrect or default values.");
			long nNames = tempNames.stream().filter(n -> n != null && !n.isBlank()).count();
			long nColors = tempColors.stream().filter(Objects::nonNull).count();
			logger.warn("(I expected {} channels, but found {} names and {} colors)", nChannels, nNames, nColors);
		}

		// Now loop through whatever we could parse and add QuPath ImageChannel objects
		List<ImageChannel> channels = new ArrayList<>();
		for (int c = 0; c < nChannels; c++) {
			String channelName = c < tempNames.size() ? tempNames.get(c) : null;
			var color = c < tempColors.size() ? tempColors.get(c) : null;
			Integer channelColor;
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
		return channels;
	}


	private static OptionalDouble tryToGetMagnification(MetadataRetrieve meta, int series) {
		try {
			String objectiveID = meta.getObjectiveSettingsID(series);
			int objectiveIndex = -1;
			int instrumentIndex = -1;
			int nInstruments = meta.getInstrumentCount();
			for (int i = 0; i < nInstruments; i++) {
				for (int o = 0; 0 < meta.getObjectiveCount(i); o++) {
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
					return OptionalDouble.of(magnificationObject);
			}
		} catch (Exception e) {
			logger.debug("Unable to parse magnification: {}", e.getLocalizedMessage());
		}
		return OptionalDouble.empty();
	}


	private static String getImageName(MetadataRetrieve meta, String baseName, int seriesIndex, boolean multiImage) {
		String shortName = ReaderUtils.getImageName(meta, seriesIndex).orElse("");
		if (shortName.isBlank()) {
			if (multiImage) {
				return baseName + " - Series " + seriesIndex;
			}
		} else if (!baseName.equals(shortName)) {
			return baseName + " - " + shortName;
		}
		return baseName;
	}


	private static List<ImageResolutionLevel> buildResolutions(IFormatReader reader, int width, int height) {
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
				if ("CellSens VSI".equals(reader.getFormat())) {
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
				logger.warn("Error attempting to extract resolution {}", i, e);
				break;
			}
		}
		return resolutionBuilder.build();
	}

	
	/**
	 * Get a sensible default tile size for a specified dimension.
	 * @param tileLength tile width or height
	 * @param imageLength corresponding image width or height
	 * @return a sensible tile length, bounded by the image width or height
	 */
	private static int getDefaultTileLength(int tileLength, int imageLength) {
		if (tileLength <= 0) {
			tileLength = DEFAULT_TILE_SIZE;
		} else if (tileLength < MIN_TILE_SIZE) {
			tileLength = (int)Math.ceil((double)MIN_TILE_SIZE / tileLength) * tileLength;
		}
		return Math.min(tileLength, imageLength);
	}

	/**
	 * Prefer a file path where possible, but Bio-Formats can support some URLs as a Location.
	 * @param uri the uri of the image to open
	 * @return a string representing the file path (if available), or URI otherwise
	 */
	private static String convertToFilePathOrUrl(URI uri) {
		try {
			var path = GeneralTools.toPath(uri);
			if (path != null) {
				// Use toRealPath to resolve any symbolic links
				return path.toRealPath().toString();
			}
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		logger.debug("Using URI as file path: {}", uri);
		return uri.toString();
	}

	/**
	 * Get the format String, as returned by Bio-Formats {@code IFormatReader.getFormat()}.
	 * @return
	 */
	public String getFormat() {
		return readerPool.getFormat();
	}
	
	@Override
	public Collection<URI> getURIs() {
		return Collections.singletonList(uri);
	}
	
	@Override
	public String createID() {
		return ServerTools.createDefaultID(getClass(), uri, args);
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		var currentMetadata = getMetadata();
		super.setMetadata(metadata);
		if (currentMetadata != metadata && !currentMetadata.getLevels().equals(metadata.getLevels())) {
			logger.warn("Can't set metadata to use incompatible pyramid levels - reverting to original pyramid levels");
			super.setMetadata(
					new ImageServerMetadata.Builder(metadata)
					.levels(currentMetadata.getLevels())
					.build()
			);
		}
	}
	
	/**
	 * Returns a builder capable of creating a server like this one.
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return DefaultImageServerBuilder.createInstance(BioFormatsServerBuilder.class, getMetadata(), uri, args);
	}
	
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
		return !imageMap.isEmpty();
	}

	/**
	 * Get the MetadataStore, as used by Bio-Formats. This can be used to query metadata values not otherwise accessible.
	 * @return
	 */
	public OMEPyramidStore getMetadataStore() {
		return readerPool.getMetadata();
	}
	
	/**
	 * Retrieve a string representation of the metadata OME-XML.
	 * @return
	 */
	public String dumpMetadata() {
		try {
			return getMetadataStore().dumpXML();
		} catch (Exception e) {
			logger.error("Unable to dump metadata", e);
		}
		return null;
	}

	
	@Override
	public List<String> getAssociatedImageList() {
		if (associatedImageMap.isEmpty())
			return Collections.emptyList();
		return List.copyOf(associatedImageMap.keySet());
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		var series = associatedImageMap.getOrDefault(name, null);
		if (series == null)
			throw new IllegalArgumentException("No associated image with name '" + name + "' for " + getPath());
		
		try {
			return readerPool.openSeries(series.getSeries());
		} catch (Exception e) {
            logger.error("Error reading associated image {}: {}", name, e.getMessage(), e);
			return null;
		}
	}
	
	
	/**
	 * Get the underlying file.
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

	@Override
	public Collection<PathObject> readPathObjects() {
		// TODO: Avoid reader access outside the reader pool
		IFormatReader reader = readerPool.getMainReader();
		if (reader == null) {
			logger.debug("Cannot get reader. Returning no path objects");
			return List.of();
		}
		reader.setSeries(series);

		if (!(reader.getMetadataStore().getRoot() instanceof OMEXMLMetadataRoot metadata)) {
			logger.debug("Metadata store of reader {} not instance of OMEXMLMetadataRoot. Returning no path objects", reader.getMetadataStore());
			return List.of();
		}

		return IntStream.range(0, metadata.sizeOfROIList())
				.mapToObj(metadata::getROI)
				.map(bioFormatsRoi -> {
					logger.debug("Converting {} to QuPath path object", bioFormatsRoi);

					List<Shape> shapes = IntStream.range(0, bioFormatsRoi.getUnion().sizeOfShapeList())
							.mapToObj(i -> bioFormatsRoi.getUnion().getShape(i))
							.filter(Objects::nonNull)
							.toList();

					List<ROI> rois = shapes.stream()
							.map(BioFormatsShapeConverter::convertShapeToRoi)
							.flatMap(Optional::stream)
							.toList();

					ROI roi;
					if (rois.stream().allMatch(ROI::isPoint)) {
						logger.debug("Got point ROIs {} from {}. Combining them", rois, bioFormatsRoi);
						roi = ROIs.createPointsROI(
								rois.stream()
										.map(ROI::getAllPoints)
										.flatMap(List::stream)
										.distinct()
										.toList(),
								rois.isEmpty() ? ImagePlane.getDefaultPlane() : rois.getFirst().getImagePlane()
						);
					} else {
						logger.debug("Got non point ROIs {} from {}. Creating union from it", rois, bioFormatsRoi);
						roi = RoiTools.union(rois);
					}

					PathObject pathObject = PathObjects.createAnnotationObject(roi);

					try {
						pathObject.setID(UUID.fromString(bioFormatsRoi.getID()));
						logger.debug("ID {} of {} set to {}", bioFormatsRoi.getID(), bioFormatsRoi, pathObject);
					} catch (IllegalArgumentException | NullPointerException e) {
						logger.debug("ID {} of {} is not a valid UUID", bioFormatsRoi.getID(), bioFormatsRoi, e);
					}

					Optional<Boolean> locked = shapes.stream()
							.map(Shape::getLocked)
							.filter(Objects::nonNull)
							.findAny();
					if (locked.isPresent()) {
						pathObject.setLocked(locked.get());
						logger.debug("Locked status {} of {} set to {}", locked.get(), bioFormatsRoi, pathObject);
					} else {
						logger.debug("Locked status not found in {}", bioFormatsRoi);
					}

					pathObject.setName(bioFormatsRoi.getName());

					logger.debug("PathObject {} created from {}", pathObject, bioFormatsRoi);

					return pathObject;
				})
				.toList();
	}


}
