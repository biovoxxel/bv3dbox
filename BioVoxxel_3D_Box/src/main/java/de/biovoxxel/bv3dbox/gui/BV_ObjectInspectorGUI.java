package de.biovoxxel.bv3dbox.gui;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_ObjectInspector;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import net.imagej.updater.UpdateService;

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


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Analysis>Object Inspector (2D/3D)")
public class BV_ObjectInspectorGUI extends DynamicCommand {

	@Parameter(required = true, label = "Primary objects (labels)", description = "")
	private ImagePlus primary_ImagePlus;
	
	@Parameter(required = true, label = "Secondary objects (labels)", description = "")
	private ImagePlus secondary_ImagePlus;
	
	
	@Parameter(required = true, persist = true, label = "Primary original image (gray)", description = "", initializer = "initializeOriginalImageChoices")
	private String original_1_title;
	
	@Parameter(required = true, persist = true, label = "Secondary original image (gray)", description = "", initializer = "initializeOriginalImageChoices")
	private String original_2_title;
	
	
	@Parameter(required = true, label = "Primary volume limitation (px)", description = "")
	private String primary_volume_range = "0-Infinity";
	
	@Parameter(required = true, label = "Primary mean/max extension ratio", description = "")
	private String primary_MMER_range = "0.00-1.00";
	
	@Parameter(required = true, label = "Secondary volume limitation (px)", description = "")
	private String secondary_volume_range = "0-Infinity";
	
	@Parameter(required = true, label = "Secondary mean/max extension ratio", description = "")
	private String secondary_MMER_range = "0.00-1.00";
	
	@Parameter(required = false, label = "Exclude primary edge objects", description = "")
	private Boolean exclude_primary_objects_on_edges = true;
	
	@Parameter(required = false, label = "Pad stack tops", description = "Adds a black slice before the first and after the last stack slice.\r\n"
			+ "	 This way objects still visible in the first or last slice will not be excluded by the exclude on edge function.\r\n"
			+ "	 This however introduces a certain bias and error in any analysis and should be used with care or only in test cases.")
	private Boolean pad_stack_tops = false;
	
	@Parameter(required = false, label = "Display results tables", description = "")
	private Boolean display_results_tables = true;
	
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
		
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2,clijx-assistant,clijx-assistant-extensions,3D ImageJ Suite");
				
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
		BV_ObjectInspector bvoi = new BV_ObjectInspector(primary_ImagePlus, secondary_ImagePlus);
		
		bvoi.setOriginalImages(original_1_title, original_2_title);
		bvoi.setPrimaryVolumeRange(primary_volume_range);
		bvoi.setPrimaryMMDTCRRange(primary_MMER_range);
		bvoi.setSecondaryVolumeRange(secondary_volume_range);
		bvoi.setSecondaryMMDTCRRange(secondary_MMER_range);
		bvoi.setEdgeExclusion(exclude_primary_objects_on_edges);
		bvoi.padStackTops(pad_stack_tops);
		bvoi.setOutputImageFlags(display_results_tables, display_analyzed_label_maps, show_count_map);
		
		bvoi.inspect();
	}
	
}
