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

package qupath.lib.images.servers;

import java.util.Map;

import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;
import qupath.lib.regions.RegionRequest;

/**
 * Helper class for creating ImageServers from a given path - which may be a file path or URL.
 * <p>
 * This class is responsible for hunting through potential constructors to find a server that works.
 * <p>
 * 
 * @author Pete Bankhead
 *
 */
public interface ImageServerBuilder<T> {
	
	/**
	 * Estimated 'support level' for a given file path, where support level is a summary of the likelihood that
	 * pixel values and metadata will be returned correctly and in a way that achieves good performance.
	 * <p>
	 * The support level should be a value between 0 and 4.  The following is a guide to its interpretation:
	 * <ul>
	 * <li>4 - 'ideal' support, e.g. the image was written by the library behind the ImageServer</li>
	 * <li>3 - good support</li>
	 * <li>2 - unknown support, i.e. worth a try</li>
	 * <li>1 - partial/poor support, i.e. there are known limitations and all higher-scoring possibilities should be tried first</li>
	 * <li>0 - no support</li>
	 * </ul>
	 * The use of floating point enables subclasses to make more subtle evaluations of performance, e.g. if an ImageServer
	 * is particularly strong for RGB images, but falls short of guaranteeing ideal performance.
	 * <p>
	 * In practice, this is used by the ServiceLoader to rank potential ImageServerProviders so that the 'best' ones
	 * are tried first for new image paths.  The ServiceLoader will not attempt to create the ImageServer if the support level is 0.
	 * 
	 * @param path
	 * @param info
	 * @return
	 */
	public float supportLevel(String path, ImageCheckType info, Class<?> cls);
	
	/**
	 * Attempt to create {@code ImageServer<T>} from the specified path.
	 * @param path
	 * @param cache
	 * @return
	 */
	public ImageServer<T> buildServer(String path, Map<RegionRequest, T> cache) throws Exception;
	
	/**
	 * Get a human-readable name for the kind of ImageServer this builds.
	 * @return
	 */
	public String getName();
	
	/**
	 * Get a short, human-readable description for display in a GUI.
	 * @return
	 */
	public String getDescription();
	
}
