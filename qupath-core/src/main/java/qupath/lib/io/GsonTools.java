package qupath.lib.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import qupath.lib.roi.interfaces.ROI;

/**
 * Helper class providing Gson instances with type adapters registered to serialize 
 * several key classes.
 * <p>
 * These include:
 * <ul>
 * <li>{@link ROI}</li>
 * </ul>
 * 
 * @author Pete Bankhead
 *
 */
public class GsonTools {
	
	private static Gson gson = new GsonBuilder()
			.registerTypeHierarchyAdapter(ROI.class, new ROITypeAdapter())
			.serializeSpecialFloatingPointValues()
			.setLenient()
			.create();
	
	/**
	 * Get default Gson, capable of serializing/deserializing some key QuPath classes.
	 * @return
	 * 
	 * @see #getGsonPretty()
	 */
	public static Gson getGsonDefault() {
		return gson;
	}
	
	/**
	 * Get default Gson with pretty printing enabled.
	 * @return
	 * 
	 * @see #getGsonDefault()
	 */
	public static Gson getGsonPretty() {
		return getGsonDefault().newBuilder().setPrettyPrinting().create();
	}
	
}
