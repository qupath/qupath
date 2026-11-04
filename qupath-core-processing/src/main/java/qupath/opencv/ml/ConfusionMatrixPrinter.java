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
 * Create a string representation of a confusion matrix for (monospaced) printing.
 */
public class ConfusionMatrixPrinter<T> {

    private static final Logger logger = LoggerFactory.getLogger(ConfusionMatrixPrinter.class);

    private final ConfusionMatrix<T> matrix;
    private final Function<T, String> stringFunction;
    private final NumberFormat formatter;
    private final boolean usePercent;

    private ConfusionMatrixPrinter(ConfusionMatrix<T> matrix, Function<T, String> stringFunction, NumberFormat formatter, boolean usePercent) {
        Objects.requireNonNull(matrix);
        Objects.requireNonNull(stringFunction);
        Objects.requireNonNull(formatter);
        this.matrix = matrix;
        this.stringFunction = stringFunction;
        this.formatter = formatter;
        this.usePercent = usePercent;
    }

    /**
     * Create a builder to customize the string conversion of a confusion matrix.
     * @param matrix the confusion matrix
     * @return a new builder
     * @param <T> generic parameter of the confusion matric
     */
    public static <T> Builder<T> builder(ConfusionMatrix<T> matrix) {
        return new Builder<>(matrix);
    }

    /**
     * Get a string representation of a confusion matrix, showing raw counts.
     * @param matrix the confusion matrix
     * @return the string
     */
    public static String getString(ConfusionMatrix<?> matrix) {
        return builder(matrix).build().getString();
    }

    /**
     * Get a string representation of a confusion matrix, showing percentages.
     * @param matrix the confusion matrix
     * @return the string
     */
    public static String getStringWithPercent(ConfusionMatrix<?> matrix) {
        return builder(matrix).percentage(true).build().getString();
    }

    /**
     * Get the confusion matrix to be converted to a string.
     * @return
     */
    public ConfusionMatrix<T> getConfusionMatrix() {
        return matrix;
    }

    private String getCell(T row, T col) {
        if (usePercent) {
            return formatter.format(matrix.getCount(row, col) * 100.0 / matrix.getTotal()) + " %";
        } else {
            return formatter.format(matrix.getCount(row, col));
        }
    }

    /**
     * Get the string representation.
     * @return
     */
    public String getString() {
        var labels = matrix.getLabels();
        int n = labels.size();
        List<String> allStrings = new ArrayList<>();

        // Top row
        allStrings.add(" ");
        for (T col : labels) {
            allStrings.add(stringFunction.apply(col));
        }

        // Other rows
        for (T row : labels) {
            allStrings.add(stringFunction.apply(row));
            for (T col : labels) {
                allStrings.add(getCell(row, col));
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

    /**
     * Builder for {@link ConfusionMatrixPrinter}.
     * @param <T>
     */
    public static class Builder<T> {

        private final ConfusionMatrix<T> matrix;
        private Function<T, String> stringFunction = Objects::toString;
        private NumberFormat formatter;
        private boolean usePercent = false;

        private Builder(ConfusionMatrix<T> matrix) {
            this.matrix = matrix;
            this.formatter = NumberFormat.getNumberInstance();
            this.formatter.setMaximumFractionDigits(2);
        }

        /**
         * Define a function to convert labels to strings.
         * @param stringFunction the string conversion function
         * @return this builder
         */
        public Builder<T> stringFunction(Function<T, String> stringFunction) {
            Objects.requireNonNull(stringFunction);
            this.stringFunction = stringFunction;
            return this;
        }

        /**
         * Define a formatter to convert numeric values.
         * @param formatter the formatter
         * @return this builder
         */
        public Builder<T> formatter(NumberFormat formatter) {
            Objects.requireNonNull(formatter);
            this.formatter = formatter;
            return this;
        }

        /**
         * Optionally show percentages instead of counts in the matrix cells.
         * @param usePercent if true, show percentages; if false, show raw counts.
         * @return this builder
         */
        public Builder<T> percentage(boolean usePercent) {
            this.usePercent = usePercent;
            return this;
        }

        /**
         * Build the confusion matrix printer.
         * @return this printer
         */
        public ConfusionMatrixPrinter<T> build() {
            return new ConfusionMatrixPrinter<T>(matrix, stringFunction, formatter, usePercent);
        }

    }

    public static void main(String[] args) {
        List<String> names = List.of("One", "Two", "Three");
        var matrix = new ConfusionMatrix<>("My matrix", names);
        var rng = new Random(120);
        for (int i = 0; i < 500; i++) {
            matrix.accumulate(names.get(rng.nextInt(names.size())), names.get(rng.nextInt(names.size())));
        }
        logger.info("\n{}", matrix.getPrintableString());
    }
}
