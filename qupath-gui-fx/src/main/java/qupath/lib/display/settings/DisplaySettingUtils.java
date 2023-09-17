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

import qupath.lib.display.ChannelDisplayInfo;
import qupath.lib.display.DirectServerChannelInfo;
import qupath.lib.display.ImageDisplay;
import qupath.lib.gui.prefs.PathPrefs;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

public class DisplaySettingUtils {

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

    public static boolean settingsCompatibleWithDisplay(ImageDisplay display, ImageDisplaySettings settings) {
        if (display == null || settings == null || display.availableChannels().size() != settings.getChannels().size())
            return false;
        var names = display.availableChannels().stream().map(ChannelDisplayInfo::getName).collect(Collectors.toSet());
        var namesSettings = settings.getChannels().stream().map(ChannelSettings::getName).collect(Collectors.toSet());
        return names.equals(namesSettings);
    }

    public static boolean applySettingsToDisplay(ImageDisplay display, ImageDisplaySettings settings) {
        if (settings == null || !settingsCompatibleWithDisplay(display, settings)) {
            return false;
        }
        display.setUseInvertedBackground(settings.invertBackground());
        var available = new ArrayList<>(display.availableChannels());
        var selected = new HashSet<>(display.selectedChannels());
        for (var channel : available) {
            var channelSettings = settings.getChannels().stream()
                    .filter(s -> s.getName().equals(channel.getName()))
                    .findFirst()
                    .orElse(null);
            if (channelSettings == null) {
                continue;
            }
            if (channelSettings.isShowing()) {
                selected.add(channel);
            } else {
                selected.remove(channel);
            }
            var color = channelSettings.getColor();
            if (color != null && channel instanceof DirectServerChannelInfo info)
                info.setLUTColor(color.getRed(), color.getGreen(), color.getBlue());
            display.setMinMaxDisplay(channel, channelSettings.getMinDisplay(), channelSettings.getMaxDisplay());
            display.setChannelSelected(channel, channelSettings.isShowing());
        }
        display.setUseInvertedBackground(settings.invertBackground());
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

}
