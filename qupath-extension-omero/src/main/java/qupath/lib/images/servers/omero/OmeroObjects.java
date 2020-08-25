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
		
		@SerializedName(value = "Name")
		private String name;
		
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
		 * Return the name associated with this object
		 * @return name
		 */
		public String getName() {
			return name;
		}
		
		/**
		 * Return the OMERO type associated with this object
		 * @return type
		 */
		public String getType() {
			return this.type;
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
	
	static class Server extends OmeroObjects.OmeroObject {
		
		Server(OmeroWebImageServer server) {
			super.id = Integer.parseInt(server.getId());
			super.type = "#server";
			super.owner = null;
		}
	}
	
	static class Project extends OmeroObjects.OmeroObject {
		
		@SerializedName(value = "Description")
		private String description;
		
		@SerializedName("omero:childCount")
		private int childCount;
		
		public String getDescription() {
			return description;
		}
		
		public int getNChildren() {
			return childCount;
		}
	}
	
	static class Dataset extends OmeroObjects.OmeroObject {
		
		@SerializedName(value = "Description")
		private String description;
		
		@SerializedName("omero:childCount")
		private int childCount;

		
		public String getDescription() {
			return description;
		}
		
		public int getNChildren() {
			return childCount;
		}
	}

	static class Image extends OmeroObjects.OmeroObject {
		
		@SerializedName(value = "AcquisitionDate")
		private long acquisitionDate = -1;
		
		@SerializedName(value = "Pixels")
		private PixelInfo pixels;
		
		public long getAcquisitionDate() {
			return acquisitionDate;
		}
		
		public int[] getImageDimensions() {
			return pixels.getImageDimensions();
		}
		
		public PhysicalSize[] getPhysicalSizes() {
			return pixels.getPhysicalSizes();
		}
		
		public String getPixelType() {
			return pixels.getPixelType();
		}
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
		
		private Owner(String firstName, String middleName, String lastName, String emailAddress, String institution, String username) {
			this.firstName = firstName;
			this.middleName = middleName;
			this.lastName = lastName;
			this.emailAddress = emailAddress;
			this.institution = institution;
			this.username = username;
		}
		
		@Override
		public String toString() {
			List<String> list = new ArrayList<String>(Arrays.asList("Owner: " + getName(), emailAddress, institution, username));
			list.removeAll(Collections.singleton(""));
			return String.join(", ", list);
		}
		
		static public Owner getAllMembersOwner() {
			return new Owner("All members", "", "", "", "", "");
		}
	}
	
	static class PixelInfo {
		
		@SerializedName(value = "SizeX")
		private int width;
		
		@SerializedName(value = "SizeY")
		private int height;
		
		@SerializedName(value = "SizeZ")
		private int z;
		
		@SerializedName(value = "SizeC")
		private int c;
		
		@SerializedName(value = "SizeT")
		private int t;
		
		@SerializedName(value = "PhysicalSizeX")
		private PhysicalSize physicalSizeX;
		
		@SerializedName(value = "PhysicalSizeY")
		private PhysicalSize physicalSizeY;
		
		@SerializedName(value = "PhysicalSizeZ")
		private PhysicalSize physicalSizeZ;
		
		@SerializedName(value = "Type")
		private ImageType imageType;
		
		public int[] getImageDimensions() {
			return new int[] {width, height, z, c, t};
		}
		
		public PhysicalSize[] getPhysicalSizes() {
			return new PhysicalSize[] {physicalSizeX, physicalSizeY, physicalSizeZ};
		}
		
		public String getPixelType() {
			return imageType.getValue();
		}

		
	}
	
	static class PhysicalSize {
		
		@SerializedName(value = "Symbol")
		private String symbol;
		
		@SerializedName(value = "Value")
		private int value;
		
		
		public String getSymbol() {
			return symbol;
		}
			
		public int getValue() {
			return value;
		}
		
	}
	
	static class ImageType {
		
		@SerializedName(value = "value")
		private String value;
		
		public String getValue() {
			return value;
		}	
	}
	
}
