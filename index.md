<script src="https://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML" type="text/javascript"></script>

# BioVoxxel 3D Box (bv3dbox)

Most of the known [BioVoxxel Toolbox](https://github.com/biovoxxel/BioVoxxel-Toolbox) functions now for 2D and 3D images in one place. All functions are heavily based on GPU computing via the fabulous [CLIJ2 library](url)

![image](https://user-images.githubusercontent.com/10721817/151507835-a243ccfd-913b-4f5d-bddb-0a4ed2433005.png)

## Functionalities
### Threshold Check: 
A helping tool to better identify suitable histogram-based automatic intensity thresholds, compare them qualitatively and quantitatively. This is based on the publication: [Qualitative and Quantitative Evaluation of Two New Histogram Limiting Binarization Algorithms](https://www.cscjournals.org/library/manuscriptinfo.php?mc=IJIP-829), IJIP (2014).

The Threshold Check allows to compare all implemented [Auto Thresholds](https://imagej.net/plugins/auto-threshold) from ImageJ (by [Gabriel Landini](https://github.com/landinig)) and its counterparts from the CLIJ2 library. It indicates in blue colors the non extracted image areas and in red/orange/yellow the extracted ones. The contrast saturation slider helps to push visibility of the actual intensities in the original image and to better see, if the extracted areas coincide with the actually intended objects which should be extracted from the image. In the ImageJ main window, the status bar shows the corresponding sensitivity and specificity values for the extracted areas in relation to the highlighted object ares (influenced by the chosen saturation). 

![image](https://user-images.githubusercontent.com/10721817/151660419-101bf26c-0127-465c-95fb-280f1cc92504.png)

If the saturation is kept fixed a more objective and quantitative comparison of the performance of individual Auto Thresholds can be achieved. 

![image](https://user-images.githubusercontent.com/10721817/151660612-7974d883-c221-4bb8-9cf6-1d3c96ce3a12.png)


If the user finds a useful threshold the output can be set to either binary with the values 0/255 (ImageJ binary standard), binary 0/1 (CLIJ2 standard) or "Labels" which extracts the the objects filled with unique intensity values to be used as labeled connected components for further analysis (recommended setup).

![image](https://user-images.githubusercontent.com/10721817/151660615-ea6ae986-f0b3-4c9b-b3b8-c9f30e6c09ce.png)



### Flat Field Correction
The flat field correction allows to correct for uneven illumination including a dark-field (dark current) image subtraction. The dark-field image can also be ommited if unavailable. If the original image which sould be corrected for uneven illumination is a stack, flat-field as well as dark-field images can also be provided as single images. Those will be adapted to the original stack slice number. Otherwise, if they are not single slices, dark-field and flat-field need to have the same size in all dimensions. 
Input images can have 8, 16, or 32-bit. _RGB images are not yet supported_.
The Output image is always 32-bit to account for correct float-point values after image division. 

Formula:

$$result = { original - darkfield \over flatfield - darkfield }$$


![image](https://user-images.githubusercontent.com/10721817/151598573-534b8f3f-99bd-4bb7-b420-140ca8f94ef7.png)

### Pseudo Flat Field Corection
The pseudo flat field correction takes a copy of the original image to be corrected and blurs it with a Gaussian Blur filter. If the image is scaled 3D stack (with indicated units such as µm), the filter will also consider the correct x/y/z scaling ratio and perform the blurring accordingly in 3D space. This way the background created stays undistorted in relation to the original data. If the original image is a time series or slices should be considered independent the blurring can be forced to be done in 2D and correction will be applied slice by slice to the original.
The background image can be displayed and for proper correction, the blurring radius should be high enough to eliminate all traces of any original object in the background image. Only different intensity shading must remain.
In the case of a 3D image all slices can be checked using the stack slice slider.
The output will be 32-bit to account for accurate float-point pixel intensity values.

![image](https://user-images.githubusercontent.com/10721817/151659090-8a4032cb-337a-402e-889f-8e7781acfe35.png)

### Recursive Filter
A recursive filter repetitively applies the same filter on the previously filtered version of the underlying image. This keepsspecifically for the median filter shape alterations low, perfectly removes noise, homogenizes objects but still keeps border of also small objects better that a median filter with a bigger radius. It also performs efficiently due to the small filter size.

![image](https://user-images.githubusercontent.com/10721817/151659864-04528775-85e3-4980-9fb5-00fb5424838d.png)

### Voronoi Threshold Labeler
The labeler is meant to be used as a image segmentation tool combining image pre-processing using a variety of convolition filters, background subtraction methods, auto thresholding and intensity maxima detection. The latter allows object separation similar to the a watershed algorithm, but will be only effective if _Labels_ is chosen as output. Dependent on the combination of pre-processing. background subtraction, threshold and maxima detection quite variable objects can be extracted from an image.

![image](https://user-images.githubusercontent.com/10721817/151660909-302f642f-e9c3-4c4b-a761-10acb79cf932.png)

This tool works in 2D as well as 3D images.

### Object Inspector
The _Object Inspector_ is the new version of the [_Speckle Inspector_](https://imagej.net/plugins/biovoxxel-toolbox#speckle-inspector). It analyzes (secondary) objects inside (primary) objects.
Input parameters are:

- `Primary objects`: this is an image holding the primary (outer) objects in form of labels or a binary image. Must be provided!
- `Secondary objects`: this is an image holding the secondary (inner) objects in form of labels or a binary image. Must be provided!
- `Primary original image`: The original image related to the primary objects. This has to be provided only if pixel intensity-related values should be analyzed
- `Secondary original image`: The original image related to the secondary objects. This has to be provided only if pixel intensity-related values should be analyzed
- `Primary volume limitation`: all primary objects inside this 2D area / 3D volume range will be analyzed. All others will be excluded from analysis
- `Primary MMDTC ratio`: This refers to the **M**ean / **M**ax **D**istance **T**o **C**entroid ratio (ratio between the average and maximum distance of the objects' border to the centroid). This is used to exclude objects of similar size but difference in shape (similar to the circularity in the standard ImageJ `Analyze Particles...`function.
- `Secondary volume limitation`: equivalent of the above for the secondary objects.
- `Secondary MMDTC ratio`: equivalent of the above for the secondary objects.
- `Exclude primary edge objects`: All objects which touch the image border or in 3D the image borders and the upper and lower stack slice will be excluded. All secondary objects contained in those objects will also be removed from the nalysis and output image
- `Pad stack tops`: if active it will add a black slice before the first and after the last stack slice. This avoids removing primary objects still visible in the first and last slice if _Exclude primary edge objects_ is active. To achieve proper measurements, however, it is recommended to avoid this function and acquire objects completely during imaging.
- `Show analysis label maps`: will display the analyzed objects as intensity coded labels (with new consecutive numbering)
- `Show count map`: shows labels of the primary objects with the intensity indicating the numbers of secondary objects contained inside them.

Results tables are available for primary as well as secondary objects including object counting and relational identification, size, intensity and some shape values.

![image](https://user-images.githubusercontent.com/10721817/151661661-fbc7ae90-b30b-4ffa-ac44-752a7ca37b48.png)

### Overlap Extractor
This tool is the new version of the [_Binary Feature Extractor_](https://imagej.net/plugins/biovoxxel-toolbox#binary-feature-extractor). It keeps objects from one image which overlap with objects from a second image by a specified area (2D) or volume (3D) range. All primary objects which are covered less or more than the specified range values will be excluded from the analysis. The remaining ones will be extracted in a separate image. Original primary objects can also be displayed with the actual volume coverage. Original statistics for all objects are displayed in one table if desired while extraction statistics are displayed in a seperate table (_OE3D_Statistics_)

![image](https://user-images.githubusercontent.com/10721817/151672100-7a913fc8-cf9f-46ee-bbfc-a5d49ffac5cc.png)

### Post Processor
This tool is meant to be used on binary images or labels but can be used for most functions also as a normal image filter tool. This way, is partially the counter part of the [Filter Check](https://imagej.net/plugins/biovoxxel-toolbox#filter-check)

![image](https://user-images.githubusercontent.com/10721817/151672895-30af6deb-67b4-45fb-ac6d-5deb9777c8a7.png)

_Ongoing development: more filter functions will be added in future_

### 3D Neighbor Analysis
planned and coming in near future

### 

## Installation
The Biovoxxel Toolbox as well as the BioVoxxel 3D Box are distributed via the [BioVoxxel Fiji update site](https://imagej.net/update-sites/following)

## Issues
[https://github.com/biovoxxel/bv3dbox/issues](https://github.com/biovoxxel/bv3dbox/issues)

## Contact
via [e-mail](mailto:jan.brocher@biovoxxel.de)
via [BioVoxxel gitter channel](https://gitter.im/biovoxxel/BioVoxxel_Toolbox)

## Acknowledgement
The BioVoxxel 3D Box funtions are heavily based and rely strongly on the CLIJ library family.
Therefore, this development would have not been possible without the work of Robert Haase and colleagues.

Robert Haase, Loic Alain Royer, Peter Steinbach, Deborah Schmidt, Alexandr Dibrov, Uwe Schmidt, Martin Weigert, Nicola Maghelli, Pavel Tomancak, Florian Jug, Eugene W Myers. [CLIJ: GPU-accelerated image processing for everyone. Nat Methods (2019)](https://doi.org/10.1038/s41592-019-0650-1)
