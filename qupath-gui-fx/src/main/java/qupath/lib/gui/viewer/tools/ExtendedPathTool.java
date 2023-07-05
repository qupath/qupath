package qupath.lib.gui.viewer.tools;

import java.util.Collection;

import javafx.beans.binding.Bindings;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import qupath.lib.gui.viewer.QuPathViewer;


public class ExtendedPathTool implements PathTool {
	
	private ObservableList<PathTool> tools;
	private ObjectProperty<PathTool> selectedTool;
	
	private StringProperty nameProperty = new SimpleStringProperty();
	private ObjectProperty<Node> iconProperty = new SimpleObjectProperty<>();
	
	private QuPathViewer viewer;
	
	ExtendedPathTool(Collection<? extends PathTool> tools) {
		this(tools, tools.iterator().next());
	}
	
	ExtendedPathTool(Collection<? extends PathTool> tools, PathTool defaultSelectedTool) {
		this.tools = FXCollections.observableArrayList(tools);
		this.selectedTool = new SimpleObjectProperty<>(defaultSelectedTool);
		this.nameProperty.bind(Bindings.createStringBinding(() -> selectedTool.getValue().getName(), selectedTool));
		this.iconProperty.bind(Bindings.createObjectBinding(() -> selectedTool.getValue().getIcon(), selectedTool));
		this.selectedTool.addListener(this::handleToolChange);
	}
	
	private void handleToolChange(ObservableValue<? extends PathTool> selectedTool, PathTool previousTool, PathTool newTool) {
		if (viewer == null)
			return;
		
		if (previousTool != null && viewer != null)
			previousTool.deregisterTool(viewer);
		
		if (newTool != null && viewer != null)
			newTool.registerTool(viewer);
	}
	
	public ObservableList<PathTool> getAvailableTools() {
		return tools;
	}
	
	public ObjectProperty<PathTool> selectedTool() {
		return selectedTool;
	}

	@Override
	public void registerTool(QuPathViewer viewer) {
		var tool = selectedTool.getValue();
		if (tool == null || viewer == null)
			return;
		tool.registerTool(viewer);
		this.viewer = viewer;
	}

	@Override
	public void deregisterTool(QuPathViewer viewer) {
		var tool = selectedTool.getValue();
		if (tool == null || viewer == null)
			return;
		tool.deregisterTool(viewer);
		if (this.viewer == viewer)
			this.viewer = null;
	}

	@Override
	public ReadOnlyStringProperty nameProperty() {
		return nameProperty;
	}

	@Override
	public ReadOnlyObjectProperty<Node> iconProperty() {
		return iconProperty;
	}

}
