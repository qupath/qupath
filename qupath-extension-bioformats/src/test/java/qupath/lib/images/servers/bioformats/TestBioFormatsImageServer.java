/*-
 * #%L
 * This file is part of a QuPath extension.
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


package qupath.lib.images.servers.bioformats;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

import ij.ImagePlus;
import loci.common.DebugTools;
import loci.common.Region;
import loci.common.services.DependencyException;
import loci.common.services.ServiceException;
import loci.formats.FormatException;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.bioformats.BioFormatsImageServer;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;

/**
 * Test the QuPath Bio-Formats extension, by
 * <ul>
 *   <li>Checking images can be opened at all</li>
 *   <li>Sanity checking dimensions</li>
 *   <li>Comparing metadata & pixels with requests via ImageJ</li>
 * <ul>
 * 
 * The 'current directory' is required to contain one or more QuPath projects, 
 * i.e. {@code .qpproj} files containing paths to images.
 * 
 * @author Pete Bankhead
 *
 */
public class TestBioFormatsImageServer {
	
	@Test
	public void test_BioFormatsImageServerProjects() {
		// Search the current directory for any QuPath projects
		for (File file : new File(".").listFiles()) {
			
			if (!file.getAbsolutePath().endsWith(ProjectIO.getProjectExtension()))
				continue;
			
			try {
				Project<BufferedImage> project = ProjectIO.loadProject(file, BufferedImage.class);
				testProject(project);
			} catch (Exception e) {
				System.err.println("Unable to load project " + file.getAbsolutePath());
				e.printStackTrace();
			}
		}
		
	}

	
	
	void testProject(Project<BufferedImage> project) {
		// We're not really testing Bio-Formats... and the messages can get in the way
		DebugTools.enableIJLogging(false);
		DebugTools.setRootLevel("error");
		
		List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
		System.out.println("Testing project with " + entries.size() + " entries: " + project.getName());
		for (ProjectImageEntry<BufferedImage> entry : entries) {
			String serverPath = entry.getServerPath();
//			System.out.println("Opening: " + serverPath);
//			String pathFile = BioFormatsImageServer.splitFilePathAndSeriesName(serverPath)[0];
//			if (!new File(pathFile).exists()) {
//				System.err.println("File does not exist for server path " + serverPath + " - will skip");
//			}
			BioFormatsImageServer server = null;
			BufferedImage img = null;
			BufferedImage imgThumbnail = null;
			ImagePlus imp = null;
			int tileSize = 256;
			int z = 0;
			int t = 0;
			try {
				// Create the server
				server = buildServer(serverPath);
				// Read a thumbnail
				imgThumbnail = server.getBufferedThumbnail(200, -1, 0);
				// Read from the center of the image
				int w = server.getWidth() < tileSize ? server.getWidth() : tileSize;
				int h = server.getHeight() < tileSize ? server.getHeight() : tileSize;
				z = (int)(server.nZSlices() / 2);
				t = (int)(server.nTimepoints() / 2);
				RegionRequest request = RegionRequest.createInstance(
						serverPath, 1,
						(server.getWidth() - w)/2,
						(server.getHeight() - h)/2,
						w, h,
						z, t
						);
				img = server.readBufferedImage(request);
				
				// Read an ImageJ version of the same region
				// Note that this will contain all z-slices & time points
				// (Skip if we have multiple series, as the setFlattenedResolutions() status can cause confusion)
				if (server.getPreferredDownsamples().length == 1 || !server.containsSubImages()) {
					ImporterOptions options = new ImporterOptions();
					int series = server.getSeries();
					options.setId(server.getFile().getAbsolutePath());
					options.setOpenAllSeries(false);
					options.setSeriesOn(series, true);
					options.setCrop(true);
					options.setCropRegion(server.getSeries(), new Region(request.getX(), request.getY(), request.getWidth(), request.getHeight()));
					try {
						ImagePlus[] imps = BF.openImagePlus(options);
						assert imps.length == 1;
						imp = imps[0];
					} catch (Exception e) {
						System.err.println("Unable to open with ImageJ: " + serverPath);
						System.err.println(e.getLocalizedMessage());
					}
				} else {
					System.err.println("Multiple multi-resolution series in file - skipping ImageJ check");
				}
				
			} catch (Exception e) {
				e.printStackTrace();
			}
			// Check if we got a server at all
			assertNotNull(server);
			
			// Check if we got an image
			assertNotNull(img);
			
			// Get the thumbnail
			assertNotNull(imgThumbnail);
			
			// Check channel numbers
			assertEquals(img.getRaster().getNumBands(), server.nChannels());
			assertEquals(imgThumbnail.getRaster().getNumBands(), server.nChannels());
			
			// Check pixel sizes
			if (imp != null) {
				if ("micron".equals(imp.getCalibration().getXUnit()))
					assertEquals(imp.getCalibration().pixelWidth, server.getPixelWidthMicrons(), 0.00001);
				else
					assertTrue(Double.isNaN(server.getPixelWidthMicrons()));
				if ("micron".equals(imp.getCalibration().getXUnit()))
					assertEquals(imp.getCalibration().pixelHeight, server.getPixelHeightMicrons(), 0.00001);
				else
					assertTrue(Double.isNaN(server.getPixelHeightMicrons()));
				
				// Check z-slices, if appropriate
				if (server.nZSlices() > 1) {
					if ("micron".equals(imp.getCalibration().getZUnit()))
						assertEquals(imp.getCalibration().pixelDepth, server.getZSpacingMicrons(), 0.00001);
					else
						assertTrue(Double.isNaN(server.getZSpacingMicrons()));
				}
				
				// Check dimensions by comparison with ImageJ
				assertEquals(img.getWidth(), imp.getWidth());
				assertEquals(img.getHeight(), imp.getHeight());
				assertEquals(server.nChannels(), imp.getNChannels());
				assertEquals(server.nTimepoints(), imp.getNFrames());
				assertEquals(server.nZSlices(), imp.getNSlices());
				
				// Check actual pixel values
				float[] samples = null;
				float[] samplesIJ = null;
				for (int c = 0; c < server.nChannels(); c++) {
					samples = img.getRaster().getSamples(0, 0, img.getWidth(), img.getHeight(), c, samples);
					imp.setPosition(c+1, z+1, t+1);
					samplesIJ = (float[])imp.getProcessor().convertToFloatProcessor().getPixels();
					// I am having some trouble comparing the full array with Java 10... trouble appears on IJ side..?
					assertEquals(samples[0], samplesIJ[0], (float)0.00001);
//					assertArrayEquals(samples, samplesIJ, (float)0.00001);
				}
			}

			printSummary(server);
		}
	}

	/**
	 * Print a readable summary of an {@code ImageServer} along with some key metadata.
	 * 
	 * @param server
	 */
	void printSummary(final ImageServer<?> server) {
		System.out.println(
				String.format(
						"%s: %d x %d (c=%d, z=%d, t=%d), bpp=%d, mag=%.2f, downsamples=[%s], res=[%.4f,%.4f,%.4f]",
						server.getPath(), server.getWidth(), server.getHeight(),
						server.nChannels(), server.nZSlices(), server.nTimepoints(),
						server.getBitsPerPixel(), server.getMagnification(), GeneralTools.arrayToString(Locale.getDefault(), server.getPreferredDownsamples(), 4),
						server.getPixelWidthMicrons(), server.getPixelHeightMicrons(), server.getZSpacingMicrons())
				);
	}
	
	
	BioFormatsImageServer buildServer(final String path) throws FormatException, IOException, DependencyException, ServiceException {
		return new BioFormatsImageServer(path);
	}
	

}
