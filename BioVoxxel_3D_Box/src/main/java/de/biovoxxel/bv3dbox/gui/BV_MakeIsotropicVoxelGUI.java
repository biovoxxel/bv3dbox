package de.biovoxxel.bv3dbox.gui;

import javax.swing.JOptionPane;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_MakeIsotropicVoxel;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;

import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Segmentation>Make Voxel Isotropic")
public class BV_MakeIsotropicVoxelGUI implements Command {

	
	@Parameter(label = "Image", required = true)
	ImagePlus inputImagePlus;
	
	@Parameter(label = "Voxel size [units]", required = true)
	Float finalVoxelSize = 1f;
	
	
	@Override
	public void run() {
		
		if (inputImagePlus.hasImageStack()) {
			
			CLIJ2 clij2 = CLIJ2.getInstance();
			clij2.clear();
			BV_MakeIsotropicVoxel bvmii = new BV_MakeIsotropicVoxel(clij2, inputImagePlus);
			ClearCLBuffer isotropic_image = bvmii.makeIsotropic(clij2, inputImagePlus, finalVoxelSize);
			
//			double pixelSize = inputImagePlus.getCalibration().pixelWidth;
			
			ImagePlus isotropicImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, isotropic_image, true, LutNames.GRAY);
			isotropic_image.close();
						
			isotropicImagePlus.setTitle("iso_" + WindowManager.getUniqueName(inputImagePlus.getTitle()));
				
			Calibration cal = new Calibration();
			cal.pixelWidth = cal.pixelHeight = cal.pixelDepth = finalVoxelSize;
			cal.setUnit(inputImagePlus.getCalibration().getUnit());	
			
			isotropicImagePlus.setCalibration(cal);
			
			isotropicImagePlus.show();
			
			//BV3DBoxUtilities.addImagePlusToBatchModeImages(isotropicImagePlus);	//not solving the issue that in batch mode macros the output image is not displayed
						
			clij2.clear();
			
		} else {
			JOptionPane.showMessageDialog(null, "Works only on stacks", "Stack required", JOptionPane.WARNING_MESSAGE);
		}
	}

}
