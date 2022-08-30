/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2021 QuPath developers, The University of Edinburgh
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

package qupath.lib.gui.dialogs;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TestDialogs {

	/**
	 * Ensure there are no exceptions when showing basic messages and notifications without the UI.
	 */
	@Test
	void testNoUI() {
		
		assertDoesNotThrow(() -> Dialogs.showErrorMessage("Error", "Message"));
		assertDoesNotThrow(() -> Dialogs.showErrorMessage("Error", new RuntimeException("This RuntimeException is intentional")));
//		assertDoesNotThrow(() -> Dialogs.showMessageDialog(title, message)); // This returns true/false depending upon button
		assertDoesNotThrow(() -> Dialogs.showNoImageError("No image title"));
		assertDoesNotThrow(() -> Dialogs.showNoProjectError("No project title"));
		
		assertDoesNotThrow(() -> Dialogs.showInfoNotification("Info", "Notification"));
		assertDoesNotThrow(() -> Dialogs.showPlainNotification("Plain", "Notification"));
		assertDoesNotThrow(() -> Dialogs.showWarningNotification("Warning", "Notification"));
		assertDoesNotThrow(() -> Dialogs.showErrorNotification("Error", "Notification"));

	}

}
