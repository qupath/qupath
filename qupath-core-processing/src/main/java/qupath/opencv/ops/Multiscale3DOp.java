package qupath.opencv.ops;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.Mat;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ColorTransforms;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.images.servers.ServerTools;
import qupath.lib.regions.Padding;
import qupath.lib.regions.RegionRequest;
import qupath.opencv.tools.MultiscaleFeatures;
import qupath.opencv.tools.OpenCVTools;

// TODO: Call ImageOps.registerDataOps at a suitable point
// TODO: Consider use of LocalNormalization.SmoothingScale to adjust how smoothing is performed.
//       This could also make the op work for both 2D and 3D.
class Multiscale3DOp implements ImageDataOp {

    private final List<ColorTransforms.ColorTransform> colorTransforms;
    private final double[] scales;
    private final Set<MultiscaleFeatures.MultiscaleFeature> features;
    private final ImageOp postprocessing;

    private transient volatile Padding basePadding;

    Multiscale3DOp(Collection<? extends ColorTransforms.ColorTransform> colorTransforms,
                   Collection<MultiscaleFeatures.MultiscaleFeature> features,
                   double[] scales) {
        this(colorTransforms, features, scales, null);
    }

    private Multiscale3DOp(Collection<? extends ColorTransforms.ColorTransform> colorTransforms,
                           Collection<MultiscaleFeatures.MultiscaleFeature> features,
                           double[] scales,
                           ImageOp postprocessing) {
        this.colorTransforms = List.copyOf(colorTransforms);
        this.features = new LinkedHashSet<>(features);
        this.scales = scales.clone();
        this.postprocessing = postprocessing;
    }

    @Override
    public Mat apply(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException {

        var builder = resultsBuilder();

        List<BufferedImage> images = new ArrayList<>();
        List<Mat> matsInput = new ArrayList<>();
        List<Mat> matsOutput = new ArrayList<>();

        var basePadding = getBasePadding();
        var totalPadding = postprocessing == null ? basePadding : basePadding.add(postprocessing.getPadding());

        var server = imageData.getServer();
        for (int z = 0; z < server.nZSlices(); z++) {
            var requestZ = request.updateZ(z);
            var img = ServerTools.getPaddedRequest(imageData.getServer(), requestZ, totalPadding);
            images.add(img);
        }

        float[] pixels = null;
        // Loop through channels / color transforms
        for (var channel : getColorTransforms(imageData)) {
            // Loop through z-slices to build a list of Mat for input
            for (int z = 0; z < server.nZSlices(); z++) {
                var img = images.get(z);
                if (pixels == null)
                    pixels = new float[img.getWidth() * img.getHeight()];
                channel.extractChannel(server, img, pixels);

                // Create or reuse an image
                Mat mat;
                if (matsInput.size() <= z) {
                    mat = new Mat(img.getHeight(), img.getWidth(), opencv_core.CV_32FC1);
                    matsInput.add(mat);
                } else {
                    mat = matsInput.get(z);
                }
                OpenCVTools.putPixelsFloat(mat, pixels);
            }

            // Loop through scales
            for (double sigma : scales) {
                // Sigma is defined in terms of pixels - so we don't pass the pixel calibration to the results builder
                var cal = server.getPixelCalibration();
                double zScale = 1.0;
                if (cal.hasPixelSizeMicrons() && cal.hasZSpacingMicrons()) {
                    zScale = cal.getAveragedPixelSizeMicrons() / cal.getZSpacingMicrons();
                }
                var featureMap = builder
                        .sigmaXY(sigma)
                        .sigmaZ(sigma * zScale)
                        .build(matsInput, request.getZ());

                for (var feature : features) {
                    matsOutput.add(featureMap.get(feature));
                }
            }
        }

        var output = OpenCVTools.mergeChannels(matsOutput, null);
        if (postprocessing != null)
            output = postprocessing.apply(output);
        return ImageOps.stripPadding(output, basePadding);
    }

    private Padding getBasePadding() {
        if (basePadding == null) {
            synchronized (this) {
                if (basePadding == null) {
                    basePadding = computeBasePadding();
                }
            }
        }
        return basePadding;
    }

    private List<ColorTransforms.ColorTransform> getColorTransforms(ImageData<?> imageData) {
        if (colorTransforms.isEmpty()) {
            List<ColorTransforms.ColorTransform> transforms = new ArrayList<>();
            for (int i = 0; i < imageData.getServer().nChannels(); i++) {
                transforms.add(ColorTransforms.createChannelExtractor(i));
            }
            return transforms;
        } else {
            return colorTransforms;
        }
    }

    private synchronized Padding computeBasePadding() {
        int pad = (int) Math.ceil(Arrays.stream(scales).max().orElse(0) * 6);
        return Padding.symmetric(pad);
    }


    private MultiscaleFeatures.MultiscaleResultsBuilder resultsBuilder() {
        return new MultiscaleFeatures.MultiscaleResultsBuilder()
                .gaussianSmoothed(features.contains(MultiscaleFeatures.MultiscaleFeature.GAUSSIAN))
                .laplacianOfGaussian(features.contains(MultiscaleFeatures.MultiscaleFeature.LAPLACIAN))
                .hessianDeterminant(features.contains(MultiscaleFeatures.MultiscaleFeature.HESSIAN_DETERMINANT))
                .hessianEigenvalues(
                        features.contains(MultiscaleFeatures.MultiscaleFeature.HESSIAN_EIGENVALUE_MAX) ||
                                features.contains(MultiscaleFeatures.MultiscaleFeature.HESSIAN_EIGENVALUE_MIDDLE) ||
                                features.contains(MultiscaleFeatures.MultiscaleFeature.HESSIAN_EIGENVALUE_MIN))
                .gradientMagnitude(features.contains(MultiscaleFeatures.MultiscaleFeature.GRADIENT_MAGNITUDE))
                .structureTensorEigenvalues(
                        features.contains(MultiscaleFeatures.MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MAX) ||
                                features.contains(MultiscaleFeatures.MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MIDDLE) ||
                                features.contains(MultiscaleFeatures.MultiscaleFeature.STRUCTURE_TENSOR_EIGENVALUE_MIN) ||
                                features.contains(MultiscaleFeatures.MultiscaleFeature.STRUCTURE_TENSOR_COHERENCE)
                )
                .weightedStdDev(features.contains(MultiscaleFeatures.MultiscaleFeature.WEIGHTED_STD_DEV));
    }

    @Override
    public boolean supportsImage(ImageData<BufferedImage> imageData) {
        if (imageData.getServer().nZSlices() == 1)
            return false;
        for (var channel : colorTransforms) {
            if (!channel.supportsImage(imageData.getServer()))
                return false;
        }
        return true;
    }

    @Override
    public List<ImageChannel> getChannels(ImageData<BufferedImage> imageData) {
        List<String> names = new ArrayList<>();
        for (var transform : getColorTransforms(imageData)) {
            for (var scale : scales) {
                for (var feature : features) {
                    names.add(
                            transform.getName() + "-" + feature.toString() + " (sigma=" + GeneralTools.formatNumber(scale, 2) + ")"
                    );
                }
            }
        }
        var channels = ImageChannel.getChannelList(names.toArray(String[]::new));
        if (postprocessing == null)
            return channels;
        else
            return postprocessing.getChannels(channels);
    }

    @Override
    public ImageDataOp appendOps(ImageOp... ops) {
        if (ops.length == 0)
            return this;
        List<ImageOp> sequential = new ArrayList<>();
        if (postprocessing != null)
            sequential.add(postprocessing);
        for (var op : ops) {
            sequential.add(op);
        }
        return new Multiscale3DOp(
                colorTransforms,
                features,
                scales,
                ImageOps.Core.sequential(sequential)
        );
    }

    @Override
    public PixelType getOutputType(PixelType inputType) {
        // Feature output is float32
        if (postprocessing == null)
            return PixelType.FLOAT32;
        else
            return postprocessing.getOutputType(PixelType.FLOAT32);
    }

    @Override
    public Collection<URI> getURIs() {
        return List.of();
    }

    @Override
    public boolean updateURIs(Map<URI, URI> replacements) {
        return false;
    }
}
