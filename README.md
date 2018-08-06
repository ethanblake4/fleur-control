# fleur-control
This is the Android control app for the in-progress Fleur hoverdrone project.

![Screenshot](https://user-images.githubusercontent.com/9874748/43705025-4c131682-9916-11e8-89bf-86eb84a2a980.png)


## Project Overview
The [Fleur project](https://docs.google.com/document/d/1A1V8CYays-iltLlI5e2uotQGhuW_GCRXdnoU04Tryas/edit?usp=sharing) aims to create a working electric hoverdrone capable of manned flight. 

The current design is capable of a theoretical half hour of flight time at near sea level with a 200 lb load. 

## App Overview
The Fleur Control Android app directly interfaces with the hoverdrone hardware through the [IOIO-OTG V2](https://www.sparkfun.com/products/13613) microcontroller.

This app utilizes various strategies to obtain precise, accurate, and low-latency position and rotation estimates. 
* On modern devices, this is accomplished effectively with ARCore, which combines sensor and camera inputs for an accurate 60fps position estimate.
* For older devices, the app can revert to OpenCV's optical flow implementation along with gyro/accelerometer sensor fusion.
* Overall location is integrated over time with GPS position to enable navigation and "return-to-home" features.
