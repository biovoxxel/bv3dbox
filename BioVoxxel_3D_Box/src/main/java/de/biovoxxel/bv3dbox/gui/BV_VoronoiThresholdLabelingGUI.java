package de.biovoxxel.bv3dbox.gui;

import java.util.Arrays;
import java.util.List;

import org.joml.Math;
import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.ChoiceWidget;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.plugins.BV_VoronoiThresholdLabeling;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.plugins.AutoThresholderImageJ1;
import net.imagej.updater.UpdateService;


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Segmentation>Voronoi Threshold Labler (2D/3D)")
public class BV_VoronoiThresholdLabelingGUI extends DynamicCommand {

	
	@Parameter(required = true, initializer = "setupImage")
	private ImagePlus inputImagePlus;
	
	@Parameter(label = "Image filter", choices = {"None", "Gaussian", "DoG", "Median", "Mean", "Open", "Close", "Variance"}, callback = "adaptFilter")
	private String filterMethod = "Gaussian";
	
	@Parameter(label = "Filter radius", min = "0f", max = "100f", callback = "adaptFilter")
	private Float filterRadius = 1.0f;
	
	@Parameter(label = "Background subtraction", choices = {"None", "DoG", "DoM", "TopHat", "BottomHat"}, callback = "adaptBackground")
	private String backgroundSubtractionMethod;
	
	@Parameter(label = "Background radius", min = "0f", max = "100f", callback = "adaptBackground")
	private Float backgroundRadius = 1.0f;
	
	@Parameter(label = "Threshold method", initializer = "thresholdMethodList", callback = "processImage")
	private String thresholdMethod = "Default";
	
	@Parameter(label = "Separation method", choices = {"Maxima", "Eroded box", "Eroded sphere"}, callback = "processImage")
	private String separationMethod = "Maxima";
	
	@Parameter(label = "Spot sigma", min = "0f", callback = "processImage")
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
	
	private ClearCLBuffer input_image;
	
	private String priorFilterMethod;
	private String priorBackgroundMethod;
	
	
	@Override
	public void run() {
				
		if (inputImagePlus.getRoi() != null && applyOnCompleteImage) {
			bvvtl.getOutputImage().close();
			inputImagePlus.killRoi();
			setupImage();
			processImage();
			bvvtl.getInputImageAsClearClBuffer().close();
			bvvtl.getCurrentCLIJ2Instance().close();
		} else {
			bvvtl.getInputImageAsClearClBuffer().close();
			bvvtl.getCurrentCLIJ2Instance().close();
		}
	}
	
	
	private void setupImage() {
		
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2");
		
		bvvtl.setupInputImage(inputImagePlus);
		input_image = bvvtl.getInputImageAsClearClBuffer();
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());	
			
		} else {
			
			stackSlice.setMaximumValue(1);
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
			mutableFilterRadius.setMaximumValue(200f);
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
			mutableBackgroundRadius.setMaximumValue(200f);
		}
		
		processImage();
	}
	
	
	private void processImage() {
		ClearCLBuffer filteredImage = bvvtl.filterImage(input_image, filterMethod, filterRadius);
		ClearCLBuffer backgroundSubtractedImage = bvvtl.backgroundSubtraction(filteredImage, backgroundSubtractionMethod, backgroundRadius);
		filteredImage.close();
		ClearCLBuffer thresholdedImage = bvvtl.thresholdImage(backgroundSubtractedImage, thresholdMethod);
		backgroundSubtractedImage.close();
		
		ClearCLBuffer seedImage = bvvtl.getCurrentCLIJ2Instance().create(input_image);
		if (separationMethod.equals("Maxima")) {
			seedImage = bvvtl.detectMaxima(input_image, spotSigma, maximaRadius);		
		} else {
			seedImage = bvvtl.createErodedSeeds(thresholdedImage, Math.round(spotSigma), separationMethod);
		}
		ClearCLBuffer outputImage = bvvtl.createLabels(seedImage, thresholdedImage);
		thresholdedImage.close();
		seedImage.close();
		bvvtl.createOutputImage(outputImage, outputType);
		outputImage.close();
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
		
	}

}
