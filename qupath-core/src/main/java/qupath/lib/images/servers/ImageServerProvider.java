/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.images.servers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.FileFormatInfo.ImageCheckType;
import qupath.lib.regions.RegionRequest;

/**
 * Service provider for creating ImageServers from a given path - which may be a file path or URL.
 * <p>
 * This class is responsible for hunting through potential ImageServerBuilders, ranked by support level, to find the first that works.
 * <p>
 * 
 * @author Pete Bankhead
 *
 */
public class ImageServerProvider {
	
	final private static Logger logger = LoggerFactory.getLogger(ImageServerProvider.class);
	
	private static Map<Class<?>, Map<RegionRequest, ?>> cacheMap = new HashMap<>();
	
	@SuppressWarnings("rawtypes")
	private static ServiceLoader<ImageServerBuilder> serviceLoader = ServiceLoader.load(ImageServerBuilder.class);
	
	public static <T> void setCache(Map<RegionRequest, T> cache, final Class<T> cls) {
		cacheMap.put(cls, cache);
	}
	
	public static <T> Map<RegionRequest, T> getCache(final Class<T> cls) {
		return (Map<RegionRequest, T>)cacheMap.get(cls);
	}
	
	/**
	 * Replace the default service loader with another.
	 * 
	 * This can be handy if the ServiceLoader should be using an alternative ClassLoader,
	 * e.g. to auto-discover ImageServerBuilders in alternative directories.
	 * 
	 * @param newLoader
	 */
	public static void setServiceLoader(final ServiceLoader<ImageServerBuilder> newLoader) {
		serviceLoader = newLoader;
	}

	
	public static List<ImageServerBuilder<?>> getInstalledImageServerBuilders() {
		List<ImageServerBuilder<?>> builders = new ArrayList<>();
		for (ImageServerBuilder<?> b : serviceLoader)
			builders.add(b);
		return builders;
	}
	
	
	/**
	 * Attempt to create {@code ImageServer<T>} from the specified path, which returns images of the specified class
	 * (e.g. BufferedImage).  The class needs to be passed to assist with ensuring the correct generic type of
	 * the returned {@code ImageServer<T>}.
	 * 
	 * @param path
	 * @param cls
	 * @return
	 */
	public static <T> ImageServer<T> buildServer(final String path, final Class<T> cls) {
		
//		if (path == null)
//			return null;
		
		List<ImageServerBuilder<?>> providers = new ArrayList<ImageServerBuilder<?>>();
		for (ImageServerBuilder<?> provider : serviceLoader) {
			providers.add(provider);
		}
		
		final ImageCheckType type = FileFormatInfo.checkImageType(path);
		Collections.sort(providers, new Comparator<ImageServerBuilder<?>>() {

			@Override
			public int compare(ImageServerBuilder<?> o1, ImageServerBuilder<?> o2) {
				return (int)Math.signum(o2.supportLevel(path, type, cls) - o1.supportLevel(path, type, cls));
			}
			
		});
		
		if (logger.isDebugEnabled()) {
			for (ImageServerBuilder<?> provider : providers)
				logger.debug("{}: rank {} ", provider, provider.supportLevel(path, type, cls));				
		}
		long maxImageSize = Runtime.getRuntime().maxMemory() / 2;
		for (ImageServerBuilder<?> provider : providers) {
			if (provider.supportLevel(path, type, cls) == 0) {
				logger.error("No image server provider found for {}", path);
				return null;
			}
			try {
				@SuppressWarnings("unchecked")
				Map<RegionRequest, T> cache = (Map<RegionRequest, T>)cacheMap.getOrDefault(cls, null);
				@SuppressWarnings("unchecked")
				ImageServerBuilder<T> possibleProvider = (ImageServerBuilder<T>)provider;
				ImageServer<T> server = possibleProvider.buildServer(path, cache);
				if (server != null) {
					// Check size is reasonable - should be small, or large & tiled
					if ((long)server.getWidth() * server.getHeight() * server.getBitsPerPixel() * server.nChannels() / 8 < maxImageSize || server.getPreferredDownsamples().length > 1) {
						logger.info("Returning server: {} for {}", server.getServerType(), path);
						return server;
					} else
						logger.warn("Cannot open {} with {} - image size too large ({} MB)", path, provider, server.getWidth() / (1024.0 * 1024.0 * 8.0) * server.getHeight() * server.getBitsPerPixel() * server.nChannels());
				}
			} catch (Exception e) {
				logger.warn("ImageServer creation failed", e);
			}
			logger.debug("Provider " + provider + " support level " + provider.supportLevel(path,  type, cls));
		}
		
		logger.error("Unable to build whole slide server - check your classpath for a suitable library (e.g. OpenSlide, BioFormats)\n\t");
		logger.error(System.getProperty("java.class.path"));
//		throw new IOException("Unable to build a whole slide server for " + path);
		return null;
	}
	
	
}
