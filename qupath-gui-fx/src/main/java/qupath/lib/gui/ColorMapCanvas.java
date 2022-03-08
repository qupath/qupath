/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui;

import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.gui.tools.ColorToolsFX;

/**
 * Canvas to show the range of a ColorMap (i.e. look-up table).
 */
// TODO: Change the canvas in MeasurementMapPane to use this?
public class ColorMapCanvas extends Canvas {
	private double height;
	private ObjectProperty<ColorMap> colorMapProperty;
	private Image image;
	private Map<Double, Rectangle> recs;
	private Function<Double, String> fun;
	private Tooltip tooltip;
	
	/**
	 * Create a canvas that displays the range of the specified {@link ColorMap} with key tooltips.
	 * @param height
	 * @param colorMap
	 * @param fun function to map the 256 values of the color map to a displayable string
	 */
	public ColorMapCanvas(double height, ColorMap colorMap, Function<Double, String> fun) {
		this.height = height;
		this.colorMapProperty = new SimpleObjectProperty<>(Objects.requireNonNull(colorMap));
		this.recs = new HashMap<>();
		this.image = createColorMapImage(colorMap, recs);
		if (fun != null)
			installTooltip(fun);
	}
	
	private void installTooltip(Function<Double, String> fun) {
		this.fun = fun;
		this.tooltip = new Tooltip();
		tooltip.setShowDelay(Duration.ZERO);
		setOnMouseMoved(e -> {
			tooltip.setText(this.fun.apply(Math.floor(e.getX()/getWidth()*255)));
			tooltip.setGraphic(recs.get(Math.floor(e.getX()/getWidth()*255)));
		});
		Tooltip.install(this, tooltip);	
	}
	
	/**
	 * @param height
	 * @param colorMap
	 */
	public ColorMapCanvas(double height, ColorMap colorMap) {
		this(height, colorMap,  d ->"Value: " + d);
	}
	
	@Override
	public double minHeight(double width) {
	    return height;
	}

	@Override
	public double maxHeight(double width) {
	    return height;
	}

	@Override
	public double prefHeight(double width) {
	    return height;
	}
	
	@Override
	public double minWidth(double width) {
		return 0;
	}
	
	@Override
	public double maxWidth(double width) {
		return Double.MAX_VALUE;
	}
	
	@Override
	public boolean isResizable() {
		return true;
	}
	
	@Override
	public void resize(double width, double height)	{
	    super.setWidth(width);
	    super.setHeight(height);
	    updateColorMapImage(image);
	}
	
	/**
	 * Set the {@link ColorMap} to display to the one with the specified list index.
	 * @param ColorMap
	 */
	public void setColorMap(ColorMap ColorMap) {
		colorMapProperty.set(ColorMap);
		image = createColorMapImage(ColorMap, recs);
		updateColorMapImage(image);
	}
	
	/**
	 * Return the currently displayed {@link ColorMap}.
	 * @return ColorMap
	 */
	public ColorMap getColorMap() {
		return colorMapProperty.get();
	}
	
	/**
	 * Return the ColorMap property of this canvas.
	 * @return ColorMap property
	 */
	public ObjectProperty<ColorMap> colorMapProperty() {
		return colorMapProperty;
	}
	
	/**
	 * Set the function that will take a value between 0 and 255 (from the color map) and output a displayable string
	 * @param fun
	 */
	public void setTooltipFunction(Function<Double, String> fun) {
		this.fun = fun;
		
	}
	
	/**
	 * Create an {@link Image} that shows the range of the {@code ColorMap} and creates appropriate tooltips.
	 * @param colorMap
	 * @param recs 
	 * @return image
	 */
	private static Image createColorMapImage(final ColorMap colorMap, final Map<Double, Rectangle> recs) {
		BufferedImage imgKey = new BufferedImage(255, 10, BufferedImage.TYPE_INT_ARGB);
		if (colorMap != null) {
			for (int i = 0; i < imgKey.getWidth(); i++) {
				Integer rgb = colorMap.getColor(i, 0, 255);
				Rectangle rec = new Rectangle(50, 50);
				rec.setFill(ColorToolsFX.getCachedColor(rgb));
				recs.put((double)i, rec);
				for (int j = 0; j < imgKey.getHeight(); j++) {
					imgKey.setRGB(i, j, rgb);
				}
			}
		}
		Image img = SwingFXUtils.toFXImage(imgKey, null);
		return img;
	}
	
	/**
	 * Update an existing {@link Image} to show the range of the current {@link ColorMap}.
	 * @param image
	 */
	private void updateColorMapImage(final Image image) {
		GraphicsContext gc = getGraphicsContext2D();
		gc.clearRect(0, 0, getWidth(), getHeight());
		if (image != null)
			gc.drawImage(image,
					0, 0, image.getWidth(), image.getHeight(),
					0, 0, getWidth(), getHeight());
	}
}
