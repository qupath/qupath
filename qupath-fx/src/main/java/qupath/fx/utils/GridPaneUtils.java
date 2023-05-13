package qupath.fx.utils;

import java.util.Arrays;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;

/**
 * Assorted static functions to help when working with a JavaFX panes.
 * 
 * @author Pete Bankhead
 *
 */
public class GridPaneUtils {

	/**
	 * Add a row of nodes.  The rowspan is always 1.  The colspan is 1 by default, 
	 * unless a Node is added multiple times consecutively in which case it is the sum 
	 * of the number of times the node is added.
	 * 
	 * @param pane
	 * @param row
	 * @param col
	 * @param tooltipText 
	 * @param nodes
	 */
	public static void addGridRow(GridPane pane, int row, int col, String tooltipText, Node... nodes) {
		Node lastNode = null;
		var tooltip = tooltipText == null ? null : new Tooltip(tooltipText);
		
		for (var n : nodes) {
			if (n == null) {
				col++;
				continue;
			}
			if (lastNode == n) {
				Integer span = GridPane.getColumnSpan(n);
				if (span == null)
					GridPane.setColumnSpan(n, 2);
				else
					GridPane.setColumnSpan(n, span + 1);
			} else {
				pane.add(n, col, row);
				GridPane.setColumnSpan(n, 1);
				if (tooltip != null) {
					installTooltipRecursive(tooltip, n, false);
				}
			}
			lastNode = n;
			col++;
		}
	}
	
	static void installTooltipRecursive(Tooltip tooltip, Node node, boolean overrideExisting) {
		if (node instanceof Control) {
			var control = (Control)node;
			if (overrideExisting || control.getTooltip() == null)
				control.setTooltip(tooltip);
		} else {
			Tooltip.install(node, tooltip);
			if (node instanceof Region) {
				for (var child : ((Region)node).getChildrenUnmodifiable())
					installTooltipRecursive(tooltip, child, overrideExisting);
			}
		}
	}
	
	/**
	 * Set the {@link GridPane#setHgrow(Node, Priority)} property for specified nodes.
	 * @param priority
	 * @param nodes
	 */
	public static void setHGrowPriority(Priority priority, Node... nodes) {
		for (var n : nodes) {
			GridPane.setHgrow(n, priority);
		}
	}

	/**
	 * Set the {@link GridPane#setVgrow(Node, Priority)} property for specified nodes.
	 * @param priority
	 * @param nodes
	 */
	public static void setVGrowPriority(Priority priority, Node... nodes) {
		for (var n : nodes) {
			GridPane.setVgrow(n, priority);
		}
	}

	/**
	 * Set the max width property for the specified regions.
	 * This can be especially useful when setting the width to {@link Double#MAX_VALUE}, indicating 
	 * that the region may be enlarged as required.
	 * @param width
	 * @param regions
	 */
	public static void setMaxWidth(double width, Region...regions) {
		for (var r : regions)
			r.setMaxWidth(width);
	}

	/**
	 * Set the max height property for the specified regions.
	 * This can be especially useful when setting the height to {@link Double#MAX_VALUE}, indicating 
	 * that the region may be enlarged as required.
	 * @param height
	 * @param regions
	 */
	public static void setMaxHeight(double height, Region...regions) {
		for (var r : regions)
			r.setMaxHeight(height);
	}
	
	/**
	 * Set the min width property for the specified regions.
	 * This can be especially useful when setting the width to {@link Region#USE_PREF_SIZE}, indicating 
	 * that the region should not be shrunk beyond its preferred size.
	 * @param width
	 * @param regions
	 */
	public static void setMinWidth(double width, Region...regions) {
		for (var r : regions)
			r.setMinWidth(width);
	}

	/**
	 * Set the min height property for the specified regions.
	 * This can be especially useful when setting the height to {@link Region#USE_PREF_SIZE}, indicating 
	 * that the region should not be shrunk beyond its preferred size.
	 * @param height
	 * @param regions
	 */
	public static void setMinHeight(double height, Region...regions) {
		for (var r : regions)
			r.setMinHeight(height);
	}
	
	/**
	 * Set the {@link GridPane#setFillWidth(Node, Boolean)} property for specified nodes.
	 * @param doFill
	 * @param nodes
	 */
	public static void setFillWidth(Boolean doFill, Node...nodes) {
		for (var n : nodes)
			GridPane.setFillWidth(n, doFill);
	}
	
	/**
	 * Set constraints and max width values (where possible) so that the specified nodes 
	 * fill all available horizontal space in a {@link GridPane}.
	 * @param nodes
	 */
	public static void setToExpandGridPaneWidth(Node...nodes) {
		setFillWidth(Boolean.TRUE, nodes);
		setHGrowPriority(Priority.ALWAYS, nodes);
		setMaxWidth(Double.MAX_VALUE, Arrays.stream(nodes).filter(n -> n instanceof Region).map(n -> (Region)n).toArray(Region[]::new));
	}
	
	/**
	 * Set constraints and max width values (where possible) so that the specified nodes 
	 * fill all available vertical space in a {@link GridPane}.
	 * @param nodes
	 */
	public static void setToExpandGridPaneHeight(Node...nodes) {
		setFillHeight(Boolean.TRUE, nodes);
		setVGrowPriority(Priority.ALWAYS, nodes);
		setMaxHeight(Double.MAX_VALUE, Arrays.stream(nodes).filter(n -> n instanceof Region).map(n -> (Region)n).toArray(Region[]::new));
	}

	
	/**
	 * Set the {@link GridPane#setFillHeight(Node, Boolean)} property for specified nodes.
	 * @param doFill
	 * @param nodes
	 */
	public static void setFillHeight(Boolean doFill, Node...nodes) {
		for (var n : nodes)
			GridPane.setFillHeight(n, doFill);
	}

	/**
	 * Create a GridPane containing rows that resize similarly to Swing's GridLayout().
	 * 
	 * @param nodes
	 * @return
	 */
	public static GridPane createRowGrid(final Node... nodes) {
		GridPane pane = new GridPane();
		int n = nodes.length;
		for (int i = 0; i < n; i++) {
			RowConstraints row = new RowConstraints();
			row.setPercentHeight(100.0/n);			
			pane.getRowConstraints().add(row);
			pane.add(nodes[i], 0, i);
		}
		ColumnConstraints col = new ColumnConstraints();
		col.setPercentWidth(100);
		pane.getColumnConstraints().add(col);
		return pane;
	}

	/**
	 * Create a GridPane containing columns that resize similarly to Swing's GridLayout(),
	 * while also resizing contained objects to have the corresponding widths.
	 * 
	 * @param nodes
	 * @return
	 */
	public static GridPane createRowGridControls(final Node... nodes) {
		GridPane pane = new GridPane();
		int n = nodes.length;
		for (int i = 0; i < n; i++) {
			RowConstraints row = new RowConstraints();
			row.setPercentHeight(100.0/n);			
			pane.getRowConstraints().add(row);
			Node node = nodes[i];
			pane.add(node, 0, i);
			if (node instanceof Control) {
//				((Control)node).setMinSize(((Control) node).getPrefWidth(), ((Control) node).getPrefHeight());
				((Control)node).prefWidthProperty().bind(pane.widthProperty());
			}
		}
		ColumnConstraints col = new ColumnConstraints();
		col.setPercentWidth(100);
		pane.getColumnConstraints().add(col);
		return pane;
	}

	/**
	 * Create a GridPane containing columns that resize similarly to Swing's GridLayout(),
	 * where controls have their widths bound to their parent.
	 * 
	 * @param nodes
	 * @return
	 */
	public static GridPane createColumnGridControls(final Node... nodes) {
		GridPane pane = new GridPane();
		int n = nodes.length;
		double maxMinWidth = 0;
		for (int i = 0; i < n; i++) {
			ColumnConstraints col = new ColumnConstraints();
			col.setPercentWidth(100.0/n);			
			pane.getColumnConstraints().add(col);
			Node node = nodes[i];
			pane.add(node, i, 0);
			if (node instanceof Control) {
				maxMinWidth = Math.max(maxMinWidth, ((Control)node).getPrefWidth());
				((Control)node).prefWidthProperty().bind(pane.widthProperty().divide(n));
			}
		}
		RowConstraints row = new RowConstraints();
		row.setPercentHeight(100);
		pane.getRowConstraints().add(row);
		pane.setMinWidth(maxMinWidth * n);
		pane.setPrefWidth(maxMinWidth * n);
		return pane;
	}

	/**
	 * Create a GridPane containing columns that resize similarly to Swing's GridLayout().
	 * 
	 * @param nodes
	 * @return
	 */
	public static GridPane createColumnGrid(final Node... nodes) {
		GridPane pane = new GridPane();
		int n = nodes.length;
		for (int i = 0; i < n; i++) {
			ColumnConstraints col = new ColumnConstraints();
			col.setPercentWidth(100.0/n);			
			pane.getColumnConstraints().add(col);
			pane.add(nodes[i], i, 0);
		}
		RowConstraints row = new RowConstraints();
		row.setPercentHeight(100);
		pane.getRowConstraints().add(row);
		return pane;
	}

	/**
	 * Create a GridPane containing columns that resize similarly to Swing's GridLayout().
	 * 
	 * @param nCols
	 * @return
	 */
	public static GridPane createColumnGrid(final int nCols) {
		GridPane pane = new GridPane();
		for (int i = 0; i < nCols; i++) {
			ColumnConstraints col = new ColumnConstraints();
			col.setPercentWidth(100.0/nCols);			
			pane.getColumnConstraints().add(col);
		}
		RowConstraints row = new RowConstraints();
		row.setPercentHeight(100);
		pane.getRowConstraints().add(row);
		return pane;
	}


}