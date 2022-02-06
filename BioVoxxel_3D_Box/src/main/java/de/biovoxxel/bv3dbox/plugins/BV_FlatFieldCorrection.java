package de.biovoxxel.bv3dbox.plugins;

import javax.swing.JOptionPane;

import ij.ImagePlus;
import ij.WindowManager;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij.coremem.enums.NativeTypeEnum;
import net.haesleinhuepf.clij2.CLIJ2;

public class BV_FlatFieldCorrection {

	private CLIJ2 clij2;
	
	private ImagePlus originalImagePlus = null;
	private ImagePlus flatFieldImagePlus = null;
	private ImagePlus darkFieldImagePlus = null;
	
	private ClearCLBuffer original_image = null;
	private ClearCLBuffer flat_field_image = null;
	private ClearCLBuffer dark_field_image = null;
	
	private final int WIDTH = 0;
	private final int HEIGHT = 1;
	private final int DEPTH = 2;
	
	public BV_FlatFieldCorrection() {
		clij2 = CLIJ2.getInstance();
		clij2.clear();
	}
	

	
	public void setImages(ImagePlus originalImagePlus, ImagePlus flatFieldImagePlus, ImagePlus darkFieldImagePlus) {
		
		this.originalImagePlus = originalImagePlus;
		this.flatFieldImagePlus = flatFieldImagePlus;
		this.darkFieldImagePlus = darkFieldImagePlus;
		
		long[] originalDimensions;
		long[] flatFieldDimensions;
		long[] darkFieldDimensions;
		
		
				
		if (originalImagePlus == null) {
			JOptionPane.showMessageDialog(null, "The original image to be corrected is missing", "Image missing", JOptionPane.ERROR_MESSAGE);
			return;
		} else {
			
			originalDimensions = new long[]{(long)originalImagePlus.getWidth(), (long)originalImagePlus.getHeight(), (long)originalImagePlus.getStackSize()};
			
			original_image = clij2.create(originalDimensions, NativeTypeEnum.Float);
			original_image = clij2.push(originalImagePlus);
		}
		
		
		if (flatFieldImagePlus == null) {
			JOptionPane.showMessageDialog(null, "The flat field image is missing", "Image missing", JOptionPane.ERROR_MESSAGE);
			return;
		} else {
			flatFieldDimensions = new long[]{(long)flatFieldImagePlus.getWidth(), (long)flatFieldImagePlus.getHeight(), (long)flatFieldImagePlus.getStackSize()};
			
			flat_field_image = clij2.create(originalDimensions, NativeTypeEnum.Float);
			
			if (originalDimensions[DEPTH] > 1 && flatFieldDimensions[DEPTH] == 1) {
				
				ClearCLBuffer temp_flat_field = clij2.push(flatFieldImagePlus);

				clij2.imageToStack(temp_flat_field, flat_field_image, originalDimensions[DEPTH]);
				
			} else {
				flat_field_image = clij2.push(flatFieldImagePlus);				
			}
			
			
		}
		

	
		if (darkFieldImagePlus != null) {
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
	
	public void flatFieldCorrection() {
		
		ClearCLBuffer original_minus_dark_field;
		ClearCLBuffer flat_field_minus_dark_field;
		ClearCLBuffer corrected_image = clij2.create(original_image.getDimensions(), NativeTypeEnum.Float);
		
		if (dark_field_image != null) {
			original_minus_dark_field = clij2.create(original_image.getDimensions(), NativeTypeEnum.Float);
			flat_field_minus_dark_field = clij2.create(original_image.getDimensions(), NativeTypeEnum.Float);
		
			
			clij2.subtractImages(original_image, dark_field_image, original_minus_dark_field);
			clij2.subtractImages(flat_field_image, dark_field_image, flat_field_minus_dark_field);
//			original_image.close();
//			flat_field_image.close();

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
			
			clij2.multiplyImageAndScalar(divided_image, corrected_image, meanOfFlatField);
			divided_image.close();
		}
		
		ImagePlus correctedImagePlus = clij2.pull(corrected_image);
		correctedImagePlus.setTitle(WindowManager.getUniqueName("FFCorr_" + originalImagePlus.getTitle()));
		correctedImagePlus.getProcessor().resetMinAndMax();
		correctedImagePlus.show();
	}

}
