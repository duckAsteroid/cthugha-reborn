package com.asteroid.duck.opengl.util.resources.shader.vars;

import com.asteroid.duck.opengl.util.GLCoded;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL21.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.opengl.GL46.*;

/**
 * An enum over the Open GL shader (uniform/attribute) variable data types:
 * <a href="https://registry.khronos.org/OpenGL-Refpages/gl4/html/glGetActiveUniform.xhtml">Original table</a>
 */
public enum ShaderVariableType implements GLCoded {
	/** GLSL {@code float} — single-precision 32-bit scalar. */
	FLOAT(GL_FLOAT,"float"),
	/** GLSL {@code vec2} — two-component single-precision float vector. */
	FLOAT_VEC2(GL_FLOAT_VEC2,"vec2"),
	/** GLSL {@code vec3} — three-component single-precision float vector. */
	FLOAT_VEC3(GL_FLOAT_VEC3,"vec3"),
	/** GLSL {@code vec4} — four-component single-precision float vector, used for colours and homogeneous coordinates. */
	FLOAT_VEC4(GL_FLOAT_VEC4,"vec4"),
	/** GLSL {@code double} — 64-bit double-precision scalar; requires GL 4.0+. */
	DOUBLE(GL_DOUBLE,"double"),
	/** GLSL {@code dvec2} — two-component double-precision vector. */
	DOUBLE_VEC2(GL_DOUBLE_VEC2,"dvec2"),
	/** GLSL {@code dvec3} — three-component double-precision vector. */
	DOUBLE_VEC3(GL_DOUBLE_VEC3,"dvec3"),
	/** GLSL {@code dvec4} — four-component double-precision vector. */
	DOUBLE_VEC4(GL_DOUBLE_VEC4,"dvec4"),
	/** GLSL {@code int} — signed 32-bit integer scalar. */
	INT(GL_INT,"int"),
	/** GLSL {@code ivec2} — two-component signed integer vector. */
	INT_VEC2(GL_INT_VEC2,"ivec2"),
	/** GLSL {@code ivec3} — three-component signed integer vector. */
	INT_VEC3(GL_INT_VEC3,"ivec3"),
	/** GLSL {@code ivec4} — four-component signed integer vector. */
	INT_VEC4(GL_INT_VEC4,"ivec4"),
	/** GLSL {@code unsigned int} — unsigned 32-bit integer scalar. */
	UNSIGNED_INT(GL_UNSIGNED_INT,"unsigned int"),
	/** GLSL {@code uvec2} — two-component unsigned integer vector. */
	UNSIGNED_INT_VEC2(GL_UNSIGNED_INT_VEC2,"uvec2"),
	/** GLSL {@code uvec3} — three-component unsigned integer vector. */
	UNSIGNED_INT_VEC3(GL_UNSIGNED_INT_VEC3,"uvec3"),
	/** GLSL {@code uvec4} — four-component unsigned integer vector. */
	UNSIGNED_INT_VEC4(GL_UNSIGNED_INT_VEC4,"uvec4"),
	/** GLSL {@code bool} — boolean scalar; equivalent to an {@code int} uniform under the hood. */
	BOOL(GL_BOOL,"bool"),
	/** GLSL {@code bvec2} — two-component boolean vector. */
	BOOL_VEC2(GL_BOOL_VEC2,"bvec2"),
	/** GLSL {@code bvec3} — three-component boolean vector. */
	BOOL_VEC3(GL_BOOL_VEC3,"bvec3"),
	/** GLSL {@code bvec4} — four-component boolean vector. */
	BOOL_VEC4(GL_BOOL_VEC4,"bvec4"),
	/** GLSL {@code mat2} — 2×2 column-major float matrix. */
	FLOAT_MAT2(GL_FLOAT_MAT2,"mat2"),
	/** GLSL {@code mat3} — 3×3 column-major float matrix; commonly used for normal transforms. */
	FLOAT_MAT3(GL_FLOAT_MAT3,"mat3"),
	/** GLSL {@code mat4} — 4×4 column-major float matrix; the standard model/view/projection matrix type. */
	FLOAT_MAT4(GL_FLOAT_MAT4,"mat4"),
	/** GLSL {@code mat2x3} — 2 columns, 3 rows float matrix. */
	FLOAT_MAT2x3(GL_FLOAT_MAT2x3,"mat2x3"),
	/** GLSL {@code mat2x4} — 2 columns, 4 rows float matrix. */
	FLOAT_MAT2x4(GL_FLOAT_MAT2x4,"mat2x4"),
	/** GLSL {@code mat3x2} — 3 columns, 2 rows float matrix. */
	FLOAT_MAT3x2(GL_FLOAT_MAT3x2,"mat3x2"),
	/** GLSL {@code mat3x4} — 3 columns, 4 rows float matrix. */
	FLOAT_MAT3x4(GL_FLOAT_MAT3x4,"mat3x4"),
	/** GLSL {@code mat4x2} — 4 columns, 2 rows float matrix. */
	FLOAT_MAT4x2(GL_FLOAT_MAT4x2,"mat4x2"),
	/** GLSL {@code mat4x3} — 4 columns, 3 rows float matrix. */
	FLOAT_MAT4x3(GL_FLOAT_MAT4x3,"mat4x3"),
	/** GLSL {@code dmat2} — 2×2 double-precision matrix; requires GL 4.0+. */
	DOUBLE_MAT2(GL_DOUBLE_MAT2,"dmat2"),
	/** GLSL {@code dmat3} — 3×3 double-precision matrix. */
	DOUBLE_MAT3(GL_DOUBLE_MAT3,"dmat3"),
	/** GLSL {@code dmat4} — 4×4 double-precision matrix. */
	DOUBLE_MAT4(GL_DOUBLE_MAT4,"dmat4"),
	/** GLSL {@code dmat2x3} — 2 columns, 3 rows double-precision matrix. */
	DOUBLE_MAT2x3(GL_DOUBLE_MAT2x3,"dmat2x3"),
	/** GLSL {@code dmat2x4} — 2 columns, 4 rows double-precision matrix. */
	DOUBLE_MAT2x4(GL_DOUBLE_MAT2x4,"dmat2x4"),
	/** GLSL {@code dmat3x2} — 3 columns, 2 rows double-precision matrix. */
	DOUBLE_MAT3x2(GL_DOUBLE_MAT3x2,"dmat3x2"),
	/** GLSL {@code dmat3x4} — 3 columns, 4 rows double-precision matrix. */
	DOUBLE_MAT3x4(GL_DOUBLE_MAT3x4,"dmat3x4"),
	/** GLSL {@code dmat4x2} — 4 columns, 2 rows double-precision matrix. */
	DOUBLE_MAT4x2(GL_DOUBLE_MAT4x2,"dmat4x2"),
	/** GLSL {@code dmat4x3} — 4 columns, 3 rows double-precision matrix. */
	DOUBLE_MAT4x3(GL_DOUBLE_MAT4x3,"dmat4x3"),
	/** GLSL {@code sampler1D} — samples a 1-D texture; used e.g. for palette/LUT lookups. */
	SAMPLER_1D(GL_SAMPLER_1D,"sampler1D"),
	/** GLSL {@code sampler2D} — the most common sampler; samples a 2-D texture. */
	SAMPLER_2D(GL_SAMPLER_2D,"sampler2D"),
	/** GLSL {@code sampler3D} — samples a 3-D (volume) texture. */
	SAMPLER_3D(GL_SAMPLER_3D,"sampler3D"),
	/** GLSL {@code samplerCube} — samples a cube-map texture by a direction vector. */
	SAMPLER_CUBE(GL_SAMPLER_CUBE,"samplerCube"),
	/** GLSL {@code sampler1DShadow} — 1-D depth texture with hardware percentage-closer filtering. */
	SAMPLER_1D_SHADOW(GL_SAMPLER_1D_SHADOW,"sampler1DShadow"),
	/** GLSL {@code sampler2DShadow} — 2-D depth texture with hardware PCF; the standard shadow map sampler. */
	SAMPLER_2D_SHADOW(GL_SAMPLER_2D_SHADOW,"sampler2DShadow"),
	/** GLSL {@code sampler1DArray} — array of 1-D textures indexed by the second texture coordinate. */
	SAMPLER_1D_ARRAY(GL_SAMPLER_1D_ARRAY,"sampler1DArray"),
	/** GLSL {@code sampler2DArray} — array of 2-D textures; the third coordinate selects the layer. */
	SAMPLER_2D_ARRAY(GL_SAMPLER_2D_ARRAY,"sampler2DArray"),
	/** GLSL {@code sampler1DArrayShadow} — array of 1-D depth textures with PCF. */
	SAMPLER_1D_ARRAY_SHADOW(GL_SAMPLER_1D_ARRAY_SHADOW,"sampler1DArrayShadow"),
	/** GLSL {@code sampler2DArrayShadow} — array of 2-D depth textures with PCF. */
	SAMPLER_2D_ARRAY_SHADOW(GL_SAMPLER_2D_ARRAY_SHADOW,"sampler2DArrayShadow"),
	/** GLSL {@code sampler2DMS} — samples a multisampled 2-D texture; requires explicit sample index. */
	SAMPLER_2D_MULTISAMPLE(GL_SAMPLER_2D_MULTISAMPLE,"sampler2DMS"),
	/** GLSL {@code sampler2DMSArray} — array of multisampled 2-D textures. */
	SAMPLER_2D_MULTISAMPLE_ARRAY(GL_SAMPLER_2D_MULTISAMPLE_ARRAY,"sampler2DMSArray"),
	/** GLSL {@code samplerCubeShadow} — cube-map depth texture with PCF; used for omnidirectional shadow mapping. */
	SAMPLER_CUBE_SHADOW(GL_SAMPLER_CUBE_SHADOW,"samplerCubeShadow"),
	/** GLSL {@code samplerBuffer} — samples a buffer texture (TBO); 1-D, no filtering, large capacity. */
	SAMPLER_BUFFER(GL_SAMPLER_BUFFER,"samplerBuffer"),
	/** GLSL {@code sampler2DRect} — samples a non-power-of-two rectangle texture using non-normalised coordinates. */
	SAMPLER_2D_RECT(GL_SAMPLER_2D_RECT,"sampler2DRect"),
	/** GLSL {@code sampler2DRectShadow} — rectangle texture depth sampler with PCF. */
	SAMPLER_2D_RECT_SHADOW(GL_SAMPLER_2D_RECT_SHADOW,"sampler2DRectShadow"),
	/** GLSL {@code isampler1D} — integer-typed 1-D texture sampler. */
	INT_SAMPLER_1D(GL_INT_SAMPLER_1D,"isampler1D"),
	/** GLSL {@code isampler2D} — integer-typed 2-D texture sampler. */
	INT_SAMPLER_2D(GL_INT_SAMPLER_2D,"isampler2D"),
	/** GLSL {@code isampler3D} — integer-typed 3-D texture sampler. */
	INT_SAMPLER_3D(GL_INT_SAMPLER_3D,"isampler3D"),
	/** GLSL {@code isamplerCube} — integer-typed cube-map sampler. */
	INT_SAMPLER_CUBE(GL_INT_SAMPLER_CUBE,"isamplerCube"),
	/** GLSL {@code isampler1DArray} — integer-typed 1-D array sampler. */
	INT_SAMPLER_1D_ARRAY(GL_INT_SAMPLER_1D_ARRAY,"isampler1DArray"),
	/** GLSL {@code isampler2DArray} — integer-typed 2-D array sampler. */
	INT_SAMPLER_2D_ARRAY(GL_INT_SAMPLER_2D_ARRAY,"isampler2DArray"),
	/** GLSL {@code isampler2DMS} — integer-typed multisampled 2-D sampler. */
	INT_SAMPLER_2D_MULTISAMPLE(GL_INT_SAMPLER_2D_MULTISAMPLE,"isampler2DMS"),
	/** GLSL {@code isampler2DMSArray} — integer-typed multisampled 2-D array sampler. */
	INT_SAMPLER_2D_MULTISAMPLE_ARRAY(GL_INT_SAMPLER_2D_MULTISAMPLE_ARRAY,"isampler2DMSArray"),
	/** GLSL {@code isamplerBuffer} — integer-typed buffer texture sampler. */
	INT_SAMPLER_BUFFER(GL_INT_SAMPLER_BUFFER,"isamplerBuffer"),
	/** GLSL {@code isampler2DRect} — integer-typed rectangle texture sampler. */
	INT_SAMPLER_2D_RECT(GL_INT_SAMPLER_2D_RECT,"isampler2DRect"),
	/** GLSL {@code usampler1D} — unsigned integer 1-D texture sampler. */
	UNSIGNED_INT_SAMPLER_1D(GL_UNSIGNED_INT_SAMPLER_1D,"usampler1D"),
	/** GLSL {@code usampler2D} — unsigned integer 2-D texture sampler. */
	UNSIGNED_INT_SAMPLER_2D(GL_UNSIGNED_INT_SAMPLER_2D,"usampler2D"),
	/** GLSL {@code usampler3D} — unsigned integer 3-D texture sampler. */
	UNSIGNED_INT_SAMPLER_3D(GL_UNSIGNED_INT_SAMPLER_3D,"usampler3D"),
	/** GLSL {@code usamplerCube} — unsigned integer cube-map sampler. */
	UNSIGNED_INT_SAMPLER_CUBE(GL_UNSIGNED_INT_SAMPLER_CUBE,"usamplerCube"),
	/** GLSL {@code usampler1DArray} — unsigned integer 1-D array sampler. */
	UNSIGNED_INT_SAMPLER_1D_ARRAY(GL_UNSIGNED_INT_SAMPLER_1D_ARRAY,"usampler2DArray"),
	/** GLSL {@code usampler2DArray} — unsigned integer 2-D array sampler. */
	UNSIGNED_INT_SAMPLER_2D_ARRAY(GL_UNSIGNED_INT_SAMPLER_2D_ARRAY,"usampler2DArray"),
	/** GLSL {@code usampler2DMS} — unsigned integer multisampled 2-D sampler. */
	UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE(GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE,"usampler2DMS"),
	/** GLSL {@code usampler2DMSArray} — unsigned integer multisampled 2-D array sampler. */
	UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY(GL_UNSIGNED_INT_SAMPLER_2D_MULTISAMPLE_ARRAY,"usampler2DMSArray"),
	/** GLSL {@code usamplerBuffer} — unsigned integer buffer texture sampler. */
	UNSIGNED_INT_SAMPLER_BUFFER(GL_UNSIGNED_INT_SAMPLER_BUFFER,"usamplerBuffer"),
	/** GLSL {@code usampler2DRect} — unsigned integer rectangle texture sampler. */
	UNSIGNED_INT_SAMPLER_2D_RECT(GL_UNSIGNED_INT_SAMPLER_2D_RECT,"usampler2DRect"),
	/** GLSL {@code image1D} — read/write access to a single layer of a 1-D image (requires image load/store, GL 4.2+). */
	IMAGE_1D(GL_IMAGE_1D,"image1D"),
	/** GLSL {@code image2D} — read/write access to a 2-D image; the most common image unit type. */
	IMAGE_2D(GL_IMAGE_2D,"image2D"),
	/** GLSL {@code image3D} — read/write access to a 3-D (volume) image. */
	IMAGE_3D(GL_IMAGE_3D,"image3D"),
	/** GLSL {@code image2DRect} — read/write access to a non-power-of-two rectangle image. */
	IMAGE_2D_RECT(GL_IMAGE_2D_RECT,"image2DRect"),
	/** GLSL {@code imageCube} — read/write access to a cube-map image. */
	IMAGE_CUBE(GL_IMAGE_CUBE,"imageCube"),
	/** GLSL {@code imageBuffer} — read/write access to a buffer texture image; very large, no filtering. */
	IMAGE_BUFFER(GL_IMAGE_BUFFER,"imageBuffer"),
	/** GLSL {@code image1DArray} — read/write access to a 1-D image array. */
	IMAGE_1D_ARRAY(GL_IMAGE_1D_ARRAY,"image1DArray"),
	/** GLSL {@code image2DArray} — read/write access to a 2-D image array. */
	IMAGE_2D_ARRAY(GL_IMAGE_2D_ARRAY,"image2DArray"),
	/** GLSL {@code image2DMS} — read/write access to a multisampled 2-D image. */
	IMAGE_2D_MULTISAMPLE(GL_IMAGE_2D_MULTISAMPLE,"image2DMS"),
	/** GLSL {@code image2DMSArray} — read/write access to a multisampled 2-D image array. */
	IMAGE_2D_MULTISAMPLE_ARRAY(GL_IMAGE_2D_MULTISAMPLE_ARRAY,"image2DMSArray"),
	/** GLSL {@code iimage1D} — signed integer variant of {@link #IMAGE_1D}. */
	INT_IMAGE_1D(GL_INT_IMAGE_1D,"iimage1D"),
	/** GLSL {@code iimage2D} — signed integer variant of {@link #IMAGE_2D}. */
	INT_IMAGE_2D(GL_INT_IMAGE_2D,"iimage2D"),
	/** GLSL {@code iimage3D} — signed integer variant of {@link #IMAGE_3D}. */
	INT_IMAGE_3D(GL_INT_IMAGE_3D,"iimage3D"),
	/** GLSL {@code iimage2DRect} — signed integer variant of {@link #IMAGE_2D_RECT}. */
	INT_IMAGE_2D_RECT(GL_INT_IMAGE_2D_RECT,"iimage2DRect"),
	/** GLSL {@code iimageCube} — signed integer variant of {@link #IMAGE_CUBE}. */
	INT_IMAGE_CUBE(GL_INT_IMAGE_CUBE,"iimageCube"),
	/** GLSL {@code iimageBuffer} — signed integer variant of {@link #IMAGE_BUFFER}. */
	INT_IMAGE_BUFFER(GL_INT_IMAGE_BUFFER,"iimageBuffer"),
	/** GLSL {@code iimage1DArray} — signed integer variant of {@link #IMAGE_1D_ARRAY}. */
	INT_IMAGE_1D_ARRAY(GL_INT_IMAGE_1D_ARRAY,"iimage1DArray"),
	/** GLSL {@code iimage2DArray} — signed integer variant of {@link #IMAGE_2D_ARRAY}. */
	INT_IMAGE_2D_ARRAY(GL_INT_IMAGE_2D_ARRAY,"iimage2DArray"),
	/** GLSL {@code iimage2DMS} — signed integer variant of {@link #IMAGE_2D_MULTISAMPLE}. */
	INT_IMAGE_2D_MULTISAMPLE(GL_INT_IMAGE_2D_MULTISAMPLE,"iimage2DMS"),
	/** GLSL {@code iimage2DMSArray} — signed integer variant of {@link #IMAGE_2D_MULTISAMPLE_ARRAY}. */
	INT_IMAGE_2D_MULTISAMPLE_ARRAY(GL_INT_IMAGE_2D_MULTISAMPLE_ARRAY,"iimage2DMSArray"),
	/** GLSL {@code uimage1D} — unsigned integer variant of {@link #IMAGE_1D}. */
	UNSIGNED_INT_IMAGE_1D(GL_UNSIGNED_INT_IMAGE_1D,"uimage1D"),
	/** GLSL {@code uimage2D} — unsigned integer variant of {@link #IMAGE_2D}. */
	UNSIGNED_INT_IMAGE_2D(GL_UNSIGNED_INT_IMAGE_2D,"uimage2D"),
	/** GLSL {@code uimage3D} — unsigned integer variant of {@link #IMAGE_3D}. */
	UNSIGNED_INT_IMAGE_3D(GL_UNSIGNED_INT_IMAGE_3D,"uimage3D"),
	/** GLSL {@code uimage2DRect} — unsigned integer variant of {@link #IMAGE_2D_RECT}. */
	UNSIGNED_INT_IMAGE_2D_RECT(GL_UNSIGNED_INT_IMAGE_2D_RECT,"uimage2DRect"),
	/** GLSL {@code uimageCube} — unsigned integer variant of {@link #IMAGE_CUBE}. */
	UNSIGNED_INT_IMAGE_CUBE(GL_UNSIGNED_INT_IMAGE_CUBE,"uimageCube"),
	/** GLSL {@code uimageBuffer} — unsigned integer variant of {@link #IMAGE_BUFFER}. */
	UNSIGNED_INT_IMAGE_BUFFER(GL_UNSIGNED_INT_IMAGE_BUFFER,"uimageBuffer"),
	/** GLSL {@code uimage1DArray} — unsigned integer variant of {@link #IMAGE_1D_ARRAY}. */
	UNSIGNED_INT_IMAGE_1D_ARRAY(GL_UNSIGNED_INT_IMAGE_1D_ARRAY,"uimage1DArray"),
	/** GLSL {@code uimage2DArray} — unsigned integer variant of {@link #IMAGE_2D_ARRAY}. */
	UNSIGNED_INT_IMAGE_2D_ARRAY(GL_UNSIGNED_INT_IMAGE_2D_ARRAY,"uimage2DArray"),
	/** GLSL {@code uimage2DMS} — unsigned integer variant of {@link #IMAGE_2D_MULTISAMPLE}. */
	UNSIGNED_INT_IMAGE_2D_MULTISAMPLE(GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE,"uimage2DMS"),
	/** GLSL {@code uimage2DMSArray} — unsigned integer variant of {@link #IMAGE_2D_MULTISAMPLE_ARRAY}. */
	UNSIGNED_INT_IMAGE_2D_MULTISAMPLE_ARRAY(GL_UNSIGNED_INT_IMAGE_2D_MULTISAMPLE_ARRAY,"uimage2DMSArray"),
	/** GLSL {@code atomic_uint} — unsigned integer atomic counter; updated via dedicated atomic counter buffer bindings. */
	UNSIGNED_INT_ATOMIC_COUNTER(GL_UNSIGNED_INT_ATOMIC_COUNTER,"atomic_uint");

	private final String glslTypeName;
	private final int value;

	ShaderVariableType(int value, String glslTypeName) {
		this.glslTypeName = glslTypeName;
		this.value = value;
	}

	/**
	 * Look up the enum constant corresponding to the given OpenGL type token.
	 *
	 * @param i the GL type constant (e.g. {@code GL_FLOAT}, {@code GL_SAMPLER_2D})
	 * @return the matching {@link ShaderVariableType}, or {@code null} if the token is not recognised
	 */
	public static ShaderVariableType from(int i) {
		for(ShaderVariableType type : values()) {
			if (type.value == i) {
				return type;
			}
		}
		return null;
	}

	/**
	 * Return the canonical GLSL type keyword for this variable type, e.g. {@code "float"}, {@code "vec4"},
	 * {@code "sampler2D"}.
	 *
	 * @return the GLSL type name string
	 */
	public String getGlslTypeName() {
		return glslTypeName;
	}

	public int openGlCode() {
		return value;
	}

	@Override
	public String toString() {
		return name()+"["+ glslTypeName +"]";
	}
}
