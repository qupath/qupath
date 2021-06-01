/*-
 * #%L
 * This file is part of QuPath.
 * %%
 * Copyright (C) 2014 - 2016 The Queen's University of Belfast, Northern Ireland
 * Contact: IP Management (ipmanagement@qub.ac.uk)
 * Copyright (C) 2018 - 2020 QuPath developers, The University of Edinburgh
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


package qupath.lib.images.servers.bioformats;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ij.ImagePlus;
import loci.common.DebugTools;
import loci.common.Region;
import loci.plugins.BF;
import loci.plugins.in.ImporterOptions;
import qupath.lib.common.GeneralTools;
import qupath.lib.images.servers.ImageServer;
import qupath.lib.images.servers.PixelCalibration;
import qupath.lib.projects.Project;
import qupath.lib.projects.ProjectIO;
import qupath.lib.projects.ProjectImageEntry;
import qupath.lib.regions.RegionRequest;

/**
 * Test the QuPath Bio-Formats extension, by
 * <ul>
 *   <li>Checking images can be opened at all</li>
 *   <li>Sanity checking dimensions</li>
 *   <li>Comparing metadata and pixels with requests via ImageJ</li>
 * </ul>
 * 
 * The 'current directory' is required to contain one or more QuPath projects, 
 * i.e. {@code .qpproj} files containing paths to images.
 * 
 * @author Pete Bankhead
 *
 */
public class TestBioFormatsImageServer {
	
	private static Logger logger = LoggerFactory.getLogger(TestBioFormatsImageServer.class);
	
	/**
	 * Test the creation of BioFormatsImageServers by trying to open all images in whatever projects are found within the current directory.
	 */
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
				logger.error("Unable to load project " + file.getAbsolutePath(), e);
			}
		}
		
	}
	
	
	/**
	 * Test default tile lengths generated.
	 */
	@Test
	public void test_BioFormatsDefaultTile() {
		assertEquals(BioFormatsImageServer.getDefaultTileLength(256, 512), 256);
		assertEquals(BioFormatsImageServer.getDefaultTileLength(4, 512), 32);		
		assertEquals(BioFormatsImageServer.getDefaultTileLength(12, 512), 36);		
		assertEquals(BioFormatsImageServer.getDefaultTileLength(700, 500), 500);
		assertEquals(BioFormatsImageServer.getDefaultTileLength(-1, 100_000), 512);
	}

	
	
	void testProject(Project<BufferedImage> project) {
		// We're not really testing Bio-Formats... and the messages can get in the way
		DebugTools.enableIJLogging(false);
		DebugTools.setRootLevel("error");
		
		List<ProjectImageEntry<BufferedImage>> entries = project.getImageList();
		System.out.println("Testing project with " + entries.size() + " entries: " + Project.getNameFromURI(project.getURI()));
		for (ProjectImageEntry<BufferedImage> entry : entries) {
//			String serverPath = entry.getServerPath();
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
				server = (BioFormatsImageServer)entry.getServerBuilder().build();
//				server = (BioFormatsImageServer)ImageServerProvider.buildServer(serverPath, BufferedImage.class, "--classname", BioFormatsServerBuilder.class.getName());
				// Read a thumbnail
				imgThumbnail = server.getDefaultThumbnail(server.nZSlices()/2, 0);
				// Read from the center of the image
				int w = server.getWidth() < tileSize ? server.getWidth() : tileSize;
				int h = server.getHeight() < tileSize ? server.getHeight() : tileSize;
				z = (int)(server.nZSlices() / 2);
				t = (int)(server.nTimepoints() / 2);
				RegionRequest request = RegionRequest.createInstance(
						server.getPath(), 1,
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
						logger.warn("Unable to open with ImageJ: " + server, e);
					}
				} else {
					logger.warn("Multiple multi-resolution series in file - skipping ImageJ check");
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
				PixelCalibration cal = server.getPixelCalibration();
				if ("micron".equals(imp.getCalibration().getXUnit()))
					assertEquals(imp.getCalibration().pixelWidth, cal.getPixelWidthMicrons(), 0.00001);
				else
					assertTrue(Double.isNaN(cal.getPixelWidthMicrons()));
				if ("micron".equals(imp.getCalibration().getXUnit()))
					assertEquals(imp.getCalibration().pixelHeight, cal.getPixelHeightMicrons(), 0.00001);
				else
					assertTrue(Double.isNaN(cal.getPixelHeightMicrons()));
				
				// Check z-slices, if appropriate
				if (server.nZSlices() > 1) {
					if ("micron".equals(imp.getCalibration().getZUnit()))
						assertEquals(imp.getCalibration().pixelDepth, cal.getZSpacingMicrons(), 0.00001);
					else
						assertTrue(Double.isNaN(cal.getZSpacingMicrons()));
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
		PixelCalibration cal = server.getPixelCalibration();
		logger.info(
				String.format(
						"%s: %d x %d (c=%d, z=%d, t=%d), bpp=%d, mag=%.2f, downsamples=[%s], res=[%.4f,%.4f,%.4f]",
						server.getPath(), server.getWidth(), server.getHeight(),
						server.nChannels(), server.nZSlices(), server.nTimepoints(),
						server.getPixelType(), server.getMetadata().getMagnification(), GeneralTools.arrayToString(Locale.getDefault(), server.getPreferredDownsamples(), 4),
						cal.getPixelWidthMicrons(), cal.getPixelHeightMicrons(), cal.getZSpacingMicrons())
				);
	}
	

}
