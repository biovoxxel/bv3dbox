package de.biovoxxel.bv3dbox.gui;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BVFlatFieldCorrection;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.WindowManager;

//menuPath = "Plugins>BioVoxxel 3D Box>Flat Field Correction (2D/3D)"
@Plugin(type = Command.class)
public class BVFlatFieldCorrectionGUI extends DynamicCommand {

	@Parameter(required = true)
	ImagePlus originalImagePlus = null;
	
	@Parameter(required = true)
	ImagePlus flatFieldImagePlus = null;
	
	@Parameter(required = false, initializer = "initializeOriginalImageChoices")
	String darkFieldImageName = "";

	
	
	@Override
	public void run() {
		BVFlatFieldCorrection bvffcorr = new BVFlatFieldCorrection();
		
		ImagePlus darkFieldImagePlus = WindowManager.getImage(darkFieldImageName);
		
		bvffcorr.setImages(originalImagePlus, flatFieldImagePlus, darkFieldImagePlus);
		
		bvffcorr.flatFieldCorrection();
	}
	
	public void initializeOriginalImageChoices() {
		
		List<String> extendedImageList = Arrays.asList(BV3DBoxUtilities.extendImageTitleListWithNone());
		
		final MutableModuleItem<String> darkFieldImageName = getInfo().getMutableInput("darkFieldImageName", String.class);
		darkFieldImageName.setChoices(extendedImageList);
	
	}
	
}
