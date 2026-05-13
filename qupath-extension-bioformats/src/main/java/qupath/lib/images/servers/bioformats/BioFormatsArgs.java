package qupath.lib.images.servers.bioformats;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine;

class BioFormatsArgs {

    @CommandLine.Option(names = {"--series", "-s"}, defaultValue = "-1", description = "Series number (0-based, must be < image count for the file)")
    int series = -1;

    @CommandLine.Option(names = {"--name", "-n"}, defaultValue = "", description = "Series name (legacy option, please use --series instead)")
    String seriesName = "";

    @CommandLine.Option(names = {"--dims"}, defaultValue = "", description = "Swap dimensions. "
            + "This should be a String of the form XYCZT, ordered according to how the image plans should be interpreted.")
    String swapDimensions = null;

    // Specific options used by some Bio-Formats readers, e.g. Map.of("zeissczi.autostitch", "false")
    @CommandLine.Option(names = {"--bfOptions"}, description = "Bio-Formats reader options")
    Map<String, String> readerOptions = new LinkedHashMap<>();

    @CommandLine.Unmatched
    List<String> unmatched = new ArrayList<>();

    BioFormatsArgs() {
    }

    /**
     * Return to an array of String args.
     *
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
            args.add(option.getKey() + "=" + option.getValue());
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
