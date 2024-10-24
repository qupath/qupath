/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2024 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.images.servers.transforms;

import org.junit.jupiter.api.Test;
import qupath.lib.color.ColorModelFactory;
import qupath.lib.images.servers.ImageChannel;
import qupath.lib.images.servers.PixelType;

import java.awt.image.BandedSampleModel;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.DataBufferByte;
import java.awt.image.DataBufferDouble;
import java.awt.image.DataBufferFloat;
import java.awt.image.DataBufferInt;
import java.awt.image.DataBufferUShort;
import java.awt.image.WritableRaster;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestSubtractOffsetAndScaleNormalizer {

    @Test
    public void testFloat32() {
        var imgOnes = createImage(PixelType.FLOAT32, 1, 1, 1, 1);

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.createSubtractOffset(0, 1, 2, 3).filter(imgOnes, null),
                1, 0, -1, -2
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.createSubtractOffsetAndClipZero(0, 1, 2, 3).filter(imgOnes, null),
                1, 0, 0, 0
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.createScaled(1, -3, 0, 2).filter(imgOnes, null),
                1, -3, 0, 2
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.createWithClipRange(
                        new double[]{0, 0, 0, 0},
                        new double[]{1, -3, 0, 2}, -1, 1).filter(imgOnes, null),
                1, -1, 0, 1
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.create(
                        new double[]{1, 2, 3, 4},
                        new double[]{1, -3, 0, 2}).filter(imgOnes, null),
                0, 3, 0, -6
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.create(
                        null,
                        new double[]{1, 0.5, 0.25, 300}).filter(imgOnes, null),
                1, 0.5, 0.25, 300
        );
    }

    @Test
    public void testUint8() {
        var imgOnes = createImage(PixelType.UINT8, 1, 1, 1, 1);

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.createSubtractOffset(0, 1, 2, 3).filter(imgOnes, null),
                1, 0, 0, 0
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.createSubtractOffsetAndClipZero(0, 1, 2, 3).filter(imgOnes, null),
                1, 0, 0, 0
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.createScaled(1, -3, 0, 2).filter(imgOnes, null),
                1, 0, 0, 2
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.createWithClipRange(
                        new double[]{0, 0, 0, 0},
                        new double[]{1, -3, 0, 2}, -1, 1).filter(imgOnes, null),
                1, 0, 0, 1
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.create(
                        new double[]{1, 2, 3, 4},
                        new double[]{1, -3, 0, 2}).filter(imgOnes, null),
                0, 3, 0, 0
        );

        checkChannelPixelValues(
                SubtractOffsetAndScaleNormalizer.create(
                        null,
                        new double[]{1, 0.5, 0.25, 300}).filter(imgOnes, null),
                1, 1, 0, 255
        );
    }

    private void checkChannelPixelValues(BufferedImage img, double... channelValues) {
        var raster = img.getRaster();
        for (int i = 0; i < channelValues.length; i++) {
            assertEquals(channelValues[i], raster.getSampleDouble(0, 0, i), 1e-6);
        }
    }


    private BufferedImage createImage(PixelType type, double... channelValues) {
        int nChannels = channelValues.length;
        DataBuffer dataBuffer = switch(type) {
            case UINT8 -> new DataBufferByte(1, nChannels);
            case UINT16 -> new DataBufferUShort(1, nChannels);
            case UINT32 -> new DataBufferInt(1, nChannels);
            case FLOAT32 -> new DataBufferFloat(1, nChannels);
            case FLOAT64 -> new DataBufferDouble(1, nChannels);
            default ->
                throw new UnsupportedOperationException("Unsupported pixel type: " + type);
        };
        var sampleModel = new BandedSampleModel(dataBuffer.getDataType(), 1, 1, nChannels);
        var raster = WritableRaster.createWritableRaster(sampleModel, dataBuffer, null);
        for (int i = 0; i < nChannels; i++)
            raster.setSample(0, 0, i, channelValues[i]);
        return new BufferedImage(
                ColorModelFactory.createColorModel(type, ImageChannel.getDefaultChannelList(nChannels)),
                raster,
                false,
                null);
    }


}
