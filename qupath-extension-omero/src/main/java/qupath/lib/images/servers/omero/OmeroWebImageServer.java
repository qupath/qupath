/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.omero.OmeroShapes.OmeroShape;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectReader;

/**
 * ImageServer that reads pixels using the OMERO web API.
 * <p>
 * Note that this does not provide access to the raw data, but rather RGB tiles only in the manner of a web viewer. 
 * Consequently, only RGB images are supported and some small changes in pixel values can be expected due to compression.
 * 
 * @author Pete Bankhead
 *
 */
public class OmeroWebImageServer extends AbstractTileableImageServer implements PathObjectReader {

	private static final Logger logger = LoggerFactory.getLogger(OmeroWebImageServer.class);

	private final URI uri;
	private final String[] args;	
	private final String host;
	private final String scheme;

	private ImageServerMetadata originalMetadata;

	/**
	 * Image OMERO ID
	 */
	private String id;
	
	/**
	 * Client used to open this image.
	 */
	private final OmeroWebClient client;

	/**
	 * Default JPEG quality if none is specified in the args
	 */
	private static double DEFAULT_JPEG_QUALITY = 0.9;
	
	/**
	 * Quality of requested JPEG.
	 */
	private double quality = DEFAULT_JPEG_QUALITY;

//	/**
//	 * There appears to be a max size (hard-coded?) in OMERO, so we need to make sure we don't exceed that.
//	 * Requesting anything larger just returns a truncated image.
//	 */
//	private static int OMERO_MAX_SIZE = 1024;

	/**
	 * Instantiate an OMERO server.
	 * 
	 * Note that there are five URI options currently supported:
	 * <ul>
	 * 	<li> Copy and paste from web viewer ("{@code /host/webclient/img_detail/id/}")</li>
	 *  <li> Copy and paste from the 'Link' button ("{@code /host/webclient/?show=id}")</li>
	 *  <li> Copy and paste from the old viewer ("{@code /host/webgateway/img_detail/id}")</li>
	 *  <li> Copy and paste from the new viewer ("{@code /host/iviewer/?images=id}")</li>
	 *  <li> Id provided as only fragment after host</li>
	 * </ul>
	 * The fifth option could be removed.
	 * 
	 * @param uri
	 * @param client
	 * @param args
	 * @throws IOException
	 */
	OmeroWebImageServer(URI uri, OmeroWebClient client, String...args) throws IOException {
		super();
		this.uri = uri;
		this.scheme = uri.getScheme();
		this.host = uri.getHost();
		this.client = client;
		this.originalMetadata = buildMetadata();
		// Args are stored in the JSON - passwords and usernames must not be included!
		// Do an extra check to ensure someone hasn't accidentally passed one
		var invalid = Arrays.asList("--password", "-p", "-u", "--username", "-password");
		for (int i = 0; i < args.length; i++) {
			String arg = args[i].toLowerCase().strip();
			if (invalid.contains(arg)) {
				throw new IllegalArgumentException("Cannot build server with arg " + arg);
			}
			if (arg.equals("--quality") || arg.equals("-q")) {
				if (i < args.length-1) {
					try {
						var parsedQuality = Double.parseDouble(args[i+1]);
						if (parsedQuality > 0 && parsedQuality <= 1) {
							quality = parsedQuality;
						} else
							logger.error("Requested JPEG quality '{}' is invalid, must be between 0 and 1. I will use {} instead.", parsedQuality, quality);
					} catch (NumberFormatException ex) {
						logger.error("Unable to parse JPEG quality from {}", args[i+1]);
					}
				}
			}
		}
		this.args = args;
		
		// Add URI to the client's list of URIs
		client.addURI(uri);
	}
	
	protected ImageServerMetadata buildMetadata() throws IOException {
		String uriQuery = uri.getQuery();
		if (uriQuery != null && !uriQuery.isEmpty() && uriQuery.startsWith("show=image-")) {
			Pattern pattern = Pattern.compile("show=image-(\\d+)");
			Matcher matcher = pattern.matcher(uriQuery);
			if (matcher.find())
				this.id = matcher.group(1);
		}
		if (this.id == null)
			this.id = uri.getFragment();

		int sizeX;
		int sizeY;

		String imageName = null;
		int sizeT = 1;
		int sizeZ = 1;
		int sizeC = 3;
		int[] tileSize = null;//{256, 256};
		double pixelWidthMicrons = Double.NaN;
		double pixelHeightMicrons = Double.NaN;
		double zSpacingMicrons = Double.NaN;
		PixelType pixelType = PixelType.UINT8;
		boolean isRGB = true;
		double magnification = Double.NaN;
		
		JsonObject map = OmeroRequests.requestMetadata(scheme, host, Integer.parseInt(id));
		JsonObject size = map.getAsJsonObject("size");

		sizeX = size.getAsJsonPrimitive("width").getAsInt();
		sizeY = size.getAsJsonPrimitive("height").getAsInt();
		sizeC = size.getAsJsonPrimitive("c").getAsInt();
		sizeZ = size.getAsJsonPrimitive("z").getAsInt();
		sizeT = size.getAsJsonPrimitive("t").getAsInt();
		
		

		JsonElement pixelSizeElement = map.get("pixel_size");
		if (pixelSizeElement != null) {
			JsonObject pixelSize = pixelSizeElement.getAsJsonObject();
			// TODO: Check micron assumption

			if (pixelSize.has("x") && !pixelSize.get("x").isJsonNull())
				pixelWidthMicrons = pixelSize.getAsJsonPrimitive("x").getAsDouble();
			if (pixelSize.has("y") && !pixelSize.get("y").isJsonNull())
				pixelHeightMicrons = pixelSize.getAsJsonPrimitive("y").getAsDouble();
			if (pixelSize.has("z")) {
				JsonElement zSpacing = pixelSize.get("z");
				if (!zSpacing.isJsonNull())
					zSpacingMicrons = zSpacing.getAsDouble();
			}
		}
		
		String pixelsType = null;

		if (map.has("meta")) {
			JsonObject meta = map.getAsJsonObject("meta");
			if (meta.has("imageName"))
				imageName = meta.get("imageName").getAsString();
			if (meta.has("pixelsType"))
				pixelsType = meta.get("pixelsType").getAsString();
		}
		
		
		List<ImageChannel> channels = null;
		if (sizeC == 3)
			channels = ImageChannel.getDefaultRGBChannels();
//		else if (sizeC == 1)
//			channels = ImageChannel.getDefaultChannelList(1);
		
		if (channels == null || (pixelsType != null && !"uint8".equals(pixelsType)))
			throw new IOException("Only 8-bit RGB images supported! Selected image has " + sizeC + " channel(s) & pixel type " + pixelsType);
			
		var levelBuilder = new ImageServerMetadata.ImageResolutionLevel.Builder(sizeX, sizeY);
		
		if (map.getAsJsonPrimitive("tiles").getAsBoolean()) {
			int levels = map.getAsJsonPrimitive("levels").getAsInt();
			if (levels > 1) {
				JsonObject zoom = map.getAsJsonObject("zoomLevelScaling");
				for (int i = 0; i < levels; i++) {
					levelBuilder.addLevelByDownsample(1.0 / zoom.getAsJsonPrimitive(Integer.toString(i)).getAsDouble());
				}
			} else {
				levelBuilder.addFullResolutionLevel();
			}

			if (map.has("tile_size")) {
				JsonObject tileSizeJson = map.getAsJsonObject("tile_size");
				tileSize = new int[]{
						(int)tileSizeJson.getAsJsonPrimitive("width").getAsDouble(),
						(int)tileSizeJson.getAsJsonPrimitive("height").getAsDouble()
				};
			} else {
				tileSize = new int[] {sizeX, sizeY};
			}
		} else {
			int tileSizeX = Math.min(sizeX, 3192);
			int tileSizeY = Math.min(sizeY, 3192);
			tileSize = new int[] {tileSizeX, tileSizeY};
		}

		if (map.has("nominalMagnification"))
			magnification = map.getAsJsonPrimitive("nominalMagnification").getAsDouble();
		
		ImageServerMetadata.Builder builder = new ImageServerMetadata.Builder(getClass(), uri.toString(), sizeX, sizeY)
				.sizeT(sizeT)
				.channels(ImageChannel.getDefaultRGBChannels())
				.sizeZ(sizeZ)
//				.args(args)
				.name(imageName)
				.pixelType(pixelType)
				.rgb(isRGB)
				.magnification(magnification)
				.levels(levelBuilder.build());
		
		if (Double.isFinite(pixelWidthMicrons + pixelHeightMicrons))
			builder.pixelSizeMicrons(pixelWidthMicrons, pixelHeightMicrons);
		
		if (Double.isFinite(zSpacingMicrons) && zSpacingMicrons > 0)
			builder.zSpacingMicrons(zSpacingMicrons);

		if (tileSize.length >= 2) {
			builder.preferredTileSize(tileSize[0], tileSize[1]);
		}

		return builder.build();
	}

	@Override
	protected String createID() {
		return getClass().getName() + ": " + uri.toString() + " quality=" + quality;
	}

	@Override
	public Collection<URI> getURIs() {
		return Collections.singletonList(uri);
	}

	
	/**
	 * Retrieve any ROIs stored with this image as annotation objects.
	 * <p>
	 * Warning: This method is subject to change in the future.
	 * 
	 * @return
	 * @throws IOException
	 */
	@Override
	public Collection<PathObject> readPathObjects() throws IOException {

		//		URL urlROIs = new URL(
		//				scheme, host, -1, "/webgateway/get_rois_json/" + id
		//				);

		// Options are: Rectangle, Ellipse, Point, Line, Polyline, Polygon and Label
		var data = OmeroRequests.requestROIs(scheme, host, id);
		List<PathObject> list = new ArrayList<>();
		var gson = new GsonBuilder().registerTypeAdapter(OmeroShape.class, new OmeroShapes.GsonShapeDeserializer()).setLenient().create();
			
		for (int i = 0; i < data.size(); i++) {
			JsonObject roiJson = data.get(i).getAsJsonObject();
			JsonArray shapesJson = roiJson.getAsJsonArray("shapes");
			
			for (int j = 0; j < shapesJson.size(); j++) {
				try {
					var shape = gson.fromJson(shapesJson.get(j), OmeroShape.class);
					if (shape != null)
						list.add(shape.createAnnotation());
				} catch (Exception e) {
					logger.error("Error parsing shape: " + e.getLocalizedMessage(), e);
				}
			}
		}
		return list;
	}	
	
	@Override
	public String getServerType() {
		return "OMERO web server";
	}

	@Override
	public ImageServerMetadata getOriginalMetadata() {
		return originalMetadata;
	}

	@Override
	protected BufferedImage readTile(TileRequest request) throws IOException {

		int level = request.getLevel();

		int targetWidth = request.getTileWidth();
		int targetHeight = request.getTileHeight();

		String urlFile;

		if (nResolutions() > 1) {
			int x = request.getTileX() / getPreferredTileWidth();
			int y = request.getTileY() / getPreferredTileHeight();

			// Note!  It's important to use the preferred tile size so that the correct x & y can be used
			//			int width = request.getTileWidth();
			//			int height = request.getTileHeight();
			int width = getPreferredTileWidth();
			int height = getPreferredTileHeight();

			// It's crucial not to request tiles that are too large, but the AbstractTileableImageServer should deal with this
//			// Incorporate max size OMERO supports
//			if (targetWidth > OMERO_MAX_SIZE || targetHeight > OMERO_MAX_SIZE) {
//				BufferedImage img = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
//				Graphics2D g2d = img.createGraphics();
//				int requestSize = (int)Math.round(OMERO_MAX_SIZE * request.getRegionRequest().getDownsample());
//				for (int yy = 0; yy < request.getImageHeight(); yy += requestSize) {
//					for (int xx = 0; xx < request.getImageWidth(); xx += requestSize) {
//						RegionRequest requestTile = RegionRequest.createInstance(
//								request.getRegionRequest().getPath(), request.getRegionRequest().getDownsample(), 
//								request.getImageX() + xx, request.getImageY() + yy, requestSize, requestSize, request.getZ(), request.getT());
//						BufferedImage imgTile = readTile(
//								new TileRequest(requestTile, level, OMERO_MAX_SIZE, OMERO_MAX_SIZE));
//						g2d.drawImage(imgTile, (int)((xx / requestSize) * OMERO_MAX_SIZE), (int)((yy / requestSize) * OMERO_MAX_SIZE), null);
//					}
//
//				}
//				g2d.dispose();
//				return img;
//			}		

			urlFile = "/webgateway/render_image_region/" + id + 
					"/" + request.getZ() + 
					"/" + request.getT() +
					"/?tile=" + level + "," + x + "," + y + "," + width + "," + height +
					"&c=1|0:255$FF0000,2|0:255$00FF00,3|0:255$0000FF" +
					"&maps=[{%22inverted%22:{%22enabled%22:false}},{%22inverted%22:{%22enabled%22:false}},{%22inverted%22:{%22enabled%22:false}}]" +
					"&m=c&p=normal&q=" + quality;

			URL url = new URL(scheme, host, urlFile);

			BufferedImage img = ImageIO.read(url);

			return img;

		} else {
			int x = request.getTileX();
			int y = request.getTileY();
			//			int width = request.getTileWidth();
			//			int height = request.getTileHeight();
			int width = getPreferredTileWidth();
			int height = getPreferredTileHeight();

			urlFile = "/webgateway/render_image_region/" + id + 
					"/" + request.getZ() + 
					"/" + request.getT() +
					"/?region=" + x + "," + y + "," + width + "," + height +
					"&c=1|0:255$FF0000,2|0:255$00FF00,3|0:255$0000FF" +
					"&maps=[{%22inverted%22:{%22enabled%22:false}},{%22inverted%22:{%22enabled%22:false}},{%22inverted%22:{%22enabled%22:false}}]" +
					"&m=c&p=normal&q=" + quality;			
		}


		URL url = new URL(scheme, host, urlFile);

		BufferedImage img = ImageIO.read(url);

		return BufferedImageTools.resize(img, targetWidth, targetHeight, allowSmoothInterpolation());
	}
	
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return ImageServerBuilder.DefaultImageServerBuilder.createInstance(
				OmeroWebImageServerBuilder.class,
				getMetadata(),
				uri,
				args);
	}
	
	/**
	 * Return the preferred tile width of this {@code ImageServer}.
	 * @return preferredTileWidth
	 */
	public int getPreferredTileWidth() {
		return getMetadata().getPreferredTileWidth();
	}

	/**
	 * Return the preferred tile height of this {@code ImageServer}.
	 * @return preferredTileHeight
	 */
	public int getPreferredTileHeight() {
		return getMetadata().getPreferredTileHeight();
	}

	
	/**
	 * Return the web client used for this image server.
	 * @return client
	 */
	public OmeroWebClient getWebclient() {
		return client;
	}
	
	/**
	 * Return the OMERO ID of the image
	 * @return id
	 */
	public String getId() {
		return id;
	}
	
	/**
	 * Return the URI host used by this image server
	 * @return host
	 */
	public String getHost() {
		return host;
	}
	
	/**
	 * Return the URI scheme used by this image server
	 * @return scheme
	 */
	public String getScheme() {
		return scheme;
	}

	@Override
	public int hashCode() {
		return Objects.hash(host, client);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this)
            return true;
		
		if (!(obj instanceof OmeroWebImageServer))
			return false;
		
		return host.equals(((OmeroWebImageServer)obj).getHost()) &&
				client.getUsername().equals(((OmeroWebImageServer)obj).getWebclient().getUsername());
	}
}