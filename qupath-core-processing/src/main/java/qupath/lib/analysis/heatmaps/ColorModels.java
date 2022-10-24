/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.analysis.heatmaps;

import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.util.Objects;
import java.util.function.DoubleToIntFunction;

import qupath.lib.color.ColorMaps;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.color.ColorMaps.ColorMap;
import qupath.lib.images.servers.PixelType;
import qupath.lib.io.GsonTools;
import qupath.lib.io.GsonTools.SubTypeAdapterFactory;

/**
 * Helper class for creating a JSON-serializable way to generate a {@link ColorModel}.
 * 
 * @author Pete Bankhead
 */
public class ColorModels {
	
	private static SubTypeAdapterFactory<ColorModelBuilder> factory = GsonTools.createSubTypeAdapterFactory(ColorModelBuilder.class, "type")
			.registerSubtype(SingleChannelColorModelBuilder.class);
	
	static {
		GsonTools.getDefaultBuilder().registerTypeAdapterFactory(factory);
	}

	/**
	 * Simple builder to create a {@link ColorModel}.
	 * @implSpec implementations should be JSON-serializable and registered with {@link GsonTools}.
	 */
	public static interface ColorModelBuilder {
		
		/**
		 * Build a {@link ColorModel}.
		 * @return
		 */
		public ColorModel build();
		
	}
	
	/**
	 * Create a {@link ColorModelBuilder} with a main channel and an optional alpha channel.
	 * @param mainChannel the main channel to display (colormap will be used)
	 * @param alphaChannel an optional alpha channel (colormap will be ignored)
	 * @return the {@link ColorModelBuilder}
	 */
	public static ColorModelBuilder createColorModelBuilder(DisplayBand mainChannel, DisplayBand alphaChannel) {
		return new SingleChannelColorModelBuilder(mainChannel, alphaChannel);
	}
	
	/**
	 * Create a {@link DisplayBand} to define the colormap associated with an image band (channel).
	 * @param colorMapName name of the {@link ColorMap}
	 * @param band image band (the {@link BufferedImage} term - QuPath often refers to this as a channel)
	 * @param minDisplay value associated with the first entry in the {@link ColorMap}
	 * @param maxDisplay value associated with the last entry in the {@link ColorMap}
	 * @return
	 * @see #createColorModelBuilder(DisplayBand, DisplayBand)
	 * @see ColorMaps#getColorMaps()
	 */
	public static DisplayBand createBand(String colorMapName, int band, double minDisplay, double maxDisplay) {
		return createBand(colorMapName, band, minDisplay, maxDisplay, 1);
	}
	
	/**
	 * Create a {@link DisplayBand} to define the colormap associated with an image band (channel).
	 * @param colorMapName name of the {@link ColorMap}
	 * @param band image band (the {@link BufferedImage} term - QuPath often refers to this as a channel)
	 * @param minDisplay value associated with the first entry in the {@link ColorMap}
	 * @param maxDisplay value associated with the last entry in the {@link ColorMap}, used to adjust the value nonlinearly when requesting a color
	 * @param gamma gamma value associated with the {@link ColorMap}
	 * @return
	 * @see #createColorModelBuilder(DisplayBand, DisplayBand)
	 * @see ColorMaps#getColorMaps()
	 */
	public static DisplayBand createBand(String colorMapName, int band, double minDisplay, double maxDisplay, double gamma) {
		return new DisplayBand(colorMapName, null, band, minDisplay, maxDisplay, gamma);
	}
	
	/**
	 * Helper class to the display of a single channel (band) in a {@link ColorModel}.
	 * This exists to avoid passing a plethora of parameters to {@link ColorModels#createColorModelBuilder(DisplayBand, DisplayBand)}
	 */
	public static class DisplayBand {
		
		private String colorMapName;
		private ColorMap colorMap;
		
		private int band;
		private double minDisplay;
		private double maxDisplay;
		private double gamma = 1;
		
		private DisplayBand(String colorMapName, ColorMap colorMap, int band, double minDisplay, double maxDisplay, double gamma) {
			this.colorMapName = colorMapName;
			this.colorMap = colorMap;
			this.band = band;
			this.minDisplay = minDisplay;
			this.maxDisplay = maxDisplay;
			this.gamma = gamma;
		}
		
		private ColorMap getColorMap() {
			if (colorMap != null)
				return colorMap;
			else if (colorMapName != null)
				return ColorMaps.getColorMaps().getOrDefault(colorMapName, null);
			else
				return null;
		}
		
	}
	
	static class SingleChannelColorModelBuilder implements ColorModelBuilder {
		
		private DisplayBand band;
		private DisplayBand alphaBand;
		
		private SingleChannelColorModelBuilder(DisplayBand band, DisplayBand alphaBand) {
			Objects.requireNonNull(band);
			this.band = band;
			this.alphaBand = alphaBand;
		}
		
		@Override
		public ColorModel build() {
			var map = band.getColorMap();
			if (map == null)
				map = ColorMaps.getDefaultColorMap();
			if (band.gamma != 1)
				map = ColorMaps.gammaColorMap(map, band.gamma);
			
			int alphaBandInd = -1;
			double alphaMin = 0;
			double alphaMax = 1;
			double alphaGamma = -1;
			if (alphaBand != null) {
				alphaBandInd = alphaBand.band;
				alphaMin = alphaBand.minDisplay;
				alphaMax = alphaBand.maxDisplay;
				alphaGamma = alphaBand.gamma;				
			}
			return buildColorModel(map, band.band, band.minDisplay, band.maxDisplay, alphaBandInd, alphaMin, alphaMax, alphaGamma);
		}
		
	}
	
	
	
	private static ColorModel buildColorModel(ColorMap colorMap, int band, double minDisplay, double maxDisplay, int alphaCountBand, double minAlpha, double maxAlpha, double alphaGamma) {
		DoubleToIntFunction alphaFun = null;
		if (alphaCountBand < 0) {
			if (alphaGamma <= 0) {
				alphaFun = null;
				alphaCountBand = -1;
			} else {
				alphaFun = ColorModelFactory.createGammaFunction(alphaGamma, minAlpha, maxAlpha);
				alphaCountBand = 0;
			}
		} else if (alphaFun == null) {
			if (alphaGamma < 0)
				alphaFun = d -> 255;
			else if (alphaGamma == 0)
				alphaFun = d -> d > minAlpha ? 255 : 0;
			else
				alphaFun = ColorModelFactory.createGammaFunction(alphaGamma, minAlpha, maxAlpha);
		}
		return ColorModelFactory.createColorModel(PixelType.FLOAT32, colorMap, band, minDisplay, maxDisplay, alphaCountBand, alphaFun);
	}
	
}
