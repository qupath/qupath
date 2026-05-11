package qupath.lib.images.servers.bioformats;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.util.List;
import java.util.Objects;
import loci.formats.ClassList;
import loci.formats.FormatException;
import loci.formats.IFormatReader;
import loci.formats.ImageReader;
import loci.formats.ReaderWrapper;
import loci.formats.gui.AWTImageTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.TileRequest;

/**
 * Because an {@link IFormatReader} is not thread-safe, this wraps around a reader
 */
class SynchronizedImageReader implements Closeable {

    private static final Logger logger = LoggerFactory.getLogger(SynchronizedImageReader.class);

    private final IFormatReader reader;
    private final String id;

    SynchronizedImageReader(IFormatReader reader, String id) {
        Objects.requireNonNull(reader, "Reader must not be null");
        this.reader = reader;
        this.id = id;
    }
    


    BufferedImage openImage(TileRequest tileRequest, int series, int nChannels, int[] samplesPerPixel, boolean isRGB, ColorModel colorModel) throws IOException {
        int level = tileRequest.getLevel();
        int tileX = tileRequest.getTileX();
        int tileY = tileRequest.getTileY();
        int tileWidth = tileRequest.getTileWidth();
        int tileHeight = tileRequest.getTileHeight();
        int z = tileRequest.getZ();
        int t = tileRequest.getT();

        byte[][] bytes;
        int effectiveC;
        ByteOrder order;
        boolean interleaved;
        int pixelType;
        boolean normalizeFloats;

        try {
            // Check if this is non-zero
            if (tileWidth <= 0 || tileHeight <= 0) {
                throw new IOException("Unable to request pixels for region with downsampled size " + tileWidth + " x " + tileHeight);
            }

            synchronized (reader) {
                ensureOpen();
                reader.setSeries(series);

                // Some files provide z scaling (the number of z stacks decreases when the resolution becomes
                // lower, like the width and height), so z needs to be updated for levels > 0
                if (level > 0 && z > 0) {
                    reader.setResolution(0);
                    int zStacksFullResolution = reader.getSizeZ();
                    reader.setResolution(level);
                    int zStacksCurrentResolution = reader.getSizeZ();

                    if (zStacksFullResolution != zStacksCurrentResolution) {
                        z = (int) (z * zStacksCurrentResolution / (float) zStacksFullResolution);
                    }


                } else {
                    reader.setResolution(level);
                }

                order = reader.isLittleEndian() ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
                interleaved = reader.isInterleaved();
                pixelType = reader.getPixelType();
                normalizeFloats = reader.isNormalized();

                // Single-channel & RGB images are straightforward... nothing more to do
                if ((reader.isRGB() && isRGB) || nChannels == 1) {
                    // Read the image - or at least the first channel
                    int ind = reader.getIndex(z, 0, t);
                    try {
                        byte[] bytesSimple = reader.openBytes(ind, tileX, tileY, tileWidth, tileHeight);
                        return AWTImageTools.openImage(bytesSimple, reader, tileWidth, tileHeight);
                    } catch (Exception | UnsatisfiedLinkError e) {
                        logger.warn("Unable to open image {} for {}", ind, tileRequest.getRegionRequest());
                        throw ReaderUtils.convertToIOException(e);
                    }
                }
                // Read bytes for all the required channels
                effectiveC = reader.getEffectiveSizeC();
                bytes = new byte[effectiveC][];
                try {
                    for (int c = 0; c < effectiveC; c++) {
                        int ind = reader.getIndex(z, c, t);
                        bytes[c] = reader.openBytes(ind, tileX, tileY, tileWidth, tileHeight);
                    }
                } catch (ClosedChannelException e) {
                    // This occurs when a request is interrupted
                    logger.warn("Closed channel exception, closing reader");
                    reader.close(false);
                    throw e;
                } catch (Exception | UnsatisfiedLinkError e) {
                    throw ReaderUtils.convertToIOException(e);
                }
            }
        } catch (FormatException e) {
            logger.debug("Unable to open reader: {}", e.getMessage(), e);
            throw new IOException(e);
        }

        return new OMEPixelParser.Builder()
                .isInterleaved(interleaved)
                .pixelType(ReaderUtils.formatToPixelType(pixelType))
                .byteOrder(order)
                .normalizeFloats(normalizeFloats)
                .effectiveNChannels(effectiveC)
                .samplesPerPixel(samplesPerPixel)
                .build()
                .parse(bytes, tileWidth, tileHeight, nChannels, colorModel);
    }


    String getFormat() {
        return reader.getFormat();
    }


    private void ensureOpen() throws IOException, FormatException {
        synchronized (reader) {
            if (!id.equals(reader.getCurrentFile())) {
                reader.close();
                reader.setFlattenedResolutions(false);
                reader.setId(id);
            }
        }
    }

    public BufferedImage openSeries(int series) throws FormatException, IOException {
        synchronized (reader) {
            ensureOpen();
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
    }

    ImageServerMetadata parseMetadata(Series series, String imageName, String serverPath) throws IOException, FormatException {
        synchronized (reader) {
            ensureOpen();
            return ReaderUtils.buildOriginalMetadata(
                    reader,
                    series,
                    imageName,
                    serverPath
            );
        }
    }

    boolean checkCanRead() throws IOException, FormatException {
        synchronized (reader) {
            ensureOpen();
            return reader.getSizeX() > 0
                    && reader.getSizeY() > 0
                    && reader.openBytes(0, 0, 0, 1, 1) != null;
        }
    }

    @Override
    public void close() throws IOException {
        this.reader.close();
    }

    List<Series> getAllSeries() throws IOException, FormatException {
        synchronized (reader) {
            ensureOpen();
            return Series.parseFromReader(reader);
        }
    }

    ClassList<IFormatReader> getBaseClassList() throws IOException, FormatException {
        synchronized (reader) {
            ensureOpen();
            return unwrapBaseClassList(reader);
        }
    }

    private static ClassList<IFormatReader> unwrapBaseClassList(IFormatReader reader) {
        while (true) {
            IFormatReader nextReader = null;
            if (reader instanceof ReaderWrapper wrapper)
                nextReader = wrapper.getReader();
            else if (reader instanceof ImageReader imageReader)
                nextReader = imageReader.getReader();
            if (nextReader == null)
                break;
            else
                reader = nextReader;
        }
        var classlist = new ClassList<>(IFormatReader.class);
        classlist.addClass(reader.getClass());
        return classlist;
    }

}
