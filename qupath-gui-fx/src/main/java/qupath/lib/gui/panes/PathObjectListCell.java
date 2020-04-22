package qupath.lib.gui.panes;

import java.util.function.Function;

import javafx.scene.control.ListCell;
import javafx.scene.control.Tooltip;
import qupath.lib.gui.tools.IconFactory;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.roi.interfaces.ROI;

/**
 * A {@link ListCell} for displaying {@linkplain PathObject PathObjects}, including {@link ROI} icons.
 */
public class PathObjectListCell extends ListCell<PathObject> {

	private Tooltip tooltip;
	private Function<PathObject, String> fun;
	
	/**
	 * Constructor, using default {@link PathObject#toString()} method to generate the String representation.
	 */
	public PathObjectListCell() {
		this(PathObject::toString);
	}
	
	/**
	 * Constructor using a custom string extraction function.
	 * @param stringExtractor function to generate a String representation of the object.
	 */
	public PathObjectListCell(Function<PathObject, String> stringExtractor) {
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
			setGraphic(IconFactory.createPathObjectIcon(value, w, h));
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