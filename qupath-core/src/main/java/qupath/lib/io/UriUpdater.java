/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;
import qupath.lib.projects.Project;

/**
 * Helper class for updating URIs, for example whenever files have moved or projects have been transferred between computers.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class UriUpdater<T extends UriResource> {

	private static final Logger logger = LoggerFactory.getLogger(UriUpdater.class);

	private Collection<T> resources;
	private Collection<SingleUriItem> items;
	private Map<SingleUriItem, SingleUriItem> replacements;

	private int maxDepth = 2;

	
	/**
	 * Create a new UriUpdater to support updating URIs for a single {@link UriResource}.
	 * @param resource
	 * @return
	 * @throws IOException
	 */
	public static <T extends UriResource> UriUpdater<T> create(T resource) throws IOException {
		return create(Collections.singleton(resource));
	}
	
	/**
	 * Create a new UriUpdater to support updating URIs for one or more {@link UriResource} objects.
	 * @param resources
	 * @return
	 * @throws IOException
	 */
	public static <T extends UriResource> UriUpdater<T> create(Collection<T> resources) throws IOException {
		return create(new ArrayList<>(resources), new ArrayList<>(), new HashMap<>());
	}
	
	/**
	 * Create a new UriUpdater using the specified collections.
	 * The main use of this is as a convenience to build a UI using observable collections.
	 * 
	 * @param resources collection of resources that may include URIs
	 * @param items collection containing all the distinct URIs from resources, each as a {@link SingleUriItem}. 
	 *              Note that this collection will be regenerated, so any existing items in the collection will be removed.
	 * @param replacements map used to store any replacements identified by the {@link UriUpdater}. Note that any existing contents will be discarded.
	 * @return
	 * @throws IOException
	 */
	public static <T extends UriResource> UriUpdater<T> create(Collection<T> resources, Collection<SingleUriItem> items, Map<SingleUriItem, SingleUriItem> replacements) throws IOException {
		return new UriUpdater<>(resources, items, replacements);
	}
	
	
	/**
	 * Wrap one or more URIs in a {@link UriResource} so they can be updated together.
	 * Any changes can then be requred from the {@link UriResource}.
	 * 
	 * @param uris
	 * @return
	 */
	public static UriResource wrap(URI... uris) {
		return new DefaultUriResource(uris);
	}
	
	/**
	 * Wrap one or more URIs in a {@link UriResource} so they can be updated together.
	 * Note that the collection is not used directly. Any changes should be accessed from the 
	 * {@link UriResource}.
	 * 
	 * @param uris
	 * @return
	 */	
	public static UriResource wrap(Collection<URI> uris) {
		return wrap(uris.toArray(URI[]::new));
	}
	
	/**
	 * Attempt to update a URI to find an existing file using the specified search paths.
	 * @param uri the URI to search for
	 * @param searchDepth the depth of the search (i.e. how many subdirectories)
	 * @param searchPaths the base directories to search, in order
	 * @return a new URI corresponding to an existing file with the same name, 
	 *         or the original URI if no replacement was found or required
	 * @throws IOException 
	 */
	public static URI locateFile(URI uri, int searchDepth, Path... searchPaths) throws IOException {
		var resource = wrap(uri);
		var updater = create(resource);
		int nMissing = updater.countMissing();
		if (nMissing == 0)
			return uri;
		updater.searchDepth(searchDepth);
		for (var p : searchPaths) {
			if (p == null)
				continue;
			if (!Files.isDirectory(p))
				p = p.getParent();
			updater.searchPath(p);
			updater.applyReplacements();
			nMissing = updater.countMissing();
			if (nMissing == 0)
				break;
		}
		return resource.getURIs().iterator().next();
	}
	
	/**
	 * Attempt to update a file using the specified search paths.
	 * @param path the path to a file that may or may not exist
	 * @param searchDepth the depth of the search (i.e. how many subdirectories)
	 * @param searchPaths the base directories to search, in order
	 * @return the path to a file with the same name as 'path' that <i>does</i> exist, 
	 *         or path unchanged if no existing file could be found.
	 * @throws IOException 
	 */
	public static String locateFile(String path, int searchDepth, Path... searchPaths) throws IOException {
		try {
			var uri = GeneralTools.toURI(path);
			var uri2 = locateFile(uri, searchDepth, searchPaths);
			if (uri2 == null || Objects.equals(uri, uri2))
				return path;
			return Paths.get(uri2).toAbsolutePath().toString();
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
	}
	
	
	/**
	 * Attempt to fix any broken paths by updating URIs based upon the current project.
	 * @param resource the resource to update
	 * @param project
	 * @return number of URIs that were updated
	 */
	public static int fixUris(UriResource resource, Project<?> project) {
		int n = 0;
		try {
			var uris = resource.getURIs();
			if (uris.isEmpty())
				return n;
			
			var updater = UriUpdater.create(resource);
			int nMissing = updater.countMissing();
			if (nMissing == 0)
				return n;
			
			if (project != null) {
				// Try to fix with relative changes from the project
				updater.relative(project.getPreviousURI(), project.getURI());
				n += updater.applyReplacements();
				if (updater.countMissing() == 0)
					return n;
				
				// Try to fix by searching within the current project
				var path = project.getPath();
				if (!Files.isDirectory(path))
					path = path.getParent();
				updater.searchDepth(4);
				updater.searchPath(path);
				n += updater.applyReplacements();
				if (updater.countMissing() == 0)
					return n;
				
			}
			
		} catch (IOException e) {
			logger.warn("Error fixing URIs for '{}'", resource);
			logger.debug(e.getLocalizedMessage(), e);
		}
		return n;
	}
	
	
//	public static <T extends UriResource> int updateUris(Collection<T> resources, )
	
	
	
	private UriUpdater(Collection<T> resources, Collection<SingleUriItem> items, Map<SingleUriItem, SingleUriItem> replacements) throws IOException {
		this.resources = resources;
		this.items = items;
		this.replacements = replacements;

		updateItems();
		this.replacements.clear();
	}

	/**
	 * Maximum search depth when using {@link #searchDepth(int)} to match filenames in directories and subdirectories.
	 * @param maxDepth
	 * @return
	 */
	public UriUpdater<T> searchDepth(int maxDepth) {
		this.maxDepth = maxDepth;
		return this;
	}

	/**
	 * Identify replacements for missing URIs by relativizing URI.
	 * This is generally used to make corrections whenever a project has been moved.
	 * 
	 * @param uriOriginal the previous path (usually for the project)
	 * @param uriCurrent the current path
	 * @return
	 */
	public UriUpdater<T> relative(URI uriOriginal, URI uriCurrent) {
		var pathOriginal = uriOriginal == null ? null : GeneralTools.toPath(uriOriginal);
		var pathCurrent = uriCurrent == null ? null : GeneralTools.toPath(uriCurrent);
		return relative(pathOriginal, pathCurrent);
	}

	/**
	 * Identify replacements for missing URIs by relativizing paths.
	 * This is generally used to make corrections whenever a project has been moved.
	 * 
	 * @param pathOriginal the previous path (usually for the project)
	 * @param pathCurrent the current path
	 * @return
	 */
	public UriUpdater<T> relative(Path pathOriginal, Path pathCurrent) {
		if (pathOriginal != null && pathCurrent != null) {
			int n = updateReplacementsRelative(items, pathOriginal, pathCurrent, replacements);
			if (n > 0)
				logger.debug("Updated {} URI(s) with relative paths", n);
			else
				logger.trace("Updated {} URIs with relative paths", n);
		}
		return this;
	}

	/**
	 * Search for filenames that match missing URIs, recursively up to the depth specified by {@link #searchDepth(int)}.
	 * @param path the base directory within which to search
	 * @return
	 * @see #searchDepth(int)
	 */
	public UriUpdater<T> searchPath(Path path) {
		try {
			int n = searchDirectoriesRecursive(path.toFile(), items, maxDepth, replacements);
			logger.debug("Updated {} URI(s) with relative paths", n);
			return this;
		} catch (Exception e) {
			logger.error("Error searching {}", path);
			logger.error(e.getLocalizedMessage(), e);
			return this;
		}
	}

	/**
	 * Add a single replacement to the replacement map.
	 * @param originalItem current URI for a missing resource
	 * @param updatedItem updated URI to use instead
	 * @return
	 */
	public UriUpdater<T> makeReplacement(URI originalItem, URI updatedItem) {
		var item1 = new SingleUriItem(originalItem);
		var item2 = updatedItem == null ? null : new SingleUriItem(updatedItem);
		if (item2 == null || Objects.equals(item1, item2))
			this.replacements.remove(item1);
		else
			this.replacements.put(item1, item2);
		return this;
	}

	/**
	 * Get a map of all replacements.
	 * @return
	 */
	public Map<URI, URI> getReplacements() {
		return Collections.unmodifiableMap(
				replacements.entrySet().stream().filter(s -> s.getValue() != null).collect(Collectors.toMap(s -> s.getKey().getURI(), s -> s.getValue().getURI()))
				);
	}
	
	/**
	 * Apply all current replacements, updating the {@link UriResource} objects.
	 * @return
	 * @throws IOException
	 */
	public int applyReplacements() throws IOException {
		if (replacements.isEmpty())
			return 0;
		int n = replaceItems(resources, replacements);
		updateItems();
		replacements.clear();
		return n;
	}
	
	
	private void updateItems() throws IOException {
		var newItems = getItems(resources);
		if (!newItems.equals(items)) {
			this.items.clear();
			this.items.addAll(newItems);
		}
	}
	
	/**
	 * Get a count of the items flagged as missing.
	 * @return
	 */
	public int countMissing() {
		return getMissingItems().size();
	}
	
	/**
	 * Get a count of the number of replacements for missing items.
	 * @return
	 */
	public int countReplacements() {
		return (int)replacements.entrySet().stream()
				.filter(e -> e.getKey().getStatus() == UriStatus.MISSING && e.getValue() != null && e.getValue().getStatus() != UriStatus.MISSING)
				.count();
	}
	
	/**
	 * Get all missing items.
	 * @return
	 */
	public Collection<SingleUriItem> getMissingItems() {
		return getItems(UriStatus.MISSING);
	}
	
	/**
	 * Get all items with the specified status, or all items is status is null.
	 * @param status
	 * @return
	 */
	public Collection<SingleUriItem> getItems(UriStatus status) {
		if (status == null)
			return Collections.unmodifiableCollection(items);
		return items.stream().filter(i -> i.getStatus() == status).collect(Collectors.toList());
	}

	

	private static int updateReplacementsRelative(Collection<SingleUriItem> items, Path pathPrevious, Path pathBase, Map<SingleUriItem, SingleUriItem> replacements) {

		// We care about the directory rather than the actual file
		if (pathBase != null && !Files.isDirectory(pathBase)) {
			pathBase = pathBase.getParent();
		}
		if (pathPrevious != null && !Files.isDirectory(pathPrevious)) {
			pathPrevious = pathPrevious.getParent();
		}
		boolean tryRelative = pathBase != null && pathPrevious != null && !pathBase.equals(pathPrevious);
		
		int count = 0;

		// Map the URIs to a list of potential replacements
		for (var item : items) {
			if (item.getStatus() == UriStatus.MISSING && replacements.getOrDefault(item, null) == null) {
				Path pathItem = item.getPath();
				try {
					if (tryRelative &&
							pathItem != null &&
							pathPrevious != null &&
							Objects.equals(pathItem.getRoot(), pathPrevious.getRoot())
							) {
						Path pathRelative = pathBase.resolve(pathPrevious.relativize(pathItem));
						if (Files.exists(pathRelative)) {
							URI uri2 = pathRelative.normalize().toUri().normalize();
							replacements.put(item, new SingleUriItem(uri2));
							count++;
						}
					}
				} catch (Exception e) {
					// Shouldn't occur (but being extra careful in case resolve/normalize/toUri or sth else complains)
					logger.warn("Error relativizing paths: {}", e.getLocalizedMessage());
					logger.debug(e.getLocalizedMessage(), e);
				}
			}
		}
		return count;
	}


	private static int replaceItems(Collection<? extends UriResource> uriResources, Map<SingleUriItem, SingleUriItem> replacements) throws IOException {
		Map<URI, URI> map = replacements.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().getURI(), e -> e.getValue().getURI()));
		return replace(uriResources, map);
	}


	private static int replace(Collection<? extends UriResource> uriResources, Map<URI, URI> replacements) throws IOException {
		int count = 0;
		for (var entry : uriResources) {
			if (entry.updateURIs(replacements))
				count++;
		}
		return count;
	}


	private static List<SingleUriItem> getItems(Collection<? extends UriResource> uriResources) throws IOException {
		// Get all the URIs
		Set<URI> imageUris = new LinkedHashSet<>();
		for (var item : uriResources) {
			imageUris.addAll(item.getURIs());
		}
		return imageUris.stream().map(u -> new SingleUriItem(u)).collect(Collectors.toList());
	}


	private static int searchDirectoriesRecursive(File dir, Collection<SingleUriItem> allItems, int maxDepth, Map<SingleUriItem, SingleUriItem> replacements) {
		// Get a map of filenames and URIs for easier search
		Map<String, List<SingleUriItem>> missing = allItems.stream().filter(p -> p.getStatus() == UriStatus.MISSING && p.getPath() != null && replacements.getOrDefault(p, null) == null)
				.collect(Collectors.groupingBy(p -> p.getPath().getFileName().toString()));

		int sizeBefore = replacements.size();
		searchDirectoriesRecursive(dir, missing, maxDepth, replacements);
		return replacements.size() - sizeBefore;
	}


	private static void searchDirectoriesRecursive(File dir, Map<String, List<SingleUriItem>> missing, int maxDepth, Map<SingleUriItem, SingleUriItem> replacements) {
		if (dir == null || !dir.canRead() || !dir.isDirectory() || missing.isEmpty() || maxDepth <= 0)
			return;

		List<File> subdirs = new ArrayList<>();

		logger.debug("Searching {}", dir);
		var list = dir.listFiles();
		if (list == null)
			return;
		
		for (File f : list) {
			if (f == null)
				continue;
			if (f.isHidden())
				continue;
			else if (f.isFile()) {
				// If we find something with the correct name, update the URIs
				String name = f.getName();
				List<SingleUriItem> myList = missing.remove(name);
				if (myList != null) {
					for (var item : myList)
						replacements.put(item, new SingleUriItem(f.toURI()));
				}
				// Check if we are done
				if (missing.isEmpty())
					return;
			} else if (f.isDirectory()) {
				subdirs.add(f);
			}
		}
		for (File subdir : subdirs) {
			searchDirectoriesRecursive(subdir, missing, maxDepth-1, replacements);
		}
	}


	/**
	 * Enum representing the status of a URI, i.e. whether it is known to be accessible or not.
	 */
	public static enum UriStatus { 
		/**
		 * URI refers to a file that is known to exist.
		 */
		EXISTS,
		/**
		 * URI refers to a file that does not appear to exist or is inaccessible.
		 */
		MISSING,
		/**
		 * URI status is unclear, e.g. because it does not refer to a file.
		 */
		UNKNOWN;
	}

	/**
	 * Wrapper for a URI, providing access to a {@link Path} if available.
	 */
	public static class SingleUriItem {

		private URI uri;
		private Path path;

		private SingleUriItem(URI uri) {
			this.uri = uri;
			this.path = GeneralTools.toPath(uri);
		}

		/**
		 * Get the URI status.
		 * @return
		 */
		public UriStatus getStatus() {
			if (path == null)
				return UriStatus.UNKNOWN;
			if (Files.exists(path))
				return UriStatus.EXISTS;
			return UriStatus.MISSING;
		}

		/**
		 * Get the URI.
		 * @return
		 */
		public URI getURI() {
			return uri;
		}

		/**
		 * Get the {@link Path} corresponding to the URI, or none if the URI does not refer to a file.
		 * @return
		 */
		public Path getPath() {
			return path;
		}

		@Override
		public String toString() {
			if (path == null)
				return uri.toString();
			return path.toString();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((path == null) ? 0 : path.hashCode());
			result = prime * result + ((uri == null) ? 0 : uri.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			SingleUriItem other = (SingleUriItem) obj;
			if (path == null) {
				if (other.path != null)
					return false;
			} else if (!path.equals(other.path))
				return false;
			if (uri == null) {
				if (other.uri != null)
					return false;
			} else if (!uri.equals(other.uri))
				return false;
			return true;
		}

	}
	
	
	static class DefaultUriResource implements UriResource {
		
		private List<URI> uris;
		
		DefaultUriResource(URI...uris) {
			this.uris = Arrays.asList(uris);
		}

		@Override
		public Collection<URI> getURIs() throws IOException {
			return Collections.unmodifiableList(uris);
		}

		@Override
		public boolean updateURIs(Map<URI, URI> replacements) throws IOException {
			boolean changes = false;
			for (int i = 0; i < uris.size(); i++) {
				var uri = uris.get(i);
				var replace = replacements.get(uri);
				if (!Objects.equals(uri, replace)) {
					uris.set(i, replace);
					changes = true;
				}
			}
			return changes;
		}
		
	}

}
