/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;

import org.scijava.ItemVisibility;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.prefs.PrefService;
import org.scijava.widget.Button;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.StackProcessor;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
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
	
	@Parameter(label = "Invert Image", callback = "invertImage", required = false)
	private Button invertImage = null;

//	@Parameter(label = "Threshold library", choices = {"CLIJ2", "IJ"}, callback = "changeThresholdLibrary")
//	private String thresholdLibrary = "IJ";

	@Parameter(label = "Highlight ground truth", min = "0.00", max = "100.00", stepSize = "0.05", style = NumberWidget.SLIDER_STYLE, callback = "thresholdCheck", persist = false, required = false)
	private Double saturation = 0.00;
	
	@Parameter(label = "Toggle overlay", callback = "toggleThresholdLUT")
	Boolean showOverlay = true;
	
	@Parameter(label = "Histogram usage", choices = {"full (default)", "ignore black", "ignore white", "ignore both"}, callback = "thresholdCheck")
	private String histogramUsage = "full";
	
	@Parameter(label = "Auto Threshold", initializer = "thresholdMethodList", callback = "thresholdCheck", persist = false)
	private String thresholdMethod = "None";

	@Parameter(label = "Stack slice", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	private Integer stackSlice;
	
//	@Parameter(label = "Binary output style", choices = {"0/255", "Labels", "0/1"}, style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
//	private String outputImageStyle;
	
	@Parameter(label = "True / False Positive", persist = false, required=false, visibility = ItemVisibility.MESSAGE)
	private String tpfp = "";
	
	@Parameter(label = "True / False Negative", persist = false, required=false, visibility = ItemVisibility.MESSAGE)
	private String tnfn = "";
	
//	@Parameter(label = "Sensitivity", persist = false, required=false, visibility = ItemVisibility.MESSAGE)
//	private String sens = "";
//	
//	@Parameter(label = "Specificity", persist = false, required=false, visibility = ItemVisibility.MESSAGE)
//	private String spec = "";
	
	@Parameter(label = "Jaccard Index", persist = false, required=false, visibility = ItemVisibility.MESSAGE)
	private String jaccard = "";
	
	@Parameter(label = "Dice Coefficient", persist = false, required=false, visibility = ItemVisibility.MESSAGE)
	private String dice = "";
		
//	@Parameter(label = "Create Binary", callback = "createBinary", required = false)
//	private Button createBinary = null;


		
	CLIJ2 clij2;
	ClearCLBuffer inputImage;
	int bins = 256;
	
	byte[] red = new byte[bins];
	byte[] green = new byte[bins];
	byte[] blue = new byte[bins];

	//public double thresholdValue = 0.0;
	private LUT originalLut;
	private DecimalFormat df = new DecimalFormat("0.00000");

	private double truePositive;

	private double trueNegative;

	private double falsePositive;

	private double falseNegative;

//	private double sensitivity;
//
//	private double specificity;

	private double jaccardIndex;

	private double diceCoeff;
		
	
	
	public void run() {
	
		double thresholdValue = getThreshold();
		applyThreshold(thresholdValue);			

	}
	
//	public void createBinary() {
//		double thresholdValue = getThreshold();
//		applyThreshold(thresholdValue);	
//		
//	}
	
	
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
		
		double thresholdValue = 255.0;
		
//		if (thresholdLibrary.equals("CLIJ2") && !thresholdMethod.equals("None")) {
//			
//			thresholdValue = BV3DBoxUtilities.getThresholdValue(thresholdMethod, finalHistogram);
//			//thresholdValue = clij2.getAutomaticThreshold(inputImage, thresholdMethod);	
//			
//		} else 
		
		if (!thresholdMethod.equals("None")) {
			
			AutoThresholder autoThresholder = new AutoThresholder();
			
			if (thresholdMethod.equals("Huang2")) {

				thresholdValue = (double) BV3DBoxUtilities.calculateHuang2(finalHistogram);
				
			} else {
				
				thresholdValue = (double) autoThresholder.getThreshold(thresholdMethod, finalHistogram);
			}
		}
		
		thresholdValue = thresholdValue + 1; //correction to achieve same result as IJ 
		
		log.debug(thresholdMethod + " with value = " + thresholdValue + " displayed");
		return thresholdValue;
			
	}


	public void applyThresholdLUT(double thresholdValue) {
		
		LUT thresholdLUT = createLUT(thresholdValue);
		
		inputImagePlus.setLut(thresholdLUT);
		
		
	}
	
	public void toggleThresholdLUT() {
		if (showOverlay) {
			thresholdCheck();
		} else {
			inputImagePlus.setLut(originalLut);			
		}
	}

	
	
	
	public LUT createLUT(double thresholdValue) {
		
		double saturatedIntensity = saturation > 0.00 ? getSaturatedMaxIntentsity(saturation, thresholdValue) : 255.0;
		
		final MutableModuleItem<Boolean> mutableToggleOverlay = getInfo().getMutableInput("showOverlay", Boolean.class);
		mutableToggleOverlay.setValue(this, true);
		
		final MutableModuleItem<String> mutableTrueFalsePositive = getInfo().getMutableInput("tpfp", String.class);
		mutableTrueFalsePositive.setValue(this, ""+(int)truePositive + " / " + (int)falsePositive + " pixels");
		
		final MutableModuleItem<String> mutableTrueFalseNegative = getInfo().getMutableInput("tnfn", String.class);
		mutableTrueFalseNegative.setValue(this, ""+(int)trueNegative + " / " + (int)falseNegative + " pixels");
		
//		final MutableModuleItem<String> mutableSensitivity = getInfo().getMutableInput("sens", String.class);
//		mutableSensitivity.setValue(this, ""+sensitivity);
//		
//		final MutableModuleItem<String> mutableSpecificity = getInfo().getMutableInput("spec", String.class);
//		mutableSpecificity.setValue(this, ""+specificity);
				
		final MutableModuleItem<String> mutableJaccard = getInfo().getMutableInput("jaccard", String.class);
		mutableJaccard.setValue(this, "<html><b style=\"color: red;\">"+jaccardIndex + "</b></html>");
		
		final MutableModuleItem<String> mutableDice = getInfo().getMutableInput("dice", String.class);
		mutableDice.setValue(this, "<html><b style=\"color: red;\">"+diceCoeff + "</b></html>");
		
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
		log.debug("outputImage = " + outputImage);
		
//		if (inputImagePlus.getBitDepth() == 16) {
//			double minInt = inputImagePlus.getProcessor().getMin();
//			double maxInt = inputImagePlus.getProcessor().getMax();
//			thresholdValue = minInt + (thresholdValue * maxInt / 255.0);
//		}
//		log.debug("thresholdValue = " + thresholdValue);
		
		clij2.threshold(inputImage, outputImage, thresholdValue);
		
		String outputImageName = WindowManager.getUniqueName(thresholdMethod + "_" + inputImagePlus.getTitle());
		
		log.debug("outputImageName = " + outputImageName);
		
		inputImagePlus.setLut(originalLut);
		
		log.debug("creating 0/255 result");
		ImagePlus outputImagePlus = clij2.pullBinary(outputImage);
		outputImagePlus.setTitle(outputImageName);
		outputImagePlus.setCalibration(inputImagePlus.getCalibration());
		outputImagePlus.show();				
		
//TODO: in batch mode images are not correctly displayed
//			if (Interpreter.isBatchMode()) {
//				log.debug("Batch mode = " + Interpreter.isBatchMode());
//				BV3DBoxUtilities.addImagePlusToBatchModeImages(outputImagePlus);
//			}
		
		
		
//		old code for different output options (deprecated)
//		if (outputImageStyle.equals("0/255")) {
//			
//		} else if (outputImageStyle.equals("0/1")) {
//			log.debug("creating 0/1 result");
//			outputImage.setName(outputImageName);
//			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, outputImage, true, LutNames.GRAY, inputImagePlus.getCalibration());
//			
//		} else {
//			log.debug("creating label result");
//			ClearCLBuffer labelOutputImage = clij2.create(outputImage.getDimensions(), NativeTypeEnum.Float);
//			clij2.connectedComponentsLabelingBox(outputImage, labelOutputImage);
//			labelOutputImage.setName(outputImageName);
//			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, labelOutputImage, true, LutNames.GLASBEY_LUT, inputImagePlus.getCalibration());
//			labelOutputImage.close();
//			
//		}
		
		
		//cleanup
		inputImage.close();
		outputImage.close();
		clij2.clear();
		
	}
	
	
	
	@SuppressWarnings("unused")
	private void changeThresholdLibrary() {
		thresholdMethodList();
		thresholdCheck();
	}
	
	private void thresholdMethodList() {
		
		List<String> finalThresholdMethodList;
		
//		if (thresholdLibrary.equals("CLIJ2")) {
//			
//			finalThresholdMethodList =  Arrays.asList(AutoThresholderImageJ1.getMethods());
//			
//		} else { 
//		}	
			
		String[] thresholdMethodArray = AutoThresholder.getMethods();
		String[] extendedThresholdMethodArray = new String[thresholdMethodArray.length + 2];
		
		extendedThresholdMethodArray[0] = "None";
		System.arraycopy(thresholdMethodArray, 0, extendedThresholdMethodArray, 1, 2);
		extendedThresholdMethodArray[3] = "Huang2";
		System.arraycopy(thresholdMethodArray, 2, extendedThresholdMethodArray, 4, thresholdMethodArray.length-2);
		
		System.out.println(extendedThresholdMethodArray);
		
		finalThresholdMethodList = Arrays.asList(extendedThresholdMethodArray);
			
		
		final MutableModuleItem<String> thresholdMethod = getInfo().getMutableInput("thresholdMethod", String.class);
		thresholdMethod.setChoices(finalThresholdMethodList);
		
	}
	
	
	@SuppressWarnings("unused")
	private void slideSlices() {
	
		inputImagePlus.setSlice(stackSlice);
		
	}
	
	@SuppressWarnings("unused")
	private void imageSetup() {
				
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
		
		ImageProcessor inputImageProcessor = inputImagePlus.getProcessor();
		if (inputImageProcessor.getBitDepth() == 24) {
			cancel("RGB images are not supported by auto thresholding");
		}
				
		originalLut = inputImagePlus.getProcessor().getLut();

		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		inputImage = clij2.push(BV3DBoxUtilities.convertToGray8(inputImagePlus));
					
		log.debug(inputImagePlus.getTitle() + " pushed to GPU");
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		if(inputImagePlus.hasImageStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setMaximumValue(1);
			stackSlice.setValue(this, 1);
		}
	
		//thresholdCheck();

	}
	
	
	@SuppressWarnings("unused")
	private void invertImage() {
		
		inputImagePlus.setLut(originalLut);
		
		StackProcessor sp = new StackProcessor(inputImagePlus.getStack());
		sp.invert();
		
		imageSetup();
		
		thresholdCheck();
		
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
		
		truePositive = Math.min(saturatedPixelCount, foregroundPixelCount);
		log.debug("truePositive =" + truePositive);
		trueNegative = Math.min(backgroundPixelCount, (totalPixelCount - saturatedPixelCount));
		log.debug("trueNegative =" + trueNegative);
		falsePositive = Math.max(foregroundPixelCount - saturatedPixelCount, 0);
		log.debug("falsePositive =" + falsePositive);
		falseNegative = Math.max(saturatedPixelCount - foregroundPixelCount, 0);
		log.debug("falseNegative =" + falseNegative);
		
//		sensitivity = truePositive / (truePositive + falseNegative);
//		sensitivity = Math.max(sensitivity, 1.0);
//		log.debug("Sensitivity = " + df.format(sensitivity));
//		
//		specificity = trueNegative / (trueNegative + falsePositive);
//		specificity = Math.max(specificity, 1.0);
//		log.debug("Specificity = " + df.format(specificity));
		
		jaccardIndex = truePositive / (truePositive + falsePositive + falseNegative);
		log.debug("JaccardIndex = " + df.format(jaccardIndex));
		
		diceCoeff = (2 * truePositive) / (2 * truePositive + falsePositive + falseNegative);
		log.debug("DiceCoeff = " + df.format(diceCoeff));
		
		System.out.println("recalc dice = " + ((2*jaccardIndex) / (jaccardIndex + 1)));
		
//TODO: calculation of at least the specificity seems to be still not correct (at least for stacks)
		IJ.showStatus(thresholdMethod + "JaccardIndex=" + df.format(jaccardIndex) + " / DiceCoeff = " + df.format(diceCoeff));
		
		return saturationIntensity;
	}
	
	
		
	public void cancel() {
		System.out.println("ThresholdCheck closed/cancelled");
		inputImage.close();
		clij2.close();
		inputImagePlus.setLut(originalLut);
	}
	
}
