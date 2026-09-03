/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2026 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.commands;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServers;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.Projects;
import qupath.lib.regions.ImagePlane;
import qupath.lib.roi.ROIs;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests verifying that importing from another .qpproj respects the importObjects flag.
 */
public class TestProjectImportObjects {

	@TempDir
	Path tempDir;

	private File imageFile;
	private File projectADir;
	private Project<BufferedImage> projectA;

	@BeforeEach
	public void setUp() throws Exception {
		// Create a test image file
		imageFile = tempDir.resolve("test_image.png").toFile();
		BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
		ImageIO.write(img, "png", imageFile);

		// Create Source Project A
		projectADir = tempDir.resolve("ProjectA").toFile();
		projectADir.mkdirs();
		projectA = Projects.createProject(projectADir, BufferedImage.class);

		var server = ImageServers.buildServer(imageFile.toURI());
		var entryA = projectA.addImage(server.getBuilder());
		
		var imageDataA = new ImageData<>(server, ImageData.ImageType.BRIGHTFIELD_H_DAB);
		var roi1 = ROIs.createRectangleROI(10, 10, 20, 20, ImagePlane.getDefaultPlane());
		var roi2 = ROIs.createRectangleROI(40, 40, 30, 30, ImagePlane.getDefaultPlane());
		var annot1 = PathObjects.createAnnotationObject(roi1, PathClass.StandardPathClasses.TUMOR);
		var annot2 = PathObjects.createAnnotationObject(roi2, PathClass.StandardPathClasses.STROMA);
		imageDataA.getHierarchy().addObject(annot1);
		imageDataA.getHierarchy().addObject(annot2);
		
		entryA.saveImageData(imageDataA);
		projectA.syncChanges();
	}

	@Test
	public void testImportProjectWithoutObjects() throws Exception {
		// Create Destination Project B
		File projectBDir = tempDir.resolve("ProjectB_NoObjects").toFile();
		projectBDir.mkdirs();
		Project<BufferedImage> projectB = Projects.createProject(projectBDir, BufferedImage.class);

		// Load project A entries
		var loadedProjectA = ProjectIO.loadProject(projectA.getPath().toUri(), BufferedImage.class);
		var projectImages = loadedProjectA.getImageList();
		assertEquals(1, projectImages.size());

		boolean importObjects = false;
		for (var temp : projectImages) {
			if (importObjects) {
				projectB.addDuplicate(temp, true);
			} else {
				var newEntry = projectB.addDuplicate(temp, false);
				if (temp.hasImageData()) {
					var imageData = temp.readImageData();
					if (imageData != null) {
						imageData.getHierarchy().clearAll();
						newEntry.saveImageData(imageData);
					}
				}
			}
		}
		projectB.syncChanges();

		assertEquals(1, projectB.getImageList().size());
		var entryB = projectB.getImageList().get(0);
		assertTrue(entryB.hasImageData());
		
		var imageDataB = entryB.readImageData();
		assertNotNull(imageDataB);
		assertEquals(ImageData.ImageType.BRIGHTFIELD_H_DAB, imageDataB.getImageType());
		// Verify NO annotations were imported
		assertEquals(0, imageDataB.getHierarchy().getAnnotationObjects().size());
		assertEquals(0, imageDataB.getHierarchy().getAllObjects(false).size());
	}

	@Test
	public void testImportProjectWithObjects() throws Exception {
		// Create Destination Project C
		File projectCDir = tempDir.resolve("ProjectC_WithObjects").toFile();
		projectCDir.mkdirs();
		Project<BufferedImage> projectC = Projects.createProject(projectCDir, BufferedImage.class);

		// Load project A entries
		var loadedProjectA = ProjectIO.loadProject(projectA.getPath().toUri(), BufferedImage.class);
		var projectImages = loadedProjectA.getImageList();
		assertEquals(1, projectImages.size());

		boolean importObjects = true;
		for (var temp : projectImages) {
			if (importObjects) {
				projectC.addDuplicate(temp, true);
			} else {
				var newEntry = projectC.addDuplicate(temp, false);
				if (temp.hasImageData()) {
					var imageData = temp.readImageData();
					if (imageData != null) {
						imageData.getHierarchy().clearAll();
						newEntry.saveImageData(imageData);
					}
				}
			}
		}
		projectC.syncChanges();

		assertEquals(1, projectC.getImageList().size());
		var entryC = projectC.getImageList().get(0);
		assertTrue(entryC.hasImageData());
		
		var imageDataC = entryC.readImageData();
		assertNotNull(imageDataC);
		assertEquals(ImageData.ImageType.BRIGHTFIELD_H_DAB, imageDataC.getImageType());
		// Verify ALL annotations were imported
		assertEquals(2, imageDataC.getHierarchy().getAnnotationObjects().size());
	}
}