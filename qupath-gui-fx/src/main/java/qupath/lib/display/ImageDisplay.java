/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.display;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import javafx.beans.binding.Bindings;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ListChangeListener;
import qupath.lib.analysis.stats.Histogram;
import qupath.lib.color.ColorTransformer;
import qupath.lib.color.ColorTransformer.ColorTransformMethod;
import qupath.lib.common.ColorTools;
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

	/**
	 * TODO: This needs to be updated to make it properly observable and consistent.
	 *       In particular, ImageData changes aren't observable.
	 *       The problem is that changing the ImageData results in changes to available
	 *       channels, and both should be viewable immediately.
	 *       Storing internally as separate properties, then updating their values, doesn't
	 *       work because then listeners bound to one property can be notified before the
	 *       other changes.
	 */

	private static final Logger logger = LoggerFactory.getLogger(ImageDisplay.class);
	
	/**
	 * Identifier used when storing/retrieving display settings from ImageData properties.
	 */
	private static final String PROPERTY_DISPLAY = ImageDisplay.class.getName();

	// Lists to store the different kinds of channels we might need
	// Pack RGB (all channels in one image, only adjustable together)
	private RGBDirectChannelInfo rgbDirectChannelInfo;
	// Normalized optical density channels; useful to find the 'predominant' color when selecting stain vectors
	private RGBNormalizedChannelInfo rgbNormalizedChannelInfo;
	// Direct (editable) RGB channels
	private List<ChannelDisplayInfo> rgbDirectChannels = new ArrayList<>();
	// Split (uneditable) RGB channels
	private List<ChannelDisplayInfo> rgbSplitChannels = new ArrayList<>();
	// Hue, Saturation, Value
	private List<ChannelDisplayInfo> rgbHsvChannels = new ArrayList<>();
	// Color-deconvolved channels
	private List<ChannelDisplayInfo> rgbBrightfieldChannels = new ArrayList<>();
	// Chromaticity channels
	private List<ChannelDisplayInfo> rgbChromaticityChannels = new ArrayList<>();

	// Image & color transform-related variables
	private BooleanProperty useGrayscaleLuts = new SimpleBooleanProperty();
	private BooleanProperty useInvertedBackground = new SimpleBooleanProperty(false);

	private ImageData<BufferedImage> imageData;
	private ObservableList<ChannelDisplayInfo> channelOptions = FXCollections.observableArrayList();

	private ObservableList<ChannelDisplayInfo> selectedChannels = FXCollections.observableArrayList();
	private ChannelDisplayInfo lastSelectedChannel = null;

	private LongProperty changeTimestamp = new SimpleLongProperty(System.currentTimeMillis());
	
	private ObjectBinding<ChannelDisplayMode> displayMode = Bindings.createObjectBinding(() -> calculateDisplayMode(),
			useGrayscaleLutProperty(), useInvertedBackgroundProperty());

	private static Map<String, HistogramManager> cachedHistograms = Collections.synchronizedMap(new HashMap<>());
	private HistogramManager histogramManager = null;
	private List<BufferedImage> imagesForHistograms = new ArrayList<>(); // Cache images needed to recompute histograms

	private static BooleanProperty showAllRGBTransforms = PathPrefs.createPersistentPreference("showAllRGBTransforms", true);

	// Used to store the channel colors before switching to grayscale, so this can be restored later
	// We use the names rather than the actual channels so that the colors are preserved if the image is changed,
	// and we still do our best to preserve the matching channels
	private transient Set<String> beforeGrayscaleChannels = new HashSet<>();

	// Used to store the channel that should be selected when switching to grayscale (optional).
	// This is useful to develop more intuitive interfaces & prevent surprises when switching to grayscale mode.
	// For example, this could be in the selected item in a list or table (independent of whether the channel is
	// shown or hidden).
	private transient ObjectProperty<ChannelDisplayInfo> switchToGrayscaleChannel = new SimpleObjectProperty<>();

	// Flag when the image data is being set, to prevent other changes or events being processed
	private boolean settingImageData = false;

	/**
	 * Constructor.
	 */
	public ImageDisplay() {
		useGrayscaleLuts.addListener((v, o, n) -> {
			if (n) {
				// Snapshot the names of channels active before switching to grayscale
				selectedChannels.stream().map(ChannelDisplayInfo::getName).forEach(beforeGrayscaleChannels::add);
				var switchToGrayscale = switchToGrayscaleChannel.get();
				if (switchToGrayscale != null) {
					if (!availableChannels.contains(switchToGrayscale)) {
						// If we have a different object to represent the channel, search for it by name -
						// because we need to be careful not to select a channel that isn't 'available'
						// See https://github.com/qupath/qupath/pull/1482
						String switchToGrayscaleChannelName = switchToGrayscale.getName();
						switchToGrayscale = availableChannels.stream()
								.filter(c -> Objects.equals(c.getName(), switchToGrayscaleChannelName))
								.findFirst().orElse(null);
					}
					if (switchToGrayscale != null)
						setChannelSelected(switchToGrayscale, true);
				}
				if (lastSelectedChannel != null)
					setChannelSelected(lastSelectedChannel, true);
				else if (!selectedChannels.isEmpty())
					setChannelSelected(selectedChannels.get(0), true);
				else if (!availableChannels.isEmpty()) {
					setChannelSelected(availableChannels.get(0), true);
				}
			} else {
				// Restore the channels that were active before switching to grayscale, if possible
				if (!beforeGrayscaleChannels.isEmpty()) {
					var channelsToSelect = availableChannels().stream().filter(c -> beforeGrayscaleChannels.contains(c.getName())).toList();
					if (!channelsToSelect.isEmpty()) {
						selectedChannels.setAll(channelsToSelect);
					}
				}
				beforeGrayscaleChannels.clear();
			}
			saveChannelColorProperties();
		});
		useInvertedBackground.addListener((v, o, n) -> {
			saveChannelColorProperties();
		});

		selectedChannels.addListener((ListChangeListener<ChannelDisplayInfo>) c -> {
			if (c.getList().contains(null)) {
				logger.warn("Null channel selected");
			}
		});
	}

	/**
	 * Create a new image display, and set the specified image data.
	 * @param imageData
	 * @return
	 * @throws IOException
	 */
	public static ImageDisplay create(ImageData<BufferedImage> imageData) throws IOException {
		var display = new ImageDisplay();
		if (imageData != null)
			display.setImageData(imageData, false);
		return display;
	}


	/**
	 * Set the {@link ImageData} to a new value
	 * @param imageData image data that should how be displayed
	 * @param retainDisplaySettings if true, retain the same display settings as for the previous image if possible 
	 *                              (i.e. the images have similar channels)
	 */
	public void setImageData(ImageData<BufferedImage> imageData, boolean retainDisplaySettings) throws IOException {
		if (this.imageData == imageData)
			return;

		settingImageData = true;
		try {
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
			this.imagesForHistograms = imageData == null ? Collections.emptyList() : getImagesForHistogram(imageData.getServer());
			updateChannelOptions(true);
			updateHistogramMap();
			if (imageData != null) {
				// Load any existing color properties
				loadChannelColorProperties();
				// Update from the last image, if required
				if (lastDisplayJSON != null && !lastDisplayJSON.isEmpty()) {
					updateFromJSON(lastDisplayJSON);
				}
			}
		} finally {
			settingImageData = false;
			changeTimestamp.set(System.currentTimeMillis());
		}
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
	 * Property that specifies whether the background should be inverted (i.e. to make fluorescence resemble brightfield, and vice versa)
	 * @return
	 */
	public BooleanProperty useInvertedBackgroundProperty() {
		return useInvertedBackground;
	}
	
	/**
	 * Get the value of {@link #useInvertedBackgroundProperty()}
	 * @return
	 */
	public boolean useInvertedBackground() {
		return useInvertedBackground.get();
	}
	
	/**
	 * Get the value of {@link #useInvertedBackgroundProperty()}
	 * @return
	 */
	public ObjectBinding<ChannelDisplayMode> displayMode() {
		return displayMode;
	}
	
	private ChannelDisplayMode calculateDisplayMode() {
		if (useGrayscaleLuts()) {
			if (useInvertedBackground())
				return ChannelDisplayMode.INVERTED_GRAYSCALE;
			else
				return ChannelDisplayMode.GRAYSCALE;
		} else if (useInvertedBackground())
			return ChannelDisplayMode.INVERTED_COLOR;
		else
			return ChannelDisplayMode.COLOR;
	}

	/**
	 * Set the value of {@link #useInvertedBackgroundProperty()}
	 * @param useInvertedBackground
	 */
	public void setUseInvertedBackground(boolean useInvertedBackground) {
		this.useInvertedBackground.set(useInvertedBackground);
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

		rgbSplitChannels.clear();
		rgbDirectChannels.clear();
		rgbHsvChannels.clear();
		rgbBrightfieldChannels.clear();
		rgbChromaticityChannels.clear();
		
		if (imageData == null)
			return;
		
		rgbDirectChannelInfo = new RGBDirectChannelInfo(imageData);
		rgbNormalizedChannelInfo = new RGBNormalizedChannelInfo(imageData);

		// Add simple channel separation (changed for v0.6.0)
		rgbSplitChannels.add(new RBGColorTransformInfo(imageData, ColorTransformMethod.Red, false));
		rgbSplitChannels.add(new RBGColorTransformInfo(imageData, ColorTransformMethod.Green, false));
		rgbSplitChannels.add(new RBGColorTransformInfo(imageData, ColorTransformMethod.Blue, false));

		rgbDirectChannels.add(new DirectServerChannelInfo(imageData, 0));
		rgbDirectChannels.add(new DirectServerChannelInfo(imageData, 1));
		rgbDirectChannels.add(new DirectServerChannelInfo(imageData, 2));

		rgbHsvChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Hue, false));
		rgbHsvChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Saturation, false));
		rgbHsvChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.RGB_mean, false));

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

	/**
	 * Property indicating which channel should be used if {@link #useGrayscaleLutProperty()} is turned on.
	 * This is useful to develop more intuitive interfaces and prevent surprises when switching to grayscale mode.
	 * <p>
	 * Settings this value does not have any immediate effect on whether channels are selected or not, but rather it
	 * is only used when switching to grayscale mode.
	 * @return
	 * @since v0.5.0
	 */
	public ObjectProperty<ChannelDisplayInfo> switchToGrayscaleChannelProperty() {
		return switchToGrayscaleChannel;
	}

	/**
	 * Set the value of {@link #switchToGrayscaleChannelProperty()}.
	 * @param channel
	 * @since v0.5.0
	 */
	public void setSwitchToGrayscaleChannel(ChannelDisplayInfo channel) {
		switchToGrayscaleChannel.set(channel);
	}

	/**
	 * Get the value of {@link #switchToGrayscaleChannelProperty()}.
	 * @return
	 * @since v0.5.0
	 */
	public ChannelDisplayInfo getSwitchToGrayscaleChannel() {
		return switchToGrayscaleChannel.get();
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
			if (!switchToGrayscaleChannel.isBound())
				switchToGrayscaleChannel.set(null);
			return;
		}
		
		List<ChannelDisplayInfo> tempChannelOptions = new ArrayList<>();
		List<ChannelDisplayInfo> tempSelectedChannels = new ArrayList<>(this.selectedChannels);
		if (server.isRGB()) {
			createRGBChannels(imageData);
			if (imageData.isFluorescence()) {
				tempChannelOptions.addAll(rgbDirectChannels);
			} else {
				// Remove joint RGB display as an option for fluorescence
				tempChannelOptions.add(rgbDirectChannelInfo);
				// Add color deconvolution options if we have a brightfield image
				if (imageData.isBrightfield()) {
					tempChannelOptions.addAll(rgbBrightfieldChannels);
					tempChannelOptions.add(rgbNormalizedChannelInfo);
				}
				tempChannelOptions.addAll(rgbSplitChannels);
				if (showAllRGBTransforms.get()) {
					// Change v0.6.0 - don't show all channels for fluorescence (as they are more distracting than helpful)
					// If they are needed, using ImageType.OTHER
					tempChannelOptions.addAll(rgbHsvChannels);
					tempChannelOptions.addAll(rgbChromaticityChannels);
				}
			}
			// Remove any invalid channels
			tempSelectedChannels.retainAll(tempChannelOptions);
			// Select the original channel (RGB)
			if (tempSelectedChannels.isEmpty()) {
				// Default to all channels
				if (!useGrayscaleLuts.get() && tempChannelOptions.stream().allMatch(c -> c instanceof DirectServerChannelInfo))
					tempSelectedChannels.addAll(tempChannelOptions);
				else
					tempSelectedChannels.add(tempChannelOptions.getFirst());
			}
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
				if (option instanceof DirectServerChannelInfo directChannel && c < server.nChannels()) {
					var channel = server.getChannel(c);
					if (!Objects.equals(option.getColor(), channel.getColor())) {
						directChannel.setLUTColor(channel.getColor());
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
				tempSelectedChannels.add(tempChannelOptions.getFirst());
			else if (useColorLUTs())
				tempSelectedChannels.addAll(tempChannelOptions);
			selectedChannels.clear();
		}
		channelOptions.setAll(tempChannelOptions);
		selectedChannels.setAll(tempSelectedChannels);

		// Attempt to preserve the switchToGrayscaleChannel, if possible
		if (!switchToGrayscaleChannel.isBound()) {
			var switchToGrayscale = switchToGrayscaleChannel.get();
			if (switchToGrayscale != null) {
				if (!channelOptions.contains(switchToGrayscale))
					switchToGrayscaleChannel.set(
							channelOptions.stream().filter(c -> Objects.equals(c.getName(), switchToGrayscale.getName())).findFirst().orElse(null)
					);
			}
		}
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
		if (property instanceof String json) {
			try {
				updateFromJSON(json);
				return true;
			} catch (Exception e) {
				logger.warn("Unable to parse display settings from {}", property);
			}
		}
		
		// Legacy code for the old color-only-storing property approach
		int n = 0;
		for (ChannelDisplayInfo info : channelOptions) {
			if (info instanceof DirectServerChannelInfo multiInfo) {
				Integer colorOld = multiInfo.getColor();
				Object colorNew = imageData.getProperty("COLOR_CHANNEL:" + info.getName());
				if (colorNew instanceof Integer colorInt && colorInt.equals(colorOld)) {
					multiInfo.setLUTColor(colorInt);
					n++;
				}
			}
		}
		if (n == 1)
			logger.info("Loaded color channel info for one channel");
		else if (n > 1)
            logger.info("Loaded color channel info for {} channels", n);
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
		if (info instanceof ModifiableChannelDisplayInfo modifiableInfo) {
			if (modifiableInfo.getMinDisplay() == minDisplay && modifiableInfo.getMaxDisplay() == maxDisplay)
				return;
			modifiableInfo.setMinDisplay(minDisplay);
			modifiableInfo.setMaxDisplay(maxDisplay);
		}
		if (fireUpdate && channelOptions.contains(info))
			saveChannelColorProperties();		
	}
	
	
	/**
	 * Save color channels in the ImageData properties.  This lets them be deserialized later.
	 */
	public void saveChannelColorProperties() {
		// Don't process a change if we're still setting the image data
		if (settingImageData)
			return;
		if (imageData == null) {
			logger.warn("Cannot save color channel properties - no ImageData available");
			return;
		}
		// Store the current display settings in the ImageData
		imageData.setProperty(PROPERTY_DISPLAY, toJSON(false));
		changeTimestamp.set(System.currentTimeMillis());
	}


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
		BufferedImage imgResult = applyTransforms(imgInput, imgOutput, selectedChannels, displayMode().getValue());
		return imgResult;
	}
	
	/**
	 * Convert an image to RGB by applying the specified {@linkplain ChannelDisplayInfo ChannelDisplayInfos}.
	 * 
	 * @param imgInput
	 * @param imgOutput
	 * @param selectedChannels
	 * @param useGrayscaleLuts
	 * @return
	 * @deprecated use instead {@link #applyTransforms(BufferedImage, BufferedImage, List, ChannelDisplayMode)}
	 */
	@Deprecated
	public static BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput, List<? extends ChannelDisplayInfo> selectedChannels, boolean useGrayscaleLuts) {
		return applyTransforms(imgInput, imgOutput, selectedChannels, useGrayscaleLuts ? ChannelDisplayMode.GRAYSCALE : ChannelDisplayMode.COLOR);
	}
	
	/**
	 * Convert an image to RGB by applying the specified {@linkplain ChannelDisplayInfo ChannelDisplayInfos} and {@link ChannelDisplayMode}.
	 * 
	 * @param imgInput the input image to transform
	 * @param imgOutput optional output image (must be the same size as the input image, and RGB)
	 * @param selectedChannels the channels to use
	 * @param mode the mode used to determine RGB colors for each channel
	 * @return an RGB image determined by transforming the input image using the specified channels
	 */
	public static BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput, List<? extends ChannelDisplayInfo> selectedChannels, ChannelDisplayMode mode) {
		int width = imgInput.getWidth();
		int height = imgInput.getHeight();

		if (imgOutput == null || imgOutput.getWidth() != width || imgOutput.getHeight() != height) {
			//			imgOutput = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(width, height);
			imgOutput = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		}
		
		// Ensure we have a mode
		if (mode == null)
			mode = ChannelDisplayMode.COLOR;
		
		boolean invertBackground = mode.invertColors();
		boolean isGrayscale = mode == ChannelDisplayMode.GRAYSCALE || mode == ChannelDisplayMode.INVERTED_GRAYSCALE;
				
		// Check if we have any changes to make - if not, just copy the image
		// Sometimes the first entry of selectedChannels was null... not sure why... this test is therefore to paper over the cracks...
		if (selectedChannels.size() == 1 && (selectedChannels.get(0) == null || !selectedChannels.get(0).doesSomething()) && !invertBackground && !isGrayscale) {
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
					pixels = info.getRGB(imgInput, pixels, mode);
					firstChannel = false;
				} else {
					info.updateRGBAdditive(imgInput, pixels, mode);
				}
			}
		} catch (Exception e) {
			logger.error("Error extracting pixels for display", e);
		}

		// If we have no channels, we might have no pixels
		// But we allow that to happen because we may still have to invert
		if (pixels == null)
			pixels = new int[imgOutput.getWidth() * imgOutput.getHeight()];

		// Apply inversion
		if (mode.invertColors()) {
			invertRGB(pixels);
		}
		imgOutput.getRaster().setDataElements(0, 0, imgOutput.getWidth(), imgOutput.getHeight(), pixels);
		return imgOutput;
	}
	
	
	private static void invertRGB(int[] pixels) {
		for (int i = 0; i < pixels.length; i++) {
			int val = pixels[i];
			int r = ColorTools.red(val);
			int g = ColorTools.green(val);
			int b = ColorTools.blue(val);
			pixels[i] = ColorTools.packRGB(255 - r, 255 - g, 255 - b);
		}
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





	private void updateHistogramMap() throws IOException {
		ImageServer<BufferedImage> server = imageData == null ? null : imageData.getServer();
		if (server == null) {
			histogramManager = null;
			return;
		}
		
		histogramManager = cachedHistograms.get(server.getPath());
		if (histogramManager == null) {
			histogramManager = new HistogramManager(0L);
			histogramManager.ensureChannels(server, channelOptions, imagesForHistograms);
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


	private List<BufferedImage> getImagesForHistogram(final ImageServer<BufferedImage> server) throws IOException {
		if (server == null)
			return Collections.emptyList();
		// Request default thumbnails (at lowest available resolution)
		int nImages = server.nTimepoints() * server.nZSlices();
		// Try to get the first image 'normally', so that if there is an exception it can be handled
		var list = new ArrayList<BufferedImage>();
		list.add(server.getDefaultThumbnail(0, 0));
		if (nImages > 1) {
			// If we have multiple images, we want to parallelize & return as much as we can
			IntStream.range(1, nImages).parallel().mapToObj(i -> {
				int z = i % server.nZSlices();
				int t = i / server.nZSlices();
				try {
					return server.getDefaultThumbnail(z, t);
				} catch (IOException e) {
					logger.error("Error requesting thumbnail for {} (z={}, t={})", server.getPath(), z, t, e);
					return null;
				}
			}).filter(img -> img != null).forEach(list::add);
		}
		return list;
	}


	private void autoSetDisplayRange(ChannelDisplayInfo info, Histogram histogram, double saturation, boolean fireUpdate) {
		if (histogram == null) {
			// TODO: Look at other times whenever no histogram will be provided
			if (!(info instanceof RGBDirectChannelInfo))
				logger.warn("Cannot set display range for {} - no histogram found", info);
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
		return histogramManager.getHistogram(getServer(), info, imagesForHistograms);
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
	 * Check if an image display is 'compatible' with this one.
	 * Compatible means that they have the same number of channels, and the same channel names.
	 * This may be used p
	 * @param display
	 * @return
	 */
	public boolean isCompatible(ImageDisplay display) {
		var available = availableChannels();
		var other = display.availableChannels();
		if (available.size() != other.size())
			return false;
		for (int i = 0; i < available.size(); i++) {
			if (!Objects.equals(available.get(i).getClass(), other.get(i).getClass()))
				return false;
			if (!Objects.equals(available.get(i).getName(), other.get(i).getName()))
				return false;
		}
		return true;
	}

	/**
	 * Update the current display based upon a different display.
	 * This only makes changes if {@link #isCompatible(ImageDisplay)} returns true.
	 * <p>
	 * This method exists to make it easier to sync display settings across viewers.
	 * @param display
	 * @return
	 */
	public boolean updateFromDisplay(ImageDisplay display) {
		if (this == display)
			return false;
		if (isCompatible(display)) {
			useGrayscaleLuts.set(display.useGrayscaleLuts());
			useInvertedBackground.set(display.useInvertedBackground());
			if (updateFromJSON(display.toJSON()))
				saveChannelColorProperties();
			return true;
		}
		return false;
	}
	
	/**
	 * Update current display info based on deserializing a JSON String.
	 * This will match as many channels as possible.
	 * @param json
	 * @return true if changes were made, false otherwise
	 */
	boolean updateFromJSON(final String json) {
		Gson gson = new Gson();
		Type type = new TypeToken<List<JsonHelperChannelInfo>>(){}.getType();
		List<JsonHelperChannelInfo> helperList = gson.fromJson(json, type);
		// Try updating everything
		List<ChannelDisplayInfo> newSelectedChannels = new ArrayList<>();
		boolean changes = false;
		for (JsonHelperChannelInfo helper : helperList) {
			for (ChannelDisplayInfo info : channelOptions) {
				if (helper.matches(info)) {
					// Set the min/max display & color if needed
					if (helper.updateInfo(info)) {
						changes = true;
					}
					// Store whether the channel is selected
					if (Boolean.TRUE.equals(helper.selected)) {
						newSelectedChannels.add(info);
					}
				}
			}
		}
		if (!newSelectedChannels.equals(selectedChannels)) {
			selectedChannels.setAll(newSelectedChannels);
			changes = true;
		}
		return changes;
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
		 * @return true if changes were made, false otherwise
		 */
		boolean updateInfo(final ChannelDisplayInfo info) {
			if (!matches(info))
				return false;
			boolean changes = false;
			if (info instanceof ModifiableChannelDisplayInfo modifiableInfo) {
				if (minDisplay != null && minDisplay != modifiableInfo.getMinDisplay()) {
					modifiableInfo.setMinDisplay(minDisplay);
					changes = true;
				}
				if (maxDisplay != null && maxDisplay != modifiableInfo.getMaxDisplay()) {
					modifiableInfo.setMaxDisplay(maxDisplay);
					changes = true;
				}
			}
			if (color != null && info instanceof DirectServerChannelInfo directInfo) {
				if (!Objects.equals(color, directInfo.getColor())) {
					directInfo.setLUTColor(color);
					changes = true;
				}
			}
			return changes;
		}
	}
	
	
	
	static class HistogramManager {
		
		private static int NUM_BINS = 1024;
		
		private Map<String, Histogram> map = Collections.synchronizedMap(new LinkedHashMap<>());

		private List<BufferedImage> requiredImages;
		
		private long timestamp;
		
		HistogramManager(long timestamp) {
			this.timestamp = timestamp;
		}
		
		long getTimestamp() {
			return timestamp;
		}
		
		String getKey(final ChannelDisplayInfo channel) {
			return channel.getClass().getName() + "::" + channel.getName();
		}
		
		void ensureChannels(final ImageServer<BufferedImage> server, final List<ChannelDisplayInfo> channels, final List<BufferedImage> imgList) {

			// Check what we might need to process
			List<SingleChannelDisplayInfo> channelsToProcess = new ArrayList<>();
			float serverMin = server.getMetadata().getMinValue().floatValue();
			float serverMax = server.getMetadata().getMaxValue().floatValue();
			
			for (ChannelDisplayInfo channel : channels) {
				Histogram histogram = map.get(getKey(channel));
				if (histogram != null) {
					 // We have the histogram
					if (channel instanceof ModifiableChannelDisplayInfo modifiableChannel) {
						modifiableChannel.setMinMaxAllowed(
								(float)Math.min(0, histogram.getMinValue()), (float)histogram.getMaxValue());
					}
					continue;
				} else if (channel instanceof SingleChannelDisplayInfo singleChannel) {
					// We need to compute the histogram
					channelsToProcess.add(singleChannel);
					if (channel instanceof ModifiableChannelDisplayInfo modifiableChannel) {
						modifiableChannel.setMinMaxAllowed(serverMin, serverMax);
					}
				} else {
					// A histogram doesn't exist for the channel, and we can't compute one
					map.put(getKey(channel), null);
				}
			}

			if (channelsToProcess.isEmpty() || imgList == null || imgList.isEmpty())
				return;
			
			logger.debug("Building {} histograms for {}", channelsToProcess.size(), server.getPath());
			long startTime = System.currentTimeMillis();

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
		
		Histogram getHistogram(final ImageServer<BufferedImage> server, final ChannelDisplayInfo channel, final List<BufferedImage> images) {
			if (channel instanceof SingleChannelDisplayInfo singleChannel) {
				// Always recompute histogram for mutable channels
				if (singleChannel.isMutable()) {
					map.remove(getKey(channel));
				}
			}
			ensureChannels(server, Collections.singletonList(channel), images);
			return map.get(getKey(channel));
		}
		
	}
	

}
