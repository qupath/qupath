/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.display.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.common.ColorTools;
import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.io.GsonTools;
import qupath.lib.projects.Project;
import qupath.lib.projects.ResourceManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for working with image display and image display settings objects.
 * The former is used by QuPath to display images, while the latter is used to store
 * the settings in a JSON-friendly form.
 */
public class DisplaySettingUtils {

    private static Logger logger = LoggerFactory.getLogger(DisplaySettingUtils.class);

    /**
     * Create a new image display settings object from the image display.
     * This is typically used to save the settings to a file.
     * @param display
     * @param name
     * @return
     */
    public static ImageDisplaySettings displayToSettings(ImageDisplay display, String name) {
        List<ChannelSettings> channels = new ArrayList<>();
        var available = new ArrayList<>(display.availableChannels());
        var selected = new HashSet<>(display.selectedChannels());
        for (var channel : available) {
            channels.add(asChannelSettings(channel, selected.contains(channel)));
        }
        return ImageDisplaySettings.create(
                name,
                PathPrefs.viewerGammaProperty().get(),
                display.useInvertedBackground(),
                channels
        );
    }

    /**
     * Check if the settings are compatible with the display.
     * This is true if the number and names of the channels match.
     * @param display
     * @param settings
     * @return
     */
    public static boolean settingsCompatibleWithDisplay(ImageDisplay display, ImageDisplaySettings settings) {
        if (display == null || settings == null || display.availableChannels().size() != settings.getChannels().size())
            return false;
        var names = display.availableChannels().stream().map(ChannelDisplayInfo::getName).collect(Collectors.toSet());
        var namesSettings = settings.getChannels().stream().map(ChannelSettings::getName).collect(Collectors.toSet());
        return names.equals(namesSettings);
    }

    /**
     * Apply the settings to the display, if they are compatible.
     * @param display
     * @param settings
     * @return true if the settings were applied, false otherwise.
     */
    public static boolean applySettingsToDisplay(ImageDisplay display, ImageDisplaySettings settings) {
        if (settings == null || !settingsCompatibleWithDisplay(display, settings)) {
            return false;
        }
        display.setUseInvertedBackground(settings.invertBackground());
        var available = new ArrayList<>(display.availableChannels());
        // Maintain a list of colors in case we need to update the image metadata
        var directChannels = new ArrayList<ImageChannel>();
        for (var channel : available) {
            var channelSettings = settings.getChannels().stream()
                    .filter(s -> s.getName().equals(channel.getName()))
                    .findFirst()
                    .orElse(null);
            if (channelSettings == null) {
                continue;
            }
            var color = channelSettings.getColor();
            if (color != null && channel instanceof DirectServerChannelInfo info) {
                info.setLUTColor(color.getRed(), color.getGreen(), color.getBlue());
                directChannels.add(ImageChannel.getInstance(channel.getName(),
                        ColorTools.packRGB(color.getRed(), color.getGreen(), color.getBlue())));
            }
            display.setMinMaxDisplay(channel, channelSettings.getMinDisplay(), channelSettings.getMaxDisplay());
            display.setChannelSelected(channel, channelSettings.isShowing());
        }
        display.setUseInvertedBackground(settings.invertBackground());
        // Set image metadata if channel colors have changed
        // See https://github.com/qupath/qupath/issues/1726
        var imageData = display.getImageData();
        var server = imageData == null ? null : imageData.getServer();
        if (server != null && server.nChannels() == directChannels.size() && !directChannels.equals(server.getMetadata().getChannels())) {
            server.setMetadata(
                    new ImageServerMetadata.Builder(server.getMetadata())
                            .channels(directChannels)
                            .build()
            );
        }
        return true;
    }

    private static ChannelSettings asChannelSettings(ChannelDisplayInfo info, boolean isShowing) {
        Integer rgb = info.getColor();
        return ChannelSettings.create(
                info.getName(),
                info.getMinDisplay(),
                info.getMaxDisplay(),
                rgb == null ? null : ChannelColor.fromPackedARGB(rgb),
                isShowing
        );
    }

    /**
     * Check if the JSON element is a valid image display settings object.
     * @param element
     * @return
     */
    private static boolean isDisplaySettings(JsonElement element) {
        if (!element.isJsonObject())
            return false;
        var obj = element.getAsJsonObject();
        return obj.has("name") &&
                obj.get("name").isJsonPrimitive() &&
                obj.has("channels") &&
                obj.get("channels").isJsonArray() &&
                obj.has("gamma") &&
                obj.has("invertBackground");
    }

    /**
     * Parse the JSON element into an image display settings object, if possible.
     * @param element the JSON element to parse
     * @return an optional containing the settings, or empty if the element is not a valid settings object
     * @see #parseDisplaySettings(Path)
     */
    public static Optional<ImageDisplaySettings> parseDisplaySettings(JsonElement element) {
        if (isDisplaySettings(element)) {
            var gson = GsonTools.getInstance();
            try {
                var settings = gson.fromJson(element, ImageDisplaySettings.class);
                return Optional.of(settings);
            } catch (JsonSyntaxException e) {
                logger.debug("Unable to parse display settings", e);
            }
        }
        return Optional.empty();
    }

    /**
     * Parse the JSON element into an image display settings object.
     * This assumes that the path is to a JSON file containing a valid JSON representation of an image display settings
     * object, and throws an exception if this is not the case.
     * <p>
     * For a more conservative approach, use {@link #parseDisplaySettings(JsonElement)} instead.
     * @param path
     * @return
     * @throws IOException
     * @see #parseDisplaySettings(JsonElement)
     */
    public static ImageDisplaySettings parseDisplaySettings(Path path) throws IOException {
        try (var reader = Files.newBufferedReader(path)) {
            var gson = GsonTools.getInstance();
            return gson.fromJson(reader, ImageDisplaySettings.class);
        } catch (JsonSyntaxException e) {
            throw new IOException(e);
        }
    }


    /**
     * Get the resource manager for image display settings from a project.
     * @param project
     * @return
     */
    public static ResourceManager.Manager<ImageDisplaySettings> getResourcesForProject(Project<?> project) {
        return project.getResources("resources/display", ImageDisplaySettings.class, ".json");
    }


}
