package de.biovoxxel.bv3dbox.utilities;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Window;

import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.Roi;
import ij.macro.Interpreter;
import ij.measure.Calibration;
import ij.plugin.LutLoader;
import ij.process.ImageConverter;
import ij.process.LUT;
import ij.process.StackStatistics;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;
import net.haesleinhuepf.clij2.plugins.AutoThresholderImageJ1;
import net.imagej.updater.UpdateService;
import net.imagej.updater.UpdateSite;

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


public class BV3DBoxUtilities {
	
	private static LogService log = new StderrLogService();
		
	public static void main(String[] args) {
		//System.out.println(BV3DBoxUtilities.class.getClassLoader().getResourceAsStream("/plugins.config"));
		
	}
	
	public BV3DBoxUtilities() {
		
	}
	
	/**
	 * 
	 * @param updateService
	 * @param updateSiteName A comma-separated String of update site names 
	 */
	public static void displayMissingDependencyWarning(UpdateService updateService, String updateSiteName) {
					
		String[] updateSiteArray = updateSiteName.split(",");
		
		for (int site = 0; site < updateSiteArray.length; site++) {
			try {
				UpdateSite requestedUpdateSite = updateService.getUpdateSite(updateSiteArray[site].trim()); 
				
				if (!requestedUpdateSite.isActive()) {
					
					log.warn(updateSiteArray[site].trim() + " update site needs to be activated to use this plugin.\n"
							+ "Go to >Help >Update... and then use the Manage update sites button.");
				}
				
			} catch (NullPointerException e) {
				e.printStackTrace();
				log.warn("Update site \"" + updateSiteArray[site].trim() + "\" is not existing in list of ImageJ update sites");
			}		
		}
	}
	
	
	
	/**
	 * 
	 * @param image
	 * @return	double[] holding first (voxelWidth / voxelHeight) and second (voxelDepth / voxelWidth) ratios 
	 */
	public static double[] getVoxelRatios(ImagePlus image) {
		
		Calibration cal = image.getCalibration();
		
		double voxelWidth = cal.pixelWidth;
		double voxelHeight = cal.pixelHeight;
		double voxelDepth;
		
		if (image.hasImageStack()) {
			voxelDepth = cal.pixelDepth;
		} else {
			voxelDepth = 0.0;
		}
		
		System.out.println("voxelWidth=" + voxelWidth + "/voxelHeight=" + voxelHeight + "/voxelDepth=" + voxelDepth);
		
		double width_height_ratio = voxelWidth / voxelHeight;
		double depth_width_ratio = voxelDepth / voxelWidth;
		
		double[] voxelRatios = new double[] { width_height_ratio, depth_width_ratio };
		
		System.out.println("voxelRatios = " + voxelRatios[0] + "/" + voxelRatios[1]);
		
		return voxelRatios;
	}
	
	
	public static long[] getImageDimensions(ImagePlus image) {
		
		return new long[] { (long)image.getWidth(), (long)image.getHeight(), (long)image.getStackSize() };
		
	}
	
	
	/**
	 * 
	 * @param image
	 * @return	double[] with first index 0 = pixel width, index 1 = width / height ratio, index 2 = width/ depth 
	 */
	public static double[] readCalibration(ImagePlus image) {
		
		double[] calibration = new double[3];

		if (image != null) {
			
			Calibration cal = image.getCalibration();
			calibration[0] = cal.pixelWidth;
			calibration[1] = cal.pixelWidth / cal.pixelHeight;
			calibration[2] = cal.pixelDepth / cal.pixelWidth;
		}
						
		return calibration;
			
	}
	
	
	public static String[] extendImageTitleListWithNone() {
		String[] allImageNames = WindowManager.getImageTitles();
		String[] imageNames = new String[allImageNames.length + 1];
		imageNames[0] = "None";
		for (int w = 0; w < allImageNames.length; w++) {
			imageNames[w+1] = allImageNames[w];
		}
		return imageNames;
	}

	
	public static void pullAndDisplayImageFromGPU(CLIJ2 clij2, ClearCLBuffer imageToShow, boolean autoContrast, LutNames lutName, Calibration cal) {
		
		ImagePlus imagePlusToBePulled = clij2.pull(imageToShow);
		imagePlusToBePulled.setTitle(imageToShow.getName());
				
		LUT outputLut = null;
		if (lutName.equals(LutNames.GRAY)) {
			
			outputLut = createGrayLUT();
		} else {
			
		
			outputLut = LutLoader.openLut(IJ.getDirectory("luts") + lutName.lutName + ".lut");			
		}
		
		if (outputLut != null) { imagePlusToBePulled.setLut(outputLut); }
		

		if (autoContrast) {
			double max_int = clij2.maximumOfAllPixels(imageToShow);
			imagePlusToBePulled.setDisplayRange(0.0, max_int);			
		} else {
			imagePlusToBePulled.resetDisplayRange();			
		}
		
		System.out.println("Calibration = " + cal);
		if (cal != null) {
			imagePlusToBePulled.setCalibration(cal);
		}
//TODO: in batch mode images are not correctly displayed
//		if (Interpreter.isBatchMode()) {
//			addImagePlusToBatchModeImages(imagePlusToBePulled);
//		}
		imagePlusToBePulled.show();			
		
	}
	
	public static ImagePlus pullImageFromGPU(CLIJ2 clij2, ClearCLBuffer imageToShow, boolean autoContrast, LutNames lutName) {
		
		ImagePlus imagePlusToBePulled = clij2.pull(imageToShow);
		imagePlusToBePulled.setTitle(imageToShow.getName());
				
		LUT outputLut;
		if (lutName.equals(LutNames.GRAY)) {
			outputLut = createGrayLUT();
		} else if (lutName.equals(LutNames.OUTLINE)) {
			outputLut = createOutlineLUT();
		} else {
			outputLut = LutLoader.openLut(IJ.getDirectory("luts") + lutName.lutName + ".lut");		
		}
		
		if (outputLut != null) { imagePlusToBePulled.setLut(outputLut); }

		if (autoContrast) {
			double max_int = clij2.maximumOfAllPixels(imageToShow);
			imagePlusToBePulled.setDisplayRange(0.0, max_int);			
		} else {
			imagePlusToBePulled.resetDisplayRange();			
		}
		
		return imagePlusToBePulled;
	}
	
	
	public static void addImagePlusToBatchModeImages(ImagePlus imageToAdd) {
		
		System.out.println("isBatchMode=" + Interpreter.isBatchMode());
		if (Interpreter.isBatchMode()) {
			System.out.println("ImageID = " + imageToAdd.getID());
			Interpreter.addBatchModeImage(imageToAdd);
			System.out.println("Last batch image = " + Interpreter.getLastBatchModeImage());
		}
	}
	
	
	public static void updateOutputImagePlus(ImagePlus imageToShow, String imageName) {
		ImagePlus outputImagePlus = WindowManager.getImage(imageName);
		
		if (outputImagePlus == null) {
			outputImagePlus = new ImagePlus();
		}
		outputImagePlus.setImage(imageToShow);
		outputImagePlus.setTitle(imageName);
		outputImagePlus.show();
	}
	
	
	
	public static ClearCLBuffer convertBinaryToLabelBuffer(CLIJ2 clij2, ImagePlus binary_image) {
		ClearCLBuffer temp_input_image = clij2.push(binary_image);
		ClearCLBuffer connectedComponentLabels = clij2.create(temp_input_image);
		clij2.connectedComponentsLabelingDiamond(temp_input_image, connectedComponentLabels);
		temp_input_image.close();
		
		return connectedComponentLabels;
	}
	
	
	public static int[] getHistogram(ImagePlus image) {
		StackStatistics stackStatistics;
		if (image.getRoi() != null) {
			stackStatistics = new StackStatistics(image.duplicate());
		} else {
			stackStatistics = new StackStatistics(image);
		}
				
		double[] tempHistogram = stackStatistics.histogram();
//		Plot hist = new Plot("Histogram for thresholding", "intensity", "count");
//		hist.add("bar", tempHistogram);
//		hist.show();
		
		int[] finalHistogram = new int[tempHistogram.length];
		
		for (int i = 0; i < tempHistogram.length; i++) {
			finalHistogram[i] = (int) Math.round(tempHistogram[i]);
		}
		
		
		
		return finalHistogram;
	}
	
	
	public static int[] getLimitedHistogram(int[] histogram, int min, int max) {
	
		int[] limitedHistogram = new int[histogram.length]; 
		
		min = (min < 0) ? 0 : min;
		min = (min > histogram.length-1) ? histogram.length-1 : min;
		
		max = (max < 0) ? 0 : max;
		max = (max > histogram.length-1) ? histogram.length-1 : max;
		
		for (int i = 0; i < histogram.length; i++) {
									
			if (i < min) {
				limitedHistogram[i] = 0; 
			} else if ((histogram.length - 1 - i) > max) {
				limitedHistogram[i] = 0; 
			} else {
				limitedHistogram[i] = histogram[i];
			}
		}
		
		return limitedHistogram;
	}
	
	
	public static double getThresholdValue(CLIJ2 clij2, String thresholdMethod, ClearCLBuffer image, String limitation) {
		
		double min = 0;
		double max = 255;
		
		switch (limitation) {
		case "full":
			break;
		case "ignore black":
			min = 1;
			break;
		case "ignore white":
			max = 254;
			break;
		case "ignore both":
			min = 1;
			max = 254;
			break;
		default:
			break;
		}
			
		return clij2.getAutomaticThreshold(image, thresholdMethod, min, max, 256);
	}
	
	
	public static int getThresholdValue(String thresholdMethod, int[] histogram) {
		
		int thresholdValue = 255;
		
		if (thresholdMethod.equals("Huang2")) {
			thresholdValue = calculateHuang2(histogram);
		} else {
			
			AutoThresholderImageJ1 autoThresholderIJ1 = new AutoThresholderImageJ1();
			
			thresholdValue = autoThresholderIJ1.getThreshold(thresholdMethod, histogram);
		}
		
		return thresholdValue;
		
	}
	
	
	public static ClearCLBuffer thresholdImage(CLIJ2 clij2, ClearCLBuffer input_image, double threshold) {
		
		log.debug("threshold = " + threshold);
		
		ClearCLBuffer thresholded_image = clij2.create(input_image);
		
		clij2.threshold(input_image, thresholded_image, threshold);
		
		return thresholded_image;
		
	}
	
	
	public static void showWindow(String windowName, boolean show) {
		Window logWindow = WindowManager.getWindow(windowName);
		if (logWindow != null) {
			if (show) {
				logWindow.setVisible(true);				
			} else {
				logWindow.setVisible(false);				
			}
		}
	}
	
	
	public static float getMinFromRange(String range) throws NumberFormatException {
		float min_value = Float.NaN;
		if (range.contains("-")) {
			String min_value_string = range.substring(0, range.indexOf("-"));
			if (min_value_string.equalsIgnoreCase("infinity")) {
				min_value = Float.POSITIVE_INFINITY;
			} else {
				min_value = Float.parseFloat(min_value_string);
			}
		}
		return min_value;
		
	}
	
	public static float getMaxFromRange(String range) throws NumberFormatException {
		float max_value = Float.NaN;
		if (range.contains("-")) {
			String max_value_string = range.substring(range.indexOf("-") + 1);
			if (max_value_string.equalsIgnoreCase("infinity")) {
				max_value = Float.POSITIVE_INFINITY;
			} else {
				max_value = Float.parseFloat(max_value_string);
			}
		}
		return max_value;
		
	}
	
	public enum LutNames {
		FIRE_LUT("fire"),
		GRAY("Grays"),
		GLASBEY_LUT("glasbey_on_dark"),
		GEEN_FIRE_BLUE_LUT("Green Fire Blue"),
		OUTLINE("outline"),
		PHYSICS("physics");

		public final String lutName;
		
		LutNames(String lutName) {
			 this.lutName = lutName;
		}
	}
	
	
//	public enum HistogramLimit {
//		FULL("full"),
//		IGNORE_BLACK("ignore black"),
//		IGNORE_WHITE("ignore white"),
//		IGNORE_BOTH("ignore both");
//		
//		public final String limit;
//		
//		HistogramLimit(String limit) {
//			 this.limit = limit;
//		}
//	}
	
	public static LUT createGrayLUT() {
		
		byte[] red = new byte[256];
		byte[] green = new byte[256];
		byte[] blue = new byte[256];
		
		for (int v = 0; v < 256; v++) {
			red[v] = (byte) v;
			green[v] = (byte) v;
			blue[v] = (byte) v;
		}
		
		LUT grayLUT = new LUT(red, green, blue);
		
		log.debug("Gray LUT created");
		
		return grayLUT;
	}
	
	
	public static LUT createOutlineLUT() {
		
		byte[] red = new byte[256];
		byte[] green = new byte[256];
		byte[] blue = new byte[256];
		
		for (int v = 0; v < 255; v++) {
			red[v] = (byte) v;
			green[v] = (byte) v;
			blue[v] = (byte) v;
		}
		
		Color roiColor = Roi.getColor();
		
		System.out.println(roiColor);
		
		red[255] = (byte)roiColor.getRed();
		green[255] = (byte)roiColor.getGreen();
		blue[255] = (byte)roiColor.getBlue();
		
		LUT outlineLUT = new LUT(red, green, blue);
		
		System.out.println(outlineLUT);
		
		log.debug("Outline LUT created");
		
		return outlineLUT;
	}
	
	
	public static ImagePlus convertToGray8(ImagePlus image) {
		if (image.getProcessor().getBitDepth() > 8) {

			ImagePlus byteTypeInputImage = image.duplicate();
			ImageConverter ic = new ImageConverter(byteTypeInputImage);
			ImageConverter.setDoScaling(true);
			ic.convertToGray8();
			log.debug("input image converted to byte processor");
			
			return byteTypeInputImage;
			
		} else {
			
			return image;
			
		}
	}
	
	
	public static void adaptImageDisplay(ImagePlus source, ImagePlus target) {
		
		if (source != null && target != null) {
			ImageCanvas sourceCanvas = source.getCanvas();
			ImageCanvas targetCanvas = target.getCanvas();
			
			ImageWindow sourceWindow = source.getWindow();
			ImageWindow targetWindow = target.getWindow();
			
			if (sourceWindow != null && targetWindow != null) {
				
			Point sourceLocation = sourceWindow.getLocation();
			Rectangle sourceRectangle = sourceCanvas.getSrcRect();
			
			targetCanvas.setSize(sourceCanvas.getSize());
			targetCanvas.setMagnification(sourceCanvas.getMagnification());
			targetCanvas.setSourceRect(sourceRectangle);
			
			targetWindow.setLocation(sourceLocation.x + sourceWindow.getWidth(), sourceLocation.y);
			targetWindow.setSize(sourceWindow.getSize());
			}
		}
	}
	
	
	public static int calculateHuang2(int [] data ) {
		// Implements Huang's fuzzy thresholding method 
		// Uses Shannon's entropy function (one can also use Yager's entropy function) 
		// Huang L.-K. and Wang M.-J.J. (1995) "Image Thresholding by Minimizing  
		// the Measures of Fuzziness" Pattern Recognition, 28(1): 41-51
		// Reimplemented (to handle 16-bit efficiently) by Johannes Schindelin Jan 31, 2011

		// find first and last non-empty bin
		int first, last;
		for (first = 0; first < data.length && data[first] == 0; first++)
			; // do nothing
		for (last = data.length - 1; last > first && data[last] == 0; last--)
			; // do nothing
		if (first == last)
			return 0;

		// calculate the cumulative density and the weighted cumulative density
		double[] S = new double[last + 1], W = new double[last + 1];
		S[0] = data[0];
		for (int i = Math.max(1, first); i <= last; i++) {
			S[i] = S[i - 1] + data[i];
			W[i] = W[i - 1] + i * data[i];
		}

		// precalculate the summands of the entropy given the absolute difference x - mu (integral)
		double C = last - first;
		double[] Smu = new double[last + 1 - first];
		for (int i = 1; i < Smu.length; i++) {
			double mu = 1 / (1 + Math.abs(i) / C);
			Smu[i] = -mu * Math.log(mu) - (1 - mu) * Math.log(1 - mu);
		}

		// calculate the threshold
		int bestThreshold = 0;
		double bestEntropy = Double.MAX_VALUE;
		for (int threshold = first; threshold <= last; threshold++) {
			double entropy = 0;
			int mu = (int)Math.round(W[threshold] / S[threshold]);
			for (int i = first; i <= threshold; i++)
				entropy += Smu[Math.abs(i - mu)] * data[i];
			mu = (int)Math.round((W[last] - W[threshold]) / (S[last] - S[threshold]));
			for (int i = threshold + 1; i <= last; i++)
				entropy += Smu[Math.abs(i - mu)] * data[i];

			if (bestEntropy > entropy) {
				bestEntropy = entropy;
				bestThreshold = threshold;
			}
		}

		return bestThreshold;
	}

	
}
