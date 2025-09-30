//package qupath.lib.gui.viewer.tools;
//
//import org.w3c.dom.Node;
//import qupath.lib.common.ColorTools;
//import qupath.lib.gui.ToolManager;
//import qupath.lib.gui.viewer.QuPathViewer;
//import qupath.lib.objects.PathObject;
//import qupath.lib.objects.PathObjects;
//import qupath.lib.objects.classes.PathClassFactory;
//import qupath.lib.roi.ROIs;
//import qupath.lib.roi.interfaces.ROI;
//
//import java.awt.*;
//import java.awt.event.MouseEvent;
//import java.awt.geom.Rectangle2D;
//
//public class PDL1Tool extends PathTools {
//    // Customize these (or make them user-editable via a dialog in activate())
//
//    private double boxW = 1274.0;
//    private double boxH =  794.0;
//    private static final String CLASS_NAME = "PD-L1 Box";
//
////    private String tissue = "Breast"; // set from your chooser if you like
////
////    private QuPathViewer viewer;
////    private PathObject preview;
////    private final ToolManager toolManager;
////
////    public PDL1Tool(ToolManager toolManager){
////        this.toolManager = toolManager;
////    }
////
////    public void setViewer(QuPathViewer viewer) { this.viewer = viewer; }
////
////    public void mousePressed(MouseEvent e){
////        if (viewer == null || viewer.getImageData() == null) return;
////
////        // center of current visible image region
////        var r  = viewer.getImageRegionStore().
////        double cx = r.getCenterX(), cy = r.getCenterY();    }
////
//////
//////    // Optional: use your own icon here if you’ve bundled it as a resource
//////    @Override public Node getIcon() {
//////        return qupath.fx.utils.IconFactory.createNode(16, 16, qupath.fx.utils.PathIcons.SQUARE);
//////        // or return your custom SVG/ImageView
//////    }
//////
//////    @Override public void activate(QuPathViewer viewer) {
//////        this.viewer = viewer;
//////
//////        // (Optional) Show a quick dialog to set tissue/size once per activation
//////        // var params = new qupath.fx.dialogs.ParameterList()
//////        //     .addChoiceParameter("tissue", "Tissue", tissue,
//////        //         java.util.List.of("Upper GI","Cervix","Head and Neck","Breast","Lung"), tissue)
//////        //     .addDoubleParameter("w", "Width (px)", boxW, null, 50, 5000, 0)
//////        //     .addDoubleParameter("h", "Height (px)", boxH, null, 50, 5000, 0);
//////        // if (params.showDialog("PD-L1 Tool", viewer.getWindow())) {
//////        //     tissue = (String) params.getChoiceParameterValue("tissue");
//////        //     boxW = params.getDoubleParameterValue("w");
//////        //     boxH = params.getDoubleParameterValue("h");
//////        // }
//////    }
//////
//////    @Override public void deactivate() {
//////        if (viewer != null && preview != null)
//////            viewer.getTransientObjects().remove(preview);
//////        preview = null;
//////        viewer = null;
//////    }
//////
//////    @Override public void mousePressed(MouseEvent e) {
//////        if (viewer == null || viewer.getImageData() == null) return;
//////
//////        var p = viewer.pixelToImagePlane(e.getX(), e.getY());
//////        ROI roi = ROIs.createRectangleROI(
//////                p.getX() - boxW/2.0, p.getY() - boxH/2.0, boxW, boxH, viewer.getImagePlane());
//////
//////        var cls = PathClassFactory.getPathClass("PD-L1 Box", ColorTools.makeRGB(220,30,30));
//////        preview = PathObjects.createAnnotationObject(roi, cls);
//////        preview.setName("PD-L1 Box (preview)");
//////        viewer.getTransientObjects().add(preview);
//////        viewer.repaint();
//////    }
//////
//////    @Override public void mouseDragged(MouseEvent e) {
//////        if (viewer == null || preview == null) return;
//////        var p = viewer.pixelToImagePlane(e.getX(), e.getY());
//////        Rectangle2D r = new Rectangle2D.Double(p.getX() - boxW/2.0, p.getY() - boxH/2.0, boxW, boxH);
//////        preview.setROI(ROIs.createRectangleROI(r.getX(), r.getY(), r.getWidth(), r.getHeight(), viewer.getImagePlane()));
//////        viewer.repaint();
//////    }
//////
//////    @Override public void mouseReleased(MouseEvent e) {
//////        if (viewer == null || viewer.getImageData() == null || preview == null) {
//////            if (viewer != null) viewer.getTransientObjects().remove(preview);
//////            preview = null;
//////            return;
//////        }
//////
//////        // Commit the box
//////        var roi = preview.getROI();
//////        var cls = PathClassFactory.getPathClass("PD-L1 Box", ColorTools.makeRGB(220,30,30));
//////        PathObject box = PathObjects.createAnnotationObject(roi, cls);
//////        box.setName("PD-L1 Box");
//////
//////        // 🔖 Tag PD-L1 metadata right here
//////        var ml = box.getMeasurementList();
//////        ml.put("PDL1.Tissue", tissue);
//////        ml.put("PDL1.BoxWidthPx", boxW);
//////        ml.put("PDL1.BoxHeightPx", boxH);
//////        ml.put("PDL1.StartTimeMs", System.currentTimeMillis());
//////        // You can add live counts later:
//////        // ml.put("PDL1.Nuclei", nuclei);
//////        // ml.put("PDL1.Tumor",  tumor);
//////
//////        var hier = viewer.getImageData().getHierarchy();
//////        hier.addPathObject(box);
//////        hier.getSelectionModel().setSelectedObject(box);
//////
//////        // Clean up preview
//////        viewer.getTransientObjects().remove(preview);
//////        preview = null;
//////        viewer.repaint();
//////    }
////}
