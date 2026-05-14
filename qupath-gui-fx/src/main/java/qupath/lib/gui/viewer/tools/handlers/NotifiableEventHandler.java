/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.viewer.tools.handlers;

import qupath.lib.gui.viewer.QuPathViewer;

/**
 * Interface for a viewer event handler that should be notified when it is added or removed.
 */
public interface NotifiableEventHandler {

    /**
     * Method called after the event handler has been added to a viewer.
     * @param viewer the viewer to which the event handler was added
     */
    void handlerAdded(QuPathViewer viewer);

    /**
     * Method called after the event handler has been removed from a viewer.
     * @param viewer the viewer from which the event handler was removed
     */
    void handlerRemoved(QuPathViewer viewer);

}
