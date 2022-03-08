package de.biovoxxel.bv3dbox.utilities;

import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;

import ij.IJ;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.LutLoader;
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
	
	
	public static void displayMissingDependencyWarning(UpdateService updateService, String updateSiteName) {
					
		String[] updateSiteArray = updateSiteName.split(",");
		
		for (int site = 0; site < updateSiteArray.length; site++) {
			try {
				UpdateSite requestedUpdateSite = updateService.getUpdateSite(updateSiteArray[site].trim()); 
				
				if (!requestedUpdateSite.isActive()) {
					
					log.error(updateSiteArray[site].trim() + " update site needs to be activated to use this plugin.\n"
							+ "Go to >Help >Update... and then use the Manage update sites button.");
				}
				
			} catch (NullPointerException e) {
				e.printStackTrace();
				log.error("Update site \"" + updateSiteArray[site].trim() + "\" is not existing in list of ImageJ update sites");
			}		
		}
	}
	
	
	
	/**
	 * 
	 * @param image
	 * @return	double[] holding first (voxelWidth / voxelHeight) and second (voxelDepth / voxelWidth) ratios 
	 */
	public static double[] getVoxelRelations(ImagePlus image) {
		
		Calibration cal = image.getCalibration();
		
		double voxelWidth = cal.pixelWidth;
		double voxelHeight = cal.pixelHeight;
		double voxelDepth;
		
		if (image.isStack()) {
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
	
	public static String[] extendImageTitleListWithNone() {
		String[] allImageNames = WindowManager.getImageTitles();
		String[] imageNames = new String[allImageNames.length + 1];
		imageNames[0] = "None";
		for (int w = 0; w < allImageNames.length; w++) {
			imageNames[w+1] = allImageNames[w];
		}
		return imageNames;
	}

	
	public static void pullAndDisplayImageFromGPU(CLIJ2 clij2, ClearCLBuffer imageToShow, boolean autoContrast, LutNames lutName) {
		
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
		imagePlusToBePulled.show();
		
	}
	
	public static ImagePlus pullImageFromGPU(CLIJ2 clij2, ClearCLBuffer imageToShow, boolean autoContrast, LutNames lutName) {
		
		ImagePlus imagePlusToBePulled = clij2.pull(imageToShow);
		imagePlusToBePulled.setTitle(imageToShow.getName());
		
		
		LUT outputLut;
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
		
		return imagePlusToBePulled;
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
		StackStatistics stackStatistics = new StackStatistics(image);
		if (image.getRoi() != null) {
			stackStatistics = new StackStatistics(image.duplicate());
		} else {
			stackStatistics = new StackStatistics(image);
		}
				
		//double[] histogram = stackStatistics.histogram();
 	
		double[] tempHistogram = stackStatistics.histogram();
		
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
	
	
	
	public static int getThresholdValue(String thresholdMethod, int[] histogram) {
		
		AutoThresholderImageJ1 autoThresholderIJ1 = new AutoThresholderImageJ1();
		
		return autoThresholderIJ1.getThreshold(thresholdMethod, histogram);
		
	}
	
	
	public static ClearCLBuffer thresholdImage(CLIJ2 clij2, ClearCLBuffer background_subtracted_image, double threshold) {
		
		log.debug("threshold = " + threshold);
		
		ClearCLBuffer thresholded_image = clij2.create(background_subtracted_image);
		
		clij2.threshold(background_subtracted_image, thresholded_image, threshold);
		
		return thresholded_image;
		
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
		PHYSICS("physics");

		public final String lutName;
		
		LutNames(String lutName) {
			 this.lutName = lutName;
		}
	}
	
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
		
		return grayLUT;
	}
	
	
	
}
