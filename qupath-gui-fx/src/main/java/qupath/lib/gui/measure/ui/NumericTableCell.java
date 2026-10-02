package qupath.lib.gui.measure.ui;

import javafx.event.EventHandler;
import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import qupath.lib.common.GeneralTools;

class NumericTableCell<T> extends TableCell<T, Number> {

    public NumericTableCell(Tooltip tooltip, EventHandler<? super MouseEvent> mouseListener) {
        setTooltip(tooltip);
        setOnMouseClicked(mouseListener);
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
}
