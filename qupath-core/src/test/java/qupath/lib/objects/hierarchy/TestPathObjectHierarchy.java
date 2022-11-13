/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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

package qupath.lib.objects.hierarchy;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import qupath.lib.regions.ImagePlane;
import qupath.lib.regions.ImageRegion;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjectTools;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathRootObject;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyEvent;
import qupath.lib.objects.hierarchy.events.PathObjectHierarchyListener;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

@SuppressWarnings("javadoc")
public class TestPathObjectHierarchy {
	PathObjectHierarchy myPH = new PathObjectHierarchy();
	PO_hlistener myPOHL = new PO_hlistener();
	PathObjectHierarchyEvent event = PathObjectHierarchyEvent.createObjectAddedEvent(new Object(), myPH, new PathAnnotationObject(), new PathAnnotationObject());
	PathRootObject myPRO = new PathRootObject();
	ROI my_PR1 = ROIs.createRectangleROI(10, 10, 2, 2, ImagePlane.getDefaultPlane());
	ROI my_PR2 = ROIs.createRectangleROI(10, 10, 1, 1, ImagePlane.getDefaultPlane());
	ROI my_PR3 = ROIs.createRectangleROI(30, 30, 1, 1, ImagePlane.getDefaultPlane());
	PathObject myChild1PAO = PathObjects.createAnnotationObject(my_PR1);
	PathObject myChild2PAO = PathObjects.createAnnotationObject(my_PR2); 
	PathObject myChild3PAO = PathObjects.createAnnotationObject(my_PR3);
	ImageRegion myIR = ImageRegion.createInstance(25, 25, 10, 10, 0, 0); // set to contain child3 - other values can be used to test negative 
	
	@Test
	public void test_PathHierarchy() {

		// Created new PH with listeners
		myPH.addListener(myPOHL);
		assertTrue(myPH.isEmpty());
		
		// Firing direct event 
		myPH.fireEvent(event);
		assertEquals(myPOHL.getFiredState(), 1); // event(ADDED) fired
		myPOHL.setFiredState(0);
		
		// Creating structure of POs
		myChild1PAO.addChildObject(myChild3PAO);
		myPRO.addChildObject(myChild1PAO);
		assertEquals(myPRO.nChildObjects(), 1);
		assertEquals(myChild1PAO.getParent(), myPRO);
		
		// Firing indirect events (adding/removing from hierarchy)
		// Adding one PO with a child (so 2)
		myPH.addObject(myChild1PAO);
		Collection<PathObject> POAL1 = new ArrayList<>();
		POAL1 = myPH.getObjects(POAL1, PathAnnotationObject.class);
		assertEquals(POAL1.size(), 2); // 1 + child
		assertEquals(myPH.getObjects(null, PathAnnotationObject.class), POAL1);
		assertEquals(myChild1PAO.getParent(), myPH.getRootObject()); // child1 has been added to the PH - the PH root is the parent of child1
		assertEquals(myChild3PAO.getParent(), myChild1PAO); // child3 is added to the PH through the addition of child1 (its parent)
		
		assertEquals(myPOHL.getFiredState(), 1); // event(ADDED) fired
		myPOHL.setFiredState(0);

		// Adding one PO without a child (so 1) - this PO, however, is fully contained within Child1 
		myPH.insertPathObject(myChild2PAO, true);
		Collection<PathObject> POAL2 = new ArrayList<>();
		POAL2 = myPH.getObjects(POAL2, PathAnnotationObject.class);
		assertEquals(POAL2.size(), 3); //  2 + 1 
		assertEquals(myPH.getObjects(null, PathAnnotationObject.class), POAL2);
		assertEquals(myChild2PAO.nChildObjects(), 0); // child2 doesn't have any children (child3 is only a child to child1 through the PO lineage)
		//assertEquals(myChild2PAO.getParent(), myPH.getRootObject()); // child2's parent is not the root of the PH
		assertEquals(myChild2PAO.getParent(), myChild1PAO); // child2's parent is child1 (as child2 is contained within child1)
		
		Collection<PathObject> POAL3 = new ArrayList<>();
		POAL3 = PathObjectTools.getDescendantObjects(myChild1PAO, POAL3, PathAnnotationObject.class);
		assertEquals(POAL3.size(), 2); // child1 has now 2 descendants - one on the PH lineage (child2) and one on the PO lineage (child3)
		assertEquals(PathObjectTools.getDescendantObjects(myChild1PAO, null, PathAnnotationObject.class), POAL3);
		
		List<PathObject> POAL4 = new ArrayList<>();
		POAL4 = myPH.getFlattenedObjectList(POAL4);
		assertEquals(POAL4.size(), 4); // all nodes (including parent node from hierarchy)
		assertEquals(myPH.getFlattenedObjectList(null), POAL4);
				
		assertEquals(myPH.nObjects(), 3); // descendants - TODO: name may be a bit misleading???
		
//		// Remove one PO without a child (so 2 left)		
//		myPH.removeObject(myChild2PAO, true); // no children, so a changed structure event will fire 
//		List<PathObject> POAL5 = new ArrayList<>();
//		POAL5 = myPH.getObjects(POAL5, PathAnnotationObject.class);
//		assertEquals(POAL5.size(), 2); // 3 - 1  
//		assertEquals(myPH.getObjects(null, PathAnnotationObject.class), POAL5);		
//
//		assertEquals(myPOHL.getFiredState(), 3); // event(CHANGED STRUCTURE) fired
//		myPOHL.setFiredState(0);
		
		// Remove one PO without a child (so 2 left)		
		myPH.removeObject(myChild2PAO, true); // no children, so a removed event will fire 
		Collection<PathObject> POAL5 = new ArrayList<>();
		POAL5 = myPH.getObjects(POAL5, PathAnnotationObject.class);
		assertEquals(POAL5.size(), 2); // 3 - 1  
		assertEquals(myPH.getObjects(null, PathAnnotationObject.class), POAL5);		

		assertEquals(myPOHL.getFiredState(), 2); // event(CHANGED REMOVED) fired
		myPOHL.setFiredState(0);
		
		// Remove one PO with a child but keep child (so 1 left)		
		myPH.removeObject(myChild1PAO, true);
		Collection<PathObject> POAL6 = new ArrayList<>();
		POAL6 = myPH.getObjects(POAL6, PathAnnotationObject.class);
		assertEquals(POAL6.size(), 1); // 2 - 1  
		assertEquals(myPH.getObjects(null, PathAnnotationObject.class), POAL6);		

		assertEquals(myPOHL.getFiredState(), 2); // event(REMOVED) fired
		myPOHL.setFiredState(0);
		
		// Check how many objects present in the region indicated below 
		Collection<PathObject> POAL7 = new ArrayList<>();
		POAL7 = myPH.getObjectsForRegion(PathAnnotationObject.class, myIR, POAL7);
		assertEquals(POAL7.size(), 1); // since there's only 1 object left (child3), this checks whether it falls within the region   
		assertEquals(myPH.getObjects(null, PathAnnotationObject.class), POAL7);		
		
		// Finalise by removing all items left
		assertEquals(myPH.nObjects(), 1); 
		myPH.clearAll();
		assertEquals(myPH.nObjects(), 0);

	}
	
	/**
	 * Introduced in v0.2.1 to cope with fixed behavior with TMA cores.
	 * This test failed in v0.2.0, however {@link #test_resolveHierarchy()} already passed.
	 */
	@Test
	public void test_resolveHierarchyWithTMA() {
		
		var hierarchy = new PathObjectHierarchy();
		
		var core = PathObjects.createTMACoreObject(1000, 1000, 1000, false);
		var tmaGrid = DefaultTMAGrid.create(Collections.singletonList(core), 1);
		hierarchy.setTMAGrid(tmaGrid);
		
		assertEquals(hierarchy.nObjects(), 1); 
		
		var annotationInCore = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(900, 900, 200, 200, ImagePlane.getDefaultPlane())
				);
		annotationInCore.setName("Annotation in core");
		hierarchy.addObject(annotationInCore);

		var annotationInAnnotation = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(950, 950, 100, 100, ImagePlane.getDefaultPlane())
				);
		annotationInAnnotation.setName("Annotation in " + annotationInCore.getName());
		hierarchy.addObject(annotationInAnnotation);

		var annotationOutsideCore = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(2000, 2000, 100, 100, ImagePlane.getDefaultPlane())
				);
		annotationOutsideCore.setName("Annotation outside core");
		hierarchy.addObject(annotationOutsideCore);

		// An annotation containing the core should *not* become a parent of the core,
		// since cores must always be directly below the root
		var annotationContainingCore = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(400, 400-100, 1200, 1200, ImagePlane.getDefaultPlane())
				);
		annotationContainingCore.setName("Annotation containing core");
		hierarchy.addObject(annotationContainingCore);
		
		// Sanity check to ensure that our rectangle does indeed contain the core
		assertTrue(annotationContainingCore.getROI().getGeometry().contains(core.getROI().getGeometry()));
		assertFalse(core.getROI().getGeometry().contains(annotationContainingCore.getROI().getGeometry()));
		
		// Add a detection at the centroid of each annotation
		var mapDetections = new LinkedHashMap<PathObject, PathObject>();
		for (var annotation : Arrays.asList(annotationInAnnotation, annotationOutsideCore)) {
			double radius = 2;
			var roi = annotation.getROI();
			var detection = PathObjects.createDetectionObject(
					ROIs.createEllipseROI(
							roi.getCentroidX()-radius,
							roi.getCentroidX()-radius,
							radius*2, radius*2, roi.getImagePlane())
					);
			detection.setName("Detection in " + annotation.getName());
			mapDetections.put(detection, annotation);
			hierarchy.addObject(detection);
		}
		// Add another detection outside of everything
		var detectionOutside = PathObjects.createDetectionObject(
				ROIs.createRectangleROI(4000, 4000, 5, 5, ImagePlane.getDefaultPlane())
				);
		detectionOutside.setName("Detection outside everything");
		hierarchy.addObject(detectionOutside);
		mapDetections.put(detectionOutside, hierarchy.getRootObject());

		// Add another detection inside the core but outside of any annotations
		var detectionInCore = PathObjects.createDetectionObject(
				ROIs.createRectangleROI(1000, 510, 1, 1, ImagePlane.getDefaultPlane())
//				ROIs.createPointsROI(1000, 510, ImagePlane.getDefaultPlane())
				);
//		System.err.println("Centroid: " + detectionInCore.getROI().getCentroidX() + ", " + detectionInCore.getROI().getCentroidY());
		detectionInCore.setName("Detection in core only");
		hierarchy.addObject(detectionInCore);
		mapDetections.put(detectionInCore, core);


		// Check hierarchy size
		assertEquals(hierarchy.nObjects(), 5 + mapDetections.size()); 
				
		// Check level before resolving the hierarchy
		assertEquals(core.getLevel(), 1); 
		assertEquals(annotationContainingCore.getLevel(), 1); 
		assertEquals(annotationInCore.getLevel(), 1); 
		assertEquals(annotationInAnnotation.getLevel(), 1); 
		assertEquals(annotationOutsideCore.getLevel(), 1); 
		
		assertEquals(core.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationContainingCore.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationInCore.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationInAnnotation.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationOutsideCore.getParent(), hierarchy.getRootObject()); 
		
		for (var detection : mapDetections.keySet())
			assertEquals(detection.getParent(), hierarchy.getRootObject()); 

		hierarchy.resolveHierarchy();
		
		// Check level after resolving the hierarchy
		assertEquals(core.getLevel(), 1); 
		assertEquals(annotationInCore.getLevel(), 2); 
		assertEquals(annotationInAnnotation.getLevel(), 3); 
		assertEquals(annotationOutsideCore.getLevel(), 1); 
		
		assertEquals(core.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationContainingCore.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationInCore.getParent(), core); 
		assertEquals(annotationInAnnotation.getParent(), annotationInCore); 
		assertEquals(annotationOutsideCore.getParent(), hierarchy.getRootObject()); 
		
		for (var entry : mapDetections.entrySet()) {
//			System.err.println(entry.getKey() + ": " + entry.getKey().getParent() + ", " + entry.getValue());
			assertEquals(entry.getKey().getParent(), entry.getValue()); 
		}

	}
	
	
	/**
	 * Based on {@link #test_resolveHierarchyWithTMA()}, without the TMA core involved.
	 * This test already passed in v0.2.0.
	 */
	@Test
	public void test_resolveHierarchy() {
		
		var hierarchy = new PathObjectHierarchy();
		
		var annotationInCore = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(900, 900, 200, 200, ImagePlane.getDefaultPlane())
				);
		annotationInCore.setName("Annotation in core");
		hierarchy.addObject(annotationInCore);

		var annotationInAnnotation = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(950, 950, 100, 100, ImagePlane.getDefaultPlane())
				);
		annotationInAnnotation.setName("Annotation in " + annotationInCore.getName());
		hierarchy.addObject(annotationInAnnotation);

		var annotationOutsideCore = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(2000, 2000, 100, 100, ImagePlane.getDefaultPlane())
				);
		annotationOutsideCore.setName("Annotation outside core");
		hierarchy.addObject(annotationOutsideCore);

		// This was previously containing the entire TMA core - without the core, it acts as a stand-in
		var annotationContainingCore = PathObjects.createAnnotationObject(
				ROIs.createRectangleROI(400, 400-100, 1200, 1200, ImagePlane.getDefaultPlane())
				);
		annotationContainingCore.setName("Annotation stand-in for core");
		hierarchy.addObject(annotationContainingCore);
		
		// Add a detection at the centroid of each annotation
		var mapDetections = new LinkedHashMap<PathObject, PathObject>();
		for (var annotation : Arrays.asList(annotationInAnnotation, annotationOutsideCore)) {
			double radius = 2;
			var roi = annotation.getROI();
			var detection = PathObjects.createDetectionObject(
					ROIs.createEllipseROI(
							roi.getCentroidX()-radius,
							roi.getCentroidX()-radius,
							radius*2, radius*2, roi.getImagePlane())
					);
			detection.setName("Detection in " + annotation.getName());
			mapDetections.put(detection, annotation);
			hierarchy.addObject(detection);
		}
		// Add another detection outside of everything
		var detectionOutside = PathObjects.createDetectionObject(
				ROIs.createRectangleROI(4000, 4000, 5, 5, ImagePlane.getDefaultPlane())
				);
		detectionOutside.setName("Detection outside everything");
		hierarchy.addObject(detectionOutside);
		mapDetections.put(detectionOutside, hierarchy.getRootObject());

		// Add another detection inside the core but outside of any annotations
		var detectionInCore = PathObjects.createDetectionObject(
				ROIs.createRectangleROI(1000, 510, 1, 1, ImagePlane.getDefaultPlane())
//				ROIs.createPointsROI(1000, 510, ImagePlane.getDefaultPlane())
				);
//		System.err.println("Centroid: " + detectionInCore.getROI().getCentroidX() + ", " + detectionInCore.getROI().getCentroidY());
		detectionInCore.setName("Detection in core only");
		hierarchy.addObject(detectionInCore);


		// Check hierarchy size
		assertEquals(hierarchy.nObjects(), 5 + mapDetections.size()); 
				
		// Check level before resolving the hierarchy
		assertEquals(annotationContainingCore.getLevel(), 1); 
		assertEquals(annotationInCore.getLevel(), 1); 
		assertEquals(annotationInAnnotation.getLevel(), 1); 
		assertEquals(annotationOutsideCore.getLevel(), 1); 
		
		assertEquals(annotationContainingCore.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationInCore.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationInAnnotation.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationOutsideCore.getParent(), hierarchy.getRootObject()); 
		
		for (var detection : mapDetections.keySet())
			assertEquals(detection.getParent(), hierarchy.getRootObject()); 

		hierarchy.resolveHierarchy();
		
		// Check level after resolving the hierarchy
		assertEquals(annotationInCore.getLevel(), 2); 
		assertEquals(annotationInAnnotation.getLevel(), 3); 
		assertEquals(annotationOutsideCore.getLevel(), 1); 
		
		assertEquals(annotationContainingCore.getParent(), hierarchy.getRootObject()); 
		assertEquals(annotationInCore.getParent(), annotationContainingCore); 
		assertEquals(annotationInAnnotation.getParent(), annotationInCore); 
		assertEquals(annotationOutsideCore.getParent(), hierarchy.getRootObject()); 
		
		for (var entry : mapDetections.entrySet()) {
			assertEquals(entry.getKey().getParent(), entry.getValue()); 
		}

	}
	
	
}

// Helper classes for testing

class PO_hlistener implements PathObjectHierarchyListener {
	private int firedState = 0;  
	
	public int getFiredState() {
		return firedState;
	}
	
	public void setFiredState(int state) {
		this.firedState = state;
	}

	@Override
	public void hierarchyChanged(PathObjectHierarchyEvent event) {
		if (event.getEventType() == PathObjectHierarchyEvent.HierarchyEventType.ADDED)
			//System.out.println("Added!");
			this.firedState = 1; 
		else if (event.getEventType() == PathObjectHierarchyEvent.HierarchyEventType.REMOVED)
			//System.out.println("Removed!");
			this.firedState = 2;
		else if (event.getEventType() == PathObjectHierarchyEvent.HierarchyEventType.OTHER_STRUCTURE_CHANGE)
			//System.out.println("Other!");
			this.firedState = 3;
	}

}

