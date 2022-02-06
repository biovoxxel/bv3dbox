package de.biovoxxel.bv3dbox.gui;

import java.util.Arrays;
import java.util.List;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.module.MutableModuleItem;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_FlatFieldCorrection;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.WindowManager;
import net.imagej.updater.UpdateService;


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Filtering>Flat Field Correction (2D/3D)")
public class BV_FlatFieldCorrectionGUI extends DynamicCommand {

	@Parameter(required = true)
	ImagePlus originalImagePlus = null;
	
	@Parameter(required = true)
	ImagePlus flatFieldImagePlus = null;
	
	@Parameter(required = false, initializer = "initializeOriginalImageChoices")
	String darkFieldImageName = "";

	
	
	@Override
	public void run() {
		BV_FlatFieldCorrection bvffcorr = new BV_FlatFieldCorrection();
		
		ImagePlus darkFieldImagePlus = WindowManager.getImage(darkFieldImageName);
		
		bvffcorr.setImages(originalImagePlus, flatFieldImagePlus, darkFieldImagePlus);
		
		bvffcorr.flatFieldCorrection();
	}
	
	public void initializeOriginalImageChoices() {
		
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2");
		
		List<String> extendedImageList = Arrays.asList(BV3DBoxUtilities.extendImageTitleListWithNone());
		
		final MutableModuleItem<String> darkFieldImageName = getInfo().getMutableInput("darkFieldImageName", String.class);
		darkFieldImageName.setChoices(extendedImageList);
	
	}
	
}
