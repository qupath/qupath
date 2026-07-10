package qupath.imagej.images.servers;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.measure.Calibration;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferUShort;
import java.awt.image.Raster;
import java.awt.image.SampleModel;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.imagej.tools.IJTools;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;

class ImageJServerUtils {

    private static final Logger logger = LoggerFactory.getLogger(ImageJServerUtils.class);

    static ImagePlus openImage(URI uri) {
        var filePath = GeneralTools.toPath(uri);
        var file = filePath != null ? filePath.toFile() : null;
        String path = file == null ? uri.toString() : file.getAbsolutePath();

        // Open as a virtual stack if we have 1) a TIFF, with 2) multiple slices and 3) a large file size -
        // otherwise try to open directly (which is much faster if memory permits)
        var runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long availableMemory = maxMemory - (runtime.totalMemory() - runtime.freeMemory());
        if (file != null && path.toLowerCase().endsWith(".tif") || path.toLowerCase().endsWith(".tiff")) {
            // Because ImageJ only supports uncompressed TIFFs, we simply use the file size
            // There is some questionable logic here to try to open virtual stacks at appropriate times
            long fileLength = file == null ? Long.MAX_VALUE : file.length();
            long maxFileLength = Math.max(1024*1024*10, Math.min(1024*1024*400, availableMemory / 8));
            if (fileLength > maxFileLength) {
                var info = Opener.getTiffFileInfo(path);
                if (info != null && (info.length > 1 || (info.length == 1 && info[0].nImages > 1))) {
                    logger.debug("Opening {} as virtual stack", uri);
                    return IJ.openVirtual(path);
                }
            }
        }
        logger.debug("Opening {} as ImagePlus", uri);
        var imp = IJ.openImage(path);
        // Log a warning if the image is very large
        double sizeBytes = imp.getSizeInBytes();
        if (!imp.getStack().isVirtual() && sizeBytes > maxMemory / 16.0) {
            logger.warn("The image is very large relative to the available memory ({} MB / {} MB, {} %)",
                    GeneralTools.formatNumber(sizeBytes / (1024.0 * 1024.0), 1),
                    GeneralTools.formatNumber(maxMemory / (1024.0 * 1024.0), 1),
                    GeneralTools.formatNumber(sizeBytes / maxMemory * 100.0, 1));
            logger.warn("Consider saving the image in a pyramidal format, e.g. using 'QuPath convert-ome' from the command line to create a pyramidal OME-TIFF.");
        }
        return imp;
    }

    static ImageServerMetadata parseMetadata(ImagePlus imp) {
        Calibration cal = imp.getCalibration();
        double xMicrons = IJTools.tryToParseMicrons(cal.pixelWidth, cal.getXUnit());
        double yMicrons = IJTools.tryToParseMicrons(cal.pixelHeight, cal.getYUnit());
        double zMicrons = IJTools.tryToParseMicrons(cal.pixelDepth, cal.getZUnit());
        TimeUnit timeUnit = parseTimeUnit(cal.getTimeUnit());
        double[] timepoints = null;
        if (timeUnit != null) {
            timepoints = new double[imp.getNFrames()];
            for (int i = 0; i < timepoints.length; i++) {
                timepoints[i] = i * cal.frameInterval;
            }
        }

        PixelType pixelType;
        boolean isRGB = false;
        switch (imp.getType()) {
            case (ImagePlus.COLOR_RGB):
                isRGB = true;
            case (ImagePlus.COLOR_256):
            case (ImagePlus.GRAY8):
                pixelType = PixelType.UINT8;
                break;
            case (ImagePlus.GRAY16):
                pixelType = PixelType.UINT16;
                break;
            case (ImagePlus.GRAY32):
                pixelType = PixelType.FLOAT32;
                break;
            default:
                throw new IllegalArgumentException("Unknown ImagePlus type " + imp.getType());
        }

        List<ImageChannel> channels;
        if (isRGB)
            channels = ImageChannel.getDefaultRGBChannels();
        else {
            String[] sliceLabels = tryToParseChannelNames(imp);

            // Get default channels
            channels = new ArrayList<>(ImageChannel.getDefaultChannelList(imp.getNChannels()));

            // Try to update the channel names and/or colors from ImageJ if we can
            if (sliceLabels != null || imp instanceof CompositeImage) {
                for (int channel = 0; channel < imp.getNChannels(); channel++) {
                    String name = channels.get(channel).getName();
                    Integer color = channels.get(channel).getColor();
                    if (imp instanceof CompositeImage impComp) {
                        LUT lut = impComp.getChannelLut(channel+1);
                        int ind = lut.getMapSize()-1;
                        color = lut.getRGB(ind);
                    }
                    if (sliceLabels != null) {
                        name = sliceLabels[channel];
                    }
                    channels.set(
                            channel,
                            ImageChannel.getInstance(name, color)
                    );
                }
            }
        }

        var builder = new ImageServerMetadata.Builder() //, uri.normalize().toString())
                .width(imp.getWidth())
                .height(imp.getHeight())
                .name(imp.getTitle())
                .channels(channels)
                .sizeZ(imp.getNSlices())
                .sizeT(imp.getNFrames())
                .rgb(isRGB)
                .pixelType(pixelType)
                .zSpacingMicrons(zMicrons)
                .preferredTileSize(imp.getWidth(), imp.getHeight());

        if (!Double.isNaN(xMicrons + yMicrons))
            builder = builder.pixelSizeMicrons(xMicrons, yMicrons);

        if (timeUnit != null)
            builder = builder.timepoints(timeUnit, timepoints);

        return builder.build();
    }


    /**
     * Try to parse channel names based upon the slice labels of an image.
     * @param imp
     * @return an array of channel names, or null if channel names could not be determined
     */
    private static String[] tryToParseChannelNames(ImagePlus imp) {
        Set<String> set = new LinkedHashSet<>();
        for (int c = 1; c <= imp.getNChannels(); c++) {
            var name = getNameForChannel(imp, c);
            if (name == null)
                return null;
            set.add(name);
        }
        if (set.size() == imp.getNChannels())
            return set.toArray(String[]::new);
        else
            return null;
    }

    /**
     * Try to parse a channel name based upon the slice names for all z-slices and time points for the given channel.
     * @param imp image to check
     * @param channel 1-based channel index
     * @return a potential channel name, or null if a channel name cannot be determined
     */
    private static String getNameForChannel(ImagePlus imp, int channel) {
        String currentName = null;
        for (int z = 1; z <= imp.getNSlices(); z++) {
            for (int t = 1; t <= imp.getNFrames(); t++) {
                int ind = imp.getStackIndex(channel, z, t);
                String sliceLabel = imp.getStack().getSliceLabel(ind);
                if (sliceLabel != null) {
                    sliceLabel = sliceLabel.split("\\R", 2)[0];
                }
                if (sliceLabel == null) {
                    // Skip empty slice labels
                    continue;
                } else if (currentName == null) {
                    // Set our current best guess at a channel name
                    currentName = sliceLabel;
                } else if (!Objects.equals(currentName, sliceLabel)) {
                    // We have different slice names within the same channel,
                    // so slice names shouldn't be used as channel names
                    return null;
                }
            }
        }
        return currentName;
    }

    /**
     * Attempt to parse a time unit from an ImageJ calibration string.
     * @param unit
     * @return a time unit if possible, or null if none could be found
     */
    private static TimeUnit parseTimeUnit(String unit) {
        if (unit == null || unit.isBlank())
            return null;
        unit = unit.toLowerCase().strip();
        switch (unit) {
            case "s":
            case "sec":
            case "second":
            case "seconds":
                return TimeUnit.SECONDS;
            case "ms":
            case "msec":
            case "millisecond":
            case "milliseconds":
                return TimeUnit.MILLISECONDS;
            case "us":
            case "usec":
            case "microsecond":
            case "microseconds":
                return TimeUnit.MICROSECONDS;
            case "ns":
            case "nsec":
            case "nanosecond":
            case "nanoseconds":
                return TimeUnit.NANOSECONDS;
            case "min":
            case "minute":
            case "minutes":
                return TimeUnit.MINUTES;
            case "h":
            case "hr":
            case "hour":
            case "hours":
                return TimeUnit.HOURS;
            case "d":
            case "day":
            case "days":
                return TimeUnit.DAYS;
        }
        for (TimeUnit timeUnit : TimeUnit.values()) {
            if (timeUnit.toString().equalsIgnoreCase(unit))
                return timeUnit;
        }
        return null;
    }


    /**
     * Convert an ImagePlus to a BufferedImage, for a specific z-slice and timepoint.
     * <p>
     * Note that ImageJ uses 1-based indices for z and t! Therefore these should be &gt;= 1.
     * <p>
     * A {@link ColorModel} can optionally be provided; otherwise, a default ColorModel will be
     * created for the image (with may not be particularly suitable).
     *
     * @param imp2
     * @param z
     * @param t
     * @param colorModel
     * @return
     */
    static BufferedImage convertToBufferedImage(ImagePlus imp2, int z, int t, ColorModel colorModel) {
        // Extract processor
        int nChannels = imp2.getNChannels();
        int ind = imp2.getStackIndex(1, z, t);
        ImageProcessor ip = imp2.getStack().getProcessor(ind);

        BufferedImage img = null;
        int w = ip.getWidth();
        int h = ip.getHeight();
        if (ip instanceof ColorProcessor) {
            img = ip.getBufferedImage();
        } else {
            // Try to create a suitable BufferedImage for whatever else we may need
            SampleModel model;
            if (colorModel == null) {
                if (ip instanceof ByteProcessor)
                    colorModel = ColorModelFactory.createColorModel(PixelType.UINT8, ImageChannel.getDefaultChannelList(nChannels));
                else if (ip instanceof ShortProcessor)
                    colorModel = ColorModelFactory.createColorModel(PixelType.UINT16, ImageChannel.getDefaultChannelList(nChannels));
                else
                    colorModel = ColorModelFactory.createColorModel(PixelType.FLOAT32, ImageChannel.getDefaultChannelList(nChannels));
            }

            switch (ip) {
                case ByteProcessor byteProcessor -> {
                    model = new BandedSampleModel(DataBuffer.TYPE_BYTE, w, h, nChannels);
                    byte[][] bytes = new byte[nChannels][w * h];
                    for (int i = 0; i < nChannels; i++) {
                        int sliceInd = imp2.getStackIndex(i + 1, z, t);
                        bytes[i] = ((byte[]) imp2.getStack().getPixels(sliceInd)).clone();
                    }
                    DataBufferByte buffer = new DataBufferByte(bytes, w * h);
                    return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), false, null);
                }
                case ShortProcessor shortProcessor -> {
                    model = new BandedSampleModel(DataBuffer.TYPE_USHORT, w, h, nChannels);
                    short[][] bytes = new short[nChannels][w * h];
                    for (int i = 0; i < nChannels; i++) {
                        int sliceInd = imp2.getStackIndex(i + 1, z, t);
                        bytes[i] = ((short[]) imp2.getStack().getPixels(sliceInd)).clone();
                    }
                    DataBufferUShort buffer = new DataBufferUShort(bytes, w * h);
                    return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), false, null);
                }
                case FloatProcessor floatProcessor -> {
                    model = new BandedSampleModel(DataBuffer.TYPE_FLOAT, w, h, nChannels);
                    float[][] bytes = new float[nChannels][w * h];
                    for (int i = 0; i < nChannels; i++) {
                        int sliceInd = imp2.getStackIndex(i + 1, z, t);
                        bytes[i] = ((float[]) imp2.getStack().getPixels(sliceInd)).clone();
                    }
                    DataBufferFloat buffer = new DataBufferFloat(bytes, w * h);
                    return new BufferedImage(colorModel, Raster.createWritableRaster(model, buffer, null), false, null);
                }
                default -> {
                    logger.error("Sorry, currently only RGB & single-channel images supported with ImageJ");
                    return null;
                }
            }
        }
        return img;
    }


}
