/**
 * 
 */
package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.plugins.BV_LabelSplitter;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.imagej.updater.UpdateService;

/**
 * @author BioVoxxel
 *
 */

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Labels>Label Splitter (2D/3D)")
public class BV_LabelSplitterGUI extends DynamicCommand {

	@Parameter(required = true, initializer = "setupImage")
	private ImagePlus inputImagePlus;
	
	@Parameter(label = "Separation method", choices = {"Maxima", "Eroded box", "Eroded sphere"}, callback = "processImage")
	private String separationMethod = "Maxima";
	
	@Parameter(label = "Spot sigma", min = "0f", callback = "processImage")
	private Float spotSigma;
	
	@Parameter(label = "Maxima detection radius", min = "0f", callback = "processImage")
	private Float maximaRadius;
	
	@Parameter(label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	private BV_LabelSplitter labelSplitter;
	private String outputImageName; 
	
	
	public void run() {
		if (labelSplitter != null) {
			labelSplitter.getCurrentCLIJ2Instance().close();			
		}
	}
	
	
	
	public void setupImage() {
		
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2");
		
		if (inputImagePlus.getProcessor().isBinary()) {
			
			labelSplitter = new BV_LabelSplitter(inputImagePlus);
			
			outputImageName = WindowManager.getUniqueName("Split_" + inputImagePlus.getTitle());
			System.out.println(outputImageName);
			
			final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
			
			if(inputImagePlus.isStack()) {
				
				stackSlice.setMaximumValue(inputImagePlus.getStackSize());	
				
			} else {
				
				stackSlice.setMaximumValue(1);
				stackSlice.setValue(this, 1);
			}
			
		} else {
			cancel("The input image needs to be of type binary");
		}
	}
	
	
	public void processImage() {
		
		ClearCLBuffer splitted_label_image = labelSplitter.splitLabels(separationMethod, spotSigma, maximaRadius);
		
		ImagePlus outputImagePlus = BV3DBoxUtilities.pullImageFromGPU(labelSplitter.getCurrentCLIJ2Instance(), splitted_label_image, false, LutNames.GLASBEY_LUT);
		splitted_label_image.close();
		
		BV3DBoxUtilities.updateOutputImagePlus(outputImagePlus, outputImageName);
		
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
		if (labelSplitter != null) {
			labelSplitter.getCurrentCLIJ2Instance().close();			
		}
	}
	

}
