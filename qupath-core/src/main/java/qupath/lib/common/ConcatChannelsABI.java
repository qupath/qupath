package qupath.lib.common;

import qupath.lib.images.servers.*;
import qupath.lib.regions.RegionRequest;

import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.List;
//import java.lang.Object.com.imsl.stat.CrossCorrelation;

/**
 * Functions to help with combining fluorescent channels that are the same or similar.
 *
 * @author Jaedyn Ward
 *
 */

public class ConcatChannelsABI {

    /**
     * This method is used to compare two channels together to see if they are similar or not using normalised cross-correlation.
     *
     * @param firstChannel
     * @param secondChannel
     */
    public static boolean normCrossCorrelation(float[][] firstChannel, float[][] secondChannel) {
        //TODO: implement normalised cross-correlation here or use a package
        double NCCResult = 0;
        if(NCCResult > 0.85) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * This method is used to convert a regular ImageChannel into a float array so it can be compared using
     * normalised cross-correlation.
     *
     * @param imgChnl
     */
    public static float[][] convertChannelToFloatArray(ImageChannel imgChnl) {
        //TODO: implement converting an ImageChannel object into an array of the pixel values for the individual channel.
        float[][] channelArray = null;
        return channelArray;
    }

    public static ImageServer concatDuplicateChannels(ImageServer imageServer){
        int nChannels = imageServer.nChannels();
        List<Integer> duplicateChannelNumbers = null;
        float[][] tmpChannelOne;
        float[][] tmpChannelTwo;

        for(int i = 0; i < nChannels - 1; i++) {
            for(int j = 1; j < nChannels; j++) {
                tmpChannelOne = convertChannelToFloatArray(imageServer.getChannel(i));
                tmpChannelTwo = convertChannelToFloatArray(imageServer.getChannel(j));
                if(normCrossCorrelation(tmpChannelOne, tmpChannelTwo)) {
                    duplicateChannelNumbers.add(j);
                    i++;
                    j++;
                }
            }
        }
        //TODO: remove duplicate channels from the image server using duplicateChannelNumbers.
        return imageServer;
    }
}
