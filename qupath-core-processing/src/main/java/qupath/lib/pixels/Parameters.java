/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2023 QuPath developers, The University of Edinburgh
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

package qupath.lib.pixels;

import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.TileRequest;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.RegionRequest;
import qupath.lib.roi.interfaces.ROI;

import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * Parameters for use with a {@link PixelProcessor}.
 * @param <S> the image type
 * @param <T> the mask type
 * @since v0.5.0
 */
public class Parameters<S, T> {

    private ImageData<BufferedImage> imageData;
    private ImageServer<BufferedImage> server;
    private TileRequest tile;
    private RegionRequest region;
    private ImageSupplier<S> imageFun;
    private MaskSupplier<S, T> maskFun;
    private transient S image;
    private transient T mask;
    private PathObject parent;

    private Parameters(Builder<S, T> builder) {
        this.imageData = builder.imageData;
        this.server = builder.server;
        this.tile = builder.tile;
        this.region = builder.region;
        this.imageFun = builder.imageFun;
        this.maskFun = builder.maskFun;
//        this.image = builder.image;
//        this.mask = builder.mask;
        this.parent = builder.parent;
        validate();
    }

    private void validate() throws IllegalArgumentException {
        if (this.image == null) {
            if (this.imageFun == null || getServer() == null)
                throw new IllegalArgumentException("Either an image or image function + server must be provided!");
        }
        if (getRegionRequest() == null)
            throw new IllegalArgumentException("Either a region request or tile request must be provided!");
        if (parent == null)
            throw new IllegalArgumentException("A parent object must be provided!");
    }

    /**
     * Get the image data.
     * @return
     */
    public ImageData<BufferedImage> getImageData() {
        return imageData;
    }

    /**
     * Get the server. This is often the accessed from {@link #getImageData()}, but in some cases it may be different
     * (e.g. if the processing should be applied to a transformed image).
     * @return
     */
    public ImageServer<BufferedImage> getServer() {
        return server == null ? imageData.getServer() : server;
    }

    /**
     * Get the region request.
     * @return
     */
    public RegionRequest getRegionRequest() {
        return region;
    }

    /**
     * Get the tile request, if available.
     * This should be used when the region request is derived at a specific tile resolution, because it is able to
     * provide more accurate tile coordinates without rounding errors.
     * @return
     */
    public TileRequest getTileRequest() {
        return tile;
    }

    /**
     * Get the image to process.
     * This may be stored in memory, or generated on demand.
     * If generated on demand it will be cached until {@link #clearCachedImages()} is called,
     * and so the parameters should not be retained for long periods of time.
     * @return
     */
    public S getImage() throws IOException {
        if (image == null) {
            synchronized (this) {
                if (image == null) {
                    image = imageFun.getImage(this);
                }
            }
        }
        return image;
    }

    /**
     * Get the mask associated with the main ROI or the parent object, or null if no mask is available.
     * Note that the mask returned should correspond to the same region as {@link #getImage()} - and <i>not</i>
     * the bounds of the ROI.
     * @return
     * @throws IOException
     */
    public T getMask() throws IOException {
        if (mask == null) {
            synchronized (this) {
                if (mask == null) {
                    var parent = getParent();
                    mask = getMask(parent == null ? null : parent.getROI());
                }
            }
        }
        return mask;
    }

    /**
     * Get the mask associated with any ROI, or null if no mask is available.
     * Note that the mask returned should correspond to the same region as {@link #getImage()} - and <i>not</i>
     * the bounds of the ROI.
     * @param roi
     * @return
     * @throws IOException
     */
    public T getMask(ROI roi) throws IOException {
        return maskFun == null ? null : maskFun.getMask(this, roi);
    }

//    /**
//     * Clear any cached images.
//     */
//    public void clearCachedImages() {
//        synchronized (this) {
//            image = null;
//            mask = null;
//        }
//    }

    /**
     * Get the parent object.
     * @return
     */
    public PathObject getParent() {
        return parent;
    }

    /**
     * Create a new builder for parameters.
     * @return
     * @param <S>
     * @param <T>
     */
    public static <S, T> Builder<S, T> builder() {
        return new Builder<>();
    }

    /**
     * Builder class for {@link Parameters}.
     * @param <S> the image type
     * @param <T> the mask type
     */
    public static class Builder<S, T> {

        private ImageData<BufferedImage> imageData;
        private ImageServer<BufferedImage> server;
        private TileRequest tile;
        private RegionRequest region;
        private ImageSupplier<S> imageFun;
        private MaskSupplier<S, T> maskFun;
        private PathObject parent;

        /**
         * Set the image data.
         * @param imageData
         * @return
         */
        public Builder<S, T> imageData(ImageData<BufferedImage> imageData) {
            this.imageData = imageData;
            return this;
        }

        /**
         * Set the server. This is optional, for cases when processing should be applied to a different image
         * from that stored in the image data.
         * @param server
         * @return
         */
        public Builder<S, T> server(ImageServer<BufferedImage> server) {
            this.server = server;
            return this;
        }

        /**
         * Specify the region to use for processing.
         * @param region
         * @return
         * @see #tile(TileRequest)
         */
        public Builder<S, T> region(RegionRequest region) {
            this.region = region;
            return this;
        }

        /**
         * Specify the tile relevant for processing.
         * If the regions are available as tile requests, these should be used instead of region requests -
         * because they enable more accurate contour tracing across tile boundaries.
         * @param tile
         * @return
         * @see #region(RegionRequest)
         */
        public Builder<S, T> tile(TileRequest tile) {
            this.tile = tile;
            return region(tile.getRegionRequest());
        }

        /**
         * Function used ot lazily read the image.
         * @param function
         * @return
         */
        public Builder<S, T> imageFunction(ImageSupplier<S> function) {
            this.imageFun = function;
            return this;
        }

        /**
         * Function used to lazily create a mask corresponding to the image for a specified ROI.
         * @param function
         * @return
         */
        public Builder<S, T> maskFunction(MaskSupplier<S, T> function) {
            this.maskFun = function;
            return this;
        }

        /**
         * Set the parent object.
         * @param parent
         * @return
         */
        public Builder<S, T> parent(PathObject parent) {
            this.parent = parent;
            return this;
        }

        /**
         * Build the parameters.
         * @return
         */
        public Parameters<S, T> build() {
            return new Parameters<>(this);
        }

    }


}