package qupath.opencv.io;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.FileStorage;
import org.bytedeco.opencv.opencv_core.Mat;

class MatTypeAdapter extends AbstractOpenCVTypeAdapter<Mat> {

    @Override
    void write(FileStorage fs, Mat value) {
        opencv_core.write(fs, "mat", value);
    }

    @Override
    Mat read(FileStorage fs) {
        return fs.getFirstTopLevelNode().mat();
    }

}
