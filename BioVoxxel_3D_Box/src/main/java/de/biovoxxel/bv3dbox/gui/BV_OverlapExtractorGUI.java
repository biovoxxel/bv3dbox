package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_OverlapExtractor;
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

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Analysis>Overlap Extractor (2D/3D)")
public class BV_OverlapExtractorGUI implements Command {

	@Parameter
	UpdateService updateService;
	
	@Parameter(label = "Primary objects", description = "", persist = true, initializer = "checkUpdateSites")
	private ImagePlus image_plus_1;
	
	@Parameter(label = "Secondary objects", description = "", persist = true)
	private ImagePlus image_plus_2;
	
	@Parameter(label = "%-Volume range", description = "", min = "0", max = "100")
	private String volume_range = "0.0-100.0";
	
	@Parameter(label = "Exclude primary on edges", description = "")
	private Boolean exclude_edge_objects = false;
	
	@Parameter(label = "Show original primary statistics", description = "")
	private Boolean show_original_primary_statistics = false;
	
	@Parameter(label = "Show extracted objects", description = "")
	private Boolean show_extracted_objects = false;
	
	@Parameter(label = "Show count statistics", description = "")
	private Boolean show_count_statistics = false;
	
	@Parameter(label = "Show volume statistics", description = "")
	private Boolean show_volume_statistics = false;
		
	@Parameter(label = "Show %-volume map", description = "")
	private Boolean show_percent_volume_map = false;

	@Override
	public void run() {
		
		BV_OverlapExtractor bvolex = new BV_OverlapExtractor(image_plus_1, image_plus_2);
		
		bvolex.setVolumeRange(volume_range);
		bvolex.setOutputFlags(exclude_edge_objects, show_original_primary_statistics, show_extracted_objects, show_count_statistics, show_volume_statistics, show_percent_volume_map);
		
		bvolex.extract();
	}	
	
	public void checkUpdateSites() {
		BV3DBoxUtilities.displayMissingDependencyWarning(updateService, "clij,clij2,clijx-assistant,clijx-assistant-extensions,3D ImageJ Suite");
		
	}
}
