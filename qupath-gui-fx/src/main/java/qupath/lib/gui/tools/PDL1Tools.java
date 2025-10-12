package qupath.lib.gui.tools;

import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import qupath.lib.gui.scripting.QPEx;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathObject;
import qupath.lib.regions.ImageRegion;

import java.util.Collection;
import java.util.concurrent.Executors;

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
    // Debounce state for the box updater
    private static final java.util.concurrent.ScheduledExecutorService PDL1_EXEC =
            Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"PDL1-view"); t.setDaemon(true); return t; });


    private static java.util.concurrent.ScheduledFuture<?> viewFuture, quickFuture, fullFuture;
    private static final java.util.concurrent.atomic.AtomicLong SEQ = new java.util.concurrent.atomic.AtomicLong(0);

    private static void scheduleLiveRuns(QuPathViewer viewer, PathObject box, String nameBase) {
        long id = SEQ.incrementAndGet();
        if (quickFuture != null) { quickFuture.cancel(true); quickFuture = null; }
        if (fullFuture  != null) { fullFuture.cancel(true);  fullFuture  = null; }

        quickFuture = PDL1_EXEC.schedule(() -> {
            if (SEQ.get() != id) return;
            runWatershed(viewer, box, true, () -> updateNameWithCount(viewer, box, nameBase));
        }, 200, java.util.concurrent.TimeUnit.MILLISECONDS);

        fullFuture = PDL1_EXEC.schedule(() -> {
            if (SEQ.get() != id) return;
            runWatershed(viewer, box, false, () -> updateNameWithCount(viewer, box, nameBase));
        }, 700, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public static void run(QuPathViewer viewer) {
        var imageData = viewer.getImageData();
        if (imageData == null) return;

        // Use viewport center & dynamic size instead of whole-image
        var node = viewer.getView();
        double ds = viewer.getDownsampleFactor();
        double viewW = node.getWidth() * ds, viewH = node.getHeight() * ds;
        double cx = viewer.getCenterPixelX(), cy = viewer.getCenterPixelY();
        double boxW = viewW * 0.5, boxH = viewH * 0.5;

        var roi = qupath.lib.roi.ROIs.createRectangleROI(
                cx - boxW/2.0, cy - boxH/2.0, boxW, boxH, viewer.getImagePlane());

        int rgb = qupath.lib.common.ColorTools.packRGB(220, 30, 30);
        var cls = qupath.lib.objects.classes.PathClass.fromString("Nucleus", rgb);
        var box = qupath.lib.objects.PathObjects.createAnnotationObject(roi, cls);
        box.setName(String.format("PD-L1 Box | %.0fx%.0f", boxW, boxH));

        var hier = imageData.getHierarchy();
        hier.addObject(box, false);
        hier.getSelectionModel().setSelectedObject(box);

        // If you have detections already segmented, you don’t need Watershed here.
        // If you still want to run it:
        // runWatershed(viewer, box, false, () -> updateNameWithCount(viewer, box, "PD-L1 Box"));

        // If you want live re-detection when the box moves:
        installPdl1LiveUpdater(viewer, box);
    }



    // Label for breast PD-L1 (CPS 10 cutoff)
    private static String breastPdL1Label(int pdL1PosTumor, int pdL1PosImmune, int viableTumorCells) {
        int cps = computeCPS(pdL1PosTumor, pdL1PosImmune, viableTumorCells);
        String status = (cps >= 10) ? "CPS ≥ 10 (positive)" : "CPS < 10 (negative)";
        return String.format("Breast (CPS): %d  —  %s", cps, status);
    }

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

    // PD-L1 Events on Viewer. Author: Nasif Hossain
    // === HUD label we overlay on top of the viewer ===
    private static Label HUD;
    private static StackPane HUDContainer; // the StackPane we attach to

    private static StackPane findStackPane(javafx.scene.Node node) {
        javafx.scene.Parent p = node.getParent();
        while (p != null && !(p instanceof javafx.scene.layout.StackPane)) {
            p = p.getParent();
        }
        return (javafx.scene.layout.StackPane)p;
    }

    private static void attachHud(qupath.lib.gui.viewer.QuPathViewer viewer) {
        if (HUD != null) return; // already attached

        var viewNode = viewer.getView(); // JavaFX Node rendered by the viewer
        var stack = findStackPane(viewNode);
        if (stack == null) return;

        HUD = new Label("");
        HUD.setMouseTransparent(true);
        HUD.setStyle("""
        -fx-background-color: rgba(0,0,0,0.6);
        -fx-text-fill: white;
        -fx-font-size: 12px;
        -fx-padding: 4 8 4 8;
        -fx-background-radius: 6;
    """);

        HUDContainer = stack;
        javafx.geometry.Pos pos = javafx.geometry.Pos.TOP_LEFT;
        javafx.application.Platform.runLater(() -> {
            javafx.scene.layout.StackPane.setAlignment(HUD, pos);
            javafx.scene.layout.StackPane.setMargin(HUD, new javafx.geometry.Insets(8, 8, 8, 8));
            stack.getChildren().add(HUD);
        });
    }

    private static void detachHud() {
        if (HUD != null && HUDContainer != null) {
            var toRemove = HUD;
            var parent = HUDContainer;
            HUD = null;
            HUDContainer = null;
            javafx.application.Platform.runLater(() -> parent.getChildren().remove(toRemove));
        }
    }

    private static void setHudText(String s) {
        if (HUD == null) return;
        javafx.application.Platform.runLater(() -> HUD.setText(s == null ? "" : s));
    }

    public static void startViewportCounter(QuPathViewer viewer) {
        stopViewportCounter(); // ensure only one running at a time
        attachHud(viewer);
        viewFuture = PDL1_EXEC.scheduleAtFixedRate(() -> {
            try {
                int[] events = countNucleiInViewport(viewer);
                if (events[0] == 0 && events[1] == 0)
                    setHudText("No cells detected");
                else
                    setHudText(String.format("PD-L1 events — Tumor: %d | Nuclei: %d", events[0], events[1]));


            } catch (Throwable err) {
                err.printStackTrace();
            }
        }, 0, 250, java.util.concurrent.TimeUnit.MILLISECONDS);
    }


    /*/
    Function to count PD-L1 events in the view
     */
    public static int[] countNucleiInViewport(QuPathViewer viewer) {
        var imageData = viewer.getImageData();
        if (imageData == null)
            return new int[]{0, 0};

        var hier = imageData.getHierarchy();

        // === Compute current visible region ===
        double ds = viewer.getDownsampleFactor();             // image px per screen px
        double cx = viewer.getCenterPixelX();
        double cy = viewer.getCenterPixelY();
        double viewW = viewer.getView().getWidth() * ds;
        double viewH = viewer.getView().getHeight() * ds;

        int x = (int)Math.floor(cx - viewW / 2.0);
        int y = (int)Math.floor(cy - viewH / 2.0);
        int w = (int)Math.ceil(viewW);
        int h = (int)Math.ceil(viewH);

        // Clamp bounds to image
        var server = imageData.getServer();
        if (x < 0) { w += x; x = 0; }
        if (y < 0) { h += y; y = 0; }
        if (x + w > server.getWidth())  w = server.getWidth()  - x;
        if (y + h > server.getHeight()) h = server.getHeight() - y;
        if (w <= 0 || h <= 0) return new int[]{0, 0};

        var plane = viewer.getImagePlane();
        var region = ImageRegion.createInstance(x, y, w, h, plane.getZ(), plane.getT());

        // === Count detections (nuclei) in this region ===
        Collection<PathObject> hits = hier.getAllDetectionsForRegion(region);

        int total = 0, tumor = 0;

        for (var det : hits) {
            double centx = det.getROI().getCentroidX();
            double centy = det.getROI().getCentroidY();
            // Require centroid inside region bounds
            if (centx < region.getX() || centy < region.getY()
                    || centx >= region.getX() + region.getWidth()
                    || centy >= region.getY() + region.getHeight()) {
                continue; // skip detections outside the viewport
            }

            total++;
            var pc = det.getPathClass();
            if (pc != null) {
                String name = pc.getName().toLowerCase();
                if (name.contains("tumor"))
                    tumor++;
            }
        }

        return new int[]{tumor, total};

    }


    public static void stopViewportCounter() {
        if (viewFuture != null) {
            viewFuture.cancel(true);
            viewFuture = null;
        }
//        setHudText("");
        detachHud();
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

//    private static int countTumorCellsInBox(PathObject box) {
//        // Example: tumor if "Cell: Cytoplasm Hematoxylin mean" < X AND "Cell: DAB mean" pattern etc.
//        // Replace with your real features / classifier thresholds.
//        return (int)detectionsUnder(box)
//                .filter(d -> {
//                    var ml = d.getMeasurementList();
//                    double probTumor = m(ml, "Class probability: Tumor");
//                    return !Double.isNaN(probTumor) ? probTumor >= 0.5 : false;
//                })
//                .count();
//    }

//    private static int countPDL1PositiveTumorCellsInBox(PathObject box) {
//        return (int)detectionsUnder(box)
//                .filter(d -> {
//                    var ml = d.getMeasurementList();
//                    double probTumor = m(ml, "Class probability: Tumor");
//                    double dabMean   = m(ml, "Nucleus: DAB mean");   // or your PD-L1 feature
//                    return (probTumor >= 0.5) && (!Double.isNaN(dabMean) && dabMean > 0.15);
//                })
//                .count();
//    }

//    private static int countPDL1PositiveImmuneCellsInBox(PathObject box) {
//        return (int)detectionsUnder(box)
//                .filter(d -> {
//                    var ml = d.getMeasurementList();
//                    double probImmune = m(ml, "Class probability: Immune");
//                    double dabMean    = m(ml, "Cell: DAB mean");
//                    return (probImmune >= 0.5) && (!Double.isNaN(dabMean) && dabMean > 0.15);
//                })
//                .count();
//    }
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

//        // 3) (Optional) update the box label with the final detection count
//        javafx.application.Platform.runLater(() -> {
//            int n = box.getChildObjects().size();
//            int viableTumorCells      = countTumorCellsInBox(box);                 // denominator
//            int pdL1PosTumor          = countPDL1PositiveTumorCellsInBox(box);     // part of numerator
//            int pdL1PosImmune         = countPDL1PositiveImmuneCellsInBox(box);    // part of numerator
//
//            String label = breastPdL1Label(pdL1PosTumor, pdL1PosImmune, viableTumorCells);
////			box.setName("PD-L1 Box | " + label);
//
//            box.setName(String.format("PD-L1 Box | Cells: %d — %s", n, label));
//        });
    }
}
