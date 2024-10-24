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

package qupath.lib.images.servers;

import qupath.lib.awt.common.BufferedImageTools;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * An ImageServer implementation that converts the pixel type of an image.
 *
 * @since v0.6.0
 */
public class TypeConvertImageServer extends AbstractTileableImageServer {

    private static final Set<PixelType> INVALID_TYPES = Set.of(
            PixelType.INT8, PixelType.UINT32
    );

    private final ImageServer<BufferedImage> server;
    private final PixelType pixelType;
    private final ImageServerMetadata metadata;
//
    protected TypeConvertImageServer(ImageServer<BufferedImage> server, PixelType outputType) {
        super();
        if (INVALID_TYPES.contains(outputType)) {
            throw new IllegalArgumentException("Invalid pixel type: " + outputType);
        }
        this.server = server;
        this.pixelType = outputType;
        this.metadata = new ImageServerMetadata.Builder(server.getMetadata())
                .pixelType(outputType)
                .rgb(outputType == PixelType.UINT8 &&
                        Objects.equals(server.getMetadata().getChannels(), ImageChannel.getDefaultRGBChannels()))
                .name(server.getMetadata().getName() + " (" + outputType + ")")
                .build();
    }

    /**
     * Get underlying ImageServer, i.e. the one that is being wrapped.
     *
     * @return
     */
    protected ImageServer<BufferedImage> getWrappedServer() {
        return server;
    }

    @Override
    public Collection<URI> getURIs() {
        return getWrappedServer().getURIs();
    }

    @Override
    public String getServerType() {
        return "Type convert (" + pixelType + ")";
    }

    @Override
    public ImageServerMetadata getOriginalMetadata() {
        return metadata;
    }

    @Override
    protected BufferedImage readTile(TileRequest tileRequest) throws IOException {
        var img = getWrappedServer().readRegion(tileRequest.getRegionRequest());
        return BufferedImageTools.convertImageType(img, pixelType, getMetadata().getChannels());
    }

    @Override
    protected ImageServerBuilder.ServerBuilder<BufferedImage> createServerBuilder() {
        return new ImageServers.TypeConvertImageServerBuilder(getMetadata(), getWrappedServer().getBuilder(), pixelType);
    }

    @Override
    protected String createID() {
        return "Type converted: " + getWrappedServer().getPath() + " (" + pixelType + ")";
    }
}
