package qupath.lib.projects;


import qupath.lib.images.servers.ImageServer;

public class ImageRetCode<T> {

    public enum IMAGE_CODE {
        CHANGED,
        NO_CHANGES,
        EXCEPTION
    }

    private IMAGE_CODE retCode;
    private ImageServer server;

    public ImageRetCode(IMAGE_CODE retCode, ImageServer server) {
        this.retCode = retCode;
        this.server = server;
    }

    public IMAGE_CODE getRetCode() {
        return retCode;
    }

    public ImageServer getServer() {
        return server;
    }

    /**
     * This method should be used with great caution as we cannot access the OpenslideImageServer.class
     * so it basically rely on the OpenslideImageServer name as a String. If OpenslideImageServer ever
     * change its name the method may cease to function properly.
     * @return
     */
    public boolean isOpenSlideImageServer() {
        if (server != null && server.getClass().toString().toLowerCase().contains("openslide")) {
            return true;
        }
        return false;
    }
}
