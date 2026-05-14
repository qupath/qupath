package qupath.lib.gui.measure.ui;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;

class BasicTableCell<S, T> extends TableCell<S, T> {

    public BasicTableCell(Tooltip tooltip) {
        setAlignment(Pos.CENTER);
        setTooltip(tooltip);
    }

    @Override
    protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (item == null || empty) {
            setText(null);
            setGraphic(null);
            return;
        }
        setText(item.toString());
    }

}
