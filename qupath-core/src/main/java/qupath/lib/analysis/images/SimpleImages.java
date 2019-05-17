package qupath.lib.analysis.images;

/**
 * Create {@link SimpleImage SimpleImage} instances for basic pixel processing.
 * 
 * @author Pete Bankhead
 *
 */
public class SimpleImages {
	
	/**
	 * Create a {@link SimpleImage} backed by an existing float array of pixels.
	 * <p>
	 * Pixels are stored in row-major order.
	 * 
	 * @param data
	 * @param width
	 * @param height
	 * @return
	 */
	public static SimpleModifiableImage createFloatImage(float[] data, int width, int height) {
		return new FloatArraySimpleImage(data, width, height);
	}

	/**
	 * Create a {@link SimpleImage} backed by a float array of pixels.
	 *
	 * @param width
	 * @param height
	 * @return
	 */
	public static SimpleModifiableImage createFloatImage(int width, int height) {
		return new FloatArraySimpleImage(new float[width * height], width, height);
	}
	
	
	/**
	 * Implementation of a SimpleImage backed by an array of floats.
	 * 
	 * @author Pete Bankhead
	 *
	 */
	static class FloatArraySimpleImage implements SimpleModifiableImage {

		private float[] data;
		private int width;
		private int height;
		
		public FloatArraySimpleImage(float[] data, int width, int height) {
			this.data = data;
			this.width = width;
			this.height = height;
		}
		
		public FloatArraySimpleImage(int width, int height) {
			this.data = new float[width * height];
			this.width = width;
			this.height = height;
		}
		
		@Override
		public float getValue(int x, int y) {
			return data[y * width + x];
		}

		@Override
		public void setValue(int x, int y, float val) {
			data[y * width + x] = val;
		}

		@Override
		public int getWidth() {
			return width;
		}

		@Override
		public int getHeight() {
			return height;
		}

	}
}
