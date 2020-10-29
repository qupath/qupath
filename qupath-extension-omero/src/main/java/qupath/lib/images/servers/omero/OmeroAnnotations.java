package qupath.lib.images.servers.omero;

import java.lang.reflect.Type;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

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
class OmeroAnnotations {
	
	private final static Logger logger = LoggerFactory.getLogger(OmeroAnnotations.class);
	
	static class GsonOmeroAnnotationDeserializer implements JsonDeserializer<OmeroAnnotation> {

		@Override
		public OmeroAnnotation deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			
			var type = ((JsonObject)json).get("class").getAsString().toLowerCase();
			
			OmeroAnnotation omeroAnnotation;
			// some OMERO 'class' have an extra I?
			if (type.contains("tagannotation"))	
				omeroAnnotation = context.deserialize(json, TagAnnotation.class);
			else if (type.contains("mapannotation"))
				omeroAnnotation = context.deserialize(json, MapAnnotation.class);
			else if (type.contains("fileannotation"))
				omeroAnnotation = context.deserialize(json, FileAnnotation.class);
			else if (type.contains("commentannotation"))
				omeroAnnotation = context.deserialize(json, CommentAnnotation.class);
			else if (type.contains("longannotation"))
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
		private int owner;
		
		@SerializedName(value = "permissions")
		private Permission permissions;
		
		@SerializedName(value = "parent")
		private Parent parent;
		
		@SerializedName(value = "class")
		private String clazz;
	}
	
	/**
	 * 'Tags'
	 */
	static class TagAnnotation extends OmeroAnnotation {
		
		@SerializedName(value = "textValue")
		String value;

		String getValue() {
			return value;
		}
	}
	
	/**
	 * 'Key-Value Pairs'
	 */
	static class MapAnnotation extends OmeroAnnotation {
		
		@SerializedName(value = "values")
		Map<String, String> values;

		Map<String, String> getValues() {
			return values;
		}
	}
	
	
	/**
	 * 'Attachments'
	 */
	// TODO: handle the file object?
	static class FileAnnotation extends OmeroAnnotation {

		@SerializedName(value = "file")
		String file;

		String getFile() {
			return file;
		}
	}
	
	/**
	 * 'Comments'
	 */
	static class CommentAnnotation extends OmeroAnnotation {

		@SerializedName(value = "textValue")
		String value;

		String getValue() {
			return value;
		}
	}
	
	/**
	 * 'Comments'
	 */
	static class LongAnnotation extends OmeroAnnotation {

		@SerializedName(value = "longValue")
		short value;

		short getValue() {
			return value;
		}
		
	}
	
	
	static class Permission {
		
		@SerializedName(value = "canDelete")
		private boolean canDelete;
		
		@SerializedName(value = "canAnnotate")
		private boolean canAnnotate;
		
		@SerializedName(value = "canLink")
		private boolean canLink;
		
		@SerializedName(value = "canEdit")
		private boolean canEdit;
	}
	
	
	static class Parent {
		
		@SerializedName(value = "id")
		private int id;
		
		@SerializedName(value = "class")
		private String clazz;
		
		@SerializedName(value = "name")
		private String name;
		
	}
	
	
}


