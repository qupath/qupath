package qupath.lib.objects;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import qupath.lib.common.GeneralTools;
import qupath.lib.io.GsonTools;

/**
 * Class to export {@code PathObject}s in GeoJSON format.
 * 
 * @author Melvin Gelbard
 */
public final class PathObjectIO {
	
	/**
	 * Compatible extensions
	 */
	final static String[] exts = new String[] {".geojson", ".zip"};
	
	// Suppress default constructor for non-instantiability
	private PathObjectIO() {
		throw new AssertionError();
	}
	
	/**
	 * Export the {@code objs} to the {@code outFile} in GeoJSON format (compressed or not, based on the file extension).
	 * 
	 * @param objs
	 * @param outFile
	 * @param includeMeasurements 
	 * @param prettyPrint
	 * @throws IOException
	 */
	public static void exportObjectsToGeoJson(Collection<? extends PathObject> objs, File outFile, boolean includeMeasurements, boolean prettyPrint) throws IOException {
		try (var bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
			if (outFile.getPath().endsWith(".zip")) {
				try (var zos = new ZipOutputStream(bos)) {
					ZipEntry entry = new ZipEntry(GeneralTools.getNameWithoutExtension(outFile) + ".geojson");
					zos.putNextEntry(entry);
					exportObjectsToGeoJson(objs, zos, includeMeasurements,prettyPrint);
					zos.closeEntry();
				}
			} else
				exportObjectsToGeoJson(objs, bos, includeMeasurements, prettyPrint);
		}
	}
	
	/**
	 * Export the {@code objs} to the {@code OutputStream} in GeoJSON format.
	 * <p>
	 * The {@code OutputStream} is <b>not</b> automatically closed after completion of this method.
	 * 
	 * @param objs
	 * @param bos
	 * @param includeMeasurements
	 * @param prettyPrint
	 * @throws IOException
	 */
	public static void exportObjectsToGeoJson(Collection<? extends PathObject> objs, OutputStream bos, boolean includeMeasurements, boolean prettyPrint) throws IOException {
		// If exclude measurements, 'transform' each PathObject to get rid of measurements
		if (!includeMeasurements)
			objs.stream().map(e -> PathObjectTools.transformObject(e, null, false)).collect(Collectors.toList());
		
		byte[] out = GsonTools.getInstance(prettyPrint).toJson(objs).getBytes(Charset.forName("UTF-8"));
		bos.write(out);
	}
	
	/**
	 * Extract the {@code PathObject}s from the given file.
	 * 
	 * @param file
	 * @return objs
	 * @throws ClassNotFoundException
	 * @throws IllegalArgumentException 
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static List<PathObject> extractObjectsFromFile(File file) throws ClassNotFoundException, IllegalArgumentException, FileNotFoundException, IOException {
		List<PathObject> objs = new ArrayList<>();

		try (var fis = new FileInputStream(file)) {
			if (file.getPath().endsWith(exts[0]))			// .geojson
					objs = PathObjectIO.importObjectsFromGeoJson(fis);
			else if (file.getPath().endsWith(exts[1])) {	// .zip
				try (var zipFile = new ZipFile(file)) {
					objs = PathObjectIO.importObjectsFromZip(zipFile);
				}
			} else
				throw new IllegalArgumentException("File not supported: " + file.getPath());
		}
		
		return objs;
	}
	
	/**
	 * Create a collection of {@code PathObject}s from GeoJson input (via stream).
	 * 
	 * @param stream
	 * @return pathObjects
	 * @throws IOException
	 */
	public static List<PathObject> importObjectsFromGeoJson(InputStream stream) throws IOException {
		// Prepare template
		var type = new TypeToken<List<PathObject>>() {}.getType();
		
		// Deserialize
		try (InputStreamReader isr = new InputStreamReader(stream); JsonReader reader = new JsonReader(isr)) {
			return GsonTools.getInstance().fromJson(reader, type);
		}
	}
	
	/**
	 * Create a collection of {@code PathObject}s from Java serialized input (via stream).
	 * 
	 * @param bytes 
	 * @return pathObjects
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	public static List<PathObject> importObjectsFromGeoJson(byte[] bytes) throws IOException, ClassNotFoundException {
		// Prepare template
		var type = new TypeToken<List<PathObject>>() {}.getType();
		
		try (JsonReader reader = new JsonReader(new InputStreamReader(new ByteArrayInputStream(bytes)))) {
			return GsonTools.getInstance().fromJson(reader, type);
		}
	}
	
	/**
	 * Create a collection of {@code PathObject}s from a ZIP file.
	 * 
	 * @param zipFile
	 * @return pathObjects
	 * @throws IOException 
	 * @throws ZipException 
	 * @throws ClassNotFoundException 
	 */
	public static List<PathObject> importObjectsFromZip(ZipFile zipFile) throws ZipException, IOException, ClassNotFoundException {
		List<PathObject> list = new ArrayList<>();
		
		// In case we have more than one compressed file, iterate through each entry
		for (ZipEntry entry : Collections.list(zipFile.entries())) {
			try (final BufferedInputStream input = new BufferedInputStream(zipFile.getInputStream(entry))) {
				if (entry.getName().endsWith(".geojson"))
					list.addAll(importObjectsFromGeoJson(input.readAllBytes()));
			}
		}
		return list;
	}
	
	/**
	 * Return an String array of compatible extension for {@code PathObject}s I/O.
	 * 
	 * @return extensions
	 */
	public static String[] getCompatibleFileExtensions() {
		return exts;
	}

	/**
	 * Return whether the {@code PathObject} is an ellipse.
	 * 
	 * @param ann
	 * @return isEllipse
	 */
	public static boolean isEllipse(PathObject ann) {
		return ann.getROI() != null && ann.getROI().getRoiName().equals("Ellipse");
	}
}
