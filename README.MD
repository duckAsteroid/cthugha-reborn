# Cthugha Reborn

This project is an attempt to revive the 1990's music visualisation software Cthugha
originally written by Kevin "Zaph" Burfitt.

While at University in the late 90s I spent many happy evenings playing with Cthugha while
listening to my favourite CDs of the time. Fast forward to 2022 and while laid out and isolating 
with COVID-19 I had something of a 90s revival and wanted to resurrect the software and tinker with 
it. After playing with the original and Ctugha95 variants I decide I would try to port to
Java so I could ultimately maybe make it work on my Android and Google Home devices.

Much of the project was created by reverse engineering the original C code and converting
to Java.

## How does it work?

The application is written in Java and uses AWT for core graphics code. Sound data is captured
using the core `javax.sound` APIs.

Lots of the core graphics processing is done using palettized raster graphics. An array of pixels 
representing the rendered screen index into a 256 colour palette, where each colour is represented 
by a traditional 24 bit (RGB) value.  

The application runs a core loop that does the following:
  1. Performs a [map based translation](#map-based-translation) on the screen buffer
  2. Renders a [flame function](#flame-function) across the screen buffer
  3. Gathers a [sample of audio data](#audio-sampling)
  4. Renders various [audio waveforms](#waveform-rendering) onto the buffer
  5. Renders the [screenbuffer onto the screen](#rendering-on-screen) using double buffering

### Map based translation

This renders most of the crazy visuals by translating pixels around the screen. This is done by
giving the pixels in the buffer a numerical index (starting top left, proceeding across the screen 
and down). The map tells the application the source location for each pixel in the next frame. 

For example in a simple 3x3 buffer the pixels are numbered:

    0 1 2
    3 4 5
    6 7 8

To render a clockwise rotation animation we have the following map:

    3 0 1
    6 4 2
    7 8 5

In the original application these TAB matrices were stored in files and were loaded from disk. In
the current implementation these are generated on the fly by various random
variations on the TAB generator code (reverse engineered from C).

### Flame function

This performs a convolution kernel operation on the pixels (like a blur). The current implementation
averages values of 4 pixels in any square into the 1 on the bottom left.

### Audio Sampling

This goes to the current audio source and acquires a buffer of the audio sample data that has
been read since the last loop.

### Waveform Rendering

Take the waverform data and render an on screen view of that. There are currently several waveforms:
  1. A standard oscilliscope linear waveform time series amplitude. This can be split into two for
stereo channels.
  2. A circular linear waveform where amplitude represents the radius and time is represented by the
rotational angle.
  3. A spectral

### Rendering on screen

# To do list

* Save current config of all screen elements to a favourite list
* Randomly pick from favourite list
* Allow sequential selection of MAPs
* More TABs
  * Screen Quadrant reflections? L->R, R<-L etc.
  * Blow to edges (and slow at edge) - like pouring off top of sphere
* Cache MAPs (and images?) to avoid loading/parsing delays
* Strings - especially when quiet
* ~~Image files splashed over screen~~
* Other flame functions
* ~~Auto rotate WAVE~~
* Stereo WAVE
* FFT on audio 
  * Display Retro EQ style? https://www.youtube.com/watch?v=X21mgZcPLFY&t=1s
* More WAVEs
  * ~~Circle around co-ord distance from centre = amplitude~~
  * ~~Pulsing circle~~(circles = stereo?)
  * Animate between line and circle? esp. if circle implemented as polyline
* Beat detection:
  * Auto animate WAVE from beat
  * Auto randomise on beat
* ~~Lightning (whole screen) flashes?~~
* Screenshots to folder (for the website)
  * Screen recording?
* Proper full screen mode
* Dual window (debug stuff in one?)
