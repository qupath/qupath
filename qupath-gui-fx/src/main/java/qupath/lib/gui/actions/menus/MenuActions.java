package qupath.lib.gui.actions.menus;

import java.util.List;

import org.controlsfx.control.action.Action;

public interface MenuActions {
	
	List<Action> getActions();
	
	String getName();
	
}
