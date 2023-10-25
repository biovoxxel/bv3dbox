package de.biovoxxel.bv3dbox.plugins;

import javax.swing.JOptionPane;

import org.scijava.Cancelable;
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
import ij.ImageStack;
import ij.WindowManager;
import ij.macro.Variable;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
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

/**
 * 
 * @author BioVoxxel
 *
 */
public class BV_ObjectInspector implements Cancelable {
	
	
	PrefService prefs = new DefaultPrefService();

	LogService log = new StderrLogService();

	private ImagePlus primary_ImagePlus;
	private ImagePlus secondary_ImagePlus;
	private String original_1_title;
	private String original_2_title;
	private String primary_volume_range = "0-Infinity";
	private String primary_MMER_range = "0.00-1.00";
	private String secondary_volume_range = "0-Infinity";
	private String secondary_MMER_range = "0.00-1.00";
	private Boolean exclude_primary_objects_on_edges = true;
	private Boolean pad_stack_tops = false;
	private Boolean display_results_tables = true;
	private Boolean display_analyzed_label_maps = false;
	private Boolean show_count_map = false;
	
	CLIJ2 clij2;
	
	ClearCLBuffer labels_1_gpu = null;
	ClearCLBuffer labels_2_gpu = null;
	ClearCLBuffer original_1_gpu = null;
	ClearCLBuffer original_2_gpu = null;
	
	String PRIMARY_RESULTS_TABLE_NAME = "Primary_Results";
	String SECONDARY_RESULTS_TABLE_NAME = "Secondary_Results";
	
	ResultsTable final_primary_results_table = new ResultsTable();
	ResultsTable final_secondary_results_table = new ResultsTable();
	
	String GLASBEY_LUT = "glasbey_on_dark";
	String GEEN_FIRE_BLUE_LUT = "Green Fire Blue";
	String FIRE_LUT = "Fire";
	
	/**
	 * 
	 * @param primary_ImagePlus
	 * @param secondary_ImagePlus
	 */
	public BV_ObjectInspector(ImagePlus primary_ImagePlus, ImagePlus secondary_ImagePlus) {
		
		this.primary_ImagePlus = primary_ImagePlus;
		this.secondary_ImagePlus = secondary_ImagePlus;
	}
	
	/**
	 * Original images (one or both, can also be the same) need to be set if the analysis should also read out pixel intensity-based data. 
	 *  
	 * @param original_1_title
	 * @param original_2_title
	 */
	public void setOriginalImages(String original_1_title, String original_2_title) {
		this.original_1_title = original_1_title;
		this.original_2_title = original_2_title;
	}
	
	/**
	 * Exclusion size for primary object labels
	 * 
	 * @param primary_volume_range
	 */
	public void setPrimaryVolumeRange(String primary_volume_range) {
		this.primary_volume_range = primary_volume_range;
	}
	
	/**
	 * Exclusion of primary object labels based on their min-to-max-extension ratio.
	 * This is the ratio of the smallest distance from the centroid to the objects' border
	 * devided by the maximum distance from centroid to object border.
	 * This way shape can be used as inclusion/exclusion criterion.
	 * 
	 * This is still experimental and might change e.g. to mean-to-max-extension ratio.
	 * 
	 * @param primary_MMER_range
	 */
	public void setPrimaryMMDTCRRange(String primary_MMER_range) {
		this.primary_MMER_range = primary_MMER_range;
	}
	
	/**
	 * Exclusion size for secondary object labels
	 * @param secondary_volume_range
	 */
	public void setSecondaryVolumeRange(String secondary_volume_range) {
		this.secondary_volume_range = secondary_volume_range;
	}
	
	/**
	 * Exclusion of secondary object labels based on their min-to-max-extension ratio.
	 * This is the ratio of the smallest distance from the centroid to the objects' border
	 * devided by the maximum distance from centroid to object border.
	 * This way shape can be used as inclusion/exclusion criterion.
	 * 
	 * This is still experimental and might change e.g. to mean-to-max-extension ratio.
	 * 
	 * @param secondary_MMER_range
	 */
	public void setSecondaryMMDTCRRange(String secondary_MMER_range) {
		this.secondary_MMER_range = secondary_MMER_range;
	}
	
	/**
	 * Exclude image border touching object labels
	 * 
	 * @param exclude_primary_objects_on_edges
	 */
	public void setEdgeExclusion(boolean exclude_primary_objects_on_edges) {
		this.exclude_primary_objects_on_edges = exclude_primary_objects_on_edges;
	}
	
	/**
	 * Adds a black slice before the first and after the last stack slice.
	 * This way objects still visible in the first or last slice will not be excluded by the exclude on edge function.
	 * This however introduces a certain bias and error in any analysis and should be used with care or only in test cases.
	 * 
	 * @param pad_stack_tops
	 */
	public void padStackTops(boolean pad_stack_tops) {
		this.pad_stack_tops = pad_stack_tops;
	}
	
	/**
	 * Defines which output images will be displayed
	 * 
	 * @param display_analyzed_label_maps
	 * @param show_count_map
	 */
	public void setOutputImageFlags(boolean display_results_tables, boolean display_analyzed_label_maps, boolean show_count_map) {
		this.display_results_tables = display_results_tables;
		this.display_analyzed_label_maps = display_analyzed_label_maps;
		this.show_count_map = show_count_map;
	}
	
		
	public void inspect() {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		log.debug("------------------------------------------------------");
		log.debug("labels_1_ImagePlus = " + primary_ImagePlus);
		log.debug("labels_2_ImagePlus = " + secondary_ImagePlus);
		log.debug("original_1_title = " + original_1_title);
		log.debug("original_2_title = " + original_2_title);
		log.debug("primary_volume_range = " + primary_volume_range);
		log.debug("primary_MMDTCR_range = " + primary_MMER_range);
		log.debug("secondary_volume_range = " + secondary_volume_range);
		log.debug("secondary_MMDTCR_range = " + secondary_MMER_range);
		log.debug("exclude_primary_objects_on_edges = " + exclude_primary_objects_on_edges);
		log.debug("pad_stack_tops = " + pad_stack_tops);
		log.debug("display_results_tables = " + display_results_tables);
		log.debug("display_analyzed_label_maps = " + display_analyzed_label_maps);
		log.debug("show_count_map = " + show_count_map);
		log.debug("------------------------------------------------------");
		
		
		clij2.clear();

		if (primary_ImagePlus == secondary_ImagePlus) {
			cancel("Primary and secondary label image need to be different");
			return;
		}
		
		if (primary_ImagePlus.getNDimensions() > 3 || secondary_ImagePlus.getNDimensions() > 3) {
			cancel("Does not work on hyperstacks");
		}
		
		int[] dimensions_label_image_1 = primary_ImagePlus.getDimensions();
		int[] dimensions_label_image_2 = secondary_ImagePlus.getDimensions();
		
		for (int dim = 0; dim < dimensions_label_image_1.length; dim++) {
			if (dimensions_label_image_1[dim] != dimensions_label_image_2[dim]) {
				cancel("Image dimensions between primary and secondary image do not match");
			}
		}
		
		if (primary_ImagePlus.getNDimensions() > 3 || secondary_ImagePlus.getNDimensions() > 3) {
			cancel("Does not work on hyperstacks");
		}
		
		ImagePlus original_1_ImagePlus = WindowManager.getImage(original_1_title);
		ImagePlus original_2_ImagePlus = WindowManager.getImage(original_2_title);

		if (original_1_ImagePlus != null) {
			
			if (original_1_ImagePlus.getNDimensions() > 3) {
				cancel("Does not work on hyperstacks");
			}
			
			int[] dimensions_original_1 = original_1_ImagePlus.getDimensions();
			
			for (int dim = 0; dim < dimensions_label_image_1.length; dim++) {
				if (dimensions_original_1[dim] != dimensions_label_image_1[dim]) {
					cancel("Image dimensions of " + original_1_ImagePlus.getTitle() + " do not match");
				}
			}
			
		}
		
		if (original_2_ImagePlus != null) {
			
			if (original_2_ImagePlus.getNDimensions() > 3) {
				cancel("Does not work on hyperstacks");
			}
			
			int[] dimensions_original_2 = original_2_ImagePlus.getDimensions();
			
			for (int dim = 0; dim < dimensions_label_image_1.length; dim++) {
				if (dimensions_original_2[dim] != dimensions_label_image_1[dim]) {
					cancel("Image dimensions of " + original_2_ImagePlus.getTitle() + " do not match");
				}
			}
			
		}
		
		
		Calibration voxel_calibration = primary_ImagePlus.getCalibration();
		String calibrated_units = voxel_calibration.getUnit();
		if (!calibrated_units.matches(".*ixel.*") && !calibrated_units.matches(".*oxel.*") && original_1_ImagePlus != null) {
			voxel_calibration = original_1_ImagePlus.getCalibration();
			calibrated_units = voxel_calibration.getUnit();
		}
		double voxel_width = voxel_calibration.pixelWidth;
		double voxel_height = voxel_calibration.pixelHeight;
		double voxel_depth = voxel_calibration.pixelDepth;
		
		double voxel_volume = voxel_width * voxel_height * voxel_depth;
		
	
		if (exclude_primary_objects_on_edges && pad_stack_tops) {
			padStackLids(primary_ImagePlus);
		}
		
		
		
		
		if (primary_ImagePlus.getProcessor().isBinary()) {
			
			log.debug("Start convert " + primary_ImagePlus.getTitle() + " to connected components");
			ClearCLBuffer binaryInput_1 = clij2.push(primary_ImagePlus);
			labels_1_gpu = clij2.create(binaryInput_1.getDimensions(), NativeTypeEnum.Float);
			clij2.connectedComponentsLabelingBox(binaryInput_1, labels_1_gpu);
			binaryInput_1.close();
			log.debug("End convert " + primary_ImagePlus.getTitle() + " to connected components");
			
		} else if (primary_ImagePlus.getBitDepth() != 24) {
			
			labels_1_gpu = clij2.push(primary_ImagePlus);
			log.debug("Pushed to GPU = " + labels_1_gpu);
			
		} else {
			
			JOptionPane.showMessageDialog(null, "Wrong input image format\nNeeds to be of type gray-scale label mask or 8-bit binary", "Wrong image type", JOptionPane.WARNING_MESSAGE);
			return;
			
		}
		labels_1_gpu.setName("gpu_" + primary_ImagePlus.getTitle());
		
		
		
				
		if (secondary_ImagePlus.getProcessor().isBinary()) {
			
			log.debug("Start convert " + secondary_ImagePlus.getTitle() + " to connected components");
			ClearCLBuffer binaryInput_2 = clij2.push(secondary_ImagePlus);
			labels_2_gpu = clij2.create(binaryInput_2.getDimensions(), NativeTypeEnum.Float);
			clij2.connectedComponentsLabelingBox(binaryInput_2, labels_2_gpu);
			binaryInput_2.close();
			log.debug("End convert " + secondary_ImagePlus.getTitle() + " to connected components");
			
		} else if (secondary_ImagePlus.getBitDepth() != 24) {
			
			labels_2_gpu = clij2.push(secondary_ImagePlus);
			log.debug("Pushed to GPU = " + labels_2_gpu);
			
		} else {
			
			JOptionPane.showMessageDialog(null, "Wrong input image format\nNeeds to be of type gray-scale label mask or 8-bit binary", "Wrong image type", JOptionPane.WARNING_MESSAGE);
			return;
			
		}
		
		labels_2_gpu.setName("gpu_" + secondary_ImagePlus.getTitle());
		
		
		
		
		if (original_1_ImagePlus != null) {
			original_1_gpu = clij2.push(original_1_ImagePlus);
			original_1_gpu.setName("gpu_" + original_1_ImagePlus.getTitle());
			
			log.debug("Pushed to GPU = " + original_1_ImagePlus);
		}
	
		
		
		if (original_2_ImagePlus != null) {
			original_2_gpu = clij2.push(original_2_ImagePlus);
			original_2_gpu.setName("gpu_" + original_2_ImagePlus.getTitle());
			
			log.debug("Pushed to GPU = " + original_2_ImagePlus);
		}
		
		
		if (exclude_primary_objects_on_edges) {
						
			ClearCLBuffer temp_input_to_exclude_edge_objects = clij2.create(labels_1_gpu);
			clij2.copy(labels_1_gpu, temp_input_to_exclude_edge_objects);
			
			if (pad_stack_tops) {
				ClearCLBuffer temp_output_to_exclude_edge_objects = clij2.create(labels_1_gpu);
				labels_1_gpu.close();
				
				long width = temp_input_to_exclude_edge_objects.getWidth();
				long height = temp_input_to_exclude_edge_objects.getHeight();
				long depth = temp_input_to_exclude_edge_objects.getDepth() - 2;
				
				clij2.excludeLabelsOnEdges(temp_input_to_exclude_edge_objects, temp_output_to_exclude_edge_objects);
				
				labels_1_gpu = clij2.create(width, height, depth);
				removePaddedSlices(temp_output_to_exclude_edge_objects, labels_1_gpu);
				temp_output_to_exclude_edge_objects.close();
				
			} else {
				clij2.excludeLabelsOnEdges(temp_input_to_exclude_edge_objects, labels_1_gpu);
			}
			
			temp_input_to_exclude_edge_objects.close();
		}
		
		
		
		
		//exclude primary labels according to input limiters
		ClearCLBuffer finalLabels_1 = clij2.create(labels_1_gpu);
		finalLabels_1.setName("final_" + primary_ImagePlus.getTitle());
		
		
		ResultsTable final_edge_analysis_table_1 = new ResultsTable();
		
		//TODO: test if min_max- or mean_max
		labelExclusion(labels_1_gpu, primary_volume_range, primary_MMER_range, final_edge_analysis_table_1, finalLabels_1);

		labels_1_gpu.close();
		
		
		
		//Masking secondary labels with primary labels
		ClearCLBuffer tempMaskedLabels_2 = clij2.create(labels_2_gpu);
		log.debug("Secondary label number before masking = " + clij2.maximumOfAllPixels(labels_2_gpu));
		
		ClearCLBuffer maskedLabels_2 = clij2.create(labels_2_gpu);
		maskedLabels_2.setName("masked_" + labels_2_gpu.getName());
		
		clij2.mask(labels_2_gpu, finalLabels_1, tempMaskedLabels_2);
		clij2.closeIndexGapsInLabelMap(tempMaskedLabels_2, maskedLabels_2);
		
		tempMaskedLabels_2.close();
		labels_2_gpu.close();

		log.debug(labels_2_gpu + "masked with " + finalLabels_1 + " with output as " + maskedLabels_2);
				
		
		
		//exclude secondary labels according to input limiters
		ClearCLBuffer finalLabels_2 = clij2.create(maskedLabels_2);
		finalLabels_2.setName("final_" + secondary_ImagePlus.getTitle());
		
		ResultsTable final_edge_analysis_table_2 = new ResultsTable();
		
		//TODO: test if min_max- or mean_max
		labelExclusion(maskedLabels_2, secondary_volume_range, secondary_MMER_range, final_edge_analysis_table_2, finalLabels_2);

		maskedLabels_2.close();
				
		//create overlap count mask		
		ClearCLBuffer overlapCountMap = clij2.create(finalLabels_1);
		overlapCountMap.setName("CountMap_" + primary_ImagePlus.getTitle());
		boolean label_overlap_count_map_created = clij2.labelOverlapCountMap(finalLabels_1, finalLabels_2, overlapCountMap);
		log.debug("LabelOverlapCountMap finished = " + label_overlap_count_map_created);
		
			
		ResultsTable primary_original_measurements_table = new ResultsTable();
		
						
		if (original_1_gpu == null) {
			
			clij2.statisticsOfLabelledPixels(finalLabels_1, finalLabels_1, primary_original_measurements_table);
	
		} else {
			
			clij2.statisticsOfLabelledPixels(original_1_gpu, finalLabels_1, primary_original_measurements_table);

		}
		
//		read primary identifier add to final table
		Variable[] primary_label_identifier = primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.IDENTIFIER.name());
		final_primary_results_table.setColumn("PRIM_OBJ_ID", primary_label_identifier);
		
		
//		get secondary object count and display count map if desired
		ResultsTable overlapCountTable = new ResultsTable();
		clij2.statisticsOfLabelledPixels(overlapCountMap, finalLabels_1, overlapCountTable);
		Variable[] secondaryObjectCountArray = overlapCountTable.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name());
//		secondaryObjectCountArray[0] = new Variable("Background");
		final_primary_results_table.setColumn("SEC_OBJECT_COUNT", secondaryObjectCountArray);
		
		if (show_count_map) {
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, overlapCountMap, true, LutNames.GEEN_FIRE_BLUE_LUT, voxel_calibration);	//test output			
		}
		overlapCountMap.close();
		
		
		//Calculate primary scaled volumes
		double[] primary_volume_in_pixels = primary_original_measurements_table.getColumnAsDoubles(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.value);
		
		Variable[] primary_volume_in_units = new Variable[primary_volume_in_pixels.length];
		for (int cal = 0; cal < primary_volume_in_pixels.length; cal++) {
			primary_volume_in_units[cal] = new Variable(primary_volume_in_pixels[cal] * voxel_volume);
		}
		final_primary_results_table.setColumn("VOLUME ("+calibrated_units+"^3)", primary_volume_in_units);
		
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.name()));
		
		if (original_1_gpu != null) {
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name()));
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAXIMUM_INTENSITY.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAXIMUM_INTENSITY.name()));
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MINIMUM_INTENSITY.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MINIMUM_INTENSITY.name()));
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.STANDARD_DEVIATION_INTENSITY.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.STANDARD_DEVIATION_INTENSITY.name()));
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.SUM_INTENSITY.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.SUM_INTENSITY.name()));
			
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_X.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_X.name()));
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_Y.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_Y.name()));
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_Z.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_Z.name()));
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_DISTANCE_TO_MASS_CENTER.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_DISTANCE_TO_MASS_CENTER.name()));
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_DISTANCE_TO_MASS_CENTER.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_DISTANCE_TO_MASS_CENTER.name()));
			final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_MEAN_DISTANCE_TO_MASS_CENTER_RATIO.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_MEAN_DISTANCE_TO_MASS_CENTER_RATIO.name()));
					
		} else {
			//skip intensity based measurements if original input image not available
		}
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_X.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_X.name()));
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_Y.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_Y.name()));
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_Z.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_Z.name()));
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_DISTANCE_TO_CENTROID.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_DISTANCE_TO_CENTROID.name()));
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_DISTANCE_TO_CENTROID.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_DISTANCE_TO_CENTROID.name()));
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_MEAN_DISTANCE_TO_CENTROID_RATIO.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_MEAN_DISTANCE_TO_CENTROID_RATIO.name()));
		
		//final_edge_analysis_table_1.show("final_edge_analysis_table_1");
	
		final_primary_results_table.setColumn("MIN_MAX_EXTENSION_RATIO", final_edge_analysis_table_1.getColumnAsVariables("MIN_MAX_EXTENSION_RATIO"));
		final_primary_results_table.setColumn("MEAN_MAX_EXTENSION_RATIO", final_edge_analysis_table_1.getColumnAsVariables("MEAN_MAX_EXTENSION_RATIO"));
		final_primary_results_table.setColumn("MIN_EXTENSION", final_edge_analysis_table_1.getColumnAsVariables("MIN_EXTENSION"));
		final_primary_results_table.setColumn("MAX_EXTENSION", final_edge_analysis_table_1.getColumnAsVariables("MAX_EXTENSION"));
		final_primary_results_table.setColumn("MEAN_EXTENSION", final_edge_analysis_table_1.getColumnAsVariables("MEAN_EXTENSION"));
		final_primary_results_table.setColumn("STD_DEV_EXTENSION", final_edge_analysis_table_1.getColumnAsVariables("STD_DEV_EXTENSION"));
		
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_WIDTH.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_WIDTH.name()));
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_HEIGHT.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_HEIGHT.name()));
		final_primary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_DEPTH.name(), primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_DEPTH.name()));
		
		
		Variable[] primary_bounding_box_width = primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_WIDTH.name());
		Variable[] primary_bounding_box_height = primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_HEIGHT.name());
		Variable[] primary_bounding_box_depth = primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_DEPTH.name());
		Variable[] primary_bounding_box_extent = new Variable[primary_bounding_box_width.length];
		for (int e = 0; e < primary_bounding_box_extent.length; e++) {
			primary_bounding_box_extent[e] = new Variable( (primary_volume_in_pixels[e] / (primary_bounding_box_width[e].getValue() * primary_bounding_box_height[e].getValue() * primary_bounding_box_depth[e].getValue())) );
		}
		final_primary_results_table.setColumn("BOUNDING_BOX_EXTENT", primary_bounding_box_extent);
		
		primary_original_measurements_table = null;

//TODO: separate output for overall statistics 
//		double primary_image_volume = (double) labels_1_gpu.getVolume();
//		double total_pixel_count_of_all_primary_objects =  primary_image_volume - primary_volume_in_pixels[0]; //image volume - background volume
//		log.info("total_pixel_count_of_all_primary_objects = " + total_pixel_count_of_all_primary_objects);
//		double volume_fraction_of_primary_objects = primary_image_volume /  total_pixel_count_of_all_primary_objects;
//		log.info("volume_fraction_of_primary_objects = " + volume_fraction_of_primary_objects);
	
		
		//calculate secondary distances
		ClearCLBuffer center_distance_map = clij2.create(finalLabels_1);
		center_distance_map.setName("centroid_dist_" + finalLabels_1.getName());
		clij2.euclideanDistanceFromLabelCentroidMap(finalLabels_1, center_distance_map);
		log.debug("EuclideanDistanceFromLabelCentroidMap created");
		
		ClearCLBuffer border_distance_map = clij2.create(finalLabels_1);
		border_distance_map.setName("border_dist_" + finalLabels_1.getName());
		clij2.distanceMap(finalLabels_1, border_distance_map);
		log.debug("MaximumExtensionMap created");

		
		double max_primary_label_count = clij2.maximumOfAllPixels(finalLabels_1);
		log.debug("max_primary_label_count = " + max_primary_label_count);
		
		ResultsTable secondary_original_measurements_table = new ResultsTable();
		ResultsTable center_distance_table = new ResultsTable();
		ResultsTable border_distance_table = new ResultsTable();
		
		ResultsTable primary_label_origin_of_secondary_label_table = new ResultsTable();
		clij2.statisticsOfLabelledPixels(finalLabels_1, finalLabels_2, primary_label_origin_of_secondary_label_table);
					
		if (original_2_gpu == null) {
			
			clij2.statisticsOfLabelledPixels(finalLabels_2, finalLabels_2, secondary_original_measurements_table);
			
		} else {
			
			clij2.statisticsOfLabelledPixels(original_2_gpu, finalLabels_2, secondary_original_measurements_table);
			
		}
		clij2.statisticsOfLabelledPixels(center_distance_map, finalLabels_2, center_distance_table);
		center_distance_map.close();
		clij2.statisticsOfLabelledPixels(border_distance_map, finalLabels_2, border_distance_table);
		border_distance_map.close();
		
		
		if (display_analyzed_label_maps) {
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, finalLabels_1, true, LutNames.GLASBEY_LUT, voxel_calibration);
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, finalLabels_2, true, LutNames.GLASBEY_LUT, voxel_calibration);
		}
		finalLabels_1.close();
		finalLabels_2.close();
		
		
		if (primary_label_origin_of_secondary_label_table.size() > 0) {
			
			Variable[] primary_label_origin_of_secondary_label = primary_label_origin_of_secondary_label_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name());
			//primary_label_origin_of_secondary_label[0] = new Variable("Background");
			final_secondary_results_table.setColumn("PRIMARY_LABEL", primary_label_origin_of_secondary_label);
			primary_label_origin_of_secondary_label_table = null;
		}
		
		if (secondary_original_measurements_table.size() > 0) {
			
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.IDENTIFIER.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.IDENTIFIER.name()));
			
			//Calculate primary scaled volumes
			double[] secondary_volume_in_pixels = secondary_original_measurements_table.getColumnAsDoubles(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.value);
			Variable[] secondary_volume_in_units = new Variable[secondary_volume_in_pixels.length];
			for (int cal2 = 0; cal2 < secondary_volume_in_pixels.length; cal2++) {
				secondary_volume_in_units[cal2] = new Variable(secondary_volume_in_pixels[cal2] * voxel_volume);
			}
			final_secondary_results_table.setColumn("VOLUME ("+calibrated_units+"^3)", secondary_volume_in_units);
			
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.name()));
			
			if (original_2_gpu != null) {
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name()));
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAXIMUM_INTENSITY.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAXIMUM_INTENSITY.name()));
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MINIMUM_INTENSITY.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MINIMUM_INTENSITY.name()));
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.STANDARD_DEVIATION_INTENSITY.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.STANDARD_DEVIATION_INTENSITY.name()));
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.SUM_INTENSITY.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.SUM_INTENSITY.name()));
				
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_X.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_X.name()));
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_Y.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_Y.name()));
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_Z.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MASS_CENTER_Z.name()));
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_DISTANCE_TO_MASS_CENTER.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_DISTANCE_TO_MASS_CENTER.name()));
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_DISTANCE_TO_MASS_CENTER.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_DISTANCE_TO_MASS_CENTER.name()));
				final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_MEAN_DISTANCE_TO_MASS_CENTER_RATIO.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_MEAN_DISTANCE_TO_MASS_CENTER_RATIO.name()));
				
			} else {
				//skip intensity based measurements if original input image not available
			}
			
			//final_edge_analysis_table_2.show("final_edge_analysis_table_2");
			
			final_secondary_results_table.setColumn("MIN_MAX_EXTENSION_RATIO", final_edge_analysis_table_2.getColumnAsVariables("MIN_MAX_EXTENSION_RATIO"));
			final_secondary_results_table.setColumn("MEAN_MAX_EXTENSION_RATIO", final_edge_analysis_table_2.getColumnAsVariables("MEAN_MAX_EXTENSION_RATIO"));
			final_secondary_results_table.setColumn("MIN_EXTENSION", final_edge_analysis_table_2.getColumnAsVariables("MIN_EXTENSION"));
			final_secondary_results_table.setColumn("MAX_EXTENSION", final_edge_analysis_table_2.getColumnAsVariables("MAX_EXTENSION"));
			final_secondary_results_table.setColumn("MEAN_EXTENSION", final_edge_analysis_table_2.getColumnAsVariables("MEAN_EXTENSION"));
			final_secondary_results_table.setColumn("STD_DEV_EXTENSION", final_edge_analysis_table_2.getColumnAsVariables("STD_DEV_EXTENSION"));
			
			
			final_secondary_results_table.setColumn("AVER_BORDER_DIST", center_distance_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name()));
			final_secondary_results_table.setColumn("SHORT_BORDER_DIST", center_distance_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MINIMUM_INTENSITY.name()));
			final_secondary_results_table.setColumn("LONG_BORDER_DIST", center_distance_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAXIMUM_INTENSITY.name()));
			
			final_secondary_results_table.setColumn("AVER_CENTER_DIST", border_distance_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name()));
			final_secondary_results_table.setColumn("SHORT_CENTER_DIST", border_distance_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MINIMUM_INTENSITY.name()));
			final_secondary_results_table.setColumn("LONG_CENTER_DIST", border_distance_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAXIMUM_INTENSITY.name()));
			
			center_distance_table = null;
			border_distance_table = null;
			
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_X.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_X.name()));
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_Y.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_Y.name()));
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_Z.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.CENTROID_Z.name()));
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_DISTANCE_TO_CENTROID.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_DISTANCE_TO_CENTROID.name()));
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_DISTANCE_TO_CENTROID.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_DISTANCE_TO_CENTROID.name()));
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_MEAN_DISTANCE_TO_CENTROID_RATIO.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_MEAN_DISTANCE_TO_CENTROID_RATIO.name()));
			
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_WIDTH.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_WIDTH.name()));
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_HEIGHT.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_HEIGHT.name()));
			final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_DEPTH.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_DEPTH.name()));
			
			
			Variable[] secondary_bounding_box_width = secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_WIDTH.name());
			Variable[] secondary_bounding_box_height = secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_HEIGHT.name());
			Variable[] secondary_bounding_box_depth = secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.BOUNDING_BOX_DEPTH.name());
			Variable[] secondary_bounding_box_extent = new Variable[secondary_bounding_box_width.length];
			for (int e = 0; e < secondary_bounding_box_extent.length; e++) {
				secondary_bounding_box_extent[e] = new Variable( (secondary_volume_in_pixels[e] / (secondary_bounding_box_width[e].getValue() * secondary_bounding_box_height[e].getValue() * secondary_bounding_box_depth[e].getValue())) );
			}
			final_secondary_results_table.setColumn("BOUNDING_BOX_EXTENT", secondary_bounding_box_extent);
			
			final_secondary_results_table.sort("PRIMARY_LABEL");
			
			secondary_original_measurements_table = null;
			
		}
		
		if (display_results_tables) {
			final_primary_results_table.show(PRIMARY_RESULTS_TABLE_NAME);	
			final_secondary_results_table.show(SECONDARY_RESULTS_TABLE_NAME);			
		}
		
		clij2.clear();
	}
	
	
	public ResultsTable getPrimaryTable() {
		return final_primary_results_table;
	}
	
	public ResultsTable getSecondaryTable() {
		return final_secondary_results_table;
	}
	
	
	public void padStackLids(ImagePlus inputImage) {
		ImageStack imageStack = inputImage.getStack();
		int width = imageStack.getWidth();
		int height = imageStack.getHeight();
		int bitDepth = imageStack.getBitDepth();
		
		ImagePlus black_slice_ImagePlus = IJ.createImage("black_slice", bitDepth + "-bit black", (int) width, (int) height, 1, 1, 1);
		ImageProcessor black_slice_Processor = black_slice_ImagePlus.getProcessor();
		
		imageStack.addSlice(black_slice_Processor);
		imageStack.addSlice("padded_slice", black_slice_Processor, 0);
	}
	
	
	public void removePaddedSlices(ClearCLBuffer input, ClearCLBuffer output) {
	
		int depth = (int) input.getDepth();
		clij2.subStack(input, output, 1, depth-1);
	
	}
	
	public void labelExclusion(ClearCLBuffer input, String volumeRange, String MMER_Range, ResultsTable final_edge_analysis_table, ClearCLBuffer output) throws NumberFormatException {
		
		log.debug("Starting label exclusion for " + input.getName());
		//get minimum volume limiter
		float minVolume = BV3DBoxUtilities.getMinFromRange(volumeRange);
		log.debug("Min volume = " + minVolume);
		
		//get maximum volume limiter
		float maxVolume = BV3DBoxUtilities.getMaxFromRange(volumeRange);
		log.debug("Max volume = " + maxVolume);

		//get MAX_MIN_DISTANCE_TO_CENTROID_RATIO minimum limiter
		float min_MMER = BV3DBoxUtilities.getMinFromRange(MMER_Range);
		log.debug("Min MMDTCR = " + min_MMER);
		
		//get MAX_MIN_DISTANCE_TO_CENTROID_RATIO maximum limiter
		float max_MMER = BV3DBoxUtilities.getMaxFromRange(MMER_Range);
		log.debug("Max MMDTCR = " + max_MMER);
		
			
		ResultsTable inputStatisticsTable = new ResultsTable(); 
		clij2.statisticsOfLabelledPixels(input, input, inputStatisticsTable);
		//inputStatisticsTable.show("inputStatisticsTable_" + input.getName());	//test output
		float[] volumeOfLabel = inputStatisticsTable.getColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.value);
		if (volumeOfLabel != null) {
			
			log.debug("volumeOfLabel[] size = " + volumeOfLabel.length);
			
			int label_count = inputStatisticsTable.size();
			log.debug("Object count = " + label_count);
			
			ResultsTable edge_analysis_table = getLabelEdgeAnalysisTable(input);
			log.debug("Initial edge_analysis_table size = " + edge_analysis_table.size());
			
			float[] min_extension = edge_analysis_table.getColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MINIMUM_INTENSITY.value);
			float[] max_extension = edge_analysis_table.getColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAXIMUM_INTENSITY.value);
			float[] mean_extension = edge_analysis_table.getColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.value);
			float[] std_extension = edge_analysis_table.getColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.STANDARD_DEVIATION_INTENSITY.value);
			float[] min_max_extension_ratio = new float[min_extension.length];
			float[] mean_max_extension_ratio = new float[min_extension.length];
			
			int[] label_exclusion_vector = new int[label_count + 1];
			label_exclusion_vector[0] = 0;
			int keptObjects = 0;
			int excludedObjects = 0;
			
			for (int object = 0; object < label_count; object++) {
				min_max_extension_ratio[object] = (max_extension[object] == 0) ? 0 : min_extension[object] / max_extension[object];
				log.debug("Object --> " + object + " Volume = " + volumeOfLabel[object] + "/ min-max-extension-ratio = " + min_max_extension_ratio[object]);
				
				mean_max_extension_ratio[object] = (max_extension[object] == 0) ? 0 : mean_extension[object] / max_extension[object];
				log.debug("Object --> " + object + " --> mean-max-extension-ratio = " + mean_max_extension_ratio[object]);
				
				if (volumeOfLabel[object] >= minVolume && volumeOfLabel[object] <= maxVolume && mean_max_extension_ratio[object] >= min_MMER && mean_max_extension_ratio[object] <= max_MMER) {
					
					label_exclusion_vector[object + 1] = 0;	//keep label
					keptObjects++;
					final_edge_analysis_table.addRow();
					final_edge_analysis_table.addValue("VOLUME_OF_LABEL", volumeOfLabel[object]);	//for test reasons
					final_edge_analysis_table.addValue("MIN_MAX_EXTENSION_RATIO", min_max_extension_ratio[object]);
					final_edge_analysis_table.addValue("MEAN_MAX_EXTENSION_RATIO", mean_max_extension_ratio[object]);
					final_edge_analysis_table.addValue("MIN_EXTENSION", min_extension[object]);
					final_edge_analysis_table.addValue("MAX_EXTENSION", max_extension[object]);
					final_edge_analysis_table.addValue("MEAN_EXTENSION", mean_extension[object]);
					final_edge_analysis_table.addValue("STD_DEV_EXTENSION", std_extension[object]);
					
				} else {
					
					label_exclusion_vector[object + 1] = 1;	//remove label
					excludedObjects++;
					
				}
				log.debug("label_exclusion_vector[0]["+object+"] = " + label_exclusion_vector[object + 1]);
				
			}
			
			log.debug("kept objects = " + keptObjects);
			log.debug("excluded objects = " + excludedObjects);
			log.debug("final edge_analysis_table size = " + edge_analysis_table.size());
			
			ImagePlus exclusion_vector_ImagePlus = IJ.createImage("label_exclusion_vector " + input.getName(), label_count + 1, 1, 1, 8);
			ImageProcessor exclusionVectorProcessor = exclusion_vector_ImagePlus.getProcessor();
			
			for (int xPixel = 0; xPixel < label_exclusion_vector.length; xPixel++) {
				exclusionVectorProcessor.putPixel(xPixel, 0, label_exclusion_vector[xPixel]);
			}
			
			ClearCLBuffer exclusionVectorImage = clij2.push(exclusion_vector_ImagePlus);
			
			clij2.excludeLabels(exclusionVectorImage, input, output);
			
			//cleanup
			exclusionVectorImage.close();
			
		} else {
			
			clij2.copy(input, output);
		}
		
		log.debug("Finishing label exclusion for " + input.getName());
	}
	
	
	public ResultsTable getLabelEdgeAnalysisTable(ClearCLBuffer input) {
		
		ClearCLBuffer label_edges = clij2.create(input);
		ClearCLBuffer distance_map = clij2.create(input);
		clij2.reduceLabelsToLabelEdges(input, label_edges);
		clij2.euclideanDistanceFromLabelCentroidMap(input, distance_map);
		ResultsTable edge_agalysis_table = new ResultsTable();
		clij2.statisticsOfLabelledPixels(distance_map, label_edges, edge_agalysis_table);
		label_edges.close();
		distance_map.close();
		return edge_agalysis_table;
	}
	
	
			
		

	@Override
	public boolean isCanceled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void cancel(String reason) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public String getCancelReason() {
		// TODO Auto-generated method stub
		return null;
	}
	
}
