Translate is an array where

Render to texture:
https://www.opengl-tutorial.org/intermediate-tutorials/tutorial-14-render-to-texture/

Writing to buffers quickly from CPU
https://www.khronos.org/opengl/wiki/Buffer_Object_Streaming

Could we get the AudioSystem to write to the GPU buffer memory in it's native
format? Avoid transforming every sample?

A ring buffer for FFT length vs. wave drawing length?

FFT heat map - which signals are most common? Add FFT spectra together over time and
take an average..


## Transform Map

Screen sizes:

    1,327,104 (1536x864)
    2,073,600 (1920x1080)
    8,294,400 (3840x2160)

4,294,967,295 = UInt32 MAX
2,147,483,647 = Int32 MAX

So 32 bit per cell is more than enough to represent a full screen addressable
map.

Transform map can be passed as texture

## Flame (blur)
https://stackoverflow.com/questions/5243983/what-is-the-most-efficient-way-to-implement-a-convolution-filter-within-a-pixel
> in the particular case of a blur filter (Gauss or not), which are all kind of "weighted sums", you can take advantage of texture interpolation, which may be faster for small kernel sizes (but definitively not for large kernel sizes).

Gaussian
https://www.rastergrid.com/blog/2010/09/efficient-gaussian-blur-with-linear-sampling/

```glsl
uniform sampler2D image;
 
out vec4 FragmentColor;
 
uniform float offset[3] = float[](0.0, 1.3846153846, 3.2307692308);
uniform float weight[3] = float[](0.2270270270, 0.3162162162, 0.0702702703);
 
void main(void) {
    FragmentColor = texture2D(image, vec2(gl_FragCoord) / 1024.0) * weight[0];
    for (int i=1; i<3; i++) {
        FragmentColor +=
            texture2D(image, (vec2(gl_FragCoord) + vec2(0.0, offset[i])) / 1024.0)
                * weight[i];
        FragmentColor +=
            texture2D(image, (vec2(gl_FragCoord) - vec2(0.0, offset[i])) / 1024.0)
                * weight[i];
    }
}
```
This will need updating to GLSL 4

## Colour Map

The original Cthugha uses a 256 bit colour map - there's
nothing really to stop us using a longer colour map as a uniform shader param
and then using this in the frag shader to lookup colours?

# Setup

