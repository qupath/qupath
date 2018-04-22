package qupath.lib.images.stores;

import java.util.Map;

import qupath.lib.regions.RegionRequest;

/**
 * Simple interface defining a store for image regions.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public interface RegionCache<T> extends Map<RegionRequest, T> {

	T put(RegionRequest request, T img);

	boolean containsKey(RegionRequest request);

	T get(RegionRequest request);

}