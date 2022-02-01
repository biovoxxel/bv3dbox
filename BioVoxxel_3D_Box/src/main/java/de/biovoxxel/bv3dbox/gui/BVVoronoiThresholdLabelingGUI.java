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

import de.biovoxxel.bv3dbox.plugins.BVVoronoiThresholdLabeling;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.plugins.AutoThresholderImageJ1;

//menuPath = "Plugins>BioVoxxel 3D Box>Voronoi Threshold Labler (2D/3D)"
@Plugin(type = Command.class)
public class BVVoronoiThresholdLabelingGUI extends DynamicCommand {

	
	@Parameter(required = true, initializer = "setupImage")
	private ImagePlus inputImagePlus;
	
	@Parameter(label = "Image Filter", choices = {"None", "Gaussian", "DoG", "Median", "Mean", "Open", "Close", "Variance"}, callback = "adaptFilter")
	private String filterMethod = "Gaussian";
	
	@Parameter(label = "", min = "0f", max = "100f", callback = "adaptFilter")
	private Float filterRadius = 1.0f;
	
	@Parameter(label = "Background Subtraction", choices = {"None", "DoG", "DoM", "TopHat", "BottomHat"}, callback = "adaptBackground")
	private String backgroundSubtractionMethod;
	
	@Parameter(label = "Background radius", min = "0f", max = "100f", callback = "adaptBackground")
	private Float backgroundRadius = 1.0f;
	
	@Parameter(label = "Threshold method", initializer = "thresholdMethodList", callback = "processImage")
	private String thresholdMethod;
	
	@Parameter(label = "Spot sigma", min = "0f", callback = "processImage")
	private Float spotSigma;
	
	@Parameter(label = "Maxima detection radius", min = "0f", callback = "processImage")
	private Float maximaRadius;
	
	@Parameter(label = "Output type", choices = {"Labels", "Binary"}, style = ChoiceWidget.RADIO_BUTTON_HORIZONTAL_STYLE, callback = "processImage")
	private String outputType;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	@Parameter
	Boolean applyOnCompleteImage = false;
	
	
	BVVoronoiThresholdLabeling bvvtl;
	
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
		}
	}
	
	
	private void setupImage() {
		
		bvvtl = new BVVoronoiThresholdLabeling(inputImagePlus);
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
		ClearCLBuffer maximaImage = bvvtl.detectMaxima(input_image, spotSigma, maximaRadius);
		ClearCLBuffer outputImage = bvvtl.createLabels(maximaImage, thresholdedImage);
		thresholdedImage.close();
		maximaImage.close();
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