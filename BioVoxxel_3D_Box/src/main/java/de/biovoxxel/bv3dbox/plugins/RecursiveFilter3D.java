/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import org.scijava.command.Command;
import org.scijava.command.DynamicCommand;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import ij.ImagePlus;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

/**
 * @author BioVoxxel
 *
 */
@Plugin(type = Command.class, menuPath = "BV3DBox>Recursive Filter")
public class RecursiveFilter3D extends DynamicCommand {

	@Parameter(required = true, label = "", description = "")
	ImagePlus current_image_plus;
	
	@Parameter(required = true, label = "", description = "", choices = {"Median", "Gaussian"})
	String filter_method = "Gaussian";
	
	@Parameter(required = true, label = "", description = "", min = "0.5", max = "20.0", stepSize = "0.5")
	Double radius = 1.0;
	
	@Parameter(required = true, label = "", description = "", stepSize = "10", max = "200")
	Integer iterations = 10;
	
	
	
	ClearCLBuffer difference_check;
	int finalIteration = 0;
	double lastDifference = Double.NEGATIVE_INFINITY;
	
	@Override
	public void run() {
		runRecursiveMedian();
	}

	
	/**
	 * applies a median filter of a given radius (max = 4 px) a specified number of times (iterations) recursively on an image.
	 * The plugin decides automatically between 2D or 3D filtering dependent on the input image.  
	 * Uncalibrated images are filtered isotropically in all dimensions. Calibrated images are filtered in relation to their
	 * x/y, x/z, and y/z calibrated aspect ratio to avoid dimensional distortion artifacts.
	 */
	public void runRecursiveMedian() {
		CLIJ2 clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		Calibration cal = current_image_plus.getCalibration();
		double x_y_ratio = cal.pixelWidth / cal.pixelHeight;
		double z_x_ratio = cal.pixelDepth / cal.pixelWidth;
		double z_y_ratio = cal.pixelDepth / cal.pixelHeight;
		
		int z_slices = current_image_plus.getNSlices();
		
		ClearCLBuffer current_image = clij2.push(current_image_plus);
		ClearCLBuffer temp_image = clij2.create(current_image);
		difference_check = clij2.create(current_image);
		
		finalIteration = iterations;
		
		if (z_slices == 1 ) {
			
			int fullIterations = 1;
			double finalRadius = 0;
			if (radius > 4.0) {
				fullIterations = (int) Math.floor(radius / 4);
				finalRadius = radius - (fullIterations * 4);	
			}
			
			for (int i = 0; i < iterations; i++) {
				
				for (int j = 0; j < fullIterations; j++) {
					
					clij2.copy(current_image, temp_image);
					clij2.median2DSphere(temp_image, current_image, radius, (radius * x_y_ratio));
					
				}
				if (isLowDifference(clij2, temp_image, current_image)) {
					finalIteration = i;
					break;
				}
			}
			
			if (finalRadius != 0) {
				clij2.copy(current_image, temp_image);
				clij2.median2DSphere(temp_image, current_image, finalRadius, (finalRadius * x_y_ratio));
				
			}
		
			
			if (filter_method.equals("Gaussian")) {
				
				for (int i = 0; i < iterations; i++) {
					
					clij2.copy(current_image, temp_image);
					clij2.gaussianBlur2D(temp_image, current_image, radius, (radius * x_y_ratio));
					
					if (isLowDifference(clij2, temp_image, current_image)) {
						finalIteration = i;
						break;
					}
				}
			}			
		} else {
			
			if (filter_method.equals("Median")) {
			
				int fullIterations = 1;
				double finalRadius = 0;
				if (radius > 4.0) {
					fullIterations = (int) Math.floor(radius / 4);
					finalRadius = radius - (fullIterations * 4);	
				}
				
				for (int i = 0; i < iterations; i++) {
					
					for (int j = 0; j < fullIterations; j++) {
						
						clij2.copy(current_image, temp_image);
						clij2.median3DSphere(temp_image, current_image, radius, (radius * x_y_ratio), radius / z_x_ratio);
						
					}

					if (isLowDifference(clij2, temp_image, current_image)) {
						finalIteration = i;
						break;
					}
				}
				
				if (finalRadius != 0) {
					clij2.copy(current_image, temp_image);
					clij2.median3DSphere(temp_image, current_image, (finalRadius * z_x_ratio), (finalRadius * z_y_ratio), finalRadius);					
				}
			}
			
			if (filter_method.equals("Gaussian")) {
				
				for (int i = 0; i < iterations; i++) {
					
					clij2.copy(current_image, temp_image);
					clij2.gaussianBlur3D(temp_image, current_image, radius, (radius * x_y_ratio), radius / z_x_ratio);
									
					if (isLowDifference(clij2, temp_image, current_image)) {
						finalIteration = i;
						break;
					}		
				}
			}
		}
		
		ImagePlus output_image = clij2.pull(current_image);
		output_image.setTitle(current_image_plus.getTitle() + "_" + radius + "_" + finalIteration + "x");
		output_image.show();
		
		current_image.close();
		temp_image.close();
		clij2.clear();
		
	}
	
	
	private boolean isLowDifference(CLIJ2 clij2, ClearCLBuffer img1, ClearCLBuffer img2) {
		clij2.absoluteDifference(img1, img2, difference_check);
		double currentDifference = clij2.meanOfAllPixels(difference_check);
		
		System.out.println("lastDifference=" + lastDifference + " / currentDifference=" + currentDifference);
		boolean lowDifference = false;
		if (currentDifference - lastDifference == 0) {
			lowDifference = true;
		}
		lastDifference = currentDifference;
		return lowDifference;
	}

	
}
