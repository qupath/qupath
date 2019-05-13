/**
 * Supports accessing pixels and metadata in QuPath.
 * <p>
 * {@link qupath.lib.images.servers.ImageServer ImageServer} is the key interface for 
 * requesting pixels and {@linkplain qupath.lib.images.servers.ImageServerMetadata metadata}.
 * Implementations may be backed by specific image reading libraries, or optionally wrap around other 
 * ImageServers to perform additional transforms.
 */
package qupath.lib.images.servers;