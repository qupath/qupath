/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2026 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.images.servers.bioformats;

import java.util.function.Predicate;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.formats.ome.OMEPyramidStore;
import ome.xml.model.Shape;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageServerBuilder.DefaultImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.RoiTools;
import qupath.lib.roi.interfaces.ROI;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * QuPath ImageServer that uses the Bio-Formats library to read image data.
 * <p>
 * See <a href="https://www.openmicroscopy.org/bio-formats/">Bio-Formats homepage</a>.
 * <p>
 * See also <a href="https://bio-formats.readthedocs.io/en/stable/developers/matlab-dev.html#improving-reading-performance">Improving reading performance.</a>
 *
 * @author Pete Bankhead
 *
 */
public class BioFormatsImageServer extends AbstractTileableImageServer implements PathObjectReader {
	
	private static final Logger logger = LoggerFactory.getLogger(BioFormatsImageServer.class);

	/**
	 * A map linking an identifier (image name) to series number for 'full' images.
	 */
	private final Map<String, Series> imageMap = new LinkedHashMap<>();

	/**
	 * A map linking an identifier (image name) to series for additional images, e.g. thumbnails or macro images.
	 */
	private final Map<String, Series> associatedImageMap = new LinkedHashMap<>();

	/**
	 * The original URI requested for this server.
	 */
	private final URI uri;

	/**
	 * Arguments passed to constructor.
	 */
	private final String[] args;

	/**
	 * Parsed arguments.
	 */
	private final BioFormatsArgs bfArgs;

	/**
	 * Original metadata, populated when reading the file.
	 */
	private final ImageServerMetadata originalMetadata;

	/**
	 * File path if possible, or a URI otherwise.
	 */
	private final String filePathOrUrl;
	
	/**
	 * The series (image) that this server opens (there can be multiple series in a file).
	 */
	private final Series series;

	/**
	 * ColorModel to use with all BufferedImage requests.
	 */
	private final ColorModel colorModel;
	
	/**
	 * Pool of readers for use with this server.
	 */
	private final ReaderPool readerPool;
	
	/**
	 * Create a minimal BioFormatsImageServer that can be used to query server builders.
	 * This is checked to ensure it can return at least one pixel, to avoid returning a server that will fail later.
	 * @param uri the URI of the image to open
	 * @param options standard Bio-Formats options
	 * @param args optional arguments
	 * @return a server containing an image that can return pixels
	 * @throws IOException if the image cannot be read
	 */
	static BioFormatsImageServer checkSupport(URI uri, final BioFormatsServerOptions options, String...args) throws IOException {
		try {
			var server = new BioFormatsImageServer(uri, options, args);
			// Attempt to read one pixel from the image.
			// This is expected to throw an exception if it fails; without this check, Bio-Formats can appear to work
			// but then fail when trying to read the first tile.
			// The risk is that this will be slow for large, non-tiled images.
			if (!server.readerPool.checkCanRead())
				throw new IOException("Unable to read bytes from " + uri);
			return server;
		} catch (Throwable t) {
			throw ReaderUtils.convertToIOException(t);
		}
	}

	/**
	 * Create an ImageServer using the Bio-Formats library.
	 * <p>
	 * This requires an <i>absolute</i> URI, where an integer fragment can be used to define the series number.
	 *
	 * @param uri for the image that should be opened; this might include a sub-image as a query or fragment.
	 * @param args optional arguments
	 */
	public BioFormatsImageServer(final URI uri, String...args) throws FormatException, IOException, DependencyException, ServiceException, URISyntaxException {
		this(uri, BioFormatsServerOptions.getInstance(), args);
	}


    private BioFormatsImageServer(URI uri, final BioFormatsServerOptions options, String...args) throws FormatException, IOException, DependencyException, ServiceException, URISyntaxException {
		super();

		long startTime = System.currentTimeMillis();

		this.uri = cleanUri(uri);
		
		// Parse the arguments
		this.args = args.clone();
		this.bfArgs = BioFormatsArgs.parse(args);

		// Try to get a local file path, but accept something else (since Bio-Formats handles other URIs)
		this.filePathOrUrl = convertToFilePathOrUrl(uri);

		// Create a reader & extract the metadata
		this.readerPool = new ReaderPool(options, filePathOrUrl, bfArgs);

		// If we have more than one series, we need to select one and
		// construct maps of 'main' & 'associated' images
		List<Series> allSeries = readerPool.getAllSeries();
		if (allSeries.isEmpty())
			throw new IOException("No series found for " + filePathOrUrl);

		populateImageMaps(allSeries, imageMap, associatedImageMap);

		this.series = findSeriesToOpen(allSeries, bfArgs).orElseThrow(() -> new IOException("No series found"));
		this.originalMetadata = readerPool.parseMetadata(
				series,
				getImageName(series, getFile().getName(), imageMap.size() > 1),
				getPath());

		colorModel = createColorModel(originalMetadata);
		ensureImageIoCacheOff();
		long endTime = System.currentTimeMillis();
		logger.debug("Initialization time: {} ms", endTime - startTime);
	}


	private static Optional<Series> findSeriesToOpen(final List<Series> allSeries, final BioFormatsArgs bfArgs) throws IOException {
		// Use the series index from the args whenever possible
		int seriesIndex = bfArgs.series;
		if (seriesIndex >= 0) {
			var matched = allSeries.stream().filter(s -> seriesIndex == s.getSeries()).findFirst();
			if (matched.isPresent())
				return matched;
		}

		// Use the series name from the args, if present
		String requestedSeriesName = bfArgs.seriesName.isBlank() ? null : bfArgs.seriesName;
		if (requestedSeriesName != null) {
			var matchingSeries = findMatchingSeries(requestedSeriesName, allSeries);
			if (matchingSeries.isPresent()) {
				return matchingSeries;
			} else {
				logger.warn("No series found with name {}", requestedSeriesName);
			}
		}

		// Get the non-associated images
		var imageSeries = allSeries.stream().filter(Predicate.not(Series::isAssociatedImage)).toList();
		if (imageSeries.isEmpty())
			return Optional.empty();

		var firstSeries = imageSeries.getFirst();
		if (imageSeries.size() == 1)
			return Optional.of(firstSeries);

		// Take the first series unless it's substantially smaller than the others.
		// If it's a lot smaller, it's probably a thumbnail.
		// But if it's only a *little* smaller, we might simply have images of different sizes -
		// and the user will most likely expect we pick the first image and not some other that's far down the list.
		var maxPixels = imageSeries.stream().mapToLong(Series::totalPixelsXYZT).max().orElse(0L);
		if (firstSeries.totalPixelsXYZT() * 4 > maxPixels)
			return Optional.of(firstSeries);
		else
			return findLargestSeries(imageSeries);
	}


	private static void populateImageMaps(List<Series> allSeries, Map<String, Series> mainImages, Map<String, Series> associatedImages) {
		for (var series : allSeries) {
			if (series.isAssociatedImage()) {
				associatedImages.put(series.getUniqueSeriesName(), series);
			} else {
				mainImages.put(series.getUniqueSeriesName(), series);
			}
		}
	}

	private static Optional<Series> findLargestSeries(Collection<Series> allSeries) {
		long nPixels = -1;
		Series maxSeries = null;
		for (var series : allSeries) {
			long n = series.getResolutions().getFirst().totalPixelsXYZT();
			if (n > nPixels) {
				maxSeries = series;
				nPixels = n;
			}
		}
		return Optional.ofNullable(maxSeries);
	}

	private static Optional<Series> findMatchingSeries(String requestedSeriesName, Collection<Series> allSeries) {
		return allSeries.stream()
				.filter(series -> isRequestedSeries(requestedSeriesName, series))
				.findFirst();
	}

	private static boolean isRequestedSeries(String requestedSeriesName, Series series) {
		return requestedSeriesName.equals(series.getUniqueSeriesName()) ||
				requestedSeriesName.equals(series.getCleanSeriesName()) ||
				requestedSeriesName.contentEquals(series.getOriginalSeriesName());
	}


	/**
	 * Ensure the URI is valid for use with Bio-Formats, or throw an exception if invalid elements
	 * (e.g., query, fragment) are found.
	 * @param uri the input URI
	 * @return the clean URI, or original URI if no cleaning is required
     */
	private static URI cleanUri(URI uri) throws IOException, URISyntaxException {
		// Zarr images can be opened by selecting the .zattrs or .zgroup file
		// In that case, the parent directory contains the whole image
		if (uri.toString().endsWith(".zattrs") || uri.toString().endsWith(".zgroup")) {
			return new File(uri).getParentFile().toURI();
		}

		// See if there is a series name embedded in the path (temporarily the way things were done in v0.2.0-m1 and v0.2.0-m2)
		// Add it to the args if so
		if (uri.getFragment() != null) {
			logger.warn("Series as fragment is no longer supported (value={})", uri.getFragment());
		} else if (uri.getQuery() != null) {
			// Queries supported name=image-name or series=series-number... only one or the other!
			String query = uri.getQuery();
			String seriesQuery = "series=";
			String nameQuery = "name=";
			if (query.startsWith(seriesQuery)) {
				throw new IOException("Series as query no longer supported");
			} else if (query.startsWith(nameQuery)) {
				throw new IOException("Series name as query no longer supported");
			}
		}
		return new URI(uri.getScheme(), uri.getHost(), uri.getPath(), null);
	}




	/**
	 * Bio-Formats can use ImageIO for JPEG decoding, and permitting the disk-based cache can slow it down (a lot).
	 * Here we turn it off; unfortunately, it can only be done globally.
	 */
	private static void ensureImageIoCacheOff() {
		if (ImageIO.getUseCache()) {
			logger.debug("Setting ImageIO.setUseCache(false)");
			ImageIO.setUseCache(false);
		}
	}


	private static ColorModel createColorModel(final ImageServerMetadata metadata) {
		if (metadata.isRGB())
			return ColorModel.getRGBdefault();
		else
			return ColorModelFactory.createColorModel(metadata.getPixelType(), metadata.getChannels());
	}


	private static String getImageName(Series series, String baseName, boolean multiImage) {
		String shortName = series.getCleanSeriesName();
		if (shortName.isBlank()) {
			if (multiImage) {
				return baseName + " - Series " + series.getSeries();
			}
		} else if (!baseName.equals(shortName)) {
			return baseName + " - " + shortName;
		}
		return baseName;
	}


	/**
	 * Prefer a file path where possible, but Bio-Formats can support some URLs as a Location.
	 * @param uri the uri of the image to open
	 * @return a string representing the file path (if available), or URI otherwise
	 */
	private static String convertToFilePathOrUrl(URI uri) {
		try {
			var path = GeneralTools.toPath(uri);
			if (path != null) {
				// Use toRealPath to resolve any symbolic links
				return path.toRealPath().toString();
			}
		} catch (Exception e) {
			logger.error(e.getLocalizedMessage(), e);
		}
		logger.debug("Using URI as file path: {}", uri);
		return uri.toString();
	}

	/**
	 * Get the format String, as returned by Bio-Formats {@code IFormatReader.getFormat()}.
	 * @return
	 */
	public String getFormat() {
		return readerPool.getFormat();
	}
	
	@Override
	public Collection<URI> getURIs() {
		return Collections.singletonList(uri);
	}
	
	@Override
	public String createID() {
		return ServerTools.createDefaultID(getClass(), uri, args);
	}

	@Override
	public void setMetadata(ImageServerMetadata metadata) {
		var currentMetadata = getMetadata();
		super.setMetadata(metadata);
		if (currentMetadata != metadata && !currentMetadata.getLevels().equals(metadata.getLevels())) {
			logger.warn("Can't set metadata to use incompatible pyramid levels - reverting to original pyramid levels");
			super.setMetadata(
					new ImageServerMetadata.Builder(metadata)
					.levels(currentMetadata.getLevels())
					.build()
			);
		}
	}
	
	/**
	 * Returns a builder capable of creating a server like this one.
	 */
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return DefaultImageServerBuilder.createInstance(BioFormatsServerBuilder.class, getMetadata(), uri, args);
	}
	
	/**
	 * Get the series index, as used by Bio-Formats.
	 * @return
	 */
	public int getSeries() {
		return series.getSeries();
	}
	
	
	@Override
	public BufferedImage readTile(TileRequest tileRequest) throws IOException {
		try {
			return readerPool.openImage(tileRequest, series.getSeries(), nChannels(), isRGB(), colorModel);
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}
	
	
	@Override
	public String getServerType() {
		return "Bio-Formats";
	}
	
	@Override
	public synchronized void close() throws Exception {
		super.close();
		readerPool.close();
	}

	boolean containsSubImages() {
		return !imageMap.isEmpty();
	}

	/**
	 * Get the MetadataStore, as used by Bio-Formats.
	 * This can be used to query metadata values not otherwise accessible.
	 * <p>
	 * <b>Warning!</b> Because the returned object is used internally,
	 * it should not be modified by any external code.
	 * @return
	 */
	public OMEPyramidStore getMetadataStore() {
		return readerPool.getMetadata();
	}
	
	/**
	 * Retrieve a string representation of the metadata OME-XML.
	 * @return
	 */
	public String dumpMetadata() {
		try {
			return getMetadataStore().dumpXML();
		} catch (Exception e) {
			logger.error("Unable to dump metadata", e);
		}
		return null;
	}

	
	@Override
	public List<String> getAssociatedImageList() {
		if (associatedImageMap.isEmpty())
			return Collections.emptyList();
		return List.copyOf(associatedImageMap.keySet());
	}

	@Override
	public BufferedImage getAssociatedImage(String name) {
		var series = associatedImageMap.getOrDefault(name, null);
		if (series == null)
			throw new IllegalArgumentException("No associated image with name '" + name + "' for " + getPath());
		
		try {
			return readerPool.openSeries(series.getSeries());
		} catch (Exception e) {
            logger.error("Error reading associated image {}: {}", name, e.getMessage(), e);
			return null;
		}
	}
	
	
	/**
	 * Get the underlying file.
	 * @return
	 */
	public File getFile() {
		return filePathOrUrl == null ? null : new File(filePathOrUrl);
	}


	Map<String, ServerBuilder<BufferedImage>> getImageBuilders() {
		Map<String, ServerBuilder<BufferedImage>> builders = new LinkedHashMap<>();
		for (var entry : imageMap.entrySet()) {
			builders.put(entry.getKey(), createServerBuilder(entry.getValue()));
		}
		return Collections.unmodifiableMap(builders);
	}

	private ServerBuilder<BufferedImage> createServerBuilder(Series series) {
		return DefaultImageServerBuilder.createInstance(
				BioFormatsServerBuilder.class, null, uri,
				bfArgs.backToArgs(series.getSeries())
		);
	}


	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}

	@Override
	public Collection<PathObject> readPathObjects() {
		return readerPool.getROIs(series.getSeries()).stream()
				.map(BioFormatsImageServer::roiToObject)
				.toList();
	}

	private static PathObject roiToObject(ome.xml.model.ROI bioFormatsRoi) {
		logger.debug("Converting {} to QuPath path object", bioFormatsRoi);

		List<Shape> shapes = bioFormatsRoi.getUnion().copyShapeList().stream()
				.filter(Objects::nonNull)
				.toList();

		List<ROI> rois = shapes.stream()
				.map(BioFormatsShapeConverter::convertShapeToRoi)
				.flatMap(Optional::stream)
				.toList();

		ROI roi;
		if (rois.stream().allMatch(ROI::isPoint)) {
			logger.debug("Got point ROIs {} from {}. Combining them", rois, bioFormatsRoi);
			roi = ROIs.createPointsROI(
					rois.stream()
							.map(ROI::getAllPoints)
							.flatMap(List::stream)
							.distinct()
							.toList(),
					rois.isEmpty() ? ImagePlane.getDefaultPlane() : rois.getFirst().getImagePlane()
			);
		} else {
			logger.debug("Got non point ROIs {} from {}. Creating union from it", rois, bioFormatsRoi);
			roi = RoiTools.union(rois);
		}

		PathObject pathObject = PathObjects.createAnnotationObject(roi);

		try {
			pathObject.setID(UUID.fromString(bioFormatsRoi.getID()));
			logger.debug("ID {} of {} set to {}", bioFormatsRoi.getID(), bioFormatsRoi, pathObject);
		} catch (IllegalArgumentException | NullPointerException e) {
			logger.debug("ID {} of {} is not a valid UUID", bioFormatsRoi.getID(), bioFormatsRoi, e);
		}

		Optional<Boolean> locked = shapes.stream()
				.map(Shape::getLocked)
				.filter(Objects::nonNull)
				.findAny();
		if (locked.isPresent()) {
			pathObject.setLocked(locked.get());
			logger.debug("Locked status {} of {} set to {}", locked.get(), bioFormatsRoi, pathObject);
		} else {
			logger.debug("Locked status not found in {}", bioFormatsRoi);
		}

		pathObject.setName(bioFormatsRoi.getName());

		logger.debug("PathObject {} created from {}", pathObject, bioFormatsRoi);

		return pathObject;
	}

}
