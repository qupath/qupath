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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class PathIOTest {
	
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
		
		for (var zipExtension : Arrays.asList(".zip", ".jar")) {
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
					PathIO.unzippedExtensions(tmpZipFile));
			Files.deleteIfExists(tmpZipFile);
		}

	}

	
}
