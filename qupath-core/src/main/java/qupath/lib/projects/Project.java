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
import java.net.URI;
import java.nio.file.Path;
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
	
	/**
	 * Check for missing paths.  This assumes local files; other URIs will be ignored.
	 * 
	 * @param relativize Optionally try to resolve relative paths.
	 * @return a list of local file paths supposedly to images, but for which no files could be found.
	 */
	public List<String> validateLocalPaths(boolean relativize);
	
	/**
	 * Get a URI that can be used when saving/reloading this project.
	 * 
	 * @return
	 */
	public URI getURI();
	
	/**
	 * Extract a usable project name from a URI.
	 * 
	 * @param uri
	 * @return
	 */
	public static String getNameFromURI(URI uri) {
		if (uri == null)
			return "No URI";
			
		String[] path = uri.getPath().split("/");
		if (path.length == 0)
			return "No name";
		String name = path[path.length-1];
		
		String ext = ProjectIO.getProjectExtension(true);
		if (name.endsWith(ext))
			name = name.substring(0, name.length()-ext.length());
		if (path.length == 1)
			return name;
		return path[path.length-2] + "-" + name;
	}
	
	/**
	 * Get the base directory containing this project, if possible.
	 * <p>
	 * This is relevant for projects using the local file system, but should return null in other cases.
	 * 
	 * @return
	 */
	@Deprecated
	public File getBaseDirectory();
	
	/**
	 * The version string for this project, which can be used to distinguish new and older project 
	 * (which may contain different information).
	 * <p>
	 * This may be null if the version information is not stored.
	 * 
	 * @return
	 */
	public String getVersion();
	
	/**
	 * Get a path to this project, or null if this project on a local file system.
	 * <p>
	 * If not null, the path may be a file or a directory.
	 * 
	 * @return
	 * @see ProjectImageEntry#getEntryPath()
	 */
	public Path getPath();
	
	/**
	 * Add multiple images to the project. Note that it is implementation-specific whether 
	 * these images entries are duplicated or used directly, e.g. it is undefined whether or not 
	 * subsequent changes to any entries within the collection will be reflected in this project or not.
	 * 
	 * @param entries
	 * @return
	 */
	public boolean addAllImages(final Collection<ProjectImageEntry<T>> entries);
	
	/**
	 * Test if the project contains any images.
	 * @return
	 */
	public boolean isEmpty();

	/**
	 * Add an image for a particular ImageServer.
	 * @param server
	 * @return
	 */
	public ProjectImageEntry<T> addImage(final ImageServer<T> server);
	
	/**
	 * Request a {@link ProjectImageEntry} with an image server path.
	 * @param path
	 * @return
	 */
	public ProjectImageEntry<T> getImageEntry(final String path);
	
	/**
	 * Remove an image from the project.
	 * 
	 * @param entry
	 */
	public void removeImage(final ProjectImageEntry<?> entry);

	/**
	 * Remove multiple images from the project.
	 * 
	 * @param entries
	 */
	public void removeAllImages(final Collection<ProjectImageEntry<T>> entries);
	
	/**
	 * Save the project.
	 * 
	 * @throws IOException
	 */
	public void syncChanges() throws IOException;
	
	/**
	 * Get a list of image entries for the project.
	 * 
	 * @return
	 */
	public List<ProjectImageEntry<T>> getImageList();
	
	public String getName();
	
	/**
	 * Request a timestamp from when the project was created.
	 * @return
	 */
	public long getCreationTimestamp();
	
	/**
	 * Request a timestamp from when the project was last synchronized.
	 * @return
	 * 
	 * @see #syncChanges()
	 */
	public long getModificationTimestamp();
	
	
	
	
	
	/**
	 * List all scripts available in the project. Note that this currently provides only a snapshot in time, i.e. 
	 * one should request the script names whenever they are needed and not retain the list.
	 * 
	 * @return
	 * @throws IOException
	 */
	public List<String> listScripts() throws IOException;
	
	/**
	 * Load a script with a name as returned by {@link listScripts}
	 * 
	 * @param name
	 * @return
	 * @throws IOException
	 */
	public String loadScript(String name) throws IOException;
	
	/**
	 * Save a script for this project.
	 * 
	 * @param name
	 * @param script
	 * @throws IOException
	 */
	public void saveScript(String name, String script) throws IOException;
	
	
//	public List<String> listPixelClassifiers();
//	
//	public PixelClassifier loadPixelClassifier(String name);
//	
//	public void savePixelClassifier(String name, String PixelClassifier);
	
	
	
	
	
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
