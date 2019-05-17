/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.images.writers;

import java.io.IOException;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.regions.RegionRequest;

/**
 * Interface for defining class that can write images.
 * <p>
 * This may not have been a particularly good idea and may change in the future... (e.g. using services?).
 * <p>
 * As it is, it's best to avoid using it because the design is rather clumsy (and goes back to a day whenever 
 * external dependencies were avoided with an enthusiasm that may have been a bit too much).
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
@Deprecated
public interface ImageWriter<T> {
	
	public String getName();

	public String getExtension();

	/**
	 * Check if writer can handle multiple timepoints.
	 * @return
	 */
	public boolean supportsT();

	/**
	 * Check if writer can handle multiple z-slices.
	 * @return
	 */
	public boolean supportsZ();
	
	/**
	 * Check if writer can handle RGB (it probably can...).
	 * @return
	 */
	public boolean supportsRGB();
	
	/**
	 * Test whether images provided by a specified ImageServer can be successfully written.
	 * <p>
	 * Reasons why it might not be are the number of channels and/or bit-depth.
	 * @param server
	 * @return
	 */
	public boolean suportsImageType(ImageServer<T> server);

	public boolean supportsPyramidal();

	public boolean supportsPixelSize();
	
	public String getDetails();
	
	public T writeImage(ImageServer<T> server, RegionRequest region, String pathOutput) throws IOException;

	public void writeImage(T img, String pathOutput) throws IOException;

}
