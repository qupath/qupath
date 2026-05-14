/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2025 QuPath developers, The University of Edinburgh
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

package qupath.lib.images.servers.openslide;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qupath.lib.images.servers.TileRequest;

import java.awt.color.ICC_ColorSpace;
import java.awt.color.ICC_Profile;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.net.URI;
import java.util.List;

/**
 * ImageServer implementation using OpenSlide, while also applying source and destination ICC profiles to transform
 * pixels.
 * <p>
 * By default, the source ICC profile is read from the image itself, while the destination profile is the default for
 * sRGB>
 */
public class OpenslideIccImageServer extends OpenslideImageServer {

	private static final Logger logger = LoggerFactory.getLogger(OpenslideIccImageServer.class);

    private volatile transient ColorConvertOp iccOp;

    /**
     * Create an ImageServer using OpenSlide for the specified file, applying ICC profiles to transform pixels.
     *
     * @param uri URI for the image
     * @param args optional arguments; these will also be passed to {@link OpenslideImageServer}
     * @throws IOException if the image and/or ICC profiles cannot be opened
     */
    public OpenslideIccImageServer(URI uri, String... args) throws IOException {
        super(uri, args);
    }

    private ColorConvertOp getOp() throws IOException {
        if (iccOp == null) {
            synchronized (this) {
                try {
                    iccOp = createOp();
                } catch (IllegalArgumentException e) {
                    throw new IOException("Exception creating ICC profiles", e);
                }
            }
        }
        return iccOp;
    }

    private ColorConvertOp createOp() throws IOException, IllegalArgumentException {
        var args = getArgs();
        return new ColorConvertOp(
                new ICC_Profile[] {
                        getSourceProfile(args),
                        getDestProfile(args)
                }, null);
    }

    private ICC_Profile getSourceProfile(List<String> args) throws IOException {
        var source = searchArgsForProfile(args, "--icc-profile-source");
        if (source == null) {
            return readEmbeddedIccProfile();
        } else {
            logger.debug("Requesting source ICC profile {}", source);
            return ICC_Profile.getInstance(source);
        }
    }

    private ICC_Profile readEmbeddedIccProfile() throws IOException {
        var bytes = getIccProfileBytes();
        if (bytes == null)
            throw new IOException("Unable to read ICC profile from " + getURI());
        logger.debug("Read embedded ICC profile ({} bytes)", bytes.length);
        return ICC_Profile.getInstance(bytes);
    }

    private ICC_Profile getDestProfile(List<String> args) throws IOException {
        var dest = searchArgsForProfile(args, "--icc-profile-dest");
        if (dest == null) {
            logger.debug("Using default destination ICC profile");
            return getDefaultDestProfile();
        } else {
            logger.debug("Requesting destination ICC profile {}", dest);
            return ICC_Profile.getInstance(dest);
        }
    }

    private ICC_Profile getDefaultDestProfile() {
        return ICC_Profile.getInstance(ICC_ColorSpace.CS_sRGB);
    }

    /**
     * Search a list of arguments for the next value occurring after a specified key.
     * @param args the arguments
     * @param key the key to search for
     * @return
     */
    private String searchArgsForProfile(List<String> args, String key) {
        int ind = args.indexOf(key);
        if (ind < 0)
            return null;
        else if (ind >= args.size()-1) {
            logger.warn("Unmatched arg key will be ignored ({})", key);
            return null;
        } else {
            return args.get(ind + 1);
        }
    }

    @Override
    public BufferedImage readTile(TileRequest tileRequest) throws IOException {
        var img = super.readTile(tileRequest);
        applyInPlace(getOp(), img.getRaster());
        return img;
    }

    private static void applyInPlace(ColorConvertOp op, WritableRaster raster) {
        op.filter(raster, raster);
    }

    @Override
    public String getServerType() {
        return "OpenSlide (ICC profile)";
    }

    @Override
    protected String createID() {
        return super.createID() + " (ICC profile)";
    }

}
