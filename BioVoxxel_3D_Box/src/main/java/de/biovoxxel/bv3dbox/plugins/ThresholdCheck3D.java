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
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import ij.ImagePlus;
import ij.process.AutoThresholder;
import ij.process.ImageProcessor;
import ij.process.LUT;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.AutoThresholderImageJ1;
import utilities.BV3DBoxSettings;
import utilities.BV3DBoxUtilities;
import utilities.BV3DBoxUtilities.LutNames;

/**
 * @author BioVoxxel
 *
 */
@Plugin(type = Command.class, menuPath = "BV3DBox>Threshold Check 2D/3D")
public class ThresholdCheck3D extends DynamicCommand {

	@Parameter
	PrefService prefs;
	
	@Parameter
	LogService log;
	
	
	@Parameter
	ImagePlus inputImagePlus;
	
	@Parameter(label = "Threshold library", choices = {"CLIJ2", "IJ"}, callback = "changeThresholdLibrary")
	String thresholdLibrary = "CLIJ2";
	
	@Parameter(label = "Auto Threshold", initializer = "thresholdMethodList", callback = "thresholdCheck", persist = true)
	String thresholdMethod = "Default";
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	@Parameter(label = "Contrast saturation (%)", min = "0.0", max = "100.0", stepSize = "0.1", style = NumberWidget.SLIDER_STYLE, callback = "thresholdCheck", persist = false)
	Double saturation = 0.0;

	@Parameter(label = "Binary output style", choices = {"0/255", "Labels", "0/1"}, style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE)
	String binaryOutputStyle;
	
	@Parameter(visibility = ItemVisibility.MESSAGE, callback = "thresholdCheck")
	String qualityIndicators = "";
		
	CLIJ2 clij2;
	ClearCLBuffer inputImage;
	int bins = 256;
	
	byte[] red = new byte[bins];
	byte[] green = new byte[bins];
	byte[] blue = new byte[bins];

	public double thresholdValue = 0.0;
	private LUT originalLut;
	private DecimalFormat df = new DecimalFormat("0.00000");
	private double sensitivity;
	private double specificity;
	
	/**
	 * 
	 */
	
	public void run() {
		applyThreshold();
	}
	
	public void thresholdCheck() {
		
		if (thresholdLibrary.equals("CLIJ2")) {
			
			thresholdValue = clij2.getAutomaticThreshold(inputImage, thresholdMethod);	
			
		} else if (thresholdLibrary.equals("IJ")) {
			
			AutoThresholder autoThresholder = new AutoThresholder();
			int[] histogram = inputImagePlus.getProcessor().getHistogram(256);
			thresholdValue = (double) autoThresholder.getThreshold(thresholdMethod, histogram);
			
		}
			
		LUT thresholdLUT = createLUT();
		
		inputImagePlus.setLut(thresholdLUT);
		
		log.debug(thresholdMethod + " with value = " + thresholdValue + " displayed");
	}
	
	public LUT createLUT() {
		
		double saturatedIntensity = saturation > 0.00 ? getSaturatedMaxIntentsity(saturation) : 255.0;
		double normalizedThresholdValue = getNormalizedThreshold();
		
		log.debug("normalizedThresholdValue =" + normalizedThresholdValue);
		
		for (int v = 0; v < bins; v++) {
			
			if ( v < normalizedThresholdValue) {
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

	
	public void applyThreshold() {
		
		ClearCLBuffer outputImage = clij2.create(inputImage);
		clij2.threshold(inputImage, outputImage, thresholdValue);
		
		if (binaryOutputStyle.equals("0/255")) {
			ImagePlus outputImagePlus = clij2.pullBinary(outputImage);
			outputImagePlus.setTitle(thresholdMethod + "_" + inputImagePlus.getTitle());
			outputImagePlus.show();
		} else if (binaryOutputStyle.equals("0/1")) {
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, outputImage, true, LutNames.GRAY);
		} else {
			ClearCLBuffer labelOutputImage = clij2.create(outputImage.getDimensions(), NativeTypeEnum.Float);
			
			clij2.connectedComponentsLabelingBox(outputImage, labelOutputImage);
			BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, labelOutputImage, true, LutNames.GRAY);
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
				
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "debug_level", LogLevel.INFO));
		
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
			
		}
	
		thresholdCheck();

	}


	
	public double getSaturatedMaxIntentsity(double percentSaturation) {
		
		percentSaturation = percentSaturation > 100 ? 100 : percentSaturation;
		
		//int totalPixelCount = (inputImagePlus.getWidth() * inputImagePlus.getHeight() * inputImagePlus.getNSlices());
		double totalPixelCount = (double) inputImage.getVolume();
		log.debug("totalPixelCount =" + totalPixelCount);
		
		double acceptedSaturatedPixelCount = totalPixelCount / 100 * percentSaturation;
		double normalizedThresholdValue = getNormalizedThreshold();
		
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
			
			if (intensity >= normalizedThresholdValue) {
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
		
		sensitivity = truePositive / (truePositive + falseNegative);
		specificity = trueNegative / (trueNegative + falsePositive);
		
		qualityIndicators = "Sensitivity=" + df.format(sensitivity) + " / Specificity = " + df.format(specificity);
		log.debug(qualityIndicators);
		
		return saturationIntensity;
	}
	
	
	private double getNormalizedThreshold() {
		int bitDepth = inputImagePlus.getBitDepth();
		return thresholdValue * 255 / (Math.pow(2, bitDepth) - 1.0);
	}
	
	public void cancel() {
		inputImagePlus.setLut(originalLut);
	}
	
}
