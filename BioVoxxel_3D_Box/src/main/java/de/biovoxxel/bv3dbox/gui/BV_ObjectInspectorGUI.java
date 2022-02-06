package de.biovoxxel.bv3dbox.gui;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BVObjectInspector;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;


/**
 * 
 * @author BioVoxxel
 *
 */


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Object Inspector (2D/3D)")
public class BVObjectInspectorGUI extends DynamicCommand {

	@Parameter(required = true, label = "Primary objects (labels)", description = "")
	private ImagePlus primary_ImagePlus;
	
	@Parameter(required = true, label = "Secondary objects (labels)", description = "")
	private ImagePlus secondary_ImagePlus;
	
	
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
	
	@Parameter(required = false, label = "Pad stack tops", description = "Adds a black slice before the first and after the last stack slice.\r\n"
			+ "	 This way objects still visible in the first or last slice will not be excluded by the exclude on edge function.\r\n"
			+ "	 This however introduces a certain bias and error in any analysis and should be used with care or only in test cases.")
	private Boolean pad_stack_tops = false;
	
	@Parameter(required = false, label = "Show analysis label map", description = "")
	private Boolean display_analyzed_label_maps = false;
	
	@Parameter(required = false, label = "Show count map", description = "")
	private Boolean show_count_map = false;


	
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
	
	public void run() {
		BVObjectInspector bvoi = new BVObjectInspector(primary_ImagePlus, secondary_ImagePlus);
		
		bvoi.setOriginalImages(original_1_title, original_2_title);
		bvoi.setPrimaryVolumeRange(primary_volume_range);
		bvoi.setPrimaryMMDTCRRange(primary_MMDTCR_range);
		bvoi.setSecondaryVolumeRange(secondary_volume_range);
		bvoi.setSecondaryMMDTCRRange(secondary_MMDTCR_range);
		bvoi.setEdgeExclusion(exclude_primary_objects_on_edges);
		bvoi.setOutputImageFlags(display_analyzed_label_maps, show_count_map);
		
		bvoi.inspect();
	}
	
}
