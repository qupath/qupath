/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.helpers;

import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.RowConstraints;

/**
 * Some static methods for helping with JavaFX layouts.
 * 
 * @author Pete Bankhead
 *
 */
public class PanelToolsFX {

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

}
