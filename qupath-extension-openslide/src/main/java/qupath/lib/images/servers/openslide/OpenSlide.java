package qupath.lib.images.servers.openslide;

/*
 *  OpenSlide, a library for reading whole slide image files
 *
 *  Copyright (c) 2007-2011 Carnegie Mellon University
 *  All rights reserved.
 *
 *  OpenSlide is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Lesser General Public License as
 *  published by the Free Software Foundation, version 2.1.
 *
 *  OpenSlide is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with OpenSlide. If not, see
 *  <http://www.gnu.org/licenses/>.
 *
 */

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.Closeable;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.swing.filechooser.FileFilter;

public final class OpenSlide implements Closeable {
    private static final FileFilter FILE_FILTER = new FileFilter() {
        @Override
        public boolean accept(File f) {
            return f.isDirectory() || OpenSlide.detectVendor(f) != null;
        }

        @Override
        public String getDescription() {
            return "Virtual slide";
        }
    };

    private static final String LIBRARY_VERSION = OpenSlideJNA.INSTANCE.openslide_get_version();

    final public static String PROPERTY_NAME_COMMENT = "openslide.comment";

    final public static String PROPERTY_NAME_VENDOR = "openslide.vendor";

    final public static String PROPERTY_NAME_QUICKHASH1 = "openslide.quickhash-1";

    final public static String PROPERTY_NAME_BACKGROUND_COLOR = "openslide.background-color";

    final public static String PROPERTY_NAME_OBJECTIVE_POWER = "openslide.objective-power";

    final public static String PROPERTY_NAME_MPP_X = "openslide.mpp-x";

    final public static String PROPERTY_NAME_MPP_Y = "openslide.mpp-y";

    final public static String PROPERTY_NAME_BOUNDS_X = "openslide.bounds-x";

    final public static String PROPERTY_NAME_BOUNDS_Y = "openslide.bounds-y";

    final public static String PROPERTY_NAME_BOUNDS_WIDTH = "openslide.bounds-width";

    final public static String PROPERTY_NAME_BOUNDS_HEIGHT = "openslide.bounds-height";

    private long osr;

    final private ReadWriteLock lock = new ReentrantReadWriteLock();

    final private long[] levelWidths;

    final private long[] levelHeights;

    final private double[] levelDownsamples;

    final private int levelCount;

    final private Map<String, String> properties;

    final private Map<String, AssociatedImage> associatedImages;

    final private File canonicalFile;

    final private int hashCodeVal;

    public static String detectVendor(File file) {
        return OpenSlideJNA.INSTANCE.openslide_detect_vendor(file.getPath());
    }

    public OpenSlide(File file) throws IOException {
        if (!file.exists()) {
            throw new FileNotFoundException(file.toString());
        }

        osr = OpenSlideJNA.INSTANCE.openslide_open(file.getPath());

        if (osr == 0) {
            throw new IOException(file
                    + ": Not a file that OpenSlide can recognize");
        }
        // dispose on error, we are in the constructor
        try {
            checkError();
        } catch (IOException e) {
            dispose();
            throw e;
        }

        // store level count
        levelCount = OpenSlideJNA.INSTANCE.openslide_get_level_count(osr);

        // store dimensions
        levelWidths = new long[levelCount];
        levelHeights = new long[levelCount];
        levelDownsamples = new double[levelCount];

        for (int i = 0; i < levelCount; i++) {
            long[] w = new long[1], h = new long[1];
            OpenSlideJNA.INSTANCE.openslide_get_level_dimensions(osr, i, w, h);
            levelWidths[i] = w[0];
            levelHeights[i] = h[0];
            levelDownsamples[i] = OpenSlideJNA.INSTANCE.openslide_get_level_downsample(
                    osr, i);
        }

        // properties
        HashMap<String, String> props = new HashMap<String, String>();
        for (String s : OpenSlideJNA.INSTANCE.openslide_get_property_names(osr)) {
            props.put(s, OpenSlideJNA.INSTANCE.openslide_get_property_value(osr, s));
        }

        properties = Collections.unmodifiableMap(props);

        // associated images
        HashMap<String, AssociatedImage> associated =
                new HashMap<String, AssociatedImage>();
        for (String s : OpenSlideJNA.INSTANCE
                .openslide_get_associated_image_names(osr)) {
            associated.put(s, new AssociatedImage(s, this));
        }

        associatedImages = Collections.unmodifiableMap(associated);

        // store info for hash and equals
        canonicalFile = file.getCanonicalFile();
        String quickhash1 = getProperties().get(PROPERTY_NAME_QUICKHASH1);
        if (quickhash1 != null) {
            hashCodeVal = (int) Long.parseLong(quickhash1.substring(0, 8), 16);
        } else {
            hashCodeVal = canonicalFile.hashCode();
        }

        // dispose on error, we are in the constructor
        try {
            checkError();
        } catch (IOException e) {
            dispose();
            throw e;
        }
    }

    // call with the reader lock held, or from the constructor
    private void checkError() throws IOException {
        String msg = OpenSlideJNA.INSTANCE.openslide_get_error(osr);

        if (msg != null) {
            throw new IOException(msg);
        }
    }

    // takes the writer lock
    public void dispose() {
        Lock wl = lock.writeLock();
        wl.lock();
        try {
            if (osr != 0) {
                OpenSlideJNA.INSTANCE.openslide_close(osr);
                osr = 0;
            }
        } finally {
            wl.unlock();
        }
    }

    public int getLevelCount() {
        return levelCount;
    }

    // call with the reader lock held
    private void checkDisposed() {
        if (osr == 0) {
            throw new OpenSlideDisposedException();
        }
    }

    public long getLevel0Width() {
        return levelWidths[0];
    }

    public long getLevel0Height() {
        return levelHeights[0];
    }

    public long getLevelWidth(int level) {
        return levelWidths[level];
    }

    public long getLevelHeight(int level) {
        return levelHeights[level];
    }

    public void paintRegionOfLevel(Graphics2D g, int dx, int dy, int sx,
                                   int sy, int w, int h, int level) throws IOException {
        paintRegion(g, dx, dy, sx, sy, w, h, levelDownsamples[level]);
    }

    // takes the reader lock
    public void paintRegionARGB(int[] dest, long x, long y, int level, int w,
                                int h) throws IOException {
        if ((long) w * (long) h > dest.length) {
            throw new ArrayIndexOutOfBoundsException("Size of data ("
                    + dest.length + ") is less than w * h");
        }

        if (w < 0 || h < 0) {
            throw new IllegalArgumentException("w and h must be nonnegative");
        }

        Lock rl = lock.readLock();
        rl.lock();
        try {
            checkDisposed();
            OpenSlideJNA.INSTANCE.openslide_read_region(osr, dest, x, y, level, w, h);
            checkError();
        } finally {
            rl.unlock();
        }
    }

    public void paintRegion(Graphics2D g, int dx, int dy, long sx, long sy,
                            int w, int h, double downsample) throws IOException {
        if (downsample < 1.0) {
            throw new IllegalArgumentException("downsample (" + downsample
                    + ") must be >= 1.0");
        }

        // get the level
        int level = getBestLevelForDownsample(downsample);

        // figure out its downsample
        double levelDS = levelDownsamples[level];

        // compute the difference
        double relativeDS = downsample / levelDS;

        // scale source coordinates into level coordinates
        long baseX = (long) (downsample * sx);
        long baseY = (long) (downsample * sy);
        long levelX = (long) (relativeDS * sx);
        long levelY = (long) (relativeDS * sy);

        // scale width and height by relative downsample
        int levelW = (int) Math.round(relativeDS * w);
        int levelH = (int) Math.round(relativeDS * h);

        // clip to edge of image
        levelW = (int) Math.min(levelW, getLevelWidth(level) - levelX);
        levelH = (int) Math.min(levelH, getLevelHeight(level) - levelY);
        w = (int) Math.round(levelW / relativeDS);
        h = (int) Math.round(levelH / relativeDS);

        if (debug) {
            System.out.println("levelW " + levelW + ", levelH " + levelH
                    + ", baseX " + baseX + ", baseY " + baseY);
        }

        if (levelW <= 0 || levelH <= 0) {
            // nothing to draw
            return;
        }

        BufferedImage img = new BufferedImage(levelW, levelH,
                BufferedImage.TYPE_INT_ARGB_PRE);

        int[] data = ((DataBufferInt) img.getRaster().getDataBuffer())
                .getData();

        paintRegionARGB(data, baseX, baseY, level, img.getWidth(), img
                .getHeight());

        // g.scale(1.0 / relativeDS, 1.0 / relativeDS);
        g.drawImage(img, dx, dy, w, h, null);

        if (debug) {
            System.out.println(img);

            if (debugThingy == 0) {
                g.setColor(new Color(1.0f, 0.0f, 0.0f, 0.4f));
                debugThingy = 1;
            } else {
                g.setColor(new Color(0.0f, 1.0f, 0.0f, 0.4f));
                debugThingy = 0;
            }
            g.fillRect(dx, dy, w, h);
        }
    }

    final boolean debug = false;

    private int debugThingy = 0;

    public BufferedImage createThumbnailImage(int x, int y, long w, long h,
                                              int maxSize, int bufferedImageType) throws IOException {
        double ds;

        if (w > h) {
            ds = (double) w / maxSize;
        } else {
            ds = (double) h / maxSize;
        }

        if (ds < 1.0) {
            ds = 1.0;
        }

        int sw = (int) (w / ds);
        int sh = (int) (h / ds);
        int sx = (int) (x / ds);
        int sy = (int) (y / ds);

        BufferedImage result = new BufferedImage(sw, sh, bufferedImageType);

        Graphics2D g = result.createGraphics();
        paintRegion(g, 0, 0, sx, sy, sw, sh, ds);
        g.dispose();
        return result;
    }

    public BufferedImage createThumbnailImage(int x, int y, long w, long h,
                                              int maxSize) throws IOException {
        return createThumbnailImage(x, y, w, h, maxSize,
                BufferedImage.TYPE_INT_RGB);
    }

    public BufferedImage createThumbnailImage(int maxSize) throws IOException {
        return createThumbnailImage(0, 0, getLevel0Width(), getLevel0Height(),
                maxSize);
    }

    public double getLevelDownsample(int level) {
        return levelDownsamples[level];
    }

    public int getBestLevelForDownsample(double downsample) {
        // too small, return first
        if (downsample < levelDownsamples[0]) {
            return 0;
        }

        // find where we are in the middle
        for (int i = 1; i < levelCount; i++) {
            if (downsample < levelDownsamples[i]) {
                return i - 1;
            }
        }

        // too big, return last
        return levelCount - 1;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public Map<String, AssociatedImage> getAssociatedImages() {
        return associatedImages;
    }

    // takes the reader lock
    BufferedImage getAssociatedImage(String name) throws IOException {
        Lock rl = lock.readLock();
        rl.lock();
        try {
            checkDisposed();

            long[]d0 = new long[1];
            long[]d1 = new long[1];
            OpenSlideJNA.INSTANCE.openslide_get_associated_image_dimensions(osr, name,
                    d0, d1);
            checkError();
            if (d0[0] == -1) {
                // non-terminal error
                throw new IOException("Failure reading associated image");
            }

            BufferedImage img = new BufferedImage((int) d0[0], (int) d1[0],
                    BufferedImage.TYPE_INT_ARGB_PRE);

            int[] data = ((DataBufferInt) img.getRaster().getDataBuffer())
                    .getData();

            OpenSlideJNA.INSTANCE.openslide_read_associated_image(osr, name, data);
            checkError();
            return img;
        } finally {
            rl.unlock();
        }
    }

    public static String getLibraryVersion() {
        return LIBRARY_VERSION;
    }

    public static FileFilter getFileFilter() {
        return FILE_FILTER;
    }

    @Override
    public int hashCode() {
        return hashCodeVal;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof OpenSlide) {
            OpenSlide os2 = (OpenSlide) obj;
            String quickhash1 = getProperties()
                    .get(PROPERTY_NAME_QUICKHASH1);
            String os2_quickhash1 = os2.getProperties()
                    .get(PROPERTY_NAME_QUICKHASH1);

            if (quickhash1 != null && os2_quickhash1 != null) {
                return quickhash1.equals(os2_quickhash1);
            } else if (quickhash1 == null && os2_quickhash1 == null) {
                return canonicalFile.equals(os2.canonicalFile);
            } else {
                return false;
            }
        }

        return false;
    }

    @Override
    public void close() {
        dispose();
    }

    public interface OpenSlideJNA extends Library {
        OpenSlideJNA INSTANCE = Native.load("openslide", OpenSlideJNA.class);

        String openslide_get_version();
        String openslide_detect_vendor(String file);
        long openslide_open(String file);
        int openslide_get_level_count(long osr);
        void openslide_get_level_dimensions(long osr, int level, long[] w, long[] h);
        double openslide_get_level_downsample(long osr, int level);
        void openslide_close(long osr);
        String[] openslide_get_property_names(long osr);
        String openslide_get_property_value(long osr, String name);
        String[] openslide_get_associated_image_names(long osr);
        void openslide_read_region(long osr, int[] dest, long x, long y, int level, long w, long h);
        void openslide_get_associated_image_dimensions(long osr, String name, long[] w, long[] h);
        void openslide_read_associated_image(long osr, String name, int[] dest);
        String openslide_get_error(long osr);
    }

    public static class OpenSlideDisposedException extends RuntimeException {
        private static final String MSG = "OpenSlide object has been disposed";

        public OpenSlideDisposedException() {
            super(MSG);
        }
    }

}
