package qupath.lib.common;

import qupath.lib.images.servers.AbstractImageServer;
//import java.lang.Object.com.imsl.stat.CrossCorrelation;

/**
 * Functions to help with combining fluorescent channels that are the same or similar.
 *
 * @author Jaedyn Ward
 *
 */

public class ConcatChannelsABI {
    public boolean normCrossCorrelation(float[][] firstChannel, float[][] secondChannel) {
        //implement normalised cross-correlation here or find a package
        double NCCResult = 0;
        if(NCCResult > 0.85) {
            return true;
        } else {
            return false;
        }
    }
}
