package qupath.lib.images.servers.omero;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import qupath.lib.io.GsonTools;

class OmeroObjects {
	
private final static Logger logger = LoggerFactory.getLogger(OmeroObjects.class);

	
	static class GsonOmeroObjectDeserializer implements JsonDeserializer<OmeroObject> {

		@Override
		public OmeroObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			
			var type = ((JsonObject)json).get("@type").getAsString().toLowerCase();
			if (type.endsWith("#project")) {
				Owner owner = GsonTools.getInstance().fromJson(((JsonObject)json).get("omero:details").getAsJsonObject().get("owner"), Owner.class);
				Project project = context.deserialize(json, Project.class);
				if (owner != null)
					project.setOwner(owner);
				return project;
			}
			if (type.endsWith("#dataset")) {
				Owner owner = context.deserialize(((JsonObject)json).get("omero:details").getAsJsonObject().get("owner"), Owner.class);
				Dataset dataset = context.deserialize(json, Dataset.class);
				if (owner != null)
					dataset.setOwner(owner);
				return dataset;
			}
			if (type.endsWith("#image")) {
				Owner owner = context.deserialize(((JsonObject)json).get("omero:details").getAsJsonObject().get("owner"), Owner.class);
				Image image = context.deserialize(json, Image.class);
				if (owner != null)
					image.setOwner(owner);
				return image;
			}
			logger.warn("Unsupported type {}", type);
			return null;
		}
		
	}
	
	
	static abstract class OmeroObject {
		
		@SerializedName(value = "@id")
		private int id = -1;
		
		@SerializedName(value = "@type")
		private String type;
		
		private transient Owner owner;
		
		/**
		 * Return the OMERO ID associated with this object
		 * @return id
		 */
		public int getId() {
			return id;
		}
		
		/**
		 * Return the OMERO owner of this object
		 * @return owner
		 */
		public Owner getOwner() {
			return owner;
		}
		
		protected void setOwner(Owner owner) {
			this.owner = owner;
		}
	}
	
	static class Project extends OmeroObjects.OmeroObject {
		
		@SerializedName(value = "Name")
		private String name;
		
		@SerializedName(value = "Decription")
		private String description;
		
		@SerializedName("omero:childCount")
		private int childCount;
		
		public String getName() {
			return name;
		}
		
		public int getNChildren() {
			return childCount;
		}
	}
	
	static class Dataset extends OmeroObjects.OmeroObject {
		@SerializedName(value = "Name")
		private String name;
		
		@SerializedName(value = "Decription")
		private String description;
		
		@SerializedName("omero:childCount")
		private int childCount;
		
		public String getName() {
			return name;
		}
		
		public int getNChildren() {
			return childCount;
		}
	}

	static class Image extends OmeroObjects.OmeroObject {
		
	}
	
	
	static class Owner {
		
		@SerializedName(value = "FirstName")
		private String firstName;
		
		@SerializedName(value = "MiddleName")
		private String middleName;
		
		@SerializedName(value = "LastName")
		private String lastName;
		
		@SerializedName(value = "Email")
		private String emailAddress;
		
		@SerializedName(value = "Institution")
		private String institution;
		
		@SerializedName(value = "UserName")
		private String username;
		
		public String getName() {
			return firstName + " " + (middleName.isEmpty() ? "" : middleName + " ") + lastName;
		}
		
		@Override
		public String toString() {
			List<String> list = new ArrayList<String>(Arrays.asList("Owner: " + getName(), emailAddress, institution, username));
			list.removeAll(Collections.singleton(""));
			return String.join(", ", list);
			
		}
	}
}
