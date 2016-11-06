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

package qupath.lib.plugins;

import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import qupath.lib.images.ImageData;
import qupath.lib.images.stores.ImageRegionStore;
import qupath.lib.regions.ImageRegion;

/**
 * A PluginRunner that simple logs progress and output.
 * 
 * This doesn't need to be run on any particular thread (e.g. Platform of EDT).
 * 
 * @author Pete Bankhead
 *
 * @param <T>
 */
public class CommandLinePluginRunner<T> extends AbstractPluginRunner<T> {
	
	private ImageData<T> imageData;

	public CommandLinePluginRunner(final ImageData<T> imageData, boolean batchMode) {
		this(null, imageData, batchMode);
	}

	public CommandLinePluginRunner(final ImageRegionStore<T> regionStore, final ImageData<T> imageData, boolean batchMode) {
		super(regionStore, batchMode);
		this.imageData = imageData;
	}

	@Override
	public SimpleProgressMonitor makeProgressMonitor() {
		return new CommandLineProgressMonitor();
	}

	@Override
	public ImageData<T> getImageData() {
		return imageData;
	}
	
	
	
	
	public static class CommandLineProgressMonitor implements SimpleProgressMonitor {
		
		final private static Logger logger = LoggerFactory.getLogger(CommandLineProgressMonitor.class);
		
		private long startTime;
		private AtomicInteger progress = new AtomicInteger(0);
		private int maxProgress;
		private String lastMessage;
		
		@Override
		public void startMonitoring(String message, int maxProgress, boolean mayCancel) {
			startTime = System.currentTimeMillis();
			this.maxProgress = maxProgress;
			if (message != null)
				logger.info(message);
		}

		@Override
		public synchronized void updateProgress(int increment, String message, ImageRegion region) {
			int progressValue = progress.addAndGet(increment);
			int progressPercent = (int)((double)progressValue / maxProgress * 100 + .5);
			String newMessage = message + " (" + progressPercent + "%)";
			if (!newMessage.equals(lastMessage) || progressValue >= maxProgress)
				logger.info(newMessage);
			lastMessage = newMessage;
			
			if (progressValue >= maxProgress) {
				pluginCompleted(String.format("Processing complete in %.2f seconds", (System.currentTimeMillis() - startTime)/1000.));
			}
		}

		@Override
		public void pluginCompleted(String message) {
			logger.info(message);
		}
		
		// Not possible to cancel
		@Override
		public boolean cancelled() {
			return false;
		}
		
	}
	

}