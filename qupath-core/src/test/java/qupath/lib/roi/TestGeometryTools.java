package qupath.lib.roi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.awt.geom.AffineTransform;

import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.util.AffineTransformation;

/**
 * Test {@link GeometryTools}. Note that most of the relevant tests for ROI conversion are in {@link TestROIs}.
 */
public class TestGeometryTools {
	
	/**
	 * Compare conversion of {@link AffineTransform} and {@link AffineTransformation} objects.
	 */
	@Test
	public void testAffineTransforms() {
		
		double[] source = new double[] {1.3, 2.7};
		
		double[] destTransform = new double[source.length];
		var transform = AffineTransform.getScaleInstance(2.0, 0.5);
		transform.rotate(0.5);
		transform.translate(-20, 42);
		transform.transform(source, 0, destTransform, 0, source.length/2);
		
		var transformation = GeometryTools.convertTransform(transform);
		var c = new Coordinate(source[0], source[1]);
		transformation.transform(c, c);
		double[] destTransformation = new double[] {c.x, c.y};
		
		var transformBack = GeometryTools.convertTransform(transformation);
		double[] matBefore = new double[6];
		double[] matAfter = new double[6];
		transform.getMatrix(matBefore);
		transformBack.getMatrix(matAfter);
		
		assertArrayEquals(destTransform, destTransformation, 0.001);
		assertArrayEquals(matBefore, matAfter, 0.001);
		
	}
	

}
