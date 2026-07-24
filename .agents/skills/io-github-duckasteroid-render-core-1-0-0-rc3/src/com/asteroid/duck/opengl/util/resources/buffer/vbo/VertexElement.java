package com.asteroid.duck.opengl.util.resources.buffer.vbo;

/**
 * A single named data attribute within a vertex record, as stored in a {@link VertexBufferObject}.
 *
 * <p>A {@code VertexElement} binds together:</p>
 * <ul>
 *   <li>A {@link VertexElementType} that defines the GL scalar type, component count, byte size,
 *       and Java serialization/deserialization logic.</li>
 *   <li>A {@code name} that must match the corresponding {@code in} variable in the vertex shader.</li>
 * </ul>
 *
 * <p>Elements are declared once and shared across multiple structures or VBOs; they are immutable
 * value objects (records).</p>
 *
 * <p>Example:</p>
 * <pre>{@code
 * VertexElement position = new VertexElement(VertexElementType.VEC_2F, "position");
 * VertexElement colour   = new VertexElement(VertexElementType.VEC_3F, "colour");
 * }</pre>
 *
 * @param type the serialization type describing the GL data format and its Java counterpart
 * @param name the shader attribute variable name this element maps to
 * @see VertexElementType
 * @see VertexDataStructure
 */
public record VertexElement(VertexElementType<?> type, String name) {

	/**
	 * Validates that a runtime value is assignment-compatible with this element's Java type.
	 *
	 * <p>A {@code null} value is always accepted; non-null values are checked via
	 * {@link Class#isAssignableFrom(Class)}.</p>
	 *
	 * @param value the value to check, may be null
	 * @throws IllegalArgumentException if {@code value} is non-null and its type is not
	 *                                  assignable to {@link VertexElementType#getJavaType()}
	 */
	public void checkInstanceOf(Object value) throws IllegalArgumentException {
		if (value != null) {
			Class<?> actualType = value.getClass();
			Class<?> expectedType = type.getJavaType();
			if (!expectedType.isAssignableFrom(actualType)) {
				throw new IllegalArgumentException(name + ": "+actualType.getName() +" is not of type " + expectedType.getName());
			}
		}
	}

}
