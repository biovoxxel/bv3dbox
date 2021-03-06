/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import org.scijava.Cancelable;

import ij.ImagePlus;
import ij.measure.Calibration;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

/*
 * BSD 3-Clause License
 *
 * Copyright (c) 2022, Jan Brocher (BioVoxxel)
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * 3. Neither the name of the copyright holder nor the names of its
 * contributors may be used to endorse or promote products derived from
 * this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * 
 * Please cite BioVoxxel according to the provided DOI related to this software.
 * 
 */

/**
 * @author BioVoxxel
 *
 */

public class BV_RecursiveFilter implements Cancelable {

	ImagePlus inputImagePlus;
	private ClearCLBuffer input_image;
	ClearCLBuffer difference_check;
	
	int finalIteration = 0;
	double lastDifference = Double.NEGATIVE_INFINITY;
	
	private CLIJ2 clij2;
	private double x_y_ratio;
	private double z_x_ratio;
	private double z_y_ratio;
	private int z_slices;
	
	
	
	public BV_RecursiveFilter(ImagePlus inputImagePlus) {
		setupImage(inputImagePlus);
	}
	
		
	private void setupImage(ImagePlus image) {
		this.inputImagePlus = image;
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		Calibration cal = inputImagePlus.getCalibration();
		x_y_ratio = cal.pixelWidth / cal.pixelHeight;
		z_x_ratio = cal.pixelDepth / cal.pixelWidth;
		z_y_ratio = cal.pixelDepth / cal.pixelHeight;
		
		z_slices = inputImagePlus.getNSlices();
		
		if (inputImagePlus.getRoi() != null) {
			input_image = clij2.pushCurrentSelection(inputImagePlus);
		} else {
			input_image = clij2.push(inputImagePlus);
		}
		
	}
	
	
	public CLIJ2 getCurrentCLIJ2Instance() {
		return clij2;
		
	}
	
	
	/**
	 * applies a median filter of a given radius (max = 4 px) a specified number of times (iterations) recursively on an image.
	 * The plugin decides automatically between 2D or 3D filtering dependent on the input image.  
	 * Uncalibrated images are filtered isotropically in all dimensions. Calibrated images are filtered in relation to their
	 * x/y, x/z, and y/z calibrated aspect ratio to avoid dimensional distortion artifacts.
	 */
	public ClearCLBuffer runRecursiveFilter(String filterMethod, double radius, int iterations) {
		
		ClearCLBuffer temp_image = clij2.create(input_image);
		difference_check = clij2.create(input_image);
		
		finalIteration = iterations;
		
		if (z_slices == 1) {
			
			int fullIterations = 1;
			double finalRadius = 0;
			if (radius > 4.0) {
				fullIterations = (int) Math.floor(radius / 4);
				finalRadius = radius - (fullIterations * 4);	
			}
			
			for (int i = 0; i < iterations; i++) {
				
				for (int j = 0; j < fullIterations; j++) {
					
					clij2.copy(input_image, temp_image);
					clij2.median2DSphere(temp_image, input_image, radius, (radius * x_y_ratio));
					
				}
				if (isLowDifference(clij2, temp_image, input_image)) {
					finalIteration = i;
					break;
				}
			}
			
			if (finalRadius != 0) {
				clij2.copy(input_image, temp_image);
				clij2.median2DSphere(temp_image, input_image, finalRadius, (finalRadius * x_y_ratio));
				
			}
		
			
			if (filterMethod.equals("Gaussian")) {
				
				for (int i = 0; i < iterations; i++) {
					
					clij2.copy(input_image, temp_image);
					clij2.gaussianBlur2D(temp_image, input_image, radius, (radius * x_y_ratio));
					
					if (isLowDifference(clij2, temp_image, input_image)) {
						finalIteration = i;
						break;
					}
				}
			}			
		} else {
			
			if (filterMethod.equals("Median")) {
			
				int fullIterations = 1;
				double finalRadius = 0;
				if (radius > 4.0) {
					fullIterations = (int) Math.floor(radius / 4);
					finalRadius = radius - (fullIterations * 4);	
				}
				
				for (int i = 0; i < iterations; i++) {
					
					for (int j = 0; j < fullIterations; j++) {
						
						clij2.copy(input_image, temp_image);
						clij2.median3DSphere(temp_image, input_image, radius, (radius * x_y_ratio), radius / z_x_ratio);
						
					}

					if (isLowDifference(clij2, temp_image, input_image)) {
						finalIteration = i;
						break;
					}
				}
				
				if (finalRadius != 0) {
					clij2.copy(input_image, temp_image);
					clij2.median3DSphere(temp_image, input_image, (finalRadius * z_x_ratio), (finalRadius * z_y_ratio), finalRadius);					
				}
			}
			
			if (filterMethod.equals("Gaussian")) {
				
				for (int i = 0; i < iterations; i++) {
					
					clij2.copy(input_image, temp_image);
					clij2.gaussianBlur3D(temp_image, input_image, radius, (radius * x_y_ratio), radius / z_x_ratio);
									
					if (isLowDifference(clij2, temp_image, input_image)) {
						finalIteration = i;
						break;
					}		
				}
			}
		}
		
		temp_image.close();

		return input_image;		
	}
	
	
	private boolean isLowDifference(CLIJ2 clij2, ClearCLBuffer img1, ClearCLBuffer img2) {
		clij2.absoluteDifference(img1, img2, difference_check);
		double currentDifference = clij2.meanOfAllPixels(difference_check);
		
		boolean lowDifference = false;
		if (currentDifference - lastDifference == 0) {
			lowDifference = true;
		}
		lastDifference = currentDifference;
		return lowDifference;
	}


	@Override
	public boolean isCanceled() {
		// TODO Auto-generated method stub
		return false;
	}


	@Override
	public void cancel(String reason) {
		// TODO Auto-generated method stub
		
	}


	@Override
	public String getCancelReason() {
		// TODO Auto-generated method stub
		return null;
	}

	
}
