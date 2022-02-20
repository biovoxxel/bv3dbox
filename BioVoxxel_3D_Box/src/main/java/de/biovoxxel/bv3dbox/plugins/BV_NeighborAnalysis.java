package de.biovoxxel.bv3dbox.plugins;

import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;
import org.scijava.prefs.DefaultPrefService;
import org.scijava.prefs.PrefService;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.Plot;
import ij.measure.ResultsTable;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.StatisticsOfLabelledPixels;

/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2022, Jan Brocher (BioVoxxel)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * Please cite BioVoxxel according to the provided DOI related to this software.
 * 
 */

public class BV_NeighborAnalysis {

	private LogService log = new StderrLogService();
	private PrefService prefs = new DefaultPrefService();
	private Boolean displayDebugImages = prefs.getBoolean(BV3DBoxSettings.class, "bv_3d_box_settings_display_debug_images", false);
	private CLIJ2 clij2;
	private ClearCLBuffer connectedComponentLabels;
	
	
	
	public BV_NeighborAnalysis(ImagePlus inputImage) {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
		
		this.clij2 = CLIJ2.getInstance();
		setupImage(inputImage);
	}

	public CLIJ2 getCurrentCLIJ2Instance() {
		return clij2;
	}
	
	
	private void setupImage(ImagePlus inputImage) {
		
		if (inputImage.getProcessor().isBinary()) {
			
			ClearCLBuffer temp_input_image = clij2.push(inputImage);
			connectedComponentLabels = clij2.create(temp_input_image.getDimensions(), NativeTypeEnum.Float);
			clij2.connectedComponentsLabelingDiamond(temp_input_image, connectedComponentLabels);
			temp_input_image.close();
			
		} else {
			
			connectedComponentLabels = clij2.push(inputImage);
			
		}
		connectedComponentLabels.setName(inputImage.getTitle());
	}

	
	public ClearCLBuffer getConnectedComponentInput() {
		return connectedComponentLabels;
	}
	
	/**
	 * 
	 * @param input_image - connected component labels image as ClearCLBuffer expected
	 * @param method - NeighborMethods enum type expected
	 * @return ClearCLBuffer
	 */
	public ClearCLBuffer getNeighborCountMap(ClearCLBuffer input_image, String method, String sizeRange, String distanceRange, Boolean excludeFinalEdgeObjects) {
		
		log.debug("input_image = " + input_image);
		log.debug("method = " + method);
		log.debug("distanceRange = " + distanceRange);
		log.debug("excludeFinalEdgeObjects = " + excludeFinalEdgeObjects);
		
		if (!sizeRange.equalsIgnoreCase("0-infinity")) {
			
			log.debug("Running size excludion with range = " + sizeRange);
			
			ClearCLBuffer size_limited_label_image = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);
			
			clij2.copy(input_image, size_limited_label_image);
			
			double minSize = (double) BV3DBoxUtilities.getMinFromRange(distanceRange);
			double maxSize = (double) BV3DBoxUtilities.getMaxFromRange(distanceRange);
			
			clij2.excludeLabelsOutsideSizeRange(size_limited_label_image, input_image, minSize, maxSize);
			
			if(displayDebugImages) { BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, input_image, true, LutNames.GLASBEY_LUT); }
			
			size_limited_label_image.close();
		} 
		
		
		ClearCLBuffer voronoi_image = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);	
		ClearCLBuffer neighbor_count_map = clij2.create(voronoi_image);
		neighbor_count_map.setName("NCM_" + input_image.getName());
		
		clij2.extendLabelingViaVoronoi(input_image, voronoi_image);
		
		if (displayDebugImages) { BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, voronoi_image, false, LutNames.GLASBEY_LUT); }
		
		if (method.equals(NeighborMethods.OBJECTS.method)) {
								
			ClearCLBuffer touching_voronoi_neighbor_map = clij2.create(voronoi_image);
			clij2.touchingNeighborCountMap(voronoi_image, touching_voronoi_neighbor_map);
			
			if (excludeFinalEdgeObjects) {
				
				ClearCLBuffer no_edge_voronoi_image = clij2.create(voronoi_image);
				clij2.excludeLabelsOnEdges(voronoi_image, no_edge_voronoi_image);
				
				ClearCLBuffer no_edge_voronoi_neighbor_map = clij2.create(voronoi_image);
				clij2.mask(touching_voronoi_neighbor_map, no_edge_voronoi_image, no_edge_voronoi_neighbor_map);
				no_edge_voronoi_image.close();
				
				clij2.mask(no_edge_voronoi_neighbor_map, input_image, neighbor_count_map);
				
			} else {
				
				clij2.mask(touching_voronoi_neighbor_map, input_image, neighbor_count_map);
				
			}
			
			touching_voronoi_neighbor_map.close();
			
		} else if ((method.equals(NeighborMethods.DISTANCE.method))) {
			
			float minDistance = BV3DBoxUtilities.getMinFromRange(distanceRange);
			float maxDistance = BV3DBoxUtilities.getMaxFromRange(distanceRange);
			
			ClearCLBuffer proximal_neighbor_map = clij2.create(voronoi_image);
			clij2.proximalNeighborCountMap(input_image, proximal_neighbor_map, minDistance, maxDistance);
			
			if (excludeFinalEdgeObjects) {
				
				ClearCLBuffer no_edge_voronoi = clij2.create(voronoi_image);
				clij2.excludeLabelsOnEdges(voronoi_image, no_edge_voronoi);
				clij2.mask(proximal_neighbor_map, no_edge_voronoi, neighbor_count_map);
				
				
				no_edge_voronoi.close();
				
			} else {
				
				clij2.copy(proximal_neighbor_map, neighbor_count_map);
				
			}
			
			proximal_neighbor_map.close();
		}
		
		voronoi_image.close();
		
		
//		else if ((method.equals(NeighborMethods.CENTROIDS.method))) {
//			
//			ClearCLBuffer centroid_image = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);
//			clij2.reduceLabelsToCentroids(input_image, centroid_image);
//			clij2.extendLabelingViaVoronoi(centroid_image, voronoi_image);
//			centroid_image.close();
//			
//			ClearCLBuffer touching_neighbor_map = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);
//			clij2.touchingNeighborCountMap(voronoi_image, touching_neighbor_map);
//			clij2.mask(touching_neighbor_map, input_image, neighbor_count_map);
//			
//			voronoi_image.close();
//			touching_neighbor_map.close();
//			
//		} 
	
		return neighbor_count_map;
	}
	
	
	public double[] getNeighborCounts(ClearCLBuffer neighbor_count_map) {
		
		ResultsTable table = new ResultsTable();
		clij2.statisticsOfLabelledPixels(neighbor_count_map, connectedComponentLabels, table);
		
		return table.getColumnAsDoubles(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.value);

	}
	
	public Plot getNeighborPlotFromCountMap(ClearCLBuffer neighbor_count_map) {
		
		return getNeighborPlotFromHistogram(getNeighborCounts(neighbor_count_map));
	
	}
	
	
	public Plot getNeighborPlotFromHistogram(double[] neighborCount) {
						
		Plot plot = new Plot("NeighborCount_" + connectedComponentLabels.getName(), "Label", "Neighbor Count");
		plot.add("separate bars", neighborCount);
		
		return plot;
		
	}
	
	
	public Plot getNeighborDistribution(ClearCLBuffer neighbor_count_map) {
		
		int maxNeighborCount = (int) clij2.getMaximumOfAllPixels(neighbor_count_map);
		log.debug("maxNeighborCount = " + maxNeighborCount);
		double[] distribution = new double[maxNeighborCount + 1];
		
		double[] neighborCounts = getNeighborCounts(neighbor_count_map);
		log.debug("neighborCounts.length = " + neighborCounts.length);
		
		for (int n = 0; n < neighborCounts.length; n++) {
			log.debug("neighbor " + (n+1) + " ---> neighbor count = " + (int)neighborCounts[n]);
			distribution[(int)neighborCounts[n]] += 1;
		}
		
		distribution[0] = 0;
		
		Plot plot = new Plot("NeighborDistribution_" + connectedComponentLabels.getName(), "Neighbors", "Labels");
		plot.add("separate bars", distribution);
	
		return plot;
	}
	
	
	public enum NeighborMethods {
		OBJECTS("objects"),
//		CENTROIDS("centroids"),
		DISTANCE("distance");
		
		public final String method;
		
		NeighborMethods(String method) {
			 this.method = method;
		}
	}
	
	
	public static void main(String[] args) {
		
		Boolean plotNeighborCount = true;
		Boolean plotNeighborDistribution = true;
		
		IJ.open("D:\\Programming\\Java\\Eclipse Workspaces\\BioVoxxel 3D Box\\Test Data\\Binary Nuclei.tif");
		ImagePlus inputImagePlus = WindowManager.getCurrentImage();
		
		BV_NeighborAnalysis neighborAnalysis = new BV_NeighborAnalysis(inputImagePlus);
		
		ClearCLBuffer neighbor_image = neighborAnalysis.getNeighborCountMap(neighborAnalysis.getConnectedComponentInput(), "objects", "200-infinity", "1-infinity", false);
		
		ImagePlus neighborCountMapImp = BV3DBoxUtilities.pullImageFromGPU(neighborAnalysis.getCurrentCLIJ2Instance(), neighbor_image, false, LutNames.GEEN_FIRE_BLUE_LUT);
		neighborCountMapImp.setTitle(WindowManager.getUniqueName("NeighborCount_" + inputImagePlus.getTitle()));
		
		neighborCountMapImp.show();
		
		if (plotNeighborCount) {
			Plot plot = neighborAnalysis.getNeighborPlotFromCountMap(neighbor_image);
			plot.setStyle(0, "blue,#a0a0ff,0");
			plot.show();
		}
		
		if (plotNeighborDistribution) {
			Plot plot = neighborAnalysis.getNeighborDistribution(neighbor_image);
			plot.setStyle(0, "blue,#a0a0ff,0");
			plot.show();
		}
		
	}
	
}
