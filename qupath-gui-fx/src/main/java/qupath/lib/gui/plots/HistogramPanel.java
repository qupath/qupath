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

package qupath.lib.gui.plots;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.gui.helpers.ColorToolsFX;


/**
 * An extremely basic histogram display, which does not support drawing axis.
 * 
 * It may be used in places where a histogram would be useful, but it isn't worth creating a full one -
 * or where there isn't space to include axes.
 * 
 * A tooltip is used instead of axes, to provide some feedback regarding the underlying values.
 * 
 * Vertical lines (e.g. to depict thresholds) can also be included.
 * 
 * @author Pete Bankhead
 *
 */
public class HistogramPanel extends Canvas {

	private static DecimalFormat df = new DecimalFormat("#.##");
	
	private Histogram histogram;
	
	private Map<Double, Color> vLines = new HashMap<>();
	
	private Color colorBackground = ColorToolsFX.getColorWithOpacity(Color.WHITE, .25);
	
	private Color colorDraw = ColorToolsFX.getColorWithOpacity(Color.DARKGRAY, 0.70);
	private Color colorFill = ColorToolsFX.getColorWithOpacity(Color.DARKGRAY, 0.50);

	private Color defaultLineColor = ColorToolsFX.getColorWithOpacity(Color.RED, 0.2);

	// Padding of this component used for drawing the axes - and needed to compute the tooltip text
	private int padX1 = 1, padX2 = 1, padY1 = 1, padY2 = 1;
	
	// Tooltip used to display where the cursor currently is
	private boolean tooltipInstalled = false;
	private Tooltip tooltip = new Tooltip();
	
	private Pane pane;
	
	
	public HistogramPanel(final Histogram histogram) {
		super();
		this.histogram = histogram;
		setWidth(100);
		setHeight(100);
		pane = new StackPane();
		pane.getChildren().add(this);
		
		widthProperty().addListener(e -> repaint());
        heightProperty().addListener(e -> repaint());
        
		widthProperty().bind(pane.prefWidthProperty());
		heightProperty().bind(pane.prefHeightProperty());
		
		addEventFilter(MouseEvent.ANY, e -> updateToolTip(e.getX(), e.getY()));
	}
	
	@Override
    public boolean isResizable() {
        return true;
    }
	
	@Override
    public double prefWidth(double height) {
        return getWidth();
    }

    @Override
    public double prefHeight(double width) {
        return getHeight();
    }
	
	public void setHistogram(Histogram histogram) {
		if (this.histogram == histogram)
			return;
		this.histogram = histogram;
		repaint();
	}
	
	public Pane getPane() {
		return pane;
	}
	
	public void setHistogramBackgroundColor(final Color colorBackground) {
		this.colorBackground = colorBackground;
		repaint();
	}

	public Color getHistogramBackgroundColor() {
		return colorBackground;
	}
	
	public void setHistogramBarEdgeColor(final Color colorDraw) {
		this.colorDraw = colorDraw;
		repaint();
	}

	public Color getHistogramBarEdgeColor() {
		return colorDraw;
	}
	
	public void setHistogramBarFillColor(final Color colorFill) {
		this.colorFill = colorFill;
		repaint();
	}

	public Color getHistogramBarFillColor() {
		return colorFill;
	}
	
	/**
	 * Set histogram bar &amp; fill colors based on a base color, which will automatically have its
	 * opacity adjusted to improve appearance.
	 * 
	 * For more control over appearance, setHistogramBarEdgeColor and setHistogramBarFillColor can be used instead.
	 * 
	 * @param color
	 */
	public void setBaseHistogramBarColor(final Color color) {
		colorDraw = ColorToolsFX.getColorWithOpacity(color, 0.70);
		colorFill = ColorToolsFX.getColorWithOpacity(color, 0.50);
		repaint();
	}

	private void updateToolTip(double x, double y) {
		if (histogram == null) {
			setTooltipText(null);
			return;
		}
		double width = getWidth() - padX1 - padX2;
		double value = ((x - padX1) / width) * histogram.getEdgeRange() + histogram.getEdgeMin();
		int ind = histogram.getBinIndexForValue(value);
		if (ind < 0) {
			setTooltipText(null);
			return;
		}
		String text;
		
//		if (histogram.getNormalizeCounts())
			text = "Bin center: " + df.format(0.5 * (histogram.getBinLeftEdge(ind) + histogram.getBinRightEdge(ind))) +
					", Counts: " + df.format(histogram.getCountsForBin(ind)) + " (" + df.format(histogram.getNormalizedCountsForBin(ind)*100) + "%)";
//		else
//			text = "Bin center: " + df.format(0.5 * (histogram.getBinLeftEdge(ind) + histogram.getBinRightEdge(ind))) +
//					", Counts: " + df.format(histogram.getCountsForBin(ind));
		setTooltipText(text);
//		if (histogram.getNormalizeCounts())
//			return String.format("Bin center: %.2f, Counts :%.2f%%",
//				0.5 * (histogram.getBinLeftEdge(ind) + histogram.getBinRightEdge(ind)),
//				histogram.getCountsForBin(ind)*100);
//		else
//			return String.format("Bin center: %.2f, Counts :%.2f",
//					0.5 * (histogram.getBinLeftEdge(ind) + histogram.getBinRightEdge(ind)),
//					histogram.getCountsForBin(ind));
	}
	
	
	private void setTooltipText(final String text) {
		if (text == null) {
			if (tooltipInstalled) {
				Tooltip.uninstall(this, tooltip);
				tooltipInstalled = false;
			}
			return;
		}
		tooltip.setText(text);
		if (!tooltipInstalled) {
			Tooltip.install(this, tooltip);
			tooltipInstalled = true;
		}
	}
	
	
	public Histogram getHistogram() {
		return histogram;
	}
	
	/**
	 * Add vertical lines at requested x coordinate
	 * @param x
	 */
	public void addVerticalLine(double x, Color color) {
		vLines.put(x, color);
		repaint();
	}
	
	public void setVerticalLines(double[] x, Color color) {
		vLines.clear();
		if (x == null)
			return;
		for (double xx : x)
			vLines.put(xx, color);
		repaint();
	}
	
	public void clearVerticalLines() {
		setVerticalLines(null, null);
	}
	
	
	private void repaint() {
		GraphicsContext g = getGraphicsContext2D();
		g.clearRect(0, 0, getWidth(), getHeight());
		
		if (histogram == null)
			return;
		
		
//		Insets insets = getInsets();
//		drawHistogram(g, insets.left, insets.top, getWidth()-insets.left-insets.right, getHeight()-insets.top-insets.bottom);
		
		drawHistogram(g, padX1, padY1, getWidth()-padX1-padX2, getHeight()-padY1-padY2);

	}
	
	public void drawHistogram(GraphicsContext g2d, double x, double y, double width, double height) {
		
		g2d.save();
		
		// Shift according to the requested origin
		g2d.translate(x, y);
		
		drawBars(g2d, x, y, width, height);
		
		g2d.restore();
	}
	
	public void drawBars(GraphicsContext g2d, double x, double y, double width, double height) {
		double scaleY = height / histogram.getMaxCount();
		double scaleX = width / histogram.getEdgeRange();
		
		double edgeMin = histogram.getEdgeMin();
		double edgeMax = histogram.getEdgeMax();
		
		// Draw any required vertical lines
		if (!vLines.isEmpty()) {
			g2d.setLineWidth(2);
			for (Entry<Double, Color> entry : vLines.entrySet()) {
				double xLine = entry.getKey();
				if (xLine < edgeMin || xLine > edgeMax)
					continue;
				double xx = x + (xLine - edgeMin) * scaleX;
				Color color = entry.getValue();
				if (color != null)
					g2d.setStroke(color);
				else
					g2d.setStroke(defaultLineColor);
				g2d.strokeLine(xx, y, xx, y+height);
			}
		}
				

		// Draw a rectangle
		g2d.setLineWidth(1);
		g2d.setFill(colorBackground);
		g2d.fillRect(x, y, width, height);
		g2d.setStroke(Color.LIGHTGRAY);
		g2d.strokeRect(x, y, width, height);

		
		// Draw the bars
		g2d.setLineWidth(1.5);

		for (int i = 0; i < histogram.nBins(); i++) {
			double xStart = x + scaleX * (histogram.getBinLeftEdge(i) - edgeMin);
			double xWidth = scaleX * histogram.getBinWidth(i);
			double counts = histogram.getCountsForBin(i);
			double yStart = height-counts*scaleY+y;
			double yHeight = counts*scaleY;
			if (colorFill != null) {
				g2d.setFill(colorFill);
				g2d.fillRect(xStart, yStart, xWidth, yHeight);
			}
			if (colorDraw != null) {
				g2d.setStroke(colorDraw);
				g2d.strokeRect(xStart, yStart, xWidth, yHeight);
			}
		}
		
		
		defaultLineColor = ColorToolsFX.getColorWithOpacity(Color.RED, 0.2);
		
	}
	
}