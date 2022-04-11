package de.biovoxxel.bv3dbox.plugins;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;

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
import ij.plugin.LutLoader;
import ij.process.LUT;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clijx.CLIJx;
import net.haesleinhuepf.clijx.imagej2.ImageJ2Tubeness;


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
 */

public class BV_VoronoiThresholdLabeling implements Cancelable {


	PrefService prefs = new DefaultPrefService();
	LogService log = new StderrLogService();
			
	private CLIJ2 clij2;
	private CLIJx clijx;
	
	ImageJ2Tubeness ij2Tubeness = new ImageJ2Tubeness();
//	private TubenessProcessor tubenessProcessor = new TubenessProcessor(false);
//	ImagePlus tubenessImagePlus;
	
	private ImagePlus inputImagePlus;
	private Window inputImageWindow;
	private Rectangle displayedArea;
	private Point windowLocation;
	
	private ClearCLBuffer input_image;
	private ImagePlus outputImagePlus = null;
	private String outputImageName = "";
	
	private String filterMethod = "Gaussian";
	private Float filterRadius = 0.0f;
	private String backgroundSubtractionMethod;
	private Float backgroundRadius = 0.0f;
	private String thresholdMethod = "Default";
	private String separationMethod = "Maxima";
	private Float spotSigma = 0.0f;
	private Float maximaRadius = 0.0f;
	private String outputType = "Labels";
	
	private double [] calibration;
	
	LUT grays = BV3DBoxUtilities.createGrayLUT();
	LUT glasbey = LutLoader.openLut(IJ.getDirectory("luts") + LutNames.GLASBEY_LUT.lutName + ".lut");

	private final String OUTPUT_PREFIX = "VTL_"; 

	private ClearCLBuffer filteredImage = null;
	private ClearCLBuffer backgroundSubtractedImage = null;
	private ClearCLBuffer thresholdedImage = null;
	private ClearCLBuffer seedImage = null;
	private ClearCLBuffer outputImage = null;

	
	public BV_VoronoiThresholdLabeling() {
		
	}
	
	
	public BV_VoronoiThresholdLabeling(ImagePlus inputImagePlus) {
		
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
	public BV_VoronoiThresholdLabeling(ImagePlus inputImagePlus, String filterMethod, Float filterRadius, String backgroundSubtractionMethod, Float backgroundRadius, String thresholdMethod, String separationMethod, Float spotSigma, Float maximaRadius, String outputType) {
		this.inputImagePlus = inputImagePlus;
		this.filterMethod = filterMethod;
		this.filterRadius = filterRadius;
		this.backgroundSubtractionMethod = backgroundSubtractionMethod;
		this.backgroundRadius = backgroundRadius;
		this.thresholdMethod = thresholdMethod;
		this.separationMethod = separationMethod;
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
		
		calibration = BV3DBoxUtilities.readCalibration(image);
		
		displayedArea = inputImagePlus.getCanvas().getSrcRect();
		inputImageWindow = inputImagePlus.getWindow();
		windowLocation = inputImagePlus.getWindow().getLocation();
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		clijx = CLIJx.getInstance();
		clijx.clear();
		
		Roi currentRoi = inputImagePlus.getRoi();
		log.debug("currentRoi = " + currentRoi);
		
		if (currentRoi != null) {
			
			Rectangle boundingRectangle = currentRoi.getBounds();
			log.debug("boundingRectangle = " + boundingRectangle);
			
			ImageStack croppedStack = inputImagePlus.getStack().crop(boundingRectangle.x, boundingRectangle.y, 0, boundingRectangle.width, boundingRectangle.height, inputImagePlus.getNSlices());
			log.debug("croppedStack = " + croppedStack);
			
			ImagePlus tempImagePlus = new ImagePlus("tempImage", croppedStack);
			log.debug("tempImagePlus = " + tempImagePlus);

			input_image = clij2.push(BV3DBoxUtilities.convertToGray8(tempImagePlus));
			
		} else {
			
			input_image = clij2.push(BV3DBoxUtilities.convertToGray8(inputImagePlus));
			
		}
	}
	
	
	
	/**
	 * 
	 * @param filterMethod
	 * @param filterRadius
	 * @param backgroundSubtractionMethod
	 * @param backgroundRadius
	 * @param thresholdMethod
	 * @param separationMethod
	 * @param spotSigma
	 * @param maximaRadius
	 * @param outputType
	 */
	public void setParameters(String filterMethod, Float filterRadius, String backgroundSubtractionMethod, Float backgroundRadius, String thresholdMethod, String separationMethod, Float spotSigma, Float maximaRadius, String outputType) {
		this.filterMethod = filterMethod;
		this.filterRadius = filterRadius;
		this.backgroundSubtractionMethod = backgroundSubtractionMethod;
		this.backgroundRadius = backgroundRadius;
		this.thresholdMethod = thresholdMethod;
		this.separationMethod = separationMethod;
		this.spotSigma = spotSigma;
		this.maximaRadius = maximaRadius;
		this.outputType = outputType;
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
				
		BV_LabelSplitter labelSplitter = new BV_LabelSplitter(clij2);
		
		outputImage = labelSplitter.splitLabels(thresholdedImage, separationMethod, spotSigma, maximaRadius);
		IJ.showProgress(0.8);
		
		thresholdedImage.close();
		seedImage.close();
		IJ.showProgress(0.9);
		
		createOutputImage(outputImage, outputType);
		outputImage.close();
		IJ.showProgress(1.0);
		
	}

	
	public void invertImage(ClearCLBuffer input_image, ClearCLBuffer inverted_image) {
		clij2.invert(input_image, inverted_image);
	}
	

	public ClearCLBuffer filterImage(ClearCLBuffer input_image, String filterMethod, Float filterRadius) {
					
		ClearCLBuffer filtered_image = clij2.create(input_image);
		
		double y_filter_radius = filterRadius * calibration[1];
		double z_filter_radius = filterRadius / calibration[2];
		
		long stackSize = input_image.getDepth();
		
		if (stackSize == 1) {
			z_filter_radius = 0;
		}
		
		switch (filterMethod) {
		case "Gaussian":
			clij2.gaussianBlur3D(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
			break;
		case "DoG":
			double dogFilterRadius = filterRadius + 2d;
			clij2.differenceOfGaussian3D(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius, dogFilterRadius, (dogFilterRadius * calibration[1]), (dogFilterRadius / calibration[2]));
			break;
		case "Median":
			if (stackSize == 1) {
				clij2.median2DSphere(input_image, filtered_image, filterRadius, y_filter_radius);	
			} else {
				clij2.median3DSliceBySliceSphere(input_image, filtered_image, filterRadius, y_filter_radius);
			}
			break;
		case "Mean":
			if (stackSize == 1) {
				clij2.mean2DSphere(input_image, filtered_image, filterRadius, y_filter_radius);	
			} else {
				clij2.mean3DSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);				
			}
			break;
		case "Open":
			clij2.greyscaleOpeningSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
			break;
		case "Close":
			clij2.greyscaleClosingSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
			break;
		case "Variance":
			clij2.varianceSphere(input_image, filtered_image, filterRadius, y_filter_radius, z_filter_radius);
			break;
		case "Tubeness":
			ij2Tubeness.imageJ2Tubeness(clij2, input_image, filtered_image, filterRadius, 0f, 0f, 0f);
			break;
		case "Inverted Tubeness":
			ClearCLBuffer inverted_image = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);
			clij2.invert(input_image, inverted_image);
			//BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, inverted_image, false, LutNames.GRAY);
			ij2Tubeness.imageJ2Tubeness(clij2, inverted_image, filtered_image, filterRadius, 0f, 0f, 0f);
			inverted_image.close();
			break;
		default:
			clij2.copy(input_image, filtered_image);
			break;
		}
		
		return filtered_image;
	}

	
	public ClearCLBuffer backgroundSubtraction(ClearCLBuffer filtered_image, String backgroundSubtractionMethod, Float backgroundRadius) {
		
		ClearCLBuffer background_subtracted_image = clij2.create(filtered_image);
		
		double y_bckgr_radius = backgroundRadius * calibration[1];
		double z_bckgr_radius = backgroundRadius / calibration[2];
		
		long zSlices = filtered_image.getDepth();
		
		if (zSlices == 1) {
			z_bckgr_radius = 0;
		}
		
		switch (backgroundSubtractionMethod) {
		case "None":
			clij2.copy(filtered_image, background_subtracted_image);
			break;
		case "DoG":
			clij2.differenceOfGaussian3D(filtered_image, background_subtracted_image, 0, 0, 0, backgroundRadius, y_bckgr_radius, z_bckgr_radius);	
			break;
		case "DoM":
			ClearCLBuffer tempMedian = clij2.create(filtered_image);
			if (zSlices == 1) {
				clij2.median2DSphere(filtered_image, tempMedian, backgroundRadius, y_bckgr_radius);	
			} else {
				clij2.median3DSliceBySliceSphere(filtered_image, tempMedian, backgroundRadius, y_bckgr_radius);				
			}
			clij2.subtractImages(filtered_image, tempMedian, background_subtracted_image);
			tempMedian.close();
			break;
		case "TopHat":
			clij2.topHatBox(filtered_image, background_subtracted_image, backgroundRadius, y_bckgr_radius, z_bckgr_radius);
			break;
		case "BottomHat":
			clij2.bottomHatBox(filtered_image, background_subtracted_image, backgroundRadius, y_bckgr_radius, z_bckgr_radius);
			break;
		case "Inverted Tubeness":
			ClearCLBuffer temp_image = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);
			ClearCLBuffer tubeness_image = clij2.create(input_image.getDimensions(), NativeTypeEnum.Float);
			clij2.invert(filtered_image, temp_image);
			ij2Tubeness.imageJ2Tubeness(clij2, temp_image, tubeness_image, backgroundRadius, 0f, 0f, 0f);
			clij2.multiplyImageAndScalar(tubeness_image, temp_image, 2.0);	//increase tube intensity to elivate the subtraction effect
			clij2.subtractImages(filtered_image, temp_image, background_subtracted_image);
			temp_image.close();
			tubeness_image.close();
			break;
		default:
			clij2.copy(input_image, filtered_image);
			break;
		}
		
		return background_subtracted_image;
	}


	
	
	@Deprecated
	public ClearCLBuffer thresholdImage(ClearCLBuffer background_subtracted_image, String thresholdMethod) {
		
		ClearCLBuffer thresholded_image = clij2.create(background_subtracted_image);
						
		log.debug("threshold = " + clij2.getAutomaticThreshold(background_subtracted_image, thresholdMethod));
		
		clij2.automaticThreshold(background_subtracted_image, thresholded_image, thresholdMethod);
		
		return thresholded_image;
	}

	

	//TODO: Outlines does not work as intended, since the gray LUT is not applied to the output image
	public void createOutputImage(ClearCLBuffer output_image, String outputType) {
		ImagePlus tempOutputImagePlus = null;
		
		if (outputType.equals("Binary")) {
			tempOutputImagePlus = clij2.pullBinary(output_image);
		} else if (outputType.equals("Labels")) {
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, output_image, true, LutNames.GLASBEY_LUT);
		} else {
//			ClearCLBuffer temp_output_image = clij2.create(input_image);
//			clij2.visualizeOutlinesOnOriginal(input_image, output_image, temp_output_image);
//			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, temp_output_image, true, LutNames.GRAY);
//			output_image.close();
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
		
		outputImagePlus.getWindow().setLocation(windowLocation.x + inputImagePlus.getWindow().getWidth(), windowLocation.y);
		outputImagePlus.getWindow().setSize(inputImageWindow.getSize());
		outputImagePlus.getCanvas().setSourceRect(displayedArea);
		
	}
	
		
	
	public String getOutputImageName() {
		return outputImageName;
	}
	
	public ClearCLBuffer getInputImageAsClearClBuffer() {
		return input_image;	
	}
	
	
	public ImagePlus getOutputImage() {
		return outputImagePlus;
	}
	
	public CLIJ2 getCurrentCLIJ2Instance() {
		return clij2;
	}
	
		
	public void cancel() {
		
	}

	public boolean isCanceled() {
		// TODO Auto-generated method stub
		return false;
	}

	public void cancel(String reason) {
		
		ImagePlus outputImagePlus = WindowManager.getImage(outputImageName);
		if (outputImagePlus != null) {
			outputImagePlus.close();
		}
		clij2.close();
		
	}

	public String getCancelReason() {
		// TODO Auto-generated method stub
		return null;
	}
	

}