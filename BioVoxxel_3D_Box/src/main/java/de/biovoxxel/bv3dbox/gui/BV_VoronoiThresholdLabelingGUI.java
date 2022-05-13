package de.biovoxxel.bv3dbox.gui;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
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
import net.haesleinhuepf.clijx.plugins.BinaryFillHolesSliceBySlice;
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
	private String filterMethod = "None";
	
	@Parameter(label = "Filter radius", min = "0f", max = "1000f", callback = "processImageOnTheFly")
	private Float filterRadius = 1.0f;
	
	@Parameter(label = "Background subtraction", choices = {"None", "DoG", "DoM", "TopHat", "BottomHat", "Inverted Tubeness"}, callback = "adaptBackground")
	private String backgroundSubtractionMethod = "None";
	
	@Parameter(label = "Background radius", min = "0f", max = "1000f", callback = "processImageOnTheFly")
	private Float backgroundRadius = 1.0f;
		
	@Parameter(label = "Histogram usage", choices = {"full", "ignore black", "ignore white", "ignore both"}, callback = "processImageOnTheFly")
	private String histogramUsage = "full";
	
	@Parameter(label = "Threshold method", initializer = "thresholdMethodList", callback = "processImageOnTheFly")
	private String thresholdMethod = "Default";
	
	@Parameter(label = "Fill Holes", choices = {"Off","2D","3D"}, callback = "processImageOnTheFly")
	private String fillHoles = "Off";
	
	@Parameter(label = "Separation method", choices = {"None", "Maxima", "Eroded Maxima", "EDM Maxima", "Maxima Spheres", "DoG Seeds", "Eroded box", "Eroded sphere"}, callback = "processImageOnTheFly")
	private String separationMethod = "Maxima";
	
	@Parameter(label = "Spot sigma / Erosion", min = "0f", callback = "processImageOnTheFly")
	private Float spotSigma = 0f;
	
	@Parameter(label = "Maxima detection radius", min = "0f", callback = "processImageOnTheFly")
	private Float maximaRadius = 0f;
	
	@Parameter(label = "Volume range", min = "0f")
	private String volumeRange = "0-Infinity";
	
	@Parameter(label = "Exclude on edges", callback = "processImageOnTheFly")
	private Boolean excludeOnEdges = false;

	@Parameter(label = "Output type", choices = {"Labels", "Binary", "Outlines"}, style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, callback = "processImage")
	private String outputType = "Labels";
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices", required = false)
	private Integer stackSlice = 1;
	
	@Parameter(label = "Apply on complete image")
	private Boolean applyOnCompleteImage = false;
	
	@Parameter(label = "On the fly mode", required = false)
	private Boolean processOnTheFly = false;
	
	@Parameter(label = "Preview", callback = "processImage", required = false)
	private Button previewButton;
	
	
	BV_VoronoiThresholdLabeling bvvtl = new BV_VoronoiThresholdLabeling();
	BV_LabelSplitter labelSplitter;
	
	private CLIJ2 clij2;
	
	private ClearCLBuffer input_image;
	
//	private String priorFilterMethod;
//	private String priorBackgroundMethod;
	
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
		
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2,clijx-assistant,clijx-assistant-extensions,3D ImageJ Suite");
		
		bvvtl.setupInputImage(inputImagePlus);
		input_image = bvvtl.getInputImageAsClearClBuffer();
		
		clij2 = bvvtl.getCurrentCLIJ2Instance();
		
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
		
//		if(!filterMethod.equals(priorFilterMethod)) {
//			mutableFilterRadius.setValue(this, 1f);
//			priorFilterMethod = filterMethod;
//		}
				
		
		if (filterMethod.equals("Median")) {
			
			mutableFilterRadius.setValue(this, 1f);
			mutableFilterRadius.setMaximumValue(15f);
			
		} else {
			mutableFilterRadius.setMaximumValue(1000f);
		}
		
	}


	
	@SuppressWarnings("unused")
	private void adaptBackground() {
		
		final MutableModuleItem<Float> mutableBackgroundRadius = getInfo().getMutableInput("backgroundRadius", Float.class);
		
//		if (!backgroundSubtractionMethod.equals(priorBackgroundMethod)) {
//			mutableBackgroundRadius.setValue(this, 1f);
//			priorBackgroundMethod = backgroundSubtractionMethod;
//		}
		
		if (backgroundSubtractionMethod.equals("DoM")) {
			mutableBackgroundRadius.setValue(this, 1f);
			mutableBackgroundRadius.setMaximumValue(15f);
		} else {
			mutableBackgroundRadius.setMaximumValue(1000f);
		}
		
	}
	
//TODO: delete if button recordability is officially functional
//	private void adaptVolumeMin() {
//		final MutableModuleItem<Float> mutableVolumeMin = getInfo().getMutableInput("minVolume", Float.class);
//		
//		if (minVolume > maxVolume) {
//			mutableVolumeMin.setValue(this, maxVolume);			
//		}
//		
//		processImage();
//		
//	}
//	
//	private void adaptVolumeMax() {
//		final MutableModuleItem<Float> mutableVolumeMax = getInfo().getMutableInput("maxVolume", Float.class);
//		
//		if (maxVolume < minVolume) {
//			mutableVolumeMax.setValue(this, minVolume);			
//		}
//		
//		processImage();
//		
//	}
	
	@SuppressWarnings("unused")
	private void processImageOnTheFly() {
		if (processOnTheFly) {
			processImage();
		}
	}
		
	private void processImage() {
	
		ClearCLBuffer filtered_image = bvvtl.filterImage(input_image, filterMethod, filterRadius);
		ClearCLBuffer background_subtracted_image = bvvtl.backgroundSubtraction(filtered_image, backgroundSubtractionMethod, backgroundRadius);
		filtered_image.close();
		
		double thresholdValue = 0.0;
		
		if (inputImagePlus.getRoi() == null) {
			
			thresholdValue = BV3DBoxUtilities.getThresholdValue(clij2, thresholdMethod, background_subtracted_image, histogramUsage);
			
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
		
		
		
		ClearCLBuffer thresholded_image = BV3DBoxUtilities.thresholdImage(clij2, background_subtracted_image, thresholdValue);		
		background_subtracted_image.close();
				
		
		switch (fillHoles) {
		
		case "2D":			
			ClearCLBuffer temp_fill_slice_holes_image = clij2.create(thresholded_image);
			clij2.copy(thresholded_image, temp_fill_slice_holes_image);
			BinaryFillHolesSliceBySlice.binaryFillHolesSliceBySlice(clij2, temp_fill_slice_holes_image, thresholded_image);
			temp_fill_slice_holes_image.close();
			
			break;
			
		case "3D":
			ClearCLBuffer temp_fill_holes_image = clij2.create(thresholded_image);
			clij2.copy(thresholded_image, temp_fill_holes_image);
			clij2.binaryFillHoles(temp_fill_holes_image, thresholded_image);
			temp_fill_holes_image.close();
			break;
			
		default:
			break;
		}
		
		
		
		ClearCLBuffer seed_image;
		
		switch (separationMethod) {
		
		case "None":
			seed_image = thresholded_image;
			break;
		case "Maxima":
			seed_image = labelSplitter.detectMaxima(input_image, spotSigma, maximaRadius);
			break;
		case "Eroded Maxima":
			seed_image = labelSplitter.detectErodedMaxima(input_image, Math.round(spotSigma), maximaRadius);
			break;
		case "EDM Maxima":
			seed_image = labelSplitter.detectDistanceMapMaxima(thresholded_image, maximaRadius);
			break;
		case "Maxima Spheres":
			seed_image = labelSplitter.createMaximaSpheres(thresholded_image, spotSigma, maximaRadius);
			break;
		case "DoG Seeds":
			
			ClearCLBuffer binary_8_bit_image = clij2.create(thresholded_image);
			clij2.replaceIntensity(thresholded_image, binary_8_bit_image, 1, 255);
			seed_image = labelSplitter.detectDoGSeeds(binary_8_bit_image, spotSigma, maximaRadius);
			binary_8_bit_image.close();
			break;
		default:
			seed_image = labelSplitter.createErodedSeeds(thresholded_image, Math.round(spotSigma), separationMethod);
			break;
		}
		
		
		ClearCLBuffer output_image = labelSplitter.createLabels(seed_image, thresholded_image);
		
		if (!volumeRange.equalsIgnoreCase("0-infinity")) {
			
			ClearCLBuffer size_limited_temp_image = clij2.create(output_image);
			clij2.copy(output_image, size_limited_temp_image);

			
			float minVolume = BV3DBoxUtilities.getMinFromRange(volumeRange);
			float maxVolume = BV3DBoxUtilities.getMaxFromRange(volumeRange);
			
			clij2.excludeLabelsOutsideSizeRange(size_limited_temp_image, output_image, minVolume, maxVolume); 
			size_limited_temp_image.close();
		}
		
		if (excludeOnEdges) {
			ClearCLBuffer excluded_on_edges_image = clij2.create(output_image);
			clij2.copy(output_image, excluded_on_edges_image);
			clij2.excludeLabelsOnEdges(excluded_on_edges_image, output_image);
			excluded_on_edges_image.close();
		}
		
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