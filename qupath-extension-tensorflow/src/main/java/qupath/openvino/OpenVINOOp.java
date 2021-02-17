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

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.intel.openvino.*;

import qupath.lib.regions.Padding;
import qupath.opencv.ops.ImageOps.PaddedOp;
import qupath.opencv.tools.OpenCVTools;

/**
 * An {@link ImageOp} that runs a OpenVINO model for prediction.
 *
 * @author Dmitry Kurtaev
 */
public class OpenVINOOp extends PaddedOp {

    private final static Logger logger = LoggerFactory.getLogger(OpenVINOOp.class);

    private String modelPath;
    private int tileWidth = 512;
    private int tileHeight = 512;

    private Padding padding;

    // Identifier for the requested output node - may be null to use the default output
    private String outputName = null;

    private transient OpenVINOBundle bundle;
    private transient Exception exception;

    OpenVINOOp(String modelPath, int tileWidth, int tileHeight, Padding padding, String outputName) {
        super();
        logger.debug("Creating op from {}", modelPath);
        this.modelPath = modelPath;
        this.outputName = outputName;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        if (padding == null)
            this.padding = Padding.empty();
        else
            this.padding = padding;

        IECore.loadNativeLibs();
        loadBundle(modelPath, tileWidth, tileHeight);
    }

    private OpenVINOBundle getBundle() {
        if (bundle == null && exception == null) {
            try {
                bundle = loadBundle(modelPath, tileWidth, tileHeight);
            } catch (Exception e) {
                logger.error("Unable to load bundle: " + e.getLocalizedMessage(), e);
                this.exception = e;
            }
        }
        return bundle;
    }

    // Not needed
    @Override
    protected Padding calculatePadding() {
        return padding;
    }

    @Override
    protected Mat transformPadded(Mat input) {
        var bundle = getBundle();
        if (exception != null)
            throw new RuntimeException(exception);
        if (tileWidth > 0 && tileHeight > 0)
            return OpenCVTools.applyTiled(m -> bundle.run(m, outputName), input, tileWidth, tileHeight, opencv_core.BORDER_REFLECT);
        else
            return bundle.run(input, outputName);
    }

    private static Map<String, OpenVINOBundle> cachedBundles = new HashMap<>();

    private static OpenVINOBundle loadBundle(String path, int tileWidth, int tileHeight) {
        return cachedBundles.computeIfAbsent(path, p -> new OpenVINOBundle(p, tileWidth, tileHeight));
    }

    private static class OpenVINOBundle {
        private final static Logger logger = LoggerFactory.getLogger(OpenVINOBundle.class);
        private String inpName;
        private String outName;

        private IECore ie = new IECore();
        private InferRequest[] requests;
        // Pre-allocated buffers for outputs.
        private Mat[] outputs;
        private int idx = 0;

        private OpenVINOBundle(String pathModel, int tileWidth, int tileHeight) {
            logger.info("Initialize OpenVINO network");

            // Determine default number of async streams.
            Map<String, String> config = Map.of("CPU_THROUGHPUT_STREAMS", "CPU_THROUGHPUT_AUTO");
            ie.SetConfig(config, "CPU");
            String nStr = ie.GetConfig("CPU", "CPU_THROUGHPUT_STREAMS").asString();
            int nstreams = Integer.parseInt(nStr);
            logger.info("Number of asynchronous streams: " + nstreams);

            String xmlPath = Paths.get(pathModel, "saved_model.xml").toString();
            CNNNetwork net = ie.ReadNetwork(xmlPath);

            // Get input and output info and perform network reshape in case of changed tile size
            Map<String, InputInfo> inputsInfo = net.getInputsInfo();
            inpName = new ArrayList<String>(inputsInfo.keySet()).get(0);
            InputInfo inputInfo = inputsInfo.get(inpName);

            Map<String, Data> outputsInfo = net.getOutputsInfo();
            outName = new ArrayList<String>(outputsInfo.keySet()).get(0);
            Data outputInfo = outputsInfo.get(outName);

            int[] inpDims = inputInfo.getTensorDesc().getDims();
            if (inpDims[2] != tileHeight || inpDims[3] != tileWidth) {
                inpDims[2] = tileHeight;
                inpDims[3] = tileWidth;
                Map<String, int[]> shapes = new HashMap<>();
                shapes.put(inpName, inpDims);
                net.reshape(shapes);
            }
            inputInfo.setLayout(Layout.NHWC);
            outputInfo.setLayout(Layout.NHWC);
            ExecutableNetwork execNet = ie.LoadNetwork(net, "CPU");

            requests = new InferRequest[nstreams];
            outputs = new Mat[nstreams];
            for (int i = 0; i < nstreams; ++i) {
                requests[i] = execNet.CreateInferRequest();

                int[] shape = requests[i].GetBlob(outName).getTensorDesc().getDims();
                outputs[i] = new Mat(shape, opencv_core.CV_32F);

                TensorDesc tDesc = new TensorDesc(Precision.FP32, shape, Layout.NHWC);
                Blob output = new Blob(tDesc, outputs[i].data().address());
                requests[i].SetBlob(outName, output);
            }
        }

        private Mat run(Mat mat, String outputName) {
            InferRequest req = null;
            Mat output;
            synchronized (requests) {
                req = requests[idx];
                output = outputs[idx];
                idx = (idx + 1) % requests.length;
            }

            // Run inference
            Mat res;
            Blob input = OpenVINOTools.convertToBlob(mat);
            synchronized (req) {
                req.SetBlob(inpName, input);
                req.StartAsync();
                req.Wait(WaitMode.RESULT_READY);
                res = output.clone();
            }
            int c = res.size(1);
            int h = res.size(2);
            // Data is already in NHWC layout. Change just a shape.
            return res.reshape(1, c * h).reshape(c, h);
        }
    }
}
