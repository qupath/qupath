package qupath.opencv.processor;

public class Padding {
	
	private int x1, x2, y1, y2;
	
	public int getX1() {
		return x1;
	}

	public int getX2() {
		return x2;
	}
	
	public int getXSum() {
		return getX1() + getX2();
	}
	
	public int getY1() {
		return y1;
	}

	public int getY2() {
		return y2;
	}
	
	@Override
	public String toString() {
		return String.format(
				"Padding (x=[%d, %d], y=[%d, %d])",
				getX1(), getX2(), getY1(), getY2()
				);
	}

	public int getYSum() {
		return getY1() + getY2();
	}

	public boolean isSymmetric() {
		return x1 == x2 && x2 == y1 && y1 == y2;
	}
	
	public boolean isEmpty() {
		return x1 == 0 && isSymmetric();
	}
	
	public Padding add(Padding padding) {
		if (isEmpty())
			return padding;
		else if (padding.isEmpty())
			return this;
		return getPadding(
				x1 + padding.x1,
				x2 + padding.x2,
				y1 + padding.y1,
				y2 + padding.y2
				);
	}
	
	public Padding max(Padding padding) {
		if (isEmpty())
			return padding;
		else if (padding.isEmpty())
			return this;
		return getPadding(
				Math.max(x1, padding.x1),
				Math.max(x2, padding.x2),
				Math.max(y1, padding.y1),
				Math.max(y2, padding.y2)
				);
	}
	
	private Padding(int x1, int x2, int y1, int y2) {
		this.x1 = x1;
		this.x2 = x2;
		this.y1 = y1;
		this.y2 = y2;
		if (x1 < 0 || x2 < 0 || y1 < 0 || y2 < 0)
			throw new IllegalArgumentException("Padding must be >= 0! Requested " + toString());
	}
	
	private Padding(int pad) {
		this(pad, pad, pad, pad);
	}
	
	private static Padding[] symmetric = new Padding[64];
	
	static {
		for (int i = 0; i < symmetric.length; i++)
			symmetric[i] = new Padding(i);
	}
	
	public static Padding symmetric(int pad) {
		if (pad <= symmetric.length)
			return symmetric[pad];
		return new Padding(pad);
	}
	
	public static Padding getPadding(int x, int y) {
		if (x == y)
			return symmetric(x);
		return getPadding(x, x, y ,y);
	}
	
	public static Padding empty() {
		return symmetric[0];
	}
	
	public static Padding getPadding(int x1, int x2, int y1, int y2) {
		if (x1 == x2 && x1 == y1 && x1 == y2)
			return symmetric(x1);
		return new Padding(x1, x2, y1, y2);
	}

}
