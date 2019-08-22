package qupath.lib.images.servers;

/**
 * Image bit-depths and types. Not all may be well-supported; in general, 
 * expected image types are UINT8, UINT16 and FLOAT32.
 * 
 * @author Pete Bankhead
 */
public enum PixelType {
	
	/**
	 * 8-bit unsigned integer
	 */
	UINT8(8, PixelValueType.UNSIGNED_INTEGER),
	/**
	 * 8-bit signed integer
	 */
	INT8(8, PixelValueType.SIGNED_INTEGER),
	/**
	 * 16-bit unsigned integer
	 */
	UINT16(16, PixelValueType.UNSIGNED_INTEGER),
	/**
	 * 16-bit signed integer
	 */
	INT16(16, PixelValueType.SIGNED_INTEGER),
	/**
	 * 32-bit unsigned integer (not supported by BufferedImage)
	 */
	UINT32(32, PixelValueType.UNSIGNED_INTEGER),
	/**
	 * 32-bit signed integer
	 */
	INT32(32, PixelValueType.SIGNED_INTEGER),
	/**
	 * 32-bit floating point
	 */
	FLOAT32(32, PixelValueType.FLOATING_POINT),
	/**
	 * 64-bit floating point
	 */
	FLOAT64(64, PixelValueType.FLOATING_POINT);
		
	private int bitsPerPixel;
	private PixelValueType type;
	
	private PixelType(int bpp, PixelValueType type) {
		this.bitsPerPixel = bpp;
		this.type = type;
	}
	
	/**
	 * Number of bits per pixel.
	 * @return
	 * 
	 * @see #getBytesPerPixel()
	 */
	public int getBitsPerPixel() {
		return bitsPerPixel;
	}
	
	/**
	 * Number of bytes per pixel.
	 * @return
	 * 
	 * @see #getBitsPerPixel()
	 */
	public int getBytesPerPixel() {
		return (int)Math.ceil(bitsPerPixel / 8.0);
	}
	
	/**
	 * Returns true if the type is a signed integer representation.
	 * @return
	 */
	public boolean isSignedInteger() {
		return type == PixelValueType.SIGNED_INTEGER;
	}
	
	/**
	 * Returns true if the type is an unsigned integer representation.
	 * @return
	 */
	public boolean isUnsignedInteger() {
		return type == PixelValueType.UNSIGNED_INTEGER;
	}
	
	/**
	 * Returns true if the type is a floating point representation.
	 * @return
	 */
	public boolean isFloatingPoint() {
		return type == PixelValueType.FLOATING_POINT;
	}
	
	private enum PixelValueType {
		SIGNED_INTEGER, UNSIGNED_INTEGER, FLOATING_POINT;
	}

}
