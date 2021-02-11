/*-
 * #%L
 * This file is part of QuPath.
 * %%
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

package qupath.openvino;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private static void loadNativeLibs() {
        // Load native libraries
        String[] nativeFiles = {
            "plugins.xml",
            "libngraph.so",
            "libinference_engine_transformations.so",
            "libinference_engine.so",
            "libinference_engine_ir_reader.so",
            "libinference_engine_legacy.so",
            "libinference_engine_lp_transformations.so",
            "libMKLDNNPlugin.so",
            "libinference_engine_java_api.so"  // Should be at the end
        };
        try {
            File tmpDir = Files.createTempDirectory("openvino-native").toFile();
            for (String file : nativeFiles) {
                URL url = IECore.class.getClassLoader().getResource(file);
                tmpDir.deleteOnExit();
                File nativeLibTmpFile = new File(tmpDir, file);
                nativeLibTmpFile.deleteOnExit();
                try (InputStream in = url.openStream()) {
                    Files.copy(in, nativeLibTmpFile.toPath());
                }
                String path = nativeLibTmpFile.getAbsolutePath();
                if (file.endsWith(".so")) {
                    System.load(path);
                }
            }
        } catch (IOException ex) {
        }
    }

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

        loadNativeLibs();
        loadBundle(modelPath);
    }

    private OpenVINOBundle getBundle() {
        if (bundle == null && exception == null) {
            try {
                bundle = loadBundle(modelPath);
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

    private static OpenVINOBundle loadBundle(String path) {
        return cachedBundles.computeIfAbsent(path, p -> new OpenVINOBundle(p));
    }

    private static class OpenVINOBundle {
        private final static Logger logger = LoggerFactory.getLogger(OpenVINOBundle.class);
        private final static String inpName = "input";
        private final static String outName = "concatenate_4/concat";

        private IECore ie = new IECore();
        private InferRequest[] requests;
        // Vector with flags that indicate if inference request is free or busy.
        private boolean[] requestIsFree;
        // Pre-allocated buffers for outputs.
        private Mat[] outputs;

        private OpenVINOBundle(String pathModel) {
            logger.info("Initialize OpenVINO network");

            // Determine default number of async streams.
            Map<String, String> config = Map.of("CPU_THROUGHPUT_STREAMS", "CPU_THROUGHPUT_AUTO");
            ie.SetConfig(config, "CPU");
            String nStr = ie.GetConfig("CPU", "CPU_THROUGHPUT_STREAMS").asString();
            int nstreams = Integer.parseInt(nStr);
            logger.info("Number of asynchronous streams: " + nstreams);

            String xmlPath = Paths.get(pathModel, "saved_model.xml").toString();
            CNNNetwork net = ie.ReadNetwork(xmlPath);
            net.getInputsInfo().get(inpName).setLayout(Layout.NHWC);
            net.getOutputsInfo().get(outName).setLayout(Layout.NHWC);
            ExecutableNetwork execNet = ie.LoadNetwork(net, "CPU");

            requests = new InferRequest[nstreams];
            requestIsFree = new boolean[nstreams];
            outputs = new Mat[nstreams];
            for (int i = 0; i < nstreams; ++i) {
                requests[i] = execNet.CreateInferRequest();
                requestIsFree[i] = true;

                int[] shape = requests[i].GetBlob(outName).getTensorDesc().getDims();
                outputs[i] = new Mat(shape, opencv_core.CV_32F);

                TensorDesc tDesc = new TensorDesc(Precision.FP32, shape, Layout.NHWC);
                Blob output = new Blob(tDesc, outputs[i].data().address());
                requests[i].SetBlob(outName, output);
            }
        }

        private Mat run(Mat mat, String outputName) {
            // Find a free inference request.
            InferRequest req = null;
            int idx = -1;
            synchronized (requests) {
                try {
                    while (req == null) {
                        Thread.sleep(1);
                        for (idx = 0; idx < requestIsFree.length; ++idx) {
                            if (requestIsFree[idx]) {
                                req = requests[idx];
                                requestIsFree[idx] = false;
                                break;
                            }
                        }
                    }
                } catch(InterruptedException e) {}
            }

            // Run inference
            Blob input = OpenVINOTools.convertToBlob(mat);
            req.SetBlob(inpName, input);
            req.StartAsync();
            req.Wait(WaitMode.RESULT_READY);

            Mat res = outputs[idx];

            int c = res.size(1);
            int h = res.size(2);
            // Data is already in NHWC layout. Change just a shape.
            res = res.reshape(1, c * h).reshape(c, h);

            // Release inference request
            requestIsFree[idx] = true;

            return res;
        }
    }
}
