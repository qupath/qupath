package qupath.lib.gui.viewer.tools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QuPathPenManager {
	
	private static Logger logger = LoggerFactory.getLogger(QuPathPenManager.class);
	
	private static PenInputManager defaultPenManager = new DefaultPenManager();
	private static PenInputManager penManager = defaultPenManager;
	
	/**
	 * Get the current PenInputManager. This is guaranteed to return a PenInputManager even if no 
	 * pen is present - in this case, a default manager will be returned.
	 * @return
	 */
	public static PenInputManager getPenManager() {
		return penManager;
	}
	
	/**
	 * Set the PenInputManager. If null, a default PenInputManager will be used.
	 * @param manager
	 */
	public static void setPenManager(PenInputManager manager) {
		if (manager == null) {
			logger.debug("Cannot set PenInputManager to null - will set it to default instead");
			penManager = defaultPenManager;
		}
		penManager = manager;
	}
	
	/**
	 * Interface defining minimal behavior for a pen input device. 
	 * This can be used to support pressure sensitivity for some commands (e.g. brush).
	 * 
	 * @author Pete Bankhead
	 */
	public static interface PenInputManager {
	
		/**
		 * Query if there is a pen currently being used as an eraser.
		 * @return true if there is an active pen currently being used as an eraser, false otherwise. 
		 */
		public boolean isEraser();
		
		/**
		 * Returns a pressure value, between 0 and 1.
		 * @return 1 if there is no pen or a pen being used with maximum pressure, otherwise a pressure value for the current pen.
		 */
		public double getPressure();
		
	}
	
	
	private static class DefaultPenManager implements PenInputManager {

		@Override
		public boolean isEraser() {
			return false;
		}

		@Override
		public double getPressure() {
			return 1.0;
		}
		
	}
	
}