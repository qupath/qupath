package qupath.opencv.ops;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import org.bytedeco.opencv.opencv_core.Mat;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;
import qupath.lib.regions.RegionRequest;

/**
 * Request pixels from an image, potentially applying further transforms.
 * 
 * @author Pete Bankhead
 */
public interface ImageDataOp {
	
	/**
	 * Apply the operation to the requested region of the image.
	 * <p>
	 * The data and region are provided, rather than specified pixels, because the operation might require 
	 * additional information available within the {@link ImageData}, and optionally adjust the request 
	 * (e.g. to incorporate padding to remove boundary artifacts).
	 * <p>
	 * Note for implementers: Any such padding must be removed from the output before it is returned.
	 * 
	 * @param imageData
	 * @param request
	 * @return
	 * @throws IOException
	 */
	Mat apply(ImageData<BufferedImage> imageData, RegionRequest request) throws IOException;
	
	/**
	 * Query whether this transform can be applied to the specified image.
	 * Reasons why it may not be include the type or channel number being incompatible.
	 * @param imageData
	 * @return
	 */
	boolean supportsImage(ImageData<BufferedImage> imageData);
	
	/**
	 * Get appropriate channels to reflect the output of this transform, given the input.
	 * 
	 * @param imageData 
	 * 
	 * @return
	 */
	List<ImageChannel> getChannels(ImageData<BufferedImage> imageData);
	
	/**
	 * Append one or more additional {@link ImageOp}s sequentially.
	 * This {@link ImageDataOp} is unchanged, and a new one is created with the additional op appended.
	 * @param ops
	 * @return
	 */
	ImageDataOp appendOps(ImageOp... ops);
	
	/**
	 * Get the output pixel type.
	 * 
	 * @param inputType the pixel type of the input image
	 * @return the output pixel type
	 */
	public PixelType getOutputType(PixelType inputType);
	
}