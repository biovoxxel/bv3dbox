/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import javax.swing.JOptionPane;

import org.joml.Math;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;

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
		if (inputImagePlus.getProcessor().isBinary()) {
			
			clij2 = CLIJ2.getInstance();
			clij2.clear();
			
			input_image = clij2.push(inputImagePlus);
			
			voxelRatios = BV3DBoxUtilities.getVoxelRelations(inputImagePlus);
		
		} else {
			
			JOptionPane.showMessageDialog(null, "The input image needs to be of type binary", "Wrong image type", 0);
			return;
		}
	}

	
	public ClearCLBuffer splitLabels(String separationMethod, Float spotSigma, Float maximaRadius) {
		
		ClearCLBuffer seedImage = clij2.create(input_image);
		
		ClearCLBuffer thresholdedImage = clij2.create(input_image);
		clij2.threshold(input_image, thresholdedImage, 128);
		
		if (separationMethod.equals("Maxima")) {
			seedImage = detectMaxima(input_image, spotSigma, maximaRadius);		
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
