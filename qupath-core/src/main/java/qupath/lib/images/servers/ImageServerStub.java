package qupath.lib.images.servers;

import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Collections;

public class ImageServerStub extends AbstractImageServer<BufferedImage> {
    private final ProjectImageEntry<BufferedImage> entry;
    private final boolean enableRead;
    private final ImageServerMetadata originalMetadata;

    public ImageServerStub(ProjectImageEntry<BufferedImage> entry, boolean enableRead) {
        super(BufferedImage.class);
        this.entry = entry;
        this.enableRead = enableRead;
        this.originalMetadata = entry.getServerBuilder().getMetadata();
    }

    protected static BufferedImage createDefaultGreyImage(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
    }

    @Override
    public BufferedImage readRegion(final RegionRequest request) throws IOException {
        if (!enableRead)
            throw new ImageStubNotReadableException(this.getClass()+" does not allow reading image files!");
        int width = (int)Math.max(1, Math.round(request.getWidth() / request.getDownsample()));
        int height = (int)Math.max(1, Math.round(request.getHeight() / request.getDownsample()));
        return createDefaultGreyImage(width, height);
    }

    @Override
    public String getServerType() {
        return "BlankStubImage";
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return this.originalMetadata;
    }

    @Override
    public Collection<URI> getURIs() {
        try {
            return this.entry.getURIs();
        } catch (IOException e) {
            return Collections.emptyList();
        }
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return null;
    }

    @Override
    protected String createID() {
        return getClass().getSimpleName() + ": " + this.getURIs().toString();
    }
}