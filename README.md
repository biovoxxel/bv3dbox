<script src="https://cdn.mathjax.org/mathjax/latest/MathJax.js?config=TeX-AMS-MML_HTMLorMML" type="text/javascript"></script>

# BioVoxxel 3D Box (bv3dbox)

**REMARK: Please inform me about any [issues](https://github.com/biovoxxel/bv3dbox/issues) you encounter!**


[![DOI](https://zenodo.org/badge/434949702.svg)](https://zenodo.org/badge/latestdoi/434949702)
![GitHub](https://img.shields.io/github/license/biovoxxel/bv3dbox?style=plastic)
![GitHub release (latest by date including pre-releases)](https://img.shields.io/github/v/release/biovoxxel/bv3dbox?include_prereleases&style=plastic)
![GitHub issues](https://img.shields.io/github/issues/biovoxxel/bv3dbox?style=plastic)


Most of the known [BioVoxxel Toolbox](https://github.com/biovoxxel/BioVoxxel-Toolbox) functions now for 2D and 3D images in one place. All functions are heavily based on GPU computing via the fabulous [CLIJ2 library](https://clij.github.io/). Segmentation output is based stronger on labels (intensity coding of objects) instead of ROIs. Those labels can be equivalently used like ROIs with many CLIJ2 functions. Also label images created via other tools such as [MorphoLibJ](https://imagej.net/plugins/morpholibj) are suitable inputs for any plugin using labels.

![image](https://user-images.githubusercontent.com/10721817/161776889-5ff12148-fe8c-4b12-a8a1-f46d14b7fa0b.png)

---

# Installation
The BioVoxxel 3D Box are distributed via the **BioVoxxel 3D Box** update site in Fiji.
* `Run >Help >Update...`
* Select `Manage update sites`
* <span style="color:red">The following update sites need currently to be minimally active to be able to use all functionalities of the BioVoxxel 3D Box</span>


  * **BioVoxxel 3D Box**
  * **clij**
  * **clij2**
  * **clijx-assistant**
  * **clijx-assistant-extensions**
  * **3D ImageJ Suite**

---

# Functionalities

## Filtering

### Flat Field Correction
The flat field correction allows to correct for uneven illumination including a dark-field (dark current) image subtraction. The dark-field image can also be ommited if unavailable. If the original image which sould be corrected for uneven illumination is a stack, flat-field as well as dark-field images can also be provided as single images. Those will be adapted to the original stack slice number. Otherwise, if they are not single slices, dark-field and flat-field need to have the same dimensions as the image to be corrected. 
Input images can have 8, 16, or 32-bit. _RGB images are not yet supported (will be available in the future as well)_.
The Output image is always 32-bit to account for correct float-point values after image division. 

Formula:

$$result = { original - darkfield \over flatfield - darkfield } * { average\;of\;(flatfield - darkfield) }$$


![image](https://user-images.githubusercontent.com/10721817/151598573-534b8f3f-99bd-4bb7-b420-140ca8f94ef7.png)

---

### Pseudo Flat Field Correction
The pseudo flat field correction takes a copy of the original image to be corrected and blurs it with a Gaussian Blur filter using the specified radius. If the image is a scaled 3D stack (with indicated units such as µm), the filter will also consider the correct x/y/z scaling ratio and perform the blurring accordingly in 3D space. This way the background created stays undistorted in relation to the original data. If the original image is a time series, slices should be considered independent the blurring can be forced to be done in 2D and correction will be applied slice by slice to the original.
The background image can be displayed and for easier adaption of the radius value. The blurring radius should be high enough to eliminate all traces of any original object in the background image. Only different intensity shading must remain. _If this is impossible to achieve, the method might not be suitable for that particular type of image._
In the case of a 3D image all slices can be checked using the stack slice slider.
The output will be 32-bit to account for accurate float-point pixel intensity values. Calculation is done according to the upper formula used for flat-field correction without a dark-field subtraction

![image](https://user-images.githubusercontent.com/10721817/151659090-8a4032cb-337a-402e-889f-8e7781acfe35.png)

**Important: this is a non-quantitative optical correction. Intensity values will not be corrected according to any real uneven illumination and are therefore NOT suitable for intensity quantifications anymore!**
If intensity quantification is desired and uneven illumination needs to be corrected, the [Flat Field Correction](###-flat-field-correction) must be used.

---

### Recursive Filter
A recursive filter repetitively applies the same filter on the previously filtered version of the underlying image. This keepsspecifically for the median filter shape alterations low, perfectly removes noise, homogenizes objects but still keeps border of also small objects better that a median filter with a bigger radius. It also performs efficiently due to the small filter size.

![image](https://user-images.githubusercontent.com/10721817/151659864-04528775-85e3-4980-9fb5-00fb5424838d.png)

---

## Segmentation

### Threshold Check: 
A helping tool to better identify suitable histogram-based automatic intensity thresholds, compare them qualitatively and quantitatively. This is based on the publication: [Qualitative and Quantitative Evaluation of Two New Histogram Limiting Binarization Algorithms](https://www.cscjournals.org/library/manuscriptinfo.php?mc=IJIP-829), Brocher J., IJIP (2014).

The Threshold Check allows to compare all implemented [Auto Thresholds](https://imagej.net/plugins/auto-threshold) from ImageJ (by [Gabriel Landini](https://github.com/landinig)).

The ground truth you can "define" (see below) is in this case NOT the perfect, desired outcome but rather the next best estimation. It will also contain unspecific objects (or generally pixels) if the underlying image is not pre-processed by image filtering and/or background subtraction. It just serves as the quickest possible and direct way of comparing the extraction to an approximation of an acceptable outcome!

![Screenshot 2025-02-28 171523](https://github.com/user-attachments/assets/33de71db-523e-4042-9c8c-a987cd4e7168)

The `Highlight ground truth` slider serves to highlight brighter image content and "add" it to the ground truth. While the `Threshold method` field is set to "None", objects of interest should in the best case appear completely in cyan. If any other threshold method is selected true positive (extracted) areas will appear `yellow`. In the latter case, parts highlighted still in `cyan` are not recognized by the current threshold even though being of interest (false negative), while parts shown in `red` are false positive ones compared to the specified ground truth.

The `Histogram usage` field allows to restrict the histogram during threshold calculation by ignoring black or white pixels or both. This can avoid that a big amount of saturated pixels contributes oversized to the final threshold. If "full" is chosen the original histogram is taken into account. The latter is the default setting.

In the ThresholdCheck window itself as well as in ImageJ's main window, the status bar shows the corresponding [Jaccard Index](https://en.wikipedia.org/wiki/Jaccard_index) and [Dice Coefficient](https://en.wikipedia.org/wiki/S%C3%B8rensen%E2%80%93Dice_coefficient) values for the current setup and comparison of approximated ground truth versus segmentation result are displayed. The closer to 1.0 they are the more more accurate the segmentation will be (in context of the approximated ground truth). The lower they are the lower the relative "segmentation quality".

If the `highlighted ground truth` value is kept fixed a more objective and quantitative comparison of the performance of individual Auto Thresholds can be achieved. 

![Screenshot 2025-02-28 171614](https://github.com/user-attachments/assets/76340797-0dac-49ef-ac47-07a60dd708d7)

Here the used color coding in overview:

* `blue`: non-extracted pixels which are also not part of the ground truth (_true negative_)
* `cyan`: non-extracted pixels which are part of the ground truth (_false negative_)
* `red`: extracted pixels which are NOT overlapping with the ground truth (_false positive_)
* `yellow`: extracted pixels which are part of the ground truth (_true positive_)

If the user finds a useful threshold the output can be retrieved already as a binary image. Disclaimer: Do not macro record the Thresholdcheck for batch processing. Use it just to define a suitable threshold and then record the normal AutoThreshold pluging of Fiji with the corresponding desired threshold method.

![Screenshot 2025-02-28 172210](https://github.com/user-attachments/assets/90319237-e0b2-4135-96e3-69c64b9b45c5)


Currently, stacks will automatically be considered as volume and thresholding is done on the stack histogram to achieve consistent results over the complete stack.

Slice-by-slice thresholding might come up in a future release.

---


### Voronoi Threshold Labeler
The labeler is meant to be used as a image segmentation tool combining image pre-processing using a variety of convolition filters, background subtraction methods, auto thresholding and intensity maxima detection. The latter allows object separation similar to the a watershed algorithm, but will be only effective if _Labels_ is chosen as output. Dependent on the combination of pre-processing. background subtraction, threshold and maxima detection quite variable objects can be extracted from an image.

![image](https://user-images.githubusercontent.com/10721817/152376765-39b0a628-6705-490a-a9ae-921368a67b57.png)


Parameter meaning and usage:
* `Image Filter`: diverse convolution filter methods to homogenize objects and background for improved background subtraction and object segmentation
* `Filter radius`: strength of filtering. Bigger radii homogenize more but increase processing time. _Hint: the median filter has a maximum radius of 15_
* `Background subtraction`: Diverse options to reduce unspecific signal. _TopHat_ is comparable with ImageJ's Rolling Ball method (>Process >Subtract Background). 
* `Background radius`: Strength of background homogenization. One can orient on a certain prinziple like, the bigger the objects, the bigger the radius needed.
* `Histogram usage`: either the complete histogram can be handed to the thresholding algorithm or a limited one by ignoring black or white or both (black and white) pixels to achive better segmentation results.
* `Threshold method`: Automatic intensity threshold to extract basic object areas after the upper pre-processing steps
* `Separation method`: There are different object separations. Those are meant to be used in exchange for a common _Watershed_ algorithm. The different methods are explained below in the section [Label Splitter](#label-splitter)
 
  
  The erosion methods are useful for bigger and irregularly shaped objects, while the maxima method performs better for smaller objects. The erosion-based methods ignore the field `Maxima detection radius`. Too high spot sigmas will delete smaller objects from the image.
* `Spot sigma`: Blurring strength for maxima detection OR neighborhood definition for the ersosion methods.
* `Maxima detection radius`: Defines the box neighborhood in which unique maxima are detected.
* `Output type`:
  * `Labels` (recommended): creates individual labels for separable objects with consecutive numbering.
  * `Binary`: creates a binary (0/255) image without object separation.
* `Stack slice`: allows to navigate through stack slices in 3D stacks
* `Apply on complete image`: In case the procedure is tested in a user created ROI/selection it will finally be applied to the complete image if this option is active. For very big 3D images it is strongly recommended to first test on smaller subregions to avoid running out of graphics card memory and long processing times.

3D Example:

![Original-small](https://user-images.githubusercontent.com/10721817/154435398-b3a57ca1-6a88-499a-86d9-7c3d7a9d97de.gif)   ![Extracted-small](https://user-images.githubusercontent.com/10721817/154435426-957f4c4c-50ca-405d-a876-17d0a7fed78b.gif)

---

## Labels

### Label Splitter
The label splitter is the equivalent of a watershedding function for binary images or images containing labeld objects already. It will separate objects according to the following methods. The output image will be displayed as consecutive intensity labels (intensity = identifier).
This is the last part of the [Voronoi Threshold Labeler](#voronoi-threshold-labeler) processing.
All of these functions work best on 3D isotropic voxels, so consider to run [Make 3D image isotropic](#make-3d-image-isotropic)!

Methods:
* `Separation method`: There are 3 different object separations. Those are meant to be used in exchange for a common _Watershed_ algorithm. The different methods are
  * `Maxima`: intensity maxima are determined on the original image in a square/box neighborhood defined by the `Maxima detection radius` after applying a gaussian blur on the original image in a neighborhood defined by the `Spot sigma`. The detected maxima are the seeds from which the objects are filled via a masked voronoi extension.
  * `Eroded Maxima`: takes the input image and erodes the objects using the `Spot sigma` as erosion iterations. Then it identifies intensity maxima on the eroded objects using the `Maxima detection radius`.
  * `Maxima Spheres`: the EDM (euclidean distance map) is created from the binary segmentation result, then blurred with the `Spot sigma` and maxima are detected on the blurred EDM. The underlying EDM intensity of those maxima is measured and taken as a radius of a sphere. An image with those spheres serves as seeds for the label splitting (advantage is that the EDM info reflects to some extent the object size and adapts seeds accodingly which might achieves more accurate separation). 


![3d_distance_map_animation](https://user-images.githubusercontent.com/10721817/166155091-719ad2c6-909b-4038-a44f-150be30004ab.gif)   ![3d_maxima_sphere_animation](https://user-images.githubusercontent.com/10721817/166155100-4ea48697-e7fc-463e-a426-aa5bb9cd8a60.gif)


  * `DoG Seeds`: There will be a difference of gausian (DoG) filter be applied on the segmented binary objects which leads to smooth seed objects still dependent on the original object size bit with less smaller fragmented seeds. This does not work well on big connections/bridges between objects but has advantages on fairly irregularly shaped objects.
  *  `Eroded box`: The extracted objects/areas are eroded in a square/box neighborhood defined by the `Spot sigma` and used as a seed for the same voronoi filling of the objects.
  * `Eroded sphere`: The extracted objects/areas are eroded in a circle/sphere neighborhood defined by the `Spot sigma` and used as seeds.
  
  The erosion methods are useful for bigger and irregularly shaped objects, while the maxima method performs better for smaller objects. The erosion-based methods ignore the field `Maxima detection radius`. Too high spot sigmas will delete smaller objects from the image.
* `Spot sigma`: Blurring strength for maxima detection OR neighborhood definition for the ersosion methods.
* `Maxima detection radius`: Defines the box neighborhood in which unique maxima are detected. 

![image](https://user-images.githubusercontent.com/10721817/152409487-3cfd3313-90ff-4004-871f-48b91eba554a.png)

Further separation methods are planned to be added, so stay tuned!

---

### Separate Labels

The label separator takes a label image and places a separation in form of background pixels between touching labels. This can be considered the equivalent of the standard binary watershed function in ImageJ / Fiji. This can influence further post processing such as erosion or opening functions from the Post Processor.

![image](https://user-images.githubusercontent.com/10721817/155353310-c66c9f86-cbe2-4c3a-9cfa-99a2a2a70c0d.png)


---

### Post Processor
This tool is meant to be used on binary images or labels but can be used for most functions also as a normal image filter tool. This way, is partially the counter part of the [Filter Check](https://imagej.net/plugins/biovoxxel-toolbox#filter-check)

![image](https://user-images.githubusercontent.com/10721817/151672895-30af6deb-67b4-45fb-ac6d-5deb9777c8a7.png)

_Ongoing development: more filter functions will be added in future_

---

## Analysis

### Object Inspector
The _Object Inspector_ is the new version of the [_Speckle Inspector_](https://imagej.net/plugins/biovoxxel-toolbox#speckle-inspector). It analyzes (secondary) objects inside (primary) objects.
Input parameters are:

* `Primary objects`: this is an image holding the primary (outer) objects in form of labels or a binary image. Must be provided!
* `Secondary objects`: this is an image holding the secondary (inner) objects in form of labels or a binary image. Must be provided!
* `Primary original image`: The original image related to the primary objects. This has to be provided only if pixel intensity-related values should be analyzed
* `Secondary original image`: The original image related to the secondary objects. This has to be provided only if pixel intensity-related values should be analyzed
* `Primary volume limitation`: all primary objects inside this 2D area / 3D volume range will be analyzed. All others will be excluded from analysis
* `Primary mean/max extension ratio`: This refers to the **M**ean / **M**ax **E**xtension **R**atio (ratio between the average and maximum distance of the objects' border to the centroid). This is used to exclude objects of similar size but difference in shape (similar to the circularity in the standard ImageJ `Analyze Particles...`function.
* `Secondary volume limitation`: equivalent of the above for the secondary objects.
* `Secondary mean/max extension ratio`: equivalent of the above for the secondary objects.
* `Exclude primary edge objects`: All objects which touch the image border or in 3D the image borders and the upper and lower stack slice will be excluded. All secondary objects contained in those objects will also be removed from the nalysis and output image
* `Pad stack tops`: if active it will add a black slice before the first and after the last stack slice. This avoids removing primary objects still visible in the first and last slice if _Exclude primary edge objects_ is active. To achieve proper measurements, however, it is recommended to avoid this function and acquire objects completely during imaging.
* `Show analysis label maps`: will display the analyzed objects as intensity coded labels (with new consecutive numbering)
* `Show count map`: shows labels of the primary objects with the intensity indicating the numbers of secondary objects contained inside them.

Results tables are available for primary as well as secondary objects including object counting and relational identification, size, intensity and some shape values.

![image](https://user-images.githubusercontent.com/10721817/151661661-fbc7ae90-b30b-4ffa-ac44-752a7ca37b48.png)

---

### Overlap Extractor
This tool is the new version of the [_Binary Feature Extractor_](https://imagej.net/plugins/biovoxxel-toolbox#binary-feature-extractor). It keeps objects from one image which overlap with objects from a second image by a specified area (2D) or volume (3D) range. All primary objects which are covered less or more than the specified range values will be excluded from the analysis. The remaining ones will be extracted in a separate image. Original primary objects can also be displayed with the actual volume coverage. Original statistics for all objects are displayed in one table if desired while extraction statistics are displayed in a seperate table (_OE3D_Statistics_)

![image](https://user-images.githubusercontent.com/10721817/151672100-7a913fc8-cf9f-46ee-bbfc-a5d49ffac5cc.png)

---

### 3D Neighbor Analysis
The neighbor analysis allows to analyze how many neighbor objects a specific labeled object has (intensity values in objects indicate neighbor count). In addition, the neighbor counts as well as the count distribution can be plotted.

Parameters:
* `Method`: Neighbors are determined based on...
 * `Objects`: ...the voronoi drawn on basis of the original object outline
 * `Distance`: ...the distance range given calculated from the centroid of each object to the other centroids. Therefore, object shape is neglected. Only recommended for small and isotropic objects
* `Object size range`: excludes objects with an area/volume outside the given range before the analysis
* `Distance range`: centroids not reachable within the given distance range are not considered as neighbors.
* `Exclude edges from visualization`: objects which directly touch the border or whos voronoi is touching imge edges are taken into account as neighbors of others but are finally not displayed since their own neighbor count is incorrect due to missing neighbors not part of the field of view. If the complete sample is imaged this option should not be used.
* `Plot neighbour counts`: for each input label the number of neighbors will be plotted. Those are also indicated in the neighbor count map image.
* `Plot neighbor distribution`: the distribution of neighbor frequencies is plotted. The value plotted for zero neighbors are excluded objects (due to edge exclusion).

![image](https://user-images.githubusercontent.com/10721817/152757955-fad1f18e-ccad-409c-8d8d-6b706fe25607.png)

---

## Additional Functions

### Add Labels to 3D ROI Manager
This adds all 2D or 3D labels as ROIs to the 3D ROI Manager from the magnificent [3D Suite](https://mcib3d.frama.io/3d-suite-imagej/) by [Thomas Boudier](url)

![image](https://user-images.githubusercontent.com/10721817/152698429-62a83164-01b1-40bc-a4ca-a24b8e977db0.png)

In some cases this function might run a little unstable and ROIs might not directly be visible in the ROI Manager. Either try again or play with the Live ROI activation in the 3D ROI Manager.

---

### Add Labels to 2D ROI Manager
This is based on a Groovy script from Bram van den Broek (@bramvdbroek) shown and discused [here](https://forum.image.sc/t/clij-label-map-to-roi-fast-version/51356/34)

---

### Make 3D Image Isotropic
For some operations, isotropic voxels create better segmentation results due to how the individual methods are applied to the image. Therefore, it can be advantages to convert the image into one having isotropic voxels. This function is considering the actual calibration of the image e.g. in µm and reslices the volume to create those isotropic voxels. This however will apply liniear interpolation to the intensity values and change those. So, one needs to choose between best segmentation result or most original intensity values in some cases.

---

### Convoluted Background Subtraction
The equivalent function to the original convoluted background subtraction is already on the todo list

---

## Citation
If you use this library and its functions to generate and publish results, please condider to acknowledge and cite the toolbox using the DOI.

[![DOI](https://zenodo.org/badge/434949702.svg)](https://zenodo.org/badge/latestdoi/434949702)

---

## Issues
[https://github.com/biovoxxel/bv3dbox/issues](https://github.com/biovoxxel/bv3dbox/issues)

---

## Contact
via [e-mail](mailto:jan.brocher@biovoxxel.de)
via [BioVoxxel gitter channel](https://gitter.im/biovoxxel/BioVoxxel_Toolbox)

---

## Acknowledgement
The BioVoxxel 3D Box funtions are heavily based and rely strongly on the CLIJ library family.
Therefore, this development would have not been possible without the work of Robert Haase and colleagues.

Robert Haase, Loic Alain Royer, Peter Steinbach, Deborah Schmidt, Alexandr Dibrov, Uwe Schmidt, Martin Weigert, Nicola Maghelli, Pavel Tomancak, Florian Jug, Eugene W Myers. [CLIJ: GPU-accelerated image processing for everyone. Nat Methods (2019)](https://doi.org/10.1038/s41592-019-0650-1)

J. Ollion, J. Cochennec, F. Loll, C. Escudé, T. Boudier. (2013) TANGO: A Generic Tool for High-throughput 3D Image Analysis for Studying Nuclear Organization. Bioinformatics 2013 Jul 15;29(14):1840-1. http://dx.doi.org/10.1093/bioinformatics/btt276
