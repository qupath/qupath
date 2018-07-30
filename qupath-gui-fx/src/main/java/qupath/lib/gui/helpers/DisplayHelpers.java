/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

package qupath.lib.gui.helpers;

import java.awt.Desktop;
import java.awt.GraphicsEnvironment;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.controlsfx.control.Notifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.embed.swing.SwingFXUtils;
import javafx.embed.swing.SwingNode;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.WritableImage;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import qupath.lib.color.ColorDeconvolutionHelper;
import qupath.lib.color.ColorDeconvolutionStains;
import qupath.lib.color.ColorDeconvolutionStains.DEFAULT_CD_STAINS;
import qupath.lib.color.StainVector;
import qupath.lib.common.ColorTools;
import qupath.lib.gui.QuPathGUI;
import qupath.lib.gui.helpers.dialogs.ParameterPanelFX;
import qupath.lib.gui.legacy.swing.ParameterPanel;
import qupath.lib.gui.viewer.QuPathViewer;
import qupath.lib.images.ImageData;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.objects.PathObject;
import qupath.lib.objects.TMACoreObject;
import qupath.lib.objects.helpers.PathObjectTools;
import qupath.lib.objects.hierarchy.PathObjectHierarchy;
import qupath.lib.plugins.parameters.ParameterList;
import qupath.lib.plugins.workflow.DefaultScriptableWorkflowStep;
import qupath.lib.roi.PointsROI;

/**
 * Collection of static methods to help with showing information to a user,
 * as well as requesting some basic input.
 *
 * @author Pete Bankhead
 */
public class DisplayHelpers {

    final private static Logger logger = LoggerFactory.getLogger(DisplayHelpers.class);

    /**
     * Possible buttons pressed in a yes/no/cancel dialog.
     */
    public static enum DialogButton {
        YES, NO, CANCEL
    }

    /**
     * Make a semi-educated guess at the image type of a PathImageServer.
     *
     * @param server
     * @param imgThumbnail Optional thumbnail for the image. If not present, the server will be asked for a thumbnail.
     *                     Including one here can improve performance by reducing need to query server.
     * @return
     */
    public static ImageData.ImageType estimateImageType(final ImageServer<BufferedImage> server, final BufferedImage imgThumbnail) {

//		logger.warn("Image type will be automatically estimated");

        if (!server.isRGB())
            return ImageData.ImageType.FLUORESCENCE;

        BufferedImage img;
        if (imgThumbnail == null)
            img = server.getBufferedThumbnail(220, 220, 0);
        else {
            img = imgThumbnail;
//			// Rescale if necessary
//			if (img.getWidth() * img.getHeight() > 400*400) {
//				imgThumbnail.getS
//			}
        }
        int w = img.getWidth();
        int h = img.getHeight();
        int[] rgb = img.getRGB(0, 0, w, h, null, 0, w);
        long rSum = 0;
        long gSum = 0;
        long bSum = 0;
        int nDark = 0;
        int nLight = 0;
        int n = 0;
        int darkThreshold = 25;
        int lightThreshold = 220;
        for (int v : rgb) {
            int r = ColorTools.red(v);
            int g = ColorTools.green(v);
            int b = ColorTools.blue(v);
            if (r < darkThreshold & g < darkThreshold && b < darkThreshold)
                nDark++;
            else if (r > lightThreshold & g > lightThreshold && b > lightThreshold)
                nLight++;
            else {
                n++;
                rSum += r;
                gSum += g;
                bSum += b;
            }
        }
        if (nDark == 0 && nLight == 0)
            return ImageData.ImageType.UNSET;
        // If we have more dark than light pixels, assume fluorescence
        if (nDark >= nLight)
            return ImageData.ImageType.FLUORESCENCE;

//		Color color = new Color(
//				(int)(rSum/n + .5),
//				(int)(gSum/n + .5),
//				(int)(bSum/n + .5));
//		logger.debug("Color: " + color.toString());

        // Compare optical density vector angles with the defaults for hematoxylin, eosin & DAB
        ColorDeconvolutionStains stainsH_E = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DEFAULT_CD_STAINS.H_E);
        double rOD = ColorDeconvolutionHelper.makeOD(rSum / n, stainsH_E.getMaxRed());
        double gOD = ColorDeconvolutionHelper.makeOD(gSum / n, stainsH_E.getMaxGreen());
        double bOD = ColorDeconvolutionHelper.makeOD(bSum / n, stainsH_E.getMaxBlue());
        StainVector stainMean = new StainVector("Mean Stain", rOD, gOD, bOD);
        double angleH = StainVector.computeAngle(stainMean, stainsH_E.getStain(1));
        double angleE = StainVector.computeAngle(stainMean, stainsH_E.getStain(2));
        ColorDeconvolutionStains stainsH_DAB = ColorDeconvolutionStains.makeDefaultColorDeconvolutionStains(DEFAULT_CD_STAINS.H_DAB);
        double angleDAB = StainVector.computeAngle(stainMean, stainsH_DAB.getStain(2));

        // For H&E staining, eosin is expected to predominate... if it doesn't, assume H-DAB
        logger.debug("Angle hematoxylin: " + angleH);
        logger.debug("Angle eosin: " + angleE);
        logger.debug("Angle DAB: " + angleDAB);
        if (angleDAB < angleE || angleH < angleE) {
            logger.info("Estimating H-DAB staining");
            return ImageData.ImageType.BRIGHTFIELD_H_DAB;
        } else {
            logger.info("Estimating H & E staining");
            return ImageData.ImageType.BRIGHTFIELD_H_E;
        }
    }


    public static boolean showConfirmDialog(String title, String text) {
        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(AlertType.CONFIRMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(text);
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        } else
            return JOptionPane.showConfirmDialog(null, text, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }


    public static boolean showMessageDialog(final String title, final Node node) {
        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK);
            alert.setTitle(title);
            alert.getDialogPane().setContent(node);
//			if (resizable) {
//				// Note: there is nothing to stop the dialog being shrunk to a ridiculously small size!
//				alert.setResizable(resizable);
//			}
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        } else {
            JFXPanel panel = new JFXPanel();
            panel.setScene(new Scene(new StackPane(node)));
            return JOptionPane.showConfirmDialog(null, panel, title, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
        }
    }

    public static void showMessageDialog(String title, String message) {
        logger.info("{}: {}", title, message);
        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK);
            alert.setTitle(title);
            alert.getDialogPane().setHeader(null);
            alert.getDialogPane().setContentText(message);
            alert.showAndWait();
        } else
            Platform.runLater(() -> showMessageDialog(title, message));
    }


    public static boolean showConfirmDialog(String title, Node node) {
        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK, ButtonType.CANCEL);
            if (QuPathGUI.getInstance() != null)
                alert.initOwner(QuPathGUI.getInstance().getStage());
            alert.setTitle(title);
            alert.getDialogPane().setContent(node);
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        } else {
            JFXPanel panel = new JFXPanel();
            panel.setScene(new Scene(new StackPane(node)));
            return JOptionPane.showConfirmDialog(null, panel, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
        }
    }

    public static boolean showConfirmDialog(String title, JComponent content) {
        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(AlertType.NONE, null, ButtonType.OK, ButtonType.CANCEL);
            if (QuPathGUI.getInstance() != null)
                alert.initOwner(QuPathGUI.getInstance().getStage());
            alert.getDialogPane().setHeaderText(null);
            alert.setTitle(title);
            SwingNode node = new SwingNode();
            node.setContent(content);
            content.validate();
            content.setSize(content.getPreferredSize());
            StackPane pane = new StackPane(node);
            pane.setPrefSize(content.getPreferredSize().width + 25, content.getPreferredSize().height + 25);
            alert.getDialogPane().setContent(pane);
            Optional<ButtonType> result = alert.showAndWait();
            return result.isPresent() && result.get() == ButtonType.OK;
        } else
            return JOptionPane.showConfirmDialog(null, content, title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }

    public static boolean showYesNoDialog(String title, String text) {
        if (Platform.isFxApplicationThread()) {
            Alert alert = new Alert(AlertType.NONE);
            if (QuPathGUI.getInstance() != null)
                alert.initOwner(QuPathGUI.getInstance().getStage());
            alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO);
            alert.setTitle(title);
            alert.setContentText(text);
            Optional<ButtonType> result = alert.showAndWait();
            boolean response = result.isPresent() && result.get() == ButtonType.YES;
            return response;
        } else
            return JOptionPane.showConfirmDialog(null, text, title, JOptionPane.YES_NO_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.YES_OPTION;
    }


    public static DialogButton showYesNoCancelDialog(String title, String text) {
        if (Platform.isFxApplicationThread()) {
            // TODO: Check the order of buttons in Yes, No, Cancel dialog - seems weird on OSX
            Alert alert = new Alert(AlertType.NONE);
            alert.getButtonTypes().addAll(ButtonType.YES, ButtonType.NO, ButtonType.CANCEL);
            alert.setTitle(title);
            alert.setContentText(text);
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent())
                return getJavaFXPaneYesNoCancel(result.get());
            else
                return DialogButton.CANCEL;
        } else {
            int response = JOptionPane.showConfirmDialog(null, text, title, JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            return getJOptionPaneYesNoCancel(response);
        }
    }

    private static DialogButton getJOptionPaneYesNoCancel(final int response) {
        switch (response) {
            case JOptionPane.YES_OPTION:
                return DialogButton.YES;
            case JOptionPane.NO_OPTION:
                return DialogButton.NO;
            case JOptionPane.CANCEL_OPTION:
                return DialogButton.CANCEL;
            default:
                return null;
        }
    }


    private static DialogButton getJavaFXPaneYesNoCancel(final ButtonType buttonType) {
        if (buttonType == ButtonType.YES)
            return DialogButton.YES;
        if (buttonType == ButtonType.NO)
            return DialogButton.NO;
        if (buttonType == ButtonType.CANCEL)
            return DialogButton.CANCEL;
        return null;
    }


    /**
     * Show a (modal) dialog for a specified ParameterList.
     *
     * @param title
     * @param params
     * @return False if the user pressed 'cancel', true otherwise
     */
    public static boolean showParameterDialog(String title, ParameterList params) {
        if (Platform.isFxApplicationThread()) {
            return showConfirmDialog(title, new ParameterPanelFX(params).getPane());
//			return showComponentContainerDialog(owner, title, new ParameterPanel(params));
        } else {
            JOptionPane pane = new JOptionPane(new ParameterPanel(params), JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION);
            JDialog dialog = pane.createDialog(null, title);
            dialog.setAlwaysOnTop(true);
            dialog.setVisible(true);
            dialog.dispose();
            Object value = pane.getValue();
            return (value instanceof Integer) && ((Integer) value == JOptionPane.OK_OPTION);
            //		return JOptionPane.showConfirmDialog(null, new ParameterPanel(params), title, JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
        }
    }


    /**
     * Returns null.
     * <p>
     * Previously returned QuPath's JFrame... when Swing was used.
     *
     * @return
     */
    private static JFrame getPossibleParent() {
        return null;
    }


    /**
     * Show an input dialog requesting a numeric value.
     *
     * @param title
     * @param message
     * @param initialInput
     * @return Number input by the user, or NaN if no valid number was entered, or null if cancel was pressed.
     */
    public static Double showInputDialog(final String title, final String message, final Double initialInput) {
        String result = showInputDialog(title, message, initialInput == null ? "" : initialInput.toString());
        if (result == null)
            return null;
        try {
            return Double.parseDouble(result);
        } catch (Exception e) {
            logger.error("Unable to parse numeric value from {}", result);
            return Double.NaN;
        }
    }

    /**
     * Show an input dialog requesting a String input.
     *
     * @param title
     * @param message
     * @param initialInput
     * @return
     */
    public static String showInputDialog(final String title, final String message, final String initialInput) {
        if (Platform.isFxApplicationThread()) {
            TextInputDialog dialog = new TextInputDialog(initialInput);
            dialog.setTitle(title);
            dialog.setHeaderText(null);
            dialog.setContentText(message);
            // Traditional way to get the response value.
            Optional<String> result = dialog.showAndWait();
            if (result.isPresent())
                return result.get();
        } else {
            Object result = JOptionPane.showInputDialog(getPossibleParent(), message, title, JOptionPane.PLAIN_MESSAGE, null, null, initialInput);
            if (result instanceof String)
                return (String) result;
        }
        return null;
    }


    public static <T> T showChoiceDialog(final String title, final String message, final T[] choices, final T defaultChoice) {
        if (Platform.isFxApplicationThread()) {
            ChoiceDialog<T> dialog = new ChoiceDialog<>(defaultChoice, choices);
            dialog.setTitle(title);
            dialog.getDialogPane().setHeaderText(null);
            if (message != null)
                dialog.getDialogPane().setContentText(message);
            Optional<T> result = dialog.showAndWait();
            if (result.isPresent())
                return result.get();
            return null;
        } else
            return (T) JOptionPane.showInputDialog(getPossibleParent(), message, title, JOptionPane.PLAIN_MESSAGE, null, choices, defaultChoice);
    }


    public static void showErrorMessage(final String title, final Throwable e) {
        logger.error("Error", e);
        String message = e.getLocalizedMessage();
        if (message == null)
            message = "QuPath has encountered a problem, sorry.\nIf you can replicate it, please notify a developer.\n\n" + e;
        showErrorMessage(title, message);
    }

    public static void showErrorNotification(final String title, final Throwable e) {
        logger.error("{}", title, e);
        String message = e.getLocalizedMessage();
        if (message == null)
            message = "QuPath has encountered a problem, sorry.\nIf you can replicate it, please notify a developer.\n\n" + e;
        if (Platform.isFxApplicationThread())
            Notifications.create().title(title).text(message).showError();
        else {
            String finalMessage = message;
            Platform.runLater(() -> Notifications.create().title(title).text(finalMessage).showError());
        }
    }

    public static void showErrorNotification(final String title, final String message) {
        logger.error(title + ": " + message);
        Notifications.create().title(title).text(message).showError();
    }

    public static void showWarningNotification(final String title, final String message) {
        logger.warn(title + ": " + message);
        Notifications.create().title(title).text(message).showWarning();
    }

    public static void showInfoNotification(final String title, final String message) {
        logger.info(title + ": " + message);
        Notifications.create().title(title).text(message).showInformation();
    }

    public static void showPlainNotification(final String title, final String message) {
        logger.info(title + ": " + message);
        Notifications.create().title(title).text(message).show();
    }

    /**
     * Try to open a file in the native application.
     * <p>
     * This can be used to open a directory in Finder (Mac OSX) or Windows Explorer etc.
     *
     * @param file
     * @return
     */
    public static boolean openFile(final File file) {
        if (file == null || !file.exists()) {
            DisplayHelpers.showErrorMessage("Open", "File " + file + " does not exist!");
            return false;
        }
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(file);
                return true;
            } catch (IOException e1) {
                DisplayHelpers.showErrorNotification("Open directory", e1);
            }
        }
        return false;
    }


    /**
     * Try to open a URI in a web browser.
     *
     * @param uri
     * @return True if the request succeeded, false otherwise.
     */
    public static boolean browseURI(final URI uri) {
        try {
            // TODO: Look for a more elegant JavaFX solution...
            Desktop.getDesktop().browse(uri);
            return true;
        } catch (Exception e) {
            DisplayHelpers.showErrorMessage("Web error", "Unable to open URI:\n" + uri);
            logger.info("Unable to show webpage", e);
            return false;
        }
    }


    public static void showErrorMessage(final String title, final String message) {
        logger.error(title + ": " + message);
        if (!GraphicsEnvironment.isHeadless()) {
            if (Platform.isFxApplicationThread()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle(title);
                alert.getDialogPane().setHeaderText(null);
                alert.setContentText(message);
                alert.show();
            } else
                Platform.runLater(() -> showErrorMessage(title, message));
//				JOptionPane.showMessageDialog(getPossibleParent(), message, title, JOptionPane.ERROR_MESSAGE, null);
        }
//			showDialog(title, message);
    }

    public static void showErrorMessage(final String title, final Node message) {
        logger.error(title + ": " + message);
        if (!GraphicsEnvironment.isHeadless()) {
            if (Platform.isFxApplicationThread()) {
                Alert alert = new Alert(AlertType.ERROR);
                alert.setTitle(title);
                alert.getDialogPane().setHeaderText(null);
                alert.getDialogPane().setContent(message);
                alert.show();
            } else
                JOptionPane.showMessageDialog(getPossibleParent(), message, title, JOptionPane.ERROR_MESSAGE, null);
        }
//			showDialog(title, message);
    }

    public static void showPlainMessage(final String title, final String message) {
        logger.info(title + ": " + message);
        if (!GraphicsEnvironment.isHeadless()) {
            if (Platform.isFxApplicationThread()) {
                Alert alert = new Alert(AlertType.INFORMATION);
                alert.getDialogPane().setHeaderText(null);
                alert.setTitle(title);
                alert.setContentText(message);
                alert.show();
            } else
                JOptionPane.showMessageDialog(getPossibleParent(), message, title, JOptionPane.PLAIN_MESSAGE, null);
        }
//			showDialog(title, message);
    }

    public static void showPlainMessage(final String title, final JComponent message) {
        logger.info(title + ": " + message);
        if (!GraphicsEnvironment.isHeadless()) {
            if (Platform.isFxApplicationThread()) {
                Alert alert = new Alert(AlertType.INFORMATION);
                alert.getDialogPane().setHeaderText(null);
                alert.setTitle(title);
                SwingNode node = new SwingNode();
                node.setContent(message);
                alert.getDialogPane().setContent(node);
                alert.show();
            } else
                JOptionPane.showMessageDialog(getPossibleParent(), message, title, JOptionPane.PLAIN_MESSAGE, null);
        }
//			showDialog(title, message);
    }

//	private static void showDialog(final String title, String message) {
//		final JDialog dialog = new JDialog((Frame)null, title, true);
//
//		if (message.contains("\n"))
//			message = message.replaceAll("\n", "<br>");
//		JLabel label = new JLabel(message);
//		JPanel panel = new JPanel(new BorderLayout());
//		panel.add(label, BorderLayout.CENTER);
//		JButton btnOK = new JButton("OK");
//		btnOK.addActionListener(new ActionListener() {
//
//			@Override
//			public void actionPerformed(ActionEvent e) {
//				dialog.setVisible(false);
//				dialog.dispose();
//			}
//
//		});
//		JPanel panelButtons = new JPanel();
//		panelButtons.add(btnOK);
//		panel.add(panelButtons, BorderLayout.SOUTH);
//
//		dialog.add(panel);
//		dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//		dialog.pack();
//		dialog.setLocationRelativeTo(null);
//		dialog.setVisible(true);
//	}


    /**
     * Prompt to remove a single, specified selected object.
     *
     * @param pathObjectSelected
     * @param hierarchy
     * @return
     */
    public static boolean promptToRemoveSelectedObject(PathObject pathObjectSelected, PathObjectHierarchy hierarchy) {
        // Can't delete null - or a TMACoreObject
        if (pathObjectSelected == null || pathObjectSelected instanceof TMACoreObject)
            return false;

        if (QuPathGUI.getInstance().getProfileChoice() == QuPathGUI.UserProfileChoice.CONTRACTOR_MODE) {
            if (pathObjectSelected.getPathClass().getName().contains("ROI_")) {
                DisplayHelpers.showMessageDialog("Warning", "ROI annotations cannot be deleted in contractor mode!");
                return false;
            }
        }

        // Deselect first
        hierarchy.getSelectionModel().deselectObject(pathObjectSelected);

        if (pathObjectSelected.hasChildren()) {
            DialogButton confirm = showYesNoCancelDialog("Delete object", String.format("Keep %d descendant object(s)?", PathObjectTools.countDescendants(pathObjectSelected)));
            if (confirm == DialogButton.CANCEL)
                return false;
            if (confirm == DialogButton.YES)
                hierarchy.removeObject(pathObjectSelected, true);
            else
                hierarchy.removeObject(pathObjectSelected, false);
        } else if (pathObjectSelected.isPoint()) {
            int nPoints = ((PointsROI) pathObjectSelected.getROI()).getNPoints();
            if (nPoints > 1) {
                if (!DisplayHelpers.showYesNoDialog("Delete object", String.format("Delete %d points?", nPoints)))
                    return false;
                else
                    hierarchy.removeObject(pathObjectSelected, false);
            } else
                hierarchy.removeObject(pathObjectSelected, false);
        } else if (pathObjectSelected.isDetection()) {
            // Check whether to delete a detection object... can't simply be redrawn (like an annotation), so be cautious...
            if (!DisplayHelpers.showYesNoDialog("Delete object", "Are you sure you want to delete this detection object?"))
                return false;
            else
                hierarchy.removeObject(pathObjectSelected, false);
        } else
            hierarchy.removeObject(pathObjectSelected, false);
        //		updateRoiEditor();
        //		pathROIs.getObjectList().remove(pathObjectSelected);
        //		repaint();
        return true;
    }


//	public static String showInputDialog(final String title, String message, String defaultText) {
//		if (!GraphicsEnvironment.isHeadless()) {
//	
//			final JDialog dialog = new JDialog((Frame)null, title, true);
//	
//			if (message.contains("\n"))
//				message = message.replaceAll("\n", "<br>");
//			JLabel label = new JLabel(message);
//			JPanel panel = new JPanel(new BorderLayout());
//			panel.add(label, BorderLayout.NORTH);
//			final JTextField textField = new JTextField();
//			if (defaultText != null)
//				textField.setText(defaultText);
//			panel.add(textField, BorderLayout.CENTER);
//			JButton btnOK = new JButton("OK");
//			btnOK.addActionListener(new ActionListener() {
//	
//				@Override
//				public void actionPerformed(ActionEvent e) {
//					dialog.setVisible(false);
//					dialog.dispose();
//				}
//				
//			});
//			JPanel panelButtons = new JPanel();
//			panelButtons.add(btnOK);
//			panel.add(panelButtons, BorderLayout.SOUTH);
//			
//			dialog.add(panel);
//			dialog.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
//			dialog.pack();
//			dialog.setLocationRelativeTo(null);
//			dialog.setModal(true);
//			dialog.setVisible(true);
//			return textField.getText();
//		}
//		return null;
//	}


    /**
     * Make a snapshot (image) showing what is currently displayed in a QuPath window,
     * or the active viewer within QuPath.
     *
     * @param qupath
     * @param wholeWindow
     * @return
     */
    public static BufferedImage makeSnapshot(final QuPathGUI qupath, final boolean wholeWindow) {
        return SwingFXUtils.fromFXImage(makeSnapshotFX(qupath, wholeWindow), null);
    }


    public static WritableImage makeSnapshotFX(final QuPathGUI qupath, final boolean wholeWindow) {
        if (wholeWindow) {
            Scene scene = qupath.getStage().getScene();
            return scene.snapshot(null);
        } else {
            // Temporarily remove the selected border color while copying
            Color borderColor = qupath.getViewer().getBorderColor();
            qupath.getViewer().setBorderColor(null);
            WritableImage img = qupath.getViewer().getView().snapshot(null, null);
            qupath.getViewer().setBorderColor(borderColor);
            return img;
        }
    }


    public static String getMagnificationString(final QuPathViewer viewer) {
        if (viewer == null || !viewer.hasServer())
            return "";
        return String.format("%.2fx", viewer.getMagnification());
    }


    /**
     * Prompt user to select all currently-selected objects (except TMA core objects).
     *
     * @param imageData
     * @return
     */
    public static boolean promptToClearAllSelectedObjects(final ImageData<?> imageData) {
        // Get all non-TMA core objects
        PathObjectHierarchy hierarchy = imageData.getHierarchy();
        Collection<PathObject> selectedRaw = hierarchy.getSelectionModel().getSelectedObjects();
        List<PathObject> selected = selectedRaw.stream().filter(p -> !(p instanceof TMACoreObject)).collect(Collectors.toList());

        if (selected.isEmpty()) {
            if (selectedRaw.size() > selected.size())
                showErrorMessage("Delete selected objects", "No valid objects selected! \n\nNote: Individual TMA cores cannot be deleted with this method.");
            else
                showErrorMessage("Delete selected objects", "No objects selected!");
            return false;
        }

        int n = selected.size();
        if (showYesNoDialog("Delete objects", "Delete " + n + " objects?")) {
            // Check for descendants
            List<PathObject> children = new ArrayList<>();
            for (PathObject temp : selected) {
                children.addAll(temp.getChildObjects());
            }
            children.removeAll(selected);
            boolean keepChildren = true;
            if (!children.isEmpty()) {
                DialogButton response = DisplayHelpers.showYesNoCancelDialog("Delete objects", "Keep descendant objects?");
                if (response == DialogButton.CANCEL)
                    return false;
                keepChildren = response == DialogButton.YES;
            }


            hierarchy.removeObjects(selected, keepChildren);
            hierarchy.getSelectionModel().clearSelection();
            imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Delete selected objects", "clearSelectedObjects(" + keepChildren + ");"));
            if (keepChildren)
                logger.info(selected.size() + " object(s) deleted");
            else
                logger.info(selected.size() + " object(s) deleted with descendants");
            imageData.getHistoryWorkflow().addStep(new DefaultScriptableWorkflowStep("Clear selected objects", "clearSelectedObjects();"));
            logger.info(selected.size() + " object(s) deleted");
            return true;
        } else
            return false;
    }


    /**
     * Show a window containing plain text, with the specified properties.
     *
     * @param owner
     * @param title
     * @param contents
     * @param modality
     * @param isEditable
     */
    public static void showTextWindow(final Window owner, final String title, final String contents, final Modality modality, final boolean isEditable) {
        logger.info("{}\n{}", title, contents);
        Stage dialog = new Stage();
        dialog.initOwner(owner);
        dialog.initModality(modality);
        dialog.setTitle(title);

        TextArea textArea = new TextArea();
        textArea.setPrefColumnCount(60);
        textArea.setPrefRowCount(25);

        textArea.setText(contents);
        textArea.setWrapText(true);
        textArea.positionCaret(0);
        textArea.setEditable(isEditable);

        dialog.setScene(new Scene(textArea));
        dialog.show();
    }


}
