/**
 * 
 */
package de.biovoxxel.bv3dbox.plugins;

import java.awt.Rectangle;

import org.scijava.Cancelable;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;
import org.scijava.prefs.DefaultPrefService;
import org.scijava.prefs.PrefService;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.Roi;
import ij.measure.Calibration;
import ij.plugin.LutLoader;
import ij.process.LUT;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;

/**
 * @author BioVoxxel
 *
 */

public class BVVoronoiThresholdLabeling implements Cancelable {


	PrefService prefs = new DefaultPrefService();
	LogService log = new StderrLogService();
			
	CLIJ2 clij2;
	
	private ImagePlus inputImagePlus;
	private ClearCLBuffer input_image;
	private ImagePlus outputImagePlus = null;
	private String outputImageName = "";
	
	private String filterMethod = "Gaussian";
	private Float filterRadius = 0.0f;
	private String backgroundSubtractionMethod;
	private Float backgroundRadius = 0.0f;
	private String thresholdMethod = "Default";
	private Float spotSigma = 0.0f;
	private Float maximaRadius = 0.0f;
	private String outputType = "Labels";
	
	private double x_y_ratio;
	private double z_x_ratio;
	
	LUT grays = BV3DBoxUtilities.createGrayLUT();
	LUT glasbey = LutLoader.openLut(IJ.getDirectory("luts") + LutNames.GLASBEY_LUT.lutName + ".lut");

	private final String OUTPUT_PREFIX = "VTL_"; 

	private ClearCLBuffer filteredImage = null;
	private ClearCLBuffer backgroundSubtractedImage = null;
	private ClearCLBuffer thresholdedImage = null;
	private ClearCLBuffer maximaImage = null;
	private ClearCLBuffer outputImage = null;

	public BVVoronoiThresholdLabeling(ImagePlus inputImagePlus) {
		
		setupInputImage(inputImagePlus);
		
	}
	
	/**
	 * 
	 * @param inputImagePlus
	 * @param filterMethod	currently one of the following: "None", "Gaussian", "DoG", "Median", "Mean", "Open", "Close", "Variance"
	 * @param filterRadius
	 * @param backgroundSubtractionMethod	currently one of the following: "None", "DoG", "DoM", "TopHat", "BottomHat"
	 * @param backgroundRadius
	 * @param thresholdMethod One of the AutoThresholds implemented in CLIJ2
	 * @param spotSigma
	 * @param maximaRadius
	 * @param outputType	should be either "Labels" or "Binary" 
	 */
	public BVVoronoiThresholdLabeling(ImagePlus inputImagePlus, String filterMethod, Float filterRadius, String backgroundSubtractionMethod, Float backgroundRadius, String thresholdMethod, Float spotSigma, Float maximaRadius, String outputType) {
		this.inputImagePlus = inputImagePlus;
		this.filterMethod = filterMethod;
		this.filterRadius = filterRadius;
		this.backgroundSubtractionMethod = backgroundSubtractionMethod;
		this.backgroundRadius = backgroundRadius;
		this.thresholdMethod = thresholdMethod;
		this.spotSigma = spotSigma;
		this.maximaRadius = maximaRadius;
		this.outputType = outputType;
		
		setupInputImage(inputImagePlus);
	}
	
	/**
	 * Internally sets up the input image
	 * 
	 * @param image
	 */
	public void setupInputImage(ImagePlus image) {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
		this.inputImagePlus = image;
				
		outputImageName = WindowManager.getUniqueName(OUTPUT_PREFIX + inputImagePlus.getTitle());
		log.debug("outputImageName = " + outputImageName);
		
		readCalibration();
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		Roi currentRoi = inputImagePlus.getRoi();
		log.debug("currentRoi = " + currentRoi);
		
		if (currentRoi != null) {
			
			Rectangle boundingRectangle = currentRoi.getBounds();
			log.debug("boundingRectangle = " + boundingRectangle);
			
			ImageStack croppedStack = inputImagePlus.getStack().crop(boundingRectangle.x, boundingRectangle.y, 0, boundingRectangle.width, boundingRectangle.height, inputImagePlus.getNSlices());
			log.debug("croppedStack = " + croppedStack);
			
			ImagePlus tempImagePlus = new ImagePlus("tempImage", croppedStack);
			log.debug("tempImagePlus = " + tempImagePlus);
			
			input_image = clij2.push(tempImagePlus);
			
		} else {
			input_image = clij2.push(inputImagePlus);			
		}
	}
	
	
	private void readCalibration() {
		Calibration cal = inputImagePlus.getCalibration();
		x_y_ratio = cal.pixelWidth / cal.pixelHeight;
		z_x_ratio = cal.pixelDepth / cal.pixelWidth;
			
	}
	
	
	
	public ClearCLBuffer getInputImageAsClearClBuffer() {
		
		return input_image;
		
	}
	
	
	public ImagePlus getOutputImage() {
		
		return outputImagePlus;
		
	}
	
	
	/**
	 * Complete processing sequence with either the default input values or the given ones (via Constructor)
	 */
	public void processImage() {
						
		filteredImage = filterImage(input_image, filterMethod, filterRadius);
		IJ.showProgress(0.2);
		
		backgroundSubtractedImage = backgroundSubtraction(filteredImage, backgroundSubtractionMethod, backgroundRadius);
		filteredImage.close();
		IJ.showProgress(0.4);
		
		thresholdedImage = thresholdImage(backgroundSubtractedImage, thresholdMethod);
		backgroundSubtractedImage.close();
		IJ.showProgress(0.6);
		
		maximaImage = detectMaxima(input_image, spotSigma, maximaRadius);
		IJ.showProgress(0.8);
		
		outputImage = createLabels(maximaImage, thresholdedImage);
		maximaImage.close();
		thresholdedImage.close();
		IJ.showProgress(0.9);
		
		createOutputImage(outputImage, outputType);
		outputImage.close();
		IJ.showProgress(1.0);
		
	}


	

	public ClearCLBuffer filterImage(ClearCLBuffer input_image, String filterMethod, Float filterRadius) {
					
		ClearCLBuffer filtered_image = clij2.create(input_image);
		
		double y_filter_radius = filterRadius * x_y_ratio;
		double z_filter_radius = filterRadius / z_x_ratio;
		
		long stackSize = input_image.getDepth();
		
		if (stackSize == 1) {
			z_filter_radius = 0;
		}
		
		if (filterMethod.equals("None")) {
			clij2.copy(input_image, filtered_image);
		}
		
		if (filterMethod.equals("Gaussian")) {
			clij2.gaussianBlur3D(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
		}
		
		if (filterMethod.equals("DoG")) {
			double dogFilterRadius = filterRadius + 2d;
			clij2.differenceOfGaussian3D(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius, dogFilterRadius, (dogFilterRadius * x_y_ratio), (dogFilterRadius / z_x_ratio));
		}
		
		if (filterMethod.equals("Median")) {
			if (stackSize == 1) {
				clij2.median2DSphere(input_image, filtered_image, filterRadius, y_filter_radius);	
			} else {
				clij2.median3DSliceBySliceSphere(input_image, filtered_image, filterRadius, y_filter_radius);				
			}
		}
		
		if (filterMethod.equals("Mean")) {
			if (stackSize == 1) {
				clij2.mean2DSphere(input_image, filtered_image, filterRadius, y_filter_radius);	
			} else {
				clij2.mean3DSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);				
			}
		}
		
		if (filterMethod.equals("Open")) {
			clij2.greyscaleOpeningSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
		}
		
		if (filterMethod.equals("Close")) {
			clij2.greyscaleClosingSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
		}
		
		if (filterMethod.equals("Variance")) {
			clij2.varianceSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
		}
		
		return filtered_image;
	}

	
	public ClearCLBuffer backgroundSubtraction(ClearCLBuffer filtered_image, String backgroundSubtractionMethod, Float backgroundRadius) {
		
		ClearCLBuffer background_subtracted_image = clij2.create(filtered_image);
		
		double y_bckgr_radius = backgroundRadius * x_y_ratio;
		double z_bckgr_radius = backgroundRadius / z_x_ratio;
		
		long zSlices = filtered_image.getDepth();
		
		if (zSlices == 1) {
			z_bckgr_radius = 0;
		}
		
		
		if (backgroundSubtractionMethod.equals("None")) {
			clij2.copy(filtered_image, background_subtracted_image);
		}
		
		if (backgroundSubtractionMethod.equals("DoG")) {
			clij2.differenceOfGaussian3D(filtered_image, background_subtracted_image, 0, 0, 0, backgroundRadius, y_bckgr_radius, z_bckgr_radius);	
		}

		if (backgroundSubtractionMethod.equals("DoM")) {
			ClearCLBuffer tempMedian = clij2.create(filtered_image);
			if (zSlices == 1) {
				clij2.median2DSphere(filtered_image, tempMedian, backgroundRadius, y_bckgr_radius);	
			} else {
				clij2.median3DSliceBySliceSphere(filtered_image, tempMedian, backgroundRadius, y_bckgr_radius);				
			}
			clij2.subtractImages(filtered_image, tempMedian, background_subtracted_image);
			tempMedian.close();
		}
		
		if (backgroundSubtractionMethod.equals("TopHat")) {
			clij2.topHatSphere(filtered_image, background_subtracted_image, backgroundRadius, y_bckgr_radius, z_bckgr_radius);
		}
		
		if (backgroundSubtractionMethod.equals("BottomHat")) {
			clij2.bottomHatSphere(filtered_image, background_subtracted_image, backgroundRadius, y_bckgr_radius, z_bckgr_radius);
		}
		
		return background_subtracted_image;
	}


	
	
	
	public ClearCLBuffer thresholdImage(ClearCLBuffer background_subtracted_image, String thresholdMethod) {
		ClearCLBuffer thresholded_image = clij2.create(background_subtracted_image);
		
		clij2.automaticThreshold(background_subtracted_image, thresholded_image, thresholdMethod);
		
		return thresholded_image;
	}

	

	public ClearCLBuffer detectMaxima(ClearCLBuffer input_image, Float spotSigma, Float maximaRadius) {
		
		ClearCLBuffer temp = clij2.create(input_image);
		
		double y_filter_sigma = spotSigma * x_y_ratio;
		double z_filter_sigma = spotSigma / z_x_ratio;
		
		
		clij2.gaussianBlur3D(input_image, temp, spotSigma, y_filter_sigma, z_filter_sigma);
		//alternative
		//double offsetDoG = 2.0d;
		//clij2.differenceOfGaussian3D(input_image, temp, filterRadius, y_filter_sigma, z_filter_sigma, spotSigma + offsetDoG, ((spotSigma + offsetDoG) * x_y_ratio), ((spotSigma + offsetDoG) / z_x_ratio));
		
		ClearCLBuffer maxima_image = clij2.create(input_image);
		
		double y_maxima_radius = maximaRadius * x_y_ratio;
		double z_maxima_radius = maximaRadius / z_x_ratio;
		
		clij2.detectMaxima3DBox(temp, maxima_image, maximaRadius, y_maxima_radius, z_maxima_radius);
		temp.close();
		
		return maxima_image;
	}

	
	public ClearCLBuffer createLabels(ClearCLBuffer maxima_image, ClearCLBuffer thresholded_image) {
		// mask spots
		ClearCLBuffer masked_spots = clij2.create(maxima_image);
		clij2.binaryAnd(maxima_image, thresholded_image, masked_spots);
		
		ClearCLBuffer output_image = clij2.create(maxima_image.getDimensions(), NativeTypeEnum.Float);
		clij2.maskedVoronoiLabeling(masked_spots, thresholded_image, output_image);
		
		return output_image;
	}

	//TODO: Outlines does not work as intended, since the gray LUT is not applied to the output image
	public void createOutputImage(ClearCLBuffer output_image, String outputType) {
		ImagePlus tempOutputImagePlus;
		
		if (outputType.equals("Binary")) {
			tempOutputImagePlus = clij2.pullBinary(output_image);
		} else if (outputType.equals("Labels")) {
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, output_image, true, LutNames.GLASBEY_LUT);
		} else {
			ClearCLBuffer temp_output_image = clij2.create(input_image);
			clij2.visualizeOutlinesOnOriginal(input_image, output_image, temp_output_image);
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, temp_output_image, true, LutNames.GRAY);
			output_image.close();
		}
						
		outputImagePlus = WindowManager.getImage(outputImageName);			
		
		if (outputImagePlus == null) {
			outputImagePlus = new ImagePlus();
		}
		
		outputImagePlus.setImage(tempOutputImagePlus);
		outputImagePlus.setTitle(outputImageName);
		
		if (outputType.equals("Binary")) {
			outputImagePlus.setLut(grays);
		} else {
			outputImagePlus.setLut(glasbey);
		}
		
		outputImagePlus.show();
		
	}
	
		
	
	public String getOutputImageName() {
		return outputImageName;
	}
	
		
	public void cancel() {
		
	}

	@Override
	public boolean isCanceled() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void cancel(String reason) {
		
		ImagePlus outputImagePlus = WindowManager.getImage(outputImageName);
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		clij2.close();
		
	}

	@Override
	public String getCancelReason() {
		// TODO Auto-generated method stub
		return null;
	}
	

}
