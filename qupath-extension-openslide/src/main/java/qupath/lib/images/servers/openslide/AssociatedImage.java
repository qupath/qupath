package qupath.lib.images.servers.openslide;

//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by FernFlower decompiler)
//


import java.awt.image.BufferedImage;
import java.io.IOException;

public class AssociatedImage {
    private final String name;
    private final OpenSlide os;

    AssociatedImage(String name, OpenSlide os) {
        if (name != null && os != null) {
            this.name = name;
            this.os = os;
        } else {
            throw new NullPointerException("Arguments cannot be null");
        }
    }

    public String getName() {
        return this.name;
    }

    public OpenSlide getOpenSlide() {
        return this.os;
    }

    public BufferedImage toBufferedImage() throws IOException {
        return this.os.getAssociatedImage(this.name);
    }

    public int hashCode() {
        return this.os.hashCode() + this.name.hashCode();
    }

    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (!(obj instanceof AssociatedImage)) {
            return false;
        } else {
            AssociatedImage ai2 = (AssociatedImage)obj;
            return this.os.equals(ai2.os) && this.name.equals(ai2.name);
        }
    }
}
