package de.biovoxxel.bv3dbox.plugins;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

public class BV_ConvolutedBackgroundSubtraction {
	
	
	private CLIJ2 clij2;
	private ImagePlus inputImagePlus;
	private ClearCLBuffer input_image;
	
	
	public BV_ConvolutedBackgroundSubtraction() {
		this.clij2 = CLIJ2.getInstance();
	}
	
	
	public BV_ConvolutedBackgroundSubtraction(ImagePlus inputImagePlus) {
		this.inputImagePlus = inputImagePlus;
		this.clij2 = CLIJ2.getInstance();
		clij2.clear();
		this.input_image = clij2.push(inputImagePlus);
	}
	
	public void setInputImagePlus(ImagePlus inputImagePlus) {
		this.inputImagePlus = inputImagePlus;
		this.input_image = clij2.push(inputImagePlus);
	}
	
	public CLIJ2 getCLIJ2Instance() {
		return clij2;
	}
	
	public ImagePlus getInputImagePlus() {
		return inputImagePlus;
	}
	
	public ClearCLBuffer getInputBuffer() {
		return input_image;
	}
	
	public ClearCLBuffer subtractBackground(ClearCLBuffer originalBuffer, ClearCLBuffer backgroundBuffer) {
		
		ClearCLBuffer backgroundSubtractedOutputImage = clij2.create(originalBuffer);

		clij2.subtractImages(originalBuffer, backgroundBuffer, backgroundSubtractedOutputImage);
		
		return backgroundSubtractedOutputImage;
	}
	
	
	public ClearCLBuffer filterImage(ClearCLBuffer originalBuffer, String filterMethod, float filterRadius, boolean force2D) {
		
		long zSlices = originalBuffer.getDepth();
		
		double[] calibration = BV3DBoxUtilities.readCalibration(inputImagePlus);
		
		double y_filter_radius = filterRadius * calibration[1];
		double z_filter_radius = 0.0d;
		
		if (zSlices > 1 && !force2D) {
			
			z_filter_radius = filterRadius / calibration[2];
		}
		
		//System.out.println("z_filter_radius = " + z_filter_radius);
		
		ClearCLBuffer filteredImage = clij2.create(originalBuffer);
		
		switch (filterMethod) {
		case "Gaussian":
			clij2.gaussianBlur3D(originalBuffer, filteredImage, filterRadius, y_filter_radius, z_filter_radius);
			break;
		case "Median":
			if (zSlices > 1) {
				
				clij2.median3DSliceBySliceSphere(originalBuffer, filteredImage, filterRadius, y_filter_radius);
				
			} else {
				
				clij2.median2DSphere(originalBuffer, filteredImage, filterRadius, y_filter_radius);
				
			}
			break;

		case "Mean":
			if (zSlices > 1) {
				
				clij2.mean3DSphere(originalBuffer, filteredImage, filterRadius, y_filter_radius, z_filter_radius);	
			
			} else {
				
				clij2.mean2DSphere(originalBuffer, filteredImage, filterRadius, y_filter_radius);	
				
			}
			break;
			
		case "Open":
			clij2.greyscaleOpeningSphere(originalBuffer, filteredImage, filterRadius, y_filter_radius, z_filter_radius);
			break;

//		case "Close":
//			clij2.greyscaleClosingSphere(originalBuffer, filteredImage, filterRadius, y_filter_radius, z_filter_radius);
//			break;

		default:
			break;
		}
		
		ClearCLBuffer borderCorrectedImage = clij2.create(originalBuffer);
		if (zSlices > 1) {
			
			clij2.maximum3DSliceBySliceSphere(filteredImage, borderCorrectedImage, Math.floor(filterRadius/5), Math.floor(y_filter_radius/5));
			
		} else {
			
			clij2.maximum2DSphere(filteredImage, borderCorrectedImage, Math.floor(filterRadius/5), Math.floor(y_filter_radius/5));
		}
		
		return borderCorrectedImage;
		
	}
		
}
