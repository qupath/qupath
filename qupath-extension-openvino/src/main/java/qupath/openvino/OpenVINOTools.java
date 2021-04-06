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
 * Helper methods for working with OpenVINO and QuPath, with the help of OpenCV.
 *
 * @author Dmitry Kurtaev
 */
public class OpenVINOTools {

    private final static Logger logger = LoggerFactory.getLogger(OpenVINOTools.class);

    /**
     * Wrap OpenCV Mat to OpenVINO Blob to pass it then to the network.
     *
     * @param mat OpenCV Mat which represents an image with interleaved channels order
     * @return OpenVINO Blob
     */
    public static Blob convertToBlob(Mat mat) {
        int[] dimsArr = {1, mat.channels(), mat.rows(), mat.cols()};
        TensorDesc tDesc = new TensorDesc(Precision.FP32, dimsArr, Layout.NHWC);
        return new Blob(tDesc, mat.data().address());
    }
}
