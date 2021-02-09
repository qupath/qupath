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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
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

import javax.naming.OperationNotSupportedException;

import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;

import qupath.lib.common.GeneralTools;
import qupath.lib.io.GsonTools;
import qupath.lib.roi.interfaces.ROI;

/**
 * Class to export {@code PathObject}s as either serialized or GeoJSON format.
 * 
 * @author Melvin Gelbard
 */
public final class PathObjectIO {
	
	/**
	 * Compatible extensions
	 */
	final static String[] exts = new String[] {".geojson", ".qpdata", ".zip"};
	
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
	 * Export the {@code objs} to the {@code outFile} as serialized data (compressed or not, based on the file extension).
	 * <p>
	 * The {@code objs} collection must to be serializable.
	 * 
	 * @param objs
	 * @param outFile
	 * @param includeMeasurements
	 * @throws IOException
	 */
	public static void exportObjectsAsSerialized(Collection<? extends PathObject> objs, File outFile, boolean includeMeasurements) throws IOException {
		if (outFile.getPath().endsWith(".zip")) {
			try (var zos = new ZipOutputStream(new FileOutputStream(outFile))) {
				ZipEntry entry = new ZipEntry(GeneralTools.getNameWithoutExtension(outFile) + ".qpdata");
				zos.putNextEntry(entry);
				exportObjectsAsSerialized(objs, zos, includeMeasurements);
				zos.closeEntry();
			}
		} else {
			try (var bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
				exportObjectsAsSerialized(objs, bos, includeMeasurements);
			}			
		}
	}
	
	/**
	 * Export the {@code objs} to the {@code OutputStream} as serialized data.
	 * <p>
	 * The {@code objs} collection must to be serializable.
	 * <p>
	 * The {@code OutputStream} is <b>not</b> automatically closed after completion of this method.
	 * 
	 * @param objs
	 * @param bos
	 * @param includeMeasurements 
	 * @throws IOException
	 */
	public static void exportObjectsAsSerialized(Collection<? extends PathObject> objs, OutputStream bos, boolean includeMeasurements) throws IOException {
		// If exclude measurements, 'transform' each PathObject to get rid of measurements
		if (!includeMeasurements)
			objs.stream().map(e -> PathObjectTools.transformObject(e, null, false)).collect(Collectors.toList());
		
		try (var oos = new ObjectOutputStream(bos)) {
			oos.writeObject(new ArrayList<>(objs));
		}
	}
	
	/**
	 * Export the {@code objs} to the {@code outFile} in GeoJSON format (compressed or not, based on the file extension).
	 * 
	 * @param objs
	 * @param outFile
	 * @param prettyPrint
	 * @throws IOException
	 */
	public static void exportROIsToGeoJson(Collection<? extends ROI> objs, File outFile, boolean prettyPrint) throws IOException {
		try (var bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
			if (outFile.getPath().endsWith(".zip")) {
				try (var zos = new ZipOutputStream(bos)) {
					ZipEntry entry = new ZipEntry(GeneralTools.getNameWithoutExtension(outFile) + ".geojson");
					zos.putNextEntry(entry);
					exportROIsToGeoJson(objs, zos, prettyPrint);
					zos.closeEntry();
				}
			} else
				exportROIsToGeoJson(objs, bos, prettyPrint);
		}
	}
	
	/**
	 * Export the {@code objs} to the {@code OutputStream} in GeoJSON format.
	 * <p>
	 * The {@code OutputStream} is <b>not</b> automatically closed after completion of this method.
	 * 
	 * @param objs
	 * @param bos
	 * @param prettyPrint
	 * @throws IOException
	 */
	public static void exportROIsToGeoJson(Collection<? extends ROI> objs, OutputStream bos, boolean prettyPrint) throws IOException {
		byte[] out = GsonTools.getInstance(prettyPrint).toJson(new ArrayList<>(objs)).getBytes(Charset.forName("UTF-8"));
		bos.write(out);
	}
	
	/**
	 * Export the {@code objs} to the output {@code File} as serialized data (compressed or not, based on the file extension).
	 * <p>
	 * The {@code objs} collection must to be serializable.
	 * 
	 * @param objs
	 * @param outFile
	 * @throws IOException
	 */
	public static void exportROIsAsSerialized(Collection<? extends ROI> objs, File outFile) throws IOException {
		if (outFile.getPath().endsWith(".zip")) {
			try (var zos = new ZipOutputStream(new FileOutputStream(outFile))) {
				// Create entry
				ZipEntry entry = new ZipEntry(GeneralTools.getNameWithoutExtension(outFile) + ".qpdata");
				zos.putNextEntry(entry);
				
				try (ObjectOutputStream oos = new ObjectOutputStream(zos)) {
					exportROIsAsSerialized(objs, oos);
				}
			}
		} else {
			try (var bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
				exportROIsAsSerialized(objs, bos);
			}
		}
	}
	
	/**
	 * Export the {@code objs} to the {@code OutputStream} as serialized data.
	 * <p>
	 * The {@code objs} collection must to be serializable.
	 * <p>
	 * The {@code OutputStream} is <b>not</b> automatically closed after completion of this method.
	 * 
	 * @param objs
	 * @param bos
	 * @throws IOException
	 */
	public static void exportROIsAsSerialized(Collection<? extends ROI> objs, OutputStream bos) throws IOException {
		try (var oos = new ObjectOutputStream(bos)) {
			oos.writeObject(new ArrayList<>(objs));
		}
	}
	
	/**
	 * Extract the {@code PathObject}s from the given file.
	 * 
	 * @param file
	 * @return objs
	 * @throws ClassNotFoundException
	 * @throws OperationNotSupportedException
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public static List<PathObject> extractObjectsFromFile(File file) throws ClassNotFoundException, OperationNotSupportedException, FileNotFoundException, IOException {
		List<PathObject> objs = new ArrayList<>();

		try (var fis = new FileInputStream(file)) {
			if (file.getPath().endsWith(exts[0]))			// .geojson
					objs = PathObjectIO.importObjectsFromGeoJson(fis);
			else if (file.getPath().endsWith(exts[1]))		// .qpdata
					objs = PathObjectIO.importObjectsFromSerialized(fis);
			else if (file.getPath().endsWith(exts[2])) {	// .zip
				try (var zipFile = new ZipFile(file)) {
					objs = PathObjectIO.importObjectsFromZip(zipFile);
				}
			} else
				throw new OperationNotSupportedException("File not supported: " + file.getPath());			
		}
		
		return objs;
	}
	
	/**
	 * Create a collection of {@code PathObject}s from Java serialized input (via stream).
	 * 
	 * @param stream
	 * @return pathObjects
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	@SuppressWarnings("unchecked")
	public static List<PathObject> importObjectsFromSerialized(InputStream stream) throws IOException, ClassNotFoundException {
		try (ObjectInputStream ois = new ObjectInputStream(stream)) {
			return (List<PathObject>) ois.readObject();
		}
	}
	
	/**
	 * Create a collection of {@code PathObject}s from Java serialized input (via stream).
	 * 
	 * @param serializedData 
	 * @return pathObjects
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	@SuppressWarnings("unchecked")
	public static List<PathObject> importObjectsFromSerialized(byte[] serializedData) throws IOException, ClassNotFoundException {
		try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedData))) {
			return (List<PathObject>) ois.readObject();
		}
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
				if (entry.getName().endsWith(".qpdata"))
					list.addAll(importObjectsFromSerialized(input.readAllBytes()));
				else if (entry.getName().endsWith(".geojson"))
					list.addAll(importObjectsFromGeoJson(input.readAllBytes()));
			}
		}
		return list;
	}

	/**
	 * Create a collection of {@code ROI}s from GeoJson input (via stream).
	 * 
	 * @param stream
	 * @return ROIs
	 * @throws IOException
	 */
	public static List<ROI> importROIsFromGeoJson(InputStream stream) throws IOException {
		// Prepare template
		var type = new TypeToken<List<ROI>>() {}.getType();
		
		// Deserialize
		try (InputStreamReader isr = new InputStreamReader(stream); JsonReader reader = new JsonReader(isr)) {
			return GsonTools.getInstance().fromJson(reader, type);
		}
	}
	
	/**
	 * Create a collection of {@code ROI}s from Java serialized input (via stream).
	 * 
	 * @param stream
	 * @return ROIs
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	@SuppressWarnings("unchecked")
	public static List<ROI> importROIsFromSerialized(InputStream stream) throws IOException, ClassNotFoundException {
		try (ObjectInputStream ois = new ObjectInputStream(stream)) {
			return (List<ROI>) ois.readObject();
		}
	}
	
	/**
	 * Create a collection of {@code ROI}s from Java serialized input (via stream).
	 * 
	 * @param serializedData 
	 * @return ROIs
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	@SuppressWarnings("unchecked")
	public static List<ROI> importROIsFromSerialized(byte[] serializedData) throws IOException, ClassNotFoundException {
		try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedData))) {
			return (List<ROI>) ois.readObject();
		}
	}
	
	/**
	 * Create a collection of {@code ROI}s from a ZIP file.
	 * 
	 * @param zipFile
	 * @return ROIs
	 * @throws ZipException 
	 * @throws IOException 
	 * @throws ClassNotFoundException 
	 */
	public static List<ROI> importROIsFromZip(ZipFile zipFile) throws ZipException, IOException, ClassNotFoundException {
		List<ROI> list = new ArrayList<>();
		
		// In case we have more than one compressed file, iterate through each entry
		for (ZipEntry entry : Collections.list(zipFile.entries())) {
			try (final BufferedInputStream input = new BufferedInputStream(zipFile.getInputStream(entry))) {
				if (entry.getName().endsWith(".qpdata"))
					list.addAll(importROIsFromSerialized(input.readAllBytes()));
				else if (entry.getName().endsWith(".geojson"))
					list.addAll(importROIsFromSerialized(input.readAllBytes()));
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
