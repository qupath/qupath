package qupath.lib.color;

import java.awt.image.BandedSampleModel;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferShort;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.images.servers.PixelType;

/**
 * Create a ColorModel. This was originally intended for displaying probability values.
 * <p>
 * The performance isn't necessarily great; so it makes sense to draw the associated image 
 * and cache the RGB result if it will be repainted regularly (e.g. as an overlay).
 * <p>
 * TODO: The actual implementation may change...
 */
class DefaultColorModel extends ColorModel {
	
	private final static Logger logger = LoggerFactory.getLogger(DefaultColorModel.class);
		
	public static final int BACKGROUND_COLOR = ColorTools.makeRGBA(255, 253, 254, 0); // TODO: See PixelClassifierOutputChannel.TRANSPARENT;

	private static int[] DEFAULT_COLORS = new int[] {
			ColorTools.makeRGB(255, 0, 0),
			ColorTools.makeRGB(0, 255, 0),
			ColorTools.makeRGB(0, 0, 255),
			ColorTools.makeRGB(255, 0, 255),
			ColorTools.makeRGB(0, 255, 255),
			ColorTools.makeRGB(255, 255, 0)
	};
		
	private PixelType pixelType;
	private int nBands;
	private boolean residualBackground = false;
	private int backgroundChannel = -1;
	private float[] rScale, gScale, bScale;
	
	DefaultColorModel(final PixelType pixelType, int nBands, boolean residualBackground, int...colors) {
		super(pixelType.getBitsPerPixel() * nBands);
		this.pixelType = pixelType;
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
			return getRedByte((byte[])pixel);
		if (pixel instanceof float[])
			return getRedFloat((float[])pixel);
		if (pixel instanceof short[])
			return getRedShort((short[])pixel);
		if (pixel instanceof int[])
			return getRedInt((int[])pixel);
		if (pixel instanceof double[])
			return getRedDouble((double[])pixel);
		return 0;
	}

	@Override
	public int getGreen(Object pixel) {
		if (pixel instanceof byte[])
			return getGreenByte((byte[])pixel);
		if (pixel instanceof float[])
			return getGreenFloat((float[])pixel);
		if (pixel instanceof short[])
			return getGreenShort((short[])pixel);
		if (pixel instanceof int[])
			return getGreenInt((int[])pixel);
		if (pixel instanceof double[])
			return getGreenDouble((double[])pixel);
		return 0;
	}

	@Override
	public int getBlue(Object pixel) {
		if (pixel instanceof byte[])
			return getBlueByte((byte[])pixel);
		if (pixel instanceof float[])
			return getBlueFloat((float[])pixel);
		if (pixel instanceof short[])
			return getBlueShort((short[])pixel);
		if (pixel instanceof int[])
			return getBlueInt((int[])pixel);
		if (pixel instanceof double[])
			return getBlueDouble((double[])pixel);
		return 0;
	}

	@Override
	public int getAlpha(Object pixel) {
		if (pixel instanceof byte[])
			return getAlphaByte((byte[])pixel);
		if (pixel instanceof float[])
			return getAlphaFloat((float[])pixel);
		if (pixel instanceof short[])
			return getAlphaShort((short[])pixel);
		if (pixel instanceof int[])
			return getAlphaInt((int[])pixel);
		if (pixel instanceof double[])
			return getAlphaDouble((double[])pixel);
		return 255;
	}
	
	private int getRedByte(byte[] pixel) {
		return scaledPixelColor(pixel, rScale);
	}

	private int getGreenByte(byte[] pixel) {
		return scaledPixelColor(pixel, gScale);
	}

	private int getBlueByte(byte[] pixel) {
		return scaledPixelColor(pixel, bScale);
	}

	private int getAlphaByte(byte[] pixel) {
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
	
	private int getRedFloat(float[] pixel) {
		return scaledPixelColor(pixel, rScale);
	}

	private int getGreenFloat(float[] pixel) {
		return scaledPixelColor(pixel, gScale);
	}

	private int getBlueFloat(float[] pixel) {
		return scaledPixelColor(pixel, bScale);
	}
	
	private int getAlphaFloat(float[] pixel) {
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
	
	
	private int getRedDouble(double[] pixel) {
		return scaledPixelColor(pixel, rScale);
	}

	private int getGreenDouble(double[] pixel) {
		return scaledPixelColor(pixel, gScale);
	}

	private int getBlueDouble(double[] pixel) {
		return scaledPixelColor(pixel, bScale);
	}
	
	private int getAlphaDouble(double[] pixel) {
		if (backgroundChannel >= 0)
			return (int)(255 * (1f - pixel[backgroundChannel]));
		if (residualBackground) {
			float sum = 0;
			for (double p : pixel)
				sum += clipFloat(p);
			return (int)(255 * clipFloat(sum));
		}
		return 255;
	}
	
	
	private int getRedShort(short[] pixel) {
		if (pixelType == PixelType.UINT16)
			return scaledPixelColorU16(pixel, rScale);
		else
			return scaledPixelColorSigned16(pixel, rScale);
	}

	private int getGreenShort(short[] pixel) {
		if (pixelType == PixelType.UINT16)
			return scaledPixelColorU16(pixel, gScale);
		else
			return scaledPixelColorSigned16(pixel, gScale);
	}

	private int getBlueShort(short[] pixel) {
		if (pixelType == PixelType.UINT16)
			return scaledPixelColorU16(pixel, bScale);
		else
			return scaledPixelColorSigned16(pixel, bScale);
	}
	
	private int getAlphaShort(short[] pixel) {
		if (backgroundChannel >= 0)
			return (int)(255 * (1f - ushortToFloat(pixel[backgroundChannel])));
		if (residualBackground) {
			float sum = 0;
			if (pixelType == PixelType.UINT16) {
				for (short p : pixel)
					sum += ushortToFloat(p);
			} else {
				for (short p : pixel)
					sum += clipFloat(shortToFloat(p));				
			}
			return (int)(255 * clipFloat(sum));
		}
		return 255;
	}
	
	
	private int getRedInt(int[] pixel) {
		return scaledPixelColorSigned32(pixel, rScale);
	}

	private int getGreenInt(int[] pixel) {
		return scaledPixelColorSigned32(pixel, gScale);
	}

	private int getBlueInt(int[] pixel) {
		return scaledPixelColorSigned32(pixel, bScale);
	}

	private int getAlphaInt(int[] pixel) {
		if (backgroundChannel >= 0)
			return (int)(255 * (1f - intToFloat(pixel[backgroundChannel])));
		if (residualBackground) {
			float sum = 0;
			for (int p : pixel)
				sum += clipFloat(intToFloat(p));
			return (int)(255 * clipFloat(sum));
		}
		return 255;
	}
	
	
	private int scaledPixelColor(double[] pixel, float[] scale) {
		double sum = 0;
		for (int i = 0; i < pixel.length; i++) {
			if (backgroundChannel != i)
				sum += pixel[i] * scale[i];
		}
		return (int)(255 * clipFloat((float)sum));
	}
	
	private int scaledPixelColor(float[] pixel, float[] scale) {
		float sum = 0;
		for (int i = 0; i < pixel.length; i++) {
			if (backgroundChannel != i)
				sum += pixel[i] * scale[i];
		}
		return (int)(255 * clipFloat(sum));
	}
	
	private int scaledPixelColor(byte[] pixel, float[] scale) {
		float sum = 0;
		for (int i = 0; i < pixel.length; i++) {
			if (backgroundChannel != i)
				sum += byteToFloat(pixel[i]) * scale[i];
		}
		return (int)(255 * clipFloat(sum));
	}
	
	private int scaledPixelColorSigned16(short[] pixel, float[] scale) {
		float sum = 0;
		for (int i = 0; i < pixel.length; i++) {
			if (backgroundChannel != i)
				sum += shortToFloat(pixel[i]) * scale[i];
		}
		return (int)(255 * clipFloat(sum));
	}
	
	private int scaledPixelColorSigned32(int[] pixel, float[] scale) {
		float sum = 0;
		for (int i = 0; i < pixel.length; i++) {
			if (backgroundChannel != i)
				sum += intToFloat(pixel[i]) * scale[i];
		}
		return (int)(255 * clipFloat(sum));
	}
	
	private int scaledPixelColorU16(short[] pixel, float[] scale) {
		float sum = 0;
		for (int i = 0; i < pixel.length; i++) {
			if (backgroundChannel != i)
				sum += ushortToFloat(pixel[i]) * scale[i];
		}
		return (int)(255 * clipFloat(sum));
	}
	
	private static float clipFloat(double f) {
		if (f < 0f)
			return 0f;
		if (f > 1f)
			return 1f;
		return (float)f;
	}
	
	private static float clipFloat(float f) {
		if (f < 0f)
			return 0f;
		if (f > 1f)
			return 1f;
		return f;
	}
	
	private static float intToFloat(int s) {
		return (float)((double)s / Integer.MAX_VALUE);
	}
	
	private static float shortToFloat(short s) {
		return (float)s / Short.MAX_VALUE;
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
				transferType == DataBuffer.TYPE_INT ||
				transferType == DataBuffer.TYPE_SHORT ||
				transferType == DataBuffer.TYPE_FLOAT ||
				transferType == DataBuffer.TYPE_DOUBLE;
	}
	
	@Override
	public WritableRaster createCompatibleWritableRaster(int w, int h) {
		switch(pixelType) {
		case FLOAT32:
			return WritableRaster.createWritableRaster(
					new BandedSampleModel(DataBuffer.TYPE_FLOAT, w, h, nBands),
					new DataBufferFloat(w*h, nBands), null);
		case FLOAT64:
			return WritableRaster.createWritableRaster(
					new BandedSampleModel(DataBuffer.TYPE_DOUBLE, w, h, nBands),
					new DataBufferDouble(w*h, nBands), null);
		case INT16:
			return WritableRaster.createWritableRaster(
					new BandedSampleModel(DataBuffer.TYPE_SHORT, w, h, nBands),
					new DataBufferShort(w*h, nBands), null);
		case INT32:
			return WritableRaster.createBandedRaster(DataBuffer.TYPE_INT, w, h, nBands, null);
		case UINT16:
			return WritableRaster.createBandedRaster(DataBuffer.TYPE_USHORT, w, h, nBands, null);
		case UINT8:
			return WritableRaster.createBandedRaster(DataBuffer.TYPE_BYTE, w, h, nBands, null);
		case INT8:
		case UINT32:
		default:
			try {
				return super.createCompatibleWritableRaster(w, h);
			} catch (Exception e) {
				throw new UnsupportedOperationException("Unsupported pixel type " + pixelType);
			}
		}
	}
	
	@Override
	public ColorModel coerceData(WritableRaster raster, boolean isAlphaPremultiplied) {
		logger.warn("Unsupported call to coerce data for {} (isAlphaPremultiplied = {})", raster, isAlphaPremultiplied);
		return null;
	}
	
	
}