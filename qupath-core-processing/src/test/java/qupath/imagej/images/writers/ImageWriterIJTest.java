package qupath.imagej.images.writers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;

import org.junit.Test;

import ij.IJ;
import ij.ImagePlus;
import ij.io.Opener;
import ij.plugin.ImageCalculator;
import ij.process.StackStatistics;

@SuppressWarnings("javadoc")
public class ImageWriterIJTest {

	@Test
	public void testTiffAndZip() {
		var imp = IJ.createImage("Temp", 128, 256, 4, 32);
		IJ.run(imp, "Add Specified Noise...", "standard=500 stack");
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
