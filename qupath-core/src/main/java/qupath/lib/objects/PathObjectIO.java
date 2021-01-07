package qupath.lib.objects;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.reflect.TypeToken;

import qupath.lib.common.GeneralTools;
import qupath.lib.io.GsonTools;

/**
 * Class to export {@code PathObject}s as either serialized or GeoJSON format.
 * 
 * @author Melvin Gelbard
 */
public class PathObjectIO {
	
	/**
	 * Export the {@code objs} to the {@code BufferedOutputStream} in GeoJSON format (compressed or not).
	 * 
	 * @param objs
	 * @param bos
	 * @param onlyROI
	 * @param includeMeasurements
	 * @param prettyPrint
	 * @param compress
	 * @throws IOException
	 */
	public static void exportToGeoJSON(Collection<PathObject> objs, OutputStream bos, boolean onlyROI, boolean includeMeasurements, boolean prettyPrint, boolean compress) throws IOException {
		byte[] out;
		
		// If exclude measurements, make a copy of each detection without their measurements
		if (!includeMeasurements) {
			List<PathObject> objsTemp = new ArrayList<>();
			objs.forEach(e -> {
				if (!e.hasMeasurements() || !e.hasROI()) {
					objsTemp.add(e);					
					return;
				}
				var temp = PathObjectTools.transformObject(e, null, true);
				temp.getMeasurementList().clear();
				objsTemp.add(temp);
			});
			objs = objsTemp;
		}
		if (onlyROI)
			out = GsonTools.getInstance(prettyPrint).toJson(objs.parallelStream().map(e -> e.getROI()).collect(Collectors.toList())).getBytes(Charset.forName("UTF-8"));
		else
			out = GsonTools.getInstance(prettyPrint).toJson(objs).getBytes(Charset.forName("UTF-8"));
		
		if (compress) {
			var zos = new ZipOutputStream(bos);
//			ZipEntry entry = new ZipEntry(GeneralTools.getNameWithoutExtension(outFile) + ".json");
			ZipEntry entry = new ZipEntry("PathObjects.json");
			zos.putNextEntry(entry);
			zos.write(out);
			zos.closeEntry();
			zos.close();
		} else
			bos.write(out);
		bos.close();
	}
	
	/**
	 * Export the {@code objs} to the {@code outFile} in GeoJSON format (compressed or not).
	 * 
	 * @param objs
	 * @param outFile
	 * @param onlyROI
	 * @param includeMeasurements 
	 * @param prettyPrint
	 * @param compress 
	 * @throws IOException
	 */
	public static void exportToGeoJSON(Collection<PathObject> objs, File outFile, boolean onlyROI, boolean includeMeasurements, boolean prettyPrint, boolean compress) throws IOException {
		var bos = new BufferedOutputStream(new FileOutputStream(outFile));
		exportToGeoJSON(objs, bos, onlyROI, includeMeasurements, prettyPrint, compress);
	}
	
	
	/**
	 * Export the {@code objs} to the {@code BufferedOutputStream} as serialized data (compressed or not).
	 * 
	 * @param objs
	 * @param bos
	 * @param onlyROI
	 * @param includeMeasurements
	 * @param compressed
	 * @throws IOException
	 */
	public static void exportAsSerialized(Collection<PathObject> objs, OutputStream bos, boolean onlyROI, boolean includeMeasurements, boolean compressed) throws IOException {
		// If exclude measurements, make a copy of each detection without their measurements
		if (!includeMeasurements) {
			List<PathObject> objsTemp = new ArrayList<>();
			objs.forEach(e -> {
				var temp = PathObjectTools.transformObject(e, null, true);
				temp.getMeasurementList().clear();
				objsTemp.add(temp);
			});
			objs = objsTemp;
		}
		
		if (compressed) {
			GZIPOutputStream gzipOut = new GZIPOutputStream(bos);
			ObjectOutputStream objectOut = new ObjectOutputStream(gzipOut);
			
			if (onlyROI)
				objectOut.writeObject(objs.parallelStream().map(e -> e.getROI()).collect(Collectors.toList()));
			else
				objectOut.writeObject(objs);
			objectOut.close();
			gzipOut.close();
		} else {
			var oos = new ObjectOutputStream(bos);
			if (onlyROI)
				oos.writeObject(objs.parallelStream().map(e -> e.getROI()).collect(Collectors.toList()));
			else
				oos.writeObject(objs);
			oos.close();
		}
	}
	
	/**
	 * Export the {@code objs} to the {@code outFile} as serialized data (compressed or not).
	 * 
	 * @param objs
	 * @param outFile
	 * @param onlyROI
	 * @param includeMeasurements
	 * @param compressed
	 * @throws IOException
	 */
	public static void exportAsSerialized(Collection<PathObject> objs, File outFile, boolean onlyROI, boolean includeMeasurements, boolean compressed) throws IOException {
		var bos = new BufferedOutputStream(new FileOutputStream(outFile));
		exportAsSerialized(objs, bos, onlyROI, includeMeasurements, compressed);
	}

	/**
	 * Create a collection of {@code PathObject}s from GeoJson input (via stream).
	 * @param stream
	 * @return pathObjects
	 * @throws IOException 
	 */
	public static Collection<PathObject> importFromGeoJSON(InputStream stream) throws IOException {
		String json = GeneralTools.readInputStreamAsString(stream);
		return importFromGeoJSON(json);
	}
	
	
	/**
	 * Create a collection of {@code PathObject}s from GeoJson String input.
	 * @param json
	 * @return pathObjects
	 * @throws IOException
	 */
	public static Collection<PathObject> importFromGeoJSON(String json) throws IOException {
		// Prepare template
		var type = new TypeToken<List<PathObject>>() {}.getType();
		
		// Deserialize
		return GsonTools.getInstance().fromJson(json, type);
	}
	
	/**
	 * Create a collection of {@code PathObject}s from Java serialized input (via stream).
	 * @param stream
	 * @return pathObjects
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	@SuppressWarnings("unchecked")
	public static Collection<PathObject> importFromSerialized(InputStream stream) throws IOException, ClassNotFoundException {
		ObjectInputStream ois = new ObjectInputStream(stream);
		return (Collection<PathObject>) ois.readObject();
	}
	
	/**
	 * Create a collection of {@code PathObject}s from Java serialized input (via stream).
	 * @param serializedData 
	 * @return pathObjects
	 * @throws IOException
	 * @throws ClassNotFoundException 
	 */
	@SuppressWarnings("unchecked")
	public static Collection<PathObject> importFromSerialized(byte[] serializedData) throws IOException, ClassNotFoundException {
		ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(serializedData));
		return (Collection<PathObject>) ois.readObject();
	}
	
	
	
	
	/**
	 * Return whether the {@code PathObject} is an ellipse.
	 * @param ann
	 * @return isEllipse
	 */
	public static boolean isEllipse(PathObject ann) {
		return ann.getROI() != null && ann.getROI().getRoiName().equals("Ellipse");
	}
}
