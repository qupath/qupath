package qupath.lib.regions;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

@SuppressWarnings("javadoc")
public class TestImageRequest {

	@Test
	public void testImageRegions() {
		
		var region = ImageRegion.createInstance(0, 0, 1024, 1024, 0, 0);
		
		assertTrue(region.contains(0, 0, 0, 0));
		assertTrue(region.contains(100, 0, 0, 0));
		assertTrue(region.contains(0, 100, 0, 0));
		assertTrue(region.contains(1023, 1023, 0, 0));
		
		assertFalse(region.contains(0, 0, 1, 0));
		assertFalse(region.contains(0, 0, 0, 1));
		assertFalse(region.contains(1023, 1024, 0, 0));
		assertFalse(region.contains(1024, 1023, 0, 0));
		assertFalse(region.contains(1024, 1024, 0, 0));
		
		assertTrue(region.intersects(region));
		
		assertFalse(region.intersects(ImageRegion.createInstance(0, 0, 1024, 1024, 1, 0)));
		assertFalse(region.intersects(ImageRegion.createInstance(0, 0, 1024, 1024, 0, 1)));
		
		assertTrue(region.intersects(-10, -10, 50, 50));
		assertTrue(region.intersects(1023, 1023, 50, 50));
		assertFalse(region.intersects(-50, -50, 50, 50));
		assertFalse(region.intersects(1024, 1024, 50, 50));
		assertFalse(region.intersects(1023, 1024, 50, 50));
		assertFalse(region.intersects(1024, 1023, 50, 50));
		assertFalse(region.intersects(-50, 0, 50, 50));
		assertFalse(region.intersects(0, -50, 50, 50));
		
		assertFalse(region.intersects(4096, 4096, 50, 50));
		
		var region2 = ImageRegion.createInstance(8192, 16384, 512, 512, 0, 0);
		assertFalse(region2.intersects(12287, 16044, 228, 237));

		
	}

}
