package qupath.lib.common;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.*;

import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * This method is used to check if there are more than 7 channels and therefore whether channels should be concatenated or not.
     *
     * @param nChannels
     */
    public static boolean isExcessChannels(int nChannels) {
        if(nChannels >= 42) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @author Pete Bankhead
     * Set the channel colors for the specified ImageData.
     * It is not essential to pass names for all channels:
     * by passing n values, the first n channel names will be set.
     * Any name that is null will be left unchanged.
     *
     * @param imageData
     * @param colors
     */
    public static void setChannelColors(ImageData<?> imageData, Integer... colors) {
        List<ImageChannel> oldChannels = imageData.getServer().getMetadata().getChannels();
        List<ImageChannel> newChannels = new ArrayList<>(oldChannels);
        for (int i = 0; i < colors.length; i++) {
            Integer color = colors[i];
            if (color == null)
                continue;
            newChannels.set(i, ImageChannel.getInstance(newChannels.get(i).getName(), color));
            if (i >= newChannels.size()) {
                break;
            }
        }
        setChannels(imageData, newChannels.toArray(ImageChannel[]::new));
    }

    /**
     * @author Pete Bankhead
     * Set the channels for the specified ImageData.
     * Note that number of channels provided must match the number of channels of the current image.
     * <p>
     * Also, currently it is not possible to set channels for RGB images - attempting to do so
     * will throw an IllegalArgumentException.
     *
     * @param imageData
     * @param channels
     */
    public static void setChannels(ImageData<?> imageData, ImageChannel... channels) {
        ImageServer<?> server = imageData.getServer();
        if (server.isRGB()) {
            throw new IllegalArgumentException("Cannot set channels for RGB images");
        }
        List<ImageChannel> oldChannels = server.getMetadata().getChannels();
        List<ImageChannel> newChannels = Arrays.asList(channels);
        if (oldChannels.size() != newChannels.size())
            throw new IllegalArgumentException("Cannot set channels - require " + oldChannels.size() + " channels but you provided " + channels.length);

        // Set the metadata
        var metadata = server.getMetadata();
        var metadata2 = new ImageServerMetadata.Builder(metadata)
                .channels(newChannels)
                .build();
        imageData.updateServerMetadata(metadata2);
    }

    /**
     * Call setChannelColors method with the channel colours used regularly with 7 channels
     *
     * @param imageData
     */
    public static void setRegularChannelColours(ImageData<?> imageData){
        Integer[] regularChannelColourArray = new Integer[7];
        //TODO: set the regular 7 colours in the array
        setChannelColors(imageData, regularChannelColourArray);
    }

    public static void concatDuplicateChannels(ImageData<?> imageData){
        int nChannels = imageData.getServer().nChannels();
        List<Integer> duplicateChannelNumbers = null;
        float[][] tmpChannelOne;
        float[][] tmpChannelTwo;
        for(int i = 0; i < nChannels - 1; i++) {
            for(int j = 1; j < nChannels; j++) {
                tmpChannelOne = convertChannelToFloatArray(imageData.getServer().getChannel(i));
                tmpChannelTwo = convertChannelToFloatArray(imageData.getServer().getChannel(j));
                if(normCrossCorrelation(tmpChannelOne, tmpChannelTwo)) {
                    duplicateChannelNumbers.add(j);
                    i++;
                    j++;
                }
            }
        }
        setRegularChannelColours(imageData);
        //TODO: remove duplicate channels from the image server using duplicateChannelNumbers.
    }
}
