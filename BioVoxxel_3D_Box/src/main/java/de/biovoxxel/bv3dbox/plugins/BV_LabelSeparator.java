/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

/**
 * @author Admin
 *
 */

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Labels>Separate Labels (2D/3D)")	
public class BV_LabelSeparator implements Command {
	
	@Parameter(required = true, initializer = "setupImage")
	ImagePlus inputImagePlus;

	
	private CLIJ2 clij2;
	
	@Override
	public void run() {
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		ClearCLBuffer input_image = clij2.push(inputImagePlus);
		ClearCLBuffer splitted_label_image = clij2.create(input_image);
		
		splitLabels(clij2, input_image, splitted_label_image);
		input_image.close();
		
		ImagePlus outputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, splitted_label_image, true, LutNames.GLASBEY_LUT);
		
		outputImagePlus.setTitle(WindowManager.getUniqueName("BVSL_" + inputImagePlus.getTitle()));
		
		outputImagePlus.show();
		
		clij2.clear();
	}
	
	
	public BV_LabelSeparator() {
		
	}
	
	public void splitLabels(CLIJ2 clij2, ClearCLBuffer label_image, ClearCLBuffer splitted_label_image) {
		
//		boolean is3D = label_image.getDimension() > 2 ? true : false;
		
		ClearCLBuffer dilated_image = clij2.create(label_image);
		
		clij2.dilateLabels(label_image, dilated_image, 1);
		
//		if (is3D) {
//			clij2.maximum3DSphere(label_image, dilated_image, 1, 1, 1);
//		} else {
//			clij2.maximum2DSphere(label_image, dilated_image, 1, 1);
//		}
						
		ClearCLBuffer edge_image = clij2.create(dilated_image);
		clij2.reduceLabelsToLabelEdges(dilated_image, edge_image);
		
		clij2.subtractImages(dilated_image, edge_image, splitted_label_image);
		edge_image.close();
	
	}
	
	
}
