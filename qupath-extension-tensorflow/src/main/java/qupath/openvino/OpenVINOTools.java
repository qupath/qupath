/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2018 - 2021 QuPath developers, The University of Edinburgh
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

package qupath.openvino;

import java.io.File;
import java.nio.FloatBuffer;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.regions.Padding;
import qupath.opencv.ops.ImageOp;

import org.intel.openvino.*;

/**
 * Helper methods for working with TensorFlow and QuPath, with the help of OpenCV.
 *
 * @author Pete Bankhead
 */
public class OpenVINOTools {

    private final static Logger logger = LoggerFactory.getLogger(OpenVINOTools.class);

    public static Blob convertToBlob(Mat mat) {
        int[] dimsArr = {1, mat.channels(), mat.rows(), mat.cols()};
        TensorDesc tDesc = new TensorDesc(Precision.FP32, dimsArr, Layout.NHWC);
        return new Blob(tDesc, mat.data().address());
    }

    /**
     * Create an {@link ImageOp} to run a TensorFlow model with a single image input and output,
     * optionally specifying the input tile width and height.
     *
     * @param modelPath
     * @param tileWidth input tile width; ignored if &le; 0
     * @param tileHeight input tile height; ignored if &le; 0
     * @param padding amount of padding to add to each request
     * @return the {@link ImageOp}
     * @throws IllegalArgumentException if the model path is not a directory
     */
    public static ImageOp createOp(String modelPath, int tileWidth, int tileHeight, Padding padding) throws IllegalArgumentException {
        return createOp(modelPath, tileWidth, tileHeight, padding, null);
    }

    /**
     * Create an {@link ImageOp} to run a TensorFlow model with a single image input and output,
     * optionally specifying the input tile width and height.
     *
     * @param modelPath
     * @param tileWidth input tile width; ignored if &le; 0
     * @param tileHeight input tile height; ignored if &le; 0
     * @param padding amount of padding to add to each request
     * @param outputName optional name of the node to use for output (may be null)
     * @return the {@link ImageOp}
     * @throws IllegalArgumentException if the model path is not a directory
     */
    public static ImageOp createOp(String modelPath, int tileWidth, int tileHeight, Padding padding, String outputName) throws IllegalArgumentException {
        var file = new File(modelPath);
        if (!file.isDirectory()) {
            logger.error("Invalid model path, not a directory! {}", modelPath);
            throw new IllegalArgumentException("Model path should be a directory!");
        }
        return new OpenVINOOp(modelPath, tileWidth, tileHeight, padding, outputName);
    }

}
