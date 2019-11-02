/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.images;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorDeconvolutionStains.DefaultColorDeconvolutionStains;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
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
 *
 */
public class ImageData<T> implements WorkflowListener, PathObjectHierarchyListener {
	
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

	final private static Logger logger = LoggerFactory.getLogger(ImageData.class);

	transient private PropertyChangeSupport pcs;
	
	transient private ImageServer<T> server;
	
	private String lastSavedPath = null;
	
	private String serverPath;
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
	 * @param server
	 * @param type
	 */
	public ImageData(ImageServer<T> server, PathObjectHierarchy hierarchy, ImageType type) {
		pcs = new PropertyChangeSupport(this);
		this.server = server;
		this.hierarchy = hierarchy == null ? new PathObjectHierarchy() : hierarchy;
		this.serverPath = server == null ? null : server.getPath(); // TODO: Deal with sub image servers
		initializeStainMap();
		if (type == null)
			type = ImageType.UNSET;
		setImageType(type);
		
		// Add listeners for changes
		this.hierarchy.addPathObjectListener(this);
		workflow.addWorkflowListener(this);
		
		// Discard any changes during construction
		changes = false;
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
//		logger.error("WARNING: Setting color deconvolution stains is not yet scriptable!!!!");
		
		changes = true;
	}
	
	
	/**
	 * Update the ImageServer metadata. The benefit of using this method rather than manipulating 
	 * the ImageServer directly is that it will fire a property change.
	 * @param newMetadata
	 */
	public void updateServerMetadata(ImageServerMetadata newMetadata) {
		Objects.requireNonNull(newMetadata);
		logger.trace("Updating server metadata");
		var oldMetadata = server.getMetadata();
		server.setMetadata(newMetadata);
		pcs.firePropertyChange("serverMetadata", oldMetadata, newMetadata);
		changes = changes || !oldMetadata.equals(newMetadata);
	}
	
//	public void setColorDeconvolutionStains(final String stainsString) {
//		setColorDeconvolutionStains(ColorDeconvolutionStains.parseColorDeconvolutionStainsArg(stainsString));
//	}
//	
//	public void setImageType(final String type) {
//		setImageType(ImageType.valueOf(type));
//	}
	
	/**
	 * Returns true if the image type is set to brightfield.
	 * @return
	 */
	public boolean isBrightfield() {
		return getImageType().toString().toLowerCase().startsWith("brightfield");
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
	 * @param type
	 */
	public void setImageType(final ImageType type) {
		if (this.type == type)
			return;
		logger.trace("Setting image type to {}", type);
		ImageType oldType = this.type;
		this.type = type;
		
		// Log the step
		getHistoryWorkflow().addStep(
				new DefaultScriptableWorkflowStep("Set image type",
						Collections.singletonMap("Image type", type),
						"setImageType(\'" + type.name() + "');")
			);
		if (isBrightfield())
			addColorDeconvolutionStainsToWorkflow(this);
		
		// TODO: REINTRODUCE LOGGING!
//		// Log the step
//		getWorkflow().addStep(
//				new DefaultScriptableWorkflowStep("Set image type",
//						Collections.singletonMap("Image type", type),
//						QP.class.getSimpleName() + ".setImageType(\'" + type.toString() + "');")
//			);
//		if (isBrightfield())
//			addColorDeconvolutionStainsToWorkflow(this);

		pcs.firePropertyChange("imageType", oldType, type);
		changes = true;
	}
	
	
	
	// TODO: REINTRODUCE LOGGING!
	private static void addColorDeconvolutionStainsToWorkflow(ImageData<?> imageData) {
//		logger.warn("Color deconvolution stain logging not currently enabled!");

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
		
		if (lastStep != null && commandName.equals(lastStep.getName()))
			imageData.getHistoryWorkflow().replaceLastStep(newStep);
		else
			imageData.getHistoryWorkflow().addStep(newStep);

		
//		ColorDeconvolutionStains stains = imageData.getColorDeconvolutionStains();
//		if (stains == null)
//			return;
//		
//		String arg = ColorDeconvolutionStains.getColorDeconvolutionStainsAsString(imageData.getColorDeconvolutionStains(), 5);
//		Map<String, String> map = GeneralTools.parseArgStringValues(arg);
//		WorkflowStep lastStep = imageData.getWorkflow().getLastStep();
//		String commandName = "Set color deconvolution stains";
//		WorkflowStep newStep = new DefaultScriptableWorkflowStep(commandName,
//				map,
//				QP.class.getSimpleName() + ".setColorDeconvolutionStains(\'" + arg + "');");
//		
//		if (lastStep != null && commandName.equals(lastStep.getName()))
//			imageData.getWorkflow().replaceLastStep(newStep);
//		else
//			imageData.getWorkflow().addStep(newStep);
	}
	
	/**
	 * Get the ImageServer.
	 * @return
	 */
	public ImageServer<T> getServer() {
		return server;
	}
	
	/**
	 * Get the path of the ImageServer.
	 * @return
	 */
	public String getServerPath() {
		return serverPath;
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
	    	if (oldValue == null)
	    		changes = value != null;
	    	else
	    		changes = changes || !oldValue.equals(value);
//	    	System.err.println(changes + " setting " + key + " to " + value);
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
	
	
	@Override
	public String toString() {
		if (getServer() == null)
			return "ImageData: " + getImageType() + ", no server";
		else
			return "ImageData: " + getImageType() + ", " + ServerTools.getDisplayableImageName(getServer());
	}

    
}
