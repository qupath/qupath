package qupath.lib.pixels;

import qupath.lib.awt.common.BufferedImageTools;
import qupath.lib.images.ImageData;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class BasicPixelProcessor<U> extends PixelProcessor<BufferedImage, BufferedImage, U> {

    private final Processor<BufferedImage, BufferedImage, U> processor;

    public BasicPixelProcessor(Processor<BufferedImage, BufferedImage, U> processor) {
        super(processor);
        this.processor = processor;
    }

    protected BufferedImage getImage(ImageData<BufferedImage> imageData, RegionRequest request, PathObject pathObject) throws IOException {
        return imageData.getServer().readBufferedImage(request);
    }

    protected BufferedImage getMask(ImageData<BufferedImage> imageData, RegionRequest request, PathObject pathObject, BufferedImage image) {
        return BufferedImageTools.createROIMask(image.getWidth(), image.getHeight(), pathObject.getROI(), request);
    }

    @Override
    protected void handleOutput(ImageData<BufferedImage> imageData, RegionRequest request, PathObject pathObject, BufferedImage image, U output) {

    }


}
