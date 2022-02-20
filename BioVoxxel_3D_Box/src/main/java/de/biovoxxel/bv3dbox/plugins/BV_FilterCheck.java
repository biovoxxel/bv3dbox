package de.biovoxxel.bv3dbox.plugins;

import org.scijava.command.DynamicCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.prefs.PrefService;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

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

//@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Filter Check (2D/3D)")
public class BV_FilterCheck extends DynamicCommand {

	
	@Parameter
	PrefService prefs;
	
	@Parameter
	LogService log;
	
	@Parameter(required = true, initializer = "setupImage")
	ImagePlus inputImagePlus;
	
	@Parameter(label = "Image Filter", choices = {"None", "Gaussian", "DoG", "Median", "Mean", "Minimum", "Maximum", "Open", "Close", "Top-Hat", "Bottom-Hat", "Variance", "Standard Dev", "Edges (Sobel)", "Tenengrad", "Laplace", "Log", "Exp" }, callback = "adaptFilterRadius")
	private String filterCheckMethod = "None";
		
	@Parameter(persist = false, label = "Blurring radius (sigma)", min = "0f", callback = "pseudoFlatFieldCorrection")
	private Float filterCheckRadius = 1.0f;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	
	CLIJ2 clij2;
	ClearCLBuffer inputImage;
	
	private ImagePlus outputImagePlus;
	@SuppressWarnings("unused")
	private double x_y_ratio;
	@SuppressWarnings("unused")
	private double z_x_ratio;
	
	
	public void run() {
		
	}
	
	public void filterCheck() {
		
		if (filterCheckMethod.equals("Gaussian")) {
			
		}
	}

	
	
	@SuppressWarnings("unused")
	private void setupImage() {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		readCalibration();
		
		if (inputImagePlus.getRoi() != null) {
			inputImage = clij2.pushCurrentSelection(inputImagePlus);
		} else {
			inputImage = clij2.push(inputImagePlus);			
		}
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setMaximumValue(1);
			stackSlice.setValue(null, 1);	//test		
		}
	}
	
	@SuppressWarnings("unused")
	private void slideSlices() {
	
		outputImagePlus.setSlice(stackSlice);
		
	}
	
	
	private void readCalibration() {
		Calibration cal = inputImagePlus.getCalibration();
		x_y_ratio = cal.pixelWidth / cal.pixelHeight;
		z_x_ratio = cal.pixelDepth / cal.pixelWidth;
			
	}
	
	
	@SuppressWarnings("unused")
	private void adaptFilterRadius() {
		
		final MutableModuleItem<Float> filterRadius = getInfo().getMutableInput("filterRadius", Float.class);
		filterRadius.setValue(this, 1f);
		if (filterCheckMethod.equals("Median")) {
			filterRadius.setMaximumValue(15f);
			
		} else {
			filterRadius.setMaximumValue(500f);
		}
		
		filterCheck();
	}
	
	
	public void cancel() {
		ImagePlus outputImagePlus = WindowManager.getImage("PFFC_" + inputImagePlus.getTitle());
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		clij2.close();
	}
	
	
}
