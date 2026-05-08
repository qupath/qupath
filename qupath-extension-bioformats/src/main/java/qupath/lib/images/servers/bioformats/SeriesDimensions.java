package qupath.lib.images.servers.bioformats;

/**
 * Record to store the dimension of a single series (image) accessed by Bio-Formats.
 * @param sizeX image width
 * @param sizeY image height
 * @param sizeC number of channels
 * @param sizeZ number of z-slices
 * @param sizeT number of time points
 */
record SeriesDimensions(
        long sizeX, long sizeY, long sizeC, long sizeZ, long sizeT
) {

    long totalPixelsXY() {
        return sizeX * sizeY;
    }

    long totalPixelsXYZT() {
        return totalPixelsXY() * sizeZ * sizeT;
    }

}
