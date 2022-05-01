/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import org.joml.Math;
import org.scijava.prefs.DefaultPrefService;


import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.ResultsTable;
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

	private boolean showDebugImages = new DefaultPrefService().getBoolean(BV3DBoxSettings.class, "bv_3d_box_settings_display_debug_images", false);
	private CLIJ2 clij2;
	private BV_LabelSeparator labelSeparator = new BV_LabelSeparator();
	
	private double[] voxelRatios = {1.0, 1.0};
		
	
	
	public BV_LabelSplitter() {
		clij2 = CLIJ2.getInstance();
		clij2.clear();
	}
	
	/**
	 * 
	 * @param clij2
	 */
	public BV_LabelSplitter(CLIJ2 clij2) {
		this.clij2 = clij2;
	}
	
	/**
	 * 
	 * @param inputImagePlus
	 */
	public BV_LabelSplitter(ImagePlus inputImagePlus) {

		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		voxelRatios = BV3DBoxUtilities.getVoxelRatios(inputImagePlus);
				
	}

	/**
	 * 
	 * @param separationMethod
	 * @param spotSigma
	 * @param maximaRadius
	 * @return
	 */
	public ClearCLBuffer splitLabels(ClearCLBuffer input_image, String separationMethod, Float spotSigma, Float maximaRadius) {
		
		ClearCLBuffer seedImage = clij2.create(input_image);
		
		ClearCLBuffer thresholdedImage = clij2.create(input_image);
				
		clij2.threshold(input_image, thresholdedImage, 1);
						
		switch (separationMethod) {
		
		case "None":
			seedImage = thresholdedImage;
			break;
		
		case "Maxima":
			seedImage = detectMaxima(input_image, spotSigma, maximaRadius);
			break;
			
		case "Eroded Maxima":
			seedImage = detectErodedMaxima(input_image, Math.round(spotSigma), maximaRadius);
			break;
		
		case "EDM Maxima":
			seedImage = detectDistanceMapMaxima(thresholdedImage, maximaRadius);
			break;
		
		case "Maxima Spheres":	
			seedImage = createMaximaSpheres(thresholdedImage, spotSigma, maximaRadius);
			break;
			
		case "DoG Seeds":
			ClearCLBuffer binary_8_bit_image = clij2.create(thresholdedImage);
			clij2.replaceIntensity(thresholdedImage, binary_8_bit_image, 1, 255);
			seedImage = detectDoGSeeds(binary_8_bit_image, spotSigma, maximaRadius);
			binary_8_bit_image.close();
			break;
			
		default:
			seedImage = createErodedSeeds(thresholdedImage, Math.round(spotSigma), separationMethod);
			break;
		}
		
		ClearCLBuffer label_image = createLabels(seedImage, thresholdedImage);
		
		seedImage.close();
		thresholdedImage.close();
		
		return label_image;
	}
	
	
	/**
	 * 
	 * @param input_image
	 * @param spotSigma
	 * @param maximaRadius
	 * @return
	 */
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

	/**
	 * 
	 * @param input_image
	 * @param erode_iteration
	 * @param maximaRadius
	 * @return
	 */
	public ClearCLBuffer detectErodedMaxima(ClearCLBuffer input_image, Integer erode_iteration, Float maximaRadius) {
		
		ClearCLBuffer eroded_seeds = createErodedSeeds(input_image, erode_iteration, "Eroded sphere");
		
		ClearCLBuffer eroded_maxima = detectMaxima(eroded_seeds, 0f, maximaRadius);
		
		eroded_seeds.close();
		
		return eroded_maxima;
	}
	
	
	
	public ClearCLBuffer detectDistanceMapMaxima(ClearCLBuffer binary_image, Float maximaRadius) {
		
		double y_maxima_radius = maximaRadius * voxelRatios[0];
		double z_maxima_radius = maximaRadius / voxelRatios[1];
		
		ClearCLBuffer distance_map = clij2.create(binary_image.getDimensions(), NativeTypeEnum.Float);
		
		clij2.distanceMap(binary_image, distance_map);
		
		ClearCLBuffer maxima_image = clij2.create(binary_image);
		
		clij2.detectMaxima3DBox(distance_map, maxima_image, maximaRadius, y_maxima_radius, z_maxima_radius);
		
		distance_map.close();
		
		return maxima_image;
	}
	
	
	/**
	 * 
	 * @param thresholdedImage
	 * @param spotSigma
	 * @param maximaRadius
	 * @return
	 */
	public ClearCLBuffer createMaximaSpheres(ClearCLBuffer binary_image, Float spotSigma, Float maximaRadius) {
		
		double y_maxima_radius = maximaRadius * voxelRatios[0];
		double z_maxima_radius = maximaRadius / voxelRatios[1] == Double.POSITIVE_INFINITY ? 0.0 : maximaRadius / voxelRatios[1];
			
		System.out.println(maximaRadius / voxelRatios[1]);
		
		ClearCLBuffer distance_map = clij2.create(binary_image.getDimensions(), NativeTypeEnum.Float);
		clij2.distanceMap(binary_image, distance_map);
		
		ClearCLBuffer gaussian_distance_map = clij2.create(binary_image.getDimensions(), NativeTypeEnum.Float);
		clij2.gaussianBlur3D(distance_map, gaussian_distance_map, spotSigma, y_maxima_radius, z_maxima_radius);
		
		ClearCLBuffer maxima_image = clij2.create(binary_image.getDimensions(), NativeTypeEnum.Float);
		clij2.detectMaxima3DBox(gaussian_distance_map, maxima_image, maximaRadius, y_maxima_radius, z_maxima_radius);
		gaussian_distance_map.close();
		
		ClearCLBuffer maxima_labels = clij2.create(binary_image.getDimensions(), NativeTypeEnum.Float);
		clij2.connectedComponentsLabelingDiamond(maxima_image, maxima_labels);
		maxima_image.close();
				
		ResultsTable resultsTable = new ResultsTable();
		clij2.statisticsOfLabelledPixels(distance_map, maxima_labels, resultsTable);
		distance_map.close();
		maxima_labels.close();
		
		int minIntIndex = resultsTable.getColumnIndex("MINIMUM_INTENSITY");
		int x_index = resultsTable.getColumnIndex("CENTROID_X");
		int y_index = resultsTable.getColumnIndex("CENTROID_Y");
		int z_index = resultsTable.getColumnIndex("CENTROID_Z");
		
		double[] maxima_intensity = resultsTable.getColumnAsDoubles(minIntIndex);
		double[] x = resultsTable.getColumnAsDoubles(x_index);
		double[] y = resultsTable.getColumnAsDoubles(y_index);
		double[] z = resultsTable.getColumnAsDoubles(z_index);
		
		System.out.println(maxima_intensity);
		System.out.println(x);
		System.out.println(y);
		System.out.println(z);
		
		ClearCLBuffer sphere_image = clij2.create(binary_image.getDimensions(), NativeTypeEnum.Float);
		clij2.set(sphere_image, 0);
		
		for (int i = 0; i < maxima_intensity.length; i++) {
			
			double x_radius = maxima_intensity[i];
			double y_radius = maxima_intensity[i] * voxelRatios[0];
			double z_radius = maxima_intensity[i] / voxelRatios[1] == Double.POSITIVE_INFINITY ? 1.0 : maxima_intensity[i] / voxelRatios[1];
						
			clij2.drawSphere(sphere_image, x[i], y[i], z[i], x_radius, y_radius, z_radius, i+1);
		}
		
		ClearCLBuffer separated_sphere_image = clij2.create(binary_image.getDimensions(), NativeTypeEnum.Float);
		labelSeparator.splitLabels(clij2, sphere_image, separated_sphere_image);
		
		
		if (showDebugImages) {
			
			ImagePlus sphereImagePlus = WindowManager.getImage("debug_sphere_image");
			if (sphereImagePlus == null) {
				sphereImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, separated_sphere_image, true, LutNames.GLASBEY_LUT);
				sphereImagePlus.setTitle("debug_sphere_image");
				sphereImagePlus.show();		
			} else {
				sphereImagePlus.setImage(BV3DBoxUtilities.pullImageFromGPU(clij2, separated_sphere_image, true, LutNames.GLASBEY_LUT));
				sphereImagePlus.setTitle("debug_sphere_image");
			}	
		}
		
		return separated_sphere_image;
	}

	
	
	/**
	 * 
	 * @param input_image -	must be a binary image
	 * @param sigma
	 * @param threshold
	 * @return
	 */
	public ClearCLBuffer detectDoGSeeds(ClearCLBuffer input_image, Float sigma, Float threshold) {
		
		ClearCLBuffer dog_image = clij2.create(input_image);
		boolean is3D = input_image.getDimension() > 2 ? true : false;
		
		double y_filter_sigma = sigma * voxelRatios[0];
		double z_filter_sigma = sigma / voxelRatios[1];
		
		if (is3D) {
			
			clij2.differenceOfGaussian3D(input_image, dog_image, 0, 0, 0, sigma, y_filter_sigma, z_filter_sigma);
			
		} else {
			
			clij2.differenceOfGaussian2D(input_image, dog_image, 0, 0, sigma, y_filter_sigma);
			
		}
		
		ClearCLBuffer dog_seed_image = clij2.create(dog_image);
		clij2.different(input_image, dog_image, dog_seed_image, 255f - threshold);
		
		dog_image.close();
		
		return dog_seed_image;
	}
	
	
	/**
	 * 
	 * @param input_image
	 * @param erode_iteration
	 * @param erosion_method
	 * @return
	 */
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
	
	
//	experimental / seem to not really work well --> not implemented via GUI
//	public ClearCLBuffer detectPlateaus(ClearCLBuffer input_image, Float spotSigma) {
//		
//		double y_filter_sigma = spotSigma * voxelRatios[0];
//		double z_filter_sigma = spotSigma / voxelRatios[1];
//			
//		ClearCLBuffer temp = clij2.create(input_image);
//		clij2.gaussianBlur3D(input_image, temp, spotSigma, y_filter_sigma, z_filter_sigma);
//				
//		ClearCLBuffer plateaus_image = clij2.create(temp);
//		FindMaximaPlateaus.findMaximaPlateaus(clij2, temp, plateaus_image);	//not possible to use via clij2 instance since currently deactivated 
//		temp.close();	
//	
//		//BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, plateaus_image, false, LutNames.GLASBEY_LUT);
//		
//		return plateaus_image;
//	}
	
	/**
	 * 
	 * @param seed_image
	 * @param thresholded_image
	 * @return
	 */
	public ClearCLBuffer createLabels(ClearCLBuffer seed_image, ClearCLBuffer thresholded_image) {
		// mask spots
		ClearCLBuffer masked_spots = clij2.create(seed_image);
		clij2.mask(seed_image, thresholded_image, masked_spots);
		//clij2.binaryAnd(seed_image, thresholded_image, masked_spots);	//old before version 1.9.0
		
		ClearCLBuffer output_image = clij2.create(seed_image.getDimensions(), NativeTypeEnum.Float);
		clij2.maskedVoronoiLabeling(masked_spots, thresholded_image, output_image);
		masked_spots.close();
		
		return output_image;
	}
	
	
	public CLIJ2 getCurrentCLIJ2Instance() {
		return clij2;
	}
	
	
	
	public void setVoxelRatios(double[] voxelRatios) {
		this.voxelRatios = voxelRatios;
	}
	
	public void setVoxelRatios(ImagePlus imagePlus) {
		this.voxelRatios = BV3DBoxUtilities.getVoxelRatios(imagePlus);
	}
}
