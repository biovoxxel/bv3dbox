package de.biovoxxel.bv3dbox.gui;

import javax.swing.JOptionPane;

import org.scijava.command.Command;
import org.scijava.plugin.Parameter;
import org.scijava.plugin.Plugin;

import de.biovoxxel.bv3dbox.plugins.BV_MakeIsotropicImage;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities;
import de.biovoxxel.bv3dbox.utilities.BV3DBoxUtilities.LutNames;
import ij.ImagePlus;
import ij.WindowManager;
import ij.measure.Calibration;
import ij.plugin.ChannelSplitter;
import ij.plugin.RGBStackMerge;
import ij.process.LUT;
import net.haesleinhuepf.clij.clearcl.ClearCLBuffer;
import net.haesleinhuepf.clij2.CLIJ2;

@Plugin(type = Command.class, menuPath = "Plugins>BioVoxxel 3D Box>Segmentation>Make 3D Image Isotropic")
public class BV_MakeIsotropicImageGUI implements Command {

	
	@Parameter(label = "Image", required = true)
	ImagePlus inputImagePlus;
	
	@Override
	public void run() {
		
		int[] dimensions = inputImagePlus.getDimensions();
		if (dimensions[4] > 1) {
			JOptionPane.showMessageDialog(null, "Does not support time frames", "Wrong dimensionality", JOptionPane.WARNING_MESSAGE);
			
		} else if (inputImagePlus.hasImageStack()) {
						
			CLIJ2 clij2 = CLIJ2.getInstance();
			clij2.clear();
			
			ImagePlus isotropicImagePlus = null;
			
			Calibration inputImageCalibration = inputImagePlus.getCalibration();
			
			double pixelSize = inputImageCalibration.pixelWidth;
			Calibration cal = new Calibration();
			cal.pixelWidth = cal.pixelHeight = cal.pixelDepth = pixelSize;
			cal.setUnit(inputImageCalibration.getUnit());	
			
			
			if (dimensions[4] > 1) {
				JOptionPane.showMessageDialog(null, "Does not support time frames", "Wrong dimensionality", JOptionPane.WARNING_MESSAGE);
				
			}
			
			if (dimensions[2] > 1) {
				
				LUT[] luts = inputImagePlus.getLuts();
				
				//for all channels
				ImagePlus[] channelImps = new ImagePlus[dimensions[2]];
				for (int c = 1; c <= dimensions[2]; c++) {
													
					ImagePlus currentChannel = new ImagePlus("C" + c + inputImagePlus.getTitle(), ChannelSplitter.getChannel(inputImagePlus, c));
					currentChannel.setCalibration(inputImageCalibration);
					
					//currentChannel.show();	//test
					
					BV_MakeIsotropicImage bvmii = new BV_MakeIsotropicImage(clij2, currentChannel);
					ClearCLBuffer isotropic_image = bvmii.makeIsotropic(clij2, currentChannel);
					
					channelImps[c-1] = BV3DBoxUtilities.pullImageFromGPU(clij2, isotropic_image, true, LutNames.GRAY);
					
					isotropic_image.close();
					
					channelImps[c-1].setTitle("iso_C" + c + "-" + inputImagePlus.getTitle());
					
					channelImps[c-1].setLut(luts[c-1]);
					//channelImps[c-1].show();	//test
					
					
				}
				
				isotropicImagePlus = RGBStackMerge.mergeChannels(channelImps, false);
				
								
//				for (int ch = 0; ch < isotropicImagePlus.getNChannels(); ch++) {
//					isotropicImagePlus.setC(ch);
//					isotropicImagePlus.setL
//				}
				
				isotropicImagePlus.setCalibration(cal);
				
				isotropicImagePlus.show();
				
			} else {
				
				BV_MakeIsotropicImage bvmii = new BV_MakeIsotropicImage(clij2, inputImagePlus);
				ClearCLBuffer isotropic_image = bvmii.makeIsotropic(clij2, inputImagePlus);
				
//				double pixelSize = inputImagePlus.getCalibration().pixelWidth;
				
				isotropicImagePlus = BV3DBoxUtilities.pullImageFromGPU(clij2, isotropic_image, true, LutNames.GRAY);
				isotropic_image.close();
				
				isotropicImagePlus.setTitle("iso_" + WindowManager.getUniqueName(inputImagePlus.getTitle()));
				
//				Calibration cal = new Calibration();
//				cal.pixelWidth = cal.pixelHeight = cal.pixelDepth = pixelSize;
//				cal.setUnit(inputImagePlus.getCalibration().getUnit());	
				
				isotropicImagePlus.setCalibration(cal);
				
				isotropicImagePlus.show();
				
			}
			
			
			//BV3DBoxUtilities.addImagePlusToBatchModeImages(isotropicImagePlus);	//not solving the issue that in batch mode macros the output image is not displayed
						
			clij2.clear();
			
		} else {
			JOptionPane.showMessageDialog(null, "Works only on stacks", "Stack required", JOptionPane.WARNING_MESSAGE);
		}
	}

}