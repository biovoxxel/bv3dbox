package de.biovoxxel.bv3dbox.plugins;

import org.scijava.Cancelable;
import org.scijava.log.LogLevel;
import org.scijava.log.LogService;
import org.scijava.log.StderrLogService;
import org.scijava.prefs.DefaultPrefService;
import org.scijava.prefs.PrefService;

import de.biovoxxel.bv3dbox.utilities.BV3DBoxSettings;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
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

public class BV_PseudoFlatFieldCorrection implements Cancelable {

	PrefService prefs = new DefaultPrefService();
	LogService log = new StderrLogService();
			
	CLIJ2 clij2;
	ClearCLBuffer inputImage;
	
	private ImagePlus inputImagePlus;
	private ImagePlus outputImagePlus = null;
	private String outputImageName = "";
	private double x_y_ratio;
	private double z_x_ratio;

		
		
	public BV_PseudoFlatFieldCorrection(ImagePlus inputImagePlus) {
		
		setInputImage(inputImagePlus);
		
	}
	
	
	public void setInputImage(ImagePlus image) {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
		this.inputImagePlus = image;
				
		outputImageName = WindowManager.getUniqueName("PFFC_" + inputImagePlus.getTitle());
		log.debug("outputImageName = " + outputImageName);
		
		readCalibration();
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
		
		if (inputImagePlus.getRoi() != null) {
			inputImage = clij2.pushCurrentSelection(inputImagePlus);
		} else {
			inputImage = clij2.push(inputImagePlus);			
		}
		
	}
		
	
	//TODO: implement for RGB images
	public void runCorrection(float radius, boolean force2D, boolean showBackgroundImage) {
		
		ClearCLBuffer backgound = clij2.create(inputImage.getDimensions(), NativeTypeEnum.Float);
		clij2.copy(inputImage, backgound);
		ClearCLBuffer blurredBackground = clij2.create(backgound);
		
		double y_filter_radius = radius * x_y_ratio;
		
		log.debug("filterRadius=" + radius);
		log.debug("y_filter_radius=" + y_filter_radius);
		
						
		if (inputImagePlus.isStack()) {
			int frames = inputImagePlus.getNFrames();
			int z_slices = inputImagePlus.getNSlices();
			log.debug("frames=" + frames);
			log.debug("z_slices=" + z_slices);
			
			double z_filter_radius = 0; 
			if (z_slices > 1 && frames == 1 && !force2D) {
				z_filter_radius = radius / z_x_ratio;				
			} 
			log.debug("z_filter_radius=" + z_filter_radius);
			
			clij2.gaussianBlur3D(backgound, blurredBackground, radius, y_filter_radius, z_filter_radius);
			log.debug("3D filtering for background creation");
		} else {
			clij2.gaussianBlur2D(backgound, blurredBackground, radius, y_filter_radius);
			log.debug("2D filtering for background creation");
		}
		
		backgound.close();
		
		ImagePlus tempOutputImagePlus;
		if (showBackgroundImage) {
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, blurredBackground, false, LutNames.PHYSICS);
			
		} else {
			
			double meanBackgroundIntensity = clij2.meanOfAllPixels(blurredBackground);
			log.debug("meanBackgroundIntensity = " + meanBackgroundIntensity);
			
			ClearCLBuffer dividedImage = clij2.create(blurredBackground);
			clij2.divideImages(inputImage, blurredBackground, dividedImage);
			log.debug("Image devided by background");
			
			ClearCLBuffer outputImage = clij2.create(dividedImage);
			clij2.multiplyImageAndScalar(dividedImage, outputImage, meanBackgroundIntensity);
			dividedImage.close();
			
			tempOutputImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, outputImage, true, LutNames.GRAY);
			outputImage.close();
		}
		
		blurredBackground.close();
		
		
		outputImagePlus = WindowManager.getImage(outputImageName);
		
		if (outputImagePlus == null) {
			outputImagePlus = new ImagePlus();
		}
		
		outputImagePlus.setImage(tempOutputImagePlus);
		outputImagePlus.setTitle(outputImageName);
		outputImagePlus.setCalibration(inputImagePlus.getCalibration());
		outputImagePlus.show();
		//outputImagePlus.getWindow().setLocation(inputImageLocation.x + inputImageWindow.getWidth() + 10, inputImageLocation.y);
		BV3DBoxUtilities.adaptImageDisplay(inputImagePlus, outputImagePlus);
	}
	
	
	
	
	
	private void readCalibration() {
		Calibration cal = inputImagePlus.getCalibration();
		x_y_ratio = cal.pixelWidth / cal.pixelHeight;
		z_x_ratio = cal.pixelDepth / cal.pixelWidth;
			
	}
	
		
	
	public String getOutputImageName() {
		return outputImageName;
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
		clij2.clear();
		
	}


	@Override
	public String getCancelReason() {
		// TODO Auto-generated method stub
		return null;
	}
}







