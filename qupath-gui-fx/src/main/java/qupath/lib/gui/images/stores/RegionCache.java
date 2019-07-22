package qupath.lib.gui.images.stores;

import java.util.Map;

import qupath.lib.regions.RegionRequest;

/**
 * Simple interface defining a store for image regions.
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
interface RegionCache<T> extends Map<RegionRequest, T> {

	@Override
	T put(RegionRequest request, T img);

	boolean containsKey(RegionRequest request);

	T get(RegionRequest request);

}