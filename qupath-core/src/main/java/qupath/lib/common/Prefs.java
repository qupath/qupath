package qupath.lib.common;

/**
 * Core QuPath preferences. Currently these are not persistent, but this behavior may change in the future.
 * 
 * @author Pete Bankhead
 */
public class Prefs {
	
	private static int nThreads = Runtime.getRuntime().availableProcessors() - 1;
	
	/**
	 * Get the requested number of threads to use for parallelization.
	 * @return
	 */
	public static int getNumThreads() {
		return nThreads;
	}

	/**
	 * Set the requested number of threads. This will be clipped to be at least 1.
	 * @param n
	 */
	public static void setNumThreads(int n) {
		nThreads = Math.max(1, n);
	}

}
