package qupath.lib.gui.viewer.tools.handlers;

import javafx.scene.input.MouseEvent;
import qupath.lib.objects.PathObject;

class ArrowToolEventHandler extends LineToolEventHandler {
	
	private String arrowhead = null;
	
	ArrowToolEventHandler(String arrowhead) {
		this.arrowhead = arrowhead;
	}

	@Override
	protected PathObject createNewAnnotation(MouseEvent e, double x, double y) {
		var annotation = super.createNewAnnotation(e, x, y);
		annotation.getMetadata().put("arrowhead", arrowhead);
		return annotation;
	}

	
}
