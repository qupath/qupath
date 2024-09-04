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

package qupath.lib.objects.utils;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.geom.prep.PreparedGeometry;
import org.locationtech.jts.geom.prep.PreparedGeometryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.objects.PathAnnotationObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.PathTileObject;
import qupath.lib.roi.GeometryTools;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A class used to split {@link ROI} or {@link Geometry} objects into rectangular tiles.
 * This is useful for breaking up large images into manageable pieces.
 * <p>
 * The Tiler is immutable and threadsafe.
 * @since v0.5.0
 */
public class Tiler {

    private static final Logger logger = LoggerFactory.getLogger(Tiler.class);

    /**
     * Enum representing the possible alignments for tiles.
     * A tile alignment of TOP_LEFT indicates that tiling should begin at the top left bounding box,
     * and if cropping is required then this will occur at the right and bottom.
     * An alignment of CENTER indicates that tiles may be cropped on all sides.
     */
    public enum TileAlignment {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        CENTER_LEFT, CENTER, CENTER_RIGHT,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
    }

    private final int tileWidth;
    private final int tileHeight;
    private final boolean cropToParent;
    private final boolean filterByCentroid;
    private final TileAlignment alignment;

    /**
     * Constructor.
     * @param tileWidth tile width in pixels.
     * @param tileHeight tile height in pixels.
     * @param cropToParent controls whether tiles should be cropped to fit
     *                     within the parent object.
     * @param alignment controls where the tiling begins, and consequently where any
     *                      cropping or overlaps will occur if the region being tiled is
     *                      not an exact multiple of the tile size.
     * @param filterByCentroid controls whether tiles whose centroid is outwith
     *                         the parent object will be removed from the
     *                         output.
     */
    private Tiler(int tileWidth, int tileHeight,
                  boolean cropToParent, TileAlignment alignment,
                  boolean filterByCentroid) {
        if (tileWidth <= 0 || tileHeight <= 0)
            throw new IllegalArgumentException("tileWidth and tileHeight must be > 0, but were "
                    + tileWidth + " and " + tileHeight);
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.cropToParent = cropToParent;
        this.alignment = alignment;
        this.filterByCentroid = filterByCentroid;
    }

    /**
     * Get the width of output tiles
     * @return the width in pixels
     */
    public int getTileWidth() {
        return tileWidth;
    }

    /**
     * Get the height of output tiles
     * @return the height in pixels
     */
    public int getTileHeight() {
        return tileHeight;
    }

    /**
     * Check if the tiler is set to crop the output to the input parent.
     * @return whether the tiler is set to crop output to the parent object
     */
    public boolean getCropToParent() {
        return cropToParent;
    }

    /**
     * Get the tiling alignment.
     * @return The current setting
     */
    public TileAlignment getAlignment() {
        return alignment;
    }

    /**
     * Check if the tiler will filter the output based on whether the centroid
     * of tiles lies within the parent
     * @return The current setting
     */
    public boolean getFilterByCentroid() {
        return filterByCentroid;
    }

    /**
     * Create a list of {@link Geometry} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @return a list of tiles
     */
    public List<Geometry> createGeometries(Geometry parent) {
        if (parent == null) {
            logger.warn("Tiler.createGeometries() called with null parent - no tiles will be created");
            return new ArrayList<>();
        }

        Envelope boundingBox = parent.getEnvelopeInternal();

        double xStart = boundingBox.getMinX();
        double xEnd = boundingBox.getMaxX();
        switch (alignment) {
            case TOP_LEFT:
            case CENTER_LEFT:
            case BOTTOM_LEFT:
                break;
            case TOP_CENTER:
            case CENTER:
            case BOTTOM_CENTER:
                double bBoxWidth = xEnd - xStart;
                if (filterByCentroid) {
                    // Shift 'inside' the parent
                    xStart += calculateInteriorOffset(tileWidth, bBoxWidth);
                } else {
                    // Shift 'outside' the parent
                    xStart += calculateExteriorOffset(tileWidth, bBoxWidth);
                }
                break;
            case TOP_RIGHT:
            case CENTER_RIGHT:
            case BOTTOM_RIGHT:
                xStart += calculateRightAlignedStartOffset(tileWidth, xEnd - xStart);
                break;
        }

        double yStart = boundingBox.getMinY();
        double yEnd = boundingBox.getMaxY();
        switch (alignment) {
            case TOP_LEFT:
            case TOP_CENTER:
            case TOP_RIGHT:
                break;
            case CENTER_LEFT:
            case CENTER:
            case CENTER_RIGHT:
                double bBoxHeight = yEnd - yStart;
                if (filterByCentroid) {
                    // Shift 'inside' the parent
                    yStart += calculateInteriorOffset(tileHeight, bBoxHeight);
                } else {
                    // Shift 'outside' the parent
                    yStart += calculateExteriorOffset(tileHeight, bBoxHeight);
                }
                break;
            case BOTTOM_LEFT:
            case BOTTOM_CENTER:
            case BOTTOM_RIGHT:
                yStart += calculateRightAlignedStartOffset(tileHeight, yEnd - yStart);
                break;
        }

        List<Geometry> tiles = new ArrayList<>();
        for (int x = (int) xStart; x < xEnd; x += tileWidth) {
            for (int y = (int) yStart; y < yEnd; y += tileHeight) {
                tiles.add(GeometryTools.createRectangle(x, y, tileWidth, tileHeight));
            }
        }

        var preparedParent = PreparedGeometryFactory.prepare(parent);
        return tiles.parallelStream()
                .map(createTileFilter(preparedParent, cropToParent, filterByCentroid))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private static Function<Geometry, Geometry> createTileFilter(PreparedGeometry parent, boolean cropToParent, boolean filterByCentroid) {
        return (Geometry tile) -> {
            // straightforward case 1:
            // if there's no intersection, we're in the bounding box but not
            // the parent - skip tile
            if (!parent.intersects(tile)) {
                return null;
            }
            // straightforward case 2:
            // tile is cleanly within roi - return unchanged
            if (parent.covers(tile)) {
                return tile;
            }

            // cropping:
            if (cropToParent) {
                // crop the tile to fit the parent
                try {
                    return tile.intersection(parent.getGeometry());
                } catch (TopologyException e) {
                    logger.warn("Exception calculating tile intersection - tile will be skipped", e);
                    return null;
                }
            } else if (!filterByCentroid || parent.contains(tile.getCentroid())) {
                // return tile unchanged if we aren't filtering based on centroids,
                // or it'd be included anyway
                return tile;
            }
            // otherwise, skip tile
            return null;
        };
    }

    /**
     * Create a list of {@link ROI} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @return a list of tiles
     */
    public List<ROI> createROIs(ROI parent) {
        return createGeometries(parent.getGeometry()).stream()
                .map(g -> GeometryTools.geometryToROI(g, parent.getImagePlane()))
                .collect(Collectors.toList());
    }

    /**
     * Create a list of {@link PathObject} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @param creator a function used to create the desired type
     *                of {@link PathObject}
     * @return a list of tiles
     */
    public List<PathObject> createObjects(ROI parent, Function<ROI, PathObject> creator) {
        return createROIs(parent).stream().map(creator).collect(Collectors.toList());
    }

    /**
     * Create a list of {@link PathTileObject} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @return a list of tiles
     */
    public List<PathObject> createTiles(ROI parent) {
        return createObjects(parent, PathObjects::createTileObject);
    }

    /**
     * Create a list of {@link PathAnnotationObject} tiles from the input. These may
     * not all be rectangular based on the settings used.
     * @param parent the object that will be split into tiles.
     * @return a list of tiles
     */
    public List<PathObject> createAnnotations(ROI parent) {
        return createObjects(parent, PathObjects::createAnnotationObject);
    }

    /**
     * Calculate right-aligned start position
     * @param tileDim
     * @param parentDim
     * @return
     */
    private static double calculateRightAlignedStartOffset(final int tileDim, final double parentDim) {
        double mod = parentDim % tileDim;
        if (mod == 0) {
            return 0;
        }
        return -(tileDim - mod);
    }


    /**
     * Calculate offset for symmetric tiling where the tiles cannot extend beyond the parent bounds
     * @param tileDim
     * @param parentDim
     * @return
     */
    private static double calculateInteriorOffset(final int tileDim, final double parentDim) {
        double mod = parentDim % tileDim;
        if (mod == 0) {
            return 0;
        }
        return mod / 2;
    }

    /**
     * Calculate offset for symmetric tiling where the tiles can extend beyond the parent bounds
     * @param tileDim
     * @param parentDim
     * @return
     */
    private static double calculateExteriorOffset(final int tileDim, final double parentDim) {
        double mod = parentDim % tileDim;
        if (mod == 0) {
            return 0;
        }
        return -(tileDim - mod) / 2;
    }

    /**
     * Create a new builder to generate square tiles.
     * @param tileSize the width and height of the tiles, in pixels
     * @return a new builder
     */
    public static Builder builder(int tileSize) {
        return builder(tileSize, tileSize);
    }

    /**
     * Create a new builder to generate rectangular tiles.
     * @param tileWidth the width of the tiles, in pixels
     * @param tileHeight the height of the tiles, in pixels
     * @return a new builder
     */
    public static Builder builder(int tileWidth, int tileHeight) {
        return new Builder(tileWidth, tileHeight);
    }

    /**
     * Create a new builder initialized with the settings from an existing Tiler.
     * Because tilers are immutable, this is the only way to change the settings.
     * @param tiler the tiler that provides initial settings
     * @return a new builder
     */
    public static Builder builder(Tiler tiler) {
        return new Builder(tiler);
    }



    public static class Builder {

        private int tileWidth;
        private int tileHeight;
        private boolean cropToParent = true;
        private TileAlignment alignment = TileAlignment.CENTER;
        private boolean filterByCentroid = false;

        private Builder(int tileWidth, int tileHeight) {
            this.tileWidth = tileWidth;
            this.tileHeight = tileHeight;
        }

        private Builder(Tiler tiler) {
            this.tileWidth = tiler.tileWidth;
            this.tileHeight = tiler.tileHeight;
            this.cropToParent = tiler.cropToParent;
            this.alignment = tiler.alignment;
            this.filterByCentroid = tiler.filterByCentroid;
        }

        /**
         * Change the height of output tiles
         * @param tileHeight the new height in pixels
         * @return this builder
         */
        public Builder tileHeight(int tileHeight) {
            this.tileHeight = tileHeight;
            return this;
        }

        /**
         * Change the width of output tiles
         * @param tileWidth the new width in pixels
         * @return this builder
         */
        public Builder tileWidth(int tileWidth) {
            this.tileWidth = tileWidth;
            return this;
        }

        /**
         * Set whether the tiler is set to crop the output to the input parent.
         * Using this option can result in smaller and non-rectangular tiles.
         * @param cropToParent the new setting
         * @return this builder
         */
        public Builder cropTiles(boolean cropToParent) {
            this.cropToParent = cropToParent;
            return this;
        }

        /**
         * Set the tile alignment.
         * @param alignment the new setting
         * @return this builder
         */
        public Builder alignment(TileAlignment alignment) {
            this.alignment = alignment;
            return this;
        }

        /**
         * Start tiles at the top left of the ROI bounding box.
         * @return this builder
         */
        public Builder alignTopLeft() {
            return alignment(TileAlignment.TOP_LEFT);
        }

        /**
         * Start tiles at the top center of the ROI bounding box.
         * @return this builder
         */
        public Builder alignTopCenter() {
            return alignment(TileAlignment.TOP_CENTER);
        }

        /**
         * Match tiles to the top right of the ROI bounding box.
         * @return this builder
         */
        public Builder alignTopRight() {
            return alignment(TileAlignment.TOP_RIGHT);
        }

        /**
         * Match tiles to the center left of the ROI bounding box.
         * @return this builder
         */
        public Builder alignCenterLeft() {
            return alignment(TileAlignment.CENTER_LEFT);
        }

        /**
         * Center tiles within the ROI bounding box.
         * @return this builder
         */
        public Builder alignCenter() {
            return alignment(TileAlignment.CENTER);
        }

        /**
         * Match tiles to the center left of the ROI bounding box.
         * @return this builder
         */
        public Builder alignCenterRight() {
            return alignment(TileAlignment.CENTER_RIGHT);
        }

        /**
         * Match tiles to the bottom left of the ROI bounding box.
         * @return this builder
         */
        public Builder alignBottomLeft() {
            return alignment(TileAlignment.BOTTOM_LEFT);
        }

        /**
         * Start tiles at the bottom center of the ROI bounding box.
         * @return this builder
         */
        public Builder alignBottomCenter() {
            return alignment(TileAlignment.BOTTOM_CENTER);
        }

        /**
         * Match tiles to the bottom right of the ROI bounding box.
         * @return this builder
         */
        public Builder alignBottomRight() {
            return alignment(TileAlignment.BOTTOM_RIGHT);
        }

        /**
         * Set if the tiler will filter the output based on whether the centroid
         * of tiles lies within the parent
         * @param filterByCentroid the new setting
         * @return this builder
         */
        public Builder filterByCentroid(boolean filterByCentroid) {
            this.filterByCentroid = filterByCentroid;
            return this;
        }

        /**
         * Build a tiler object with the current settings.
         * @return
         */
        public Tiler build() {
            return new Tiler(tileWidth, tileHeight, cropToParent, alignment, filterByCentroid);
        }

    }

}
