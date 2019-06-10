package qupath.lib.classifiers.opencv.pixel.features;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.javacpp.indexer.FloatIndexer;
import org.bytedeco.opencv.opencv_core.Mat;

import com.google.gson.annotations.JsonAdapter;

import qupath.lib.classifiers.gui.PixelClassifierStatic;
import qupath.lib.geom.ImmutableDimension;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.io.OpenCVTypeAdapters;
import qupath.lib.regions.RegionRequest;

/**
     * Feature calculator that simply takes a square of neighboring pixels as the features.
     * <p>
     * Warning! This is far from complete and may well be removed.
     * <p>
     * Note also that it only extends BasicFeatureCalculator because that is required by the OpenCVPixelClassifier... it shouldn't really.
     * 
     * @author Pete Bankhead
     *
     */
    @JsonAdapter(OpenCVTypeAdapters.OpenCVTypeAdaptorFactory.class)
    public class ExtractNeighborsFeatureCalculator extends BasicFeatureCalculator {
    	
    	private int radius;
    	private List<String> featureNames;
    	private int[] inputChannels;
    	private int n;
    	
    	private ImmutableDimension inputShape = new ImmutableDimension(256, 256);
    	
    	public ExtractNeighborsFeatureCalculator(String name, double pixelSizeMicrons, int radius, int...inputChannels) {
    		super(name, Collections.emptyList(), Collections.emptyList(), pixelSizeMicrons);
    		this.radius = radius;
			
    		inputChannels = new int[] {0, 1, 2};
    		
    		n = (radius * 2 + 1) * (radius * 2 + 1) * inputChannels.length;
			this.inputChannels = inputChannels;
					
			featureNames = IntStream.range(0, n)
    				.mapToObj(c -> "Feature " + c)
    				.collect(Collectors.toList());
    	}

		@Override
		public Mat calculateFeatures(ImageServer<BufferedImage> server, RegionRequest request) throws IOException {
			var img = PixelClassifierStatic.getPaddedRequest(server, request, radius);
			var raster = img.getRaster();
			
			n = (radius * 2 + 1) * (radius * 2 + 1) * inputChannels.length;
			
			var mat = new Mat(img.getHeight()-radius*2, img.getWidth()-radius*2, opencv_core.CV_32FC(n));
			
			int rows = mat.rows();
			int cols = mat.cols();
			
			FloatIndexer idx = mat.createIndexer();
			
			for (long r = 0; r < rows; r++) {
				for (long c = 0; c < cols; c++) {
					long k = 0;
					for (int b : inputChannels) {
						for (int y = (int)r; y < r + radius * 2 + 1; y++) {
							for (int x = (int)c; x < c + radius * 2 + 1; x++) {
								float val = raster.getSampleFloat(x, y, b);
								//								System.err.println(r + ", " + c + ", " + k);
								idx.put(r, c, k, val);
								k++;
							}							
						}
					}				
				}
			}
			idx.release();
			
//			matToImagePlus(mat, "Features").show();
			
			return mat;
		}

		@Override
		public List<String> getFeatureNames() {
			return featureNames;
		}
    	
    	
    }