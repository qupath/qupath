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

package qupath.lib.display;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import qupath.lib.analysis.stats.Histogram;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorDeconvolutionStains.DEFAULT_CD_STAINS;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.ColorTools;
import qupath.lib.display.ChannelDisplayInfo.MultiChannelInfo;
import qupath.lib.display.ChannelDisplayInfo.RGBDirectChannelInfo;
import qupath.lib.display.ChannelDisplayInfo.SingleChannelDisplayInfo;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.stores.ImageRegionStore;

/**
 * Class used to look after the color transforms that may be applied to an image,
 * including brightness/contrast settings.
 * 
 * 
 * @author Pete Bankhead
 *
 */
public class ImageDisplay {

	final static Logger logger = LoggerFactory.getLogger(ImageDisplay.class);
	
	/**
	 * Identifier used when storing/retrieving display settings from ImageData properties.
	 */
	private static final String PROPERTY_DISPLAY = ImageDisplay.class.getName();

	// Lists to store the different kinds of channels we might need
	private ChannelDisplayInfo rgbDirectChannel = new ChannelDisplayInfo.RGBDirectChannelInfo();
	private List<ChannelDisplayInfo> rgbBasicChannels = new ArrayList<>();
	private List<ChannelDisplayInfo> rgbBrightfieldChannels = new ArrayList<>();
	private List<ChannelDisplayInfo> rgbChromaticityChannels = new ArrayList<>();

	// Image & color transform-related variables
	private boolean useColorLUTs = true;

	private ImageData<BufferedImage> imageData;
	private List<ChannelDisplayInfo> channelOptions = new ArrayList<>();

	private List<ChannelDisplayInfo> selectedChannels = new ArrayList<>();
	private ChannelDisplayInfo lastSelectedChannel = null;

	private long changeTimestamp = System.currentTimeMillis();

	transient private Map<String, Map<ChannelDisplayInfo, Histogram>> cachedHistogramMaps;

	private Map<ChannelDisplayInfo, Histogram> histogramMap = new HashMap<>();

	private ImageRegionStore<BufferedImage> regionStore;
	private boolean showAllRGBTransforms = true;

	transient private List<BufferedImage> imgList = Collections.synchronizedList(new ArrayList<>());


	public ImageDisplay(final ImageData<BufferedImage> imageData, final ImageRegionStore<BufferedImage> regionStore, final boolean showAllRGBTransforms) {
		this.regionStore = regionStore;
		this.showAllRGBTransforms = showAllRGBTransforms;
		createRGBChannels();
		setImageData(imageData);
	}
	

	public void setImageData(ImageData<BufferedImage> imageData) {
		if (this.imageData == imageData)
			return;
		this.imageData = imageData;
		//		updateChannelOptions(true);		
		updateHistogramMap();
		if (imageData != null)
			loadChannelColorProperties();
		//		updateChannelOptions(false);		
		changeTimestamp = System.currentTimeMillis();
	}

	public ImageData<BufferedImage> getImageData() {
		return imageData;
	}

	public boolean useColorLUTs() {
		return useColorLUTs;
	}

	public void setUseColorLUTs(boolean useColorLUTs) {
		this.useColorLUTs = useColorLUTs;
		if (!useColorLUTs && getSelectedChannels().size() > 1)
			setChannelSelected(lastSelectedChannel, true);
		saveChannelColorProperties();
	}


	/**
	 * Get a timestamp the last known changes for the object.
	 * 
	 * This is useful to abort painting if the display changes during a paint run.
	 * 
	 * @return
	 */
	public long getLastChangeTimestamp() {
		return changeTimestamp;
	}
	
	
	private void createRGBChannels() {

		if (!showAllRGBTransforms)
			return;

		// Add simple channel separation
		rgbBasicChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformMethod.Red));
		rgbBasicChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformMethod.Green));
		rgbBasicChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformMethod.Blue));
//		rgbBasicChannels.add(new ChannelDisplayInfo.MultiChannelInfo("Red", 8, 0, 255, 0, 0));
//		rgbBasicChannels.add(new ChannelDisplayInfo.MultiChannelInfo("Green", 8, 1, 0, 255, 0));
//		rgbBasicChannels.add(new ChannelDisplayInfo.MultiChannelInfo("Blue", 8, 2, 0, 0, 255));
		rgbBasicChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformer.ColorTransformMethod.Hue));
		rgbBasicChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformer.ColorTransformMethod.Saturation));
		rgbBasicChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformer.ColorTransformMethod.RGB_mean));

		//		rgbBasicChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(transformer, ColorTransformMethod.Red));
		//		rgbBasicChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(transformer, ColorTransformMethod.Green));
		//		rgbBasicChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(transformer, ColorTransformMethod.Blue));

		// Add optical density & color deconvolution options for brightfield images
		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, ColorTransformMethod.Stain_1));
		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, ColorTransformMethod.Stain_2));
		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, ColorTransformMethod.Stain_3));
		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, ColorTransformer.ColorTransformMethod.Optical_density_sum));
//		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformer.ColorTransformMethod.Optical_density_sum));

//		// Add projections/rejections
//		// (This was to test... they don't appear to be particularly useful (?)
//		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, 1, ColorTransformer.ColorTransformMethod.Stain_1_projection));
//		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, 1, ColorTransformer.ColorTransformMethod.Stain_1_rejection));
//		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, 2, ColorTransformer.ColorTransformMethod.Stain_2_projection));
//		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, 2, ColorTransformer.ColorTransformMethod.Stain_2_rejection));
//		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, 3, ColorTransformer.ColorTransformMethod.Stain_3_projection));
//		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorDeconvolutionInfo(this, 3, ColorTransformer.ColorTransformMethod.Stain_3_rejection));
		
		rgbChromaticityChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformer.ColorTransformMethod.Red_chromaticity));		
		rgbChromaticityChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformer.ColorTransformMethod.Green_chromaticity));		
		rgbChromaticityChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(ColorTransformer.ColorTransformMethod.Blue_chromaticity));		
		//		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(this, ColorTransformMethod.Green_divided_by_blue));		
		//		rgbBrightfieldChannels.add(new ChannelDisplayInfo.RBGColorTransformInfo(this, ColorTransformMethod.Brown));		
	}


	public void updateChannelOptions(boolean serverChanged) {
		ImageServer<BufferedImage> server = imageData == null ? null : imageData.getServer();
		if (server == null) {
			channelOptions.clear();
			return;
		}
		if (server.isRGB()) {
			channelOptions.clear();
			channelOptions.add(rgbDirectChannel);
			// Add color deconvolution options if we have a brightfield image
			if (imageData.isBrightfield()) {
				channelOptions.addAll(rgbBrightfieldChannels);

				if (imageData.getImageType() == ImageData.ImageType.BRIGHTFIELD_H_E) {
					channelOptions.add(new ChannelDisplayInfo.RGBColorReconvolution(this, ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DEFAULT_CD_STAINS.H_E), true));
					channelOptions.add(new ChannelDisplayInfo.RGBNormalizedChannelInfo());
				}
				else if (imageData.getImageType() == ImageData.ImageType.BRIGHTFIELD_H_DAB) {
					channelOptions.add(new ChannelDisplayInfo.RGBColorReconvolution(this, ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DEFAULT_CD_STAINS.H_DAB), true));
					channelOptions.add(new ChannelDisplayInfo.RGBNormalizedChannelInfo());
				}

			}
			channelOptions.addAll(rgbBasicChannels);
			channelOptions.addAll(rgbChromaticityChannels);
			// Remove any invalid channels
			selectedChannels.retainAll(channelOptions);
			// Select the original channel (RGB)
			if (selectedChannels.isEmpty())
				selectedChannels.add(channelOptions.get(0));
		} else if (serverChanged) {
			channelOptions.clear();
			// TODO: Get the number of bits per pixel in a more elegant way
			//			int bpp = server.getBufferedThumbnail(100, 100, 0).getSampleModel().getSampleSize(0);
			int bpp = server.getBitsPerPixel();
			if (server.nChannels() == 1) {
				channelOptions.add(new ChannelDisplayInfo.MultiChannelInfo("Channel 1", bpp, 0, 255, 255, 255));
			}
			else {
				for (int c = 0; c < server.nChannels(); c++) {
					int rgb = server.getDefaultChannelColor(c);
					channelOptions.add(new ChannelDisplayInfo.MultiChannelInfo("Channel " + (c + 1), bpp, c, ColorTools.red(rgb), ColorTools.green(rgb), ColorTools.blue(rgb)));

					//					int r = c == 0 ? 255 : 0;
					//					int g = c == 1 ? 255 : 0;
					//					int b = c == 2 ? 255 : 0;
					//					channelOptions.add(new ChannelDisplayInfo.MultiChannelInfo("Channel " + (c + 1), bpp, c, r, g, b));
				}
			}
		}

		// Select all the channels
		if (serverChanged) {
			selectedChannels.clear();
			if (server.isRGB() || !useColorLUTs)
				selectedChannels.add(channelOptions.get(0));
			else if (useColorLUTs)
				selectedChannels.addAll(channelOptions);
		}

	}

	
	/**
	 * Load any channel colors stored in the image properties.
	 * 
	 * @return
	 */
	public boolean loadChannelColorProperties() {
		if (imageData == null) {
			return false;
		}
		// Parse display from JSON
		Object property = imageData.getProperty(PROPERTY_DISPLAY);
		if (property instanceof String) {
			try {
				updateFromJSON((String)property);
				return true;
			} catch (Exception e) {
				logger.warn("Unable to parse display settings from {}", property);
			}
		}
		
		// Legacy code for the old color-only-storing property approach
		int n = 0;
		for (ChannelDisplayInfo info : getAvailableChannels()) {
			if (info instanceof MultiChannelInfo) {
				MultiChannelInfo multiInfo = (MultiChannelInfo)info;
				Integer colorOld = multiInfo.getColor();
				Object colorNew = imageData.getProperty("COLOR_CHANNEL:" + info.getName());
				if (colorNew instanceof Integer && colorOld != colorNew) {
					multiInfo.setLUTColor((Integer)colorNew);
					n++;
				}
			}
		}
		if (n == 1)
			logger.info("Loaded color channel info for one channel");
		else if (n > 1)
			logger.info("Loaded color channel info for " + n + " channels");
		return n > 0;
	}
	
	
	/**
	 * Set the min/max display values for a specified ChannelDisplayInfo.
	 * 
	 * The benefit of calling this method is that it will update the ImageData metadata if appropriate.
	 * 
	 * @param info
	 * @param minDisplay
	 * @param maxDisplay
	 */
	public void setMinMaxDisplay(final ChannelDisplayInfo info , float minDisplay, float maxDisplay) {
		info.setMinDisplay(minDisplay);
		info.setMaxDisplay(maxDisplay);
		if (getAvailableChannels().contains(info))
			saveChannelColorProperties();
	}
	
	
	/**
	 * Save color channels in the ImageData properties.  This lets them be deserialized later.
	 */
	public void saveChannelColorProperties() {
		if (imageData == null) {
			logger.warn("Cannot save color channel properties - no ImageData available");
			return;
		}
		// Store the current display settings in the ImageData
		imageData.setProperty(PROPERTY_DISPLAY, toJSON(false));
		changeTimestamp = System.currentTimeMillis();
		
		// Legacy code (just stored changed colors, but not min/max values)
//		int n = 0;
//		for (ChannelDisplayInfo info : getAvailableChannels()) {
//			if (info instanceof MultiChannelInfo) {
//				MultiChannelInfo multiInfo = (MultiChannelInfo)info;
//				Integer color = multiInfo.getColor();
//				imageData.setProperty("COLOR_CHANNEL:" + info.getName(), color);
//				n++;
//			}
//		}
//		if (n == 1)
//			logger.info("Saved color channel info for one channel");
//		else if (n > 1)
//			logger.info("Saved color channel info for " + n + " channels");
	}
	

	public List<ChannelDisplayInfo> getAvailableChannels() {
		return Collections.unmodifiableList(channelOptions);
	}

	public List<ChannelDisplayInfo> getSelectedChannels() {
		return Collections.unmodifiableList(selectedChannels);
	}


	/**
	 * Set the selection of a channel on or off.
	 * 
	 * If a channel's isAdditive() method returns false, all other selected channels will be cleared.
	 * Otherwise, other selected channels will be cleared if they are non-additive - but kept if they are additive
	 * (and therefore can be sensibly displayed in combination with this channel).
	 * 
	 * @param channel
	 * @param selected true if the channel should be selected, false if it should not
	 * @return the current selection list, possibly modified by this operation
	 */
	public List<ChannelDisplayInfo> setChannelSelected(ChannelDisplayInfo channel, boolean selected) {
		if (selected) {
			// If the channel is already selected, or wouldn't be valid anyway, we've got nothing to do
			//			if (selectedChannels.contains(channel) || !getAvailableChannels().contains(channel))
			//				return getSelectedChannels();
			// If this channel can't be combined with existing channels, clear the existing ones
			if (!useColorLUTs || !channel.isAdditive() || (!selectedChannels.isEmpty()) && !selectedChannels.get(0).isAdditive())
				selectedChannels.clear();
			if (!selectedChannels.contains(channel))
				selectedChannels.add(channel);
			lastSelectedChannel = channel;
		} else {
			selectedChannels.remove(channel);
			lastSelectedChannel = null;
		}
		// For a brightfield image, revert to the original if all channels are turned off
		if (selectedChannels.isEmpty() && imageData.isBrightfield()) {
			channel = getAvailableChannels().get(0);
			selectedChannels.add(channel);
			lastSelectedChannel = channel;
		}
		List<ChannelDisplayInfo> selectedChannels = getSelectedChannels();
		if (lastSelectedChannel == null && !selectedChannels.isEmpty())
			lastSelectedChannel = selectedChannels.get(0);
		
		saveChannelColorProperties();
		
		return selectedChannels;
	}




	/**
	 * Apply the required transforms to a BufferedImage to get the appropriate display.
	 * imgOutput should always be an RGB image (of some kind), or null if a new image should be created.
	 * 
	 * imgInput should always be an image of the kind that matches the imgData, e.g. RGB/non-RGB, same number of channels,
	 * same bit-depth.
	 * 
	 * Warning: This is not thread-safe.
	 * Warning #2: imgOutput should be TYPE_INT_RGB
	 * 
	 * @param imgInput
	 * @param imgOutput
	 * @return
	 */
	public BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput) {
		int width = imgInput.getWidth();
		int height = imgInput.getHeight();

		if (imgOutput == null || imgOutput.getWidth() != width || imgOutput.getHeight() != height) {
			//			imgOutput = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(width, height);
			imgOutput = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		}

		// If we don't have anything, just give a black image
		if (selectedChannels.isEmpty()) {
			Graphics2D g2d = imgOutput.createGraphics();
			g2d.setColor(Color.BLACK);
			g2d.fillRect(0, 0, width, height);
			g2d.dispose();
			return imgOutput;
		}

		// Check if we have any changes to make - if not, just copy the image
//		try {
		// Sometimes the first entry of selectedChannels was null... not sure why... this test is therefore to paper over the cracks...
			if (selectedChannels.size() == 1 && (selectedChannels.get(0) == null || !selectedChannels.get(0).doesSomething())) {
				if (imgInput == imgOutput) {
					return imgOutput;
				}
				Graphics2D g2d = imgOutput.createGraphics();
				g2d.drawImage(imgInput, 0, 0, null);
				g2d.dispose();
				return imgOutput;
			}
//		} catch (Exception e) {
//			e.printStackTrace();
//		}

		// Loop through the channels & build up an image as needed
		boolean firstChannel = true;

		// TODO: DEAL WITH BGR POSSIBILITY...?  Currently forces RGB - may need to benchmark on Linux in case this is a performance issue
		// The line below worked well for Java 7 & 8 on a Mac, but terribly (killing acceleration) for Java 6
		//		int[] pixels = ((DataBufferInt)imgOutput.getRaster().getDataBuffer()).getData();
		
		// I don't know exactly why, but I can't set this to null if there are multiple channels displayed additively...
		int[] pixels = selectedChannels.size() <= 1 ? null : new int[imgInput.getWidth() * imgInput.getHeight()];

		for (ChannelDisplayInfo info : selectedChannels) {
			if (firstChannel) {
				pixels = info.getRGB(imgInput, pixels, useColorLUTs);
				firstChannel = false;
			} else
				info.updateRGBAdditive(imgInput, pixels, useColorLUTs);
		}

		imgOutput.getRaster().setDataElements(0, 0, imgOutput.getWidth(), imgOutput.getHeight(), pixels);
		
//		imgOutput.setRGB(0, 0, imgOutput.getWidth(), imgOutput.getHeight(), pixels, 0, imgOutput.getWidth());


		//		imgOutput.setRGB(0, 0, width, height, pixels, 0, width);
		//		long endTime = System.currentTimeMillis();
		//		System.out.println("Time taken: " + (endTime - startTime)/1000.);
		return imgOutput;
	}


	public String getTransformedValueAsString(BufferedImage img, int x, int y) {
		if (selectedChannels == null || selectedChannels.isEmpty() || selectedChannels.get(0) == null)
			return "";
		if (selectedChannels.size() == 1)
			return selectedChannels.get(0).getValueAsString(img, x, y);
		
		String s = null;
		for (ChannelDisplayInfo channel : getAvailableChannels()) {
			if (selectedChannels.contains(channel) ) {
				if (s == null)
					s = channel.getValueAsString(img, x, y);
				else
					s += (", " + channel.getValueAsString(img, x, y));
			}
		}
//		String s = selectedChannels.get(0).getValueAsString(img, x, y);
//		for (int i = 1; i < selectedChannels.size(); i++) {
//			s += (", " + selectedChannels.get(i).getValueAsString(img, x, y));
//		}
		return s;
	}





	private void updateHistogramMap() {
		if (imageData == null || imageData.getServer() == null) {
			histogramMap = null;
			return;
		}

		////		String key = imageData.getServer() + "::" + imageData.getServer().getShortServerName();
		String key = imageData.getServerPath();
		if (cachedHistogramMaps == null) {
			cachedHistogramMaps = new HashMap<>();
		} else {
			histogramMap = cachedHistogramMaps.get(key);
			if (histogramMap != null) {
				channelOptions.clear();
				channelOptions.addAll(histogramMap.keySet());

				selectedChannels.clear();
				if (imageData.getServer().isRGB() || !useColorLUTs)
					selectedChannels.add(channelOptions.get(0));
				else if (useColorLUTs)
					selectedChannels.addAll(channelOptions);

				return;
			}
		}

		histogramMap = new LinkedHashMap<>();

		final ImageServer<BufferedImage> server = imageData.getServer();

		final AtomicInteger nPixels = new AtomicInteger();
		imgList.clear();
		//		if (server.nTimepoints() * server.nZSlices() == 1) {
		if (server.nZSlices() == 1) {
			BufferedImage img = regionStore.getThumbnail(server, 0, 0, true);
			imgList.add(img);
			nPixels.set(img.getWidth() * img.getHeight());
		} else {
			// If we have multiple z-slices, load thumbnails in parallel
			ExecutorService pool = Executors.newFixedThreadPool(Math.min(Runtime.getRuntime().availableProcessors(), server.nZSlices()));
			final int t = 0;
			//			for (int t = 0; t < server.nTimepoints(); t++) {
			for (int z = 0; z < server.nZSlices(); z++) {
				final int zCoord = z;
				final int tCoord = t;
				pool.submit(new Runnable() {

					@Override
					public void run() {
						BufferedImage img = regionStore.getThumbnail(server, zCoord, tCoord, true);
						imgList.add(img);
						nPixels.addAndGet(img.getWidth() * img.getHeight());
					}

				});
			}
			//			}
			pool.shutdown();
			try {
				pool.awaitTermination(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		updateChannelOptions(true);

		// Initialize the histogram map
		for (ChannelDisplayInfo info : getAvailableChannels()) {
			histogramMap.put(info, null);
		}
		cachedHistogramMaps.put(key, histogramMap);
		
		// If we don't have an RGB image, we need to compute histograms to find out how to set the brightness/contrast
		List<ChannelDisplayInfo> selectedChannels = getSelectedChannels();
		setHistograms(selectedChannels);
		for (ChannelDisplayInfo info : selectedChannels)
			autoSetDisplayRange(info);
		
		
	}



	public void autoSetDisplayRange(ChannelDisplayInfo info, Histogram histogram, double saturation) {
		if (histogram == null) {
			// TODO: Look at other times whenever no histogram will be provided
			if (!(info instanceof RGBDirectChannelInfo))
				logger.warn("Cannot set display range for {} - no histogram found", info);
			//			System.out.println("Cannot set display range for " + info + " - no histogram found");
			return;
		}
		// For unsupported saturation values, just set to the min/max
		if (saturation <= 0 || saturation >= 1) {
			info.setMinDisplay((float)histogram.getEdgeMin());
			info.setMaxDisplay((float)histogram.getEdgeMax());
			return;
		}

		double countMax = histogram.getNormalizeCounts() ? saturation : histogram.getCountSum() * saturation;
		double count = 0;
		int ind = 0;
		while (count < countMax && ind < histogram.nBins()-1) {
			count += histogram.getCountsForBin(ind);
			ind++;
		}
		info.setMinDisplay((float)histogram.getBinLeftEdge(ind));

		count = 0;
		ind = histogram.nBins()-1;
		while (count < countMax && ind > 0) {
			count += histogram.getCountsForBin(ind);
			ind--;
		}
		info.setMaxDisplay((float)histogram.getBinRightEdge(ind));
	}


	public void autoSetDisplayRange(ChannelDisplayInfo info, Histogram histogram) {
		autoSetDisplayRange(info, histogram, 0.01);
	}

	public void autoSetDisplayRange(ChannelDisplayInfo info) {
		autoSetDisplayRange(info, histogramMap.get(info));
	}
	
	public void autoSetDisplayRange(ChannelDisplayInfo info, double saturation) {
		autoSetDisplayRange(info, histogramMap.get(info), saturation);
	}


	/**
	 * Returns a histogram for a ChannelInfo, or none if no histogram is available (e.g. the channel is RGB)
	 * @param info
	 * @return
	 */
	public Histogram getHistogram(ChannelDisplayInfo info) {
		if (info == null || histogramMap == null)
			return null;
		Histogram histogram = histogramMap.get(info);

		// Create a histogram, if we need to
		if (histogram == null && info instanceof SingleChannelDisplayInfo) {
			setHistograms(Collections.singleton(info));
			histogram = histogramMap.get(info);
		}
		return histogram;
	}




	private void setHistograms(final Collection<ChannelDisplayInfo> channels) {

		int nPixels = 0;
		for (BufferedImage img : imgList)
			nPixels += img.getWidth() * img.getHeight();

		float[] values = null; // Array needed for values for a particular channel
		float[] pixels = null; // Array needed for all values if there are multiple thumbnails
		if (imgList.size() > 1)
			pixels = new float[nPixels];


		long startTime = System.currentTimeMillis();

//		updateChannelOptions(true);


		for (ChannelDisplayInfo info : channels) {
			if (!(info instanceof SingleChannelDisplayInfo)) {
				histogramMap.put(info, null);
				continue;
			}
			int counter = 0;
			for (BufferedImage img : imgList) {
				SingleChannelDisplayInfo infoSingle = (SingleChannelDisplayInfo)info;
				values = infoSingle.getValues(img, 0, 0, img.getWidth(), img.getHeight(), values);
				if (pixels != null) {
					System.arraycopy(values, 0, pixels, counter, values.length);
				}
				counter += values.length;
			}

			Histogram histogram;
			// Create the histogram
			if (pixels == null)
				histogram = new Histogram(values, 1024);
			else
				histogram = new Histogram(pixels, 1024);
			histogram.setNormalizeCounts(true);
			logger.debug("{} {}", info, histogram);
			//			System.out.println(info.toString() + " " + histogram.toString());
			histogramMap.put(info, histogram);

			// Update the possible display range for the channel
			float min = (float)histogram.getEdgeMin();
			float max = (float)histogram.getEdgeMax();
			float minAllowed = min < 0 ? min : 0;
			// If the default maximum is 255, and we have an integer histogram, that's probably a sensible default... otherwise, use the actual maximum
			boolean probably8Bit = info.getMaxAllowed() == 255 && histogram.isInteger() && histogram.getEdgeMax() <= 255 && histogram.getEdgeMin() >= 0;
			float maxAllowed = probably8Bit ? 255 : max;
			info.setMinMaxAllowed(minAllowed, maxAllowed);

			// Default to the full range of probably-not-8-bit data
			if (!probably8Bit) {
				autoSetDisplayRange(info, histogram);
			}
		}

		long endTime = System.currentTimeMillis();
		logger.debug("Histogram creation time: {} seconds", (endTime - startTime)/1000.);

	}


	
	
	
	
	/**
	 * Create a JSON representation of the main components of the current display.
	 * 
	 * @return
	 */
	public String toJSON() {
		return toJSON(false);
	}
	
	/**
	 * Create a JSON representation of the main components of the current display.
	 * 
	 * @return
	 */
	public String toJSON(final boolean prettyPrint) {
		JsonArray array = new JsonArray();
		for (ChannelDisplayInfo info : channelOptions) {
			JsonObject obj = new JsonObject();
			obj.addProperty("name", info.getName());
			obj.addProperty("class", info.getClass().getName());
			obj.addProperty("minDisplay", info.getMinDisplay());
			obj.addProperty("maxDisplay", info.getMaxDisplay());
			obj.addProperty("color", info.getColor());
			obj.addProperty("selected", selectedChannels.contains(info));			
			array.add(obj);
		}
		if (prettyPrint) {
			Gson gson = new GsonBuilder().setPrettyPrinting().create();
			return gson.toJson(array);
		} else
			return array.toString();
	}
	
	/**
	 * Update current display info based on deserializing a JSON String.
	 * 
	 * @param json
	 */
	void updateFromJSON(final String json) {
		Gson gson = new Gson();
		Type type = new TypeToken<List<JsonHelperChannelInfo>>(){}.getType();
		List<JsonHelperChannelInfo> helperList = gson.fromJson(json, type);
		// Try updating everything
		for (JsonHelperChannelInfo helper : helperList) {
			for (ChannelDisplayInfo info : channelOptions) {
				if (helper.updateInfo(info)) {
					if (Boolean.TRUE.equals(helper.selected)) {
						if (!selectedChannels.contains(info))
							selectedChannels.add(info);
					} else
						selectedChannels.remove(info);
					break;
				}
			}
		}
	}
	
	/**
	 * Class to help with deserializing JSON representation.
	 */
	static class JsonHelperChannelInfo {
		private String name;
		private Class<?> cls;
		private Float minDisplay;
		private Float maxDisplay;
		private Integer color;
		private Boolean selected;
		
		/**
		 * Check if we match the info.
		 * That means the names must be the same, and the classes must either match or 
		 * the class here needs to be <code>null</code>.
		 * 
		 * @param info
		 * @return
		 */
		boolean matches(final ChannelDisplayInfo info) {
			if (name == null)
				return false;
			return name.equals(info.getName()) && (cls == null || cls.equals(info.getClass()));
		}
		
		/**
		 * Check is this helper <code>matches</code> the info, and set its properties if so.
		 * 
		 * @param info
		 * @return
		 */
		boolean updateInfo(final ChannelDisplayInfo info) {
			if (!matches(info))
				return false;
			if (minDisplay != null)
				info.setMinDisplay(minDisplay);
			if (maxDisplay != null)
				info.setMaxDisplay(maxDisplay);
			if (color != null && info instanceof MultiChannelInfo)
				((MultiChannelInfo)info).setLUTColor(color);
			return true;
		}
	}


}
