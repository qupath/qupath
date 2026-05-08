package qupath.lib.images.servers.bioformats;

import java.io.IOException;
import java.util.Optional;
import loci.formats.FormatTools;
import ome.xml.meta.MetadataRetrieve;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.PixelType;

class ReaderUtils {

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
     *
     * @param throwable
     * @return
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
}
