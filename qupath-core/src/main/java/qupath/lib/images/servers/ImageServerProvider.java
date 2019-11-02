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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.servers.ImageServerBuilder.UriImageSupport;
import qupath.lib.regions.RegionRequest;

/**
 * Service provider for creating ImageServers from a given path - which may be a file path or URL.
 * <p>
 * This class is responsible for hunting through potential ImageServerBuilders, ranked by support level, to find the first that works.
 * 
 * @author Pete Bankhead
 *
 */
public class ImageServerProvider {
	
	final private static Logger logger = LoggerFactory.getLogger(ImageServerProvider.class);
	
	private static Map<Class<?>, Map<RegionRequest, ?>> cacheMap = new HashMap<>();
	
	@SuppressWarnings("rawtypes")
	private static ServiceLoader<ImageServerBuilder> serviceLoader = ServiceLoader.load(ImageServerBuilder.class);
	
	/**
	 * Set the cache to be used for image tiles of a specific type.
	 * @param <T>
	 * @param cache
	 * @param cls
	 */
	public static <T> void setCache(Map<RegionRequest, T> cache, final Class<T> cls) {
		cacheMap.put(cls, cache);
	}
	
	/**
	 * Get the cache in use for image tiles of a specific type.
	 * @param <T>
	 * @param cls
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> Map<RegionRequest, T> getCache(final Class<T> cls) {
		return (Map<RegionRequest, T>)cacheMap.get(cls);
	}
	
	/**
	 * Replace the default service loader with another.
	 * <p>
	 * This can be handy if the ServiceLoader should be using an alternative ClassLoader,
	 * e.g. to auto-discover ImageServerBuilders in alternative directories.
	 * 
	 * @param newLoader
	 */
	public static void setServiceLoader(@SuppressWarnings("rawtypes") final ServiceLoader<ImageServerBuilder> newLoader) {
		serviceLoader = newLoader;
	}

	/**
	 * Request all available {@link ImageServerBuilder ImageServerBuilders}.
	 * @return
	 */
	public static List<ImageServerBuilder<?>> getInstalledImageServerBuilders() {
		List<ImageServerBuilder<?>> builders = new ArrayList<>();
		synchronized (serviceLoader) {
			for (ImageServerBuilder<?> b : serviceLoader) {
				builders.add(b);
			}
		}
		return builders;
	}
	
	/**
	 * Request all available {@link ImageServerBuilder ImageServerBuilders} supporting a given image class.
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public static <T> List<ImageServerBuilder<T>> getInstalledImageServerBuilders(Class<T> imageClass) {
		List<ImageServerBuilder<T>> builders = new ArrayList<>();
		synchronized(serviceLoader) {
			for (ImageServerBuilder<?> b : serviceLoader) {
				if (imageClass.equals(b.getImageType()))
					builders.add((ImageServerBuilder<T>)b);
			}
		}
		return builders;
	}
	

	private static <T> List<UriImageSupport<T>> getServerBuilders(final Class<T> cls, final String path, String...args) throws IOException {
		URI uriTemp;
		try {
			if (path.startsWith("file:") || path.startsWith("http")) {
				uriTemp = new URI(path);
			} else {
				// Handle legacy file paths (optionally with Bio-Formats series names included)
				String delimiter = "::";
				int index = path.indexOf(delimiter);
				String seriesName = null;
				String filePath = path;
				if (index > 0 && index < path.length()-delimiter.length() && !new File(path).exists()) {
					seriesName = path.substring(index+delimiter.length());
					filePath = path.substring(0, index);
				}
				uriTemp = new File(filePath).toURI();
				if (seriesName != null) {
					uriTemp = new URI(uriTemp.getScheme(), uriTemp.getAuthority(), uriTemp.getPath(), "name="+seriesName, null);
				}
			}
		} catch (URISyntaxException e) {
			throw new IOException(e.getLocalizedMessage());
		}

		URI uri = uriTemp;

		Collection<String> requestedClassnames = new HashSet<>();
		String key = "--classname";
		if (args.length > 0) {
			int i = key.equals(args[0]) ? 1 : 0;
			while (i < args.length && !args[i].startsWith("-")) {
				requestedClassnames.add(args[i]);
				i++;
			}
			// Remove classname-related args
			args = Arrays.copyOfRange(args, i, args.length);
		}
		
		// Check which providers we can use
		List<UriImageSupport<T>> supports = new ArrayList<>();
		synchronized(serviceLoader) {
			for (ImageServerBuilder<?> provider : serviceLoader) {
				try {
					if (!cls.isAssignableFrom(provider.getImageType()))
						continue;
					UriImageSupport<T> support = (UriImageSupport<T>)provider.checkImageSupport(uri, args);
					if (support != null && support.getSupportLevel() > 0f)
						supports.add(support);
				} catch (Exception e) {
					logger.error("Error testing provider " + provider, e);
				}
			}
		}
		
		Comparator<UriImageSupport<T>> comparator = Collections.reverseOrder(new UriImageSupportComparator<>());
		supports.sort(comparator);
		return supports;
	}
	
	/**
	 * Get the preferred {@link UriImageSupport} for a specified image path.
	 * 
	 * @param <T>
	 * @param cls
	 * @param path
	 * @param args
	 * @return
	 * @throws IOException
	 */
	public static <T> UriImageSupport<T> getPreferredUriImageSupport(final Class<T> cls, final String path, String...args) throws IOException {
		List<UriImageSupport<T>> supports = getServerBuilders(cls, path, args);
		for (UriImageSupport<T> support : supports) {
			try (var server = support.getBuilders().get(0).build()) {
				return support;
			} catch (Exception e) {
				logger.warn("Unable to open {}", support);
			}
		}
		return supports.isEmpty() ? null : supports.get(0);
	}
	
	
	/**
	 * Attempt to create {@code ImageServer<T>} from the specified path and arguments.
	 * 
	 * @param path path to an image - typically a URI
	 * @param cls desired generic type for the ImageServer, e.g. BufferedImage.class
	 * @param args optional arguments, which may be used by some builders
	 * @return
	 * @throws IOException 
	 */
	public static <T> ImageServer<T> buildServer(final String path, final Class<T> cls, String... args) throws IOException {
		
		List<UriImageSupport<T>> supports = getServerBuilders(cls, path, args);

		Exception firstException = null;
		if (!supports.isEmpty()) {
			for (UriImageSupport<T> support :supports) {
				try {
					if (!support.getBuilders().isEmpty()) {
						var server = support.getBuilders().get(0).build();
						return checkServerSize(server);
					}
				} catch (Exception e) {
					if (firstException == null)
						firstException = e;
					logger.warn("Unable to build server: {}", e.getLocalizedMessage());
					logger.debug(e.getLocalizedMessage(), e);
				}
			}
		}
		
		if (supports.isEmpty()) {
			logger.error("Unable to build whole slide server - check your classpath for a suitable library (e.g. OpenSlide, BioFormats)\n\t");
			logger.error(System.getProperty("java.class.path"));
		}
		String message = firstException.getLocalizedMessage();
		if (!message.isBlank())
			message = " (" + message + ")";
		throw new IOException("Unable to build a whole slide server for " + path + message, firstException);
	}
	
	/**
	 * Check server is either pyramidal or of a manageable size.
	 * @param <T>
	 * @param serverNew
	 * @return
	 * @throws IOException
	 */
	static <T> ImageServer<T> checkServerSize(ImageServer<T> serverNew) throws IOException {
		if (serverNew.nResolutions() > 1)
			return serverNew;
		long nPixels = (long)serverNew.getWidth() * (long)serverNew.getHeight();
		long nBytes = nPixels * serverNew.nChannels() * serverNew.getMetadata().getPixelType().getBytesPerPixel();
		if (nPixels >= Integer.MAX_VALUE || nBytes > Runtime.getRuntime().maxMemory()*0.8)
			throw new IOException(
					String.format("Image is too large and not a pyramid (%d x %d, %d channel%s)!",
							serverNew.getWidth(), serverNew.getHeight(), serverNew.nChannels(), serverNew.nChannels() > 1 ? "s" : "")
					);
		return serverNew;
	}
	
	
	/**
	 * Create a comparator that aims to get the builder with the best (claimed) support, following by the one that can identify most images from the file.
	 *
	 * @param <T>
	 */
	private static class UriImageSupportComparator<T> implements Comparator<UriImageSupport<T>> {

		@Override
		public int compare(UriImageSupport<T> o1, UriImageSupport<T> o2) {
			int cmp = Float.compare(o1.getSupportLevel(), o2.getSupportLevel());
			if (cmp != 0)
				return cmp;
			cmp = Integer.compare(o1.getBuilders().size(), o2.getBuilders().size());
			if (cmp != 0)
				return cmp;
			return o1.getProviderClass().getName().compareTo(o2.getProviderClass().getName());
		}
		
	}
	
}
