package qupath.lib.gui.tools;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.roi.ROIs;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;


public class PDL1Tools{
    // Debounce state for the box updater
    private static final ScheduledExecutorService PDL1_EXEC =
            Executors.newSingleThreadScheduledExecutor(r -> { var t=new Thread(r,"PDL1-view"); t.setDaemon(true); return t; });


    private static ScheduledFuture<?> viewFuture;


    public static void run(QuPathViewer viewer) {
        var imageData = viewer.getImageData();
        if (imageData == null) return;

        // Use viewport center & dynamic size instead of whole-image
        var node = viewer.getView();
        double ds = viewer.getDownsampleFactor();
        double viewW = node.getWidth() * ds, viewH = node.getHeight() * ds;
        double cx = viewer.getCenterPixelX(), cy = viewer.getCenterPixelY();
        double boxW = viewW * 0.5, boxH = viewH * 0.5;

        var roi = ROIs.createRectangleROI(
                cx - boxW/2.0, cy - boxH/2.0, boxW, boxH, viewer.getImagePlane());

        int rgb = ColorTools.packRGB(220, 30, 30);
        var cls = PathClass.fromString("Nucleus", rgb);
        var box = PathObjects.createAnnotationObject(roi, cls);
        box.setName(String.format("PD-L1 Box | %.0fx%.0f", boxW, boxH));

        var hier = imageData.getHierarchy();
        hier.addObject(box, false);
        hier.getSelectionModel().setSelectedObject(box);

        // If you have detections already segmented, you don’t need Watershed here.
        // If you still want to run it:
        // runWatershed(viewer, box, false, () -> updateNameWithCount(viewer, box, "PD-L1 Box"));
    }


    // PD-L1 Events on Viewer. Author: Nasif Hossain
    // === HUD label we overlay on top of the viewer ===
    private static Label HUD;
    private static StackPane HUDContainer; // the StackPane we attach to

    private static StackPane findStackPane(Node node) {
        Parent p = node.getParent();
        while (p != null && !(p instanceof StackPane)) {
            p = p.getParent();
        }
        return (StackPane)p;
    }

    private static void attachHud(QuPathViewer viewer) {
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
        Pos pos = Pos.TOP_LEFT;
        Platform.runLater(() -> {
            StackPane.setAlignment(HUD, pos);
            StackPane.setMargin(HUD, new Insets(8, 8, 8, 8));
            stack.getChildren().add(HUD);
        });
    }

    private static void detachHud() {
        if (HUD != null && HUDContainer != null) {
            var toRemove = HUD;
            var parent = HUDContainer;
            HUD = null;
            HUDContainer = null;
            Platform.runLater(() -> parent.getChildren().remove(toRemove));
        }
    }

    private static void setHudText(String s) {
        if (HUD == null) return;
        Platform.runLater(() -> HUD.setText(s == null ? "" : s));
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
                    setHudText(String.format("PD-L1 events — Tumor: %d | Nuclei: %d\nCPS: %d", events[0], events[1], events[2]));


            } catch (Throwable err) {
                err.printStackTrace();
            }
        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    /*/
    Function to count PD-L1 events in the view
     */
    // Return: [tumor, total, denomTumor, pTumor, pImmune, cps]
    private static int[] countNucleiInViewport(QuPathViewer viewer) {
        var imageData = viewer.getImageData();
        var hier = imageData.getHierarchy();

        // --- visible region in image coords ---
        double ds = viewer.getDownsampleFactor();
        double cx = viewer.getCenterPixelX(), cy = viewer.getCenterPixelY();
        double viewW = viewer.getView().getWidth()  * ds;
        double viewH = viewer.getView().getHeight() * ds;

        int x = (int)Math.floor(cx - viewW/2.0);
        int y = (int)Math.floor(cy - viewH/2.0);
        int w = (int)Math.ceil(viewW);
        int h = (int)Math.ceil(viewH);

        var server = imageData.getServer();
        if (x < 0) { w += x; x = 0; }
        if (y < 0) { h += y; y = 0; }
        if (x + w > server.getWidth())  w = server.getWidth()  - x;
        if (y + h > server.getHeight()) h = server.getHeight() - y;
        if (w <= 0 || h <= 0) return new int[]{0,0,0,0,0,0};

        var plane  = viewer.getImagePlane();
        var region = qupath.lib.regions.ImageRegion.createInstance(x, y, w, h, plane.getZ(), plane.getT());

        // Prefer detections collection typed as PathDetectionObject if your API returns it
        var hits = hier.getAllDetectionsForRegion(region, null); // Collection<? extends PathDetectionObject>

        int total = 0, tumor = 0;
        int denomTumor = 0, pTumor = 0, pImmune = 0;

        final int rx = region.getX(), ry = region.getY(), rw = region.getWidth(), rh = region.getHeight();

        for (var det : hits) {
            // If your API returns PathObject, guard/cast:
            if (!(det instanceof qupath.lib.objects.PathDetectionObject d)) continue;

            var cxDet = d.getROI().getCentroidX();
            var cyDet = d.getROI().getCentroidY();
            // keep only detections whose centroid lies inside the viewport rectangle
            if (cxDet < rx || cyDet < ry || cxDet >= rx + rw || cyDet >= ry + rh)
                continue;

            total++;

            var pc = d.getPathClass();
            String name = pc == null ? "" : pc.getName().toLowerCase();
            boolean isTumorCell  = name.contains("tumor")  || name.startsWith("cancer");   // adjust to your class names
            boolean isImmuneCell = !isTumorCell && (name.contains("immune") || name.contains("lymph"));

            if (isTumorCell) {
                tumor++;
                denomTumor++;                               // denominator = viable tumor cells in view
                if (isPDL1Positive(d)) pTumor++;           // your positivity rule (flag or DAB threshold)
            } else if (isImmuneCell) {
                if (isPDL1Positive(d)) pImmune++;
            }
        }

        int cps = computeCPS(tumor, pImmune,pTumor);

        return new int[]{tumor, total, cps};
    }


    public static void stopViewportCounter() {
        if (viewFuture != null) {
            viewFuture.cancel(true);
            viewFuture = null;
        }
//        setHudText("");
        detachHud();
    }



    // Returns CPS (0..100), rounded down
    private static int computeCPS(int pdL1PosTumor, int pdL1PosImmune, int viableTumorCells) {
        if (viableTumorCells <= 0) return 0;
        double cps = 100.0 * (pdL1PosTumor + pdL1PosImmune) / viableTumorCells;
        return (int)Math.floor(cps);
    }

    // Label for breast PD-L1 (CPS 10 cutoff)


    private static double m(MeasurementList ml, String key) {
        return (ml == null) ? Double.NaN : ml.get(key);
    }


    // class name sets — adjust to your segmentation labels
    static final Set<String> TUMOR = Set.of("Tumor", "Tumor cell", "Cancer");
    static final Set<String> IMMUNE = Set.of("Immune", "Immune cell", "Lymphocyte");

    // is cell in class set?
    static boolean isClass(PathDetectionObject d, Set<String> names) {
        var pc = d.getPathClass();
        var n = (pc == null) ? null : pc.getName();
        if (n == null) return false;
        if (names.contains(n)) return true;
        for (var s : names) if (n.startsWith(s)) return true;
        return false;
    }


// Utility: try multiple possible measurement keys and return the first valid one
    private static double mAny(MeasurementList ml, String... keys) {
        if (ml == null) return Double.NaN;
        for (var k : keys) {
            double v = ml.get(k);
            if (!Double.isNaN(v)) return v;
        }
        return Double.NaN;
    }

    static boolean isPDL1Positive(PathDetectionObject d) {
        var ml = d.getMeasurementList();

        // Prefer explicit flag if present
        double flag = m(ml, "PD-L1 positive");
        if (!Double.isNaN(flag)) return flag >= 0.5;

        // Fallback to intensity-based (adjust keys to your data)
        double v = mAny(ml,
                "Cell: PD-L1 OD mean",
                "Cell: DAB mean",
                "Nucleus: DAB mean",
                "Membrane: DAB mean"
        );
        return !Double.isNaN(v) && v > 0.15; // tune threshold to your assay
    }
}
