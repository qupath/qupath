package qupath.opencv.ml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.function.Function;

/**
 * Create a string representation of a confusion matrix for display, either as delimited text or
 * a more graphical display suitable for use with a monospaced font.
 * <p>
 * Instances of this class are immutable, but not necessarily thread-safe because {@link NumberFormat} is
 * not synchronized, and the string extraction function may or may not be thread-safe.
 */
public class ConfusionMatrixPrinter<T> {

    private static final Logger logger = LoggerFactory.getLogger(ConfusionMatrixPrinter.class);

    private final Function<T, String> stringFunction;
    private final NumberFormat formatter;
    private final boolean usePercent;

    private ConfusionMatrixPrinter(Function<T, String> stringFunction, NumberFormat formatter, boolean usePercent) {
        Objects.requireNonNull(stringFunction);
        Objects.requireNonNull(formatter);
        this.stringFunction = stringFunction;
        this.formatter = formatter;
        this.usePercent = usePercent;
    }

    /**
     * Create a printer to display confusion matrix counts (not percentages), using the default method of
     * extracting a string from the confusion matrix labels.
     * @return the new printer
     */
    public static ConfusionMatrixPrinter<Object> create() {
        return new ConfusionMatrixPrinter<>(Object::toString, NumberFormat.getInstance(), false);
    }

    /**
     * Create a new printer that is similar to this one, except that it displays percentages (not counts).
     * @return the new printer
     */
    public ConfusionMatrixPrinter<T> percent() {
        return new ConfusionMatrixPrinter<>(this.stringFunction, this.formatter, true);
    }

    /**
     * Create a new printer that is similar to this one, except that it displays counts (not percentages).
     * @return the new printer
     */
    public ConfusionMatrixPrinter<T> counts() {
        return new ConfusionMatrixPrinter<>(this.stringFunction, this.formatter, false);
    }

    /**
     * Create a new printer that is similar to this one, except that uses a custom function to extract
     * strings from labels.
     * @return the new printer
     */
    public <S> ConfusionMatrixPrinter<S> stringFunction(Function<S, String> stringFunction) {
        return new ConfusionMatrixPrinter<>(stringFunction, this.formatter, this.usePercent);
    }

    /**
     * Create a new printer that is similar to this one, except that uses a custom number formatter.
     * @return the new printer
     */
    public ConfusionMatrixPrinter<T> formatter(NumberFormat formatter) {
        return new ConfusionMatrixPrinter<>(this.stringFunction, formatter, this.usePercent);
    }


    private <S> String getCellValue(ConfusionMatrix<S> matrix, S row, S col) {
        if (usePercent) {
            return formatter.format(matrix.getCount(row, col) * 100.0 / matrix.getTotal()) + "%";
        } else {
            return formatter.format(matrix.getCount(row, col));
        }
    }

    /**
     * Get a tab-delimited string containing the labels and values of the confusion matrix.
     * @param matrix the confusion matrix to display
     * @return the delimited string
     */
    public String getTabDelimitedString(ConfusionMatrix<? extends T> matrix) {
        return getDelimitedString(matrix, "\t", System.lineSeparator());
    }

    /**
     * Get a comma-delimited string containing the labels and values of the confusion matrix.
     * @param matrix the confusion matrix to display
     * @return the delimited string
     */
    public String getCommaDelimitedString(ConfusionMatrix<? extends T> matrix) {
        return getDelimitedString(matrix, ",", System.lineSeparator());
    }

    /**
     * Get a string containing the labels and values of the confusion matrix.
     * @param matrix the confusion matrix to display
     * @param delimiter the delimiter to use between values on the same row (usually tab or comma)
     * @param newline the delimiter to use to indicate the end of a row of the matrix (usually newline or semicolon)
     * @return the delimited string
     */
    public <S extends T> String getDelimitedString(ConfusionMatrix<S> matrix, String delimiter, String newline) {
        var sb = new StringBuilder();
        for (var col : matrix.getLabels()) {
            sb.append(delimiter);
            sb.append(stringFunction.apply(col));
        }
        sb.append(newline);
        for (var row : matrix.getLabels()) {
            sb.append(stringFunction.apply(row));
            for (var col : matrix.getLabels()) {
                sb.append(delimiter);
                sb.append(getCellValue(matrix, row, col));
            }
            sb.append(newline);
        }
        return sb.toString();
    }

    /**
     * Get a string representation that attempts to graphically display the confusion matrix,
     * when viewed with a monospaced font.
     * @param matrix the confusion matrix to display
     * @return the displayable string
     */
    public <S extends T> String getDisplayableString(ConfusionMatrix<S> matrix) {
        var labels = matrix.getLabels();
        int n = labels.size();
        List<String> allStrings = new ArrayList<>();

        // Top row
        allStrings.add(" ");
        for (S col : labels) {
            allStrings.add(stringFunction.apply(col));
        }

        // Other rows
        for (S row : labels) {
            allStrings.add(stringFunction.apply(row));
            for (S col : labels) {
                allStrings.add(getCellValue(matrix, row, col));
            }
        }

        // Take cell width from longest string, plus padding
        int cellWidth = allStrings.stream().mapToInt(String::length).max().orElse(0) + 2;

        StringBuilder sb = new StringBuilder();
        int ind = 0;
        // Top row
        appendTableRow(sb, '-', n+1, cellWidth);
        sb.append("|");
        for (int c = 0; c < n+1; c++) {
            appendWithPadding(sb, allStrings.get(ind++), cellWidth);
            sb.append("|");
        }
        sb.append(System.lineSeparator());

        // Other rows
        for (int r = 0; r < n; r++) {
            appendTableRow(sb, '-', n+1, cellWidth);
            appendTableRow(sb, ' ', n+1, cellWidth);

            sb.append("|");
            for (int c = 0; c < n+1; c++) {
                appendWithPadding(sb, allStrings.get(ind++), cellWidth);
                sb.append("|");
            }
            sb.append(System.lineSeparator());

            appendTableRow(sb, ' ', n+1, cellWidth);
        }
        appendTableRow(sb, '-', n+1, cellWidth);
        return sb.toString();
    }

    private static void appendTableRow(StringBuilder sb, char tableChar, int nColumns, int cellWidth) {
        for (int c = 0; c < nColumns; c++) {
            sb.append("|");
            sb.repeat(tableChar, cellWidth);
        }
        sb.append("|");
        sb.append(System.lineSeparator());
    }

    private static void appendWithPadding(StringBuilder sb, String string, int length) {
        int diff = length - string.length();
        if (diff <= 0) {
            sb.append(string);
        } else {
            sb.repeat(" ", diff / 2);
            sb.append(string);
            sb.repeat(" ", diff - diff / 2);
        }
    }


    public static void main(String[] args) {
        List<String> names = List.of("One", "Two", "Three");
        var matrix = new ConfusionMatrix<>("My matrix", names);
        var rng = new Random(120);
        for (int i = 0; i < 500; i++) {
            matrix.accumulate(names.get(rng.nextInt(names.size())), names.get(rng.nextInt(names.size())));
        }
        logger.info("\n{}", ConfusionMatrixPrinter.create()
                .getDisplayableString(matrix));
        logger.info("\n[{}]", ConfusionMatrixPrinter.create()
                        .percent()
                        .stringFunction((String s) -> s + "-append")
                        .formatter(NumberFormat.getCompactNumberInstance())
                        .getDelimitedString(matrix, ", ", "; "));
    }
}
