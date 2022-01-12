/**
 * 
 */
package test.plugins;

import org.scijava.command.Command;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

import ij.ImagePlus;
import ij.measure.ResultsTable;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.StatisticsOfLabelledPixels;
import utilities.BV3DBoxSettings;

/**
 * @author BioVoxxel
 *
 */
@Plugin(type = Command.class, menuPath = "BV3DBox>BorderBorderDistance")
public class BorderToBorderDistance implements Command {

	@Parameter
	PrefService prefs;
	
	@Parameter
	LogService log;
	
	@Parameter
	ImagePlus inputImagePlus;

	
	public void run() {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "debug_level", LogLevel.INFO));
		
		CLIJ2 clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		log.debug(inputImagePlus.getTitle());
		
		ClearCLBuffer input_label_image = clij2.push(inputImagePlus);
		log.debug(input_label_image);
		
	
		ClearCLBuffer masked_background_label = clij2.create(input_label_image);
		ClearCLBuffer distance_map = clij2.create(input_label_image);
		ClearCLBuffer voronoi_labels = clij2.create(input_label_image);
		ClearCLBuffer label_edges = clij2.create(input_label_image);
		
		clij2.labelToMask(input_label_image, masked_background_label, 0);
				
		clij2.distanceMap(masked_background_label, distance_map);	
		
		clij2.voronoiLabeling(input_label_image, voronoi_labels);
		clij2.reduceLabelsToLabelEdges(voronoi_labels, label_edges);
				
		double[][] distanceStatistics = clij2.statisticsOfLabelledPixels(distance_map, label_edges);
		
		
		ResultsTable borderDistanceTable = new ResultsTable();
		for (int i = 0; i < distanceStatistics.length; i++) {
			double min_border_distance =  distanceStatistics[i][StatisticsOfLabelledPixels.STATISTICS_ENTRY.MINIMUM_INTENSITY.value] * 2;
			double max_border_distance = distanceStatistics[i][StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAXIMUM_INTENSITY.value] * 2;
			double mean_border_distance = distanceStatistics[i][StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.value] * 2;
			double std_border_distance =  distanceStatistics[i][StatisticsOfLabelledPixels.STATISTICS_ENTRY.STANDARD_DEVIATION_INTENSITY.value] * 2;
			
			
			borderDistanceTable.setValue("IDENTIFIER", i, distanceStatistics[i][StatisticsOfLabelledPixels.STATISTICS_ENTRY.IDENTIFIER.value]);
			borderDistanceTable.setValue("MIN_BORDER_DIST", i, min_border_distance);
			borderDistanceTable.setValue("MAX_BORDER_DIST", i, max_border_distance);
			borderDistanceTable.setValue("MEAN_BORDER_DIST", i, mean_border_distance);
			borderDistanceTable.setValue("STD_BORDER_DIST", i, std_border_distance);
			borderDistanceTable.setValue("MIN_MAX_BORDER_DISTANCE", i, min_border_distance / max_border_distance);
			borderDistanceTable.setValue("MEAN_MAX_BORDER_DISTANCE", i, mean_border_distance / max_border_distance);
			borderDistanceTable.setValue("MAX-MEAN / MEAN-MIN", i, (max_border_distance-mean_border_distance) / (mean_border_distance-min_border_distance));
			borderDistanceTable.setValue("BORDER_DISTANCE_COV", i, std_border_distance / mean_border_distance);
			
		}
		
		borderDistanceTable.show("BorderDistance");
		
		clij2.close();
	}
		
}
