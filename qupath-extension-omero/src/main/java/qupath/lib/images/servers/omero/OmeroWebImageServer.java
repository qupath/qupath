package qupath.lib.images.servers.omero;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.geom.Point2;
import qupath.lib.images.servers.AbstractTileableImageServer;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.images.servers.ImageServerBuilder.ServerBuilder;
import qupath.lib.images.servers.omero.OmeroWebImageServerBuilder.OmeroWebClient;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

/**
 * ImageServer that reads pixels using the OMERO web API.
 * <p>
 * Note that this does not provide access to the raw data, but rather RGB tiles only in the manner of a web viewer. 
 * Consequently, only RGB images are supported and some small changes in pixel values can be expected due to compression.
 * 
 * @author Pete Bankhead
 *
 */
public class OmeroWebImageServer extends AbstractTileableImageServer {

	private static final Logger logger = LoggerFactory.getLogger(OmeroWebImageServer.class);

	private ImageServerMetadata originalMetadata;
	private URI uri;
	private String[] args;

	/**
	 * Image ID
	 */
	private String id;
	
	private final String host;
	private final String scheme;

	/**
	 * Quality of requested JPEG.
	 */
	private static double QUALITY = 0.9;

	/**
	 * There appears to be a max size (hard-coded?) in OMERO, so we need to make sure we don't exceed that.
	 * Requesting anything larger just returns a truncated image.
	 */
//	private static int OMERO_MAX_SIZE = 1024;

	OmeroWebImageServer(URI uri, OmeroWebClient client, String...args) throws IOException {
		super();
		
		this.uri = uri;
		

		this.scheme = uri.getScheme();
		this.host = uri.getHost();

		/*
		 * Try to parse the ID.
		 * Two options are currently supported:
		 *  - Copy and paste from web viewer ("/host/webclient/img_detail/id/")
		 *  - Id provided as only fragment after host
		 * The second option could be removed.
		 */
		String uriPath = uri.getPath();
		if (uriPath != null && uriPath.startsWith("/webclient/img_detail")) {
			Pattern pattern = Pattern.compile("webclient/img_detail/(\\d+)");
			Matcher matcher = pattern.matcher(uriPath);
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
		int[] tileSize = {256, 256};
		double pixelWidthMicrons = Double.NaN;
		double pixelHeightMicrons = Double.NaN;
		double zSpacingMicrons = Double.NaN;
		PixelType pixelType = PixelType.UINT8;
		boolean isRGB = true;
		double magnification = Double.NaN;


		URL urlMetadata = new URL(
				scheme, host, -1, "/webgateway/imgData/" + id
				);

		InputStreamReader reader = new InputStreamReader(urlMetadata.openStream());
		JsonObject map = new Gson().fromJson(reader, JsonObject.class);
		reader.close();

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
		
		if (sizeC != 3 || pixelsType != null && !"uint8".equals(pixelsType))
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
		}

		if (map.has("nominalMagnification"))
			magnification = map.getAsJsonPrimitive("nominalMagnification").getAsDouble();
		
		this.args = args;
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

		if (tileSize != null && tileSize.length >= 2) {
			builder.preferredTileSize(tileSize[0], tileSize[1]);
		}

		originalMetadata = builder.build();
	}
	
	@Override
	protected String createID() {
		return getClass().getName() + ": " + uri.toString();
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
	public Collection<PathObject> getROIs() throws IOException {

		//		URL urlROIs = new URL(
		//				scheme, host, -1, "/webgateway/get_rois_json/" + id
		//				);

		// Options are: Rectangle, Ellipse, Point, Line, Polyline, Polygon and Label
		URL urlROIs = new URL(
				scheme, host, -1, "/api/v0/m/rois/?image=" + id
				);

		List<PathObject> list = new ArrayList<>();
		try (InputStreamReader reader = new InputStreamReader(urlROIs.openStream())) {
			JsonArray roisJson = new Gson().fromJson(reader, JsonObject.class).getAsJsonObject().getAsJsonArray("data");
			for (int i = 0; i < roisJson.size(); i++) {
				JsonObject roiJson = roisJson.get(i).getAsJsonObject();
				JsonArray shapesJson = roiJson.getAsJsonArray("shapes");

				for (int j = 0; j < shapesJson.size(); j++) {
					JsonObject shapeJson = shapesJson.get(j).getAsJsonObject();

					int z = 0;
					int t = 0;

					List<Point2> points = null;
					ROI roi = null;
					String text = null;
					Integer color = null;

					double x = 0, y = 0, x2 = 0, y2 = 0, width = 0, height = 0;
					if (shapeJson.has("Points")) {
						points = new ArrayList<>();
						for (String p : shapeJson.get("Points").getAsString().split(" ")) {
							String[] p2 = p.split(",");
							points.add(new Point2(Double.parseDouble(p2[0]), Double.parseDouble(p2[1])));
						}
					}
					if (shapeJson.has("x"))
						x = shapeJson.get("x").getAsDouble();
					if (shapeJson.has("y"))
						y = shapeJson.get("y").getAsDouble();
					if (shapeJson.has("x2"))
						x2 = shapeJson.get("x2").getAsDouble();
					if (shapeJson.has("y2"))
						y2 = shapeJson.get("y2").getAsDouble();
					if (shapeJson.has("width"))
						width = shapeJson.get("width").getAsDouble();
					if (shapeJson.has("height"))
						height = shapeJson.get("height").getAsDouble();

					if (shapeJson.has("TheT"))
						t = shapeJson.get("TheT").getAsInt();
					if (shapeJson.has("TheZ"))
						z = shapeJson.get("TheZ").getAsInt();
					if (shapeJson.has("Text"))
						text = shapeJson.get("Text").getAsString();
					if (shapeJson.has("StrokeColor")) {
						color = shapeJson.get("StrokeColor").getAsInt();
					}

					ImagePlane plane = ImagePlane.getPlane(z, t);

					String type = shapeJson.get("@type").getAsString();
					if (type.toLowerCase().endsWith("#line")) {
						logger.debug("FOUND A LINE: " + id);
						ROIs.createLineROI(x, y, x2, y2, plane);
					} else if (type.toLowerCase().endsWith("#rectangle")) {
						logger.debug("FOUND A RECTANGLE: " + id);
						roi = ROIs.createRectangleROI(x, y, width, height, plane);
					} else if (type.toLowerCase().endsWith("#ellipse")) {
						logger.debug("FOUND A ELLIPSE: " + id);
						roi = ROIs.createEllipseROI(x, y, width, height, plane);
					} else if (type.toLowerCase().endsWith("#point")) {
						logger.debug("FOUND A POINT: " + id);
						roi = ROIs.createPointsROI(points, plane);
					} else if (type.toLowerCase().endsWith("#polyline")) {
						logger.debug("FOUND A POLYLINE: " + id);
						roi = ROIs.createPolylineROI(points, plane);
					} else if (type.toLowerCase().endsWith("#polygon")) {
						logger.debug("FOUND A POLYGON: " + id);
						roi = ROIs.createPolygonROI(points, plane);
					} else if (type.toLowerCase().endsWith("#label")) {
						logger.warn("I found a label and I don't know what to do with it: " + id);
					}
					if (roi != null) {
						PathObject annotation = PathObjects.createAnnotationObject(roi);
						if (text != null)
							annotation.setName(text);
						if (color != null)
							annotation.setColorRGB(color);
						list.add(annotation);
					}
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
	
	int getPreferredTileWidth() {
		return getMetadata().getPreferredTileWidth();
	}

	int getPreferredTileHeight() {
		return getMetadata().getPreferredTileHeight();
	}

	@Override
	protected BufferedImage readTile(TileRequest request) throws IOException {

		int level = request.getLevel();

		int targetWidth = request.getTileWidth();
		int targetHeight = request.getTileHeight();

		String urlFile;

		if (nResolutions() > 1) {
			int x = (int)(request.getTileX() / getPreferredTileWidth());
			int y = (int)(request.getTileY() / getPreferredTileHeight());

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
					"&m=c&p=normal&q=" + QUALITY;

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
					"&m=c&p=normal&q=" + QUALITY;			
		}


		URL url = new URL(scheme, host, urlFile);

		BufferedImage img = ImageIO.read(url);

		return BufferedImageTools.resize(img, targetWidth, targetHeight);
	}
	
	
	@Override
	protected ServerBuilder<BufferedImage> createServerBuilder() {
		return ImageServerBuilder.DefaultImageServerBuilder.createInstance(
				OmeroWebImageServerBuilder.class,
				getMetadata(),
				uri,
				args);
	}

}
