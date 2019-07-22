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
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.color.StainVector.DefaultStains;
import qupath.lib.common.GeneralTools;


/**
 * Helper class for storing stain vectors and maximum channel values to be used for color deconvolution.
 * <p>
 * The maximum channel values are the RGB values corresponding to pixels that have no staining of any kind
 * (i.e. all light has passed through... the pixel should look close to white).
 * By default, these are 255.
 * 
 * @author Pete Bankhead
 *
 */
public class ColorDeconvolutionStains implements Externalizable {
	
	private static final long serialVersionUID = 1L;
	
	final private static Logger logger = LoggerFactory.getLogger(ColorDeconvolutionStains.class);

	private static int version = 1;
	
	/**
	 * Default name (and spelling) for hematoxylin stain
	 */
	public static final String HEMATOXYLIN = "Hematoxylin";
	
	/**
	 * Default name for eosin stain
	 */
	public static final String EOSIN = "Eosin";
	
	/**
	 * Default name for DAB stain
	 */
	public static final String DAB = "DAB";
	
	private static String[] HEMATOXYLIN_SPELLINGS = {"haematoxylin", "hematoxylin", "haem", "h"};
	private static String[] EOSIN_SPELLINGS = {"eosin", "eos", "e"};
	private static String[] DAB_SPELLINGS = {"dab", "d"};
	
	/**
	 * Enum for common stain defaults.
	 * <p>
	 * TODO: Switch to using an interface and permitting alternatives
	 */
	public enum DefaultColorDeconvolutionStains {
		/**
		 * Hematoxylin and eosin
		 */
		H_E("H&E"),
		
		/**
		 * Hematoxylin and DAB
		 */
		H_DAB("H-DAB");
		
		private String name;
		
		DefaultColorDeconvolutionStains(String name) {
			this.name = name;
		};
		@Override
		public String toString() {
			return name;
		}
	}
	
//	public static enum StainType {H_E, H_DAB, OTHER};
//	
//	private StainType type;
	private String name;
	private StainVector stain1, stain2, stain3;
	private double maxRed, maxGreen, maxBlue;
	
	transient private double[][] matInverse = null;
	
	/**
	 * Create a ColorDeconvolutionStains for a default stain combination, and default max values (255 for all channels).
	 * 
	 * @param stains
	 * @return
	 */
	public static ColorDeconvolutionStains makeDefaultColorDeconvolutionStains(DefaultColorDeconvolutionStains stains) {
		switch(stains) {
		case H_E:
			return new ColorDeconvolutionStains("H&E default",
												StainVector.makeDefaultStainVector(StainVector.DefaultStains.HEMATOXYLIN),
												StainVector.makeDefaultStainVector(StainVector.DefaultStains.EOSIN),
												255, 255, 255);
		case H_DAB:
			return new ColorDeconvolutionStains("H-DAB default",
												StainVector.makeDefaultStainVector(StainVector.DefaultStains.HEMATOXYLIN),
												StainVector.makeDefaultStainVector(StainVector.DefaultStains.DAB),
												255, 255, 255);
		}
		return null;
	}
	
	
	/**
	 * Create a new ColorDeconvolutionStains object, with all settings the same except one of the stains has been changed.
	 * <p>
	 * If the third stain was a residual, it will also be regenerated (i.e. it won't remain the same residual... which would be wrong,
	 * as it would be orthogonal to the old stain rather than the new one).
	 * 
	 * @param stains
	 * @param stainNew
	 * @param stainNumber
	 * @return
	 */
	private static ColorDeconvolutionStains makeModifiedStains(ColorDeconvolutionStains stains, StainVector stainNew, int stainNumber) {
		StainVector[] stainVectors = new StainVector[3];
		stainVectors[0] = stains.getStain(1);
		stainVectors[1] = stains.getStain(2);
		stainVectors[2] = stains.getStain(3);
		stainVectors[stainNumber - 1] = stainNew;
		if (stainVectors[2].isResidual())
			stainVectors[2] = null;
		return new ColorDeconvolutionStains(stains.getName(), stainVectors[0], stainVectors[1], stainVectors[2], stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue());
	}
	
	/**
	 * Check if the name of a StainVector is "haematoxylin", "hematoxylin", "haem" or "h" (ignoring case)
	 * 
	 * @param stain
	 * @return
	 */
	public static boolean isHematoxylin(final StainVector stain) {
		String name = stain.getName();
		if (name == null)
			return false;
		name = name.toLowerCase().trim();
		for (String s : HEMATOXYLIN_SPELLINGS) {
			if (s.equals(name))
				return true;
		}		
		return false;
	}
	
	/**
	 * Check if the name of a StainVector is "eosin", "eos" or "e" (ignoring case)
	 * 
	 * @param stain
	 * @return
	 */
	public static boolean isEosin(final StainVector stain) {
		String name = stain.getName();
		if (name == null)
			return false;
		name = name.toLowerCase().trim();
		for (String s : EOSIN_SPELLINGS) {
			if (s.equals(name))
				return true;
		}		
		return false;
	}
	
	/**
	 * Check if the name of a StainVector is "dab" or "d" (ignoring case)
	 * 
	 * @param stain
	 * @return
	 */
	public static boolean isDAB(final StainVector stain) {
		String name = stain.getName();
		if (name == null)
			return false;
		name = name.toLowerCase().trim();
		for (String s : DAB_SPELLINGS) {
			if (s.equals(name))
				return true;
		}		
		return false;
	}
	
	/**
	 * Check if we have H&amp;E staining, by checking the names of the first two stains and confirming that the third stain is a residual.
	 * Note the order of the stains must be 1-Hematoxylin, 2-Eosin, 3-residual (missing)
	 * @return
	 */
	public boolean isH_E() {
		return stain3.isResidual() && isHematoxylin(stain1) && isEosin(stain2);
	}

	/**
	 * Check if we have H-DAB staining, by checking the names of the first two stains and confirming that the third stain is a residual.
	 * Note the order of the stains must be 1-Hematoxylin, 2-DAB, 3-residual (missing)
	 * @return
	 */
	public boolean isH_DAB() {
		return isHematoxylin(stain1) && isDAB(stain2); // && stain3.isResidual();
	}
	
	
	/**
	 * Create a new stains object, identical to this one but for one StainVector having been changed.
	 * 
	 * @param stainNew
	 * @param stainNumber
	 * @return
	 */
	public ColorDeconvolutionStains changeStain(StainVector stainNew, int stainNumber) {
		return makeModifiedStains(this, stainNew, stainNumber);
	}

	/**
	 * Create a new stains object with the same StainVectors but a new name.
	 * 
	 * @param name
	 * @return
	 */
	public ColorDeconvolutionStains changeName(String name) {
		return new ColorDeconvolutionStains(name, getStain(1), getStain(2), getStain(3), getMaxRed(), getMaxGreen(), getMaxBlue());
	}

	/**
	 * Create a new stains object with the same StainVectors but new max (background) values.
	 * 
	 * @param maxRed
	 * @param maxGreen
	 * @param maxBlue
	 * @return
	 */
	public ColorDeconvolutionStains changeMaxValues(double maxRed, double maxGreen, double maxBlue) {
		return new ColorDeconvolutionStains(getName(), getStain(1), getStain(2), getStain(3), maxRed, maxGreen, maxBlue);
	}

	/**
	 * Constructor for ColorDeconvolutionStains with three stain vectors.
	 * @param name
	 * @param stain1
	 * @param stain2
	 * @param stain3
	 * @param maxRed background (white) value for red channel
	 * @param maxGreen background (white) value for green channel
	 * @param maxBlue background (white) value for blue channel
	 */
	public ColorDeconvolutionStains(String name, StainVector stain1, StainVector stain2, StainVector stain3, double maxRed, double maxGreen, double maxBlue) {
		this.name = name;
		this.stain1 = stain1;
		this.stain2 = stain2;
		if (stain3 == null && stain1 != null && stain2 != null)
			this.stain3 = StainVector.makeResidualStainVector(stain1, stain2);
		else
			this.stain3 = stain3;
		this.maxRed = maxRed;
		this.maxGreen = maxGreen;
		this.maxBlue = maxBlue;
	}
	
	/**
	 * Constructor for ColorDeconvolutionStains with two stain vectors.
	 * @param name
	 * @param stain1
	 * @param stain2
	 * @param maxRed background (white) value for red channel
	 * @param maxGreen background (white) value for green channel
	 * @param maxBlue background (white) value for blue channel
	 */
	public ColorDeconvolutionStains(String name, StainVector stain1, StainVector stain2, double maxRed, double maxGreen, double maxBlue) {
		this(name, stain1, stain2, null, maxRed, maxGreen, maxBlue);
	}
	
	/**
	 * Default constructor, for Externalizable. Should not be used directly.
	 */
	public ColorDeconvolutionStains() {}

	/**
	 * Get a specified color deconvolution stain vector, where n should be 1, 2 or 3 
	 * (because color deconvolution on a 3-channel image can recover [to some extent] up to 3 stains).
	 * <p>
	 * If only 2 stains are set (i.e. stain1 and stain2), then a third stain is computed
	 * orthogonal to the first two, with the name 'residual'.
	 * 
	 * @param n
	 * @return The requested stain vector, or null if n is out of range or the stain was not set.
	 */
	public StainVector getStain(int n) {
		if (n == 1)
			return stain1;
		if (n == 2)
			return stain2;
		if (n == 3) {
			return stain3;
		}
		if (n == 0)
			logger.error("Stains are not zero-based! Do you mean you want stain 1?");
		return null;
	}
	
	/**
	 * Get the stains name.
	 * @return
	 */
	public String getName() {
		return name;
	}
	
	/**
	 * Get the number for a specific stain (1, 2 or 3).
	 * @param stain
	 * @return
	 */
	public int getStainNumber(StainVector stain) {
		if (stain1.equals(stain))
			return 1;
		if (stain2.equals(stain))
			return 2;
		if (stain3.equals(stain))
			return 3;
		return -1;
	}
	
	/**
	 * Get the maximum value for the red channel (default = 255).
	 * 
	 * @return
	 */
	public double getMaxRed() {
		return maxRed;
	}

	/**
	 * Get the maximum value for the green channel (default = 255).
	 * 
	 * @return
	 */
	public double getMaxGreen() {
		return maxGreen;
	}

	/**
	 * Get the maximum value for the blue channel (default = 255).
	 * 
	 * @return
	 */
	public double getMaxBlue() {
		return maxBlue;
	}

	
	/**
	 * Get matrix inverse, as useful for color deconvolution.
	 * See static ColorDeconvolution classes for usage.
	 * 
	 * @return
	 */
	public double[][] getMatrixInverse() {
		// Create if we don't have one already
		if (matInverse == null) {
			double[][] stainMat = new double[][]{
					getStain(1).getArray(),
					getStain(2).getArray(),
					getStain(3).getArray()};
			ColorDeconvMatrix3x3 mat3x3 = new ColorDeconvMatrix3x3(stainMat);
			matInverse = mat3x3.inverse();
		}
		return matInverse;
	}
	
	
	@Override
	public String toString() {
		return "Color deconvolution stains: " + stain1 + ", " + stain2 + ", " + stain3;
	}


	/**
	 * Get a String representation of the stains and background.
	 * @param stains
	 * @param nDecimalPlaces
	 * @return
	 * 
	 * @see #parseColorDeconvolutionStainsArg(String)
	 */
	public static String getColorDeconvolutionStainsAsString(final ColorDeconvolutionStains stains, final int nDecimalPlaces) {
		if (stains == null)
			return "";
		
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		sb.append("\"Name").append("\" : \"").append(stains.getName()).append("\", ");
		for (int i = 1; i <= 3; i++) {
			StainVector stain = stains.getStain(i);
			// No need to add the residual stain
			if (i == 3 && stain.isResidual())
				continue;
			sb.append("\"Stain ").append(i).append("\" : \"").append(stain.getName()).append("\", ");
			sb.append("\"Values ").append(i).append("\" : \"").append(stain.arrayAsString(Locale.US, nDecimalPlaces)).append("\", ");
		}
		sb.append("\"Background\" : \"");
		
		sb.append(" ").append(GeneralTools.arrayToString(Locale.US, new double[]{stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue()}, nDecimalPlaces));
//		sb.append(String.format( "%.Nf %.Nf %.Nf".replace("N", Integer.toString(nDecimalPlaces)), stains.getMaxRed(), stains.getMaxGreen(), stains.getMaxBlue()));
//		sb.append(stains.getMaxRed()).append(" ");
//		sb.append(stains.getMaxGreen()).append(" ");
//		sb.append(stains.getMaxBlue());
		sb.append("\"}");
		
		return sb.toString();
	}

	/**
	 * Parse color deconvolution stains from a JSON String, with keys 'Stain 1', 'Stain 2', (optionally) 'Stain 3' and 'Background'.
	 * @param s
	 * @return
	 * 
	 * @see #getColorDeconvolutionStainsAsString(ColorDeconvolutionStains, int)
	 */
	public static ColorDeconvolutionStains parseColorDeconvolutionStainsArg(final String s) {
		
		Map<String, String> map = GeneralTools.parseArgStringValues(s);
		if (map.isEmpty())
			return null;
		
		StainVector stain1 = parseStainVector(Locale.US, map.get("Stain 1"), map.get("Values 1"));
		StainVector stain2 = parseStainVector(Locale.US, map.get("Stain 2"), map.get("Values 2"));
		StainVector stain3 = null;
		if (map.containsKey("Stain 3"))
			stain3 = parseStainVector(Locale.US, map.get("Stain 3"), map.get("Values 3"));
		
		double[] background = parseStainValues(Locale.US, map.get("Background"));
		
		return new ColorDeconvolutionStains(map.get("Name"), stain1, stain2, stain3, background[0], background[1], background[2]);
	}
	
	
	/**
	 * Parse a stain vector from a string containing three numbers separated by spaces.
	 * @param locale
	 * @param name
	 * @param s
	 * @return
	 */
	private static StainVector parseStainVector(Locale locale, String name, String s) {
		if (s == null)
			return null;
		s = s.trim();
		if (s.length() == 0)
			return null;
		if (s.length() == 1) {
			s = s.toLowerCase();
			if (s.equals("h"))
				return StainVector.makeDefaultStainVector(DefaultStains.HEMATOXYLIN);
			else if (s.equals("e"))
				return StainVector.makeDefaultStainVector(DefaultStains.EOSIN);
			else if (s.equals("d"))
				return StainVector.makeDefaultStainVector(DefaultStains.DAB);
		}
		double[] vector = parseStainValues(locale, s);
		if (vector != null)
			return StainVector.createStainVector(name, vector[0], vector[1], vector[2]);
		// If we got here, we didn't find 3 numbers
		return null;
	}
	
	/**
	 * Parses 3 values, suitable for use as a stain vector
	 * @param s
	 * @return
	 */
	public static double[] parseStainValues(Locale locale, String s) {
		// Try to parse the stain vector
		double[] vector = new double[3];
		StringTokenizer tokenizer = new StringTokenizer(s, " \t\n\r\f[]");
		int i = 0;
		while (tokenizer.hasMoreTokens()) {
			String token = tokenizer.nextToken();
			if (token.endsWith(","))
				token = token.substring(0, token.length()-1);
			try {
				vector[i] = NumberFormat.getInstance(locale).parse(token).doubleValue();// Double.parseDouble(token);
			} catch (Exception e) {
//				// Try this as a last resort...?
				vector[i] = Double.parseDouble(token);				
//				if (token.contains(",") && !token.contains(".")) {
//					String newToken = token.replace(",", ".");
//					vector[i] = Double.parseDouble(newToken);				
//					logger.warn("Using emergency stain vector parsing! {} was replaced with {}", token, newToken);
//				}
			}
			i++;
			// If we got here, we found 3 numbers
			if (i == 3)
				return vector;
		}
		
//		Matcher matcher = StainVector.pattern.matcher(s);
//		double[] vector = new double[3];
//		int i = 0;
//		while (matcher.find()) {
//			vector[i] = Double.parseDouble(matcher.group());
//			i++;
//			// If we got here, we found 3 numbers
//			if (i == 3)
//				return vector;
//		}
		// If we got here, we didn't find 3 numbers
		return null;
	}
	
	
	
	
	@Override
	public void writeExternal(ObjectOutput out) throws IOException {
		out.writeInt(version);
		out.writeObject(getColorDeconvolutionStainsAsString(this, 8));
	}


	@Override
	public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
		int version = in.readInt();
		if (version != 1)
			logger.error("{} unsupported version number: {}", getClass().getSimpleName(), version);
		
		Object o = in.readObject();
		if (o instanceof String) {
			ColorDeconvolutionStains stains = parseColorDeconvolutionStainsArg((String)o);
			name = stains.name;
			stain1 = stains.stain1;
			stain2 = stains.stain2;
			stain3 = stains.stain3;
			maxRed = stains.maxRed;
			maxGreen = stains.maxGreen;
			maxBlue = stains.maxBlue;
		}
	}
	
}
