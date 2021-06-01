/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.imagej.images.writers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

import ij.IJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.plugin.ImageCalculator;
import ij.process.StackStatistics;

@SuppressWarnings("javadoc")
public class TestImageWriterIJ {

	@Test
	public void testTiffAndZip() {
		var imp = IJ.createImage("Temp", 128, 256, 4, 32);
		// Need to call without using a macro, to avoid java.awt.HeadlessException 
		for (int s = 1; s < imp.getStackSize(); s++)
			imp.getStack().getProcessor(s).noise(500);
//		IJ.run(imp, "Add Specified Noise...", "standard=500 stack");
		testTiff(imp);
		testZip(imp);
	}
	
	void testTiff(ImagePlus imp) {
		// Check if we can write an in-memory TIFF
		var writer = new TiffWriterIJ();
		byte[] bytes;
		try (var stream = new ByteArrayOutputStream()) {
			writer.writeImage(imp, stream);
			bytes = stream.toByteArray();
		} catch (IOException e) {
			fail("Error writing to byte array: " + e.getLocalizedMessage());
			return;
		}
		var impRead = new Opener().deserialize(bytes);
		compareImages(imp, impRead);
	}
	

	void testZip(ImagePlus imp) {
		// Check if we can write an in-memory ZIP
		var writer = new ZipWriterIJ();
		byte[] bytes;
		try (var stream = new ByteArrayOutputStream()) {
			writer.writeImage(imp, stream);
			bytes = stream.toByteArray();
		} catch (IOException e) {
			fail("Error writing to byte array: " + e.getLocalizedMessage());
			return;
		}
		
		try (var stream = new ByteArrayInputStream(bytes)) {
			var zis = new ZipInputStream(stream);
			zis.getNextEntry();
			bytes = zis.readAllBytes();
		} catch (IOException e) {
			fail("Error reading from byte array: " + e.getLocalizedMessage());
			return;
		}
		var impRead = new Opener().deserialize(bytes);
		compareImages(imp, impRead);
	}
	
	
	static void compareImages(ImagePlus impOrig, ImagePlus impRead) {
		assertEquals(impOrig.getType(), impRead.getType());
		var impDifference = new ImageCalculator().run("Difference create stack", impOrig, impRead);
		var stats = new StackStatistics(impDifference);
		assertEquals(stats.max, 0.0, 0.0);
		assertEquals(stats.min, 0.0, 0.0);
	}
	
}