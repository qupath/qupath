package qupath.process.gui.commands.ml.tasks;

import javafx.concurrent.Task;
import qupath.lib.progress.ProgressMonitor;

abstract class MonitoredTask<T> extends Task<T> implements ProgressMonitor {

    @Override
    public void updateProgress(String message, double progress) {
        updateMessage(message);
        if (!isIndeterminate(progress)) {
            updateProgress(progress, 1.0);
        }
    }

}
