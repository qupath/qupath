/*
 *  OpenSlide, a library for reading whole slide image files
 *
 *  Copyright (c) 2011 Carnegie Mellon University
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
package qupath.lib.images.servers.openslide;

import java.awt.image.BufferedImage;
import java.io.IOException;

public class AssociatedImage {
    private final String name;

    private final OpenSlide os;

    AssociatedImage(String name, OpenSlide os) {
        if (name == null || os == null) {
            throw new NullPointerException("Arguments cannot be null");
        }
        this.name = name;
        this.os = os;
    }

    public String getName() {
        return name;
    }

    public OpenSlide getOpenSlide() {
        return os;
    }

    public BufferedImage toBufferedImage() throws IOException {
        return os.getAssociatedImage(name);
    }

    @Override
    public int hashCode() {
        return os.hashCode() + name.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (obj instanceof AssociatedImage) {
            AssociatedImage ai2 = (AssociatedImage) obj;
            return os.equals(ai2.os) && name.equals(ai2.name);
        }

        return false;
    }
}
