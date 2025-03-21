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

import qupath.lib.color.ColorTransformer;
import qupath.lib.images.ImageData;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Helper class to manage which channels should be shown for an image.
 * Intended for use with ImageDisplay.
 */
class ChannelManager {

    private final RgbChannels rgbChannels;
    private final DirectChannels directChannels;

    ChannelManager(ImageData<BufferedImage> imageData) {
        Objects.requireNonNull(imageData);

        if (imageData.getServerMetadata().isRGB()) {
            rgbChannels = new RgbChannels(imageData);
            directChannels = null;
        } else {
            directChannels = new DirectChannels(imageData);
            rgbChannels = null;
        }
    }

    /**
     * Get the available channels.
     * @param includeAllTransforms if true, include all supported transforms (rather than just the main ones)
     * @return a list of all channels currently available
     */
    List<ChannelDisplayInfo> getAvailableChannels(boolean includeAllTransforms) {
        if (rgbChannels != null)
            return rgbChannels.getAvailableRgbChannels(includeAllTransforms);
        else
            return directChannels.getAvailableChannels();
    }


    private static class DirectChannels {

        private final ImageData<BufferedImage> imageData;

        private final List<DirectServerChannelInfo> availableChannels = new ArrayList<>();
        private final List<ChannelDisplayInfo> brightfieldChannels = new ArrayList<>();

        private DirectChannels(ImageData<BufferedImage> imageData) {
            this.imageData = imageData;
            var channels = imageData.getServerMetadata().getChannels();
            for (int c = 0; c < channels.size(); c++) {
                availableChannels.add(new DirectServerChannelInfo(imageData, c));
            }
            if (imageData.getServerMetadata().getChannels().size() == 3) {
                brightfieldChannels.add(new AdditiveChannelInfo(imageData, availableChannels));
                brightfieldChannels.add(new ColorDeconvolutionInfo(imageData, ColorTransformer.ColorTransformMethod.Stain_1));
                brightfieldChannels.add(new ColorDeconvolutionInfo(imageData, ColorTransformer.ColorTransformMethod.Stain_2));
                brightfieldChannels.add(new ColorDeconvolutionInfo(imageData, ColorTransformer.ColorTransformMethod.Stain_3));
                brightfieldChannels.add(new ColorDeconvolutionInfo(imageData, ColorTransformer.ColorTransformMethod.Optical_density_sum));
            }
        }

        private List<ChannelDisplayInfo> getAvailableChannels() {
            if (imageData.isBrightfield()) {
                var list = new ArrayList<>(brightfieldChannels);
                list.addAll(availableChannels);
                return list;
            } else {
                return List.copyOf(availableChannels);
            }
        }

    }


    private static class RgbChannels {

        private final ImageData<BufferedImage> imageData;

        // Lists to store the different kinds of channels we might need
        // Pack RGB (all channels in one image, only adjustable together)
        private final RGBDirectChannelInfo rgbDirectChannelInfo;
        // Normalized optical density channels; useful to find the 'predominant' color when selecting stain vectors
        private final RGBNormalizedChannelInfo rgbNormalizedChannelInfo;
        // Direct (editable) RGB channels
        private final List<ChannelDisplayInfo> rgbDirectChannels = new ArrayList<>();
        // Split (uneditable) RGB channels
        private final List<ChannelDisplayInfo> rgbSplitChannels = new ArrayList<>();
        // Hue, Saturation, Value
        private final List<ChannelDisplayInfo> rgbHsvChannels = new ArrayList<>();
        // Color-deconvolved channels
        private final List<ChannelDisplayInfo> rgbBrightfieldChannels = new ArrayList<>();
        // Chromaticity channels
        private final List<ChannelDisplayInfo> rgbChromaticityChannels = new ArrayList<>();

        private RgbChannels(ImageData<BufferedImage> imageData) {
            this.imageData = imageData;

            rgbDirectChannelInfo = new RGBDirectChannelInfo(imageData);
            rgbNormalizedChannelInfo = new RGBNormalizedChannelInfo(imageData);

            // Add simple channel separation (changed for v0.6.0)
            rgbSplitChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Red, false));
            rgbSplitChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Green, false));
            rgbSplitChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Blue, false));

            rgbDirectChannels.add(new DirectServerChannelInfo(imageData, 0));
            rgbDirectChannels.add(new DirectServerChannelInfo(imageData, 1));
            rgbDirectChannels.add(new DirectServerChannelInfo(imageData, 2));

            rgbHsvChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Hue, false));
            rgbHsvChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Saturation, false));
            rgbHsvChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.RGB_mean, false));

            // Add optical density & color deconvolution options for brightfield images
            rgbBrightfieldChannels.add(new ColorDeconvolutionInfo(imageData, ColorTransformer.ColorTransformMethod.Stain_1));
            rgbBrightfieldChannels.add(new ColorDeconvolutionInfo(imageData, ColorTransformer.ColorTransformMethod.Stain_2));
            rgbBrightfieldChannels.add(new ColorDeconvolutionInfo(imageData, ColorTransformer.ColorTransformMethod.Stain_3));
            rgbBrightfieldChannels.add(new ColorDeconvolutionInfo(imageData, ColorTransformer.ColorTransformMethod.Optical_density_sum));

            rgbChromaticityChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Red_chromaticity, false));
            rgbChromaticityChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Green_chromaticity, false));
            rgbChromaticityChannels.add(new RBGColorTransformInfo(imageData, ColorTransformer.ColorTransformMethod.Blue_chromaticity, false));
        }

        private List<ChannelDisplayInfo> getAvailableRgbChannels(boolean includeAllTransforms) {
            List<ChannelDisplayInfo> tempChannelOptions = new ArrayList<>();
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
                if (includeAllTransforms) {
                    // Change v0.6.0 - don't show all channels for fluorescence (as they are more distracting than helpful)
                    // If they are needed, using ImageType.OTHER
                    tempChannelOptions.addAll(rgbHsvChannels);
                    tempChannelOptions.addAll(rgbChromaticityChannels);
                }
            }
            return tempChannelOptions;
        }

    }


}
