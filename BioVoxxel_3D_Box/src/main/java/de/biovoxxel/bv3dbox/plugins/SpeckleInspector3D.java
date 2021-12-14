package de.biovoxxel.bv3dbox.plugins;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import org.scijava.Cancelable;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.macro.Variable;
import ij.measure.Calibration;
import ij.measure.ResultsTable;
import ij.process.ImageProcessor;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.StatisticsOfLabelledPixels;
import utilities.BV3DBoxUtilities;
import utilities.LoggerSetup;


/**
 * 
 * @author BioVoxxel
 *
 */

@Plugin(type = Command.class, menuPath = "BV3DBox>Speckle Inspector 2D/3D")
public class SpeckleInspector3D extends DynamicCommand implements Cancelable {

	Logger logger = LoggerSetup.getLogger();
	
	CLIJ2 clij2 = CLIJ2.getInstance();
	
	
	ClearCLBuffer labels_1_gpu = null;
	ClearCLBuffer labels_2_gpu = null;
	ClearCLBuffer original_1_gpu = null;
	ClearCLBuffer original_2_gpu = null;
	
	String PRIMARY_RESULTS_TABLE_NAME = "Primary_Results";
	String SECONDARY_RESULTS_TABLE_NAME = "Secondary_Results";
	
	

	@Parameter(required = true, persist = true, label = "Primary objects (labels)", description = "")
	private ImagePlus labels_1_ImagePlus;
	
	@Parameter(required = true, persist = true, label = "Secondary objects (labels)", description = "")
	private ImagePlus labels_2_ImagePlus;
	
	
	@Parameter(required = true, persist = true, label = "Primary original image", description = "", initializer = "initializeOriginalImageChoices")
	private String original_1_title;
	
	@Parameter(required = true, persist = true, label = "Secondary original image (gray)", description = "", initializer = "initializeOriginalImageChoices")
	private String original_2_title;
	
	
	@Parameter(required = true, label = "", description = "")
	private String primary_volume_range = "0-Infinity";
	
	@Parameter(required = true, label = "", description = "")
	private String primary_MMDTCR_range = "0.00-1.00";
	
	@Parameter(required = true, label = "", description = "")
	private String secondary_volume_range = "0-Infinity";
	
	@Parameter(required = true, label = "", description = "")
	private String secondary_MMDTCR_range = "0.00-1.00";
	
	@Parameter(required = false, label = "", description = "")
	private Boolean exclude_primary_objects_on_edges = true;
	
	@Parameter(required = false, label = "", description = "")
	private Boolean pad_stack_tops = false;
	
	@Parameter(required = false, label = "", description = "")
	private Boolean display_analyzed_label_maps = false;
	
	@Parameter(required = false, label = "", description = "")
	private Boolean show_count_map = false;

	@Parameter(required = false, label = "", description = "")
	private Boolean include_background_measurement = false;
	
//	@Parameter(required = false, label = "", description = "")
//	private Boolean create_outlines_on_original = false;	//use callback to switch if original not available
//	

	
		
	public void run() {
		
		clij2.clear();

		if (labels_1_ImagePlus == labels_2_ImagePlus) {
			JOptionPane.showMessageDialog(null, "Primary and secondary label image need to be different", "Duplicate image selection", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		
		ImagePlus original_1_ImagePlus = WindowManager.getImage(original_1_title);
		ImagePlus original_2_ImagePlus = WindowManager.getImage(original_2_title);
		
		
		Calibration voxel_calibration = labels_1_ImagePlus.getCalibration();
		String calibrated_units = voxel_calibration.getUnit();
		if (!calibrated_units.matches(".*ixel.*") && !calibrated_units.matches(".*oxel.*")) {
			voxel_calibration = original_1_ImagePlus.getCalibration();
			calibrated_units = voxel_calibration.getUnit();
		}
		double voxel_width = voxel_calibration.pixelWidth;
		double voxel_height = voxel_calibration.pixelHeight;
		double voxel_depth = voxel_calibration.pixelDepth;
		
		double voxel_volume = voxel_width * voxel_height * voxel_depth;
		
	
		if (exclude_primary_objects_on_edges && pad_stack_tops) {
			padStackLids(labels_1_ImagePlus);
		}
		
		
		if (labels_1_ImagePlus.getProcessor().isBinary()) {
			
			labels_1_ImagePlus.setProcessor(labels_1_ImagePlus.getProcessor().convertToFloatProcessor());
			
			ClearCLBuffer binaryInput_1 = clij2.push(labels_1_ImagePlus);
			labels_1_gpu = clij2.create(binaryInput_1);
			clij2.connectedComponentsLabelingBox(binaryInput_1, labels_1_gpu);
			binaryInput_1.close();
			
		} else if (labels_1_ImagePlus.getBitDepth() == 32) {
			
			labels_1_gpu = clij2.push(labels_1_ImagePlus);
			
		} else {
			
			cancel("Wrong input image format\\nNeeds to be of type 32-bit label mask or 8-bit binary");
			
		}
		labels_1_gpu.setName("gpu_" + labels_1_ImagePlus.getTitle());
		logger.info(labels_1_gpu + " pushed to GPU");			
		
		
				
		if (labels_2_ImagePlus.getProcessor().isBinary()) {
			
			labels_2_ImagePlus.setProcessor(labels_2_ImagePlus.getProcessor().convertToFloatProcessor());
			
			ClearCLBuffer binaryInput_2 = clij2.push(labels_2_ImagePlus);
			labels_2_gpu = clij2.create(binaryInput_2);
			clij2.connectedComponentsLabelingBox(binaryInput_2, labels_2_gpu);
			binaryInput_2.close();
			
		} else if (labels_2_ImagePlus.getBitDepth() == 32) {
			
			labels_2_gpu = clij2.push(labels_2_ImagePlus);
			
		} else {
			
			cancel("Wrong input image format\\nNeeds to be of type 32-bit label mask or 8-bit binary");
		}
		
		labels_2_gpu.setName("gpu_" + labels_2_ImagePlus.getTitle());
		
		
		
		
		if (!original_1_title.equals("None")) {
			original_1_gpu = clij2.push(original_1_ImagePlus);
			original_1_gpu.setName("gpu_" + original_1_ImagePlus.getTitle());
			
			logger.info(original_1_ImagePlus + " pushed to GPU");
		}
	
		
		
		if (!original_2_title.equals("None")) {
			original_2_gpu = clij2.push(original_2_ImagePlus);
			original_2_gpu.setName("gpu_" + original_2_ImagePlus.getTitle());
			
			logger.info(original_2_ImagePlus + " pushed to GPU");
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
		finalLabels_1.setName("final_" + labels_1_gpu.getName());
		if (!primary_volume_range.equalsIgnoreCase("0-infinity") || !primary_MMDTCR_range.equalsIgnoreCase("0.00-1.00")) {
						
			labelExclusion(labels_1_gpu, primary_volume_range, primary_MMDTCR_range, finalLabels_1);
			
		} else {
			
			clij2.copy(labels_1_gpu, finalLabels_1);
			
		}
		labels_1_gpu.close();
		
		
		//exclude secondary labels according to input limiters
		ClearCLBuffer finalLabels_2 = clij2.create(labels_2_gpu);
		finalLabels_2.setName("final_" + labels_2_gpu.getName());
		if (!secondary_volume_range.equalsIgnoreCase("0-infinity") || !secondary_MMDTCR_range.equalsIgnoreCase("0.00-1.00")) {
					
			labelExclusion(labels_2_gpu, secondary_volume_range, secondary_MMDTCR_range, finalLabels_2);
			
		} else {
			
			clij2.copy(labels_2_gpu, finalLabels_2);
			
		}
		labels_2_gpu.close();
		
		
		
		//Masking secondary labels with primary labels
		ClearCLBuffer tempMaskedLabels_2 = clij2.create(finalLabels_2);
		ClearCLBuffer maskedLabels_2 = clij2.create(finalLabels_2);
		maskedLabels_2.setName("masked_" + finalLabels_2.getName());
		clij2.mask(finalLabels_2, finalLabels_1, tempMaskedLabels_2);
		clij2.closeIndexGapsInLabelMap(tempMaskedLabels_2, maskedLabels_2);
		tempMaskedLabels_2.close();
		logger.info(finalLabels_2 + "masked with " + finalLabels_1 + " with output as " + maskedLabels_2);
		
				
		//create overlap count mask		
		ClearCLBuffer overlapCountMap = clij2.create(finalLabels_1);
		overlapCountMap.setName("overlapCountMask_" + labels_1_ImagePlus.getTitle());
		boolean label_overlap_count_map_created = clij2.labelOverlapCountMap(finalLabels_1, maskedLabels_2, overlapCountMap);
		logger.info("LabelOverlapCountMap finished = " + label_overlap_count_map_created);
		
			
		ResultsTable final_primary_results_table = new ResultsTable();
		
		ResultsTable primary_original_measurements_table = new ResultsTable();
		
						
		if (original_1_gpu == null) {
			
			clij2.statisticsOfBackgroundAndLabelledPixels(finalLabels_1, finalLabels_1, primary_original_measurements_table);
	
		} else {
			
			clij2.statisticsOfBackgroundAndLabelledPixels(original_1_gpu, finalLabels_1, primary_original_measurements_table);

		}
		
//		read primary identifier add to final table
		Variable[] primary_label_identifier = primary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.IDENTIFIER.name());
		final_primary_results_table.setColumn("PRIM_OBJ_ID", primary_label_identifier);
		
		
//		get secondary object count and display count map if desired
		ResultsTable overlapCountTable = new ResultsTable();
		clij2.statisticsOfBackgroundAndLabelledPixels(overlapCountMap, finalLabels_1, overlapCountTable);
		Variable[] secondaryObjectCountArray = overlapCountTable.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name());
		secondaryObjectCountArray[0] = new Variable("Background");
		final_primary_results_table.setColumn("SEC_OBJECT_COUNT", secondaryObjectCountArray);
		
		if (show_count_map) {
			pullAndDisplayImageFromGPU(overlapCountMap, true);	//test output			
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
		
////TODO: determine how to output single analysis results which do not fit in the primary or secondary table
//		double total_pixel_count_of_all_primary_objects =  primary_image_volume - primary_volume_in_pixels[0]; //image volume - background volume
//		double volume_fraction_of_primary_objects = primary_image_volume /  total_pixel_count_of_all_primary_objects;
		
	
		//calculate secondary distances
		ClearCLBuffer center_distance_map = clij2.create(finalLabels_1);
		center_distance_map.setName("centroid_dist_" + finalLabels_1.getName());
		clij2.euclideanDistanceFromLabelCentroidMap(finalLabels_1, center_distance_map);
		logger.info("EuclideanDistanceFromLabelCentroidMap created");
		
		ClearCLBuffer border_distance_map = clij2.create(finalLabels_1);
		border_distance_map.setName("border_dist_" + finalLabels_1.getName());
		clij2.distanceMap(finalLabels_1, border_distance_map);
		logger.info("MaximumExtensionMap created");

		
		double max_primary_label_count = clij2.maximumOfAllPixels(finalLabels_1);
		logger.info("max_primary_label_count = " + max_primary_label_count);
		
		ResultsTable secondary_original_measurements_table = new ResultsTable();
		ResultsTable center_distance_table = new ResultsTable();
		ResultsTable border_distance_table = new ResultsTable();
		
		ResultsTable primary_label_origin_of_secondary_label_table = new ResultsTable();
		clij2.statisticsOfBackgroundAndLabelledPixels(finalLabels_1, maskedLabels_2, primary_label_origin_of_secondary_label_table);
					
		if (original_2_gpu == null) {
			
			clij2.statisticsOfBackgroundAndLabelledPixels(maskedLabels_2, maskedLabels_2, secondary_original_measurements_table);
			
		} else {
			
			clij2.statisticsOfBackgroundAndLabelledPixels(original_2_gpu, maskedLabels_2, secondary_original_measurements_table);
			
		}
		clij2.statisticsOfBackgroundAndLabelledPixels(center_distance_map, maskedLabels_2, center_distance_table);
		center_distance_map.close();
		clij2.statisticsOfBackgroundAndLabelledPixels(border_distance_map, maskedLabels_2, border_distance_table);
		border_distance_map.close();
		
		
		if (display_analyzed_label_maps) {
			pullAndDisplayImageFromGPU(finalLabels_1, true);
			pullAndDisplayImageFromGPU(maskedLabels_2, true);
		}
		finalLabels_1.close();
		maskedLabels_2.close();
		
		
		
		
		ResultsTable final_secondary_results_table = new ResultsTable();
		
		Variable[] primary_label_origin_of_secondary_label = primary_label_origin_of_secondary_label_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name());
		primary_label_origin_of_secondary_label[0] = new Variable("Background");
		final_secondary_results_table.setColumn("PRIMARY_LABEL", primary_label_origin_of_secondary_label);
		primary_label_origin_of_secondary_label_table = null;
		
		final_secondary_results_table.setColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.IDENTIFIER.name(), secondary_original_measurements_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.IDENTIFIER.name()));
		
		//Calculate primary scaled volumes
		double[] secondary_volume_in_pixels = secondary_original_measurements_table.getColumnAsDoubles(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.value);
		Variable[] secondary_volume_in_units = new Variable[secondary_volume_in_pixels.length];
		for (int cal2 = 0; cal2 < secondary_volume_in_pixels.length; cal2++) {
			secondary_volume_in_units[cal2] = new Variable(secondary_volume_in_pixels[cal2] * voxel_volume);
		}
		final_secondary_results_table.setColumn("VOLUME ("+calibrated_units+")", secondary_volume_in_units);
		
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
		
		if (!include_background_measurement) {
			final_primary_results_table.deleteRow(0);
			final_secondary_results_table.deleteRow(0);
		}
		
		final_primary_results_table.show(PRIMARY_RESULTS_TABLE_NAME);	
		final_secondary_results_table.show(SECONDARY_RESULTS_TABLE_NAME);
		
		clij2.clear();
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
	
	public void labelExclusion(ClearCLBuffer input, String volumeRange, String MMDTC_Range, ClearCLBuffer output) throws NumberFormatException {
		
		//get minimum volume limiter
		float minVolume = getMinFromRange(volumeRange);
		logger.info("Min volume = " + minVolume);
		
		//get maximum volume limiter
		float maxVolume = getMaxFromRange(volumeRange);
		logger.info("Max volume = " + maxVolume);

		//get MAX_MEAN_DISTANCE_TO_CENTROID_RATIO minimum limiter
		float min_MMDTCR = getMinFromRange(MMDTC_Range);
		logger.info("Min MMDTCR = " + min_MMDTCR);
		
		//get MAX_MEAN_DISTANCE_TO_CENTROID_RATIO maximum limiter
		float max_MMDTCR = getMaxFromRange(MMDTC_Range);
		logger.info("Max MMDTCR = " + max_MMDTCR);
		
		
		
		ResultsTable inputStatisticsTable = new ResultsTable(); 
		clij2.statisticsOfBackgroundAndLabelledPixels(input, input, inputStatisticsTable);
		//inputStatisticsTable.show("inputStatisticsTable_" + input.getName());	//test output
		
		int label_count = inputStatisticsTable.getCounter();
		logger.info("Object count = " + label_count);
		
		float[] volumeOfLabel = inputStatisticsTable.getColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.value);
		float[] MMDTCRatioOfLabel = inputStatisticsTable.getColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MAX_MEAN_DISTANCE_TO_CENTROID_RATIO.value);
		
		float[] inverted_MMDTCR = new float[label_count];
		for (int labelIndex = 0; labelIndex < label_count; labelIndex++) {
			inverted_MMDTCR[labelIndex] = 1 / MMDTCRatioOfLabel[labelIndex];
		}
		
		int[] label_exclusion_vector = new int[label_count];
		label_exclusion_vector[0] = 0;
		//create primary label exclusion vector image
		for (int object = 1; object < label_exclusion_vector.length; object++) {
			logger.info("Object --> " + object + " Volume = " + volumeOfLabel[object] + "/ MMDTCR = " + inverted_MMDTCR[object]);
			if (volumeOfLabel[object] >= minVolume && volumeOfLabel[object] <= maxVolume && inverted_MMDTCR[object] >= min_MMDTCR && inverted_MMDTCR[object] <= max_MMDTCR) {
				
				label_exclusion_vector[object] = 0;	//keep label
				
			} else {
				
				label_exclusion_vector[object] = 1;	//remove label
				
			}
			logger.info("label_exclusion_vector[0]["+object+"] = " + label_exclusion_vector[object]);
			
		}
		ImagePlus exclusion_vector_ImagePlus = IJ.createImage("label_exclusion_vector " + input.getName(), label_count, 1, 1, 8);
		ImageProcessor exclusionVectorProcessor = exclusion_vector_ImagePlus.getProcessor();
		for (int xPixel = 0; xPixel < label_exclusion_vector.length; xPixel++) {
			exclusionVectorProcessor.putPixel(xPixel, 0, label_exclusion_vector[xPixel]);
		}

		ClearCLBuffer exclusionVectorImage = clij2.push(exclusion_vector_ImagePlus);
		
		clij2.excludeLabels(exclusionVectorImage, input, output);
		
		//cleanup
		exclusionVectorImage.close();
		input.close();
	}
	
	public float getMinFromRange(String range) throws NumberFormatException {
		float min_value = Float.NaN;
		if (range.contains("-")) {
			String min_value_string = range.substring(0, range.indexOf("-"));
			if (min_value_string.equalsIgnoreCase("infinity")) {
				min_value = Float.POSITIVE_INFINITY;
			} else {
				min_value = Float.parseFloat(min_value_string);
			}
		}
		return min_value;
		
	}
	
	public float getMaxFromRange(String range) throws NumberFormatException {
		float max_value = Float.NaN;
		if (range.contains("-")) {
			String max_value_string = range.substring(range.indexOf("-") + 1);
			if (max_value_string.equalsIgnoreCase("infinity")) {
				max_value = Float.POSITIVE_INFINITY;
			} else {
				max_value = Float.parseFloat(max_value_string);
			}
		}
		return max_value;
		
	}
	
	
	public void pullAndDisplayImageFromGPU(ClearCLBuffer imageToShow, boolean addFireLUT) {
		ImagePlus imagePlusToBePulled = clij2.pull(imageToShow);
		imagePlusToBePulled.setTitle(imageToShow.getName());
		imagePlusToBePulled.resetDisplayRange();
		if (addFireLUT) {
			IJ.run(imagePlusToBePulled, "Fire", "");
		}
		imagePlusToBePulled.show();
		logger.info("Pulling and displaying = " + imageToShow);
	}
	
	
	public List<String> imageListWithNoneOption() {
		
		String[] imageNames = BV3DBoxUtilities.extendImageTitleListWithNone();
		
		List<String> extendedImageList = Arrays.asList(imageNames);
		
		return extendedImageList;
	}
	
	
	public void initializeOriginalImageChoices() {
		
		List<String> extendedImageList = imageListWithNoneOption();
		
		final MutableModuleItem<String> original_1_title = getInfo().getMutableInput("original_1_title", String.class);
		original_1_title.setChoices(extendedImageList);
		
		final MutableModuleItem<String> original_2_title = getInfo().getMutableInput("original_2_title", String.class);
		original_2_title.setChoices(extendedImageList);
	
	}



	
}
