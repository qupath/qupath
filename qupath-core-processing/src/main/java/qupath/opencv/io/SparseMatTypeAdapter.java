package qupath.opencv.io;

import org.bytedeco.opencv.global.opencv_core;
import org.bytedeco.opencv.opencv_core.FileStorage;
import org.bytedeco.opencv.opencv_core.SparseMat;

class SparseMatTypeAdapter extends AbstractOpenCVTypeAdapter<SparseMat> {

    @Override
    void write(FileStorage fs, SparseMat value) {
        opencv_core.write(fs, "sparsemat", value);
    }

    @Override
    SparseMat read(FileStorage fs) {
        SparseMat mat = new SparseMat();
        opencv_core.read(fs.getFirstTopLevelNode(), mat);
        return mat;
    }

}
