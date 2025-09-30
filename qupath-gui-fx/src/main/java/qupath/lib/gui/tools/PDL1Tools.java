package qupath.lib.gui.tools;

import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;

import java.util.stream.Stream;

public class PDL1Tools{

    private static void installPdl1LiveUpdater(QuPathViewer viewer,
                                               qupath.lib.objects.PathObject box) {
        final String nameBase = "PD-L1 Box";
        final RoiSnap[] last = { new RoiSnap(box.getROI()) };

        // Poll every 250 ms; if ROI bounds changed, debounce the detection runs
        PDL1_EXEC.scheduleAtFixedRate(() -> {
            try {
                var roi = box.getROI();
                if (roi == null) return;
                if (!last[0].same(roi)) {
                    last[0] = new RoiSnap(roi);
                    scheduleLiveRuns(viewer, box, nameBase);
                }
            } catch (Throwable ignore) {}
        }, 0, 250, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public static void run(QuPathViewer viewer) {
        // 1) Create PD-L1 box in center of view
        // Center of current visible image region (image coordinates)
        var imageData = viewer.getImageData();
        var server = imageData.getServer();

        // Image width/height in pixels
        double imgW = server.getWidth();
        double imgH = server.getHeight();

        // Center in the current plane
        double cx = imgW / 2.0;
        double cy = imgH / 2.0;
        double boxW = 1500, boxH = 1000;

        var roi = qupath.lib.roi.ROIs.createPDL1RectangleROI(
                cx - boxW/2.0,
                cy - boxH/2.0,
                boxW,
                boxH,
                viewer.getImagePlane()
        );

        Integer rgb = qupath.lib.common.ColorTools.packRGB(220, 30, 30);
        var cls = qupath.lib.objects.classes.PathClass.fromString("Nucleus", rgb);

        var box = qupath.lib.objects.PathObjects.createAnnotationObject(roi, cls);
        box.setName("PD-L1 Box");

        var hier = viewer.getImageData().getHierarchy();
        hier.addObject(box,false);
        hier.getSelectionModel().setSelectedObject(box);

        box.setName(String.format("PD-L1 Box | %dx%d", (int)boxW, (int)boxH));

        // 1) Add & select your PD-L1 box (you likely already have this)
        hier.addObject(box, false);
        hier.getSelectionModel().setSelectedObject(box);
        runWatershed(viewer, box, false, () -> updateNameWithCount(viewer, box, "PD-L1 Box"));
//        installPdl1LiveUpdater(viewer, box);
        // 2) Attach it to hierarchy
        // 3) Kick off live updater
    }


    // Label for breast PD-L1 (CPS 10 cutoff)
    private static String breastPdL1Label(int pdL1PosTumor, int pdL1PosImmune, int viableTumorCells) {
        int cps = computeCPS(pdL1PosTumor, pdL1PosImmune, viableTumorCells);
        String status = (cps >= 10) ? "CPS ≥ 10 (positive)" : "CPS < 10 (negative)";
        return String.format("Breast (CPS): %d  —  %s", cps, status);
    }


//	private static void installPDL1LiveUpdater(QuPathViewer viewer, PathObject box){
//		final var imageData = viewer.getImageData();
//		final var hier = imageData.getHierarchy();
//		final var nameBase = "PD-L1 Box";
//
//		final long[] lastChangeMs = {System.currentTimeMillis()};
//		final RoiSnapshot[] last = {new RoiSnapshot(box.getROI())};
    ////		final java.util.concurrent.atomic.AtomicBoolean running = new java.util.concurrent.atomic.AtomicBoolean();
//
//		PDL1_EXEC.scheduleAtFixedRate(() ->{
//			try {
//				var roi = box.getROI();
//				if (roi == null) return;
//
//				// Has ROI moved/resized?
//				boolean changed = !last[0].equalsRoi(roi);
//				if (changed) {
//					last[0] = new RoiSnapshot(roi);
//					lastChangeMs[0] = System.currentTimeMillis();
//
//					// quick, coarse pass for instant feedback (if not already running)
//					if (running.compareAndSet(false, true)) {
//						runWatershed(viewer, box, /*quick*/true, () -> {
//							running.set(false);
//							updateNameWithCount(viewer, box, nameBase);
//						});
//					}
//				} else {
//					// If stable for > 700 ms, run full pass (if not already running)
//					if (System.currentTimeMillis() - lastChangeMs[0] > 700 && running.compareAndSet(false, true)) {
//						runWatershed(viewer, box, /*quick*/false, () -> {
//							running.set(false);
//							updateNameWithCount(viewer, box, nameBase);
//						});
//					}
//				}
//			}
//				catch (Exception e){
//				System.out.println("Help");
//			}
//		});
//	}

    private static void updateNameWithCount(
            qupath.lib.gui.viewer.QuPathViewer viewer,
            qupath.lib.objects.PathObject box,
            String nameBase) {
        javafx.application.Platform.runLater(() -> {
            int n = box.getChildObjects().size();
            box.setName(String.format("%s | Cells: %d\n", nameBase, n));
            viewer.repaint();
        });
    }


    private static Stream<PathDetectionObject> detectionsUnder(PathObject box) {
        // Prefer direct children (typical when you ran detection with the box selected)
        Stream<PathDetectionObject> direct = box.getChildObjects().stream()
                .filter(o -> o instanceof PathDetectionObject)
                .map(o -> (PathDetectionObject)o);

//		// If you sometimes have nested levels, include descendants as a fallback
//		if (box.getChildObjects().isEmpty())
//			return box.getDescendantObjects().stream()
//					.filter(o -> o instanceof PathDetectionObject)
//					.map(o -> (PathDetectionObject)o);

        return direct;
    }
    /*/
	PD-L1
	 */
    // --- scheduler & debounce state ---
    private static final java.util.concurrent.ScheduledExecutorService PDL1_EXEC =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                var t = new Thread(r, "PDL1-live"); t.setDaemon(true); return t;
            });
    private java.util.concurrent.ScheduledFuture<?> quickFuture, fullFuture;
    private final java.util.concurrent.atomic.AtomicLong seq = new java.util.concurrent.atomic.AtomicLong(0);

    // (re)schedule a quick pass soon & a full pass after a short idle
    private static void scheduleLiveRuns(qupath.lib.gui.viewer.QuPathViewer viewer,
                                         qupath.lib.objects.PathObject box,
                                         String nameBase) {
//        long id = seq.incrementAndGet();      // mark latest change
//        if (quickFuture != null) { quickFuture.cancel(true); quickFuture = null; }
//        if (fullFuture  != null) { fullFuture.cancel(true);  fullFuture  = null; }
//
//        quickFuture = PDL1_EXEC.schedule(() -> {
//            if (seq.get() != id) return; // stale
//            runWatershed(viewer, box, /*quick*/ true, () -> updateNameWithCount(viewer, box, nameBase));
//        }, 200, java.util.concurrent.TimeUnit.MILLISECONDS);
//
//        fullFuture = PDL1_EXEC.schedule(() -> {
//            if (seq.get() != id) return; // stale
//            runWatershed(viewer, box, /*quick*/ false, () -> updateNameWithCount(viewer, box, nameBase));
//        }, 700, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    private static final class RoiSnap {
        final double x,y,w,h;
        RoiSnap(qupath.lib.roi.interfaces.ROI roi) {
            x=roi.getBoundsX(); y=roi.getBoundsY(); w=roi.getBoundsWidth(); h=roi.getBoundsHeight();
        }
        boolean same(qupath.lib.roi.interfaces.ROI roi) {
            return Math.abs(roi.getBoundsX()-x)<1e-6 && Math.abs(roi.getBoundsY()-y)<1e-6
                    && Math.abs(roi.getBoundsWidth()-w)<1e-6 && Math.abs(roi.getBoundsHeight()-h)<1e-6;
        }
    }



    private static class RoiSnapshot {
        final double x, y, w, h;
        RoiSnapshot(qupath.lib.roi.interfaces.ROI roi) {
            this.x = roi.getBoundsX(); this.y = roi.getBoundsY(); this.w = roi.getBoundsWidth(); this.h = roi.getBoundsHeight();
        }
        boolean equalsRoi(qupath.lib.roi.interfaces.ROI roi) {
            return Math.abs(roi.getBoundsX()-x) < 1e-6 && Math.abs(roi.getBoundsY()-y) < 1e-6
                    && Math.abs(roi.getBoundsWidth()-w) < 1e-6 && Math.abs(roi.getBoundsHeight()-h) < 1e-6;
        }
    }

    // Returns CPS (0..100), rounded down
    private static int computeCPS(int pdL1PosTumor, int pdL1PosImmune, int viableTumorCells) {
        if (viableTumorCells <= 0) return 0;
        double cps = 100.0 * (pdL1PosTumor + pdL1PosImmune) / viableTumorCells;
        return (int)Math.floor(cps);
    }

    // Label for breast PD-L1 (CPS 10 cutoff)


    private static double m(MeasurementList ml, String key) {
        if (ml == null) return Double.NaN;

        int n = ml.size();
        for (int i = 0; i < n; i++) {
            String name = ml.getNames().get(i);
            if (name.equals(key)) {
                return ml.getMeasurements().get(i).getValue();
            }
        }
        return Double.NaN; // not found
    }

    private static int countTumorCellsInBox(PathObject box) {
        // Example: tumor if "Cell: Cytoplasm Hematoxylin mean" < X AND "Cell: DAB mean" pattern etc.
        // Replace with your real features / classifier thresholds.
        return (int)detectionsUnder(box)
                .filter(d -> {
                    var ml = d.getMeasurementList();
                    double probTumor = m(ml, "Class probability: Tumor");
                    return !Double.isNaN(probTumor) ? probTumor >= 0.5 : false;
                })
                .count();
    }

    private static int countPDL1PositiveTumorCellsInBox(PathObject box) {
        return (int)detectionsUnder(box)
                .filter(d -> {
                    var ml = d.getMeasurementList();
                    double probTumor = m(ml, "Class probability: Tumor");
                    double dabMean   = m(ml, "Nucleus: DAB mean");   // or your PD-L1 feature
                    return (probTumor >= 0.5) && (!Double.isNaN(dabMean) && dabMean > 0.15);
                })
                .count();
    }

    private static int countPDL1PositiveImmuneCellsInBox(PathObject box) {
        return (int)detectionsUnder(box)
                .filter(d -> {
                    var ml = d.getMeasurementList();
                    double probImmune = m(ml, "Class probability: Immune");
                    double dabMean    = m(ml, "Cell: DAB mean");
                    return (probImmune >= 0.5) && (!Double.isNaN(dabMean) && dabMean > 0.15);
                })
                .count();
    }
    private static void runWatershed(QuPathViewer viewer, PathObject box, boolean quick, Runnable onDonefx){
        var hier = viewer.getImageData().getHierarchy();
        hier.getSelectionModel().setSelectedObject(box);

        var params = new java.util.LinkedHashMap<String, Object>();
        params.put("detectionImage", "Hematoxylin OD");   // or "Optical density sum", etc.
        params.put("requestedPixelSizeMicrons",quick? 1.0 : 0.5);     // analysis resolution
        params.put("backgroundRadiusMicrons", 8.0);
        params.put("medianRadiusMicrons", 0.0);
        params.put("sigmaMicrons", quick ? 1.0 : 1.5);
        params.put("minAreaMicrons", 10.0);
        params.put("maxAreaMicrons", 400.0);
        params.put("threshold", quick ? 0.2 : 0.1);
        params.put("maxBackground", 2.0);
        params.put("watershedPostProcess", true);
        params.put("cellExpansionMicrons", quick ? 0.0 : 3.0);
        params.put("includeNuclei", true);
        params.put("smoothBoundaries", !quick);
        params.put("makeMeasurements", !quick);

        // Launch the detector (runs in background with progress)
        try {
            QPEx.runPlugin("qupath.imagej.detect.cells.WatershedCellDetection", params);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        // 3) (Optional) update the box label with the final detection count
        javafx.application.Platform.runLater(() -> {
            int n = box.getChildObjects().size();
            int viableTumorCells      = countTumorCellsInBox(box);                 // denominator
            int pdL1PosTumor          = countPDL1PositiveTumorCellsInBox(box);     // part of numerator
            int pdL1PosImmune         = countPDL1PositiveImmuneCellsInBox(box);    // part of numerator

            String label = breastPdL1Label(pdL1PosTumor, pdL1PosImmune, viableTumorCells);
//			box.setName("PD-L1 Box | " + label);

            box.setName(String.format("PD-L1 Box | Cells: %d" + label, n));
        });
    }
}
