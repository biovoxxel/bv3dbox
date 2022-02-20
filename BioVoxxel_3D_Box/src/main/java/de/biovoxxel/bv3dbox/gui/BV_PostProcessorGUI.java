package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.plugins.BV_PostProcessor;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
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


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Labels>Post Processor (2D/3D)")	
public class BV_PostProcessorGUI extends DynamicCommand {

	@Parameter(required = true, initializer = "setupImage")
	ImagePlus inputImagePlus;
	
	@Parameter(label = "Processing method", choices = {"Median (sphere, max r=15)", "Median (box, max r=15)", "Erode (sphere)", "Erode (box)", "Dilate (sphere)", "Dilate (box)", "Open (sphere)", "Open (box)", "Close (sphere)", "Close (box)", "Fill holes (labels)", "Variance (sphere)", "Variance (box)"}, callback = "processImage")
	String method = "Erode";
	
	@Parameter(label = "Iterations", min = "0", stepSize = "1", callback = "processImage")
	Integer iterations = 1;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	BV_PostProcessor bvpp;

	private ImagePlus outputImagePlus = null;
	private String outputImageName = null;
	
	public void run() {
		
		if (WindowManager.getImage(outputImageName) == null) {
			setupImage();
			processImage();
		}
		
		outputImagePlus.setTitle(WindowManager.getUniqueName(outputImagePlus.getTitle()));
		bvpp.getInputBuffer().close();
		CLIJ2 clij2 = bvpp.getCLIJ2Instance();
		clij2.clear();
		clij2.close();
	}

	
	
	private void setupImage() {
		
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2");
		
		bvpp = new BV_PostProcessor(inputImagePlus);
		
		outputImageName = "BVPP_" + inputImagePlus.getTitle();
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setValue(this, 1);
			stackSlice.setMaximumValue(1);
			
		}
	}
	
	public void processImage() {
		
		ClearCLBuffer outputBuffer = bvpp.postProcessor(method, iterations);
		
		ImagePlus tempImagePlus = bvpp.getImagePlus(outputBuffer);
		outputBuffer.close();
		
		outputImagePlus = WindowManager.getImage(outputImageName);
		
		if (outputImagePlus == null) {
			outputImagePlus = new ImagePlus();
		}
		outputImagePlus.setImage(tempImagePlus);
		outputImagePlus.setTitle("BVPP_" + inputImagePlus.getTitle());
		outputImagePlus.getProcessor().resetMinAndMax();
		outputImagePlus.show();
	}
	
	@SuppressWarnings("unused")
	private void slideSlices() {
		ImagePlus outputImagePlus = WindowManager.getImage(outputImageName);
		
		if (outputImagePlus != null) {

			outputImagePlus.setSlice(stackSlice);
			
		}	
	}
	
	public void cancel() {
		ImagePlus outputImagePlus = WindowManager.getImage(outputImageName);
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		CLIJ2 clij2 = bvpp.getCLIJ2Instance();
		clij2.clear();
		clij2.close();
	}
	
}
