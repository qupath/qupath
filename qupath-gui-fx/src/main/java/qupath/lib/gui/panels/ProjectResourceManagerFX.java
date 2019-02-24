package qupath.lib.gui.panels;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.controlsfx.validation.ValidationSupport;
import org.controlsfx.validation.Validator;

import javafx.scene.control.MenuItem;
import javafx.scene.control.TextInputDialog;
import qupath.lib.gui.helpers.DisplayHelpers;
import qupath.lib.projects.ProjectResourceManager;

public class ProjectResourceManagerFX {
	
	public static <T> boolean promptToSaveResource(ProjectResourceManager<T> manager, T resource, String title, String prompt, String defaultName) throws IOException {
		
		
		var names = new HashSet<>(manager.getNames().stream().map(n -> n.toLowerCase()).collect(Collectors.toList()));
		
		TextInputDialog dialog = new TextInputDialog(defaultName);
		dialog.setTitle(title);
		dialog.setHeaderText(null);
		dialog.setContentText(prompt);
		var validation = new ValidationSupport();
		validation.registerValidator(dialog.getEditor(), Validator.createPredicateValidator(t -> !names.contains(t), "Name already exists!"));
		
		// Traditional way to get the response value.
		Optional<String> result = dialog.showAndWait();
		String name = result.orElseGet(null);
		if (name == null)
			return false;
		
		if (names.contains(name.toLowerCase())) {
			DisplayHelpers.showErrorMessage("Invalid name", name + " already exists! Please choose a unique name.");
			return false;
		}
		
//		String name = DisplayHelpers.showInputDialog(title, prompt, defaultName);
		manager.putResource(name, resource);
		return true;
	}
	
	public static <T> T promptToLoadResource(ProjectResourceManager<T> manager, String title, String prompt, String defaultName) throws IOException {
		Collection<String> choices = manager.getNames();
		String name = DisplayHelpers.showChoiceDialog(title, prompt, choices, null);
		if (name == null)
			return null;
		return manager.getResource(name);
	}
	
	public static <T> MenuItem[] createMenuItems(ProjectResourceManager<T> manager, Function<T, Void> func) throws IOException {
		return manager.getNames().stream().map(n -> createMenuItem(manager, n, func)).toArray(i -> new MenuItem[i]);
	}
	
	static <T> MenuItem createMenuItem(ProjectResourceManager<T> manager, String name, Function<T, Void> func) {
		var menuItem = new MenuItem(name);
		menuItem.setOnAction(e -> {
			try {
				func.apply(manager.getResource(name));
			} catch (Exception e1) {
				DisplayHelpers.showErrorMessage(name, e1);
			}
		});
		return menuItem;
	}

}
