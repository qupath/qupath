package qupath.lib.gui.helpers;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Assorted static functions to help when working with a JavaFX {@link GridPane}.
 * 
 * @author Pete Bankhead
 *
 */
public class GridPaneTools {

	/**
	 * Add a row of nodes.  The rowspan is always 1.  The colspan is 1 by default, 
	 * unless a Node is added multiple times consecutively in which case it is the sum 
	 * of the number of times the node is added.
	 * 
	 * @param pane
	 * @param row
	 * @param col
	 * @param nodes
	 */
	public static void addGridRow(GridPane pane, int row, int col, String tooltipText, Node... nodes) {
		Node lastNode = null;
		var tooltip = tooltipText == null ? null : new Tooltip(tooltipText);
		
		for (var n : nodes) {
			if (lastNode == n) {
				Integer span = GridPane.getColumnSpan(n);
				if (span == null)
					GridPane.setColumnSpan(n, 2);
				else
					GridPane.setColumnSpan(n, span + 1);
			} else {
				pane.add(n, col, row);
				if (tooltip != null) {
					installTooltipRecursive(tooltip, n);
				}
			}
			lastNode = n;
			col++;
		}
	}
	
	static void installTooltipRecursive(Tooltip tooltip, Node node) {
		if (node instanceof Control)
			((Control)node).setTooltip(tooltip);
		else {
			Tooltip.install(node, tooltip);
			if (node instanceof Region) {
				for (var child : ((Region)node).getChildrenUnmodifiable())
					installTooltipRecursive(tooltip, child);
			}
		}
	}
	
	
	public static void setHGrowPriority(Priority priority, Node... nodes) {
		for (var n : nodes) {
			GridPane.setHgrow(n, priority);
		}
	}

	public static void setVGrowPriority(Priority priority, Node... nodes) {
		for (var n : nodes) {
			GridPane.setVgrow(n, priority);
		}
	}

	public static void setMaxWidth(double width, Region...regions) {
		for (var r : regions)
			r.setMaxWidth(width);
	}

	public static void setMaxHeight(double height, Region...regions) {
		for (var r : regions)
			r.setMaxWidth(height);
	}
	
	public static void setMaxSize(double width, double height, Region...regions) {
		for (var r : regions)
			r.setMaxSize(width, height);
	}

	public static void setFillWidth(Boolean doFill, Node...nodes) {
		for (var n : nodes)
			GridPane.setFillWidth(n, doFill);
	}

	public static void setFillHeight(Boolean doFill, Node...nodes) {
		for (var n : nodes)
			GridPane.setFillHeight(n, doFill);
	}

}
