package de.biovoxxel.bv3dbox.plugins;

import java.util.Arrays;
import java.util.List;

import javax.swing.JOptionPane;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;

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
import utilities.BV3DBoxSettings;
import utilities.BV3DBoxUtilities;
import utilities.BV3DBoxUtilities.LutNames;


/**
 * 
 * @author BioVoxxel
 *
 */

@Plugin(type = Command.class, menuPath = "BV3DBox>Object Inspector 2D/3D")
public class ObjectInspector3D extends DynamicCommand {

	@Parameter
	PrefService prefs;
	
	@Parameter
	LogService log;
	

	@Parameter(required = true, label = "Primary objects (labels)", description = "")
	private ImagePlus labels_1_ImagePlus;
	
	@Parameter(required = true, label = "Secondary objects (labels)", description = "")
	private ImagePlus labels_2_ImagePlus;
	
	
	@Parameter(required = true, persist = true, label = "Primary original image", description = "", initializer = "initializeOriginalImageChoices")
	private String original_1_title;
	
	@Parameter(required = true, persist = true, label = "Secondary original image (gray)", description = "", initializer = "initializeOriginalImageChoices")
	private String original_2_title;
	
	
	@Parameter(required = true, label = "Primary volume limitation", description = "")
	private String primary_volume_range = "0-Infinity";
	
	@Parameter(required = true, label = "Primary MMDTC ratio", description = "")
	private String primary_MMDTCR_range = "0.00-1.00";
	
	@Parameter(required = true, label = "Secondary volume limitation", description = "")
	private String secondary_volume_range = "0-Infinity";
	
	@Parameter(required = true, label = "Secondary MMDTC ratio", description = "")
	private String secondary_MMDTCR_range = "0.00-1.00";
	
	@Parameter(required = false, label = "Exclude primary edge objects", description = "")
	private Boolean exclude_primary_objects_on_edges = true;
	
	@Parameter(required = false, label = "Pad stack tops", description = "")
	private Boolean pad_stack_tops = false;
	
	@Parameter(required = false, label = "Show analysis label map", description = "")
	private Boolean display_analyzed_label_maps = false;
	
	@Parameter(required = false, label = "Show count map", description = "")
	private Boolean show_count_map = false;

//	@Parameter(required = false, label = "Include background measurements", description = "")
//	private Boolean include_background_measurement = false;

	
	CLIJ2 clij2 = CLIJ2.getInstance();
	
	
	ClearCLBuffer labels_1_gpu = null;
	ClearCLBuffer labels_2_gpu = null;
	ClearCLBuffer original_1_gpu = null;
	ClearCLBuffer original_2_gpu = null;
	
	String PRIMARY_RESULTS_TABLE_NAME = "Primary_Results";
	String SECONDARY_RESULTS_TABLE_NAME = "Secondary_Results";
	
	String GLASBEY_LUT = "glasbey_on_dark";
	String GEEN_FIRE_BLUE_LUT = "Green Fire Blue";
	String FIRE_LUT = "Fire";
	
		
	public void run() {
				
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "debug_level", LogLevel.INFO));
		log.info("------------------------------------------------------");
		log.info("labels_1_ImagePlus = " + labels_1_ImagePlus);
		log.info("labels_2_ImagePlus = " + labels_2_ImagePlus);
		log.info("original_1_title = " + original_1_title);
		log.info("original_2_title = " + original_2_title);
		log.info("primary_volume_range = " + primary_volume_range);
		log.info("primary_MMDTCR_range = " + primary_MMDTCR_range);
		log.info("secondary_volume_range = " + secondary_volume_range);
		log.info("secondary_MMDTCR_range = " + secondary_MMDTCR_range);
		log.info("exclude_primary_objects_on_edges = " + exclude_primary_objects_on_edges);
		log.info("pad_stack_tops = " + pad_stack_tops);
		log.info("display_analyzed_label_maps = " + display_analyzed_label_maps);
		log.info("show_count_map = " + show_count_map);
//		log.info("include_background_measurement = " + include_background_measurement);
		log.info("------------------------------------------------------");
		
		
		clij2.clear();

		if (labels_1_ImagePlus == labels_2_ImagePlus) {
			cancel("Primary and secondary label image need to be different");
			return;
		}
		
		if (labels_1_ImagePlus.getNDimensions() > 3 || labels_2_ImagePlus.getNDimensions() > 3) {
			cancel("Does not work on hyperstacks");
		}
		
		int[] dimensions_label_image_1 = labels_1_ImagePlus.getDimensions();
		int[] dimensions_label_image_2 = labels_2_ImagePlus.getDimensions();
		
		for (int dim = 0; dim < dimensions_label_image_1.length; dim++) {
			if (dimensions_label_image_1[dim] != dimensions_label_image_2[dim]) {
				cancel("Image dimensions between primary and secondary image do not match");
			}
		}
		
		if (labels_1_ImagePlus.getNDimensions() > 3 || labels_2_ImagePlus.getNDimensions() > 3) {
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
		
		
		Calibration voxel_calibration = labels_1_ImagePlus.getCalibration();
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
			padStackLids(labels_1_ImagePlus);
		}
		
		
		if (labels_1_ImagePlus.getProcessor().isBinary()) {
			
			ClearCLBuffer binaryInput_1 = clij2.push(labels_1_ImagePlus);
			labels_1_gpu = clij2.create(binaryInput_1.getDimensions(), NativeTypeEnum.Float);
			clij2.connectedComponentsLabelingBox(binaryInput_1, labels_1_gpu);
			binaryInput_1.close();
			log.debug("convert " + labels_1_ImagePlus.getTitle() + " to connected components");
			
		} else if (labels_1_ImagePlus.getBitDepth() != 24) {
			
			labels_1_gpu = clij2.push(labels_1_ImagePlus);
			
		} else {
			
			JOptionPane.showMessageDialog(null, "Wrong input image format\nNeeds to be of type gray-scale label mask or 8-bit binary", "Wrong image type", JOptionPane.WARNING_MESSAGE);
			return;
			
		}
		labels_1_gpu.setName("gpu_" + labels_1_ImagePlus.getTitle());
		log.debug(labels_1_gpu + " pushed to GPU");			
		
		
				
		if (labels_2_ImagePlus.getProcessor().isBinary()) {
			
			ClearCLBuffer binaryInput_2 = clij2.push(labels_2_ImagePlus);
			labels_2_gpu = clij2.create(binaryInput_2.getDimensions(), NativeTypeEnum.Float);
			clij2.connectedComponentsLabelingBox(binaryInput_2, labels_2_gpu);
			binaryInput_2.close();
			log.debug("convert " + labels_2_ImagePlus.getTitle() + " to connected components");
			
		} else if (labels_2_ImagePlus.getBitDepth() != 24) {
			
			labels_2_gpu = clij2.push(labels_2_ImagePlus);
			
		} else {
			
			JOptionPane.showMessageDialog(null, "Wrong input image format\nNeeds to be of type gray-scale label mask or 8-bit binary", "Wrong image type", JOptionPane.WARNING_MESSAGE);
			return;
			
		}
		
		labels_2_gpu.setName("gpu_" + labels_2_ImagePlus.getTitle());
		log.debug(labels_2_gpu + " pushed to GPU");	
		
		
		
		if (!original_1_title.equals("None")) {
			original_1_gpu = clij2.push(original_1_ImagePlus);
			original_1_gpu.setName("gpu_" + original_1_ImagePlus.getTitle());
			
			log.debug(original_1_ImagePlus + " pushed to GPU");
		}
	
		
		
		if (!original_2_title.equals("None")) {
			original_2_gpu = clij2.push(original_2_ImagePlus);
			original_2_gpu.setName("gpu_" + original_2_ImagePlus.getTitle());
			
			log.debug(original_2_ImagePlus + " pushed to GPU");
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
		finalLabels_1.setName("final_" + labels_1_ImagePlus.getTitle());
		
		
		ResultsTable final_edge_analysis_table_1 = new ResultsTable();
		
		if (!primary_volume_range.equalsIgnoreCase("0-infinity") || !primary_MMDTCR_range.equalsIgnoreCase("0.00-1.00")) {
						
			labelExclusion(labels_1_gpu, primary_volume_range, primary_MMDTCR_range, final_edge_analysis_table_1, finalLabels_1);
			
		} else {
			
			clij2.copy(labels_1_gpu, finalLabels_1);
			
		}
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
		finalLabels_2.setName("final_" + labels_2_ImagePlus.getTitle());
		
		ResultsTable final_edge_analysis_table_2 = new ResultsTable();
		
		if (!secondary_volume_range.equalsIgnoreCase("0-infinity") || !secondary_MMDTCR_range.equalsIgnoreCase("0.00-1.00")) {
					
			labelExclusion(maskedLabels_2, secondary_volume_range, secondary_MMDTCR_range, final_edge_analysis_table_2, finalLabels_2);
			
		} else {
			
			clij2.copy(maskedLabels_2, finalLabels_2);
			
		}
		maskedLabels_2.close();
				
		//create overlap count mask		
		ClearCLBuffer overlapCountMap = clij2.create(finalLabels_1);
		overlapCountMap.setName("CountMap_" + labels_1_ImagePlus.getTitle());
		boolean label_overlap_count_map_created = clij2.labelOverlapCountMap(finalLabels_1, finalLabels_2, overlapCountMap);
		log.debug("LabelOverlapCountMap finished = " + label_overlap_count_map_created);
		
			
		ResultsTable final_primary_results_table = new ResultsTable();
		
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
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, overlapCountMap, true, LutNames.GEEN_FIRE_BLUE_LUT);	//test output			
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
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, finalLabels_1, true, LutNames.GLASBEY_LUT);
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, finalLabels_2, true, LutNames.GLASBEY_LUT);
		}
		finalLabels_1.close();
		finalLabels_2.close();
		
		
		
		
		ResultsTable final_secondary_results_table = new ResultsTable();
		
		Variable[] primary_label_origin_of_secondary_label = primary_label_origin_of_secondary_label_table.getColumnAsVariables(StatisticsOfLabelledPixels.STATISTICS_ENTRY.MEAN_INTENSITY.name());
		//primary_label_origin_of_secondary_label[0] = new Variable("Background");
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
	
	public void labelExclusion(ClearCLBuffer input, String volumeRange, String MMDTC_Range, ResultsTable final_edge_analysis_table, ClearCLBuffer output) throws NumberFormatException {
		
		log.debug("Label exclusion for " + input.getName());
		//get minimum volume limiter
		float minVolume = BV3DBoxUtilities.getMinFromRange(volumeRange);
		log.debug("Min volume = " + minVolume);
		
		//get maximum volume limiter
		float maxVolume = BV3DBoxUtilities.getMaxFromRange(volumeRange);
		log.debug("Max volume = " + maxVolume);

		//get MAX_MIN_DISTANCE_TO_CENTROID_RATIO minimum limiter
		float min_MMDTCR = BV3DBoxUtilities.getMinFromRange(MMDTC_Range);
		log.debug("Min MMDTCR = " + min_MMDTCR);
		
		//get MAX_MIN_DISTANCE_TO_CENTROID_RATIO maximum limiter
		float max_MMDTCR = BV3DBoxUtilities.getMaxFromRange(MMDTC_Range);
		log.debug("Max MMDTCR = " + max_MMDTCR);
		
			
		ResultsTable inputStatisticsTable = new ResultsTable(); 
		clij2.statisticsOfLabelledPixels(input, input, inputStatisticsTable);
		//inputStatisticsTable.show("inputStatisticsTable_" + input.getName());	//test output
		float[] volumeOfLabel = inputStatisticsTable.getColumn(StatisticsOfLabelledPixels.STATISTICS_ENTRY.PIXEL_COUNT.value);
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
			log.debug("Object --> " + object + " --> mean-max-extension-ratio = " + min_max_extension_ratio[object]);
			
			if (volumeOfLabel[object] >= minVolume && volumeOfLabel[object] <= maxVolume && min_max_extension_ratio[object] >= min_MMDTCR && min_max_extension_ratio[object] <= max_MMDTCR) {
				
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
		input.close();
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


	public void cancel() {
		return;
	}
	
}
