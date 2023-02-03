package de.biovoxxel.bv3dbox.plugins;

import javax.swing.JOptionPane;

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
import ij.process.ImageConverter;
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

public class BV_FlatFieldCorrection {

	PrefService prefs = new DefaultPrefService();
	private Boolean showDebugImages = prefs.getBoolean(BV3DBoxSettings.class, "bv_3d_box_settings_display_debug_images", false);

	LogService log = new StderrLogService();
	
	private CLIJ2 clij2;
	
	private ImagePlus originalImagePlus = null;
		
	private ClearCLBuffer original_image = null;
	private ClearCLBuffer flat_field_image = null;
	private ClearCLBuffer dark_field_image = null;
	
	//private final int WIDTH = 0;
	//private final int HEIGHT = 1;
	private final int DEPTH = 2;
	
	public BV_FlatFieldCorrection() {
		
		log.setLevel(prefs.getInt(BV3DBoxSettings.class, "bv_3d_box_settings_debug_level", LogLevel.INFO));
		
		clij2 = CLIJ2.getInstance();
		clij2.clear();
	}
	

	
	public void setImages(ImagePlus originalImagePlus, ImagePlus flatFieldImagePlus, ImagePlus darkFieldImagePlus) {
		
		log.debug("originalImagePlus = " + originalImagePlus);
		log.debug("flatFieldImagePlus = " + flatFieldImagePlus);
		log.debug("darkFieldImagePlus = " + darkFieldImagePlus);
		
		
		this.originalImagePlus = originalImagePlus;
		
		long[] originalDimensions = null;
		long[] flatFieldDimensions = null;
		long[] darkFieldDimensions = null;
		
				
		if (originalImagePlus == null) {
			
			JOptionPane.showMessageDialog(null, "The original image to be corrected is missing", "Image missing", JOptionPane.ERROR_MESSAGE);
			return;
			
		} else {
			
			if (originalImagePlus.getBitDepth() == 24 && originalImagePlus.isStack()) {
				
				nonSupportedFormat(originalImagePlus);
				
			} else if (originalImagePlus.getBitDepth() == 24 && !originalImagePlus.isStack()) {
				
				ImagePlus originalBrightnessImagePlus = getBrightnessChannel(originalImagePlus);
				
				originalDimensions = new long[]{(long)originalBrightnessImagePlus.getWidth(), (long)originalBrightnessImagePlus.getHeight(), (long)originalBrightnessImagePlus.getStackSize()};
				
				original_image = clij2.create(originalDimensions, NativeTypeEnum.Float);
				original_image = clij2.push(originalBrightnessImagePlus);
				
			} else {
				
				originalDimensions = new long[]{(long)originalImagePlus.getWidth(), (long)originalImagePlus.getHeight(), (long)originalImagePlus.getStackSize()};
				
				original_image = clij2.create(originalDimensions, NativeTypeEnum.Float);
				original_image = clij2.push(originalImagePlus);
			
			}
		}
		
		
		if (flatFieldImagePlus == null) {
			JOptionPane.showMessageDialog(null, "The flat field image is missing", "Image missing", JOptionPane.ERROR_MESSAGE);
			return;
		} else {
			
			if (flatFieldImagePlus.getBitDepth() == 24 && flatFieldImagePlus.isStack()) {
				
				nonSupportedFormat(flatFieldImagePlus);
				
			} else if (flatFieldImagePlus.getBitDepth() == 24 && !flatFieldImagePlus.isStack()) {
				
				ImagePlus flatFieldBrightnessImagePlus = getBrightnessChannel(flatFieldImagePlus);
				
				flatFieldDimensions = new long[]{(long)flatFieldBrightnessImagePlus.getWidth(), (long)flatFieldBrightnessImagePlus.getHeight(), (long)flatFieldBrightnessImagePlus.getStackSize()};
				
				flat_field_image = clij2.create(flatFieldDimensions, NativeTypeEnum.Float);
				flat_field_image = clij2.push(flatFieldBrightnessImagePlus);
				
			} else {
				
				flatFieldDimensions = new long[]{(long)flatFieldImagePlus.getWidth(), (long)flatFieldImagePlus.getHeight(), (long)flatFieldImagePlus.getStackSize()};
				
				flat_field_image = clij2.create(flatFieldDimensions, NativeTypeEnum.Float);
				
				if (originalDimensions[DEPTH] > 1 && flatFieldDimensions[DEPTH] == 1) {
					
					ClearCLBuffer temp_flat_field = clij2.push(flatFieldImagePlus);
					
					clij2.imageToStack(temp_flat_field, flat_field_image, originalDimensions[DEPTH]);
					
				} else {
					flat_field_image = clij2.push(flatFieldImagePlus);				
				}
				
			}
		}
		

	
		if (darkFieldImagePlus != null) {
			
			if (darkFieldImagePlus.getBitDepth() == 24 && darkFieldImagePlus.isStack()) {
				
				nonSupportedFormat(darkFieldImagePlus);
				
			} else if (darkFieldImagePlus.getBitDepth() == 24 && !darkFieldImagePlus.isStack()) {
				
				ImagePlus darkFieldBrightnessImagePlus = getBrightnessChannel(darkFieldImagePlus);
				
				darkFieldDimensions = new long[]{(long)darkFieldBrightnessImagePlus.getWidth(), (long)darkFieldBrightnessImagePlus.getHeight(), (long)darkFieldBrightnessImagePlus.getStackSize()};
				
				dark_field_image = clij2.create(darkFieldDimensions, NativeTypeEnum.Float);
				dark_field_image = clij2.push(darkFieldBrightnessImagePlus);
				
			} else {
				
				darkFieldDimensions = new long[]{(long)darkFieldImagePlus.getWidth(), (long)darkFieldImagePlus.getHeight(), (long)darkFieldImagePlus.getStackSize()};
				
				dark_field_image = clij2.create(originalDimensions, NativeTypeEnum.Float);
				
				if (originalDimensions[DEPTH] > 1 && darkFieldDimensions[DEPTH] == 1) {
					
					ClearCLBuffer temp_dark_field = clij2.push(darkFieldImagePlus);
					
					clij2.imageToStack(temp_dark_field, dark_field_image, originalDimensions[DEPTH]);
					
				} else {
					dark_field_image = clij2.push(darkFieldImagePlus);				
				}	
			}
		}	
	}



	private void nonSupportedFormat(ImagePlus image) {
		log.debug(image.getTitle() + " is a non supported image format.");
		JOptionPane.showMessageDialog(null, image.getTitle() + " is an RGB color image stack", "Not supported format", JOptionPane.ERROR_MESSAGE);
		return;
	}



	private ImagePlus getBrightnessChannel(ImagePlus image) {
		
		ImagePlus duplicateImage = image.duplicate();
		ImageConverter ic = new ImageConverter(duplicateImage);
		ic.convertToHSB32();
		duplicateImage.setSlice(3);
		ImagePlus lightnessImagePlus = duplicateImage.crop("slice");
		duplicateImage.changes = false;
		duplicateImage.close();
		return lightnessImagePlus;
	}
	
	
	
	public void flatFieldCorrection() {
		
		ClearCLBuffer original_minus_dark_field;
		ClearCLBuffer flat_field_minus_dark_field;
		ClearCLBuffer corrected_image = clij2.create(original_image.getDimensions(), NativeTypeEnum.Float);
		
		if (dark_field_image != null) {
			original_minus_dark_field = clij2.create(original_image.getDimensions(), NativeTypeEnum.Float);
			flat_field_minus_dark_field = clij2.create(original_image.getDimensions(), NativeTypeEnum.Float);
		
			
			clij2.subtractImages(original_image, dark_field_image, original_minus_dark_field);
			clij2.subtractImages(flat_field_image, dark_field_image, flat_field_minus_dark_field);

			double meanOfFlatField = clij2.getMeanOfAllPixels(flat_field_minus_dark_field);
			
			ClearCLBuffer divided_image = clij2.create(original_minus_dark_field);
			clij2.divideImages(original_minus_dark_field, flat_field_minus_dark_field, divided_image);
			original_minus_dark_field.close();
			flat_field_minus_dark_field.close();
			
			clij2.multiplyImageAndScalar(divided_image, corrected_image, meanOfFlatField);
			divided_image.close();
			
		} else {
			ClearCLBuffer divided_image = clij2.create(original_image.getDimensions(), NativeTypeEnum.Float);
			clij2.divideImages(original_image, flat_field_image, divided_image);
			
			double meanOfFlatField = clij2.getMeanOfAllPixels(flat_field_image);
			log.debug("meanOfFlatField = " + meanOfFlatField);
			
			clij2.multiplyImageAndScalar(divided_image, corrected_image, meanOfFlatField);
			divided_image.close();
		}
		
		
		ImagePlus correctedImagePlus;
		
		if (originalImagePlus.getBitDepth() == 24 && !originalImagePlus.isStack()) {
			
			ImagePlus correctedLightnessImagePlus = clij2.pull(corrected_image);
			
			if (showDebugImages) {
				
				BV3DBoxUtilities.pullAndDisplayImageFromGPU(clij2, corrected_image, false, LutNames.GRAY, originalImagePlus.getCalibration());
				
			}
			
			correctedImagePlus = originalImagePlus.duplicate();
			ImageConverter ic = new ImageConverter(correctedImagePlus);
			ic.convertToHSB32();
			
			correctedImagePlus.getStack().setProcessor(correctedLightnessImagePlus.getProcessor(), 3);
			
			ic.convertHSB32ToRGB();
			
		} else {
			
			correctedImagePlus = clij2.pull(corrected_image);
					
		}
		corrected_image.close();
		
		correctedImagePlus.setTitle(WindowManager.getUniqueName("FFCorr_" + originalImagePlus.getTitle()));
		correctedImagePlus.getProcessor().resetMinAndMax();
		correctedImagePlus.setCalibration(originalImagePlus.getCalibration());
		correctedImagePlus.show();
	}

}
