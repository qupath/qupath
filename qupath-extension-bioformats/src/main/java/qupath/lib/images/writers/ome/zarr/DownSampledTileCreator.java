package qupath.lib.images.writers.ome.zarr;

import qupath.lib.images.servers.TileRequest;

import java.util.List;
import java.util.function.BiConsumer;


public class DownSampledTileCreator {

    private final BiConsumer<TileRequest, Object> onTileCreated;
    private final List<Double> downSamplesToCreate;

    public DownSampledTileCreator(BiConsumer<TileRequest, Object> onTileCreated, List<Double> downSamplesToCreate) {
        this.onTileCreated = onTileCreated;
        this.downSamplesToCreate = downSamplesToCreate;
    }

    public void addTile(TileRequest tileRequest, Object data) {

    }
}
