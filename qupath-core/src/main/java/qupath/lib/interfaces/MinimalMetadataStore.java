/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.interfaces;

import java.util.Map;

/**
 * Minimal interface to indicate that an object can provide a map of metadata key/value pairs.
 * <p>
 * In some use-cases, there will be a need to store some metadata values for internal use
 * and others that should be visible to the user.
 * When this is the case, it is suggested to use the convention that metadata keys that start with {@code "_"}
 * are to be used internally, but this is not enforced by this interface.
 */
public interface MinimalMetadataStore {

    /**
     * Returns a modifiable map containing the metadata.
     * <p>
     * The returned map may or may not be thread-safe. Implementing classes must
     * document the thread-safeness of the map.
     *
     * @return the metadata of this store
     */
    Map<String, String> getMetadata();

}
