package de.biovoxxel.bv3dbox.gui;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.plugins.BV_LabelSplitter;
import de.biovoxxel.bv3dbox.plugins.BV_VoronoiThresholdLabeling;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.AutoThresholderImageJ1;
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

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Segmentation>Voronoi Threshold Labler (2D/3D)")
public class BV_VoronoiThresholdLabelingGUI extends DynamicCommand {

	
	@Parameter(required = true, initializer = "setupImage")
	private ImagePlus inputImagePlus;
	
	@Parameter(label = "Image filter", choices = { "None", "Gaussian", "DoG", "Median", "Mean", "Open", "Close", "Variance", "Tubeness", "Inverted Tubeness" }, callback = "adaptFilter")
	private String filterMethod = "Gaussian";
	
	@Parameter(label = "Filter radius", min = "0f", max = "1000f", callback = "adaptFilter")
	private Float filterRadius = 1.0f;
	
	@Parameter(label = "Background subtraction", choices = {"None", "DoG", "DoM", "TopHat", "BottomHat", "Inverted Tubeness"}, callback = "adaptBackground")
	private String backgroundSubtractionMethod;
	
	@Parameter(label = "Background radius", min = "0f", max = "1000f", callback = "adaptBackground")
	private Float backgroundRadius = 1.0f;
		
	@Parameter(label = "Threshold method", initializer = "thresholdMethodList", callback = "processImage")
	private String thresholdMethod = "Default";
	
	@Parameter(label = "Histogram usage", choices = {"full", "ignore black", "ignore white", "ignore both"}, callback = "processImage")
	private String histogramUsage = "full";
	
	@Parameter(label = "Separation method", choices = {"None", "Maxima", "Eroded Maxima", "DoG Seeds", "Eroded box", "Eroded sphere"}, callback = "processImage")
	private String separationMethod = "Maxima";
	
	@Parameter(label = "Spot sigma / Erosion", min = "0f", callback = "processImage")
	private Float spotSigma;
	
	@Parameter(label = "Maxima detection radius", min = "0f", callback = "processImage")
	private Float maximaRadius;
	
	@Parameter(label = "Output type", choices = {"Labels", "Binary"}, style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, callback = "processImage")
	private String outputType;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	@Parameter(label = "Apply on complete image")
	Boolean applyOnCompleteImage = false;
	
	
	BV_VoronoiThresholdLabeling bvvtl = new BV_VoronoiThresholdLabeling();
	BV_LabelSplitter labelSplitter;
	
	private ClearCLBuffer input_image;
	
	private String priorFilterMethod;
	private String priorBackgroundMethod;
	
	private int[] stackHistogram;
	
	
	public void run() {
						
		if (inputImagePlus.getRoi() != null && applyOnCompleteImage) {
			
			bvvtl.getOutputImage().close();
			inputImagePlus.killRoi();
			setupImage();
			processImage();
			
		} else {
			
			if (bvvtl.getOutputImage() == null) {
				setupImage();
				processImage();				
			} else {
				//just keep the output image open without further action
			}
			
		}
		
		bvvtl.getInputImageAsClearClBuffer().close();
		bvvtl.getCurrentCLIJ2Instance().close();
	}
	
	
	private void setupImage() {
		
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2");
		
		bvvtl.setupInputImage(inputImagePlus);
		input_image = bvvtl.getInputImageAsClearClBuffer();
		
		
		labelSplitter = new BV_LabelSplitter(bvvtl.getCurrentCLIJ2Instance());
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		
		stackSlice.setValue(this, 1);
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setMaximumValue(1);
		}
		
		if (inputImagePlus.getRoi() != null) {
			stackHistogram = BV3DBoxUtilities.getHistogram(inputImagePlus);			
		}
	}


	
	
	@SuppressWarnings("unused")
	private void thresholdMethodList() {
		String[] thresholdMethodString = AutoThresholderImageJ1.getMethods();
		
		List<String> thresholdMethodList = Arrays.asList(thresholdMethodString);
		
		final MutableModuleItem<String> thresholdMethod = getInfo().getMutableInput("thresholdMethod", String.class);
		thresholdMethod.setChoices(thresholdMethodList);
	}
	
	
	
	
	@SuppressWarnings("unused")
	private void adaptFilter() {
		
		final MutableModuleItem<Float> mutableFilterRadius = getInfo().getMutableInput("filterRadius", Float.class);
		
		if(!filterMethod.equals(priorFilterMethod)) {
			mutableFilterRadius.setValue(this, 1f);
			priorFilterMethod = filterMethod;
		}
				
		
		if (filterMethod.equals("Median")) {
			mutableFilterRadius.setMaximumValue(15f);
			
		} else {
			mutableFilterRadius.setMaximumValue(1000f);
		}
		
		processImage();
	}


	
	@SuppressWarnings("unused")
	private void adaptBackground() {
		
		final MutableModuleItem<Float> mutableBackgroundRadius = getInfo().getMutableInput("backgroundRadius", Float.class);
		
		if (!backgroundSubtractionMethod.equals(priorBackgroundMethod)) {
			mutableBackgroundRadius.setValue(this, 1f);
			priorBackgroundMethod = backgroundSubtractionMethod;
		}
		if (backgroundSubtractionMethod.equals("DoM")) {
			mutableBackgroundRadius.setMaximumValue(15f);
		} else {
			mutableBackgroundRadius.setMaximumValue(1000f);
		}
		
		processImage();
	}
	
	
	private void processImage() {
	
		ClearCLBuffer filtered_image = bvvtl.filterImage(input_image, filterMethod, filterRadius);
		ClearCLBuffer background_subtracted_image = bvvtl.backgroundSubtraction(filtered_image, backgroundSubtractionMethod, backgroundRadius);
		filtered_image.close();
		
		double thresholdValue = 0.0;
		
		if (inputImagePlus.getRoi() == null) {
			
			thresholdValue = BV3DBoxUtilities.getThresholdValue(bvvtl.getCurrentCLIJ2Instance(), thresholdMethod, background_subtracted_image, histogramUsage);
			
		} else {
			
			int[] finalHistogram = stackHistogram.clone();
			//System.out.println("initial stackHistogram extremes =" + finalHistogram[0] + " / " + finalHistogram[stackHistogram.length-1]);
			
			switch(histogramUsage) {
			
			case "ignore black":
				finalHistogram[0] = 0;
				break;
				
			case "ignore white":
				finalHistogram[stackHistogram.length-1] = 0;
				break;
				
			case "ignore both":
				finalHistogram[0] = 0;
				finalHistogram[stackHistogram.length-1] = 0;
				break;
				
			default:
				break;
			}
			//System.out.println("final stackHistogram extremes =" + finalHistogram[0] + " / " + finalHistogram[stackHistogram.length-1]);
			
			thresholdValue = BV3DBoxUtilities.getThresholdValue(thresholdMethod, finalHistogram);
		}
		
		
		
		ClearCLBuffer thresholded_image = BV3DBoxUtilities.thresholdImage(bvvtl.getCurrentCLIJ2Instance(), background_subtracted_image, thresholdValue);		
		background_subtracted_image.close();
				
		ClearCLBuffer seed_image = bvvtl.getCurrentCLIJ2Instance().create(input_image);
		
		if (separationMethod.equals("None")) {
			seed_image = thresholded_image;
		} else if (separationMethod.equals("Maxima")) {
			seed_image = labelSplitter.detectMaxima(input_image, spotSigma, maximaRadius);		
		} else if (separationMethod.equals("Eroded Maxima")) {
			seed_image = labelSplitter.detectErodedMaxima(input_image, Math.round(spotSigma), maximaRadius);
		} else if (separationMethod.equals("DoG Seeds")) {
			CLIJ2 clij2 = bvvtl.getCurrentCLIJ2Instance();
			ClearCLBuffer binary_8_bit_image = clij2.create(thresholded_image);
			clij2.replaceIntensity(thresholded_image, binary_8_bit_image, 1, 255);
			seed_image = labelSplitter.detectDoGSeeds(binary_8_bit_image, spotSigma, maximaRadius);
			binary_8_bit_image.close();
		} else {
			seed_image = labelSplitter.createErodedSeeds(thresholded_image, Math.round(spotSigma), separationMethod);
		}
		
		
		ClearCLBuffer output_image = labelSplitter.createLabels(seed_image, thresholded_image);
		
		thresholded_image.close();
		seed_image.close();
		bvvtl.createOutputImage(output_image, outputType);
		output_image.close();
	}
	
	
	@SuppressWarnings("unused")
	private void slideSlices() {
		ImagePlus outputImagePlus = WindowManager.getImage(bvvtl.getOutputImageName());
		
		if (outputImagePlus != null) {

			outputImagePlus.setSlice(stackSlice);
			
		}	
	}
	
	@Override
	public void cancel() {
		
		ImagePlus outputImagePlus = WindowManager.getImage(bvvtl.getOutputImageName());
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		
		bvvtl.getCurrentCLIJ2Instance().close();
		
	}

}