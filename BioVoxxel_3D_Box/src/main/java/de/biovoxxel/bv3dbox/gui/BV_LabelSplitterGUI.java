/**
 * 
 */
package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.plugins.BV_LabelSplitter;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
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

/**
 * @author BioVoxxel
 */

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Labels>Label Splitter (2D/3D)")
public class BV_LabelSplitterGUI extends DynamicCommand {

	@Parameter(required = true, initializer = "setupImage")
	private ImagePlus inputImagePlus;
	
	@Parameter(label = "Separation method", choices = {"Maxima", "Eroded Maxima", "EDM Maxima", "Maxima Spheres", "DoG Seeds", "Eroded box", "Eroded sphere"}, callback = "processImageOnTheFly")
	private String separationMethod = "Maxima";
	
	@Parameter(label = "Spot sigma", min = "0f", callback = "processImageOnTheFly")
	private Float spotSigma = 1f;
	
	@Parameter(label = "Maxima detection radius", min = "0f", callback = "processImageOnTheFly")
	private Float maximaRadius = 1f;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	private Integer stackSlice = 1;
	
	@Parameter(label = "On the fly mode", required = false)
	private Boolean processOnTheFly = false;
	
	@Parameter(label = "Preview", callback = "processImage", required = false)
	private Button refreshButton;
	
	
	
	private CLIJ2 clij2 = CLIJ2.getInstance();
		private BV_LabelSplitter labelSplitter;
	
	private ClearCLBuffer input_image;
	private String outputImageName = null;
	
	
	public void run() {
		
		if (WindowManager.getImage(outputImageName) == null) {
			
			setupImage();
			ClearCLBuffer splitted_label_image = labelSplitter.splitLabels(input_image, separationMethod, spotSigma, maximaRadius);
			
			ImagePlus outputImagePlus = BV3DBoxUtilities.pullImageFromGPU(labelSplitter.getCurrentCLIJ2Instance(), splitted_label_image, false, LutNames.GLASBEY_LUT);
			splitted_label_image.close();
			
			outputImagePlus.setTitle(outputImageName);
			outputImagePlus.setCalibration(inputImagePlus.getCalibration());
			outputImagePlus.show();
			
			BV3DBoxUtilities.adaptImageDisplay(inputImagePlus, outputImagePlus);
		}
		
		
		if (clij2 != null) {
			clij2.clear();			
		}
		
	}
	
	
	
	public void setupImage() {
		
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2,clijx-assistant,clijx-assistant-extensions,3D ImageJ Suite");
		
		input_image = clij2.push(inputImagePlus);
		System.out.println(input_image);
		
		labelSplitter = new BV_LabelSplitter(clij2);
		labelSplitter.setVoxelRatios(inputImagePlus);
		
		outputImageName = WindowManager.getUniqueName("BVLS_" + inputImagePlus.getTitle());
		System.out.println(outputImageName);
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());	
			
		} else {
			
			stackSlice.setMaximumValue(1);
			stackSlice.setValue(this, 1);
		}
			
	}
	
	
	@SuppressWarnings("unused")
	private void processImageOnTheFly() {
		if (processOnTheFly) {
			processImage();
		}
	}
	
	public void processImage() {
		
		ClearCLBuffer splitted_label_image = labelSplitter.splitLabels(input_image, separationMethod, spotSigma, maximaRadius);
		
		ImagePlus outputImagePlus = BV3DBoxUtilities.pullImageFromGPU(labelSplitter.getCurrentCLIJ2Instance(), splitted_label_image, true, LutNames.GLASBEY_LUT);
		splitted_label_image.close();
		
		BV3DBoxUtilities.updateOutputImagePlus(outputImagePlus, outputImageName);
		//BV3DBoxUtilities.adaptImageDisplay(inputImagePlus, outputImagePlus);	//not working ???
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
		if (labelSplitter != null) {
			labelSplitter.getCurrentCLIJ2Instance().close();			
		}
	}
	

}
