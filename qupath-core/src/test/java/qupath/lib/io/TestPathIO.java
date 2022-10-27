/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2022 QuPath developers, The University of Edinburgh
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

package qupath.lib.io;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.roi.ROIs;

@SuppressWarnings("javadoc")
public class TestPathIO {
	
	@Test
	public void test_unzippedExtensions() throws IOException {
		
		var tmpDir = Files.createTempDirectory("anything");
		assertTrue(PathIO.unzippedExtensions(tmpDir).isEmpty());
		Files.deleteIfExists(tmpDir);
		
		var tmpFile = Files.createTempFile("anything", ".tmp");
		assertEquals(
				Collections.singleton(".tmp"),
				PathIO.unzippedExtensions(tmpFile));
		Files.deleteIfExists(tmpFile);
		
		for (var zipExtension : Arrays.asList(".zip", ".jar", ".ZIP", ".ext")) {
			var tmpZipFile = Files.createTempFile("anything", zipExtension);
			try (var zos = new ZipOutputStream(Files.newOutputStream(tmpZipFile))) {
				var tmpEntry = new ZipEntry("something.txt");
				zos.putNextEntry(tmpEntry);
				var tmpEntry2 = new ZipEntry("something/else.json");
				zos.putNextEntry(tmpEntry2);
				var tmpEntry3 = new ZipEntry("something/else-again.json");
				zos.putNextEntry(tmpEntry3);
			}
			assertEquals(
					Set.of(".txt", ".json"),
					PathIO.unzippedExtensions(tmpZipFile, zipExtension));
			assertEquals(
					Set.of(".txt", ".json"),
					PathIO.unzippedExtensions(tmpZipFile, ".zip", ".jar", ".ext"));
			assertEquals(
					Set.of(zipExtension.toLowerCase()),
					PathIO.unzippedExtensions(tmpZipFile, ".another"));
			if (".zip".equals(zipExtension.toLowerCase())) {
				assertEquals(
						Set.of(".txt", ".json"),
						PathIO.unzippedExtensions(tmpZipFile));				
			} else {
				assertEquals(
						Set.of(zipExtension.toLowerCase()),
						PathIO.unzippedExtensions(tmpZipFile, ".another"));				
			}
			Files.deleteIfExists(tmpZipFile);
		}

	}
	
	@Test
	public void test_deserialization() {
		
		var roi = ROIs.createEmptyROI();
		var pathClass = PathClass.getInstance("Anything");
		var pathObject = PathObjects.createAnnotationObject(roi, pathClass);
		var hierarchy = new PathObjectHierarchy();
		
		var mapValid = new HashMap<>();
		mapValid.put("Something", "else");
		
		// These shouldn't work with the filterd ObjectInputStream, because they use serialized non-QuPath classes
		var geometry = roi.getGeometry();

		var mapInvalid = new HashMap<>();
		mapInvalid.put("Something", geometry);

		// With a standard ObjectInputStream, everything should work
		assertEquals(roi.getClass(), serializeDeserializeStandard(roi).getClass());
		assertEquals(pathClass.getClass(), serializeDeserializeStandard(pathClass).getClass());
		assertEquals(pathObject.getClass(), serializeDeserializeStandard(pathObject).getClass());
		assertEquals(hierarchy.getClass(), serializeDeserializeStandard(hierarchy).getClass());

		assertEquals(mapValid.getClass(), serializeDeserializeStandard(mapValid).getClass());
		assertEquals(geometry.getClass(), serializeDeserializeStandard(geometry).getClass());
		assertEquals(mapInvalid.getClass(), serializeDeserializeStandard(mapInvalid).getClass());
		
		// With a filtered ObjectInputStream, only some should work
		assertEquals(roi.getClass(), serializeDeserializeFiltered(roi).getClass());
		assertEquals(pathClass.getClass(), serializeDeserializeFiltered(pathClass).getClass());
		assertEquals(pathObject.getClass(), serializeDeserializeFiltered(pathObject).getClass());
		assertEquals(hierarchy.getClass(), serializeDeserializeFiltered(hierarchy).getClass());

		assertEquals(mapValid.getClass(), serializeDeserializeFiltered(mapValid).getClass());
		assertThrows(RuntimeException.class, () -> serializeDeserializeFiltered(geometry));
		assertThrows(RuntimeException.class, () -> serializeDeserializeFiltered(mapInvalid));

	}
	
	private static <T> T serializeDeserializeStandard(T obj) {
		try {
			var bytesOut = new ByteArrayOutputStream();
			try (var stream = new ObjectOutputStream(bytesOut)) {
				stream.writeObject(obj);
			}
			var bytesIn = new ByteArrayInputStream(bytesOut.toByteArray());
			try (var stream = new ObjectInputStream(bytesIn)) {
					return (T)stream.readObject();
			}
		} catch (ClassNotFoundException | IOException e) {
			fail(e);
			return null;
		}
	}
	
	private static <T> T serializeDeserializeFiltered(T obj) {
		try {
			var bytesOut = new ByteArrayOutputStream();
			try (var stream = new ObjectOutputStream(bytesOut)) {
				stream.writeObject(obj);
			}
			var bytesIn = new ByteArrayInputStream(bytesOut.toByteArray());
			try (var stream = PathIO.createObjectInputStream(bytesIn)) {
				return (T)stream.readObject();
			}
		} catch (InvalidClassException e) {
			throw new RuntimeException(e);
		} catch (ClassNotFoundException | IOException e) {
			fail(e);
			return null;
		}
	}
	
}
