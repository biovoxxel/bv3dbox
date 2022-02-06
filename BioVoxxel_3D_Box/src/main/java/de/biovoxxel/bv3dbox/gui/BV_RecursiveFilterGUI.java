package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_RecursiveFilter;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.imagej.updater.UpdateService;


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Filtering>Recursive Filter (2D/3D)")
public class BV_RecursiveFilterGUI extends DynamicCommand {

	@Parameter
	UpdateService us;
	
	@Parameter(required = true, label = "Image", description = "", initializer = "checkUpdateSites")
	ImagePlus current_image_plus;
	
	@Parameter(required = true, label = "Filter", description = "", choices = {"Median", "Gaussian"})
	String filter_method = "Gaussian";
	
	@Parameter(required = true, label = "Radius", description = "", min = "0.5", max = "20.0", stepSize = "0.5")
	Double recursiveRadius = 1.0;
	
	@Parameter(required = true, label = "Iteration", description = "", stepSize = "10", max = "200")
	Integer iterations = 10;
	

	


	public void run() {
						
		BV_RecursiveFilter bvrf = new BV_RecursiveFilter(current_image_plus);
		
		ClearCLBuffer output_image = bvrf.runRecursiveFilter(filter_method, recursiveRadius, iterations);
		
		ImagePlus outputImage = bvrf.getCurrentCLIJ2Instance().pull(output_image);
		outputImage.setTitle(WindowManager.getUniqueName(current_image_plus.getTitle() + "_" + recursiveRadius + "_" + iterations + "x"));
		outputImage.show();
		
	}
	
	public void checkUpdateSites() {
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2");
	}
	
}
