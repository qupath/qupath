/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.lib.objects;

import java.io.IOException;
import java.util.Collection;

import qupath.lib.images.servers.ImageServer;

/**
 * Interface for classes capable of reading {@linkplain PathObject PathObjects} from some source.
 * This may be used in conjunction with an {@link ImageServer} to indicate that the server can read objects 
 * as well as pixels from its source.
 * 
 * @author Pete Bankhead
 *
 */
public interface PathObjectReader {
	
	/**
	 * Read a collection of objects from the source.
	 * @return a collection of objects, or empty list if no objects are available.
	 * @throws IOException 
	 */
	public Collection<PathObject> readPathObjects() throws IOException;

}