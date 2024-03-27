package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_DifferenceOfGaussian;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import net.imagej.updater.UpdateService;

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Filtering>Difference of Gaussian (2D/3D)")
public class BV_DifferenceOfGaussianGUI extends DynamicCommand {
	
	@Parameter
	UpdateService us;
	
	@Parameter(required = true, label = "Image", description = "", initializer = "checkUpdateSites")
	ImagePlus current_image_plus;
	
	@Parameter(required = true, label = "Sigma 1", description = "Radius of the first Gaussian Blur filter", min = "0.1", stepSize = "0.1")
	Double x_radius = 1.0;
	
	@Parameter(required = true, label = "Sigma 2", description = "Radius of the second Gaussian Blur filter", min = "0.1", stepSize = "0.1")
	Double y_radius = 1.0;
	
	@Parameter(required = true, label = "Filter in 2D only")
	Boolean filter2DOnly = true;
	
	
	public void run() {
		
		BV_DifferenceOfGaussian bvdog = new BV_DifferenceOfGaussian(current_image_plus);
		
		CLIJ2 clij2 = bvdog.getCurrentCLIJ2Instance();
		
		ClearCLBuffer dog_output_image = bvdog.runDoGFilter(x_radius, y_radius, filter2DOnly);
		
		ImagePlus outputImage = BV3DBoxUtilities.pullImageFromGPU(clij2, dog_output_image, false, LutNames.GRAY);
		
		outputImage.setCalibration(current_image_plus.getCalibration());
		outputImage.setTitle(WindowManager.getUniqueName("DoG_" + current_image_plus.getTitle()));
		
		outputImage.show();
		outputImage.setLut(current_image_plus.getProcessor().getLut());
		outputImage.getProcessor().setMinAndMax(clij2.getMinimumOfAllPixels(dog_output_image), clij2.getMaximumOfAllPixels(dog_output_image));
		outputImage.updateAndDraw();
		
		
		clij2.close();
	}
	
	
	public void checkUpdateSites() {
		BV3DBoxUtilities.displayMissingDependencyWarning(getContext().service(UpdateService.class), "clij,clij2,clijx-assistant,clijx-assistant-extensions,3D ImageJ Suite");
	}
	
}
