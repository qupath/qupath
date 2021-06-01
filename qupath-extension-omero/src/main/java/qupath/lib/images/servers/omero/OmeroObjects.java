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

import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.annotations.SerializedName;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;

/**
 * Class regrouping all OMERO objects (most of which will be instantiated through deserialization) that represent
 * OMERO objects or data.
 * 
 * @author Melvin Gelbard
 */
final class OmeroObjects {
	
	private final static Logger logger = LoggerFactory.getLogger(OmeroObjects.class);
	
	public static enum OmeroObjectType {
		SERVER("#Server", "Server"),
		PROJECT("http://www.openmicroscopy.org/Schemas/OME/2016-06#Project", "Project"),
		DATASET("http://www.openmicroscopy.org/Schemas/OME/2016-06#Dataset", "Dataset"),
		IMAGE("http://www.openmicroscopy.org/Schemas/OME/2016-06#Image", "Image"),
		PLATE("TODO", "Plate"),
		WELL("TODO", "Well"),
		SCREEN("TODO", "Screen"),
		
		// Object for OmeroWebBrowser's 'Orphaned folder' item (not for deserialization)
		ORPHANED_FOLDER("#OrphanedFolder", "Orphaned Folder"),
		
		// Default if unknown
		UNKNOWN("", "Unknown");
		
		private final String APIName;
		private final String displayedName;
		private OmeroObjectType(String APIName, String displayedName) {
			this.APIName = APIName;
			this.displayedName = displayedName;
		}

		static OmeroObjectType fromString(String text) {
	        for (var type : OmeroObjectType.values()) {
	            if (type.APIName.equalsIgnoreCase(text) || type.displayedName.equalsIgnoreCase(text))
	                return type;
	        }
	        return UNKNOWN;
	    }
		
		String toURLString() {
			return displayedName.toLowerCase() + 's';
		}
		
		@Override
		public String toString() {
			return displayedName;
		}
	}
	
	
	static class GsonOmeroObjectDeserializer implements JsonDeserializer<OmeroObject> {

		@Override
		public OmeroObject deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
				throws JsonParseException {
			
			var type = OmeroObjectType.fromString(((JsonObject)json).get("@type").getAsString().toLowerCase());
			
			OmeroObject omeroObj;
			if (type.equals(OmeroObjectType.PROJECT))
				omeroObj = context.deserialize(json, Project.class);
			else if (type.equals(OmeroObjectType.DATASET))
				omeroObj = context.deserialize(json, Dataset.class);
			else if (type.equals(OmeroObjectType.IMAGE))
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
		protected String name;
		
		@SerializedName(value = "@type")
		protected String type;
		
		private Owner owner;
		
		private Group group;
		
		private OmeroObject parent;
		
		/**
		 * Return the OMERO ID associated with this object.
		 * @return id
		 */
		int getId() {
			return id;
		}
		
		/**
		 * Return the name associated with this object.
		 * @return name
		 */
		String getName() {
			return name;
		}
		
		/**
		 * Return the URL associated with this object.
		 * @return url
		 */
		abstract String getAPIURLString();
		
		/**
		 * Return the {@code OmeroObjectType} associated with this object.
		 * @return type
		 */
		OmeroObjectType getType() {
			return OmeroObjectType.fromString(type);
		}
		
		/**
		 * Return the OMERO owner of this object
		 * @return owner
		 */
		Owner getOwner() {
			return owner;
		}
		
		/**
		 * Set the owner of this OMERO object
		 * @param owner
		 */
		void setOwner(Owner owner) {
			this.owner = owner;
		}
		
		/**
		 * Return the OMERO group of this object
		 * @return group
		 */
		Group getGroup() {
			return group;
		}
		
		/**
		 * Set the group of this OMERO object
		 * @param group
		 */
		void setGroup(Group group) {
			this.group = group;
		}
		
		/**
		 * Return the parent of this object
		 * @return parent
		 */
		OmeroObject getParent() {
			return parent;
		}
		
		/**
		 * Set the parent of this OMERO object
		 * @param parent
		 */
		void setParent(OmeroObject parent) {
			this.parent = parent;
		}
		
		/**
		 * Return the number of children associated with this object
		 * @return nChildren
		 */
		int getNChildren() {
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
		
		private String url;
		
		Server(URI uri) {
			super.id = -1;
			super.type = "Server";
			super.owner = null;
			url = uri.toString();
		}

		@Override
		String getAPIURLString() {
			return url;
		}
	}
	
	
	/**
	 * The {@code Orphaned folder} class differs from other in this class as it 
	 * is never created through deserialization of JSON objects. Note that it should only 
	 * contain orphaned images, <b>not</b> orphaned datasets (like the OMERO webclient).
	 * <p>
	 * It should only be used once per {@code OmeroWebImageServerBrowser}, with its children objects loaded 
	 * in an executor (see {@link OmeroTools#populateOrphanedImageList(URI, OrphanedFolder)}). This class keeps track of:
	 * <li>Total child count: total amount of orphaned images on the server.</li>
	 * <li>Current child count: what is displayed in the current {@code OmeroWebServerImageBrowser}, which depends on what is loaded and the current Group/Owner.</li>
	 * <li>Child count: total amount of orphaned images currently loaded (always smaller than total child count).</li>
	 * <li>{@code isLoading} property: defines whether QuPath is still loading its children objects.</li>
	 * <li>List of orphaned image objects.</li>
	 */
	static class OrphanedFolder extends OmeroObject {

		/**
		 * Number of children currently to display (based on Group/Owner and loaded objects)
		 */
		private IntegerProperty currentChildCount;
		
		/**
		 * Number of children objects loaded
		 */
		private AtomicInteger loadedChildCount;
		
		/**
		 * Total number of children (loaded + unloaded)
		 */
		private AtomicInteger totalChildCount;
		
		private BooleanProperty isLoading;
		private ObservableList<OmeroObject> orphanedImageList;
		
		OrphanedFolder(ObservableList<OmeroObject> orphanedImageList) {
			this.name = "Orphaned Images";
			this.type = OmeroObjectType.ORPHANED_FOLDER.toString();
			this.currentChildCount = new SimpleIntegerProperty(0);
			this.loadedChildCount = new AtomicInteger(0);
			this.totalChildCount = new AtomicInteger(-1);
			this.isLoading = new SimpleBooleanProperty(true);
			this.orphanedImageList = orphanedImageList;
		}	
		
		IntegerProperty getCurrentCountProperty() {
			return currentChildCount;
		}

		int incrementAndGetLoadedCount() {
			return loadedChildCount.incrementAndGet();
		}
		
		void setTotalChildCount(int newValue) {
			totalChildCount.set(newValue);
		}
		
		int getTotalChildCount() {
			return totalChildCount.get();
		}

		BooleanProperty getLoadingProperty() {
			return isLoading;
		}
		
		void setLoading(boolean value) {
			isLoading.set(value);
		}
		
		ObservableList<OmeroObject> getImageList() {
			return orphanedImageList;
		}
		
		@Override
		int getNChildren() {
			return currentChildCount.get();
		}

		@Override
		String getAPIURLString() {
			return "";
		}
	}
	
	static class Project extends OmeroObject {
		
		@SerializedName(value = "url:project")
		private String url;
		
		@SerializedName(value = "Description")
		private String description;
		
		@SerializedName(value = "omero:childCount")
		private int childCount;
		
		
		@Override
		String getAPIURLString() {
			return url;
		}
		
		@Override
		int getNChildren() {
			return childCount;
		}
		
		String getDescription() {
			return description;
		}
	}
	
	static class Dataset extends OmeroObject {
		
		@SerializedName(value = "url:dataset")
		private String url;
		
		@SerializedName(value = "Description")
		private String description;
		
		@SerializedName(value = "omero:childCount")
		private int childCount;
		
		
		@Override
		String getAPIURLString() {
			return url;
		}
		
		@Override
		int getNChildren() {
			return childCount;
		}
		
		String getDescription() {
			return description;
		}
	}

	static class Image extends OmeroObject {
		
		@SerializedName(value = "url:image")
		private String url;
		
		@SerializedName(value = "AcquisitionDate")
		private long acquisitionDate = -1;
		
		@SerializedName(value = "Pixels")
		private PixelInfo pixels;
		
		
		@Override
		String getAPIURLString() {
			return url;
		}
		
		long getAcquisitionDate() {
			return acquisitionDate;
		}
		
		int[] getImageDimensions() {
			return pixels.getImageDimensions();
		}
		
		PhysicalSize[] getPhysicalSizes() {
			return pixels.getPhysicalSizes();
		}
		
		String getPixelType() {
			return pixels.getPixelType();
		}
	}
	
	
	static class Owner {
		
		@SerializedName(value = "@id", alternate = "id")
		private int id;
		
		@SerializedName(value = "FirstName")
		private String firstName = "";
		
		@SerializedName(value = "MiddleName")
		private String middleName = "";
		
		@SerializedName(value = "LastName")
		private String lastName = "";
		
		@SerializedName(value = "Email")
		private String emailAddress = "";
		
		@SerializedName(value = "Institution")
		private String institution = "";
		
		@SerializedName(value = "UserName")
		private String username = "";
		
		// Singleton (with static factory)
		private static final Owner ALL_MEMBERS = new Owner(-1, "All members", "", "", "", "", "");
		
		private Owner(int id, String firstName, String middleName, String lastName, String emailAddress, String institution, String username) {
			this.id = Objects.requireNonNull(id);
			this.firstName = Objects.requireNonNull(firstName);
			this.middleName = Objects.requireNonNull(middleName);
			this.lastName = Objects.requireNonNull(lastName);
			
			this.emailAddress = emailAddress;
			this.institution = institution;
			this.username = username;
		}
		
		String getName() {
			// We never know if a deserialized Owner will have all the necessary information
			firstName = firstName == null ? "" : firstName;
			middleName = middleName == null ? "" : middleName;
			lastName = lastName == null ? "" : lastName;
			return firstName + " " + (middleName.isEmpty() ? "" : middleName + " ") + lastName;
		}
		
		int getId() {
			return id;
		}
		
		/**
		 * Dummy {@code Owner} object (singleton instance) to represent all owners.
		 * @return owner
		 */
		static Owner getAllMembersOwner() {
			return ALL_MEMBERS;
		}
		
		@Override
		public String toString() {
			List<String> list = new ArrayList<String>(Arrays.asList("Owner: " + getName(), emailAddress, institution, username));
			list.removeAll(Arrays.asList("", null));
			return String.join(", ", list);
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
		
		// Singleton (with static factory)
		private static final Group ALL_GROUPS = new Group(-1, "All groups");
		
		
		private Group(int id, String name) {
			this.id = id;
			this.name = name;
		}
		
		/**
		 * Dummy {@code Group} object (singleton instance) to represent all groups.
		 * @return group
		 */
		public static Group getAllGroupsGroup() { 
			return ALL_GROUPS; 
		}
		
		String getName() {
			return name;
		}
		
		int getId() {
			return id;
		}

		@Override
		public String toString() {
			return name;
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
		
		int[] getImageDimensions() {
			return new int[] {width, height, c, z, t};
		}
		
		PhysicalSize[] getPhysicalSizes() {
			return new PhysicalSize[] {physicalSizeX, physicalSizeY, physicalSizeZ};
		}
		
		String getPixelType() {
			return imageType.getValue();
		}

		
	}
	
	static class PhysicalSize {
		
		@SerializedName(value = "Symbol")
		private String symbol;
		
		@SerializedName(value = "Value")
		private double value;
		
		
		String getSymbol() {
			return symbol;
		}
			
		double getValue() {
			return value;
		}
		
	}
	
	static class ImageType {
		
		@SerializedName(value = "value")
		private String value;
		
		String getValue() {
			return value;
		}	
	}
	
	
	/**
	 * Both in OmeroAnnotations and in OmeroObjects.
	 */
	static class Permission {
		
		@SerializedName(value = "canDelete")
		private boolean canDelete;
		
		@SerializedName(value = "canAnnotate")
		private boolean canAnnotate;
		
		@SerializedName(value = "canLink")
		private boolean canLink;
		
		@SerializedName(value = "canEdit")
		private boolean canEdit;
		
		// Only in OmeroObjects
		@SerializedName(value = "isUserWrite")
		private boolean isUserWrite;
		
		@SerializedName(value = "isUserRead")
		private boolean isUserRead;
		
		@SerializedName(value = "isWorldWrite")
		private boolean isWorldWrite;
		
		@SerializedName(value = "isWorldRead")
		private boolean isWorldRead;
		
		@SerializedName(value = "isGroupWrite")
		private boolean isGroupWrite;
		
		@SerializedName(value = "isGroupRead")
		private boolean isGroupRead;
		
		@SerializedName(value = "isGroupAnnotate")
		private boolean isGroupAnnotate;

		@SerializedName(value = "perm")
		private String perm;
	}

	
	static class Link {
		
		@SerializedName(value = "id")
		private int id;
		
		@SerializedName(value = "owner")
		private Owner owner;
		
		Owner getOwner() {
			return owner;
		}
	}

	
	static class Experimenter {
		
		@SerializedName(value = "id")
		private int id;
		
		@SerializedName(value = "omeName")
		private String omeName;
		
		@SerializedName(value = "firstName")
		private String firstName;
		
		@SerializedName(value = "lastName")
		private String lastName;
		
		/**
		 * Return the Id of this {@code Experimenter}.
		 * @return id
		 */
		int getId() {
			return id;
		}
		
		/**
		 * Return the full name (first name + last name) of this {@code Experimenter}.
		 * @return full name
		 */
		String getFullName() {
			return firstName + " " + lastName;
		}
	}
}
