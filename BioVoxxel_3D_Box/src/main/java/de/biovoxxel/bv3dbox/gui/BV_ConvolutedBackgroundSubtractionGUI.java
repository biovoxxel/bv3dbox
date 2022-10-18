package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.Button;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.plugins.BV_ConvolutedBackgroundSubtraction;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Filtering>Convoluted Background Subtraction (2D/3D)")
public class BV_ConvolutedBackgroundSubtractionGUI extends DynamicCommand {
	
	BV_ConvolutedBackgroundSubtraction bvcbs;
	CLIJ2 clij2;
	
	@Parameter(required = true, label = "Image", description = "", initializer = "setup")
	ImagePlus currentImagePlus;
	
	@Parameter(required = true, label = "Filter", description = "", choices = {"Gaussian", "Median", "Mean", "Open"}, callback = "processImageOnTheFly")
	String filterMethod = "Gaussian";
	
	@Parameter(required = true, label = "Radius", description = "", min = "0.5", stepSize = "0.5", callback = "processImageOnTheFly")
	Float filterRadius = 1.0f;
	
	@Parameter(label = "Force 2D filtering", callback = "processImageOnTheFly")
	Boolean force2DFiltering = true;
	
	@Parameter(label = "Stack slice", initializer = "sliderSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices", required = false)
	private Integer stackSlice = 1;
	
	@Parameter(label = "On the fly mode", required = false)
	private Boolean processOnTheFly = false;
	
	@Parameter(label = "Preview", callback = "processImage", required = false)
	private Button previewButton;
	
	
	public void run() {
						
		if (getOutputImage() == null) {
			processImage();				
		} else {
			//just keep the output image open without further action
		}
		
	}
	
	
	@SuppressWarnings("unused")
	private void setup() {
		
		bvcbs = new BV_ConvolutedBackgroundSubtraction(currentImagePlus);
		clij2 = bvcbs.getCLIJ2Instance();
	}
	
	@SuppressWarnings("unused")
	private void processImageOnTheFly() {
		if (processOnTheFly) {
			processImage();
		}
	}
	
	
	private void processImage() {
		
		adaptFilter();
		
		ClearCLBuffer input_image = bvcbs.getInputBuffer();
		
		ClearCLBuffer filtered_image = bvcbs.filterImage(input_image, filterMethod, filterRadius, force2DFiltering);
		
		//BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, filtered_image, true, LutNames.GRAY);
		
		ClearCLBuffer output_image = bvcbs.subtractBackground(input_image, filtered_image);
		
		ImagePlus tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, output_image, false, LutNames.GRAY);
		
		String outputImageName = "BVCBS_" + currentImagePlus.getTitle();
		ImagePlus outputImagePlus = WindowManager.getImage("BVCBS_" + currentImagePlus.getTitle());			
		
		if (outputImagePlus == null) {
			outputImagePlus = new ImagePlus();
		}
		
		outputImagePlus.setImage(tempOutputImagePlus);
		outputImagePlus.setTitle(outputImageName);
		outputImagePlus.show();
		
		BV3DBoxUtilities.adaptImageDisplay(currentImagePlus, WindowManager.getImage("BVCBS_" + currentImagePlus.getTitle()));
	}
	
	
	private void adaptFilter() {
		
		final MutableModuleItem<Float> mutableFilterRadius = getInfo().getMutableInput("filterRadius", Float.class);
			
		if (filterMethod.equals("Median")) {
			
			if (filterRadius > 15) {
				mutableFilterRadius.setValue(this, 15f);
			}
			mutableFilterRadius.setMaximumValue(15f);
			
		} else {
			mutableFilterRadius.setMaximumValue(1000f);
		}
		
	}
	
	
	private ImagePlus getOutputImage() {
		
		return WindowManager.getImage("BVCBS_" + currentImagePlus.getTitle());
	}
	
	public void cancel(String reason) {
		
		ImagePlus outputImagePlus = getOutputImage();
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		clij2.close();
		
	}
	
	@SuppressWarnings("unused")
	private void slideSlices() {
		ImagePlus outputImagePlus = getOutputImage();
		
		if (outputImagePlus != null) {

			outputImagePlus.setSlice(stackSlice);
			
		}	
	}
	
	@SuppressWarnings("unused")
	private void sliderSetup() {
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		
		stackSlice.setValue(this, currentImagePlus.getSlice());
		if(currentImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(currentImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setMaximumValue(1);
		}
	}
}
