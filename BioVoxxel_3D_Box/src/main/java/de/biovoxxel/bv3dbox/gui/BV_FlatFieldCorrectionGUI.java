package de.biovoxxel.bv3dbox.gui;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_FlatFieldCorrection;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.ColorSpaceConverter;
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

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Filtering>Flat Field Correction (2D/3D)")
public class BV_FlatFieldCorrectionGUI extends DynamicCommand {

	@Parameter(required = true)
	ImagePlus originalImagePlus = null;
	
	@Parameter(label = "Flat-field image", required = true)
	ImagePlus flatFieldImagePlus = null;
	
	@Parameter(label = "Dark-field image", required = false, initializer = "initializeOriginalImageChoices")
	String darkFieldImageName = "";

	
	
	@Override
	public void run() {
					
		BV_FlatFieldCorrection bvffcorr = new BV_FlatFieldCorrection();
		
		ImagePlus darkFieldImagePlus = WindowManager.getImage(darkFieldImageName);
		
		bvffcorr.setImages(originalImagePlus, flatFieldImagePlus, darkFieldImagePlus);
		
		bvffcorr.flatFieldCorrection();
	}
	
	public void initializeOriginalImageChoices() {
		
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2");
		
		List<String> extendedImageList = Arrays.asList(BV3DBoxUtilities.extendImageTitleListWithNone());
		
		final MutableModuleItem<String> darkFieldImageName = getInfo().getMutableInput("darkFieldImageName", String.class);
		darkFieldImageName.setChoices(extendedImageList);
	
	}
	
}
