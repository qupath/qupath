package qupath.lib.gui.panels;

import java.util.function.Function;

import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import qupath.lib.gui.icons.PathIconFactory;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;

/**
 * A {@link ListCell} for displaying {@linkplain PathObject PathObjects}, including ROI icons.
 */
public class PathObjectListCell extends ListCell<PathObject> {

	private Tooltip tooltip;
	private Function<PathObject, String> fun;
	
	PathObjectListCell() {
		this(PathObject::toString);
	}
	
	PathObjectListCell(Function<PathObject, String> stringExtractor) {
		this.fun = stringExtractor;
	}


	@Override
	protected void updateItem(PathObject value, boolean empty) {
		super.updateItem(value, empty);
		updateTooltip(value);
		if (value == null || empty) {
			setText(null);
			setGraphic(null);
			return;
		}
		setText(fun.apply(value));

		int w = 16;
		int h = 16;

		if (value.hasROI())
			setGraphic(PathIconFactory.createPathObjectIcon(value, w, h));
		else
			setGraphic(null);
	}

	void updateTooltip(final PathObject pathObject) {
		if (tooltip == null) {
			if (pathObject == null || !pathObject.isAnnotation())
				return;
			tooltip = new Tooltip();
			setTooltip(tooltip);
		} else if (pathObject == null || !pathObject.isAnnotation()) {
			setTooltip(null);
			return;
		}
		PathAnnotationObject annotation = (PathAnnotationObject)pathObject;
		String description = annotation.getDescription();
		if (description == null) {
			setTooltip(null);
		} else {
			tooltip.setText(description);
			setTooltip(tooltip);
		}
	}

}