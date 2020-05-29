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

package qupath.lib.projects;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;

/**
 * Manage the storage and retrieval of resources with a {@link ResourceManager}.
 * Examples may include pixel classifiers or scripts.
 * By using this it is possible to avoid reliance on a filesystem, for example, 
 * opening the possibility to have resources stored elsewhere.
 * <p>
 * Note that names may be case-insensitive, depending upon the specific backing store.
 * This is the case for the default implementations using file storage.
 * 
 * @author Pete Bankhead
 */
public class ResourceManager {
	
	/**
	 * Simple manager to handle saving and retrieving resources of different kinds, for example from projects 
	 * or a user directory.
	 * <p>
	 * Examples of resources are scripts or classifiers. Several of these may be stored per project, 
	 * and may be identified by name.
	 * 
	 * @author Pete Bankhead
	 *
	 * @param <T> the generic type of the resource being managed
	 */
	public interface Manager<T> {
		
		/**
		 * Get a list of the available resources.
		 * @return
		 * @throws IOException
		 */
		public Collection<String> getNames() throws IOException;
		
		/**
		 * Retrieve a resource by name.
		 * @param name
		 * @return
		 * @throws IOException
		 */
		public T get(String name) throws IOException;
		
		/**
		 * Save a resource within the project.
		 * @param name
		 * @param resource
		 * @throws IOException
		 */
		public void put(String name, T resource) throws IOException;
		
		/**
		 * Remove a resource within the project.
		 * @param name
		 * @return true if a resource was successfully removed, false otherwise
		 * @throws IOException
		 */
		public boolean remove(String name) throws IOException;
		
		/**
		 * Returns true if the manager knows a resource with the specified name exists.
		 * @param name the name to check
		 * @return true if a resource with the name exists, false otherwise
		 * @throws IOException
		 */
		default public boolean contains(String name) throws IOException {
			var names = getNames();
			for (var n : names) {
				if (name.equalsIgnoreCase(n))
					return true;
			}
			return false;
		}
			
	}
	
	
	private static Map<String, Path> getNameMap(Path path, String ext) throws IOException {
		if (path == null || !Files.isDirectory(path))
			return Collections.emptyMap();
		return Files.list(path)
				.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(ext))
				.collect(Collectors.toMap(p -> nameWithoutExtension(p, ext), p -> p));
	}
	
	private static Collection<String> listFilenames(Path path, String ext) throws IOException {
		return getNameMap(path, ext).keySet();
	}
	
	private static String nameWithoutExtension(Path path, String ext) {
		String name = path.getFileName().toString();
		if (name.endsWith(ext))
			return name.substring(0, name.length()-ext.length());
		return name;
	}
	
	private static Path ensureDirectoryExists(Path path) throws IOException {
		if (!Files.isDirectory(path))
			Files.createDirectories(path);
		return path;
	}


	abstract static class FileResourceManager<T> implements Manager<T> {
		
		private final static Logger logger = LoggerFactory.getLogger(FileResourceManager.class);
		
		protected String ext;
		protected Path dir;
		
		FileResourceManager(Path dir, String ext) {
			this.dir = dir;
			if (ext.startsWith("."))
				this.ext = ext;
			else
				this.ext = "." + ext;
		}
		
		@Override
		public Collection<String> getNames() throws IOException {
			return listFilenames(dir, ext);
		}
		
		/**
		 * Remove a resource within the project.
		 * @param name
		 * @throws IOException
		 */
		@Override
		public boolean remove(String name) throws IOException {
			var path = getPathForName(name, true);
			if (path != null && Files.exists(path)) {
				logger.debug("Deleting resource '{}' from {}", name, path);
				GeneralTools.deleteFile(path.toFile(), true);
				return true;
			}
			return false;
		}
		
		protected abstract T readFromFile(Path path) throws IOException;
		
		protected abstract void writeToFile(Path path, T resource) throws IOException;
		
		@Override
		public T get(String name) throws IOException {
			var path = getPathForName(name, true);
			if (path != null && Files.exists(path)) {
				logger.debug("Reading resource '{}' from {}", name, path);
				return readFromFile(path);
			}
			throw new IOException("No resource found with name '" + name + "'");
		}

		@Override
		public void put(String name, T resource) throws IOException {
			var path = getPathForName(name, false);
			logger.debug("Writing resource '{}' to {}", name, path);
			writeToFile(path, resource);
			// Because this is case-insensitive, update the case if we need to
			var pathRequested = Paths.get(dir.toString(), name + ext);
			if (!path.equals(pathRequested)) {
				logger.debug("Renaming {} to {}", path, pathRequested);
				Files.move(path, pathRequested, StandardCopyOption.ATOMIC_MOVE);
			}
		}
		
		/**
		 * Get the {@link Path} for a specific name.
		 * If a file exists with the specific name (ignoring case), this will be returned.
		 * Otherwise, a new path will be created if nullIfMissing is false.
		 * @param name
		 * @param nullIfMissing
		 * @return
		 * @throws IOException
		 */
		protected Path getPathForName(String name, boolean nullIfMissing) throws IOException {
			var map = getNameMap(dir, ext);
			var path = map.getOrDefault(name, null);
			if (path == null) {
				for (var entry : map.entrySet()) {
					if (name.equalsIgnoreCase(entry.getKey()))
						return entry.getValue();
				}
			}
			if (path != null)
				return path;
			if (nullIfMissing)
				return null;
			ensureDirectoryExists(dir);
			return Paths.get(dir.toString(), name + ext);
		}
		
		
	}


	static class StringFileResourceManager extends FileResourceManager<String> {
		
		private Charset charset = StandardCharsets.UTF_8;

		StringFileResourceManager(Path dir) {
			super(dir, ".txt");
		}
		
		StringFileResourceManager(Path dir, String ext) {
			super(dir, ext);
		}

		@Override
		protected String readFromFile(Path path) throws IOException {
			return Files.readString(path, charset);
		}

		@Override
		protected void writeToFile(Path path, String resource) throws IOException {
			Files.writeString(path, resource, charset);
		}
		
	}


	static class ImageResourceManager<T> extends FileResourceManager<ImageServer<T>> {

		private Class<T> cls;
		
		ImageResourceManager(Path dir, Class<T> cls) {
			super(dir, ".json");
			this.cls = cls;
		}

		@Override
		protected ImageServer<T> readFromFile(Path path) throws IOException {
			try (var reader = Files.newBufferedReader(path)) {
				return GsonTools.getInstance().fromJson(reader, new TypeToken<ImageServer<T>>() {}.getType());
			}
		}

		@Override
		protected void writeToFile(Path path, ImageServer<T> resource) throws IOException {
			try (var writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GsonTools.getInstance().toJson(resource, writer);
			}
		}
		
	}


	static class SerializableFileResourceManager<T extends Serializable> extends FileResourceManager<T> {

		private Class<T> cls;
		
		SerializableFileResourceManager(Path dir, Class<T> cls) {
			super(dir, ".serialized");
			this.cls = cls;
		}

		@Override
		protected T readFromFile(Path path) throws IOException {
			try (var stream = Files.newInputStream(path)) {
				return cls.cast(new ObjectInputStream(new BufferedInputStream(stream)).readObject());
			} catch (ClassNotFoundException e) {
				throw new IOException(e);
			}
		}

		@Override
		protected void writeToFile(Path path, T resource) throws IOException {
			try (var stream = Files.newOutputStream(path)) {
				new ObjectOutputStream(new BufferedOutputStream(stream)).writeObject(resource);
			}
		}
		
	}


	static class JsonFileResourceManager<T> extends FileResourceManager<T> {
		
		private Charset charset = StandardCharsets.UTF_8;
		
		private Class<T> cls;
		
		JsonFileResourceManager(Path dir, Class<T> cls) {
			super(dir, ".json");
			this.cls = cls;
		}

		@Override
		protected T readFromFile(Path path) throws IOException {
			try (var reader = Files.newBufferedReader(path, charset)) {
				return GsonTools.getInstance().fromJson(reader, cls);
			}
		}

		@Override
		protected void writeToFile(Path path, T resource) throws IOException {
			try (var writer = Files.newBufferedWriter(path, charset)) {
				var gson = GsonTools.getInstance(true);
				gson.toJson(resource, cls, writer);
			}
		}
		
	}

}