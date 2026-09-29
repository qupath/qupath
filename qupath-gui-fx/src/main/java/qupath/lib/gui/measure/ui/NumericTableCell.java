package qupath.lib.gui.measure.ui;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.plots.display.HistogramDisplay;
import qupath.lib.gui.plots.display.PlotDisplay;

class NumericTableCell<T> extends TableCell<T, Number> {

    private final List<PlotDisplay<?>> plotDisplays;

    public NumericTableCell(Tooltip tooltip, List<PlotDisplay<?>> plotDisplays) {
        this.plotDisplays = plotDisplays;
        setTooltip(tooltip);
        if (!plotDisplays.isEmpty())
            setOnMouseClicked(this::handleMouseClick);
    }


    @Override
    protected void updateItem(Number item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
            setText(null);
            setStyle("");
        } else {
            setAlignment(Pos.CENTER);
            if (item instanceof Integer || item instanceof Long) {
                setText(item.toString());
            } else if (Double.isNaN(item.doubleValue())) {
                setText("-");
            } else {
                double value = item.doubleValue();
                if (value >= 1000)
                    setText(GeneralTools.formatNumber(value, 1));
                else if (value >= 10)
                    setText(GeneralTools.formatNumber(value, 2));
                else
                    setText(GeneralTools.formatNumber(value, 3));
            }
        }
    }

    private void handleMouseClick(MouseEvent event) {
        if (event.isAltDown() && !plotDisplays.isEmpty()) {
            for (var plotDisplay: plotDisplays) {
                plotDisplay.plotColumns(getTableColumn().getText());
            }
            event.consume();
        }
    }


}
