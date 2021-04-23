package qupath.lib.gui;

import java.awt.image.BufferedImage;
import java.util.Objects;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import qupath.lib.gui.tools.MeasurementMapper.ColorMapper;

/**
 * Canvas to show the range of a ColorMapper (i.e. look-up table).
 */
// TODO: Change the canvas in MeasurementMapPane to use this?
public class ColorMapperCanvas extends Canvas {
	
	private double height;
	private ObjectProperty<ColorMapper> colorMapperProperty;
	private Image image;
	
	/**
	 * Create a canvas that displays the range of the specified {@link ColorMapper}. 
	 * <p>
	 * A {@link NullPointerException} is thrown if {@code colorMapper} is null.
	 * 
	 * @param height
	 * @param colorMapper
	 */
	public ColorMapperCanvas(double height, ColorMapper colorMapper) {
		this.height = height;
		this.colorMapperProperty = new SimpleObjectProperty<>(Objects.requireNonNull(colorMapper));
		this.image = createColorMapImage(colorMapper);
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
	 * Set the {@link ColorMapper} to display to the one with the specified list index.
	 * @param colorMapper
	 */
	public void setColorMapper(ColorMapper colorMapper) {
		colorMapperProperty.set(colorMapper);
		image = createColorMapImage(colorMapper);
		updateColorMapImage(image);
	}
	
	/**
	 * Return the currently displayed {@link ColorMapper}.
	 * @return colorMapper
	 */
	public ColorMapper getColorMapper() {
		return colorMapperProperty.get();
	}
	
	/**
	 * Return the colorMapper property of this canvas.
	 * @return colorMapper property
	 */
	public ObjectProperty<ColorMapper> colorMapperProperty() {
		return colorMapperProperty;
	}
	
	/**
	 * Create an {@link Image} that shows the range of the {@code ColorMapper}.
	 * @param colorMapper
	 * @return image
	 */
	private static Image createColorMapImage(final ColorMapper colorMapper) {
		BufferedImage imgKey = new BufferedImage(255, 10, BufferedImage.TYPE_INT_ARGB);
		if (colorMapper != null) {
			for (int i = 0; i < imgKey.getWidth(); i++) {
				Integer rgb = colorMapper.getColor(i, 0, 254);
				for (int j = 0; j < imgKey.getHeight(); j++) {
					imgKey.setRGB(i, j, rgb);
				}
			}
		}
		Image img = SwingFXUtils.toFXImage(imgKey, null);
		return img;
	}
	
	/**
	 * Update an existing {@link Image} to show the range of the current {@link ColorMapper}.
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
