package qupath.lib.images.servers.omero;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

class OmeroObjects {
	
	private final static Logger logger = LoggerFactory.getLogger(OmeroObjects.class);
	
	
	static class GsonOmeroObjectDeserializer implements JsonDeserializer<OmeroObject> {

		@Override
		public OmeroObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			
			var type = ((JsonObject)json).get("@type").getAsString().toLowerCase();
			
			OmeroObject omeroObj;
			if (type.endsWith("#project"))
				omeroObj = context.deserialize(json, Project.class);
			else if (type.endsWith("#dataset"))
				omeroObj = context.deserialize(json, Dataset.class);
			else if (type.endsWith("#image"))
				omeroObj = context.deserialize(json, Image.class);
			else {
				logger.warn("Unsupported type {}", type);
				return null;
			}
			Owner owner = context.deserialize(((JsonObject)json).get("omero:details").getAsJsonObject().get("owner"), Owner.class);
			Group group = context.deserialize(((JsonObject)json).get("omero:details").getAsJsonObject().get("group"), Group.class);
			if (owner != null)
				omeroObj.setOwner(owner);
			if (group != null)
				omeroObj.setGroup(group);
			
			return omeroObj;
		}
	}
	
	
	static abstract class OmeroObject {
		
		@SerializedName(value = "@id")
		private int id = -1;
		
		@SerializedName(value = "Name")
		private String name;
		
		@SerializedName(value = "@type")
		private String type;
		
		private Owner owner;
		
		private Group group;
		
		private OmeroObject parent;
		
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
		 * Return the URL associated with this object
		 * @return url
		 */
		public abstract String getAPIURLString();
		
		/**
		 * Return the OMERO type associated with this object
		 * @return type
		 */
		public String getType() {
			return type;
		}
		
		/**
		 * Return the OMERO owner of this object
		 * @return owner
		 */
		public Owner getOwner() {
			return owner;
		}
		
		/**
		 * Set the owner of this OMERO object
		 * @param owner
		 */
		public void setOwner(Owner owner) {
			this.owner = owner;
		}
		
		/**
		 * Return the OMERO group of this object
		 * @return group
		 */
		public Group getGroup() {
			return group;
		}
		
		/**
		 * Set the group of this OMERO object
		 * @param group
		 */
		public void setGroup(Group group) {
			this.group = group;
		}
		
		/**
		 * Return the parent of this object
		 * @return parent
		 */
		public OmeroObject getParent() {
			return parent;
		}
		
		/**
		 * Set the parent of this OMERO object
		 * @param parent
		 */
		public void setParent(OmeroObject parent) {
			this.parent = parent;
		}
		
		/**
		 * Return the number of children associated with this object
		 * @return nChildren
		 */
		public int getNChildren() {
			return 0;
		}
		
		@Override
	    public int hashCode() {
	        return Objects.hash(id);
	    }

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
	            return true;
			
			if (!(obj instanceof OmeroObject))
				return false;
			
			return id == ((OmeroObject)obj).getId();
		}
	}
	
	static class Server extends OmeroObjects.OmeroObject {
		
		String url;
		
		Server(OmeroWebImageServer server) {
			super.id = Integer.parseInt(server.getId());
			super.type = "#server";
			super.owner = null;
			url = server.getURIs().toString();
		}

		@Override
		public String getAPIURLString() {
			return url;
		}
	}
	
	static class Project extends OmeroObjects.OmeroObject {
		
		@SerializedName(value = "url:project")
		private String url;
		
		@SerializedName(value = "Description")
		private String description;
		
		@SerializedName(value = "omero:childCount")
		private int childCount;
		
		
		@Override
		public String getAPIURLString() {
			return url;
		}
		
		@Override
		public int getNChildren() {
			return childCount;
		}
		
		public String getDescription() {
			return description;
		}
	}
	
	static class Dataset extends OmeroObjects.OmeroObject {
		
		@SerializedName(value = "url:dataset")
		private String url;
		
		@SerializedName(value = "Description")
		private String description;
		
		@SerializedName(value = "omero:childCount")
		private int childCount;
		
		
		@Override
		public String getAPIURLString() {
			return url;
		}
		
		@Override
		public int getNChildren() {
			return childCount;
		}
		
		public String getDescription() {
			return description;
		}
	}

	static class Image extends OmeroObjects.OmeroObject {
		
		@SerializedName(value = "url:image")
		private String url;
		
		@SerializedName(value = "AcquisitionDate")
		private long acquisitionDate = -1;
		
		@SerializedName(value = "Pixels")
		private PixelInfo pixels;
		
		
		@Override
		public String getAPIURLString() {
			return url;
		}
		
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
		
		@SerializedName(value = "@id")
		private int id;
		
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
		
		private Owner(int id, String firstName, String middleName, String lastName, String emailAddress, String institution, String username) {
			this.id = id;
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
		
		public int getId() {
			return id;
		}
		
		/**
		 * Dummy {@code Owner} object to represent all owners.
		 * @return owner
		 */
		static public Owner getAllMembersOwner() {
			return new Owner(-1, "All members", "", "", "", "", "");
		}
		
		@Override
		public int hashCode() {
			return Integer.hashCode(id);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			if (!(obj instanceof Owner))
				return false;
			return ((Owner)obj).id == this.id;
		}
	}
	
	static class Group {
		
		@SerializedName(value = "@id")
		private int id;
		
		@SerializedName(value = "Name")
		private String name;
		
		
		private Group(int id, String name) {
			this.id = id;
			this.name = name;
		}
		
		@Override
		public String toString() {
			return name;
		}
		
		public String getName() {
			return name;
		}
		
		public int getId() {
			return id;
		}

		/**
		 * Dummy {@code Group} object to represent all groups.
		 * @return group
		 */
		public static Group getAllGroupsGroup() {
			return new Group(-1, "All groups");
		}

		@Override
		public int hashCode() {
			return Integer.hashCode(id);
		}

		@Override
		public boolean equals(Object obj) {
			if (obj == this)
				return true;
			if (!(obj instanceof Group))
				return false;
			return ((Group)obj).id == this.id;
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
			return new int[] {width, height, c, z, t};
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
		private double value;
		
		
		public String getSymbol() {
			return symbol;
		}
			
		public double getValue() {
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
