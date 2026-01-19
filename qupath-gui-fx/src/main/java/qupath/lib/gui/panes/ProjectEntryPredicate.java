/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.panes;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import qupath.lib.projects.ProjectImageEntry;

/**
 * Class to create a predicate to filter {@link ProjectImageEntry}.
 * <p>
 * The most basic predicate checks only if the image name contains the specified text.
 * However, if {@code |} is found in the filter text then metadata key-value pairs are checked,
 * using the syntax {@code key=value}.
 * <p>
 * Therefore, to search for images ending with ".tif" and with a metadata entry "server" with value "bioformats,
 * the filter text string would be {@code "tif|server=bioformats"}.
 */
public class ProjectEntryPredicate implements Predicate<ProjectImageEntry<?>> {

    private static final Predicate<ProjectImageEntry<?>> ACCEPT_ALL = p -> true;

    private final String filterText;
    private final boolean ignoreCase;
    private final List<String> filterTokens;

    /**
     * Create a case-sensitive filter for project entries.
     * @param filterText the filter text; if empty, all entries will pass through the filter
     * @return the predicate
     */
    public static Predicate<ProjectImageEntry<?>> createCaseSensitive(String filterText) {
        return create(filterText, false);
    }

    /**
     * Create a case-insensitive filter for project entries.
     * @param filterText the filter text; if empty, all entries will pass through the filter
     * @return the predicate
     */
    public static Predicate<ProjectImageEntry<?>> createIgnoreCase(String filterText) {
        return create(filterText, true);
    }

    private static Predicate<ProjectImageEntry<?>> create(String filterText, boolean ignoreCase) {
        String text = filterText.trim();
        if (isAcceptAll(text))
            return ACCEPT_ALL;
        return new ProjectEntryPredicate(text, ignoreCase);
    }

    private static boolean isAcceptAll(String filterText) {
        return filterText.isEmpty() || filterText.replaceAll("\\|", "").isEmpty();
    }

    private ProjectEntryPredicate(String filterText, boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
        this.filterText = ignoreCase ? filterText.toLowerCase() : filterText;
        if (this.filterText.contains("|")) {
            this.filterTokens = Arrays.stream(this.filterText.split("\\|"))
                    .filter(t -> !t.isBlank())
                    .toList();
        } else {
            this.filterTokens = List.of();
        }
    }

    @Override
    public boolean test(ProjectImageEntry<?> entry) {

        // Check the image name
        var imageName = entry.getImageName();
        if (ignoreCase)
            imageName = imageName.toLowerCase();
        if (imageName.contains(filterText))
            return true;

        // If we have no tokens, then we have nothing else to test
        if (filterTokens.isEmpty())
            return false;

        // Convert metadata key/value pairs to key=value
        var metadataStrings = entry.getMetadata().entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .map(t -> ignoreCase ? t.toLowerCase() : t)
                .collect(Collectors.toCollection(ArrayList::new));

        // Add extra string for has_data, unless it's already included
        if (!entry.getMetadata().containsKey("has_data")) {
            metadataStrings.add("has_data=" + entry.hasImageData());
        }

        // Check the filter tokens - we need to find all of them
        for (var token : filterTokens) {
            boolean foundMatch = imageName.contains(token);
            if (!foundMatch) {
                for (var m : metadataStrings) {
                    if (m.contains(token)) {
                        foundMatch = true;
                        break;
                    }
                }
            }
            // If a single token isn't found, return false;
            if (!foundMatch) {
                return false;
            }
        }

        // We have tokens and found them all - return true
        return true;
    }
}
