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
import java.util.Collection;

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
public interface ImageWriter<T> {
	
	/**
	 * Get the name of the image writer.
	 * @return
	 */
	public String getName();

	/**
	 * Get the file extensions used by the image writer.
	 * These are returned without the leading 'dot'.
	 * In the case where multiple extensions are associated with a file type 
	 * (e.g. "jpg", "jpeg", "tif", "tiff") the preferred should be returned first;
	 * @return
	 */
	public Collection<String> getExtensions();
	
	/**
	 * Get the default extension. This should be the first returned by {@link #getExtensions()}.
	 * @return
	 */
	public default String getDefaultExtension() {
		return getExtensions().iterator().next();
	}
	
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

	/**
	 * Returns true if the writer is capable of writing pyramidal images.
	 * @return
	 */
	public boolean supportsPyramidal();

	/**
	 * Returns true if the writer is capable of storing pixel size information.
	 * @return
	 */
	public boolean supportsPixelSize();
	
	/**
	 * Get further details of the writer, which may be displayed to a user.
	 * @return
	 */
	public String getDetails();
	
	/**
	 * Get the class of supported images.
	 * @return
	 * {@link ImageServer#getImageClass()}
	 */
	public Class<T> getImageClass();
	
	/**
	 * Write an image region to a specified path.
	 * @param server
	 * @param region
	 * @param pathOutput
	 * @throws IOException
	 */
	public void writeImage(ImageServer<T> server, RegionRequest region, String pathOutput) throws IOException;

	/**
	 * Write a full image to a specified path.
	 * @param img
	 * @param pathOutput
	 * @throws IOException
	 */
	public void writeImage(T img, String pathOutput) throws IOException;
	
	/**
	 * Write a full image to a specified path.
	 * @param server
	 * @param pathOutput
	 * @throws IOException
	 */
	public void writeImage(ImageServer<T> server, String pathOutput) throws IOException;

}
