/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import org.joml.Math;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
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
public class BV_LabelSplitter {

	private CLIJ2 clij2;
	private ClearCLBuffer input_image;
	
	private double[] voxelRatios;
		
	/**
	 * Separates segmented labels 
	 */
	public BV_LabelSplitter(ImagePlus inputImagePlus) {

		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		input_image = clij2.push(inputImagePlus);
		
		voxelRatios = BV3DBoxUtilities.getVoxelRelations(inputImagePlus);
		
	}

	
	public ClearCLBuffer splitLabels(String separationMethod, Float spotSigma, Float maximaRadius) {
		
		ClearCLBuffer seedImage = clij2.create(input_image);
		
		ClearCLBuffer thresholdedImage = clij2.create(input_image);
		clij2.threshold(input_image, thresholdedImage, 1);
		
		if (separationMethod.equals("Maxima")) {
			seedImage = detectMaxima(input_image, spotSigma, maximaRadius);		
		} else if (separationMethod.equals("Eroded Maxima")) {
			seedImage = detectErodedMaxima(input_image, Math.round(spotSigma), maximaRadius);
		} else {
			seedImage = createErodedSeeds(thresholdedImage, Math.round(spotSigma), separationMethod);
		}
		
		return createLabels(seedImage, thresholdedImage);
	}
	
	
	public ClearCLBuffer detectMaxima(ClearCLBuffer input_image, Float spotSigma, Float maximaRadius) {
			
		double y_filter_sigma = spotSigma * voxelRatios[0];
		double z_filter_sigma = spotSigma / voxelRatios[1];
			
		ClearCLBuffer temp = clij2.create(input_image);
		clij2.gaussianBlur3D(input_image, temp, spotSigma, y_filter_sigma, z_filter_sigma);
		
		
		double y_maxima_radius = maximaRadius * voxelRatios[0];
		double z_maxima_radius = maximaRadius / voxelRatios[1];
		
		ClearCLBuffer maxima_image = clij2.create(input_image);
		clij2.detectMaxima3DBox(temp, maxima_image, maximaRadius, y_maxima_radius, z_maxima_radius);
		temp.close();
		
		return maxima_image;
	}

	
	public ClearCLBuffer detectErodedMaxima(ClearCLBuffer input_image, Integer erode_iteration, Float maximaRadius) {
		
		ClearCLBuffer eroded_seeds = createErodedSeeds(input_image, erode_iteration, "Eroded sphere");
		
		ClearCLBuffer eroded_maxima = detectMaxima(eroded_seeds, 0f, maximaRadius);
		
		return eroded_maxima;
	}
	
	public ClearCLBuffer createErodedSeeds(ClearCLBuffer input_image, Integer erode_iteration, String erosion_method) {
		
		boolean is3D = input_image.getDimension() > 2 ? true : false;
		
		ClearCLBuffer eroded_image = clij2.create(input_image);
		
		if (is3D) {
			if (erosion_method.equals("Eroded box")) {
				clij2.minimum3DBox(input_image, eroded_image, erode_iteration, erode_iteration, erode_iteration);
			}
			
			if (erosion_method.equals("Eroded sphere")) {
				clij2.minimum3DSphere(input_image, eroded_image, erode_iteration, erode_iteration, erode_iteration);
			}
		} else {
			if (erosion_method.equals("Eroded box")) {
				clij2.minimum2DBox(input_image, eroded_image, erode_iteration, erode_iteration);
			}
			
			if (erosion_method.equals("Eroded sphere")) {
				clij2.minimum2DSphere(input_image, eroded_image, erode_iteration, erode_iteration);
			}
		}
		
		return eroded_image;
		
	}
	
		
	public ClearCLBuffer createLabels(ClearCLBuffer seed_image, ClearCLBuffer thresholded_image) {
		// mask spots
		ClearCLBuffer masked_spots = clij2.create(seed_image);
		clij2.binaryAnd(seed_image, thresholded_image, masked_spots);
		
		ClearCLBuffer output_image = clij2.create(seed_image.getDimensions(), NativeTypeEnum.Float);
		clij2.maskedVoronoiLabeling(masked_spots, thresholded_image, output_image);
		
		return output_image;
	}
	
	public CLIJ2 getCurrentCLIJ2Instance() {
		return clij2;
	}
	
}
