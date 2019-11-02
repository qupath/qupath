package qupath.lib.projects;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.google.gson.reflect.TypeToken;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.GsonTools;

/**
 * Manage the storage and retrieval of resources with a {@link ResourceManager}.
 * Examples may include pixel classifiers or scripts.
 * By using this it is possible to avoid reliance on a filesystem, for example, 
 * opening the possibility to have resources stored elsewhere.
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
			
	}


	abstract static class FileResourceManager<T> implements Manager<T> {
		
		protected String ext;
		protected Path dir;
		
		private List<String> listFilenames(Path path, String ext) throws IOException {
			if (!Files.isDirectory(path))
				return Collections.emptyList();
			var list = Files.list(path).filter(p -> Files.isRegularFile(p) && p.toString().endsWith(ext)).map(p -> nameWithoutExtension(p, ext)).collect(Collectors.toList());
			return list;
		}
		
		private String nameWithoutExtension(Path path, String ext) {
			String name = path.getFileName().toString();
			if (name.endsWith(ext))
				return name.substring(0, name.length()-ext.length());
			return name;
		}
		
		Path ensureDirectoryExists(Path path) throws IOException {
			if (!Files.isDirectory(path))
				Files.createDirectories(path);
			return path;
		}
		
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
		
	}


	static class StringFileResourceManager extends FileResourceManager<String> {

		StringFileResourceManager(Path dir) {
			super(dir, ".txt");
		}
		
		StringFileResourceManager(Path dir, String ext) {
			super(dir, ext);
		}

		@Override
		public String get(String name) throws IOException {
			var path = Paths.get(dir.toString(), name + ext);
			if (Files.exists(path))
				return Files.readString(path);
			throw new IOException("No script found with name '" + name + "'");
		}

		@Override
		public void put(String name, String resource) throws IOException {
			var path = Paths.get(ensureDirectoryExists(dir).toString(), name + ext);
			Files.writeString(path, resource, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		}
		
	}


	static class ImageResourceManager<T> extends FileResourceManager<ImageServer<T>> {

		private Class<T> cls;
		
		ImageResourceManager(Path dir, Class<T> cls) {
			super(dir, ".json");
			this.cls = cls;
		}

		@Override
		public ImageServer<T> get(String name) throws IOException {
			var path = Paths.get(dir.toString(), name + ext);
			try (var reader = Files.newBufferedReader(path)) {
				return GsonTools.getInstance().fromJson(reader, new TypeToken<ImageServer<T>>() {}.getType());
			}
		}

		@Override
		public void put(String name, ImageServer<T> server) throws IOException {
			var path = Paths.get(ensureDirectoryExists(dir).toString(), name + ext);
			var json = GsonTools.getInstance().toJson(server);
			Files.writeString(path, json, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		}
		
	}


	static class SerializableFileResourceManager<T extends Serializable> extends FileResourceManager<T> {

		private Class<T> cls;
		
		SerializableFileResourceManager(Path dir, Class<T> cls) {
			super(dir, ".serialized");
			this.cls = cls;
		}

		@Override
		public T get(String name) throws IOException {
			var path = Paths.get(dir.toString(), name + ext);
			try (var stream = Files.newInputStream(path)) {
				return cls.cast(new ObjectInputStream(new BufferedInputStream(stream)).readObject());
			} catch (ClassNotFoundException e) {
				throw new IOException(e);
			}
		}

		@Override
		public void put(String name, T resource) throws IOException {
			var path = Paths.get(dir.toString(), name + ext);
			try (var stream = Files.newOutputStream(path)) {
				new ObjectOutputStream(new BufferedOutputStream(stream)).writeObject(resource);
			}
		}
		
	}


	static class JsonFileResourceManager<T> extends FileResourceManager<T> {
		
		private Class<T> cls;
		
		JsonFileResourceManager(Path dir, Class<T> cls) {
			super(dir, ".json");
			this.cls = cls;
		}

		@Override
		public T get(String name) throws IOException {
			var path = Paths.get(dir.toString(), name + ext);
			try (var reader = Files.newBufferedReader(path)) {
				return GsonTools.getInstance(true).fromJson(reader, cls);
			}
		}

		@Override
		public void put(String name, T resource) throws IOException {
			if (!Files.exists(dir))
				Files.createDirectories(dir);
			var path = Paths.get(dir.toString(), name + ext);
			
			try (var writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
				var gson = GsonTools.getInstance(true);
//				String json = gson.toJson(resource, cls);
//				writer.write(gson.toJson(resource, cls));
//				writer.write(json);
				gson.toJson(resource, cls, writer);
			}
		}
		
	}

}