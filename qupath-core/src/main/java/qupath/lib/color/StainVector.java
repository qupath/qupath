/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
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
	
	/**
	 * Enum representing default stains.
	 * <p>
	 * TODO: Replace with interface, add stain vectors directly
	 */
	public enum DefaultStains {
		/**
		 * Hematoxylin
		 */
		HEMATOXYLIN("Hematoxylin"),
		/**
		 * Eosin
		 */
		EOSIN("Eosin"),
		/**
		 * DAB
		 */
		DAB("DAB");
		
		private String name;
		DefaultStains(String name) {
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
	
	
	/**
	 * Get a default stain vector.
	 * @param stain
	 * @return
	 */
	public static StainVector makeDefaultStainVector(DefaultStains stain) {
		switch(stain) {
		case HEMATOXYLIN:
			return createStainVector(stain.toString(), STAIN_HEMATOXYLIN_DEFAULT, false);
		case EOSIN:
			return createStainVector(stain.toString(), STAIN_EOSIN_DEFAULT, false);
		case DAB:
			return createStainVector(stain.toString(), STAIN_DAB_DEFAULT, false);
		}
		return null;
	}
	
	/**
	 * Default constructor, required for {@link Externalizable} interface.
	 */
	public StainVector() {}
	
	
	static StainVector createStainVector(String name, double[] vector, boolean isResidual) {
		return new StainVector(name, vector[0], vector[1], vector[2], isResidual);
	}

	/**
	 * Create a stain vector.
	 * @param name the name of the stain
	 * @param r the stain vector red component
	 * @param g the stain vector green component
	 * @param b the stain vector blue component
	 * @return
	 */
	public static StainVector createStainVector(String name, double r, double g, double b) {
		return new StainVector(name, r, g, b, false);
	}

	StainVector(String name, double r, double g, double b, boolean isResidual) {
		setName(name);
		setStain(r, g, b);
		this.isResidual = isResidual;
	}
	
//	public static StainVector createResidualStainVector(String name, double r, double g, double b) {
//		return new StainVector(name, r, g, b, true);
//	}

	/**
	 * Returns true if this vector represents the residual (orthogonal) stain, used whenever color deconvolution is required with two stains only.
	 * @return
	 */
	public boolean isResidual() {
		return isResidual;
	}
	
	/**
	 * Returns the name of the stain vector.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	private static double getLength(double r, double g, double b) {
		return Math.sqrt(r*r + g*g + b*b);
	}
	
	/**
	 * Get the red component of the (normalized) stain vector.
	 * @return
	 */
	public double getRed() {
		return this.r;
	}

	/**
	 * Get the green component of the (normalized) stain vector.
	 * @return
	 */
	public double getGreen() {
		return this.g;
	}

	/**
	 * Get the blue component of the (normalized) stain vector.
	 * @return
	 */
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
	
	/**
	 * Get the stain vector as a 3 element array (red, green, blue).
	 * @return
	 */
	public double[] getArray() {
		return new double[]{r, g, b};
	}
	
	/**
	 * Get a Color that (roughly) corresponds to color represented by this stain vector.
	 * It may be used to create a color lookup table.
	 * 
	 * @return
	 */
	public int getColor() {
		int r2 = ColorTools.clip255(255.0 - r * 255);
		int g2 = ColorTools.clip255(255.0 - g * 255);
		int b2 = ColorTools.clip255(255.0 - b * 255);
		return ColorTools.makeRGB(r2, g2, b2);
	}
		
	
	String arrayAsString(final Locale locale, final int nDecimalPlaces) {
		return GeneralTools.arrayToString(locale, new double[]{r, g, b}, nDecimalPlaces);
//		return String.format( "%.Nf %.Nf %.Nf".replace("N", Integer.toString(nDecimalPlaces)), r, g, b );
//		return String.format( "%.Nf, %.Nf, %.Nf".replace("N", Integer.toString(nDecimalPlaces)), r, g, b );
//		return String.format( "[%.Nf, %.Nf, %.Nf]".replace("N", Integer.toString(nDecimalPlaces)), r, g, b );
//		return "[" + IJ.d2s(r, nDecimalPlaces) + ", " + IJ.d2s(g, nDecimalPlaces) + ", " + IJ.d2s(b, nDecimalPlaces) + "]";
	}
	
	/**
	 * Get a String representation of the stain vector array, formatting according to the specified Locale.
	 * @param locale
	 * @return
	 */
	public String arrayAsString(final Locale locale) {
		return arrayAsString(locale, 3);
	}
	
	@Override
	public String toString() {
		return name + ": " + arrayAsString(Locale.getDefault(Category.FORMAT));
	}

//	private static StainVector parseStainVector(String s) {
//		return parseStainVector(null, s);
//	}

	/**
	 * Calculate the angle between two stain vectors, in degrees.
	 * @param s1
	 * @param s2
	 * @return
	 */
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
	 * Compute the cross product of two vectors.
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
	static StainVector makeResidualStainVector(StainVector s1, StainVector s2) {
		return makeOrthogonalStainVector("Residual", s1, s2, true);
	}

	
	static StainVector makeOrthogonalStainVector(String name, StainVector s1, StainVector s2, boolean isResidual) {
		return createStainVector(name, cross3(s1.getArray(), s2.getArray()), isResidual);
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
