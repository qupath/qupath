/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
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


package qupath.opencv.tools;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.DoubleBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.Scalar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.common.GeneralTools;

/**
 * Read .npy and .npz files from NumPy.
 * <p>
 * Note that only a subset of files are supported.
 * Specifically, each .npy file should contain a single (possibly multidimensional) 
 * array with a type supported by OpenCV.
 * <p>
 * Structured, complex and object arrays are not supported.
 * <p>
 * See <a href="https://numpy.org/devdocs/reference/generated/numpy.lib.format.html">
 * https://numpy.org/devdocs/reference/generated/numpy.lib.format.html
 * </a> for more information about the format.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
public class NumpyTools {
	
	private static final Logger logger = LoggerFactory.getLogger(NumpyTools.class);
	
	private static final Pattern PATTERN_DTYPE = Pattern.compile("'descr': *'([^']+)'");

	private static final Pattern PATTERN_ORDER = Pattern.compile("'fortran_order': *([^,]+)");

	private static final Pattern PATTERN_SHAPE = Pattern.compile("'shape': *\\(([^']+)\\)");
	
	/**
	 * Read a single Mat from an .npy or .npz file.
	 * @param path
	 * @return
	 * @throws IOException
	 */
	public static Mat readMat(String path) throws IOException {
		return readMat(Paths.get(path));
	}
	
	/**
	 * Read a single Mat from an .npy or .npz file, optionally squeezing singleton dimensions.
	 * @param path path to the .npy file
	 * @param squeezeDimensions if true, squeeze singleton dimensions
	 * @return
	 * @throws IOException
	 */
	public static Mat readMat(String path, boolean squeezeDimensions) throws IOException {
		return readMat(Paths.get(path), squeezeDimensions);
	}
	
	/**
	 * Read a all Mats from an .npy or .npz file.
	 * This will be a single Mat for .npy but may be multiple for .npz.
	 * @param path path to the file
	 * @return a map with mat names (from file/entry names) and their corresponding Mats
	 * @throws IOException
	 */
	public static Map<String, Mat> readAllMats(String path) throws IOException {
		return readAllMats(Paths.get(path));
	}
	
	/**
	 * Read a all Mats from an .npy or .npz file, optionally squeezing singleton dimensions.
	 * This will be a single Mat for .npy but may be multiple for .npz.
	 * @param path path to the file
	 * @param squeezeDimensions if true, squeeze singleton dimensions
	 * @return a map with mat names (from file/entry names) and their corresponding Mats
	 * @throws IOException
	 */
	public static Map<String, Mat> readAllMats(String path, boolean squeezeDimensions) throws IOException {
		return readAllMats(Paths.get(path), squeezeDimensions);
	}

	/**
	 * Read a all Mats from an .npy or .npz path.
	 * This will be a single Mat for .npy but may be multiple for .npz.
	 * @param path path to the file
	 * @return a map with mat names (from file/entry names) and their corresponding Mats
	 * @throws IOException
	 */
	public static Map<String, Mat> readAllMats(Path path) throws IOException {
		return readAllMats(path, false);
	}
	
	/**
	 * Read a all Mats from an .npy or .npz path, optionally squeezing singleton dimensions
	 * This will be a single Mat for .npy but may be multiple for .npz.
	 * @param path path to the file
	 * @param squeezeDimensions if true, squeeze singleton dimensions
	 * @return a map with mat names (from file/entry names) and their corresponding Mats
	 * @throws IOException
	 */
	public static Map<String, Mat> readAllMats(Path path, boolean squeezeDimensions) throws IOException {
		FileType type;
		try (var stream = Files.newInputStream(path)) {
			type = checkType(stream);
		}
		switch (type) {
		case NPY:
			return Collections.singletonMap(GeneralTools.getNameWithoutExtension(path.toFile()), readMat(path, squeezeDimensions));
		case ZIP:
			return readZipped(path, squeezeDimensions, false);
		case UNKNOWN:
		default:
			throw new IllegalArgumentException(path + " is not a valid npy or npz file!");
		}
	}
	

	private static Map<String, Mat> readZipped(Path path, boolean squeezeDimensions, boolean firstOnly) throws IOException {
		Map<String, Mat> map = new LinkedHashMap<>();
		try (var zipFile = new ZipFile(path.toFile())) {
			var iter = zipFile.entries().asIterator();
			while (iter.hasNext()) {
				var entry = iter.next();
				try (var stream = new BufferedInputStream(zipFile.getInputStream(entry))) {
					var mat = readMat(stream, squeezeDimensions);
					map.put(GeneralTools.getNameWithoutExtension(entry.getName()), mat);
					if (firstOnly)
						break;
				}
			}
		}
		return map;
	}
	
	
	/**
	 * Read an OpenCV Mat from a Numpy .npy file.
	 * @param path path to the .npy file
	 * @return
	 * @throws IOException if a Mat could not be read from the given path
	 */
	public static Mat readMat(Path path) throws IOException {
		return readMat(path, false);
	}
	
		
	/**
	 * Read an OpenCV Mat from a Numpy .npy file, optionally squeezing singleton dimensions.
	 * @param path path to the .npy file
	 * @param squeezeDimensions if true, squeeze singleton dimensions
	 * @return
	 * @throws IOException if a Mat could not be read from the given path
	 */
	public static Mat readMat(Path path, boolean squeezeDimensions) throws IOException {
		FileType type;
		try (var stream = Files.newInputStream(path)) {
			type = checkType(stream);
		}
		switch (type) {
		case NPY:
			try (var stream = new BufferedInputStream(Files.newInputStream(path))) {
				return readMat(stream, squeezeDimensions);
			}
		case ZIP:
			var map = readZipped(path, squeezeDimensions, true);
			if (map.isEmpty())
				throw new IllegalArgumentException(path + " does not contain any arrays!");
			return map.values().iterator().next();
		case UNKNOWN:
		default:
			throw new IllegalArgumentException(path + " is not a valid npy or npz file!");
		}
	}
	
	
	private static enum FileType { NPY, ZIP, UNKNOWN }
	
	private static FileType checkType(InputStream stream) throws IOException {
		int firstByte = stream.read();
		if (firstByte == 0x93) {
			byte[] magic = stream.readNBytes(5);
			if (Arrays.equals("NUMPY".getBytes(StandardCharsets.US_ASCII), magic))
				return FileType.NPY;
			else
				return FileType.UNKNOWN;
		} else if (firstByte == 0x50) {
			int second = stream.read();
			if (second == 0x4B)
				return FileType.ZIP;
		}
		return FileType.UNKNOWN;
	}	
		
	/**
	 * Read a single Mat from an input stream, which should follow the .npy file format specification.
	 * @param stream
	 * @param squeezeDimensions
	 * @return
	 * @throws IOException
	 */
	public static Mat readMat(InputStream stream, boolean squeezeDimensions) throws IOException {
		
		if (stream.read() != 0x93) {
			throw new IOException("File is not in npy format!");
		}
		byte[] magic = stream.readNBytes(5);
		if (!Arrays.equals("NUMPY".getBytes(StandardCharsets.US_ASCII), magic))
			throw new IOException("File is not in npy format!");

		// Read the version
		int majorVersion = stream.read();
		int minorVersion = stream.read();

		// Read the header (a Python dict)
		int headerLength;
		if (majorVersion >= 2) {
			headerLength = ByteBuffer.wrap(stream.readNBytes(2))
				.order(ByteOrder.LITTLE_ENDIAN)
				.getInt(); // Really should be unsigned
		} else {
			int b1 = stream.read();
			int b2 = stream.read();
			headerLength = (((b2 & 0xFF) << 8) | (b1 & 0xFF));
		}
		if (headerLength <= 0)
			throw new IOException("Unsupported header length " + headerLength);
		byte[] headerBytes = stream.readNBytes(headerLength);
		String dict;
		if (majorVersion >= 3)
			dict = new String(headerBytes, StandardCharsets.UTF_8).strip();
		else
			dict = new String(headerBytes, StandardCharsets.ISO_8859_1).strip();
		
		logger.debug("Version: {}.{}, Dict: {}", majorVersion, minorVersion, dict);
		
		var dtypeString = getMatch(PATTERN_DTYPE, dict);
		if (dtypeString == null)
			throw new IOException("Unable to find dtype in file");
		
		var orderString = getMatch(PATTERN_ORDER, dict);
		if (orderString == null)
			throw new IOException("Unable to find fortran_order in file");
		boolean fortranOrder = Boolean.valueOf(orderString);
		if (fortranOrder)
			throw new IOException("Fortran order is not supported, sorry");
		
		var shapeString = getMatch(PATTERN_SHAPE, dict);
		if (shapeString == null)
			throw new IOException("Unable to find array shape in file");
		int[] shape = Arrays.stream(shapeString.split(","))
				.map(s -> s.strip())
				.filter(s -> !s.isEmpty())
				.mapToInt(s -> Integer.parseInt(s))
				.toArray();
		
		// Figure out byte order
		ByteOrder byteOrder = ByteOrder.nativeOrder();
		if (dtypeString.startsWith("=")) {
			logger.debug("Byte order specified as native order");
			byteOrder = ByteOrder.nativeOrder();
			dtypeString = dtypeString.substring(1);
		} else if (dtypeString.startsWith(">")) {
			logger.debug("Byte order specified as big endian");
			byteOrder = ByteOrder.BIG_ENDIAN;				
			dtypeString = dtypeString.substring(1);
		} else if (dtypeString.startsWith("<")) {
			logger.debug("Byte order specified as little endian");
			byteOrder = ByteOrder.LITTLE_ENDIAN;				
			dtypeString = dtypeString.substring(1);
		} else if (dtypeString.startsWith("|")) {
			// Doesn't matter
			dtypeString = dtypeString.substring(1);
		} else {
			logger.warn("Byte order not specified - will use " + byteOrder);
		}
		
		// Calculate number of pixels so we only read as much as we expect to need
		int nPixels = shape[0];
		for (int i = 1; i < shape.length; i++) {
			nPixels *= shape[i];
		}
		if (squeezeDimensions)
			shape = Arrays.stream(shape).filter(i -> i != 1).toArray();
		
		Mat mat;
		
		switch (dtypeString) {
		case "float32":
		case "f4": 
		case "f":
			mat = createMat(shape, opencv_core.CV_32F);
			FloatBuffer buff32 = mat.createBuffer();
			buff32.put(ByteBuffer.wrap(stream.readNBytes(nPixels * 4)).order(byteOrder).asFloatBuffer());
			return mat;
		case "float64":
		case "f8": 
		case "d": 
			mat = createMat(shape, opencv_core.CV_64F);
			DoubleBuffer buff64 = mat.createBuffer();
			buff64.put(ByteBuffer.wrap(stream.readNBytes(nPixels * 8)).order(byteOrder).asDoubleBuffer());
			return mat;
		case "uint8":
		case "u1": 
		case "b": 
			mat = createMat(shape, opencv_core.CV_8U);
			ByteBuffer bufu8 = mat.createBuffer();
			bufu8.put(ByteBuffer.wrap(stream.readNBytes(nPixels)).order(byteOrder));
			return mat;
		case "int8":
		case "i1": 
		case "B": 
			mat = createMat(shape, opencv_core.CV_8S);
			ByteBuffer bufi8 = mat.createBuffer();
			bufi8.put(ByteBuffer.wrap(stream.readNBytes(nPixels)).order(byteOrder));
			return mat;
		case "int16":
		case "i2": 
		case "h": 
			mat = createMat(shape, opencv_core.CV_16S);
			ShortBuffer bufs16 = mat.createBuffer();
			bufs16.put(ByteBuffer.wrap(stream.readNBytes(nPixels * 2)).order(byteOrder).asShortBuffer());
			return mat;
		case "uint16":
		case "u2": 
		case "H": 
			mat = createMat(shape, opencv_core.CV_16U);
			ShortBuffer bufu16 = mat.createBuffer();
			bufu16.put(ByteBuffer.wrap(stream.readNBytes(nPixels * 2)).order(byteOrder).asShortBuffer());
			return mat;
		case "int32":
		case "i4": 
		case "i": 
			mat = createMat(shape, opencv_core.CV_32S);
			IntBuffer bufs32 = mat.createBuffer();
			bufs32.put(ByteBuffer.wrap(stream.readNBytes(nPixels * 4)).order(byteOrder).asIntBuffer());
			return mat;
		case "float16":
		case "f2": 
			// float16
		case "uint32":
		case "uint4": 
			// uint32
		default:
			throw new IOException("Unsupported data type " + orderString);
		}		
	}
	
	/**
	 * Create a Mat of the specified shape, using the third dimension as channels if possible.
	 * @param shape
	 * @param depth
	 * @return
	 */
	private static Mat createMat(int[] shape, int depth) {
		if (shape.length == 3 && shape[2] < opencv_core.CV_CN_MAX) {
			return new Mat(shape[0], shape[1], opencv_core.CV_MAKETYPE(depth, shape[2]), Scalar.ZERO);
		}
		return new Mat(shape, depth, Scalar.ZERO);
	}
	

	private static String getMatch(Pattern pattern, String string) {
		var matcher = pattern.matcher(string);
		if (matcher.find()) {
			return matcher.group(1).strip();
		} else
			return null;
	}

}
