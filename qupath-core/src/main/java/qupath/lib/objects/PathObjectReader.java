package qupath.lib.objects;

import java.io.IOException;
import java.util.Collection;

import qupath.lib.images.servers.ImageServer;

/**
 * Interface for classes capable of reading {@linkplain PathObject PathObjects} from some source.
 * This may be used in conjunction with an {@link ImageServer} to indicate that the server can read objects 
 * as well as pixels from its source.
 * 
 * @author Pete Bankhead
 *
 */
public interface PathObjectReader {
	
	/**
	 * Read a collection of objects from the source.
	 * @return a collection of objects, or empty list if no objects are available.
	 * @throws IOException 
	 */
	public Collection<PathObject> readPathObjects() throws IOException;

}
