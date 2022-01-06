/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import javax.swing.JOptionPane;

import org.scijava.command.Command;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

import ij.IJ;
import ij.ImagePlus;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.StatisticsOfLabelledPixels;
import utilities.BV3DBoxSettings;
import utilities.BV3DBoxUtilities;
import utilities.BV3DBoxUtilities.LutNames;

/**
 * @author BioVoxxel
 *
 */

@Plugin(type = Command.class, menuPath = "BV3DBox>Overlap Extractor 2D/3D")
public class OverlapExtractor3D implements Command {

	@Parameter
	LogService log;
	
	@Parameter
	PrefService prefs;
	
	@Parameter(label = "Primary objects", description = "", persist = true)
	private ImagePlus image_plus_1;
	
	@Parameter(label = "Secondary objects", description = "", persist = true)
	private ImagePlus image_plus_2;
	
	@Parameter(label = "%-Volume range", description = "", min = "0", max = "100")
	private String volume_range = "0.0-100.0";
	
	@Parameter(label = "Exclude primary on edges", description = "")
	private boolean exclude_edge_objects = false;
	
	@Parameter(label = "Show original primary statistics", description = "")
	private boolean show_original_primary_statistics = false;
	
	@Parameter(label = "Show extracted objects", description = "")
	private boolean show_extracted_objects = false;
	
	@Parameter(label = "Show count statistics", description = "")
	private boolean show_count_statistics = false;
	
	@Parameter(label = "Show volume statistics", description = "")
	private boolean show_volume_statistics = false;
		
	@Parameter(label = "Show %-volume map", description = "")
	private boolean show_percent_volume_map = false;
	
	
	
//	/**
//	 * 
//	 */
//	public BinaryFeatureExtractor3D() {
//		
//	}
	
	public void run() {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "debug_level", LogLevel.INFO));
				
		if (image_plus_1 == image_plus_2) {
			JOptionPane.showMessageDialog(null, "Images need to be different", "Same Image", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		
		//get minimum volume limiter
		float minVolume = BV3DBoxUtilities.getMinFromRange(volume_range);
		
		//get maximum volume limiter
		float maxVolume = BV3DBoxUtilities.getMaxFromRange(volume_range);
	
		CLIJ2 clij2 = CLIJ2.getInstance();
		
		ClearCLBuffer image_1_CCL;
		if (image_plus_1.getProcessor().isBinary()) {
			ClearCLBuffer image_1_gpu = clij2.push(image_plus_1);
			image_1_CCL = clij2.create(image_1_gpu);
			clij2.connectedComponentsLabelingBox(image_1_gpu, image_1_CCL);
			image_1_gpu.close();
		} else {
			image_1_CCL = clij2.push(image_plus_1);
		}
		
		if (exclude_edge_objects) {
			ClearCLBuffer image_1_temp = clij2.create(image_1_CCL);
			clij2.copy(image_1_CCL, image_1_temp);
			clij2.excludeLabelsOnEdges(image_1_temp, image_1_CCL);
			image_1_temp.close();
		}

		ClearCLBuffer image_2_CCL;
		if (image_plus_2.getProcessor().isBinary()) {
			ClearCLBuffer image_2_gpu = clij2.push(image_plus_2);
			image_2_CCL = clij2.create(image_2_gpu);
			clij2.connectedComponentsLabelingBox(image_2_gpu, image_2_CCL);
			image_2_gpu.close();
		} else {
			image_2_CCL = clij2.push(image_plus_2);
		}
		
		
		ResultsTable original_results = new ResultsTable();
		clij2.statisticsOfLabelledPixels(image_1_CCL, image_1_CCL, original_results);
		double[] original_pixel_count = original_results.getColumnAsDoubles(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.value);
		
		if (show_original_primary_statistics) {
			original_results.show("Original Primary Statistics");			
		}
		
		ClearCLBuffer image_2_mask_on_image_1 = clij2.create(image_1_CCL);
		clij2.binaryAnd(image_1_CCL, image_2_CCL, image_2_mask_on_image_1);
			//BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, image_2_mask_on_image_1, false);
		
		ResultsTable comparison_1_and_2 = new ResultsTable();
		clij2.statisticsOfLabelledPixels(image_2_mask_on_image_1, image_1_CCL, comparison_1_and_2);
		//comparison_1_and_2.show("comparison_1_and_2");
		double[] comparison_1_2_overlap = comparison_1_and_2.getColumnAsDoubles(StatisticsOfLabelledPixels.STATISTICS_ENTRY.SUM_INTENSITY.value);
		
		int max_primary_label_count = (int) clij2.maximumOfAllPixels(image_1_CCL);
		
		log.debug("max_primary_label_count = " + max_primary_label_count);
		
		ImagePlus flag_list_image = IJ.createImage("kept_object_vector", max_primary_label_count + 1, 1, 1, 8);
		ImageProcessor flag_list_processor = flag_list_image.getProcessor();
		flag_list_processor.putPixel(0, 0, 0);
		
		ImagePlus percent_volume_image = IJ.createImage("percent_volume_image", max_primary_label_count + 1, 1, 1, 32);
		ImageProcessor percent_volume_processor = percent_volume_image.getProcessor();
		percent_volume_processor.putPixelValue(0, 0, 0.0);
		
		int kept_objects_count = 0;
		boolean[] kept_results = new boolean[max_primary_label_count];
		//kept_results[0] = false;
		double[] percent_volume = new double[max_primary_label_count];
		percent_volume[0] = 0;
		
		for (int c1 = 0; c1 < max_primary_label_count; c1++) {
			
			percent_volume[c1] = (100 / original_pixel_count[c1]) * comparison_1_2_overlap[c1];
		
			percent_volume_processor.putPixelValue(c1+1, 0, percent_volume[c1]);
			
			if (percent_volume[c1] >= minVolume && percent_volume[c1] <= maxVolume) {
				
				flag_list_processor.putPixel(c1+1, 0, 0);	//keep label
				kept_objects_count++;
				kept_results[c1] = true;
				
			} else {
				
				flag_list_processor.putPixel(c1+1, 0, 1);	//remove label
				kept_results[c1] = false;
			}
		}
		
				
		if (show_percent_volume_map) {
			ClearCLBuffer percent_volume_map = clij2.create(image_1_CCL);
			ClearCLBuffer percent_volume_vector = clij2.push(percent_volume_image);
			percent_volume_map.setName("%volume_" + image_plus_2.getTitle());
			clij2.generateParametricImage(percent_volume_vector, image_1_CCL, percent_volume_map);
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, percent_volume_map, true, LutNames.GEEN_FIRE_BLUE_LUT);
			percent_volume_map.close();
			percent_volume_vector.close();
		}
		
		if (show_extracted_objects) {
			ClearCLBuffer flag_list_vector = clij2.push(flag_list_image);
			ClearCLBuffer kept_image_1_CCL = clij2.create(image_1_CCL);
			kept_image_1_CCL.setName("extracted_" + image_plus_1.getTitle());
			clij2.excludeLabels(flag_list_vector, image_1_CCL, kept_image_1_CCL);		
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, kept_image_1_CCL, true, LutNames.GEEN_FIRE_BLUE_LUT);
			flag_list_vector.close();
			kept_image_1_CCL.close();
			
			//TODO: get final labels for extracted in relation to initial labels and add to full analysis table
		}
		
		if (show_count_statistics) {
			ResultsTable count_statistics_table = ResultsTable.getResultsTable("OE3D_Count");
			if (count_statistics_table == null) {
				count_statistics_table = new ResultsTable();
			} 
			count_statistics_table.addRow();
			count_statistics_table.addValue("IMAGE_NAME", image_plus_1.getTitle());
			count_statistics_table.addValue("ORIGINAL_COUNT", max_primary_label_count);
			count_statistics_table.addValue("SELECTOR_COUNT", clij2.maximumOfAllPixels(image_2_CCL));
			count_statistics_table.addValue("EXTRACTED_COUNT", kept_objects_count);
			count_statistics_table.show("OE3D_Count");
		}
		
		if (show_volume_statistics) {
			
			ResultsTable full_statistics_table = ResultsTable.getResultsTable("OE3D_Statistics");
			log.debug("full_statistics_table = " + full_statistics_table);
			if (full_statistics_table == null) {
				full_statistics_table = new ResultsTable();
			} 
			
			int starting_row = full_statistics_table.size();
			log.debug("starting_row = " + starting_row);
			double[] label_id =  original_results.getColumnAsDoubles(StatisticsOfLabelledPixels.STATISTICS_ENTRY.IDENTIFIER.value);
			log.debug("label_id length = " + label_id.length);
			int offset = 0;
			for (int row = starting_row; row < max_primary_label_count + starting_row; row++) {
				log.debug("processing row = " + row + " / starting row = " + starting_row);
				int index = row - starting_row;
				log.debug("index = " + index);
				if (kept_results[index]) {
					log.debug("row - offset = " + (row - offset));
					full_statistics_table.setValue("IDENTIFIER", row - offset, (row - offset + 1));
					full_statistics_table.setValue("ORIGINAL_LABEL_ID", row - offset, label_id[index]);
					full_statistics_table.setValue("ORIGINAL_VOXELS", row - offset, original_pixel_count[index]);
					full_statistics_table.setValue("SELECTOR_VOXELS", row - offset, comparison_1_2_overlap[index]);
					full_statistics_table.setValue("PERCENT_VOLUME", row - offset, percent_volume[index]);					
				} else {
					 offset++;
					 log.debug("offset = " + offset);
				}
			}
			
			full_statistics_table.show("OE3D_Statistics");
		}
		
		image_1_CCL.close();
		image_2_CCL.close();
		comparison_1_2_overlap = null;
		
		clij2.clear();
		
	}
}
