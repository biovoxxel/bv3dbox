package de.biovoxxel.bv3dbox.plugins;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

public class BV_DifferenceOfGaussian {

	ImagePlus inputImagePlus;
	private ClearCLBuffer input_image;
	private Calibration cal;
	
	int finalIteration = 0;
	double lastDifference = Double.NEGATIVE_INFINITY;
	
	private CLIJ2 clij2;
	private double x_y_ratio;
	private double z_x_ratio;
	//private double z_y_ratio;
	private int z_slices;
	
	
	public BV_DifferenceOfGaussian(ImagePlus inputImagePlus) {
		setupImage(inputImagePlus);
	}
	
	
	private void setupImage(ImagePlus image) {
		this.inputImagePlus = image;
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		cal = inputImagePlus.getCalibration();
		x_y_ratio = cal.pixelWidth / cal.pixelHeight;
		z_x_ratio = cal.pixelDepth / cal.pixelWidth;
		//z_y_ratio = cal.pixelDepth / cal.pixelHeight;
		
		z_slices = inputImagePlus.getNSlices();
		
		if (inputImagePlus.getRoi() != null) {
			input_image = clij2.pushCurrentSelection(inputImagePlus);
		} else {
			input_image = clij2.push(inputImagePlus);
		}
		
	}
	
	
	public ClearCLBuffer runDoGFilter(double radius_1, double radius_2, boolean limitTo2D) {
		
		ClearCLBuffer output_image = clij2.create(input_image);
		if (z_slices == 1) {
			clij2.differenceOfGaussian2D(input_image, output_image, radius_1, radius_1 * x_y_ratio, radius_2, radius_2 * x_y_ratio);			
		} else {
			if (limitTo2D) {
				clij2.differenceOfGaussian3D(input_image, output_image, radius_1, radius_1 * x_y_ratio, 0.0, radius_2, radius_2 * x_y_ratio, 0.0);			
				
			} else {
				clij2.differenceOfGaussian3D(input_image, output_image, radius_1, radius_1 * x_y_ratio, radius_1 / z_x_ratio, radius_2, radius_2 * x_y_ratio, radius_2 / z_x_ratio);			
			}
		}
		
		return output_image;
		
	}
	
	
	public CLIJ2 getCurrentCLIJ2Instance() {
		return clij2;
		
	}
	
}
