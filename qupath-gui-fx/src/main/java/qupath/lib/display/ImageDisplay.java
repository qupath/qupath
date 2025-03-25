/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2025 QuPath developers, The University of Edinburgh
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ObservableValue;
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
import qupath.lib.common.ColorTools;
import qupath.lib.display.ChannelDisplayInfo.ModifiableChannelDisplayInfo;
import qupath.lib.gui.images.stores.AbstractImageRenderer;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.RegionRequest;

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

	// Image & color transform-related variables
	private final BooleanProperty useGrayscaleLuts = new SimpleBooleanProperty();
	private final BooleanProperty useInvertedBackground = new SimpleBooleanProperty(false);

	private ImageData<BufferedImage> imageData;
	private ChannelManager channelManager = null;

	private final ObservableList<ChannelDisplayInfo> availableChannels = FXCollections.observableArrayList();
	private final ObservableList<ChannelDisplayInfo> availableChannelsReadOnly = FXCollections.unmodifiableObservableList(availableChannels);

	private final ObservableList<ChannelDisplayInfo> selectedChannels = FXCollections.observableArrayList();
	private final ObservableList<ChannelDisplayInfo> selectedChannelsReadOnly = FXCollections.unmodifiableObservableList(selectedChannels);
	private ChannelDisplayInfo lastSelectedChannel = null;

	private final AtomicLong eventCount = new AtomicLong(0L);
	private final LongProperty eventCountProperty = new SimpleLongProperty(eventCount.get());
	
	private final ObjectBinding<ChannelDisplayMode> displayMode = Bindings.createObjectBinding(this::calculateDisplayMode,
			useGrayscaleLutProperty(), useInvertedBackgroundProperty());

	private static final Map<String, HistogramManager> cachedHistograms = Collections.synchronizedMap(new HashMap<>());
	private HistogramManager histogramManager = null;

	private static final Comparator<RegionRequest> regionComparator = Comparator.comparing(RegionRequest::getPath)
			.thenComparingInt(RegionRequest::getZ)
			.thenComparingInt(RegionRequest::getT)
			.thenComparingInt(RegionRequest::getX)
			.thenComparingInt(RegionRequest::getY)
			.thenComparingInt(RegionRequest::getWidth)
			.thenComparingInt(RegionRequest::getHeight)
			.thenComparingDouble(RegionRequest::getDownsample);

	private Map<RegionRequest, BufferedImage> imagesForHistograms = new TreeMap<>(regionComparator); // Cache images needed to recompute histograms

	private static final BooleanProperty showAllRGBTransforms = PathPrefs.createPersistentPreference("showAllRGBTransforms", true);

	// Used to store the channel colors before switching to grayscale, so this can be restored later
	// We use the names rather than the actual channels so that the colors are preserved if the image is changed,
	// and we still do our best to preserve the matching channels
	private final transient Set<String> beforeGrayscaleChannels = new HashSet<>();

	// Used to store the channel that should be selected when switching to grayscale (optional).
	// This is useful to develop more intuitive interfaces & prevent surprises when switching to grayscale mode.
	// For example, this could be in the selected item in a list or table (independent of whether the channel is
	// shown or hidden).
	private final transient ObjectProperty<ChannelDisplayInfo> switchToGrayscaleChannel = new SimpleObjectProperty<>();

	// Flag when the image data is being set, to prevent other changes or events being processed
	private boolean settingImageData = false;

	/**
	 * Create a new image display, and set the specified image data.
	 * @param imageData the image to set initially; may be null
	 * @return a new instance
	 * @throws IOException if an exception occurs when trying to set the image
	 */
	public static ImageDisplay create(ImageData<BufferedImage> imageData) throws IOException {
		var display = new ImageDisplay();
		if (imageData != null)
			display.setImageData(imageData, false);
		return display;
	}

	/**
	 * Constructor.
	 */
	public ImageDisplay() {
		useGrayscaleLuts.addListener(this::handleUseGrayscaleLutsChange);
		useInvertedBackground.addListener(this::handleInvertedBackgroundChange);
		selectedChannels.addListener(this::handleSelectedChannelsChange);
	}

	private void handleInvertedBackgroundChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
		saveChannelColorProperties();
	}

	private void handleSelectedChannelsChange(ListChangeListener.Change<? extends ChannelDisplayInfo> change) {
		if (change.getList().contains(null)) {
			logger.warn("Null channel selected");
		}
	}

	/**
	 * Set the {@link ImageData} to a new value
	 * @param imageData image data that should be displayed
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
			String lastDisplayJSON;
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
			this.imagesForHistograms = imageData == null ? Collections.emptyMap() : getImagesForHistogram(imageData.getServer());
			if (imageData != null)
				channelManager = new ChannelManager(imageData);
			else
				channelManager = null;

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
			incrementEventCount();
		}
	}

	/**
	 * Increment the event count property.
	 * Listeners may then respond, e.g. to fire a repaint operation.
	 */
	private void incrementEventCount() {
		eventCountProperty.set(eventCount.incrementAndGet());
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

	private void handleUseGrayscaleLutsChange(ObservableValue<? extends Boolean> observable, Boolean oldValue, Boolean newValue) {
		if (newValue) {
			// Snapshot the names of channels active before switching to grayscale
			selectedChannels.stream().map(ChannelDisplayInfo::getName).forEach(beforeGrayscaleChannels::add);
			var switchToGrayscale = switchToGrayscaleChannel.get();
			if (switchToGrayscale != null) {
				if (!availableChannelsReadOnly.contains(switchToGrayscale)) {
					// If we have a different object to represent the channel, search for it by name -
					// because we need to be careful not to select a channel that isn't 'available'
					// See https://github.com/qupath/qupath/pull/1482
					String switchToGrayscaleChannelName = switchToGrayscale.getName();
					switchToGrayscale = availableChannelsReadOnly.stream()
							.filter(c -> Objects.equals(c.getName(), switchToGrayscaleChannelName))
							.findFirst().orElse(null);
				}
				if (switchToGrayscale != null)
					setChannelSelected(switchToGrayscale, true);
			}
			if (lastSelectedChannel != null)
				setChannelSelected(lastSelectedChannel, true);
			else if (!selectedChannels.isEmpty())
				setChannelSelected(selectedChannels.getFirst(), true);
			else if (!availableChannelsReadOnly.isEmpty()) {
				setChannelSelected(availableChannelsReadOnly.getFirst(), true);
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
	 * Get the event count, which is here used as an alternative to a timestamp.
	 * @return
	 */
	@Override
	public long getLastChangeTimestamp() {
		return eventCountProperty.get();
	}
	
	/**
	 * Counter for the number of display changes that have been made.
	 * <p>
	 * Note: This replaces a timestamp property used before v0.6.0.
	 *       It should be more reliable, because changes occurring in quick succession can still be captured -
	 *       whereas previously any changes that were faster than the millisecond close might get lost.
	 * @return
	 */
	public LongProperty eventCountProperty() {
		return eventCountProperty;
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
		
		var metadata = imageData == null ? null : imageData.getServerMetadata();
		if (metadata == null || channelManager == null) {
			selectedChannels.clear();
			availableChannels.clear();
			if (!switchToGrayscaleChannel.isBound())
				switchToGrayscaleChannel.set(null);
			return;
		}
		
		List<ChannelDisplayInfo> tempChannelOptions = channelManager.getAvailableChannels(showAllRGBTransforms.get());
		List<ChannelDisplayInfo> tempSelectedChannels = selectedChannels.stream()
				.filter(tempChannelOptions::contains)
				.collect(Collectors.toCollection(ArrayList::new));
		// Select all the channels
		if (serverChanged || tempSelectedChannels.isEmpty()) {
			tempSelectedChannels = new ArrayList<>();
			if (tempChannelOptions.getFirst() instanceof RGBDirectChannelInfo || !useColorLUTs()) {
				tempSelectedChannels.add(tempChannelOptions.getFirst());
			} else if (useColorLUTs())
				tempSelectedChannels.addAll(
						tempChannelOptions.stream()
						.filter(c -> c instanceof DirectServerChannelInfo).toList());
		}

		if (!availableChannels.equals(tempChannelOptions)) {
			availableChannels.setAll(tempChannelOptions);
		}
		if (!selectedChannels.equals(tempSelectedChannels)) {
			selectedChannels.setAll(tempSelectedChannels);
		}

		// Ensure channel colors are set
		ensureChannelColorsUpdated(imageData.getServerMetadata());

		// Attempt to preserve the switchToGrayscaleChannel, if possible
		if (!switchToGrayscaleChannel.isBound()) {
			var switchToGrayscale = switchToGrayscaleChannel.get();
			if (switchToGrayscale != null) {
				if (!availableChannels.contains(switchToGrayscale))
					switchToGrayscaleChannel.set(
							availableChannels.stream().filter(c -> Objects.equals(c.getName(), switchToGrayscale.getName())).findFirst().orElse(null)
					);
			}
		}
	}

	private void ensureChannelColorsUpdated(ImageServerMetadata metadata) {
		boolean colorsUpdated = false;
		for (var option : availableChannels) {
			if (option instanceof DirectServerChannelInfo directChannel) {
				var channel = metadata.getChannel(directChannel.getChannel());
				if (!Objects.equals(option.getColor(), channel.getColor())) {
					directChannel.setLUTColor(channel.getColor());
					colorsUpdated = true;
				}
			}
		}
		if (colorsUpdated) {
			saveChannelColorProperties();
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
		for (ChannelDisplayInfo info : availableChannels) {
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
	 * <p>
	 * The benefit of calling this method is that it will update the ImageData metadata if appropriate.
	 * 
	 * @param info
	 * @param minDisplay
	 * @param maxDisplay
	 */
	public void setMinMaxDisplay(final ChannelDisplayInfo info , float minDisplay, float maxDisplay) {
		setMinMaxDisplay(info, minDisplay, maxDisplay, true);
	}
	
	private void setMinMaxDisplay(final ChannelDisplayInfo info , float minDisplay, float maxDisplay, boolean fireUpdate) {
		if (info instanceof ModifiableChannelDisplayInfo modifiableInfo) {
			if (modifiableInfo.getMinDisplay() == minDisplay && modifiableInfo.getMaxDisplay() == maxDisplay)
				return;
			modifiableInfo.setMinDisplay(minDisplay);
			modifiableInfo.setMaxDisplay(maxDisplay);
		}
		if (fireUpdate && availableChannels.contains(info))
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
		incrementEventCount();
	}


	/**
	 * Read-only observable list containing the channels currently selected for display.
	 * @return the selected channels
	 * @see #availableChannels()
	 */
	public ObservableList<ChannelDisplayInfo> selectedChannels() {
		return selectedChannelsReadOnly;
	}

	/**
	 * Read-only observable list containing the channels currently available for display.
	 * @return the available channels
	 * @see #selectedChannels()
	 */
	public ObservableList<ChannelDisplayInfo> availableChannels() {
		return availableChannelsReadOnly;
	}


	/**
	 * Set the selection of a channel on or off.
	 * <p>
	 * If a channel's isAdditive() method returns false, all other selected channels will be cleared.
	 * Otherwise, other selected channels will be cleared if they are non-additive - but kept if they are additive
	 * (and therefore can be sensibly displayed in combination with this channel).
	 * 
	 * @param channel the channel
	 * @param selected true if the channel should be selected, false if it should not
	 */
	public void setChannelSelected(ChannelDisplayInfo channel, boolean selected) {
		// Try to minimize the number of events fired
		List<ChannelDisplayInfo> tempSelectedChannels = new ArrayList<>(selectedChannels);
		if (selected) {
			// If this channel can't be combined with existing channels, clear the existing ones
			if (!useColorLUTs() || !channel.isAdditive() || (!tempSelectedChannels.isEmpty()) && !tempSelectedChannels.getFirst().isAdditive())
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
			channel = availableChannels.getFirst();
			tempSelectedChannels.add(channel);
			lastSelectedChannel = channel;
		}
		if (lastSelectedChannel == null && !tempSelectedChannels.isEmpty())
			lastSelectedChannel = tempSelectedChannels.getFirst();
		
		selectedChannels.setAll(tempSelectedChannels);
		saveChannelColorProperties();
	}




	/**
	 * Apply the required transforms to a BufferedImage to get the appropriate display.
	 * <p>
	 * Warning: This is not thread-safe.
	 * @param imgInput the input image; this should not be null, and should be of a type that matches the {@code ImageData}
	 *                 (i.e. same number of channels, same bit-depth).
	 * @param imgOutput the output image (optional); if not null, this should be an RGB image
	 *                  (usually {@code TYPE_INT_RGB}).
	 * @return the transformed image; this may be the same as {@code imgOutput}, if provided
	 */
	@Override
	public BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput) {
        return applyTransforms(imgInput, imgOutput, selectedChannels, displayMode().getValue());
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
	public static BufferedImage applyTransforms(BufferedImage imgInput, BufferedImage imgOutput,
												List<? extends ChannelDisplayInfo> selectedChannels,
												ChannelDisplayMode mode) {
		int width = imgInput.getWidth();
		int height = imgInput.getHeight();

		if (imgOutput == null || imgOutput.getWidth() != width || imgOutput.getHeight() != height) {
			imgOutput = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		}
		
		// Ensure we have a mode
		if (mode == null)
			mode = ChannelDisplayMode.COLOR;
		
		boolean invertBackground = mode.invertColors();
		boolean isGrayscale = mode == ChannelDisplayMode.GRAYSCALE || mode == ChannelDisplayMode.INVERTED_GRAYSCALE;
				
		// Check if we have any changes to make - if not, just copy the image
		// Sometimes the first entry of selectedChannels was null... not sure why... this test is therefore to paper over the cracks...
		if (selectedChannels.size() == 1 && (selectedChannels.getFirst() == null || !selectedChannels.getFirst().doesSomething()) && !invertBackground && !isGrayscale) {
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
		if (selectedChannels.isEmpty() || selectedChannels.getFirst() == null)
			return "";
		if (selectedChannels.size() == 1)
			return selectedChannels.getFirst().getValueAsString(img, x, y);

		return availableChannels.stream()
				.filter(selectedChannels::contains)
				.map(c -> c.getValueAsString(img, x, y))
				.collect(Collectors.joining(","));
	}



	private void updateHistogramMap() {
		ImageServer<BufferedImage> server = imageData == null ? null : imageData.getServer();
		if (server == null) {
			histogramManager = null;
			return;
		}
		
		histogramManager = cachedHistograms.get(server.getPath());
		if (histogramManager == null) {
			histogramManager = new HistogramManager();
			histogramManager.updateChannels(server, availableChannels, getImagesForHistograms());
			if (server.getPixelType() == PixelType.UINT8) {
				availableChannels.parallelStream()
						.filter(c -> !(c instanceof DirectServerChannelInfo))
						.forEach(this::autoSetDisplayRangeWithoutUpdate);
			} else {
				availableChannels.parallelStream()
						.forEach(this::autoSetDisplayRangeWithoutUpdate);
			}
			cachedHistograms.put(server.getPath(), histogramManager);
		} else {
			availableChannels.parallelStream().forEach(this::autoSetDisplayRangeWithoutUpdate);
		}
	}


	private Map<RegionRequest, BufferedImage> getImagesForHistogram(final ImageServer<BufferedImage> server) throws IOException {
		if (server == null)
			return Collections.emptyMap();
		// Try to get the first image 'normally', so that if there is an exception it can be handled
		Map<RegionRequest, BufferedImage> map = new TreeMap<>(regionComparator);
		// Request the central slice at the lowest resolution
		double downsample = server.getDownsampleForResolution(server.nResolutions()-1);
		var request = RegionRequest.createInstance(server.getPath(), downsample, 0, 0, server.getWidth(), server.getHeight(),
				server.nZSlices()/2, server.nTimepoints()/2);
		map.put(request, server.readRegion(request));
		// Before v0.6.0 we tried to read all z-slices and time points - but this could be much too expensive
		// (and also require too much memory)
		return map;
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

	private static double getDefaultSaturationProportion() {
		return PathPrefs.autoBrightnessContrastSaturationPercentProperty().get()/100.0;
	}

	private void autoSetDisplayRangeWithoutUpdate(ChannelDisplayInfo info) {
		autoSetDisplayRange(info, getHistogram(info), getDefaultSaturationProportion(), false);
	}

	/**
	 * Automatically set the display range for a channel, using the default saturation defined in {@link PathPrefs#autoBrightnessContrastSaturationPercentProperty()}.
	 * @param info channel to update
	 */
	public void autoSetDisplayRange(ChannelDisplayInfo info) {
		autoSetDisplayRange(info, getHistogram(info), getDefaultSaturationProportion(), true);
	}
	
	/**
	 * Automatically set the display range for a channel.
	 * @param info channel to update
	 * @param saturation proportion of pixels that may be saturated, i.e. have the max/min display values (between 0.0 and 1.0)
	 */
	public void autoSetDisplayRange(ChannelDisplayInfo info, double saturation) {
		autoSetDisplayRange(info, getHistogram(info), saturation, true);
	}

	
	private ImageServer<BufferedImage> getServer() {
		return imageData == null ? null : imageData.getServer();
	}
	

	/**
	 * Returns a histogram for a channel of the current image.
	 * @param channel the channel
	 * @return the histogram for the specified channel, or null if no histogram is available (e.g. the channel is a
	 * 		   packed RGB representation that can't have a single histogram associated with it)
	 */
	public Histogram getHistogram(ChannelDisplayInfo channel) {
		if (channel == null || histogramManager == null)
			return null;
		return histogramManager.getHistogram(getServer(), channel, getImagesForHistograms());
	}

	private Map<RegionRequest, BufferedImage> getImagesForHistograms() {
		var server = getServer();
		if (server == null || server.nZSlices() * server.nTimepoints() == imagesForHistograms.size())
			return imagesForHistograms;

		// Get all the cached tiles we can
		Map<RegionRequest, BufferedImage> images = new TreeMap<>(regionComparator);
		int level = server.nResolutions()-1;
		long nPixelsCached = 0;
		for (var tile : server.getTileRequestManager().getTileRequestsForLevel(level)) {
			var img = server.getCachedTile(tile);
			if (img != null) {
				nPixelsCached += (long) img.getWidth() * img.getHeight();
				images.put(tile.getRegionRequest(), img);
			}
		}

		// If we don't have anything, search the main cache
		if (images.isEmpty()) {
			double downsample = server.getDownsampleForResolution(server.nResolutions()-1);
			var cache = ImageServerProvider.getCache(BufferedImage.class);
			cache.entrySet()
					.stream()
					.filter(e -> e.getKey().getPath().equals(server.getPath()) &&
							e.getKey().getDownsample() == downsample)
					.forEach(e -> images.put(e.getKey(), e.getValue()));
		}

		// Get the original histogram pixels
		long nPixelsBackup = 0;
		for (var img : imagesForHistograms.values()) {
			nPixelsBackup += (long) img.getWidth() * img.getHeight();
		}

		// Return whichever images gives us the most pixels to work with
		return nPixelsBackup >= nPixelsCached ? imagesForHistograms : images;
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
	 * @param prettyPrint optionally request pretty printing
	 * @return the Json representation, or null if no image has been set
	 */
	public String toJSON(final boolean prettyPrint) {
		if (this.imageData == null)
			return null;
		JsonArray array = new JsonArray();
		for (ChannelDisplayInfo info : availableChannels) {
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
	 * @param display the other display with which to check compatibility
	 * @return true if the display is compatible, false otherwise
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
	private boolean updateFromJSON(final String json) {
		Gson gson = new Gson();
		Type type = new TypeToken<List<JsonHelperChannelInfo>>(){}.getType();
		List<JsonHelperChannelInfo> helperList = gson.fromJson(json, type);
		// Try updating everything
		List<ChannelDisplayInfo> newSelectedChannels = new ArrayList<>();
		boolean changes = false;
		for (JsonHelperChannelInfo helper : helperList) {
			for (ChannelDisplayInfo info : availableChannels) {
				if (helper.matches(info)) {
					// Set the min/max display & color if needed
					if (helper.updateInfo(info)) {
						changes = true;
					}
					// Store whether the channel is selected
					if (helper.isSelected()) {
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

}
