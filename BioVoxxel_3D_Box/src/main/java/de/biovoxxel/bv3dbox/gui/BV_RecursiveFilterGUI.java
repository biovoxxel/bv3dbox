package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_RecursiveFilter;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
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


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Filtering>Recursive Filter (2D/3D)")
public class BV_RecursiveFilterGUI extends DynamicCommand {

	@Parameter
	UpdateService us;
	
	@Parameter(required = true, label = "Image", description = "", initializer = "checkUpdateSites")
	ImagePlus current_image_plus;
	
	@Parameter(required = true, label = "Filter", description = "", choices = {"Median", "Gaussian"})
	String filter_method = "Gaussian";
	
	@Parameter(required = true, label = "Radius", description = "", min = "0.5", max = "20.0", stepSize = "0.5")
	Double recursiveRadius = 1.0;
	
	@Parameter(required = true, label = "Iteration", description = "", stepSize = "10", max = "200")
	Integer iterations = 10;
	

	


	public void run() {
						
		BV_RecursiveFilter bvrf = new BV_RecursiveFilter(current_image_plus);
		
		ClearCLBuffer output_image = bvrf.runRecursiveFilter(filter_method, recursiveRadius, iterations);
		
		ImagePlus outputImage = BV3DBoxUtilities.pullImageFromGPU(bvrf.getCurrentCLIJ2Instance(), output_image, true, LutNames.GRAY);
		outputImage.setTitle(WindowManager.getUniqueName(current_image_plus.getTitle() + "_" + recursiveRadius + "_" + iterations + "x"));
		outputImage.setCalibration(current_image_plus.getCalibration());
		outputImage.show();
		
	}
	
	public void checkUpdateSites() {
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2,clijx-assistant,clijx-assistant-extensions,3D ImageJ Suite");
	}
	
}
