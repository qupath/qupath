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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerProvider;
import qupath.lib.objects.classes.PathClass;

/**
 * Data structure to store multiple images, relating these to a file system.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class Project<T> {
	
	private static Logger logger = LoggerFactory.getLogger(Project.class);
	
	private File file;
	private File dirBase;
	private Class<T> cls;
	private String name = null;
	
	private List<PathClass> pathClasses = new ArrayList<>();
	
	private Map<String, ProjectImageEntry<T>> images = new LinkedHashMap<>();
	long creationTimestamp;
	long modificationTimestamp;
	
	public Project(final File file, final Class<T> cls) {
		this.file = file;
		if (file.isDirectory())
			this.dirBase = file;
		else
			this.dirBase = file.getParentFile();
		this.cls = cls;
		creationTimestamp = System.currentTimeMillis();
		modificationTimestamp = System.currentTimeMillis();
	}
	
	/**
	 * Get an unmodifiable list representing the <code>PathClass</code>es associated with this project.
	 * @return
	 */
	public List<PathClass> getPathClasses() {
		return Collections.unmodifiableList(pathClasses);
	}
	
	/**
	 * Update the available PathClasses.
	 * 
	 * @param pathClasses
	 * @return <code>true</code> if the stored values changed, false otherwise.
	 */
	public boolean setPathClasses(Collection<? extends PathClass> pathClasses) {
		if (this.pathClasses.size() == pathClasses.size() && this.pathClasses.containsAll(pathClasses))
			return false;
		this.pathClasses.clear();
		this.pathClasses.addAll(pathClasses);
		return true;
	}

	public boolean addImage(final ProjectImageEntry<T> entry) {
		if (images.containsKey(cleanServerPath(entry.getServerPath())))
			return false;
		images.put(cleanServerPath(entry.getServerPath()), entry);
		return true;
	}
	
	public File getFile() {
		return file;
	}
	
	public File getBaseDirectory() {
		return dirBase;
	}
	
	public boolean addAllImages(final Collection<ProjectImageEntry<T>> entries) {
		boolean changes = false;
		for (ProjectImageEntry<T> entry : entries)
			changes = addImage(entry) | changes;
		return changes;
	}
	
	public int size() {
		return images.size();
	}

	public boolean isEmpty() {
		return images.isEmpty();
	}

	public boolean addImagesForServer(final ImageServer<T> server) {
		
		List<String> subImages = server.getSubImageList();
		if (subImages.isEmpty()) {
			return addImage(new ProjectImageEntry<>(this, server.getPath(), server.getDisplayedImageName(), null));
		}
		
		boolean changes = false;
		for (String name : subImages)
			// The sub image name might be the same across images, we should append the server displayed name to it, just to make sure it is unique
			changes = changes | addImage(new ProjectImageEntry<>(this, server.getSubImagePath(name), server.getDisplayedImageName()+" ("+name+")", null));
		return changes;
	}
	
	
	public ProjectImageEntry<T> getImageEntry(final String path) {
		return images.get(cleanServerPath(path));
	}

	String cleanServerPath(final String path) {
		String cleanedPath = path.replace("%20", " ").replace("%5C", "\\");
		cleanedPath = cleanedPath.replace("{$PROJECT_DIR}", getBaseDirectory().getAbsolutePath());
		return cleanedPath;
	}
	
	public boolean addImage(final String path) {
		try {
			ImageServer<T> server = ImageServerProvider.buildServer(path, cls);
			boolean changes = addImagesForServer(server);
			server.close();
			return changes;
		} catch (Exception e) {
			logger.error("Error adding image: {} ({})", path, e.getLocalizedMessage());
			return false;
		}
	}
	
	public void removeImage(final ProjectImageEntry<?> entry) {
		removeImage(entry.getServerPath());
	}

	public void removeAllImages(final Collection<ProjectImageEntry<T>> entries) {
		for (ProjectImageEntry<T> entry : entries)
			removeImage(entry);
	}
	
	public void removeImage(final String path) {
		images.remove(path);
	}

	/**
	 * Get a list of image entries for the project.
	 * 
	 * @return
	 */
	public List<ProjectImageEntry<T>> getImageList() {
		List<ProjectImageEntry<T>> list = new ArrayList<>(images.values());
//		list.sort(ImageEntryComparator.instance);
		return list;
	}
	
	public ImageServer<T> buildServer(final ProjectImageEntry<T> entry) {
		return ImageServerProvider.buildServer(entry.getServerPath(), cls);
	}
	
	
	public String getName() {
		if (name != null)
			return name;
		if (dirBase == null || !dirBase.isDirectory()) {
			return "(Project directory missing)";
		}
		if (file != null && file.exists() && file != dirBase) {
			return dirBase.getName() + "/" + file.getName();
		}
		return dirBase.getName();
	}
	
	@Override
	public String toString() {
		return "Project: " + getName();
	}
	
	
	public long getCreationTimestamp() {
		return creationTimestamp;
	}
	
	public long getModificationTimestamp() {
		return modificationTimestamp;
	}
	
	
	
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
