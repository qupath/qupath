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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.SerializedName;

import qupath.lib.images.servers.omero.OmeroObjects.Experimenter;
import qupath.lib.images.servers.omero.OmeroObjects.Link;
import qupath.lib.images.servers.omero.OmeroObjects.Owner;
import qupath.lib.images.servers.omero.OmeroObjects.Permission;

/**
 * Class representing OMERO annotations.
 * <p>
 * OMERO annotations are <b>not</b> similar to QuPath annotations. Rather, they 
 * represent some type of metadata visible on the right pane in the OMERO Webclient.
 * 
 * Note: Tables annotations are ignored.
 * 
 * @author Melvin Gelbard
 */
// TODO: Handle Table annotations
final class OmeroAnnotations {
	
	private final static Logger logger = LoggerFactory.getLogger(OmeroAnnotations.class);
	
	public static enum OmeroAnnotationType {
		TAG("TagAnnotationI", "tag"), 
		MAP("MapAnnotationI", "map"), 
		ATTACHMENT("FileAnnotationI", "file"), 
		COMMENT("CommentAnnotationI", "comment"), 
		RATING("LongAnnotationI", "rating"),
		UNKNOWN("Unknown", "unknown");
		
		private final String name;
		private final String urlName;
		private OmeroAnnotationType(String name, String urlName) {
			this.name = name;
			this.urlName = urlName;
		}
		
		public static OmeroAnnotationType fromString(String text) {
	        for (var type : OmeroAnnotationType.values()) {
	            if (type.name.equalsIgnoreCase(text) || type.urlName.equalsIgnoreCase(text))
	                return type;
	        }
	        return UNKNOWN;
	    }
		
		public String toURLString() {
			return urlName;
		}
		
		@Override
		public String toString() {
			return name;
		}
	}
	
	
	@SerializedName(value = "annotations")
	private final List<OmeroAnnotation> annotations;

	@SerializedName(value = "experimenters")
	private final List<Experimenter> experimenters;
	
	private final OmeroAnnotationType type;
	
	protected OmeroAnnotations(List<OmeroAnnotation> annotations, List<Experimenter> experimenters, OmeroAnnotationType type) {
		this.annotations = Objects.requireNonNull(annotations);
		this.experimenters = Objects.requireNonNull(experimenters);
		this.type = type;
	}
	
	/**
	 * Static factory method to get all annotations & experimenters in a single {@code OmeroAnnotations} object.
	 * @param json
	 * @return OmeroAnnotations
	 * @throws IOException 
	 */
	public static OmeroAnnotations getOmeroAnnotations(JsonObject json) throws IOException {
		List<OmeroAnnotation> annotations = new ArrayList<>();
		List<Experimenter> experimenters = new ArrayList<>();
		OmeroAnnotationType type = OmeroAnnotationType.UNKNOWN;
		var gson = new GsonBuilder().registerTypeAdapter(OmeroAnnotation.class, new OmeroAnnotations.GsonOmeroAnnotationDeserializer()).setLenient().create();
		
		try {
			// Get all OmeroAnnotation-s
			JsonArray annotationsArray = json.get("annotations").getAsJsonArray();
			for (var jsonAnn: annotationsArray)
				annotations.add(gson.fromJson(jsonAnn, OmeroAnnotation.class));
			
			// Get all Experimenters
			JsonArray experimentersArray = json.get("experimenters").getAsJsonArray();
			for (var jsonExp: experimentersArray)
				experimenters.add(gson.fromJson(jsonExp, Experimenter.class));
			
		} catch (JsonSyntaxException e) {
			logger.error(e.getLocalizedMessage());
			// If JSE, return empty OmeroAnnotations object with UNKNOWN type
			return new OmeroAnnotations(new ArrayList<>(), new ArrayList<>(), type);
		}
		
		// Get type
		type = annotations.isEmpty() ? null : annotations.get(0).getType();
		return new OmeroAnnotations(annotations, experimenters, type);
	}
	
	/**
	 * Return all {@code OmeroAnnotation} objects present in this {@code OmeroAnnotation}s object.
	 * @return annotations
	 */
	public List<OmeroAnnotation> getAnnotations() {
		return annotations;
	}
	
	/**
	 * Return all {@code Experimenter}s present in this {@code OmeroAnnotations} object.
	 * @return experimenters
	 */
	public List<Experimenter> getExperimenters() {
		return experimenters;
	}
	
	/**
	 * Return the type of the {@code OmeroAnnotation} objects present in this {@code OmeroAnnotations} object.
	 * @return type
	 */
	public OmeroAnnotationType getType() {
		return type;
	}
	
	/**
	 * Return the number of annotations in this {@code OmeroAnnotations} object.
	 * @return size
	 */
	public int getSize() {
		return annotations.stream().mapToInt(e -> e.getNInfo()).sum();
	}
	
	static class GsonOmeroAnnotationDeserializer implements JsonDeserializer<OmeroAnnotation> {

		@Override
		public OmeroAnnotation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			
			var type = OmeroAnnotationType.fromString(((JsonObject)json).get("class").getAsString());
			
			OmeroAnnotation omeroAnnotation;
			if (type == OmeroAnnotationType.TAG)
				omeroAnnotation = context.deserialize(json, TagAnnotation.class);
			else if (type == OmeroAnnotationType.MAP)
				omeroAnnotation = context.deserialize(json, MapAnnotation.class);
			else if (type == OmeroAnnotationType.ATTACHMENT)
				omeroAnnotation = context.deserialize(json, FileAnnotation.class);
			else if (type == OmeroAnnotationType.COMMENT)
				omeroAnnotation = context.deserialize(json, CommentAnnotation.class);
			else if (type == OmeroAnnotationType.RATING)
				omeroAnnotation = context.deserialize(json, LongAnnotation.class);
			else {
				logger.warn("Unsupported type {}", type);
				return null;
			}
			
			return omeroAnnotation;
		}
	}
	
	abstract static class OmeroAnnotation {
		
		@SerializedName(value = "id")
		private int id;
		
		@SerializedName(value = "owner")
		private Owner owner;
		
		@SerializedName(value = "permissions")
		private Permission permissions;
		
		@SerializedName(value = "class")
		private String type;
		
		@SerializedName(value = "link")
		private Link link;
		
		
		/**
		 * Return the {@code OmeroAnnotationType} of this {@code OmeroAnnotation} object.
		 * @return omeroAnnotationType
		 */
		public OmeroAnnotationType getType() {
			return OmeroAnnotationType.fromString(type);
		}
		

		/**
		 * Return the owner of this {@code OmeroAnnotation}. Which is the creator of this annotation 
		 * but not necessarily the person that added it.
		 * @return creator of this annotation
		 */
		public Owner getOwner() {
			return owner;
		}
		
		/**
		 * Return the {@code Owner} that added this annotation. This is <b>not</b> necessarily 
		 * the same as the owner <b>of</b> the annotation.
		 * @return owner who added this annotation
		 */
		public Owner addedBy() {
			return link.getOwner();
		}
		
		/**
		 * Return the number of 'fields' within this {@code OmeroAnnotation}.
		 * @return number of fields
		 */
		public int getNInfo() {
			return 1;
		}
	}
	
	/**
	 * 'Tags'
	 */
	static class TagAnnotation extends OmeroAnnotation {

		@SerializedName(value = "textValue")
		private String value;

		protected String getValue() {
			return value;
		}
	}
	
	/**
	 * 'Key-Value Pairs'
	 */
	static class MapAnnotation extends OmeroAnnotation {
		
		@SerializedName(value = "values")
		private Map<String, String> values;

		protected Map<String, String> getValues() {
			return values;
		}

		@Override
		public int getNInfo() {
			return values.size();
		}
	}
	
	
	/**
	 * 'Attachments'
	 */
	static class FileAnnotation extends OmeroAnnotation {
		
		@SerializedName(value = "file")
		private Map<String, String> map;

		protected String getFilename() {
			return map.get("name");
		}
		
		/**
		 * Size in bits.
		 * @return size
		 */
		protected long getFileSize() {
			return Long.parseLong(map.get("size"));
		}
		
		protected String getMimeType() {
			return map.get("mimetype");
		}
	}
	
	/**
	 * 'Comments'
	 */
	static class CommentAnnotation extends OmeroAnnotation {

		@SerializedName(value = "textValue")
		private String value;

		protected String getValue() {
			return value;
		}
	}
	
	/**
	 * 'Comments'
	 */
	static class LongAnnotation extends OmeroAnnotation {

		@SerializedName(value = "longValue")
		private short value;

		protected short getValue() {
			return value;
		}
	}
}


