/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.color;

import java.awt.image.BandedSampleModel;
import java.awt.image.ColorModel;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferShort;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.DoubleToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.classes.PathClassTools;

/**
 * Factory methods to help create ColorModels for use with BufferedImages.
 * 
 * @author Pete Bankhead
 *
 */
public final class ColorModelFactory {
	
	private static final Logger logger = LoggerFactory.getLogger(ColorModelFactory.class);
	
//	private static Map<Map<Integer, PathClass>, IndexColorModel> classificationModels = Collections.synchronizedMap(new HashMap<>());

	private static Map<List<ImageChannel>, ColorModel> probabilityModels8 = Collections.synchronizedMap(new HashMap<>());
	private static Map<List<ImageChannel>, ColorModel> probabilityModels32 = Collections.synchronizedMap(new HashMap<>());

	
	private ColorModelFactory() {
		throw new AssertionError();
	}
	
	/**
	 * Get a ColorModel suitable for showing output pixel classifications, using an 8-bit or 16-bit labeled image.
	 * 
	 * @param channels
	 * @return
	 */
    public static IndexColorModel getIndexedClassificationColorModel(Map<Integer, PathClass> channels) {
    	var stats = channels.keySet().stream().mapToInt(c -> c).summaryStatistics();
    	if (stats.getMin() < 0)
    		throw new IllegalArgumentException("Minimum label must be >= 0");
    	int length = stats.getMax() + 1;
    	
        int[] cmap = new int[length];
        
        for (var entry: channels.entrySet()) {
    		var pathClass = entry.getValue();
    		if (pathClass == null || pathClass == PathClass.NULL_CLASS) {
    			cmap[entry.getKey()] = ColorTools.packARGB(0, 255, 255, 255);
    		} else if (PathClassTools.isIgnoredClass(entry.getValue())) {
        		var color = pathClass == null ? 0 : pathClass.getColor();
        		int alpha = 192;
        		if (pathClass == PathClass.StandardPathClasses.IGNORE)
        			alpha = 32;
            	cmap[entry.getKey()] = ColorTools.packARGB(alpha, ColorTools.red(color), ColorTools.green(color), ColorTools.blue(color));
        	} else
        		cmap[entry.getKey()] = entry.getValue().getColor();
        }
        if (cmap.length <= 256)
            return new IndexColorModel(8, length, cmap, 0, true, -1, DataBuffer.TYPE_BYTE);    		
        else if (cmap.length <= 65536)
            return new IndexColorModel(16, length, cmap, 0, true, -1, DataBuffer.TYPE_USHORT);
        else
        	throw new IllegalArgumentException("Only 65536 possible classifications supported!");
    }
    
    /**
     * Create an indexed colormap for a labelled (indexed color) image.
     * @param labelColors map with integer labels as keys and packed (A)RGB colors as values.
     * @param includeAlpha if true, allow alpha values to be included in the colormap
     * @return
     */
    public static ColorModel createIndexedColorModel(Map<Integer, Integer> labelColors, boolean includeAlpha) {
    	var stats = labelColors.keySet().stream().mapToInt(c -> c).summaryStatistics();
    	if (stats.getMin() < 0)
    		throw new IllegalArgumentException("Minimum label must be >= 0");
    	int length = stats.getMax() + 1;
    	
        int[] cmap = new int[length];
        
        for (var entry: labelColors.entrySet()) {
        	Integer value = entry.getValue();
        	if (value == null) {
        		logger.warn("No color specified for index {} - using default gray", entry.getKey());
        		cmap[entry.getKey()] = includeAlpha ? ColorTools.packARGB(127, 127, 127, 127) : ColorTools.packRGB(127, 127, 127);
        	} else
        		cmap[entry.getKey()] = entry.getValue();
        }
        if (cmap.length <= 256)
            return new IndexColorModel(8, length, cmap, 0, includeAlpha, -1, DataBuffer.TYPE_BYTE);    		
        if (cmap.length <= 65536)
        	return new IndexColorModel(16, length, cmap, 0, includeAlpha, -1, DataBuffer.TYPE_USHORT);
    	throw new IllegalArgumentException("Only 65536 possible labels supported!");
    }
    
    
    /**
     * Create an 8-bit {@link IndexColorModel} from a {@link ColorMap}.
     * @param map
     * @return
     */
    public static IndexColorModel createIndexedColorModel8bit(ColorMap map) {
    	return createIndexedColorModel8bit(map, -1);
    }
    
    /**
     * Create an 8-bit {@link IndexColorModel} from a {@link ColorMap}, allowing for a transparent pixel to be set (e.g. 0).
     * @param map
     * @param transparentPixel 
     * @return
     */
    public static IndexColorModel createIndexedColorModel8bit(ColorMap map, int transparentPixel) {
    	Objects.requireNonNull(map);
    	return new IndexColorModel(8, 256, ColorMaps.getColors(map, 256, false), 0, false, transparentPixel, DataBuffer.TYPE_BYTE);
    }
    
    /**
     * Create a color model from a {@link ColorMap}.
     *  This is useful for heatmaps/density maps where lower values should be transparent.
     * @param pixelType
     * @param map
     * @param band 
     * @return
     */
    public static ColorModel createColorModel(PixelType pixelType, ColorMap map, int band) {
    	return createColorModel(pixelType, map, band, 0, pixelType.isFloatingPoint() ? 1.0 : pixelType.getUpperBound().doubleValue(), -1, null);
    }
    
    /**
     * Create a color model from a {@link ColorMap}, scaled within a defined range and with an optional additional alpha channel used to 
     * determine opacity. This is useful for heatmaps/density maps where lower values should be transparent.
     * @param pixelType
     * @param map
     * @param band the band of the image that defines the values used to index the color map (usually 0 for a single-channel image)
     * @param min
     * @param max
     * @param alphaChannel
     * @param alphaFun
     * @return
     */
    public static ColorModel createColorModel(PixelType pixelType, ColorMap map, int band, double min, double max, int alphaChannel, DoubleToIntFunction alphaFun) {
    	Objects.requireNonNull(map);
    	if (alphaFun == null && alphaChannel >= 0)
    		alphaFun = createLinearFunction(pixelType);
    	return new ColorMapModel(pixelType, map, band, min, max, alphaChannel, alphaFun);
    }
    
    /**
     * Create a linear function for a specific pixel type, which can be used to determine a suitable alpha value for an image 
     * that should have opacity based upon pixel values.
     * @param type
     * @return
     * @see #createColorModel(PixelType, ColorMap, int, double, double, int, DoubleToIntFunction)
     */
    public static DoubleToIntFunction createLinearFunction(PixelType type) {
    	return createLinearFunction(0, type.isFloatingPoint() ? 1.0 : type.getUpperBound().doubleValue());
    }

    
    /**
     * Create a linear function between a given range, which can be used to determine a suitable alpha value for an image 
     * that should have opacity based upon pixel values.
     * @param min
     * @param max
     * @return
     * @see #createColorModel(PixelType, ColorMap, int, double, double, int, DoubleToIntFunction)
     */
    public static DoubleToIntFunction createLinearFunction(double min, double max) {
    	return d -> (int)GeneralTools.clipValue(Math.round(255 * (d - min) / (max - min)), 0, 255);
    }
    
    /**
     * Create a gamma function for a specific pixel type, which can be used to determine a suitable alpha value for an image 
     * that should have opacity based upon pixel values.
     * @param gamma
     * @param type
     * @return
     * @see #createColorModel(PixelType, ColorMap, int, double, double, int, DoubleToIntFunction)
     */
    public static DoubleToIntFunction createGammaFunction(double gamma, PixelType type) {
    	return createGammaFunction(gamma, 0, type.isFloatingPoint() ? 1.0 : type.getUpperBound().doubleValue());
    }
    
    /**
     * Create a gamma function between a given range, which can be used to determine a suitable alpha value for an image 
     * that should have opacity based upon pixel values.
     * @param gamma
     * @param min
     * @param max
     * @return
     * @see #createColorModel(PixelType, ColorMap, int, double, double, int, DoubleToIntFunction)
     */
    public static DoubleToIntFunction createGammaFunction(double gamma, double min, double max) {
    	return d -> gamma(d, min, max, gamma);
    }
    
    private static int gamma(double val, double min, double max, double gamma) {
    	val -= min;
    	val /= Math.abs(max - min);
    	if (gamma != 1)
    		val = Math.pow(val, gamma);
    	val = GeneralTools.clipValue(val, 0, 1);
    	return (int)Math.round(val * 255);
    }
    
    
    /**
     * Get a ColorModel suitable for showing 8-bit pseudo-probabilities for multiple channels.
     * <p>
     * The range of values is assumed to be 0-255, treated as probabilities rescaled from 0-1.
     * A cached model will be retrieved where possible, rather than generating a new one.
     * 
     * @param channels
     * @return
     */
    public static ColorModel getProbabilityColorModel8Bit(List<ImageChannel> channels) {
    	var map = probabilityModels8.get(channels);
    	if (map == null) {
            int[] colors = channels.stream().mapToInt(c -> c.getColor()).toArray();
    		map = ColorModelFactory.createColorModel(PixelType.UINT8, channels.size(), channels.size() == 1, colors);
    		probabilityModels8.put(new ArrayList<>(channels), map);
    	}
    	return map;
    }
    
    
    /**
     * Get a ColorModel suitable for showing 32-bit (pseudo-)probabilities for multiple channels.
     * <p>
     * The range of values is assumed to be 0-1.
     * A cached model will be retrieved where possible, rather than generating a new one.
     * 
     * @param channels
     * @return
     */
    public static ColorModel getProbabilityColorModel32Bit(List<ImageChannel> channels) {
    	var map = probabilityModels32.get(channels);
    	if (map == null) {
            int[] colors = channels.stream().mapToInt(c -> c.getColor()).toArray();
    		map = ColorModelFactory.createColorModel(PixelType.FLOAT32, channels.size(), channels.size() == 1, colors);
    		probabilityModels32.put(new ArrayList<>(channels), map);
    	}
    	return map;
    }

	/**
	 * Get a dummy ColorModel instance.
	 * <p>
	 * This isn't very highly recommended; it is here to help in cases where a {@code BufferedImage} 
	 * is required, but really only a raster is needed.
	 * The actual color used is undefined (but it will likely be black).
	 * 
	 * @param bpp
	 * @return
	 */
	public static ColorModel getDummyColorModel(int bpp) {
		return new DummyColorModel(bpp);
	}

	/**
	 * Create a new ColorModel that can be used to display an image where pixels per channel reflect 
	 * probabilities, either as float or byte.
	 * <p>
	 * It is assumed that the probabilities sum to 1; if they sum to less than 1, <code>alphaResidual</code> 
	 * can be used to make 'unknown' pixels transparent/translucent rather than black.
	 * 
	 * @param type type for individual pixels
	 * @param nChannels Number of color channels.
	 * @param alphaResidual If true, the alpha value is scaled according to the sum of the other probabilities.
	 *                      This makes pixels with low probabilities for all other channels appear transparent.
	 * @param colors Packed RGB representations of each color, in order.  A single channel can also be set to <code>BACKGROUND_COLOR</code>,
	 * 						which indicates that it is used directly to control the alpha values, overriding <code>alphaResidual</code>.
	 * @return
	 */
	public static ColorModel createColorModel(final PixelType type, final int nChannels, final boolean alphaResidual, final int...colors) {
		return new DefaultColorModel(type, nChannels, alphaResidual, colors);
	}
	
	/**
	 * Create a ColorModel for displaying an image with the specified channel colors.
	 * Note that this currently does not provide any means to change the display range (e.g. for brightness/contrast)
	 * and therefore may not be sufficient on its own for generating a satisfactory (A)RGB image.
	 * 
	 * @param type
	 * @param channels
	 * @return
	 */
	public static ColorModel createColorModel(final PixelType type, final List<ImageChannel> channels) {
		return new DefaultColorModel(type, channels.size(), false, channels.stream().mapToInt(c -> {
			Integer color = c.getColor();
			if (color == null)
				color = ColorTools.packRGB(255, 255, 255);
			return color;
		}).toArray());
	}
    
	
	
	static class ColorMapModel extends DefaultAbstractColorModel {
		
		private ColorMap map;
		private double min, max;
		private int band = 0;
		
		private int nBits;
		private boolean hasAlphaChannel;
		private boolean isSigned;
		private int alphaChannel;
		private DoubleToIntFunction alphaFun;

		public ColorMapModel(PixelType pixelType, ColorMap map, int band, double min, double max, int alphaChannel, DoubleToIntFunction alphaFun) {
			super(pixelType, Math.max(1, alphaChannel + 1));
			this.band = band;
			this.nBits = pixelType.getBitsPerPixel();
			this.isSigned = pixelType.isSignedInteger();
			this.map = map;
			this.min = min;
			this.max = max;
			this.hasAlphaChannel = alphaChannel >= 0;
			this.alphaChannel = alphaChannel;
			this.alphaFun = alphaFun;
		}

		@Override
		public int getRed(int pixel) {
			// TODO: Check about applying band here!
			return ColorTools.red(map.getColor(pixel, min, max));
		}

		@Override
		public int getGreen(int pixel) {
			return ColorTools.green(map.getColor(pixel, min, max));
		}

		@Override
		public int getBlue(int pixel) {
			return ColorTools.blue(map.getColor(pixel, min, max));
		}

		@Override
		public int getAlpha(int pixel) {
			if (hasAlphaChannel)
				pixel = pixel << (nBits * alphaChannel);
			return alphaFun == null ? 255 : alphaFun.applyAsInt(pixel);
		}
		
		private double extractByteValue(byte b) {
			return isSigned ? (double)b : (double)(b & 0xFF);
		}

		private double extractShortValue(short s) {
			return isSigned ? (double)s : (double)(s & 0xFFFF);
		}

		@Override
		protected int getRedByte(byte[] pixel) {
			return getRed(extractByteValue(pixel[band]));
		}

		@Override
		protected int getGreenByte(byte[] pixel) {
			return getGreen(extractByteValue(pixel[band]));
		}

		@Override
		protected int getBlueByte(byte[] pixel) {
			return getBlue(extractByteValue(pixel[band]));
		}

		@Override
		protected int getAlphaByte(byte[] pixel) {
			return getAlpha(extractByteValue(pixel[hasAlphaChannel ? alphaChannel : 0]));
		}

		@Override
		protected int getRedFloat(float[] pixel) {
			return getRed(pixel[0]);
		}

		@Override
		protected int getGreenFloat(float[] pixel) {
			return getGreen(pixel[0]);
		}

		@Override
		protected int getBlueFloat(float[] pixel) {
			return getBlue(pixel[0]);
		}

		@Override
		protected int getAlphaFloat(float[] pixel) {
			return hasAlphaChannel ? getAlpha(pixel[alphaChannel]) : getAlpha(pixel[0]);
		}

		@Override
		protected int getRedDouble(double[] pixel) {
			return getRed(pixel[0]);
		}

		@Override
		protected int getGreenDouble(double[] pixel) {
			return getGreen(pixel[0]);
		}

		@Override
		protected int getBlueDouble(double[] pixel) {
			return getBlue(pixel[0]);
		}

		@Override
		protected int getAlphaDouble(double[] pixel) {
			return hasAlphaChannel ? getAlpha(pixel[alphaChannel]) : getAlpha(pixel[0]);
		}

		@Override
		protected int getRedShort(short[] pixel) {
			return getRed(extractShortValue(pixel[band]));
		}

		@Override
		protected int getGreenShort(short[] pixel) {
			return getGreen(extractShortValue(pixel[band]));
		}

		@Override
		protected int getBlueShort(short[] pixel) {
			return getBlue(extractShortValue(pixel[band]));
		}

		@Override
		protected int getAlphaShort(short[] pixel) {
			return getAlpha(extractShortValue(pixel[hasAlphaChannel ? alphaChannel : 0]));
		}

		@Override
		protected int getRedInt(int[] pixel) {
			return getRed(pixel[band]);
		}

		@Override
		protected int getGreenInt(int[] pixel) {
			return getGreen(pixel[band]);
		}

		@Override
		protected int getBlueInt(int[] pixel) {
			return getBlue(pixel[band]);
		}

		@Override
		protected int getAlphaInt(int[] pixel) {
			return hasAlphaChannel ? getAlpha(pixel[alphaChannel]) : getAlpha(pixel[0]);
		}
		
		public int getRed(double pixel) {
			return ColorTools.red(map.getColor(pixel, min, max));
		}

		public int getGreen(double pixel) {
			return ColorTools.green(map.getColor(pixel, min, max));
		}

		public int getBlue(double pixel) {
			return ColorTools.blue(map.getColor(pixel, min, max));
		}

		public int getAlpha(double pixel) {
			return alphaFun == null ? 255 : alphaFun.applyAsInt(pixel);
		}
		
	}
	
	
	
	/**
	 * Abstract color model that handles all types of transfer.
	 */
	abstract static class DefaultAbstractColorModel extends ColorModel {
		
		private PixelType pixelType;
		private int nBands;
		
		DefaultAbstractColorModel(final PixelType pixelType, int nBands) {
			super(pixelType.getBitsPerPixel() * nBands);
			this.pixelType = pixelType;
			this.nBands = nBands;
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
		
		protected abstract int getRedByte(byte[] pixel);

		protected abstract int getGreenByte(byte[] pixel);

		protected abstract int getBlueByte(byte[] pixel);

		protected abstract int getAlphaByte(byte[] pixel);
		
		protected abstract int getRedFloat(float[] pixel);

		protected abstract int getGreenFloat(float[] pixel);

		protected abstract int getBlueFloat(float[] pixel);
		
		protected abstract int getAlphaFloat(float[] pixel);
		
		protected abstract int getRedDouble(double[] pixel);

		protected abstract int getGreenDouble(double[] pixel);

		protected abstract int getBlueDouble(double[] pixel);
		
		protected abstract int getAlphaDouble(double[] pixel);
		
		protected abstract int getRedShort(short[] pixel);

		protected abstract int getGreenShort(short[] pixel);

		protected abstract int getBlueShort(short[] pixel);
		
		protected abstract int getAlphaShort(short[] pixel);
		
		protected abstract int getRedInt(int[] pixel);

		protected abstract int getGreenInt(int[] pixel);

		protected abstract int getBlueInt(int[] pixel);

		protected abstract int getAlphaInt(int[] pixel);		
		
		@Override
		public boolean isCompatibleRaster(Raster raster) {
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
	
	

}