package qupath.lib.gui.extensions;

import java.util.concurrent.Callable;

/**
 * A subcommand for the command line interface.
 * This should further implement either {@link Runnable} or {@link Callable}, 
 * and be annotated according to the expectations of picocli.
 */
public interface Subcommand {}