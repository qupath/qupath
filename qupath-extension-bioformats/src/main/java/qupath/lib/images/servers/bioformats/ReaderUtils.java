package qupath.lib.images.servers.bioformats;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import loci.formats.FormatTools;
import loci.formats.IFormatReader;
import loci.formats.in.ZarrReader;
import ome.units.UNITS;
import ome.units.quantity.Length;
import ome.xml.meta.MetadataRetrieve;
import ome.xml.model.primitives.Color;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.images.servers.PixelType;

class ReaderUtils {

    private static final Logger logger = LoggerFactory.getLogger(ReaderUtils.class);

    /**
     * Minimum tile size - smaller values will be ignored.
     */
    private static final int MIN_TILE_SIZE = 32;

    /**
     * Default tile size - when no other value is available.
     */
    private static final int DEFAULT_TILE_SIZE = 512;

    /**
     * Convert an OME format (data type) to a {@link PixelType}.
     * @param format the OME format
     * @return the equivalent {@link PixelType}
     * @throws IllegalArgumentException if the format is not supported
     */
    static PixelType formatToPixelType(int format) throws IllegalArgumentException {
        return switch (format) {
            case FormatTools.UINT8 -> PixelType.UINT8;
            case FormatTools.INT8 -> PixelType.INT8;
            case FormatTools.UINT16 -> PixelType.UINT16;
            case FormatTools.INT16 -> PixelType.INT16;
            case FormatTools.UINT32 -> PixelType.UINT32;
            case FormatTools.INT32 -> PixelType.INT32;
            case FormatTools.FLOAT -> PixelType.FLOAT32;
            case FormatTools.DOUBLE -> PixelType.FLOAT64;
            default -> throw new IllegalArgumentException("Unsupported pixel type: " + format);
        };
    }

    /**
     * Ensure a throwable is an IOException.
     * This gives the opportunity to include more human-readable messages for common errors.
     * @param throwable any throwable
     * @return an IOException, which may be the same as the input or may wrap the input
     */
    static IOException convertToIOException(Throwable throwable) {
        if (GeneralTools.isMac()) {
            String message = throwable.getMessage();
            if (message != null) {
                if (message.contains("ome.jxrlib.JXRJNI")) {
                    return new IOException("Bio-Formats does not support JPEG-XR on Apple Silicon: " + throwable.getMessage(), throwable);
                }
                if (message.contains("org.libjpegturbo.turbojpeg.TJDecompressor")) {
                    return new IOException("Bio-Formats does not currently support libjpeg-turbo on Apple Silicon", throwable);
                }
            }
        }
        if (throwable instanceof IOException io)
            return io;
        return new IOException(throwable);
    }

    /**
     * Get the image name for a series, making sure to remove any trailing null terminators.
     * <p>
     * See https://github.com/qupath/qupath/issues/573
     * @param series the series (image) whose name should be requested
     * @return an optional containing the name, if available
     */
    static Optional<String> getImageName(MetadataRetrieve meta, int series) {
         String name = meta.getImageName(series);
         while (name != null && name.endsWith("\0"))
             name = name.substring(0, name.length()-1);
         return Optional.ofNullable(name);
    }

    static ImageServerMetadata buildOriginalMetadata(IFormatReader reader, Series series, String imageName, String path) {
        reader.setSeries(series.getSeries());
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
            channels = parseChannels(meta, series.getSeries(), nChannels);
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
            builder.pixelCalibration(parsePixelCalibration(meta, series.getSeries(), nZSlices, nTimepoints));
        } catch (Exception e) {
            logger.error("Error parsing pixel calibration", e);
        }

        double magnification = tryToGetMagnification(meta, series.getSeries()).orElse(Double.NaN);
        if (Double.isFinite(magnification))
            builder = builder.magnification(magnification);

        return builder.build();
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
        var tempColors = new ArrayList<Color>(nChannels);
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

    private static List<ImageServerMetadata.ImageResolutionLevel> buildResolutions(IFormatReader reader, int width, int height) {
        // Loop through the series & determine downsamples
        int nResolutions = reader.getResolutionCount();
        var resolutionBuilder = new ImageServerMetadata.ImageResolutionLevel.Builder(width, height)
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

}
