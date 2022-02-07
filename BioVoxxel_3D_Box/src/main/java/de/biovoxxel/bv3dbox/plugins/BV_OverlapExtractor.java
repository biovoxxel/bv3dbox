/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import javax.swing.JOptionPane;

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
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
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

/**
 * @author BioVoxxel
 */

public class BV_OverlapExtractor {

	
	LogService log;
	PrefService prefs;
	
	private ImagePlus image_plus_1;
	private ImagePlus image_plus_2;
	private String volume_range = "0.0-100.0";
	private Boolean exclude_edge_objects = false;
	private Boolean show_original_primary_statistics = false;
	private Boolean show_extracted_objects = false;
	private Boolean show_count_statistics = false;
	private Boolean show_volume_statistics = false;
	private Boolean show_percent_volume_map = false;
	
	
	
	/**
	 * 
	 * @param image_plus_1
	 * @param image_plus_2
	 */
	public BV_OverlapExtractor(ImagePlus image_plus_1, ImagePlus image_plus_2) {
		this.image_plus_1 = image_plus_1;
		this.image_plus_2 = image_plus_2;
		
		this.log = new StderrLogService();
		this.prefs = new DefaultPrefService();
	}
	
	/**
	 * Exclusion size (volume for 3D and area for 2D images) for primary object labels
	 * 
	 * @param volume_range
	 */
	public void setVolumeRange(String volume_range) {
		this.volume_range = volume_range;
	}
	
	/**
	 * Defines which analyses tables and output images will be displayed
	 * 
	 * @param exclude_edge_objects
	 * @param show_original_primary_statistics
	 * @param show_extracted_objects
	 * @param show_count_statistics
	 * @param show_volume_statistics
	 * @param show_percent_volume_map
	 */
	public void setOutputFlags(boolean exclude_edge_objects, boolean show_original_primary_statistics, boolean show_extracted_objects, boolean show_count_statistics, boolean show_volume_statistics, boolean show_percent_volume_map) {
		this.exclude_edge_objects = exclude_edge_objects;
		this.show_original_primary_statistics = show_original_primary_statistics;
		this.show_extracted_objects = show_extracted_objects;
		this.show_count_statistics = show_count_statistics;
		this.show_volume_statistics = show_volume_statistics;
		this.show_percent_volume_map = show_percent_volume_map;
	}
	
	
	public void extract() {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
				
		if (image_plus_1 == image_plus_2) {
			JOptionPane.showMessageDialog(null, "Images need to be different", "Same Image", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		
		//get minimum volume limiter
		float minVolume = BV3DBoxUtilities.getMinFromRange(volume_range);
		
		//get maximum volume limiter
		float maxVolume = BV3DBoxUtilities.getMaxFromRange(volume_range);
	
		CLIJ2 clij2 = CLIJ2.getInstance();
		clij2.clear();
		
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
	
	public void run() {
		extract();
	}
}
