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
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.StatisticsOfLabelledPixels;
import utilities.BV3DBoxSettings;

/**
 * @author BioVoxxel
 *
 */
//@Plugin(type = Command.class, menuPath = "BV3DBox>BorderBorderDistanceMatrix")
public class BorderToBorderDistanceMatrix implements Command {

	@Parameter
	PrefService prefs;
	
	@Parameter
	LogService log;
	
	@Parameter
	ImagePlus inputImagePlus;

	
	public void run() {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "debug_level", LogLevel.INFO));
		
		CLIJ2 clij2 = CLIJ2.getInstance();
		
		log.debug(inputImagePlus.getTitle());
		
		ClearCLBuffer input_label_image = clij2.push(inputImagePlus);
		log.debug(input_label_image);
		
	
		long numberOfLabels = (long) clij2.maximumOfAllPixels(input_label_image);
		
		long[] dimensions = new long[] {numberOfLabels, numberOfLabels};
		
		ClearCLBuffer masked_single_label = clij2.create(input_label_image);
		ClearCLBuffer inverted_single_label = clij2.create(input_label_image);
		ClearCLBuffer single_distance_map = clij2.create(input_label_image);
		ClearCLBuffer distance_matrix_image = clij2.create(dimensions, NativeTypeEnum.Float);
		
		ResultsTable distance_matrix_table = new ResultsTable();
		
		for (int label = 1; label <= numberOfLabels; label++) {
				
			clij2.labelToMask(input_label_image, masked_single_label, label);
			clij2.binaryNot(masked_single_label, inverted_single_label);
			clij2.distanceMap(inverted_single_label, single_distance_map);			
			
			double[][] statistics = clij2.statisticsOfLabelledPixels(single_distance_map, input_label_image);
			
			for (int i = 0; i < statistics.length; i++) {
				log.debug("label = " + label + " / label = " + i);
				distance_matrix_table.setValue(label-1, i, statistics[i][StatisticsOfLabelledPixels.STATISTICS_ENTRY.MINIMUM_INTENSITY.value]);
			}
		}
		
		distance_matrix_table.show("Distance");
		clij2.pushResultsTable(distance_matrix_image, distance_matrix_table);
		
		input_label_image.close();
		masked_single_label.close();
		inverted_single_label.close();
		single_distance_map.close();
		
		ImagePlus distanceMatrixImagePlus = clij2.pull(distance_matrix_image);
		distanceMatrixImagePlus.show();
		
		distance_matrix_image.close();
		clij2.close();
	}
	
}
