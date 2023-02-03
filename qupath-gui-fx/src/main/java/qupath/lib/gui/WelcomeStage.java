/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2022 QuPath developers, The University of Edinburgh
 * %%
 * QuPath is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * QuPath is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with QuPath.  If not, see <https://www.gnu.org/licenses/>.
 * #L%
 */

package qupath.lib.gui;

import org.controlsfx.glyphfont.FontAwesome.Glyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.controlsfx.glyphfont.GlyphFontRegistry;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;
import qupath.lib.common.GeneralTools;
import qupath.lib.gui.prefs.PathPrefs;
import qupath.lib.gui.prefs.PathPrefs.AutoUpdateType;
import qupath.lib.gui.prefs.QuPathStyleManager;
import qupath.lib.gui.tools.GuiTools;
import qupath.lib.gui.tools.PaneTools;


/**
 * Welcome page when launching QuPath.
 * <p>
 * Currently this is undecorated; it can be closed by pressing 'Get started' 
 * (either with mouse or spacebar), double-clicking in the dialog, or pressing escape.
 * 
 * @author Pete Bankhead
 * @since v0.4.0
 */
class WelcomeStage {
	
	private static final Logger logger = LoggerFactory.getLogger(WelcomeStage.class);
	
	private static Stage INSTANCE;
	
	private static final String TITLE = QuPathResources.getString("Welcome.title");
	
	public static Stage getInstance(QuPathGUI qupath) {
		if (INSTANCE == null) {
			INSTANCE = buildStage(qupath);
		}
		return INSTANCE;
	}
	
	
	
	private static Stage buildStage(QuPathGUI qupath) {
		
		var stage = new Stage();
		if (qupath != null)
			stage.initOwner(qupath.getStage());
		
		var btnCode = createGlyphButton(
				QuPathResources.getString("Welcome.develop"), 
				Glyph.CODE,
				"https://github.com/qupath/qupath" 
				);
		
		var btnDocs = createGlyphButton(
				QuPathResources.getString("Welcome.docs"), 
				Glyph.FILE_TEXT_ALT,
				"https://qupath.readthedocs.io/en/0.4" 
				);
		
		var btnForum = createGlyphButton(
				QuPathResources.getString("Welcome.discuss"), 
				Glyph.COMMENTS_ALT,
				"https://forum.image.sc/tag/qupath" 
				);
		var paneButtons = PaneTools.createColumnGrid(btnDocs, btnForum, btnCode);
				
		var pane = new BorderPane(paneButtons);
		
		var imageView = new ImageView(WelcomeStage.class.getResource("/images/qupath-welcome.png").toExternalForm()); 
		imageView.setFitWidth(440.0);
		imageView.setOpacity(0.9);
		imageView.setPreserveRatio(true);
		
		var textTitle = new Text(QuPathResources.getString("Welcome.welcomeMessage")); 
		textTitle.setStyle("-fx-font-weight: bold; -fx-font-size: 150%; -fx-fill: -fx-text-base-color;"); 

		var topPane = new VBox();
		topPane.setAlignment(Pos.CENTER);
		topPane.getChildren().add(textTitle);
		var version = QuPathGUI.getVersion();
		if (version != null) {
			String versionString = version.toString();
			if (!versionString.startsWith("v")) 
				versionString = "v" + versionString; 
			var textVersion = new Text(versionString);
			textVersion.setStyle("-fx-fill: -fx-text-base-color; -fx-opacity: 0.6;"); 
			var spacer = new Pane();
			spacer.setPrefHeight(2);
			topPane.getChildren().add(spacer);
			topPane.getChildren().add(textVersion);
		}
		
		topPane.getChildren().add(imageView);
		BorderPane.setAlignment(topPane, Pos.CENTER);
		
		pane.setTop(topPane);
		
		String defaultMessage = QuPathResources.getString("Welcome.defaultMessage"); 
		var labelExplanation = new Label(defaultMessage);
		labelExplanation.setAlignment(Pos.CENTER);
		labelExplanation.textProperty().bind(Bindings.createStringBinding(() -> {
			if (btnCode.isHover()) {
				return QuPathResources.getString("Welcome.developMessage");
			} else if (btnDocs.isHover()) {
				return QuPathResources.getString("Welcome.docsMessage");
			} else if (btnForum.isHover()) {
				return QuPathResources.getString("Welcome.discussMessage");
			} else
				return defaultMessage;
		}, btnCode.hoverProperty(), btnDocs.hoverProperty(), btnForum.hoverProperty()));
		labelExplanation.setTextAlignment(TextAlignment.CENTER);
		labelExplanation.setAlignment(Pos.CENTER);
		labelExplanation.setMinHeight(40);
		labelExplanation.setOpacity(0.8);
		
		
		var comboThemes = new ComboBox<>(QuPathStyleManager.availableStylesProperty());
		comboThemes.getSelectionModel().select(QuPathStyleManager.selectedStyleProperty().get());
		comboThemes.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> QuPathStyleManager.selectedStyleProperty().set(n));
		var labelThemes = new Label(QuPathResources.getString("Welcome.chooseTheme") + ":");
		labelThemes.setLabelFor(comboThemes);
		labelThemes.setAlignment(Pos.CENTER_RIGHT);
		
		var comboUpdates = new ComboBox<AutoUpdateType>();
		comboUpdates.getItems().setAll(AutoUpdateType.values());
		comboUpdates.getSelectionModel().select(PathPrefs.autoUpdateCheckProperty().get());
		comboUpdates.getSelectionModel().selectedItemProperty().addListener((v, o, n) -> PathPrefs.autoUpdateCheckProperty().set(n));
		var labelUpdates = new Label(QuPathResources.getString("Welcome.checkUpdates") + ":");
		labelUpdates.setLabelFor(comboUpdates);
		labelUpdates.setAlignment(Pos.CENTER_RIGHT);

		var cbShowStartup = new CheckBox(QuPathResources.getString("Welcome.showOnStartup"));
		cbShowStartup.selectedProperty().bindBidirectional(PathPrefs.showStartupMessageProperty());
		cbShowStartup.setAlignment(Pos.CENTER_RIGHT);
		
		var paneOptions = new GridPane();
		paneOptions.setHgap(5);
		paneOptions.setVgap(5);
		paneOptions.setPadding(new Insets(5, 5, 0, 5));

		int row = 0;
		paneOptions.add(labelExplanation, 0, row++, GridPane.REMAINING, 1);
		var separator = new Separator(Orientation.HORIZONTAL);
		separator.setPadding(new Insets(5));
		paneOptions.add(separator, 0, row++, GridPane.REMAINING, 1);
		
		paneOptions.add(labelThemes, 0, row);
		paneOptions.add(comboThemes, 1, row, 1, 1);
		row++;

		paneOptions.add(labelUpdates, 0, row);
		paneOptions.add(comboUpdates, 1, row, 1, 1);
		row++;

		var separator2 = new Separator(Orientation.HORIZONTAL);
		separator2.setPadding(new Insets(5));
		paneOptions.add(separator2, 0, row++, GridPane.REMAINING, 1);

		var linkCiting = new Hyperlink(QuPathResources.getString("Welcome.clickForDetails"));
		linkCiting.setOnAction(e -> QuPathGUI.openInBrowser("https://qupath.readthedocs.io/en/0.4/docs/intro/citing.html")); 
		var textCiting = new TextFlow(
				new Text(QuPathResources.getString("Welcome.cite") + System.lineSeparator()),
				linkCiting
				);
		textCiting.setTextAlignment(TextAlignment.CENTER);
		for (var n : textCiting.getChildren()) {
			n.setStyle("-fx-fill: -fx-text-base-color;"); 
		}
		
		paneOptions.add(textCiting, 0, row, GridPane.REMAINING, 1);
		row++;

		
		var btnStarted = new Button(QuPathResources.getString("Welcome.getStarted"));
//		btnStarted.setPrefHeight(40);
		btnStarted.setStyle("-fx-font-weight: bold; -fx-font-size: 110%;"); 
		btnStarted.setPadding(new Insets(10));
		btnStarted.setOnAction(e -> stage.close());
		
		paneOptions.add(btnStarted, 0, row, GridPane.REMAINING, 1);
		row++;

		paneOptions.add(cbShowStartup, 0, row, GridPane.REMAINING, 1);
		row++;

		PaneTools.setToExpandGridPaneWidth(comboThemes, comboUpdates, cbShowStartup, btnStarted, labelExplanation);
		
		if (GeneralTools.isMac() && "aarch64".equals(System.getProperty("os.arch"))) {  //$NON-NLS-2$
			var textSilicon = makeMacAarch64Message();
			textSilicon.setTextAlignment(TextAlignment.CENTER);
			textSilicon.setOpacity(0.9);
			var sepSilicon = new Separator(Orientation.HORIZONTAL);
			sepSilicon.setPadding(new Insets(5, 5, 0, 5));
			PaneTools.setToExpandGridPaneWidth(sepSilicon, textSilicon);
			paneOptions.add(sepSilicon, 0, row++, GridPane.REMAINING, 1);
			paneOptions.add(textSilicon, 0, row++, GridPane.REMAINING, 1);
			row++;
		}
		
		pane.setBottom(paneOptions);
		pane.setPadding(new Insets(10));
		
		// Transparent undecorated stage looked pretty good on recent macOS,
		// but not on Windows 10 (because there was no drop shadow)
//		stage.setScene(new Scene(pane, Color.TRANSPARENT));
//		stage.initStyle(StageStyle.UNDECORATED);
		stage.setScene(new Scene(pane));
		stage.setTitle(TITLE);
		GuiTools.makeDraggableStage(stage);
		stage.getScene().setOnMouseClicked(e -> {
			if (e.getClickCount() == 2) {
				logger.info("Startup stage closed by double-click"); 
				stage.close();
			}
		});
		stage.getScene().setOnKeyPressed(e -> {
			if (!e.isConsumed() && e.getCode() == KeyCode.ESCAPE) {
				logger.info("Startup stage closed by pressing escape"); 
				stage.close();				
			}
		});
		
		btnStarted.requestFocus();
		
		return stage;
	}
	
	
	private static TextFlow makeMacAarch64Message() {
		String text = QuPathResources.getString("Welcome.macOsAarch64");
		
		int ind1 = text.indexOf("{{");
		int ind2 = text.lastIndexOf("}}");
		String startText = text.substring(0, ind1);
		String linkText = text.substring(ind1+2, ind2);
		String endText = text.substring(ind2+2);
		
		var textSiliconExperimental = new Text(startText); 
		textSiliconExperimental.setStyle("-fx-font-weight: bold; -fx-fill: -qp-script-error-color;"); 
		var linkSilicon = new Hyperlink(linkText); 
		var textSiliconExperimental2 = new Text(endText); 
		textSiliconExperimental2.setStyle("-fx-fill: -fx-text-base-color;"); 
		linkSilicon.setOnAction(e -> QuPathGUI.openInBrowser("https://qupath.readthedocs.io/en/0.4/docs/intro/installation.html")); 
		return new TextFlow(
				textSiliconExperimental, linkSilicon, textSiliconExperimental2
				);
	}
	
	
	
	
	private static Button createGlyphButton(String text, Glyph glyph, String url) {
		
		var button = new Button();
		button.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
		button.setPrefSize(100, 100);
		if (text == null)
			button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
		else {
			button.setText(text);
			button.setContentDisplay(ContentDisplay.TOP);
		}
		
		if (url != null) {
			button.setOnAction(e -> {
				QuPathGUI.openInBrowser(url);
			});
			button.setTooltip(new Tooltip(url));
		}
		
		var fontAwesome = GlyphFontRegistry.font("FontAwesome"); 
		var icon = fontAwesome.create(glyph);
		icon.size((int)(button.getPrefWidth() * 0.5));
		button.setGraphic(icon);
		
		return button;
	}
	

}
