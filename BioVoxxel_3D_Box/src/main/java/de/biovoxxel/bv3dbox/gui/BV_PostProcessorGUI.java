package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.plugins.BVPostProcessor;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Post Processor (2D/3D)")	
public class BVPostProcessorGUI extends DynamicCommand {

	@Parameter(required = true, initializer = "setupImage")
	ImagePlus inputImagePlus;
	
	@Parameter(label = "Processing method", choices = {"Median (sphere, max r=15)", "Median (box, max r=15)", "Erode (sphere)", "Erode (box)", "Dilate (sphere)", "Dilate (box)", "Open (sphere)", "Open (box)", "Close (sphere)", "Close (box)", "Fill holes (labels)", "Variance (sphere)", "Variance (box)"}, callback = "processImage")
	String method = "Erode";
	
	@Parameter(label = "Iterations", min = "0", stepSize = "1", callback = "processImage")
	Integer iterations = 1;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	BVPostProcessor bvpp;

	private ImagePlus outputImagePlus;
	private String outputImageName;
	
	public void run() {
			
		bvpp.getInputBuffer().close();
		CLIJ2 clij2 = bvpp.getCLIJ2Instance();
		clij2.clear();
		clij2.close();
	}

	
	@SuppressWarnings("unused")
	private void setupImage() {
		bvpp = new BVPostProcessor(inputImagePlus);
		
		outputImageName = "BVPP_" + inputImagePlus.getTitle();
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setMaximumValue(1);
			
		}
	}
	
	public void processImage() {
		
		ClearCLBuffer outputBuffer = bvpp.postProcessor(method, iterations);
		
		ImagePlus tempImagePlus = bvpp.getImagePlus(outputBuffer);
		outputBuffer.close();
		
		outputImagePlus = WindowManager.getImage(outputImageName);
		
		if (outputImagePlus == null) {
			outputImagePlus = new ImagePlus();
		}
		outputImagePlus.setImage(tempImagePlus);
		outputImagePlus.setTitle(WindowManager.getUniqueName("BVPP_" + inputImagePlus.getTitle()));
		outputImagePlus.getProcessor().resetMinAndMax();
		outputImagePlus.show();
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
		CLIJ2 clij2 = bvpp.getCLIJ2Instance();
		clij2.clear();
		clij2.close();
	}
	
}
