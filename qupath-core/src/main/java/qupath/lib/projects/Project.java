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

package qupath.lib.projects;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.classes.PathClass;

/**
 * Data structure to store multiple images, relating these to a file system.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface Project<T> {
	
	/**
	 * Get an unmodifiable list representing the <code>PathClass</code>es associated with this project.
	 * @return
	 */
	public List<PathClass> getPathClasses();
	
	/**
	 * Query whether 'true' or masked image names are being returned.
	 * 
	 * @return
	 * 
	 * @see #setMaskImageNames(boolean)
	 */
	public boolean getMaskImageNames();
	
	/**
	 * Request that entries return masked image names, rather than the 'true' image names.
	 * <p>
	 * The purpose of this is to support studies where the user ought not to see the image names during analysis, 
	 * reducing the potential for user bias.
	 * 
	 * @param maskNames
	 * 
	 * @see #getMaskImageNames()
	 */
	public void setMaskImageNames(boolean maskNames);
	
	/**
	 * Update the available PathClasses.
	 * 
	 * @param pathClasses
	 * @return <code>true</code> if the stored values changed, false otherwise.
	 */
	public boolean setPathClasses(Collection<? extends PathClass> pathClasses);

	public boolean addImage(final ProjectImageEntry<T> entry);
	
	// TODO: Make this non-public (or at least less important)
	@Deprecated
	public File getFile();
	
	// TODO: Make this non-public (or at least less important)
	@Deprecated
	public File getBaseDirectory();
	
	public boolean addAllImages(final Collection<ProjectImageEntry<T>> entries);
	
	public boolean isEmpty();

	public boolean addImagesForServer(final ImageServer<T> server);
	
	public ProjectImageEntry<T> getImageEntry(final String path);

	public boolean addImage(final String path);
	
	public void removeImage(final ProjectImageEntry<?> entry);

	public void removeAllImages(final Collection<ProjectImageEntry<T>> entries);
	
	public void syncChanges() throws IOException;
	
	/**
	 * Get a list of image entries for the project.
	 * 
	 * @return
	 */
	public List<ProjectImageEntry<T>> getImageList();
		
	public String getName();
	
	public long getCreationTimestamp();
	
	public long getModificationTimestamp();
	
	static class ImageEntryComparator implements Comparator<ProjectImageEntry<?>> {

		static ImageEntryComparator instance = new ImageEntryComparator();
		
		@Override
		public int compare(ProjectImageEntry<?> o1, ProjectImageEntry<?> o2) {
			String n1 = o1.getImageName();
			String n2 = o2.getImageName();
			if (n1 == null) {
				if (n2 == null)
					return 0;
				else
					return 1;
			} else if (n2 == null)
				return -1;
			return n1.compareTo(n2);
		}
		
	}
	
}
