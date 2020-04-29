package qupath.tensorflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.extensions.QuPathExtension;

/**
 * Experimental extension to connect QuPath and TensorFlow.
 * 
 * @author Pete Bankhead
 */
public class TensorFlowExtension implements QuPathExtension {
	
	private final static Logger logger = LoggerFactory.getLogger(TensorFlowExtension.class);

	@Override
	public void installExtension(QuPathGUI qupath) {
		logger.debug("Installing TensorFlow extension");
		
		// TODO Auto-generated method stub
	}

	@Override
	public String getName() {
		return "TensorFlow extension";
	}

	@Override
	public String getDescription() {
		return "Add TensorFlow support to QuPath";
	}
	
	
	
}