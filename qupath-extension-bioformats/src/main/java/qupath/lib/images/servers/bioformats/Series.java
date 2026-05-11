package qupath.lib.images.servers.bioformats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import loci.formats.IFormatReader;
import loci.formats.ome.OMEPyramidStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Representation of a single series (image) readable by Bio-Formats.
 * <p>
 * This connects the integer series identifier with the size of each resolution and 'cleaned' versions of
 * the series name in a lightweight way that does not require storing a reference to any reader or
 * metadata object.
 */
class Series {

    private static final Logger logger = LoggerFactory.getLogger(Series.class);

    /**
     * Image names (in lower case) normally associated with 'extra' images, but probably not representing the main image in the file.
     */
    private static final Collection<String> associatedImageNames = Set.of(
            "overview", "label", "thumbnail", "macro", "macro image", "macro mask image", "label image", "overview image", "thumbnail image"
    );

    private final int seriesNumber;
    private final String originalSeriesName;
    private final String cleanSeriesName;
    private final String uniqueName;
    private final List<SeriesDimensions> resolutions;

    Series(int seriesNumber, String originalSeriesName, List<SeriesDimensions> resolutions) {
        this.seriesNumber = seriesNumber;
        this.originalSeriesName = originalSeriesName == null ? "" : originalSeriesName;
        this.cleanSeriesName = cleanName(this.originalSeriesName);
        this.uniqueName = this.cleanSeriesName.isEmpty() ?
                String.format("Series %d", this.seriesNumber) :
                String.format("Series %d (%s)", this.seriesNumber, this.cleanSeriesName);
        Objects.requireNonNull(resolutions, "Resolutions must not be null!");
        this.resolutions = List.copyOf(resolutions);
    }

    private static String cleanName(final String name) {
        String cleanName = name;
        while (cleanName != null && cleanName.endsWith("\0"))
            cleanName = cleanName.substring(0, cleanName.length() - 1);
        return cleanName;
    }

    /**
     * Get the integer series number, compatible with {@link IFormatReader#setSeries(int)}.
     *
     * @return
     */
    public int getSeries() {
        return seriesNumber;
    }

    /**
     * Get the original series name as stored in the metadata,
     * or an empty string if no name is stored.
     *
     * @return
     */
    public String getOriginalSeriesName() {
        return originalSeriesName;
    }

    /**
     * Get a name for display. This includes the series number, so is unique.
     *
     * @return
     */
    public String getUniqueSeriesName() {
        return uniqueName;
    }

    /**
     * Get a cleaned version of the original series name, removing any trailing null terminators.
     * <p>
     * See https://github.com/qupath/qupath/issues/573
     *
     * @return
     */
    public String getCleanSeriesName() {
        return cleanSeriesName;
    }

    public long totalPixelsXYZT() {
        if (resolutions.isEmpty())
            return 0;
        else
            return resolutions.getFirst().totalPixelsXYZT();
    }

    public int nResolutions() {
        return resolutions.size();
    }

    public List<SeriesDimensions> getResolutions() {
        return resolutions;
    }

    boolean isAssociatedImage() {
        return nResolutions() == 1 &&
                (associatedImageNames.contains(originalSeriesName.toLowerCase()) ||
                        associatedImageNames.contains(cleanSeriesName.toLowerCase()));
    }

    public static List<Series> parseFromReader(IFormatReader reader) {
        var meta = (OMEPyramidStore)reader.getMetadataStore();
        List<Series> allSeries = new ArrayList<>();
        for (int s = 0; s < meta.getImageCount(); s++) {
            reader.setSeries(s);
            List<SeriesDimensions> resolutions = new ArrayList<>();
            for (int r = 0; r < reader.getResolutionCount(); r++) {
                reader.setResolution(r);
                var dims = new SeriesDimensions(
                        reader.getSizeX(),
                        reader.getSizeY(),
                        reader.getSizeC(),
                        reader.getSizeZ(),
                        reader.getSizeT()
                );
                resolutions.add(dims);
            }
            if (resolutions.isEmpty()) {
                logger.warn("No resolutions found for series {}", s);
            } else {
                var series = new Series(s, meta.getImageName(s), resolutions);
                allSeries.add(series);
            }
        }
        return allSeries;
    }

}
