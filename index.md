<script src="https://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML" type="text/javascript"></script>

# BioVoxxel 3D Box (bv3dbox)

The known [BioVoxxel Toolbox](https://github.com/biovoxxel/BioVoxxel-Toolbox) functions now for 2D and 3D images in one place.

![image](https://user-images.githubusercontent.com/10721817/151507835-a243ccfd-913b-4f5d-bddb-0a4ed2433005.png)

## Functionalities
### Threshold Check: 
A helping tool to better identify suitable histogram-based automatic intensity thresholds, compare them qualitatively and quantitatively. This is based on the publication: [Qualitative and Quantitative Evaluation of Two New Histogram Limiting Binarization Algorithms](https://www.cscjournals.org/library/manuscriptinfo.php?mc=IJIP-829), IJIP (2014).

### Flat Field Correction
The flat field correction allows to correct for uneven illumination including a dark-field (dark current) image subtraction. The dark-field image can also be ommited if unavailable. If the original image which sould be corrected for uneven illumination is a stack, flat-field as well as dark-field images can also be provided as single images. Those will be adapted to the original stack slice number. Otherwise, if they are not single slices, dark-field and flat-field need to have the same size in all dimensions. 
Input images can have 8, 16, or 32-bit. _RGB images are not yet supported_.
The Output image is always 32-bit to account for correct float-point values after image division. 

Formula:  $$result = { original - darkfield \over flatfield - darkfield }$$


![image](https://user-images.githubusercontent.com/10721817/151598573-534b8f3f-99bd-4bb7-b420-140ca8f94ef7.png)

### Pseudo Flat Field Corection
The pseudo flat field correction takes a copy of the original image to be corrected and blurs it with a Gaussian Blur filter. If the image is scaled 3D stack (with indicated units such as µm), the filter will also consider the correct x/y/z scaling ratio and perform the blurring accordingly in 3D space. This way the background created stays undistorted in relation to the original data. If the original image is a time series or slices should be considered independent the blurring can be forced to be done in 2D and correction will be applied slice by slice to the original.
The background image can be displayed and for proper correction, the blurring radius should be high enough to eliminate all traces of any original object in the background image. Only different intensity shading must remain.
In the case of a 3D image all slices can be checked using the stack slice slider.
The output will be 32-bit to account for accurate float-point pixel intensity values.

![image](https://user-images.githubusercontent.com/10721817/151659090-8a4032cb-337a-402e-889f-8e7781acfe35.png)



## Installation
The Biovoxxel Toolbox as well as the BioVoxxel 3D Box are distributed via the [BioVoxxel Fiji update site](https://imagej.net/update-sites/following)

## Issues
https://github.com/biovoxxel/bv3dbox/issues

## Contact
via [BioVoxxel gitter channel](https://gitter.im/biovoxxel/BioVoxxel_Toolbox)

## Acknowledgement
The BioVoxxel 3D Box funtions are heavily based and rely strongly on the CLIJ library family.
Therefore, this development would have not been possible without the work of Robert Haase and colleagues.

Robert Haase, Loic Alain Royer, Peter Steinbach, Deborah Schmidt, Alexandr Dibrov, Uwe Schmidt, Martin Weigert, Nicola Maghelli, Pavel Tomancak, Florian Jug, Eugene W Myers. [CLIJ: GPU-accelerated image processing for everyone. Nat Methods (2019)](https://doi.org/10.1038/s41592-019-0650-1)
