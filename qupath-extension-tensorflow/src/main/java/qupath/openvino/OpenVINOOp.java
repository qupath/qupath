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
import org.bytedeco.opencv.global.opencv_dnn;
import org.bytedeco.opencv.opencv_core.Mat;
import org.bytedeco.opencv.opencv_core.MatVector;

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

        private IECore ie = new IECore();
        private InferRequest req;

        private OpenVINOBundle(String pathModel) {
            System.out.println("~~~~~~~~~ OpenVINOBundle");
            CNNNetwork net = ie.ReadNetwork(pathModel + "/saved_model.xml");
            InputInfo inputInfo = net.getInputsInfo().get("input");
            inputInfo.setLayout(Layout.NHWC);
            ExecutableNetwork execNet = ie.LoadNetwork(net, "CPU");
            req = execNet.CreateInferRequest();
        }

        private static Mat nchw2nhwc(Blob blob) {
            Mat src = OpenVINOTools.convertToMat(blob);
            var images = new MatVector();
            opencv_dnn.imagesFromBlob(src, images);
            return images.get(0);
        }

        private Mat run(Mat mat, String outputName) {
            Mat res;
            synchronized (req) {
                Blob input = OpenVINOTools.convertToBlob(mat);
                req.SetBlob("input", input);
                req.Infer();
                Blob output = req.GetBlob("concatenate_4/concat");
                res = nchw2nhwc(output);
            }
            return res;
        }
    }


    // private static class TensorFlowBundle {

//     	private final static Logger logger = LoggerFactory.getLogger(TensorFlowBundle.class);

//     	private String pathModel;
//         private SavedModelBundle bundle;

//         private String inputName;
//         private String outputName;
//         private long[] inputShape;
//     	private long[] outputShape;

//     	private transient Map<String, NodeDef> nodeDefs;

//     	private TensorFlowBundle(String pathModel) {

//     		this.pathModel = pathModel;

//     		var dir = new File(pathModel);
//     		if (!dir.exists()) {
//     			throw new IllegalArgumentException(pathModel + " does not exist!");
//     		} else if (!dir.isDirectory() || !tensorflow.MaybeSavedModelDirectory(pathModel)) {
//     			throw new IllegalArgumentException(pathModel + " is not a valid TensorFlow model directory!");
//     		}

//             // If this fails, are both the main jars and the platform-specific jars present - for TensorFlow and MKL-DNN?
//             var sessionOptions = new SessionOptions();

// //            var runOptions = new RunOptions();

//             var runOptions = RunOptions.default_instance();


//             var tags = new StringUnorderedSet();
//             tags.insert(tensorflow.kSavedModelTagServe());
// //            tags.insert(tensorflow.kSavedModelTagTrain());
//             bundle = new SavedModelBundle();

//             var status = tensorflow.LoadSavedModel(
//                     sessionOptions,
//                     runOptions,
//                     pathModel,
//                     tags,
//                     bundle
//             );

//             if (!status.ok()) {
//             	throw new RuntimeException(status.error_message().getString());
//             }

//             MetaGraphDef graphDef = bundle.meta_graph_def();
//             logger.trace("Has GraphDef: {}", graphDef.has_graph_def());
//             StringSignatureDefMap sigdefMap = graphDef.signature_def();


//             logger.debug("StringSignatureDefMap size: {}", sigdefMap.size());
//             SignatureDef map = sigdefMap.get(sigdefMap.begin().first());

//             long nInputs = map.inputs().size();
//             if (nInputs != 1) {
//             	logger.warn("Only one input currently supported, but model supports {}", nInputs);
//             }
//             long nOutputs = map.outputs().size();
//             if (nOutputs != 1) {
//             	logger.warn("Only one output currently supported, but model supports {}", nOutputs);
//             }

//             // Get input & output names and shapes
//             TensorInfo input = map.inputs().get(map.inputs().begin().first());
//             inputName = input.name().getString();
//             inputShape = tensorShape(input.tensor_shape());

//             TensorInfo output = map.outputs().get(map.outputs().begin().first());
//             outputName = output.name().getString();
//             outputShape = tensorShape(output.tensor_shape());

//             logger.debug("Model input: {} ({})", inputName, arrayToString(inputShape));
//         	logger.debug("Model output: {} ({})", outputName, arrayToString(outputShape));

//         	logger.info("Loaded {}", this);
//         }

//     	private static String arrayToString(long[] shape) {
//     		return Arrays.stream(shape).mapToObj(l -> Long.toString(l)).collect(Collectors.joining(","));
//     	}

//     	private static long[] tensorShape(TensorShapeProto shape) {
//         	long[] dims = new long[shape.dim_size()];
//         	for (int i = 0; i < dims.length; i++) {
//         		dims[i] = shape.dim(i).size();
//         	}
//         	return dims;
//         }

//     	private static BytePointer SHAPE = new BytePointer("shape");

//     	// Seems to do something useful, but mostly untested
//     	private static long[] nodeDefShape(NodeDef nodeDef) {
//     		if (nodeDef == null)
//     			return null;
//     		var shape = nodeDef.attr().get(SHAPE);
//     		if (shape.has_shape())
//     			return tensorShape(shape.shape());
//         	return null;
//         }

//     	/**
//     	 * Get the path to the model (a directory).
//     	 * @return
//     	 */
//     	public String getModelPath() {
//     		return pathModel;
//     	}

//     	private String getNodeSummary() {
//     		return getNodeDefs().values().stream().map(n -> tensorflow.SummarizeNodeDef(n).getString()).collect(Collectors.joining(System.lineSeparator()));
//     	}

//     	private List<String> getNodeNames() {
//     		return new ArrayList<>(getNodeDefs().keySet());
//     	}

//     	private Map<String, NodeDef> getNodeDefs() {
//     		if (nodeDefs == null) {
// 	    		nodeDefs = readNodeDefs();
//     		}
//             return nodeDefs;
//     	}

//     	private synchronized Map<String, NodeDef> readNodeDefs() {
//     		GraphDef def = bundle.meta_graph_def().graph_def();
//    			Map<String, NodeDef> nodes = new LinkedHashMap<>();
//             for (int i = 0; i < def.node_size(); i++) {
//             	var node = def.node(i);
//             	var name = node.name().getString();
//             	nodes.put(name, node);
//             }
//             return nodes;
//     	}

//         private Mat run(Mat mat, String outputName) {
//             var tensor = TensorFlowTools.convertToTensor(mat);

//             var outputs = new TensorVector();
//             var inputs = new StringTensorPairVector(
//                     new String[] {inputName},
//                     new Tensor[] {tensor}
//             );

//             var outputNames = new StringVector(outputName == null ? this.outputName : outputName);
//             var targetNodeNames = new StringVector();
//             var status = bundle.session().Run(
//                     inputs,
//                     outputNames,
//                     targetNodeNames,
//                     outputs
//             );

//             if (!status.ok()) {
//             	throw new RuntimeException(status.error_message().getString());
//             }

//             logger.debug("Number of outputs: {}", outputs.size());
//             var outputTensor = outputs.get(0L);
//             var output = TensorFlowTools.convertToMat(outputTensor);

//             inputs.close();
//             outputNames.close();
//             targetNodeNames.close();
//             outputTensor.close();

//             return output;
//         }

//         @Override
//         public String toString() {
//         	return String.format("TensorFlow bundle: %s, (input%s [%s], output=%s [%s])",
//         			pathModel, inputName, arrayToString(inputShape), outputName, arrayToString(outputShape));
//         }

//     }


}
