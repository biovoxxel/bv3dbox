package de.biovoxxel.bv3dbox.plugins;

import java.util.Iterator;

import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;
import org.scijava.prefs.DefaultPrefService;
import org.scijava.prefs.PrefService;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.gui.Plot;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.StatisticsOfLabelledPixels;

public class BV_NeighborAnalysis {

	private LogService log = new StderrLogService();
	private PrefService prefs = new DefaultPrefService();
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
			connectedComponentLabels = clij2.create(temp_input_image);
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
	public ClearCLBuffer getNeighborCountMap(ClearCLBuffer input_image, String method, String distanceRange) {
		
		ClearCLBuffer voronoi_image = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);	
		ClearCLBuffer neighbor_count_map = clij2.create(voronoi_image);
		neighbor_count_map.setName("NCM_" + input_image.getName());
		
		if (method.equals(NeighborMethods.OBJECTS.method)) {
			
			clij2.extendLabelingViaVoronoi(input_image, voronoi_image);
					
			ClearCLBuffer touching_neighbor_map = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);
			clij2.touchingNeighborCountMap(voronoi_image, touching_neighbor_map);
			clij2.mask(touching_neighbor_map, input_image, neighbor_count_map);
			
			voronoi_image.close();
			touching_neighbor_map.close();
			
		} else if ((method.equals(NeighborMethods.DISTANCE.method))) {
			
			float minDistance = BV3DBoxUtilities.getMinFromRange(distanceRange);
			float maxDistance = BV3DBoxUtilities.getMaxFromRange(distanceRange);
			
			clij2.proximalNeighborCountMap(input_image, neighbor_count_map, minDistance, maxDistance);
			
		}
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
		System.out.println("maxNeighborCount = " + maxNeighborCount);
		double[] distribution = new double[maxNeighborCount + 1];
		
		double[] neighborCounts = getNeighborCounts(neighbor_count_map);
		System.out.println("neighborCounts.length = " + neighborCounts.length);
		
		for (int n = 0; n < neighborCounts.length; n++) {
			System.out.println((int)neighborCounts[n]);
			distribution[(int)neighborCounts[n]] += 1;
		}
		
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
	
}
