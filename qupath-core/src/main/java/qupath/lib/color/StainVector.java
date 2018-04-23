/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.color;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Locale;
import java.util.Locale.Category;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.ColorTools;
import qupath.lib.common.GeneralTools;

/**
 * Representation of a color deconvolution stain vector, defined in terms of RGB optical densities.
 * 
 * @author Pete Bankhead
 *
 */
public class StainVector implements Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	final private static Logger logger = LoggerFactory.getLogger(StainVector.class);
	
	public enum DEFAULT_STAINS {HEMATOXYLIN("Hematoxylin"), EOSIN("Eosin"), DAB("DAB");
		private String name;
		DEFAULT_STAINS(String name) {
			this.name = name;
		};
		@Override
		public String toString() {
			return name;
		}
	}
	
	static int version = 1;
	
	private double r, g, b;
	private String name;
	private boolean isResidual = false;

	private static final double[] STAIN_HEMATOXYLIN_DEFAULT = new double[]{0.65, 0.70, 0.29}; 	// From Ruifrok & Johnston's original paper
	private static final double[] STAIN_DAB_DEFAULT = new double[]{0.27, 0.57, 0.78};			// From Ruifrok & Johnston's original paper
	private static final double[] STAIN_EOSIN_DEFAULT = new double[]{0.2159, 0.8012, 0.5581}; 	// From http://amida13.isi.uu.nl/?q=node/69

//	private static final double[] STAIN_EOSIN_DEFAULT = new double[]{0.07, 0.99, 0.11}; 		// From Ruifrok & Johnston's original paper

//	public static Pattern pattern = Pattern.compile( "[-+]?\\d*\\.?\\d+([eE][-+]?\\d+)?" );
	
	
	public static StainVector makeDefaultStainVector(DEFAULT_STAINS stain) {
		switch(stain) {
		case HEMATOXYLIN:
			return new StainVector(stain.toString(), STAIN_HEMATOXYLIN_DEFAULT);
		case EOSIN:
			return new StainVector(stain.toString(), STAIN_EOSIN_DEFAULT);
		case DAB:
			return new StainVector(stain.toString(), STAIN_DAB_DEFAULT);
		}
		return null;
	}
	
	
	public StainVector() {}
	
	
	public StainVector(String name, double[] vector, boolean isResidual) {
		this(name, vector[0], vector[1], vector[2], isResidual);
	}

	public StainVector(String name, double[] vector) {
		this(name, vector[0], vector[1], vector[2]);
	}

	public StainVector(String name, double r, double g, double b) {
		this(name, r, g, b, false);
	}

	public StainVector(String name, double r, double g, double b, boolean isResidual) {
		setName(name);
		setStain(r, g, b);
		this.isResidual = isResidual;
	}

	public boolean isResidual() {
		return isResidual;
	}
	
	public String getName() {
		return name;
	}
	
	private static double getLength(double r, double g, double b) {
		return Math.sqrt(r*r + g*g + b*b);
	}
	
	public double getRed() {
		return this.r;
	}

	public double getGreen() {
		return this.g;
	}

	public double getBlue() {
		return this.b;
	}
	
	private void setName(String name) {
		this.name = name;
	}

//	private void setStain(StainVector stain) {
//		setStain(stain.getRed(), stain.getGreen(), stain.getBlue());
//	}
	
	private void setStain(double r, double g, double b) {
		double length = getLength(r, g, b);
		if (length <= 0)
			throw new IllegalArgumentException("Stain vector is not valid - must have a length > 0");

		this.r = r / length;
		this.g = g / length;
		this.b = b / length;
	}
	
	public double[] getArray() {
		return new double[]{r, g, b};
	}
	
	public static int clip255(double val) {
		return (int)Math.min(255, Math.max(val, 0));
	}
	
	/**
	 * Get a Color that (roughly) corresponds to color represented by this stain vector.
	 * It may be used to create a color lookup table.
	 * 
	 * @return
	 */
	public int getColor() {
		int r2 = clip255(255.0 - r * 255);
		int g2 = clip255(255.0 - g * 255);
		int b2 = clip255(255.0 - b * 255);
		return ColorTools.makeRGB(r2, g2, b2);
	}
	
	// This seems to produce visually more similar colors for single stains...
	// but it's hard to get a suitable scaling factor (here 2) for normalized stain view across whole image using a similar method for mixed stains
	// (the contrast is very low)
//	public Color getColor() {
//		if (!isValid())
//			return null;
////		int r2 = clip255(255.0 - r * 255);
////		int g2 = clip255(255.0 - g * 255);
////		int b2 = clip255(255.0 - b * 255);
//		int r2 = clip255(Math.exp(-r * 2) * 255);
//		int g2 = clip255(Math.exp(-g * 2) * 255);
//		int b2 = clip255(Math.exp(-b * 2) * 255);
//		return new Color(r2, g2, b2);
//	}

//	/*
//	 * Create an 8-bit LUT for the stain color.
//	 * This maps the color from getColor() to the value 127 and and white to 0; linear RGB interpolation is used for the other colors.
//	 */
//	public LUT getLUT(boolean whiteBackground) {
//		if (!isValid())
//			return LUT.createLutFromColor(Color.WHITE);
//		else if (!whiteBackground)
//			return LUT.createLutFromColor(getColor());
//		byte[] r2 = new byte[256];
//		byte[] g2 = new byte[256];
//		byte[] b2 = new byte[256];
//		for (int i = 0; i < 256; i++) {
//			r2[i] = (byte)clip255(255 - (255 - Math.exp(-r) * 255)/128 * i);
//			g2[i] = (byte)clip255(255 - (255 - Math.exp(-g) * 255)/128 * i);
//			b2[i] = (byte)clip255(255 - (255 - Math.exp(-b) * 255)/128 * i);
//		}
//		return new LUT(r2, g2, b2);		
//	}
	
	
	public String arrayAsString(final Locale locale, final String delimiter, final int nDecimalPlaces) {
		return GeneralTools.arrayToString(locale, new double[]{r, g, b}, delimiter, nDecimalPlaces);
//		return String.format( "%.Nf%s%.Nf%s%.Nf".replace("N", Integer.toString(nDecimalPlaces)), r, delimiter, g, delimiter, b);
//		return String.format( "%.Nf, %.Nf, %.Nf".replace("N", Integer.toString(nDecimalPlaces)), r, g, b );
//		return String.format( "[%.Nf, %.Nf, %.Nf]".replace("N", Integer.toString(nDecimalPlaces)), r, g, b );
//		return "[" + IJ.d2s(r, nDecimalPlaces) + ", " + IJ.d2s(g, nDecimalPlaces) + ", " + IJ.d2s(b, nDecimalPlaces) + "]";
	}
	
	
	public String arrayAsString(final Locale locale, final int nDecimalPlaces) {
		return GeneralTools.arrayToString(locale, new double[]{r, g, b}, nDecimalPlaces);
//		return String.format( "%.Nf %.Nf %.Nf".replace("N", Integer.toString(nDecimalPlaces)), r, g, b );
//		return String.format( "%.Nf, %.Nf, %.Nf".replace("N", Integer.toString(nDecimalPlaces)), r, g, b );
//		return String.format( "[%.Nf, %.Nf, %.Nf]".replace("N", Integer.toString(nDecimalPlaces)), r, g, b );
//		return "[" + IJ.d2s(r, nDecimalPlaces) + ", " + IJ.d2s(g, nDecimalPlaces) + ", " + IJ.d2s(b, nDecimalPlaces) + "]";
	}
	
	public String arrayAsString(final Locale locale) {
		return arrayAsString(locale, 3);
	}
	
	@Override
	public String toString() {
		return name + ": " + arrayAsString(Locale.getDefault(Category.FORMAT));
	}
	
	
	/**
	 * Convert to a unit vector
	 * @param vec
	 */
	public static void normalizeVector(double[] vec) {
		double len = vectorLength(vec);
		for (int i = 0; i < vec.length; i++)
			vec[i] /= len;
	}

	/**
	 * Euclidean length of a vector
	 * @param vec
	 * @return
	 */
	public static double vectorLength(double[] vec) {
		double len = 0;
		for (double v : vec)
			len += (v * v);
		return Math.sqrt(len);
	}

//	private static StainVector parseStainVector(String s) {
//		return parseStainVector(null, s);
//	}

	public static double computeAngle(StainVector s1, StainVector s2) {
		double[] v1 = s1.getArray();
		double[] v2 = s2.getArray();
		if (v1 == null || v2 == null)
			return Double.NaN;
		double n1 = 0, n2 = 0, dot = 0;
		for (int i = 0; i < v1.length; i++) {
			n1 += v1[i]*v1[i];
			n2 += v2[i]*v2[i];
			dot += v1[i]*v2[i];
		}
		if (Math.abs(1 - dot) < 0.00001)
			return 0;
		if (Math.abs(1 - Math.sqrt(n1) * Math.sqrt(n2)) < 0.001)
			return Math.acos(dot) / Math.PI * 180;
		return Math.acos(dot / (Math.sqrt(n1) * Math.sqrt(n2))) / Math.PI * 180;

		
//		double val = Math.acos(dot / (Math.sqrt(n1) * Math.sqrt(n2))) / Math.PI * 180;
//		if (Double.isNaN(val)) {
//			System.out.println(dot / (Math.sqrt(n1) * Math.sqrt(n2)));
//			System.out.println(s1 + ", " + s2);
////			System.out.println(s2);
//		}
////		System.out.println(val);
//		return Math.acos(dot / (Math.sqrt(n1) * Math.sqrt(n2))) / Math.PI * 180;
	}
	
	
	
	/**
	 * Compute cross product of two vectors
	 * @param u
	 * @param v
	 * @return
	 */
	public static double[] cross3(double[] u, double[] v) {
		double[] s = new double[3];
		s[0] = (u[1]*v[2] - u[2]*v[1]);
		s[1] = (u[2]*v[0] - u[0]*v[2]);
		s[2] = (u[0]*v[1] - u[1]*v[0]);
		return s;
	}
	
	/**
	 * Make a 'residual' stain vector, i.e. a third stain vector orthogonal to two specified vectors.
	 * 
	 * @param s1
	 * @param s2
	 * @return
	 */
	public static StainVector makeResidualStainVector(StainVector s1, StainVector s2) {
		return makeOrthogonalStainVector("Residual", s1, s2, true);
	}

	
	public static StainVector makeOrthogonalStainVector(String name, StainVector s1, StainVector s2, boolean isResidual) {
		return new StainVector(name, cross3(s1.getArray(), s2.getArray()), isResidual);
	}
	

	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(version);
		out.writeUTF(name);
		out.writeDouble(r);
		out.writeDouble(g);
		out.writeDouble(b);
		out.writeBoolean(isResidual);
	}


	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int version = in.readInt();
		if (version != 1)
			logger.error(getClass().getSimpleName() + " unsupported version number " + version);
		name = in.readUTF();
		r = in.readDouble();
		g = in.readDouble();
		b = in.readDouble();
		isResidual = in.readBoolean();
	}
	

}
