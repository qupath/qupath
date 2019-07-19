package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.io.GsonTools;

/**
 * ImageServerBuilder that constructs an ImageServer from a JSON representation.
 * 
 * @author Pete Bankhead
 *
 */
public class JsonImageServerBuilder implements ImageServerBuilder<BufferedImage> {
	
	private static final Logger logger = LoggerFactory.getLogger(JsonImageServerBuilder.class);

	@Override
	public UriImageSupport<BufferedImage> checkImageSupport(URI uri, String...args) {
		float supportLevel = supportLevel(uri, args);
		return UriImageSupport.createInstance(JsonImageServerBuilder.class, supportLevel, DefaultImageServerBuilder.createInstance(this.getClass(), null, uri, args));
	}
	
	private float supportLevel(URI uri, String...args) {
		if (uri.toString().toLowerCase().endsWith(".json"))
			return 4;
		String scheme = uri.getScheme();
		if (scheme != null && scheme.startsWith("http")) {
			logger.debug("Reading JSON servers via HTTP requests currently not supported - will not check");
			return 0;
		}
		try {
			String type = Files.probeContentType(Paths.get(uri));
			if (type == null)
				return 0f;
			if (type.endsWith("/json"))
				return 4;
			if (type.equals("text/plain"))
				return 3;
			if (type.startsWith("image"))
				return 0;
			return 1;
		} catch (IOException e) {
			logger.trace("Error checking content type", e);
			return 0;
		}
	}

	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) throws Exception {
		try (Reader reader = new BufferedReader(new InputStreamReader(uri.toURL().openStream()))) {
			ServerBuilder<BufferedImage> builder = GsonTools.getInstance().fromJson(reader, ServerBuilder.class);
			return builder.build();
//			return GsonTools.getGsonDefault().fromJson(reader, new TypeToken<ImageServer<BufferedImage>>() {}.getType());
		}
	}

	@Override
	public String getName() {
		return "JSON server builder";
	}

	@Override
	public String getDescription() {
		return "A builder that constructs ImageServers from a JSON representation";
	}
	
	@Override
	public Class<BufferedImage> getImageType() {
		return BufferedImage.class;
	}

}
