/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.servers;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
		String lower = uri.toString().toLowerCase();
		if (lower.endsWith(".json"))
			return 4;
		String scheme = uri.getScheme();
		if (scheme != null && scheme.startsWith("http")) {
			logger.debug("Reading JSON servers via HTTP requests currently not supported - will not check");
			return 0;
		}
		try {
			var path = Paths.get(uri);
			String type = Files.probeContentType(path);
			if (type == null)
				return 0f;
			if (type.endsWith("/json"))
				return 4;
			else
				return 0;
			// TODO: We could try harder to find JSON content... but need to have a reason to
			//       and we don't want to inadvertently try to parse JSON that doesn't exist
//			if (type.equals("text/plain"))
//				return 3;
//			if (type.startsWith("image"))
//				return 0;
//			return 1;
		} catch (IOException e) {
			logger.trace("Error checking content type", e);
			return 0;
		}
	}

	@Override
	public ImageServer<BufferedImage> buildServer(URI uri, String...args) throws Exception {
		try (Reader reader = new BufferedReader(new InputStreamReader(uri.toURL().openStream(), StandardCharsets.UTF_8))) {
			var gson = GsonTools.getInstance();
			var element = gson.fromJson(reader, JsonElement.class);
			if (element.isJsonObject()) {
				var obj = element.getAsJsonObject();
				if (obj.has("builder") && !obj.has("builderType")) {
					// Since v0.5.0 we serialize the server
					return gson.fromJson(obj, ImageServer.class);
				} else {
					// Before v0.5.0 we serialized the builder
					if (!obj.has("builderType")) {
						String builderType = estimateToSetBuilderType(obj);
						if (builderType == null)
							logger.warn("Unknown builder type for JSON object: {}", obj);
						else {
							logger.debug("Adding estimated builderType property: {}", builderType);
							obj.addProperty("builderType", builderType);
						}
					}
					var builder = gson.fromJson(obj, ServerBuilder.class);
					return builder.build();
				}
			}
			// Unlikely to work... but also not expecting to reach this point
			logger.debug("Attempting to deserialize server, but JSON is not an object: {}", element);
			return gson.fromJson(element, ImageServer.class);
		}
	}

	/**
	 * Try to guess the builder type based on the JSON content.
	 * <p>
	 * This is used to help deserialize JSON written to 'server.json' files within projects before v0.5.0.
	 * Here, the builder type is not included and so deserialization fails due to Gson now knowing
	 * which deserializer to use based upon the type adapter factory.
	 *
	 * @param obj
	 * @return the estimated builder type, or null if none could be found
	 * @see ImageServers
	 * @since v0.5.0
	 */
	private static String estimateToSetBuilderType(JsonObject obj) {
		if (obj.has("builderType"))
			return obj.get("builderType").getAsString();
		if (hasFields(obj, "providerClassName", "uri", "args"))
			return "uri"; // Most common
		if (hasFields(obj, "builder", "rotation"))
			return "rotated";
		if (hasFields(obj, "builder", "channels"))
			return "channels";
		if (hasFields(obj, "regions", "path"))
			return "sparse";
		if (hasFields(obj, "builder", "order"))
			return "swapRedBlue";
		if (hasFields(obj, "builder", "transforms"))
			return "color";
		if (hasFields(obj, "builder", "transform"))
			return "affine";
		if (hasFields(obj, "builder", "region"))
			return "cropped";
		if (hasFields(obj, "builder", "stains", "channels"))
			return "color_deconvolved";
		if (hasFields(obj, "builder") && obj.asMap().size() == 1)
			return "pyramidize";
		// Although we don't generally support extensions in the core code, this tries to be
		// friendly to the warpy extension
		if (hasFields(obj, "builder", "realtransforminterpolation"))
			return "realtransform";
		if (hasFields(obj, "builder", "transforminterpolation"))
			return "transforminterpolate";
		return null;
	}

	/**
	 * Check whether a JSON object has all of the specified fields.
	 * @param obj
	 * @param names
	 * @return true if all of the fields are found, false otherwise
	 */
	private static boolean hasFields(JsonObject obj, String... names) {
		for (var name : names)
			if (!obj.has(name))
				return false;
		return true;
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