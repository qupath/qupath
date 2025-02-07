package qupath.lib.gui.measure.ui;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.charts.HistogramDisplay;

class NumericTableCell<T> extends TableCell<T, Number> {

    private final HistogramDisplay histogramDisplay;

    public NumericTableCell(String tooltipText, HistogramDisplay histogramDisplay) {
        this.histogramDisplay = histogramDisplay;
        if (tooltipText != null && !tooltipText.isEmpty())
            setTooltip(new Tooltip(tooltipText));
    }


    @Override
    protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
            setText(null);
            setStyle("");
        } else {
            setAlignment(Pos.CENTER);
            if (Double.isNaN(item.doubleValue()))
                setText("-");
            else {
                if (item.doubleValue() >= 1000)
                    setText(GeneralTools.formatNumber(item.doubleValue(), 1));
                else if (item.doubleValue() >= 10)
                    setText(GeneralTools.formatNumber(item.doubleValue(), 2));
                else
                    setText(GeneralTools.formatNumber(item.doubleValue(), 3));
            }


            setOnMouseClicked(e -> {
                if (e.isAltDown() && histogramDisplay != null) {
                    histogramDisplay.showHistogram(getTableColumn().getText());
                    e.consume();
                }
            });
        }
    }

}
