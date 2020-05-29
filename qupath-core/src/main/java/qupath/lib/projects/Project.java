/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.projects;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;

import qupath.lib.classifiers.object.ObjectClassifier;
import qupath.lib.classifiers.pixel.PixelClassifier;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.ResourceManager.Manager;

/**
 * Data structure to manage images and associated data in QuPath.
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
	 * Get a URI that can be used when saving/reloading this project.
	 * 
	 * @return
	 */
	public URI getURI();
	
	/**
	 * Sometimes projects move (unfortunately). This returns the previous URI, if known - 
	 * which can be helpful for resolving relative paths to images in the event that 
	 * both project and images have moved together.
	 * 
	 * @return
	 */
	public URI getPreviousURI();
	
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
	 * The version string for this project, which can be used to distinguish new and older project 
	 * (which may contain different information).
	 * <p>
	 * This may be null if the version information is not stored.
	 * 
	 * @return
	 */
	public String getVersion();
	
	/**
	 * Get a path to this project, or null if this project is not on a local file system.
	 * <p>
	 * If not null, the path may be a file or a directory.
	 * 
	 * @return
	 * @see ProjectImageEntry#getEntryPath()
	 */
	public Path getPath();
	
	/**
	 * Create a sub-project that provides a view on the specified entries.
	 * <p>
	 * The retains exactly the same references and data, i.e. it does not duplicate entries or data files - 
	 * rather it is used to generate projects that provide access to a subset of the entries in the original project.
	 * 
	 * @param name the name of the sub-project
	 * @param entries the entries to retain within the sub-project
	 * @return
	 */
	public Project<T> createSubProject(final String name, final Collection<ProjectImageEntry<T>> entries);
	
	/**
	 * Test if the project contains any images.
	 * @return
	 */
	public boolean isEmpty();

	/**
	 * Add an image for a particular ImageServer.
	 * @param server
	 * @return
	 * @throws IOException 
	 */
	public ProjectImageEntry<T> addImage(final ServerBuilder<T> server) throws IOException;
	
	/**
	 * Add an image by duplicating an existing entry.
	 * This retains the same {@link ServerBuilder}, name, description and metadata, but assigns 
	 * a new unique ID.
	 * 
	 * @param entry the entry that should be copied
	 * @param copyData if true, copy existing image data in addition to other properties
	 * @return the new entry that has been added to the project
	 * @throws IOException
	 */
	public ProjectImageEntry<T> addDuplicate(final ProjectImageEntry<T> entry, boolean copyData) throws IOException;
		
	/**
	 * Request a {@link ProjectImageEntry} associated with an {@link ImageData}
	 * @param imageData
	 * @return
	 */
	public ProjectImageEntry<T> getEntry(final ImageData<T> imageData);
	
	/**
	 * Remove an image from the project, optionally including associated data.
	 * 
	 * @param entry
	 * @param removeAllData 
	 */
	public void removeImage(final ProjectImageEntry<?> entry, boolean removeAllData);

	/**
	 * Remove multiple images from the project, optionally including associated data.
	 * 
	 * @param entries
	 * @param removeAllData
	 */
	public void removeAllImages(final Collection<ProjectImageEntry<T>> entries, boolean removeAllData);
	
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
	
	/**
	 * Get the name of the project.
	 * 
	 * @return
	 */
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
	 * Get a manager for scripts saved within this project.
	 * 
	 * @return
	 */
	public Manager<String> getScripts();
	
	/**
	 * Get a manager for object classifiers saved within this project.
	 * 
	 * @return
	 */
	public Manager<ObjectClassifier<T>> getObjectClassifiers();
	
	/**
	 * Get a manager for pixel classifiers saved within this project.
	 * 
	 * @return
	 */
	public Manager<PixelClassifier> getPixelClassifiers();
	
	
//	public List<String> listPixelClassifiers();
//	
//	public PixelClassifier loadPixelClassifier(String name);
//	
//	public void savePixelClassifier(String name, String PixelClassifier);

	
}
