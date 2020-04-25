package qupath.opencv.operations;

import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;

/**
 * An {@link ImageServer} that wraps an {@link ImageData}.
 * This can be used if the server requires additional information within the {@link ImageData}, such as {@link ColorDeconvolutionStains}.
 * <p>
 * Warning: because many properties of the {@link ImageData} are mutable, yet {@link ImageServer}s generally are not (apart from their metadata), 
 * this interface should be used sparingly - and only temporarily (e.g. during a single processing operation).
 * 
 * @author Pete Bankhead
 * @param <T>
 */
public interface ImageDataServer<T> extends ImageServer<T> {
	
	/**
	 * Get the {@link ImageData} wrapped by the {@link ImageDataServer}.
	 * @return
	 */
	public ImageData<T> getImageData();
	
}