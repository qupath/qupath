package qupath.lib.gui.helpers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.stream.Collectors;

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.SplitPane;
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
public class PaneToolsFX {

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
	
	/**
	 * Get the nodes that are included within a {@link Parent}, optionally adding other nodes recursively.
	 * Without the recursive search, this is similar to {@link Parent#getChildrenUnmodifiable()} in most cases, 
	 * except that a separate collection is used. However in some cases {@code getItems()} must be used instead. 
	 * Currently this applies only to {@link SplitPane} but this may be used elsewhere if appropriate.
	 * @param parent
	 * @param collection
	 * @param doRecursive
	 * @return
	 */
	public static Collection<Node> getContents(Parent parent, Collection<Node> collection, boolean doRecursive) {
		if (collection == null) {
			collection = new ArrayList<>();
		}
		var children = parent.getChildrenUnmodifiable();
		if (children.isEmpty() && parent instanceof SplitPane) {
			children = ((SplitPane)parent).getItems();
		}
		for (var child : children) {
			collection.add(child);
			if (doRecursive && child instanceof Parent)
				getContents((Parent)child, collection, doRecursive);
		}
		return collection;
	}
	
	/**
	 * Get the nodes of type T that are contained within a {@link Parent}, optionally adding other nodes 
	 * recursively. This can be helpful, for example, to extract all the Buttons or Regions within a pane 
	 * in order to set some property of all of them.
	 * @param <T>
	 * @param parent
	 * @param cls
	 * @param doRecursive
	 * @return
	 * 
	 * @see #getContents(Parent, Collection, boolean)
	 */
	public static <T extends Node> Collection<T> getContentsOfType(Parent parent, Class<T> cls, boolean doRecursive) {
		return getContents(parent, new ArrayList<>(), doRecursive).stream()
				.filter(p -> cls.isInstance(p))
				.map(p -> cls.cast(p))
				.collect(Collectors.toList());
	}

}
