/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.display;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.display.ChannelDisplayInfo.ModifiableChannelDisplayInfo;
import qupath.lib.gui.images.stores.AbstractImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelType;

/**
 * Class used to look after the color transforms that may be applied to an image,
 * including brightness/contrast settings.
 * <p>
 * Warning! This implementation is likely to change in a future version.
 * 
 * @author Pete Bankhead
 */
public class ImageDisplay extends AbstractImageRenderer {

	private final static Logger logger = LoggerFactory.getLogger(ImageDisplay.class);
	
	/**
	 * Identifier used when storing/retrieving display settings from ImageData properties.
	 */
	private static final String PROPERTY_DISPLAY = ImageDisplay.class.getName();

	// Lists to store the different kinds of channels we might need
	private RGBDirectChannelInfo rgbDirectChannelInfo;
	private RGBNormalizedChannelInfo rgbNormalizedChannelInfo;
	private List<ChannelDisplayInfo> rgbBasicChannels = new ArrayList<>();
	private List<ChannelDisplayInfo> rgbBrightfieldChannels = new ArrayList<>();
	private List<ChannelDisplayInfo> rgbChromaticityChannels = new ArrayList<>();

	// Image & color transform-related variables
	private BooleanProperty useGrayscaleLuts = new SimpleBooleanProperty();

	private ImageData<BufferedImage> imageData;
	private ObservableList<ChannelDisplayInfo> channelOptions = FXCollections.observableArrayList();

	private ObservableList<ChannelDisplayInfo> selectedChannels = FXCollections.observableArrayList();
	private ChannelDisplayInfo lastSelectedChannel = null;

	private LongProperty changeTimestamp = new SimpleLongProperty(System.currentTimeMillis());
	
	transient private static Map<String, HistogramManager> cachedHistograms = Collections.synchronizedMap(new HashMap<>());
	private HistogramManager histogramManager = null;
	
	private static BooleanProperty showAllRGBTransforms = PathPrefs.createPersistentPreference("showAllRGBTransforms", true);

	/**
	 * Constructor.
	 * @param imageData image data that should be displayed
	 */
	public ImageDisplay(final ImageData<BufferedImage> imageData) {
		setImageData(imageData, false);
		useGrayscaleLuts.addListener((v, o, n) -> {
			if (n && selectedChannels.size() > 1)
				setChannelSelected(lastSelectedChannel, true);
			saveChannelColorProperties();
		});
	}
	

	/**
	 * Set the {@link ImageData} to a new value
	 * @param imageData image data that should how be displayed
	 * @param retainDisplaySettings if true, retain the same display settings as for the previous image if possible 
	 *                              (i.e. the images have similar channels)
	 */
	public void setImageData(ImageData<BufferedImage> imageData, boolean retainDisplaySettings) {
		if (this.imageData == imageData)
			return;

		// Retain display settings if requested *and* we have two similar images 
		// (i.e. same bit depth, same number and names for channels)
		String lastDisplayJSON = null;
		if (retainDisplaySettings && this.imageData != null && imageData != null) {
			ImageServer<?> lastServer = this.imageData.getServer();
			ImageServer<?> nextServer = imageData.getServer();
			retainDisplaySettings = lastServer.nChannels() == nextServer.nChannels() &&
					lastServer.getPixelType() == nextServer.getPixelType();
			if (retainDisplaySettings) {
				for (int c = 0; c < lastServer.nChannels(); c++) {
					if (!lastServer.getChannel(c).getName().equals(nextServer.getChannel(c).getName())) {
						retainDisplaySettings = false;
					}
				}
			}
		}
		lastDisplayJSON = retainDisplaySettings ? toJSON() : null;
		
		this.imageData = imageData;
		updateChannelOptions(true);
		updateHistogramMap();
		if (imageData != null) {
			// Load any existing color properties
			loadChannelColorProperties();
			// Update from the last image, if required
			if (lastDisplayJSON != null && !lastDisplayJSON.isEmpty())
				updateFromJSON(lastDisplayJSON);
		}
		changeTimestamp.set(System.currentTimeMillis());
	}
	
	/**
	 * Get the current image data
	 * @return
	 */
	public ImageData<BufferedImage> getImageData() {
		return imageData;
	}
	
	/**
	 * Property that specifies whether grayscale lookup tables should be preferred to color lookup tables
	 * @return
	 */
	public BooleanProperty useGrayscaleLutProperty() {
		return useGrayscaleLuts;
	}
	
	/**
	 * Get the value of {@link #useGrayscaleLutProperty()}
	 * @return
	 */
	public boolean useGrayscaleLuts() {
		return useGrayscaleLuts.get();
	}

	/**
	 * Set the value of {@link #useGrayscaleLutProperty()}
	 * @param useGrayscaleLuts
	 */
	public void setUseGrayscaleLuts(boolean useGrayscaleLuts) {
		this.useGrayscaleLuts.set(useGrayscaleLuts);
	}
	
	/**
	 * The opposite of {@link #useGrayscaleLuts()}
	 * @return
	 */
	public boolean useColorLUTs() {
		return !useGrayscaleLuts();
	}


	/**
	 * Get a timestamp the last known changes for the object.
	 * 
	 * This is useful to abort painting if the display changes during a paint run.
	 * 
	 * @return
	 */
	@Override
	public long getLastChangeTimestamp() {
		return changeTimestamp.get();
	}
	
	/**
	 * Timestamp for the most recent change.  This can be used to listen for 
	 * display changes.
	 * 
	 * @return
	 */
	public LongProperty changeTimestampProperty() {
		return changeTimestamp;
	}
	
	
	private void createRGBChannels(final ImageData<BufferedImage> imageData) {
		
		rgbDirectChannelInfo = null;
		rgbNormalizedChannelInfo = null;

		rgbBasicChannels.clear();
		rgbBrightfieldChannels.clear();
		rgbChromaticityChannels.clear();
		
		if (imageData == null)
			return;
		
		rgbDirectChannelInfo = new RGBDirectChannelInfo(imageData);
		rgbNormalizedChannelInfo = new RGBNormalizedChannelInfo(imageData);

		// Add simple channel separation
		rgbBasicChannels.add(new RBGColorTransformInfo(imageData, ColorTransformMethod.Red, false));
		rgbBasicChannels.add(new RBGColorTransformInfo(imageData, ColorTransformMethod.Green, false));
		rgbBasicChannels.add(new RBGColorTransformInfo(imageData, ColorTransformMethod.Blue, false));
//		rgbBasicChannels.add(new ChannelDisplayInfo.MultiChannelInfo("Red", 8, 0, 255, 0, 0));
//		rgbBasicChannels.add(new ChannelDisplayInfo.MultiChannelInfo("Green", 8, 1, 0, 255, 0));
//		rgbBasicChannels.add(new ChannelDisplayInfo.MultiChannelInfo("Blue", 8, 2, 0, 0, 255));
		rgbBasicChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Hue, false));
		rgbBasicChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Saturation, false));
		rgbBasicChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.RGB_mean, false));

		// Add optical density & color deconvolution options for brightfield images
		rgbBrightfieldChannels.add(new RBGColorDeconvolutionInfo(imageData, ColorTransformMethod.Stain_1));
		rgbBrightfieldChannels.add(new RBGColorDeconvolutionInfo(imageData, ColorTransformMethod.Stain_2));
		rgbBrightfieldChannels.add(new RBGColorDeconvolutionInfo(imageData, ColorTransformMethod.Stain_3));
		rgbBrightfieldChannels.add(new RBGColorDeconvolutionInfo(imageData, ColorTransformer.ColorTransformMethod.Optical_density_sum));

		rgbChromaticityChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Red_chromaticity, false));		
		rgbChromaticityChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Green_chromaticity, false));		
		rgbChromaticityChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Blue_chromaticity, false));		
	}

	/**
	 * Refresh the channel options. This may be used if an underlying property of the image has changed, such 
	 * as the channel names or lookup tables.
	 */
	public void refreshChannelOptions() {
		updateChannelOptions(false);
		
	}

	private void updateChannelOptions(boolean serverChanged) {
		
		logger.trace("Updating channel options (serverChanged={})", serverChanged);
		
		// If the server has changed, reset the RGB channels that we have cached
		if (serverChanged) {
			createRGBChannels(null);
		}

		ImageServer<BufferedImage> server = imageData == null ? null : imageData.getServer();
		if (server == null) {
			selectedChannels.clear();
			channelOptions.clear();
			return;
		}
		
		List<ChannelDisplayInfo> tempChannelOptions = new ArrayList<>();
		List<ChannelDisplayInfo> tempSelectedChannels = new ArrayList<>(this.selectedChannels);
		if (server.isRGB()) {
			createRGBChannels(imageData);
			tempChannelOptions.add(rgbDirectChannelInfo);
			// Add color deconvolution options if we have a brightfield image
			if (imageData.isBrightfield()) {
				tempChannelOptions.addAll(rgbBrightfieldChannels);
			}
			if (showAllRGBTransforms.get()) {
				tempChannelOptions.add(rgbNormalizedChannelInfo);
				tempChannelOptions.addAll(rgbBasicChannels);
				tempChannelOptions.addAll(rgbChromaticityChannels);
			}
			// Remove any invalid channels
			tempSelectedChannels.retainAll(tempChannelOptions);
			// Select the original channel (RGB)
			if (tempSelectedChannels.isEmpty())
				tempSelectedChannels.add(tempChannelOptions.get(0));
		} else if (serverChanged) {
			if (server.nChannels() == 1) {
				tempChannelOptions.add(new DirectServerChannelInfo(imageData, 0));
			}
			else {
				for (int c = 0; c < server.nChannels(); c++) {
					tempChannelOptions.add(new DirectServerChannelInfo(imageData, c));
				}
			}
		} else {
			// Ensure channel colors are set
			boolean colorsUpdated = false;
			for (int c = 0; c < channelOptions.size(); c++) {
				var option = channelOptions.get(c);
				if (option instanceof DirectServerChannelInfo && c < server.nChannels()) {
					var channel = server.getChannel(c);
					if (option.getColor() != channel.getColor()) {
						((DirectServerChannelInfo)option).setLUTColor(channel.getColor());
						colorsUpdated = true;
					}
				}
			}
			tempChannelOptions.addAll(channelOptions);
			if (colorsUpdated)
				saveChannelColorProperties();
		}
		
		// Select all the channels
		if (serverChanged) {
			tempSelectedChannels.clear();
			if (server.isRGB() || !useColorLUTs())
				tempSelectedChannels.add(tempChannelOptions.get(0));
			else if (useColorLUTs())
				tempSelectedChannels.addAll(tempChannelOptions);
			selectedChannels.clear();
		}
		channelOptions.setAll(tempChannelOptions);
		selectedChannels.setAll(tempSelectedChannels);
	}

	
	/**
	 * Load any channel colors stored in the image properties.
	 * 
	 * @return
	 */
	private boolean loadChannelColorProperties() {
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
		for (ChannelDisplayInfo info : channelOptions) {
			if (info instanceof DirectServerChannelInfo) {
				DirectServerChannelInfo multiInfo = (DirectServerChannelInfo)info;
				Integer colorOld = multiInfo.getColor();
				Object colorNew = imageData.getProperty("COLOR_CHANNEL:" + info.getName());
				if (colorNew instanceof Integer && ((Integer) colorNew).equals(colorOld)) {
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
		setMinMaxDisplay(info, minDisplay, maxDisplay, true);
	}
	
	void setMinMaxDisplay(final ChannelDisplayInfo info , float minDisplay, float maxDisplay, boolean fireUpdate) {
		if (info instanceof ModifiableChannelDisplayInfo) {
			((ModifiableChannelDisplayInfo)info).setMinDisplay(minDisplay);
			((ModifiableChannelDisplayInfo)info).setMaxDisplay(maxDisplay);			
		}
		if (fireUpdate && channelOptions.contains(info))
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
		changeTimestamp.set(System.currentTimeMillis());
	}
	

//	public List<ChannelDisplayInfo> getAvailableChannels() {
//		return Collections.unmodifiableList(channelOptions);
//	}

	private ObservableList<ChannelDisplayInfo> selectedChannelsReadOnly = FXCollections.unmodifiableObservableList(selectedChannels);	
	
	/**
	 * {@link ObservableList} containing the channels currently selected for display.
	 * @return
	 * @see #availableChannels()
	 */
	public ObservableList<ChannelDisplayInfo> selectedChannels() {
		return selectedChannelsReadOnly;
	}
	
	private ObservableList<ChannelDisplayInfo> availableChannels = FXCollections.unmodifiableObservableList(channelOptions);
	
	/**
	 * {@link ObservableList} containing the channels currently available for display.
	 * @return
	 * @see #selectedChannels()
	 */
	public ObservableList<ChannelDisplayInfo> availableChannels() {
		return availableChannels;
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
	 */
	public void setChannelSelected(ChannelDisplayInfo channel, boolean selected) {
		// Try to minimize the number of events fired
		List<ChannelDisplayInfo> tempSelectedChannels = new ArrayList<>(selectedChannels);
		if (selected) {
			// If the channel is already selected, or wouldn't be valid anyway, we've got nothing to do
			//			if (selectedChannels.contains(channel) || !getAvailableChannels().contains(channel))
			//				return getSelectedChannels();
			// If this channel can't be combined with existing channels, clear the existing ones
			if (!useColorLUTs() || !channel.isAdditive() || (!tempSelectedChannels.isEmpty()) && !tempSelectedChannels.get(0).isAdditive())
				tempSelectedChannels.clear();
			if (!tempSelectedChannels.contains(channel))
				tempSelectedChannels.add(channel);
			lastSelectedChannel = channel;
		} else {
			tempSelectedChannels.remove(channel);
			lastSelectedChannel = null;
		}
		// For a brightfield image, revert to the original if all channels are turned off
		if (tempSelectedChannels.isEmpty() && imageData.isBrightfield()) {
			channel = channelOptions.get(0);
			tempSelectedChannels.add(channel);
			lastSelectedChannel = channel;
		}
		if (lastSelectedChannel == null && !tempSelectedChannels.isEmpty())
			lastSelectedChannel = tempSelectedChannels.get(0);
		
		selectedChannels.setAll(tempSelectedChannels);
		saveChannelColorProperties();
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
	@Override
	public BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput) {
//		long startTime = System.currentTimeMillis();
		BufferedImage imgResult = applyTransforms(imgInput, imgOutput, selectedChannels, useGrayscaleLuts());
//		long endTime = System.currentTimeMillis();
//		System.err.println("Transform time: " + (endTime - startTime));
		return imgResult;
	}
	
	/**
	 * Convert an image too RGB by applying the specified {@linkplain ChannelDisplayInfo ChannelDisplayInfos}.
	 * 
	 * @param imgInput the input image to transform
	 * @param imgOutput optional output image (must be the same size as the input image, and RGB)
	 * @param selectedChannels the channels to use
	 * @param useGrayscaleLuts if true, prefer grayscale lookup tables rather than color
	 * @return an RGB image determined by transforming the input image using the specified channels
	 */
	public static BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput, List<? extends ChannelDisplayInfo> selectedChannels, boolean useGrayscaleLuts) {
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

		// Loop through the channels & build up an image as needed
		boolean firstChannel = true;

		// TODO: DEAL WITH BGR POSSIBILITY...?  Currently forces RGB - may need to benchmark on Linux in case this is a performance issue
		// The line below worked well for Java 7 & 8 on a Mac, but terribly (killing acceleration) for Java 6
		//		int[] pixels = ((DataBufferInt)imgOutput.getRaster().getDataBuffer()).getData();
		
		// I don't know exactly why, but I can't set this to null if there are multiple channels displayed additively...
		int[] pixels = selectedChannels.size() <= 1 ? null : new int[imgInput.getWidth() * imgInput.getHeight()];

		try {
			for (ChannelDisplayInfo info : selectedChannels.toArray(ChannelDisplayInfo[]::new)) {
				if (firstChannel) {
					pixels = info.getRGB(imgInput, pixels, !useGrayscaleLuts);
					firstChannel = false;
				} else {
					info.updateRGBAdditive(imgInput, pixels, !useGrayscaleLuts);
				}
			}
		} catch (Exception e) {
			logger.error("Error extracting pixels for display", e);
		}

		imgOutput.getRaster().setDataElements(0, 0, imgOutput.getWidth(), imgOutput.getHeight(), pixels);
		
//		imgOutput.setRGB(0, 0, imgOutput.getWidth(), imgOutput.getHeight(), pixels, 0, imgOutput.getWidth());


		//		imgOutput.setRGB(0, 0, width, height, pixels, 0, width);
		//		long endTime = System.currentTimeMillis();
		//		System.out.println("Time taken: " + (endTime - startTime)/1000.);
		return imgOutput;
	}


	/**
	 * Get a string representation of a transformed pixel value, using the currently-selected channels.
	 * @param img image providing the value
	 * @param x x-coordinate of the pixel
	 * @param y y-coordinate of the pixels
	 * @return a String representation of the pixel's transformed value
	 */
	public String getTransformedValueAsString(BufferedImage img, int x, int y) {
		if (selectedChannels == null || selectedChannels.isEmpty() || selectedChannels.get(0) == null)
			return "";
		if (selectedChannels.size() == 1)
			return selectedChannels.get(0).getValueAsString(img, x, y);
		
		String s = null;
		for (ChannelDisplayInfo channel : channelOptions) {
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
		ImageServer<BufferedImage> server = imageData == null ? null : imageData.getServer();
		if (server == null) {
			histogramManager = null;
			return;
		}
		
		histogramManager = cachedHistograms.get(server.getPath());
		if (histogramManager == null) {
			histogramManager = new HistogramManager(0L);
//			histogramManager = new HistogramManager(server.getLastChangeTimestamp());
			histogramManager.ensureChannels(server, channelOptions);
			if (server.getPixelType() == PixelType.UINT8) {
				channelOptions.parallelStream().filter(c -> !(c instanceof DirectServerChannelInfo)).forEach(channel -> autoSetDisplayRange(channel, false));								
			} else {
				channelOptions.parallelStream().forEach(channel -> autoSetDisplayRange(channel, false));				
			}
			cachedHistograms.put(server.getPath(), histogramManager);
		} else {
			channelOptions.parallelStream().forEach(channel -> autoSetDisplayRange(channel, false));
		}
	}



	private void autoSetDisplayRange(ChannelDisplayInfo info, Histogram histogram, double saturation, boolean fireUpdate) {
		if (histogram == null) {
			// TODO: Look at other times whenever no histogram will be provided
			if (!(info instanceof RGBDirectChannelInfo))
				logger.warn("Cannot set display range for {} - no histogram found", info);
			//			System.out.println("Cannot set display range for " + info + " - no histogram found");
			return;
		}
		// For unsupported saturation values, just set to the min/max
		if (saturation <= 0 || saturation >= 1) {
			setMinMaxDisplay(info, (float)histogram.getEdgeMin(), (float)histogram.getEdgeMax());
			return;
		}

		long countSum = histogram.getCountSum();
		int nBins = histogram.nBins();
		int ind = 0;
		// Possibly skip the first and/or last bins; these can often represent unscanned/clipped regions
		if (nBins > 2) {
			long firstCount = histogram.getCountsForBin(0);
			if (firstCount > histogram.getCountsForBin(1)) {
				countSum -= histogram.getCountsForBin(0);
				ind = 1;
			}
			long lastCount = histogram.getCountsForBin(nBins-1);
			if (lastCount > histogram.getCountsForBin(nBins-2)) {
				countSum -= lastCount;
				nBins -= 1;
			}
		}
		
		double countMax = countSum * saturation;
		double count = countMax;
		double minDisplay = histogram.getEdgeMin();
		while (ind < histogram.nBins()) {
			double nextCount = histogram.getCountsForBin(ind);
			if (count < nextCount) {
				minDisplay = histogram.getBinLeftEdge(ind) + (count / nextCount) * histogram.getBinWidth(ind);
				break;
			}
			count -= nextCount;
			ind++;
		}

		count = countMax;
		double maxDisplay = histogram.getEdgeMax();
		ind = histogram.nBins()-1;
		while (ind >= 0) {
			double nextCount = histogram.getCountsForBin(ind);
			if (count < nextCount) {
				maxDisplay = histogram.getBinRightEdge(ind) - (count / nextCount) * histogram.getBinWidth(ind);
				break;
			}
			count -= nextCount;
			ind--;
		}
		logger.debug(String.format("Display range for {}: %.3f - %.3f (saturation %.3f)",  minDisplay, maxDisplay, saturation), info.getName());
		setMinMaxDisplay(info, (float)minDisplay, (float)maxDisplay, fireUpdate);
		
//		double countMax = histogram.getCountSum() * saturation;
//		double count = countMax;
//		int ind = 0;
//		double minDisplay = histogram.getEdgeMin();
//		while (ind < histogram.nBins()) {
//			double nextCount = histogram.getCountsForBin(ind);
//			if (count < nextCount) {
//				minDisplay = histogram.getBinLeftEdge(ind) + (count / nextCount) * histogram.getBinWidth(ind);
//				break;
//			}
//			count -= nextCount;
//			ind++;
//		}
//
//		count = countMax;
//		double maxDisplay = histogram.getEdgeMax();
//		ind = histogram.nBins()-1;
//		while (ind >= 0) {
//			double nextCount = histogram.getCountsForBin(ind);
//			if (count < nextCount) {
//				maxDisplay = histogram.getBinRightEdge(ind) - (count / nextCount) * histogram.getBinWidth(ind);
//				break;
//			}
//			count -= nextCount;
//			ind--;
//		}
//		logger.debug(String.format("Display range for {}: %.3f - %.3f (saturation %.3f)",  minDisplay, maxDisplay, saturation), info.getName());
//		setMinMaxDisplay(info, (float)minDisplay, (float)maxDisplay, fireUpdate);
	}

	void autoSetDisplayRange(ChannelDisplayInfo info, boolean fireUpdate) {
		autoSetDisplayRange(info, getHistogram(info), PathPrefs.autoBrightnessContrastSaturationPercentProperty().get()/100.0, fireUpdate);
	}

	/**
	 * Automatically set the display range for a channel, using the default saturation defined in {@link PathPrefs#autoBrightnessContrastSaturationPercentProperty()}.
	 * @param info channel to update
	 */
	public void autoSetDisplayRange(ChannelDisplayInfo info) {
		autoSetDisplayRange(info, getHistogram(info), PathPrefs.autoBrightnessContrastSaturationPercentProperty().get()/100.0, true);
	}
	
	/**
	 * Automatically set the display range for a channel.
	 * @param info channel to update
	 * @param saturation proportion of pixels that may be saturated, i.e. have the max/min display values (between 0.0 and 1.0)
	 */
	public void autoSetDisplayRange(ChannelDisplayInfo info, double saturation) {
		autoSetDisplayRange(info, getHistogram(info), saturation, true);
	}

	
	ImageServer<BufferedImage> getServer() {
		return imageData == null ? null : imageData.getServer();
	}
	

	/**
	 * Returns a histogram for a ChannelInfo, or none if no histogram is available (e.g. the channel is RGB)
	 * @param info
	 * @return
	 */
	public Histogram getHistogram(ChannelDisplayInfo info) {
		if (info == null || histogramManager == null)
			return null;
		return histogramManager.getHistogram(getServer(), info);
	}

	
	
	/**
	 * Create a JSON representation of the main components of the current display.
	 * 
	 * @return
	 */
	private String toJSON() {
		return toJSON(false);
	}
	
	/**
	 * Create a JSON representation of the main components of the current display.
	 * 
	 * @param prettyPrint 
	 * @return
	 */
	public String toJSON(final boolean prettyPrint) {
		if (this.imageData == null)
			return null;
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
			if (info instanceof ModifiableChannelDisplayInfo) {
				if (minDisplay != null)
					((ModifiableChannelDisplayInfo)info).setMinDisplay(minDisplay);
				if (maxDisplay != null)
					((ModifiableChannelDisplayInfo)info).setMaxDisplay(maxDisplay);				
			}
			if (color != null && info instanceof DirectServerChannelInfo)
				((DirectServerChannelInfo)info).setLUTColor(color);
			return true;
		}
	}
	
	
	
	static class HistogramManager {
		
		private static int NUM_BINS = 1024;
		
		private Map<String, Histogram> map = Collections.synchronizedMap(new LinkedHashMap<>());
		
		private long timestamp;
		
		HistogramManager(long timestamp) {
			this.timestamp = timestamp;
		}
		
		long getTimestamp() {
			return timestamp;
		}
		
		List<BufferedImage> getRequiredImages(final ImageServer<BufferedImage> server) {
			// Request default thumbnails (at lowest available resolution)
			int nImages = server.nTimepoints() * server.nZSlices();
			return IntStream.range(0, nImages).parallel().mapToObj(i -> {
				int z = i % server.nZSlices();
				int t = i / server.nZSlices();
				try {
					return server.getDefaultThumbnail(z, t);
				} catch (IOException e) {
					logger.error("Error requesting default thumbnail for {} (z={}, t={})", server.getPath(), z, t);
					return null;
				}
			}).filter(img -> img != null).collect(Collectors.toList());
		}
		
		String getKey(final ChannelDisplayInfo channel) {
			return channel.getClass().getName() + "::" + channel.getName();
		}
		
		void ensureChannels(final ImageServer<BufferedImage> server, final List<ChannelDisplayInfo> channels) {
//			if (timestamp != server.getLastChangeTimestamp()) {
//				logger.warn("Timestamp changed for server!  Histograms will be rebuilt for {}", server.getPath());
//				map.clear();
//			}
			// Check what we might need to process
			List<SingleChannelDisplayInfo> channelsToProcess = new ArrayList<>();
			float serverMin = server.getMetadata().getMinValue().floatValue();
			float serverMax = server.getMetadata().getMaxValue().floatValue();
			
			for (ChannelDisplayInfo channel : channels) {
				Histogram histogram = map.get(getKey(channel));
				if (histogram != null) {
					if (channel instanceof ModifiableChannelDisplayInfo) {
						((ModifiableChannelDisplayInfo)channel).setMinMaxAllowed(
								(float)Math.min(0, histogram.getMinValue()), (float)histogram.getMaxValue());
					}
					continue;
				} else if (channel instanceof SingleChannelDisplayInfo) {
					channelsToProcess.add((SingleChannelDisplayInfo)channel);
					if (channel instanceof ModifiableChannelDisplayInfo) {
						((ModifiableChannelDisplayInfo)channel).setMinMaxAllowed(serverMin, serverMax);
					}
				} else
					map.put(getKey(channel), null);
			}
			if (channelsToProcess.isEmpty())
				return;
			
			logger.debug("Building {} histograms for {}", channelsToProcess.size(), server.getPath());
			long startTime = System.currentTimeMillis();

			// Request appropriate images to use for histogram
			List<BufferedImage> imgList = getRequiredImages(server);
			
			// Count number of pixels & estimate downsample factor
			int imgWidth, imgHeight;
			long nPixels = 0;
			double approxDownsample = 1;
			for (var img : imgList) {
				imgWidth = img.getWidth();
				imgHeight = img.getHeight();
				approxDownsample = (double)server.getWidth() / imgWidth;
				nPixels += ((long)imgWidth * (long)imgHeight);
			}
			
			if (nPixels > Integer.MAX_VALUE) {
				logger.warn("Too many pixels required for histogram ({})!  Will truncate to the first {} values", nPixels, Integer.MAX_VALUE);
				nPixels = Integer.MAX_VALUE;
			}
			float[] values = null; // Array needed for values for a particular channel
			float[] pixels = null; // Array needed for all values if there are multiple thumbnails
			if (imgList.isEmpty())
				values = new float[0];
			else if (imgList.size() > 1)
				pixels = new float[(int)nPixels];
			for (SingleChannelDisplayInfo channel : channelsToProcess) {
				int counter = 0;
				for (BufferedImage img : imgList) {
					values = channel.getValues(img, 0, 0, img.getWidth(), img.getHeight(), values);
					if (pixels != null) {
						System.arraycopy(values, 0, pixels, counter, Math.min(values.length, Integer.MAX_VALUE-counter));
					}
					counter += values.length;
					if (counter >= Integer.MAX_VALUE)
						break;
				}
				Histogram histogram = new Histogram(pixels == null ? values : pixels, NUM_BINS);
				
				// If we have more than an 8-bit image, set the display range according to actual values - with additional scaling if we downsampled
				if (channel instanceof ModifiableChannelDisplayInfo) {
					float scale = approxDownsample < 2 ? 1 : 1.5f;
					if (!histogram.isInteger() || Math.max(Math.abs(channel.getMaxAllowed()), Math.abs(channel.getMinAllowed())) > 4096) {
						((ModifiableChannelDisplayInfo)channel).setMinMaxAllowed(
								(float)Math.min(0, histogram.getMinValue()) * scale, (float)Math.max(0, histogram.getMaxValue()) * scale);
					}
				}
				
				map.put(getKey(channel), histogram);
			}
			long endTime = System.currentTimeMillis();
			logger.debug("Histograms built in {} ms", (endTime - startTime));
		}
		
		Histogram getHistogram(final ImageServer<BufferedImage> server, final ChannelDisplayInfo channel) {
			if (channel instanceof SingleChannelDisplayInfo) {
				// Always recompute histogram for mutable channels
				if (((SingleChannelDisplayInfo)channel).isMutable()) {
					map.remove(getKey(channel));
				}
			}
			ensureChannels(server, Collections.singletonList(channel));
			return map.get(getKey(channel));
		}
		
	}
	

}
