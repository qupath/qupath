/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2024 QuPath developers, The University of Edinburgh
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

package qupath.lib.images;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorDeconvolutionStains.DefaultColorDeconvolutionStains;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.ImageServerBuilder;
import qupath.lib.images.servers.ImageServerMetadata;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.plugins.workflow.Workflow;
import qupath.lib.plugins.workflow.WorkflowListener;
import qupath.lib.plugins.workflow.WorkflowStep;

/**
 * Class that brings together the main data in connection with the analysis of a single image.
 * <p>
 * Currently, this is really the server (to access the image &amp; its pixels) and the object hierarchy that represents detections.
 * In addition, there is an ImageType - as some options may change depending on this.
 * <p>
 * One particularly significant example is that of Brightfield images in pathology, for which stain vectors are often required for
 * effective stain separation.
 * 
 * @author Pete Bankhead
 * @param <T> 
 *
 */
public class ImageData<T> implements WorkflowListener, PathObjectHierarchyListener, AutoCloseable {

	/**
	 * Enum representing possible image types.
	 * <p>
	 * TODO: Warning! This is liable to change in the future to remove specific stain information.
	 */
	public enum ImageType {
			/**
			 * Brightfield image with hematoxylin and DAB stains.
			 */
			BRIGHTFIELD_H_DAB("Brightfield (H-DAB)"),
			/**
			 * Brightfield image with hematoxylin and eosin stains.
			 */
			BRIGHTFIELD_H_E("Brightfield (H&E)"),
			/**
			 * Brightfield image with any stains.
			 */
			BRIGHTFIELD_OTHER("Brightfield (other)"),
			/**
			 * Fluorescence image.
			 */
			FLUORESCENCE("Fluorescence"),
			/**
			 * Other image type, not covered by any of the alternatives above.
			 */
			OTHER("Other"),
			/**
			 * Image type has not been set.
			 */
			UNSET("Not set");
		
		private final String text;
	
		ImageType(String text) {
			this.text = text;
		}
	
		@Override
		public String toString() {
			return text;
		}
		
	}

	private static final Logger logger = LoggerFactory.getLogger(ImageData.class);

	private transient PropertyChangeSupport pcs;

	private transient ImageServerBuilder.ServerBuilder<T> serverBuilder;
	private transient ImageServerMetadata lazyMetadata;

	private transient ImageServer<T> server;
	
	private String lastSavedPath = null;
	
	private PathObjectHierarchy hierarchy;
	private ImageType type = ImageType.UNSET;
	
	// A log of steps that have been applied
	private Workflow workflow = new Workflow();
	
	// Really just one set of stains will be used, as there will only be one ImageType -
	// but here we store stains by ImageType anyway so that they are not instantly forgotten if the ImageType changes
	// Will return null for unsupported ImageTypes
	private Map<ImageType, ColorDeconvolutionStains> stainMap = new HashMap<>();
	
	private Map<String, Object> propertiesMap = new HashMap<>();
	
	
	private boolean changes = false; // Indicating changes since this ImageData was last saved
	
	
	/**
	 * Create a new ImageData with a specified object hierarchy and type.
	 * @param serverBuilder builder to use if the server is to be loaded lazily; may be null to use server instead
	 * @param server server to use directly; may be null to use builder instead
	 * @param hierarchy an object hierarchy, or null to create a new one
	 * @param type the image type, or null to default to ImageType.UNSET
	 * @throws IllegalArgumentException if neither a server nor a server builder is provided
	 */
	private ImageData(ImageServerBuilder.ServerBuilder<T> serverBuilder, ImageServer<T> server, PathObjectHierarchy hierarchy, ImageType type)
			throws IllegalArgumentException {
		if (server == null && serverBuilder == null)
			throw new IllegalArgumentException("Cannot create ImageData without a server or server builder");
		this.pcs = new PropertyChangeSupport(this);
		this.serverBuilder = serverBuilder;
		this.server = server;
		this.hierarchy = hierarchy == null ? new PathObjectHierarchy() : hierarchy;
		initializeStainMap();
		if (type == null)
			type = ImageType.UNSET;
		setImageType(type);

		// Add listeners for changes
		this.hierarchy.addListener(this);
		workflow.addWorkflowListener(this);

		// Discard any changes during construction
		changes = false;
	}

	/**
	 * Create a new ImageData with a lazily-loaded server, hierarchy and type.
	 * The server builder provides the ImageServer required to access pixels and metadata on demand.
	 * <p>
	 * If the server is never requested, then the builder is not used - which can save time and resources.
	 *
	 * @param serverBuilder builder to create the ImageServer
	 * @param hierarchy an object hierarchy, or null to create a new one
	 * @param type the image type, or null to default to ImageType.UNSET
	 */
	public ImageData(ImageServerBuilder.ServerBuilder<T> serverBuilder, PathObjectHierarchy hierarchy, ImageType type) {
		this(serverBuilder, null, hierarchy, type);
	}

	/**
	 * Create a new ImageData with a specified server, hierarchy and type.
	 * @param server server to use to access pixels and metadata
	 * @param hierarchy an object hierarchy, or null to create a new one
	 * @param type the image type, or null to default to ImageType.UNSET
	 */
	public ImageData(ImageServer<T> server, PathObjectHierarchy hierarchy, ImageType type) {
		this(null, server, hierarchy, type);
	}
	
	/**
	 * Create a new ImageData with a specified type and creating a new object hierarchy.
	 * @param server
	 * @param type
	 */
	public ImageData(ImageServer<T> server, ImageType type) {
		this(server, new PathObjectHierarchy(), type);
	}
	
	/**
	 * Get a workflow representing a history of the processing steps applied to the ImageData.
	 * 
	 * @return
	 */
	public Workflow getHistoryWorkflow() {
		return workflow;
	}
	
	
	/**
	 * Create a new ImageData with ImageType.UNKNOWN.
	 * 
	 * @param server
	 * @param hierarchy
	 */
	public ImageData(ImageServer<T> server, PathObjectHierarchy hierarchy) {
		this(server, hierarchy, null);
	}

	/**
	 * Create a new ImageData with ImageType.UNKNOWN and a new PathObjectHierarchy.
	 * 
	 * @param server
	 */
	public ImageData(ImageServer<T> server) {
		this(server, new PathObjectHierarchy());
	}
	
	
	private void initializeStainMap() {
		stainMap.put(ImageType.BRIGHTFIELD_H_DAB, ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_DAB));
		stainMap.put(ImageType.BRIGHTFIELD_H_E, ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_E));
		stainMap.put(ImageType.BRIGHTFIELD_OTHER, ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains.H_DAB));
	}
	
	
	/**
	 * Set the color deconvolution stain vectors for the current image type.
	 * <p>
	 * If the type is not brightfield, an IllegalArgumentException is thrown.
	 * 
	 * @param stains
	 */
	public void setColorDeconvolutionStains(ColorDeconvolutionStains stains) {
		if (!isBrightfield())
			throw new IllegalArgumentException("Cannot set color deconvolution stains for image type " + type);
		logger.trace("Setting stains to {}", stains);
		ColorDeconvolutionStains stainsOld = stainMap.put(type, stains);
		pcs.firePropertyChange("stains", stainsOld, stains);
		
		addColorDeconvolutionStainsToWorkflow(this);

		changes = true;
	}
	
	
	/**
	 * Update the ImageServer metadata. The benefit of using this method rather than manipulating 
	 * the ImageServer directly is that it will fire a property change.
	 * @param newMetadata
	 */
	public void updateServerMetadata(ImageServerMetadata newMetadata) {
		Objects.requireNonNull(newMetadata);
		// Try to check if metadata can be dropped - this is important for lazy loading
		var currentMetadata = getServerMetadata();
		if (Objects.equals(currentMetadata, newMetadata)) {
			logger.trace("Call to updateServerMetadata ignored - metadata is unchanged");
			return;
		}
		// Request the server if we need to update the metadata.
		// This can trigger lazy-loading, but reduces the risk of inconsistent metadata.
		logger.trace("Updating server metadata");
		var server = getServer();
		var oldMetadata = server.getMetadata();
		server.setMetadata(newMetadata);
		pcs.firePropertyChange("serverMetadata", oldMetadata, newMetadata);
		changes = changes || !oldMetadata.equals(newMetadata);
	}

	/**
	 * Get the metadata for the server.
	 * <p>
	 * This is equivalent to {@code getServer().getMetadata()}, <i>unless</i> the server is being loaded lazily
	 * <i>and</i> it is possible to query the metadata without loading the server.
	 * @return
	 */
	public ImageServerMetadata getServerMetadata() {
		if (server == null) {
			if (lazyMetadata != null) {
				logger.trace("Returning lazy metadata");
				return lazyMetadata;
			} else if (serverBuilder != null) {
				var metadata = serverBuilder.getMetadata().orElse(null);
				if (metadata != null)
					return metadata;
			}
		}
		return getServer().getMetadata();
	}
	
	/**
	 * Returns true if the image type is set to brightfield.
	 * @return
	 */
	public boolean isBrightfield() {
		return isBrightfield(getImageType());
	}

	private static boolean isBrightfield(ImageType type) {
		return type.toString().toLowerCase().startsWith("brightfield");
	}
	
	/**
	 * Returns true if the image type is set to fluorescence.
	 * @return
	 */
	public boolean isFluorescence() {
		return getImageType() == ImageType.FLUORESCENCE;
	}
	
	/**
	 * Set the image type.
	 * @param type the type of the image
	 * @throws IllegalArgumentException if the type is not supported by the image;
	 *         specifically, brightfield types using color deconvolution stains are currently only supported for
	 *         RGB images.
	 */
	public void setImageType(final ImageType type) throws IllegalArgumentException {
		if (this.type == type)
			return;

		if (isBrightfield(type) && !(getServerMetadata().isRGB() || getServerMetadata().getChannels().size() == 3)) {
			throw new IllegalArgumentException("Type for non-RGB image cannot be set to " + type);
		}

		logger.trace("Setting image type to {}", type);
		ImageType oldType = this.type;
		this.type = type;
		
		// Log the step
		getHistoryWorkflow().addStep(
				new DefaultScriptableWorkflowStep("Set image type",
						Collections.singletonMap("Image type", type),
                        "setImageType('" + type.name() + "')")
			);
		if (isBrightfield())
			addColorDeconvolutionStainsToWorkflow(this);

		pcs.firePropertyChange("imageType", oldType, type);
		changes = true;
	}
	
	
	
	private static void addColorDeconvolutionStainsToWorkflow(ImageData<?> imageData) {
		ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
		if (stains == null) {
			return;
		}
		
		String arg = ColorDeconvolutionStains.getColorDeconvolutionStainsAsString(imageData.getColorDeconvolutionStains(), 5);
		Map<String, String> map = GeneralTools.parseArgStringValues(arg);
		WorkflowStep lastStep = imageData.getHistoryWorkflow().getLastStep();
		String commandName = "Set color deconvolution stains";
		WorkflowStep newStep = new DefaultScriptableWorkflowStep(commandName,
				map,
				"setColorDeconvolutionStains(\'" + arg + "');");
		
		if (!Objects.equals(newStep, lastStep))
			imageData.getHistoryWorkflow().addStep(newStep);
	}

	/**
	 * Query whether the corresponding ImageServer was lazy-loaded or not,
	 * If {@code isLoaded()} returns false, then calls to {@link #getServer()} will result
	 * in an attempt to load the server.
	 * @return
	 */
	public boolean isLoaded() {
		return this.server != null;
	}

	/**
	 * Get the ServerBuilde corresponding to the ImageServer associated with this ImageData.
	 * <p>
	 * If the server has not yet been loaded, this will return a cached server builder
	 * if it is available and null otherwise.
	 * @return
	 * @see #getServer()
	 */
	public ImageServerBuilder.ServerBuilder<T> getServerBuilder() {
		return this.isLoaded() ? getServer().getBuilder() : this.serverBuilder;
	}
	
	/**
	 * Get the ImageServer, loading it if necessary.
	 * <p>
	 * If no server is available and loading fails, this method may throw an unchecked exception.
	 * @return
	 */
	public ImageServer<T> getServer() {
		if (server == null && serverBuilder != null) {
			synchronized (this) {
				if (server == null) {
					try {
						logger.debug("Lazily requesting image server: {}", serverBuilder);
						server = serverBuilder.build();
						if (lazyMetadata != null && !lazyMetadata.equals(server.getMetadata())) {
							updateServerMetadata(lazyMetadata);
						}
					} catch (Exception e) {
						throw new RuntimeException("Failed to load ImageServer", e);
					}
				}
			}
		}
		return server;
	}
	
	/**
	 * Get the path of the ImageServer.
	 * @return
	 */
	public String getServerPath() {
		return getServer().getPath();
	}
	
	/**
	 * Get the object hierarchy.
	 * @return
	 */
	public PathObjectHierarchy getHierarchy() {
		return hierarchy;
	}
	
	/**
	 * Get the image type
	 * @return
	 */
	public ImageType getImageType() {
		return type;
	}
	
	/**
	 * Get the stains defined for this image, or null if this is not a brightfield image suitable for color deconvolution.
	 * @return
	 */
	public ColorDeconvolutionStains getColorDeconvolutionStains() {
		return stainMap.get(getImageType());
	}
	
	/**
	 * Add a new property change listener.
	 * @param listener
	 */
	public void addPropertyChangeListener(PropertyChangeListener listener) {
		if (pcs == null)
			pcs = new PropertyChangeSupport(this);
        this.pcs.addPropertyChangeListener(listener);
    }

	/**
	 * Remove a property change listener.
	 * @param listener
	 */
    public void removePropertyChangeListener(PropertyChangeListener listener) {
        this.pcs.removePropertyChangeListener(listener);
    }


    /**
     * Get a specified property.
     * @param key
     * @return
     */
    public Object getProperty(String key) {
    		return propertiesMap.get(key);
    }

    /**
     * Set a property. Note that if properties are Serializable, they will be included in 
     * associated data files - otherwise they are stored only transiently.
     * @param key
     * @param value
     * @return
     */
    public Object setProperty(String key, Object value) {
	    	Object oldValue = propertiesMap.put(key, value);
	    	if (oldValue == value)
	    		return oldValue;
	    	if (oldValue == null)
	    		changes = value != null;
	    	else
	    		changes = changes || !oldValue.equals(value);
	    	logger.trace("Setting property: {}: {}", key, value);
	    	if (oldValue != value)
	    		pcs.firePropertyChange(key, oldValue, value);
	    	return oldValue;
    }

    /**
     * Remove a specified property.
     * @param key
     * @return
     */
    public Object removeProperty(String key) {
    	if (propertiesMap.containsKey(key)) {
        	Object oldValue = propertiesMap.remove(key);
    		changes = true;
	    	logger.trace("Removing property: {}: {}", key, oldValue);
    		pcs.firePropertyChange(key, oldValue, null);
    		return oldValue;
    	}
    	return null;
    }

    /**
     * Get an unmodifiable map representing all known properties for this ImageData.
     * @return
     */
    public Map<String, Object> getProperties() {
    		return Collections.unmodifiableMap(propertiesMap);
    }
    
    
    /**
     * Get the last path used to save this object;
     * @return
     */
    public String getLastSavedPath() {
    		return lastSavedPath;
    }
    
    /**
     * Returns true if changes have been recorded since the last time this object was notified that it was saved.
     * 
     * @return
     * 
     * @see #setLastSavedPath
     */
    public boolean isChanged() {
    		return changes;
    }
        
    /**
     * Set {@link #isChanged()} status.
     * 
     * @param isChanged
     */
    public void setChanged(boolean isChanged) {
    	this.changes = isChanged;
    }
    
    /**
     * Set the last path used to save this object;
     * 
     * @param path
     * @param resetChanged If true, then the isChanged() flag will be reset to false;
     */
    public void setLastSavedPath(final String path, final boolean resetChanged) {
    	this.lastSavedPath = path;
    	if (resetChanged)
    		this.changes = false;
    }


	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		changes = true;
	}


	@Override
	public void workflowUpdated(Workflow workflow) {
		changes = true;
	}

	/**
	 * Close the server if it has been loaded.
	 * Note that this should <i>not</i> be called if the server is still in use.
	 * @throws Exception
	 */
	@Override
	public void close() throws Exception {
		if (server != null)
			server.close();
	}


	@Override
	public String toString() {
		String serverName;
		if (server == null) {
			if (serverBuilder == null) {
				serverName = "no server";
			} else if (lazyMetadata != null){
				serverName = lazyMetadata.getName() + " (not yet loaded)";
			} else {
				serverName = "lazy-loaded server";
			}
		} else {
			serverName = ServerTools.getDisplayableImageName(server);
		}
		return "ImageData: " + getImageType() + ", " + serverName;
	}

    
}
