package qupath.lib.awt.color.model;

import java.awt.image.BandedSampleModel;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferFloat;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;

/**
 * Create a ColorModel, intended for displaying probability values.
 * 
 * The performance isn't necessarily great; so it makes sense to draw the associated image 
 * and cache the RGB result if it will be repainted regularly (e.g. as an overlay).
 * 
 * TODO: The actual implementation may change... it doesn't currently do all the checks it could/should...
 */
class ProbabilityColorModel extends ColorModel {
	
	private final static Logger logger = LoggerFactory.getLogger(ProbabilityColorModel.class);
		
	public static final int BACKGROUND_COLOR = ColorTools.makeRGBA(255, 253, 254, 0); // TODO: See PixelClassifierOutputChannel.TRANSPARENT;

	private static int[] DEFAULT_COLORS = new int[] {
			ColorTools.makeRGB(255, 0, 0),
			ColorTools.makeRGB(0, 255, 0),
			ColorTools.makeRGB(0, 0, 255),
			ColorTools.makeRGB(255, 0, 255),
			ColorTools.makeRGB(0, 255, 255),
			ColorTools.makeRGB(255, 255, 0)
	};
		
	private int nBands;
	private int bpp;
	private boolean residualBackground = false;
	private int backgroundChannel = -1;
	private float[] rScale, gScale, bScale;
	
	ProbabilityColorModel(final int bpp, int nBands, boolean residualBackground, int...colors) {
		super(bpp * nBands);
		this.bpp = bpp;
		this.nBands = nBands;
		this.residualBackground = residualBackground;
		
		// Default values for colors - use as many as required
		if (colors.length == 0)
			colors = DEFAULT_COLORS;
		
		rScale = new float[nBands];
		gScale = new float[nBands];
		bScale = new float[nBands];
		for (int i = 0; i < Math.min(colors.length, nBands); i++) {
			int val = colors[i];
			if (val == BACKGROUND_COLOR) {
				backgroundChannel = i;
				continue;
			}
			int r = ColorTools.red(val);
			int g = ColorTools.green(val);
			int b = ColorTools.blue(val);
			rScale[i] = r / 255.0f;
			gScale[i] = g / 255.0f;
			bScale[i] = b / 255.0f;
		}
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
	public int getRed(Object pixel) {
		if (pixel instanceof byte[])
			return getRed((byte[])pixel);
		if (pixel instanceof float[])
			return getRed((float[])pixel);
		if (pixel instanceof short[])
			return getRed((short[])pixel);
		return 0;
	}

	@Override
	public int getGreen(Object pixel) {
		if (pixel instanceof byte[])
			return getGreen((byte[])pixel);
		if (pixel instanceof float[])
			return getGreen((float[])pixel);
		if (pixel instanceof short[])
			return getGreen((short[])pixel);
		return 0;
	}

	@Override
	public int getBlue(Object pixel) {
		if (pixel instanceof byte[])
			return getBlue((byte[])pixel);
		if (pixel instanceof float[])
			return getBlue((float[])pixel);
		if (pixel instanceof short[])
			return getBlue((short[])pixel);
		return 0;
	}

	@Override
	public int getAlpha(Object pixel) {
		if (pixel instanceof byte[])
			return getAlpha((byte[])pixel);
		if (pixel instanceof float[])
			return getAlpha((float[])pixel);
		if (pixel instanceof short[])
			return getAlpha((short[])pixel);
		return 255;
	}
	
	public int getRed(byte[] pixel) {
		return scaledPixelColor(pixel, rScale);
	}

	public int getGreen(byte[] pixel) {
		return scaledPixelColor(pixel, gScale);
	}

	public int getBlue(byte[] pixel) {
		return scaledPixelColor(pixel, bScale);
	}

	public int getAlpha(byte[] pixel) {
		if (backgroundChannel >= 0)
			return (int)(255 * (1f - byteToFloat(pixel[backgroundChannel])));
		if (residualBackground) {
			float sum = 0;
			for (byte p : pixel)
				sum += byteToFloat(p);
			return (int)(255 * clipFloat(sum));
		}
		return 255;
	}
	
	public int getRed(float[] pixel) {
		return scaledPixelColor(pixel, rScale);
	}

	public int getGreen(float[] pixel) {
		return scaledPixelColor(pixel, gScale);
	}

	public int getBlue(float[] pixel) {
		return scaledPixelColor(pixel, bScale);
	}
	
	
	public int getRed(short[] pixel) {
		return scaledPixelColorU16(pixel, rScale);
	}

	public int getGreen(short[] pixel) {
		return scaledPixelColorU16(pixel, gScale);
	}

	public int getBlue(short[] pixel) {
		return scaledPixelColorU16(pixel, bScale);
	}
	
	
	
	public int scaledPixelColor(float[] pixel, float[] scale) {
		float sum = 0;
		for (int i = 0; i < pixel.length; i++) {
			if (backgroundChannel != i)
				sum += pixel[i] * scale[i];
		}
		return (int)(255 * clipFloat(sum));
	}
	
	
	public int scaledPixelColor(byte[] pixel, float[] scale) {
		float sum = 0;
		for (int i = 0; i < pixel.length; i++) {
			if (backgroundChannel != i)
				sum += byteToFloat(pixel[i]) * scale[i];
		}
		return (int)(255 * clipFloat(sum));
	}
	
	
	public int scaledPixelColorU16(short[] pixel, float[] scale) {
		float sum = 0;
		for (int i = 0; i < pixel.length; i++) {
			if (backgroundChannel != i)
				sum += ushortToFloat(pixel[i]) * scale[i];
		}
		return (int)(255 * clipFloat(sum));
	}
	

	public int getAlpha(float[] pixel) {
		if (backgroundChannel >= 0)
			return (int)(255 * clipFloat(1f - pixel[backgroundChannel]));
		if (residualBackground) {
			float sum = 0;
			for (float p : pixel)
				sum += clipFloat(p);
			return (int)(255 * clipFloat(sum));
		}
		return 255;
	}
	
	private static float clipFloat(float f) {
		if (f < 0f)
			return 0f;
		if (f > 1f)
			return 1f;
		return f;
	}
	
	private static float ushortToFloat(short s) {
		int val = s & 0xFFFF;
		return val / 65535f;
	}
	
	private static float byteToFloat(byte b) {
		int val = b & 0xFF;
		if (val > 255)
			return 1f;
		return val / 255f;
	}
	
	@Override
	public boolean isCompatibleRaster(Raster raster) {
		// We accept byte & float images
		int transferType = raster.getTransferType();
		return transferType == DataBuffer.TYPE_BYTE ||
				transferType == DataBuffer.TYPE_USHORT ||
				transferType == DataBuffer.TYPE_FLOAT;
	}
	
	@Override
	public WritableRaster createCompatibleWritableRaster(int w, int h) {
		if (bpp == 8)
			return WritableRaster.createBandedRaster(DataBuffer.TYPE_BYTE, w, h, nBands, null);
		if (bpp == 16)
			return WritableRaster.createBandedRaster(DataBuffer.TYPE_USHORT, w, h, nBands, null);
		if (bpp == 32)
			return WritableRaster.createWritableRaster(
					new BandedSampleModel(DataBuffer.TYPE_FLOAT, w, h, nBands),
					new DataBufferFloat(w*h, nBands), null);
//			return WritableRaster.createBandedRaster(DataBuffer.TYPE_FLOAT, w, h, nBands, null);
		return super.createCompatibleWritableRaster(w, h);
	}
	
	@Override
	public ColorModel coerceData(WritableRaster raster, boolean isAlphaPremultiplied) {
		logger.warn("Call to coerce data for {} (isAlphaPremultiplied = {})", raster, isAlphaPremultiplied);
		return null;
	}
	
	
}