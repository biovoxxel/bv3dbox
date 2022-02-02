package de.biovoxxel.bv3dbox.gui;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BVRecursiveFilter;
import ij.ImagePlus;


@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Recursive Filter (2D/3D)")
public class RecursiveFilterGUI extends DynamicCommand {

	@Parameter(required = true, label = "Image", description = "")
	ImagePlus current_image_plus;
	
	@Parameter(required = true, label = "Filter", description = "", choices = {"Median", "Gaussian"})
	String filter_method = "Gaussian";
	
	@Parameter(required = true, label = "Radius", description = "", min = "0.5", max = "20.0", stepSize = "0.5")
	Double recursiveRadius = 1.0;
	
	@Parameter(required = true, label = "Iteration", description = "", stepSize = "10", max = "200")
	Integer iterations = 10;
	

	


	public void run() {
		
		BVRecursiveFilter bvrf = new BVRecursiveFilter(current_image_plus);
		
		bvrf.runRecursiveFilter(filter_method, recursiveRadius, iterations);
		
	}
}
