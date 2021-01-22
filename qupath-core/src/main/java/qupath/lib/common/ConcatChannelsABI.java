package qupath.lib.common;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.*;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


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
    public static boolean normCrossCorrelation(float[] firstChannel, float[] secondChannel) {
        float nominator = 0;
        float firstDenominator = 0;
        float secondDenominator = 0;
        for(int i = 0; i < firstChannel.length; i++) {
                nominator += firstChannel[i] * secondChannel[i];
                firstDenominator += (firstChannel[i] * firstChannel[i]);
                secondDenominator += (secondChannel[i] * secondChannel[i]);
        }
        if(nominator/(float)(Math.sqrt((firstDenominator * secondDenominator))) > 0.85) {
            return true;
        } else {
            return false;
        }
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

//    public static float[] getPixelIntensities(BufferedImage img, int channel, float[] array) {
//        if (array == null || array.length < w * h)
//            array = new float[w * h];
//        int x = img.getWidth();
//        int y = img.getHeight();
//        float[] pixelIntensities = img.getRaster().getSamples(x, y, x, y, channel, array);
//        return pixelIntensities;
//    }

    /**
     * Call setChannelColors method with the channel colours used regularly with 7 channels
     *
     * @param imageData
     */
    public static void setRegularChannelColours(ImageData<?> imageData){
        Integer[] regularChannelColourArray = new Integer[7];
        regularChannelColourArray[0] = ColorTools.makeRGB(0,0,0);
        regularChannelColourArray[1] = ColorTools.makeRGB(0,0,0);
        regularChannelColourArray[2] = ColorTools.makeRGB(0,0,0);
        regularChannelColourArray[3] = ColorTools.makeRGB(0,0,0);
        regularChannelColourArray[4] = ColorTools.makeRGB(0,0,0);
        regularChannelColourArray[5] = ColorTools.makeRGB(0,0,0);
        regularChannelColourArray[6] = ColorTools.makeRGB(0,0,0);
        //TODO: set the regular 7 colours in the array
        setChannelColors(imageData, regularChannelColourArray);
    }

    public static void concatDuplicateChannels(ImageData<?> imageData) throws IOException {
        int nChannels = imageData.getServer().nChannels();
        if(isExcessChannels(nChannels)) {
            ImageServerMetadata newMetadata = imageData.getServer().getMetadata().duplicate();
            RegionRequest request = RegionRequest.createInstance(imageData.getServer());
            BufferedImage img = (BufferedImage) imageData.getServer().readBufferedImage(request);
            List<Integer> duplicates = null;
            float[] tmpChannelOne;
            float[] tmpChannelTwo;
            int width = img.getWidth();
            int height = img.getHeight();
            float[] array = new float[width * height];
            for(int channelOne = 0; channelOne < nChannels - 1; channelOne++) {
                //only check for duplicates in channels that aren't already considered duplicates
                if(!duplicates.contains(channelOne)) {
                    tmpChannelOne = img.getRaster().getSamples(width, height, width, height, channelOne, array);
                    for(int channelTwo = 1; channelTwo < nChannels; channelTwo++) {
                        tmpChannelTwo = img.getRaster().getSamples(width, height, width, height, channelTwo, array);
                        if(normCrossCorrelation(tmpChannelOne, tmpChannelTwo)) {
                            duplicates.add(channelTwo);
                        }
                    }
                }
            }
            imageData.getServer().setMetadata(newMetadata);
            setRegularChannelColours(imageData);
            //TODO: remove duplicate channels from the image server using duplicateChannelNumbers.
        }
    }
}
