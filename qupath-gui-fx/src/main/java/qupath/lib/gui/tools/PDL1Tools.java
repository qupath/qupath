package qupath.lib.gui.tools;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.measurements.MeasurementList;
import qupath.lib.objects.PathDetectionObject;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.PathObjects;
import qupath.lib.objects.classes.PathClass;
import qupath.lib.regions.ImageRegion;
import qupath.lib.roi.ROIs;
import qupath.lib.roi.interfaces.ROI;

import java.util.ArrayList;
import java.util.List;
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

    public static double[] showPdl1Popup() {
        Dialog<double[]> dialog = new Dialog<>();
        dialog.setTitle("CPS or TPS thresholds");

        ButtonType applyType = new ButtonType("Apply", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(applyType, ButtonType.CANCEL);

        TextField t1 = new TextField("1");
        TextField t2 = new TextField("5");
        TextField t3 = new TextField("10");

        GridPane grid = new GridPane();
        grid.setHgap(10); grid.setVgap(10); grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Threshold 1:"), t1);
        grid.addRow(1, new Label("Threshold 2:"), t2);
        grid.addRow(2, new Label("Threshold 3:"), t3);

        // simple numeric guard
        Node applyBtn = dialog.getDialogPane().lookupButton(applyType);
        applyBtn.setDisable(false);
        ChangeListener<String> guard = (_, _, _) -> {
            try {
                Double.parseDouble(t1.getText());
                Double.parseDouble(t2.getText());
                Double.parseDouble(t3.getText());
                applyBtn.setDisable(false);
            } catch (Exception ex) {
                applyBtn.setDisable(true);
            }
        };
        t1.textProperty().addListener(guard);
        t2.textProperty().addListener(guard);
        t3.textProperty().addListener(guard);

        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> {
            if (btn == applyType) {
                return new double[] {
                        Double.parseDouble(t1.getText()),
                        Double.parseDouble(t2.getText()),
                        Double.parseDouble(t3.getText())
                };
            }
            return null;
        });

        var result = dialog.showAndWait();
        return result.orElse(null);

    }

    public static void startViewportCounter(QuPathViewer viewer, double[] vals) {
        stopViewportCounter(); // ensure only one running at a time

        attachHud(viewer);

        final double t1 = vals[0], t2 = vals[1], t3 = vals[2];

        viewFuture = PDL1_EXEC.scheduleAtFixedRate(() -> {
            try {
                int[] events = countNucleiInViewport(viewer);
                // Defensive checks
                int denomTumor = events.length > 0 ? events[0] : 0;
                int nuclei     = events.length > 1 ? events[1] : 0;
                // 4) Update HUD on the JavaFX thread
                Platform.runLater(() -> {
                    if (denomTumor == 0 && nuclei == 0) {
                        setHudText("No cells detected");
                    } else {
                        double th1Count = denomTumor * t1 / 100.0;
                        double th2Count = denomTumor * t2 / 100.0;
                        double th3Count = denomTumor * t3 / 100.0;

                        setHudText(String.format(
                                "CPS or TPS Helpers — Tumor: %d (Denominator) | Nuclei: %d%n" +
                                        "Thresholds: %.0f (%.0f), %.0f (%.0f), %.0f (%.0f)",
                                denomTumor, nuclei,
                                t1, th1Count,
                                t2, th2Count,
                                t3, th3Count
                        ));
                    }
                });

            } catch (Throwable err) {
                err.printStackTrace();
            }
        }, 0, 250, TimeUnit.MILLISECONDS);
    }

    static boolean isTumor(String name) {
        if (name == null)
            return false;

        String n = name.toLowerCase().trim();

        // Ignore "non-tumor" cases first
        if (n.contains("non-tumor") || n.contains("non tumour") || n.contains("nontumor"))
            return false;

        // Check if it contains any tumor-related keywords
        return n.contains("tumor") || n.contains("tumour") || n.contains("cancer");
    }

    static boolean isImmune(String s) {
        if (s == null)
            return false;
        String n = s.toLowerCase();
        return n.contains("immune");
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
        var region = ImageRegion.createInstance(x, y, w, h, plane.getZ(), plane.getT());

        // Prefer detections collection typed as PathDetectionObject if your API returns it

        // Build a single list that includes both detections & annotations
        List<PathObject> objs = new ArrayList<>();
        objs.addAll(hier.getAllDetectionsForRegion((region)));
        objs.addAll(hier.getAllPointAnnotations());
        int total = 0, tumor = 0;
        int pTumor = 0, pImmune = 0;

        final int rx = region.getX(), ry = region.getY(), rw = region.getWidth(), rh = region.getHeight();

        for (PathObject obj : objs) {
            // If your API returns PathObject, guard/cast:
//            if (!(det instanceof qupath.lib.objects.PathDetectionObject d)) continue;

            if (!(obj instanceof PathDetectionObject)) continue;
            PathDetectionObject d = (PathDetectionObject) obj;

            ROI roi = d.getROI();
            if (roi == null) continue;

            var cxDet = d.getROI().getCentroidX();
            var cyDet = d.getROI().getCentroidY();
            // keep only detections whose centroid lies inside the viewport rectangle
            if (cxDet < rx || cyDet < ry || cxDet >= rx + rw || cyDet >= ry + rh)
                continue;

            total++;
            String name =
                    obj.getPathClass() != null ? obj.getPathClass().getName()
                            : (obj.getName() != null ? obj.getName() : "");
            if (obj.getPathClass() != null) name = obj.getPathClass().getName();
            else if (obj.getName() != null) name = obj.getName();

            boolean isTumorCell  = isTumor(name);
            boolean isImmuneCell = !isTumorCell && isImmune(name);

            if (isTumorCell) {
                tumor++;
                if (isPDL1Positive(d)) pTumor++;
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


    private static double m(MeasurementList ml) {
        return (ml == null) ? Double.NaN : ml.get("PD-L1 positive");
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
        double flag = m(ml);
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
