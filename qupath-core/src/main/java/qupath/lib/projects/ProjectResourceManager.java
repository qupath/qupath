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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServers;

/**
 * Simple manager to handle saving and retrieving resources of different kinds from projects.
 * <p>
 * Examples of resources are scripts or classifiers. Several of these may be stored per project, 
 * and may be identified by name.
 * 
 * @author Pete Bankhead
 *
 * @param <T> the generic type of the resource being managed
 */
public interface ProjectResourceManager<T> {
	
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
	public T getResource(String name) throws IOException;
	
	/**
	 * Save a resource within the project.
	 * @param name
	 * @param resource
	 * @throws IOException
	 */
	public void putResource(String name, T resource) throws IOException;
		
}
	
abstract class FileResourceManager<T> implements ProjectResourceManager<T> {
	
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


class StringFileResourceManager extends FileResourceManager<String> {

	StringFileResourceManager(Path dir) {
		super(dir, ".txt");
	}
	
	StringFileResourceManager(Path dir, String ext) {
		super(dir, ext);
	}

	@Override
	public String getResource(String name) throws IOException {
		var path = Paths.get(dir.toString(), name + ext);
		if (Files.exists(path))
			return Files.readString(path);
		throw new IOException("No script found with name '" + name + "'");
	}

	@Override
	public void putResource(String name, String resource) throws IOException {
		var path = Paths.get(ensureDirectoryExists(dir).toString(), name + ext);
		Files.writeString(path, resource, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
	}
	
}


class ImageResourceManager<T> extends FileResourceManager<ImageServer<T>> {

	private Class<T> cls;
	
	ImageResourceManager(Path dir, Class<T> cls) {
		super(dir, ".json");
		this.cls = cls;
	}

	@Override
	public ImageServer<T> getResource(String name) throws IOException {
		var path = Paths.get(dir.toString(), name + ext);
		try (var reader = Files.newBufferedReader(path)) {
			return ImageServers.fromJson(reader, cls);
		}
	}

	@Override
	public void putResource(String name, ImageServer<T> server) throws IOException {
		var path = Paths.get(ensureDirectoryExists(dir).toString(), name + ext);
		var json = ImageServers.toJson(server, true);
		Files.writeString(path, json, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
	}
	
}


class SerializableFileResourceManager<T extends Serializable> extends FileResourceManager<T> {

	private Class<T> cls;
	
	SerializableFileResourceManager(Path dir, Class<T> cls) {
		super(dir, ".serialized");
		this.cls = cls;
	}

	@Override
	public T getResource(String name) throws IOException {
		var path = Paths.get(dir.toString(), name + ext);
		try (var stream = Files.newInputStream(path)) {
			return cls.cast(new ObjectInputStream(new BufferedInputStream(stream)).readObject());
		} catch (ClassNotFoundException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void putResource(String name, T resource) throws IOException {
		var path = Paths.get(dir.toString(), name + ext);
		try (var stream = Files.newOutputStream(path)) {
			new ObjectOutputStream(new BufferedOutputStream(stream)).writeObject(resource);
		}
	}
	
}


class JsonFileResourceManager<T> extends FileResourceManager<T> {
	
	private Gson gson = new GsonBuilder().setLenient().serializeSpecialFloatingPointValues().create();
	private Class<T> cls;
	
	JsonFileResourceManager(Path dir, Class<T> cls) {
		super(dir, ".json");
		this.cls = cls;
	}

	@Override
	public T getResource(String name) throws IOException {
		var path = Paths.get(dir.toString(), name + ext);
		try (var reader = Files.newBufferedReader(path)) {
			return gson.fromJson(reader, cls);
		}
	}

	@Override
	public void putResource(String name, T resource) throws IOException {
		if (!Files.exists(dir))
			Files.createDirectories(dir);
		var path = Paths.get(dir.toString(), name + ext);
		
		try (var writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE)) {
			gson.toJson(resource, cls, writer);
		}
	}
	
}