/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.AutoThresholderImageJ1;

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
 *
 */
@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Segmentation>Threshold Check (2D/3D)")
public class BV_ThresholdCheck extends DynamicCommand {

	@Parameter
	PrefService prefs;
	
	@Parameter
	LogService log;
	
	
	@Parameter(label = "Input image", initializer = "imageSetup")
	ImagePlus inputImagePlus;
	
	@Parameter(label = "Threshold library", choices = {"CLIJ2", "IJ"}, callback = "changeThresholdLibrary")
	String thresholdLibrary = "CLIJ2";
	
	@Parameter(label = "Histogram usage", choices = {"full (default)", "ignore black", "ignore white", "ignore both"}, callback = "thresholdCheck")
	private String histogramUsage = "full";
	
	@Parameter(label = "Auto Threshold", initializer = "thresholdMethodList", callback = "thresholdCheck", persist = true)
	String thresholdMethod = "Default";
	
	@Parameter(label = "Contrast saturation (%)", min = "0.00", max = "100.00", stepSize = "0.05", style = NumberWidget.SLIDER_STYLE, callback = "thresholdCheck", persist = false, required = false)
	Double saturation = 0.00;

	@Parameter(label = "Stack slice", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	@Parameter(label = "Binary output style", choices = {"0/255", "Labels", "0/1"}, style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	String outputImageStyle;
	
		
	CLIJ2 clij2;
	ClearCLBuffer inputImage;
	int bins = 256;
	
	byte[] red = new byte[bins];
	byte[] green = new byte[bins];
	byte[] blue = new byte[bins];

	//public double thresholdValue = 0.0;
	private LUT originalLut;
	private DecimalFormat df = new DecimalFormat("0.00000");
		
	/**
	 * 
	 */
	
	public void run() {
		
		double thresholdValue = getThreshold();
		applyThreshold(thresholdValue);
		
	}
	
	
	public void thresholdCheck() {
		double thresholdValue = getThreshold();
		
		applyThresholdLUT(thresholdValue);
		
	}
	
	public double getThreshold() {
		
		int[] stackHistogram = BV3DBoxUtilities.getHistogram(inputImagePlus);
		
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
		}
		//System.out.println("final stackHistogram extremes =" + finalHistogram[0] + " / " + finalHistogram[stackHistogram.length-1]);
		
		double thresholdValue = 0.0;
		
		if (thresholdLibrary.equals("CLIJ2")) {
			
			thresholdValue = BV3DBoxUtilities.getThresholdValue(thresholdMethod, finalHistogram);
			//thresholdValue = clij2.getAutomaticThreshold(inputImage, thresholdMethod);	
			
		} else if (thresholdLibrary.equals("IJ")) {
			
			AutoThresholder autoThresholder = new AutoThresholder();
			//int[] histogram = inputImagePlus.getProcessor().getHistogram(256);
			thresholdValue = (double) autoThresholder.getThreshold(thresholdMethod, finalHistogram);

		}
		
		log.debug(thresholdMethod + " with value = " + thresholdValue + " displayed");
		return thresholdValue;
			
	}


	public void applyThresholdLUT(double thresholdValue) {
		
		LUT thresholdLUT = createLUT(thresholdValue);
		
		inputImagePlus.setLut(thresholdLUT);
		
		
	}

	
	
	
	public LUT createLUT(double thresholdValue) {
		
		double saturatedIntensity = saturation > 0.00 ? getSaturatedMaxIntentsity(saturation, thresholdValue) : 255.0;
		
		for (int v = 0; v < bins; v++) {
			
			if ( v < thresholdValue) {
				red[v] = (byte) 0;
				blue[v] = (byte) 255;
			} else {
				red[v] = (byte) 255;
				blue[v] = (byte) 0;
			}
			
			green[v] = v < saturatedIntensity ? (byte) v : (byte) 255;

		}
		
		LUT thresholdLut = new LUT(red, green, blue);
		return thresholdLut;
	}

	
	public void applyThreshold(double thresholdValue) {
		
		ClearCLBuffer outputImage = clij2.create(inputImage);
		clij2.threshold(inputImage, outputImage, thresholdValue);
		
		String outputImageName = WindowManager.getUniqueName(thresholdMethod + "_" + inputImagePlus.getTitle());
		
		if (outputImageStyle.equals("0/255")) {
			
			ImagePlus outputImagePlus = clij2.pullBinary(outputImage);
			outputImagePlus.setTitle(outputImageName);
			outputImagePlus.show();
			
		} else if (outputImageStyle.equals("0/1")) {
			
			outputImage.setName(outputImageName);
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, outputImage, true, LutNames.GRAY);
			
		} else {
			
			ClearCLBuffer labelOutputImage = clij2.create(outputImage.getDimensions(), NativeTypeEnum.Float);
			clij2.connectedComponentsLabelingBox(outputImage, labelOutputImage);
			labelOutputImage.setName(outputImageName);
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, labelOutputImage, true, LutNames.GLASBEY_LUT);
			labelOutputImage.close();
			
		}
		
		inputImagePlus.setLut(originalLut);
		
		//cleanup
		inputImage.close();
		outputImage.close();
		clij2.close();
		
	}
	
	
	
	@SuppressWarnings("unused")
	private void changeThresholdLibrary() {
		thresholdMethodList();
		thresholdCheck();
	}
	
	private void thresholdMethodList() {
		
		String[] thresholdMethodString;
		
		if (thresholdLibrary.equals("CLIJ2")) {
			thresholdMethodString = AutoThresholderImageJ1.getMethods();
		} else { 
			thresholdMethodString = AutoThresholder.getMethods();
		}
		
		List<String> thresholdMethodList = Arrays.asList(thresholdMethodString);
		
		final MutableModuleItem<String> thresholdMethod = getInfo().getMutableInput("thresholdMethod", String.class);
		thresholdMethod.setChoices(thresholdMethodList);
		
	}
	
	
	@SuppressWarnings("unused")
	private void slideSlices() {
	
		inputImagePlus.setSlice(stackSlice);
		
	}
	
	@SuppressWarnings("unused")
	private void imageSetup() {
				
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		originalLut = inputImagePlus.getProcessor().getLut();
				
		inputImage = clij2.push(inputImagePlus);
		log.debug(inputImagePlus.getTitle() + "pushed to GPU");
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setMaximumValue(1);
			stackSlice.setValue(this, 1);
		}
	
		//thresholdCheck();

	}


	
	public double getSaturatedMaxIntentsity(double percentSaturation, double thresholdValue) {
		
		percentSaturation = percentSaturation > 100 ? 100 : percentSaturation;
		
		//int totalPixelCount = (inputImagePlus.getWidth() * inputImagePlus.getHeight() * inputImagePlus.getNSlices());
		double totalPixelCount = (double) inputImage.getVolume();
		log.debug("totalPixelCount =" + totalPixelCount);
		
		double acceptedSaturatedPixelCount = totalPixelCount / 100 * percentSaturation;
		
		ImageProcessor inputProcessor = inputImagePlus.getProcessor();
		int[] histogram = inputProcessor.getHistogram(256);
		
		double saturatedPixelCount = 0;
		double foregroundPixelCount = 0;
		double saturationIntensity = 255;
		for (int intensity = 255; intensity >= 0; intensity--) {
	
			if (saturatedPixelCount <= acceptedSaturatedPixelCount) {
				saturatedPixelCount += histogram[intensity];
				saturationIntensity = intensity;
			} 
			
			if (intensity >= thresholdValue) {
				foregroundPixelCount += histogram[intensity];
			}
		}
		log.debug("foregroundPixelCount =" + foregroundPixelCount);
		
		double backgroundPixelCount = totalPixelCount - foregroundPixelCount;
		log.debug("backgroundPixelCount =" + backgroundPixelCount);
		
		double truePositive = Math.min(saturatedPixelCount, foregroundPixelCount);
		log.debug("truePositive =" + truePositive);
		double trueNegative = Math.min(backgroundPixelCount, (totalPixelCount - saturatedPixelCount));
		log.debug("trueNegative =" + trueNegative);
		double falsePositive = Math.max(foregroundPixelCount - saturatedPixelCount, 0);
		log.debug("falsePositive =" + falsePositive);
		double falseNegative = Math.max(saturatedPixelCount - foregroundPixelCount, 0);
		log.debug("falseNegative =" + falseNegative);
		
		double sensitivity = truePositive / (truePositive + falseNegative);
		log.debug("Sensitivity = " + df.format(sensitivity));
		
		double specificity = trueNegative / (trueNegative + falsePositive);
		log.debug("Specificity = " + df.format(specificity));
		
		double jaccardIndex = truePositive / (truePositive + falsePositive + falseNegative);
		log.debug("JaccardIndex = " + df.format(jaccardIndex));
		
		double diceCoeff = (2 * truePositive) / (2 * truePositive + falsePositive + falseNegative);
		log.debug("DiceCoeff = " + df.format(diceCoeff));
		
		System.out.println("recalc dice = " + ((2*jaccardIndex) / (jaccardIndex + 1)));
		
//TODO: calculation of at least the specificity seems to be still not correct (at least for stacks)
		IJ.showStatus(thresholdMethod + "(" + thresholdLibrary + "): JaccardIndex=" + df.format(jaccardIndex) + " / DiceCoeff = " + df.format(diceCoeff));
		
		return saturationIntensity;
	}
	
	
		
	public void cancel() {
		inputImagePlus.setLut(originalLut);
	}
	
}
