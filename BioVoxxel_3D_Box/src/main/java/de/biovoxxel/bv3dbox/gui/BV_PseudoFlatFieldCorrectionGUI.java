package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;
import org.scijava.widget.NumberWidget;

import de.biovoxxel.bv3dbox.plugins.BVPseudoFlatFieldCorrection;
import ij.ImagePlus;
import ij.WindowManager;


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Pseudo Flat Field Correction (2D/3D)")
public class BVPseudoFlatFieldCorrectionGUI extends DynamicCommand {

	@Parameter(required = true, initializer = "setupImage")
	ImagePlus inputImagePlus;
	
	@Parameter(persist = false, label = "Blurring radius (sigma)", min = "0f", callback = "run")
	private Float flatFieldRadius = 0.0f;
	
	@Parameter(label = "Force 2D filter (saves memory)")
	Boolean force2DFilter = true;
	
	@Parameter(label = "Show background image", callback = "run")
	Boolean showBackgroundImage = true;
	
	@Parameter(persist = false, label = "Stack slice", initializer = "imageSetup", style = NumberWidget.SLIDER_STYLE, min = "1", callback = "slideSlices")
	Integer stackSlice;
	
	
	BVPseudoFlatFieldCorrection bvpffc;
	

	public void run() {
						
		bvpffc.runCorrection(flatFieldRadius, force2DFilter, showBackgroundImage);
		
	}
	
	@SuppressWarnings("unused")
	private void setupImage() {
		
				
		bvpffc = new BVPseudoFlatFieldCorrection(inputImagePlus);
		
		final MutableModuleItem<Integer> stackSlice = getInfo().getMutableInput("stackSlice", Integer.class);
		if(inputImagePlus.isStack()) {
			
			stackSlice.setMaximumValue(inputImagePlus.getStackSize());
			
		} else {
			
			stackSlice.setMaximumValue(1);
			
		}
	}
	
	@SuppressWarnings("unused")
	private void slideSlices() {
		ImagePlus outputImagePlus = WindowManager.getImage(bvpffc.getOutputImageName());
		
		if (outputImagePlus != null) {

			outputImagePlus.setSlice(stackSlice);
			
		}	
	}
	
	@Override
	public void cancel() {
		
		ImagePlus outputImagePlus = WindowManager.getImage(bvpffc.getOutputImageName());
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		
	}
	
	
}
