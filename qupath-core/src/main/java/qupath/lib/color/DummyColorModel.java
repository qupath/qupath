package qupath.lib.color;

import java.awt.image.ColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

/**
 * A { @ColorModel } for times when you don't really want a { @ColorModel }.
 * <p>
 * Just a placeholder, pixels will be displayed as black, regardless of values.
 * 
 * @author Pete Bankhead
 *
 */
class DummyColorModel extends ColorModel {

	DummyColorModel(final int nBits) {
		super(nBits);
	}

	@Override
	public int getRed(int pixel) {
		return 0;
	}

	@Override
	public int getGreen(int pixel) {
		return 0;
	}

	@Override
	public int getBlue(int pixel) {
		return 0;
	}

	@Override
	public int getAlpha(int pixel) {
		return 0;
	}

	@Override
	public boolean isCompatibleRaster(Raster raster) {
		// We accept everything...
		return true;
	}

	@Override
	public ColorModel coerceData(WritableRaster raster, boolean isAlphaPremultiplied) {
		// Don't do anything
		return this;
	}

}