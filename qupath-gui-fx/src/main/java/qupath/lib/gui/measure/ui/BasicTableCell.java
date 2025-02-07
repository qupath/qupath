package qupath.lib.gui.measure.ui;

import javafx.geometry.Pos;
import javafx.scene.control.TableCell;
import javafx.scene.control.Tooltip;

class BasicTableCell<S, T> extends TableCell<S, T> {

    public BasicTableCell(String tooltipText) {
        setAlignment(Pos.CENTER);
        if (tooltipText != null && !tooltipText.isEmpty())
            setTooltip(new Tooltip(tooltipText));
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
