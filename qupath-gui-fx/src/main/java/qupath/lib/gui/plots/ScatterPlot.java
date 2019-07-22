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

import java.util.Arrays;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.apache.commons.math3.stat.correlation.SpearmansCorrelation;

import javafx.geometry.Rectangle2D;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import qupath.lib.color.ColorToolsAwt;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.helpers.ColorToolsFX;

/**
 * Store data and draw a simple scatter plot.
 * 
 * @author Pete Bankhead
 *
 */
public class ScatterPlot {
	
	private static Color DEFAULT_COLOR_DRAW = ColorToolsFX.getColorWithOpacity(Color.RED, 0.1);
//	private static Color DEFAULT_COLOR_FILL = null; //DisplayHelpers.getColorWithOpacity(Color.RED, 0.5);
	
	private double[] x;
	private double[] y;
	private double minX, maxX, minY, maxY;
	
	private boolean correlationsCalculated = false;
	private double pearsonsCorrelation = Double.NaN;
	private double spearmansCorrelation = Double.NaN;
	
	private Color[] colorDraw = null;
	private Color[] colorFill = null;
	
	private boolean fillMarkers = false;
	private double markerSize = 3;
	
	public ScatterPlot(double[] x, double[] y) {
		this(x, y, null, null);
	}

	public ScatterPlot(float[] x, float[] y) {
		this(x, y, null, null);
	}

	public ScatterPlot(float[] x, float[] y, int[] colorDraw, int[] colorFill) {
		this(toDouble(x), toDouble(y), colorDraw, colorFill);
	}
	
	
	private void compute(double[] x, double[] y) {
		this.x = x;
		this.y = y;
		
		minX = Double.POSITIVE_INFINITY;
		maxX = Double.NEGATIVE_INFINITY;
		for (double v : x) {
			if (v > maxX)
				maxX = v;
			else if (v < minX)
				minX = v;
		}
		
		minY = Double.POSITIVE_INFINITY;
		maxY = Double.NEGATIVE_INFINITY;
		for (double v : y) {
			if (v > maxY)
				maxY = v;
			else if (v < minY)
				minY = v;
		}
	}
	
	
	private void setColors(int[] colorDraw, int[] colorFill) {
		// Set the colors
		this.colorDraw = new Color[x.length];		
		if (colorDraw == null || colorDraw.length == 0) {
			if (colorFill == null)
				Arrays.fill(this.colorDraw, DEFAULT_COLOR_DRAW);
			else
				Arrays.fill(this.colorDraw, null);
		}
		else if (colorDraw.length == 1) {
			Arrays.fill(this.colorDraw, ColorToolsAwt.getCachedColor(colorDraw[0], ColorTools.alpha(colorDraw[0]) != 0));
		} else {
			for (int i = 0; i < Math.min(this.colorDraw.length, colorDraw.length); i++) {
				this.colorDraw[i] = ColorToolsFX.getCachedColor(colorDraw[i], ColorTools.alpha(colorFill[i]) != 0);
			}
		}

		this.colorFill = new Color[x.length];		
		fillMarkers = true;
		if (colorFill == null || colorFill.length == 0) {
			fillMarkers = false;
		}
		else if (colorFill.length == 1)
			Arrays.fill(this.colorFill, ColorToolsFX.getCachedColor(colorFill[0], false));
//			Arrays.fill(this.colorFill, ColorToolsFX.getCachedColor(colorFill[0], ColorTools.alpha(colorFill[0]) != 0));
		else {
			this.colorFill = new Color[x.length];
			for (int i = 0; i < Math.min(this.colorFill.length, colorFill.length); i++) {
				// TODO: Check what to do with alpha...?
				this.colorFill[i] = ColorToolsFX.getCachedColor(colorFill[i], false);
//				this.colorFill[i] = ColorToolsFX.getCachedColor(colorFill[i], ColorTools.alpha(colorFill[i]) != 0);
			}
		}
	}
	
	
	static double[][] removeNaNs(final double[] x, final double[] y) {
		double[] x2 = new double[x.length];
		double[] y2 = new double[y.length];
		int k = 0;
		for (int i = 0; i < x.length; i++) {
			double xx = x[i];
			double yy = y[i];
			if (Double.isNaN(xx) || Double.isNaN(yy))
				continue;
			x2[k] = xx;
			y2[k] = yy;
			k++;
		}
		if (k < x.length) {
			x2 = Arrays.copyOf(x2, k);
			y2 = Arrays.copyOf(y2, k);
		}
		return new double[][]{x2, y2};
	}
	
	
	private void calculateCorrelations() {
		double[][] denaned = removeNaNs(x, y);
		if (denaned[0].length > 0) {
			pearsonsCorrelation = new PearsonsCorrelation().correlation(denaned[0], denaned[1]);
			spearmansCorrelation = new SpearmansCorrelation().correlation(denaned[0], denaned[1]);
		}
		correlationsCalculated = true;
	}
	
	public double getPearsonsCorrelation() {
		if (!correlationsCalculated)
			calculateCorrelations();
		return pearsonsCorrelation;
	}

	
	public double getSpearmansCorrelation() {
		if (!correlationsCalculated)
			calculateCorrelations();
		return spearmansCorrelation;
	}

	
	public ScatterPlot(double[] x, double[] y, int[] colorDraw, int[] colorFill) {
		compute(x, y);
		setColors(colorDraw, colorFill);
	}
	
	
//	public static float[] toFloat(final double[] arr) {
//		float[] arr2 = new float[arr.length];
//		for (int i = 0; i < arr.length; i++)
//			arr2[i] = (float)arr[i];
//		return arr2;
//	}
	
	public static double[] toDouble(final float[] arr) {
		double[] arr2 = new double[arr.length];
		for (int i = 0; i < arr.length; i++)
			arr2[i] = arr[i];
		return arr2;
	}
	
	public void setLimitsX(final double minX, final double maxX) {
		this.minX = (float)minX;
		this.maxX = (float)maxX;
	}
	
	public void setLimitsY(final double minY, final double maxY) {
		this.minY = (float)minY;
		this.maxY = (float)maxY;
	}
	
	
	public double getMinX() {
		return minX;
	}

	public double getMinY() {
		return minY;
	}
	
	public double getMaxX() {
		return maxX;
	}

	public double getMaxY() {
		return maxY;
	}
	
	public double getMarkerSize() {
		return markerSize;
	}
	
	public void setMarkerSize(final double markerSize) {
		this.markerSize = markerSize;
	}
	
	public boolean getFillMarkers() {
		return fillMarkers;
	}
	
	public void setFillMarkers(final boolean doFill) {
		this.fillMarkers = doFill;
	}

	public void drawPlot(GraphicsContext g, Rectangle2D region) {
		drawPlot(g, region, 10000);
	}

	
	public void drawPlot(GraphicsContext g, Rectangle2D region, int maxPoints) {
		
		g.save();
		
		g.beginPath();
		g.moveTo(region.getMinX(), region.getMinY());
		g.lineTo(region.getMaxX(), region.getMinY());
		g.lineTo(region.getMaxX(), region.getMaxY());
		g.lineTo(region.getMinX(), region.getMaxY());
		g.closePath();
		g.clip();
		
//		int pad = 10;
		double scaleX = region.getWidth() / (maxX - minX);
		double scaleY = region.getHeight() / (maxY - minY);

		g.setLineWidth(1.5f);
		
//		g.setStroke(javafx.scene.paint.Color.GRAY);
//		g.strokeRect(region.getX(), region.getY(), region.getWidth(), region.getHeight());
		
		g.translate(region.getMinX(), region.getMinY());

//		g2d.drawLine(0, 0, 0, region.height);
//		g2d.drawLine(0, region.height, region.width, region.height);
		
		double increment;
		if (maxPoints < 0 || x.length <= maxPoints)
			increment = 1;
		else
			increment = (double)x.length / maxPoints;
		
		for (double i = 0; i < x.length; i += increment) {
			int ind = (int)i;
			double xx = x[ind];
			double yy = y[ind];
//			// Skip if out of range
//			if (xx < minX || xx > maxX || yy < minY || yy > maxY)
//				continue;
			
			double xo = (xx-minX)*scaleX-markerSize/2;
			double yo = region.getHeight() - (yy-minY)*scaleY-markerSize/2;
			
			Color cDraw = colorDraw == null ? null : colorDraw[ind];
			if (fillMarkers) {
				Color cFill = colorFill[ind] == null ? cDraw : colorFill[ind];
				if (cFill != null) {
					g.setFill(cFill);
					g.fillOval(xo, yo, markerSize, markerSize);
					// Don't need to draw if it would be the same color anyway
					if (cFill == cDraw)
						continue;
				}				
			}
			if (cDraw != null) {
				g.setStroke(cDraw);
				g.strokeOval(xo, yo, markerSize, markerSize);
			}
		}
		
		g.restore();
	}

	
	
//	public static void main(String[] args) {
//		
//		int n = 25;
//		final double[] x = new double[n];
//		final double[] y = new double[n];
//		for (int i = 0; i < n; i++) {
//			x[i] = Math.random() * 100;
//			y[i] = Math.random() * 100;
//		}
//		
//		JPanel panel = new JPanel() {
//			
//			/**
//			 * 
//			 */
//			private static final long serialVersionUID = 1L;
//			ScatterPlot painter = new ScatterPlot(x, y);
//			
//			public void paintComponent(Graphics g) {
//				painter.drawPlot(g, new Rectangle(10, 10, getWidth()-20, getHeight()-20));
//			}
//			
//		};
//		
//		panel.setPreferredSize(new Dimension(200, 200));
//		
//		JFrame frame = new JFrame("Scatterplot");
//		frame.add(panel);
//		frame.pack();
//		frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//
//		frame.setVisible(true);
//				
//	}
	
	
}