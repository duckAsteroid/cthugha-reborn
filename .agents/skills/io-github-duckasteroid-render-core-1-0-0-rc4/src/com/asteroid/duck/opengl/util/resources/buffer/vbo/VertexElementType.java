package com.asteroid.duck.opengl.util.resources.buffer.vbo;

import com.asteroid.duck.opengl.util.resources.shader.vars.ShaderVariableType;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.nio.ByteBuffer;
import java.util.Objects;

import static org.lwjgl.opengl.GL11.GL_FLOAT;

/**
 * Strategy object that bridges a Java type {@code T} and a packed OpenGL vertex attribute.
 *
 * <p>Each instance encodes three closely related pieces of metadata:</p>
 * <ul>
 *   <li><b>Java type</b> – the high-level Java class accepted by {@link #serializeRaw} and
 *       returned by {@link #deserializeRaw}.</li>
 *   <li><b>Dimensions</b> – the number of scalar components (e.g. 1 for {@code float}, 3 for
 *       {@link Vector3f}).</li>
 *   <li><b>GL type</b> – the OpenGL scalar type constant used in
 *       {@code glVertexAttribPointer} (currently always {@code GL_FLOAT}).</li>
 * </ul>
 *
 * <p>Pre-built singletons cover the most common cases:</p>
 * <ul>
 *   <li>{@link #FLOAT}   – single {@code float}</li>
 *   <li>{@link #VEC_2F}  – {@link Vector2f}</li>
 *   <li>{@link #VEC_3F}  – {@link Vector3f}</li>
 *   <li>{@link #VEC_4F}  – {@link Vector4f}</li>
 * </ul>
 *
 * <p>Custom types can be added by subclassing and implementing {@link #serialize} and
 * {@link #deserialize}.</p>
 *
 * @param <T> the Java type of the value stored in this element
 * @see VertexElement
 * @see VertexDataStructure
 */
public abstract class VertexElementType<T> {
	private final Class<T> javaType;
	private final int dimensions;
	private final int glType;

	/**
	 * Constructs a new type descriptor.
	 *
	 * @param t      Java class for this type
	 * @param s      number of scalar components
	 * @param glType OpenGL scalar type constant (e.g. {@code GL_FLOAT})
	 */
	public VertexElementType(Class<T> t, int s, int glType) {
		this.javaType = t;
		this.dimensions = s;
		this.glType = glType;
	}

	/**
	 * Serializes an untyped value into the buffer, applying a null-replacement when necessary.
	 *
	 * <p>If {@code obj} is null, {@link #nullReplacementValue()} is used. The value is then
	 * cast to {@code T} and delegated to {@link #serialize(Object, ByteBuffer)}.</p>
	 *
	 * @param obj    value to serialize; may be null if the type supports a null replacement
	 * @param buffer destination buffer; must have sufficient remaining capacity
	 * @throws IllegalArgumentException if {@code obj} is not an instance of {@link #getJavaType()}
	 */
	public void serializeRaw(Object obj, ByteBuffer buffer) {
		try {
			if (obj == null) {
				obj = nullReplacementValue();
			}
			serialize(javaType.cast(obj), buffer);
		} catch (ClassCastException e) {
			throw new IllegalArgumentException("Object is not of type " + javaType.getName(), e);
		}
	}

	/**
	 * Returns the OpenGL scalar type constant for this element's components.
	 *
	 * @return GL scalar type (e.g. {@code GL_FLOAT})
	 */
	public int glType() {
		return glType;
	}

	/**
	 * Returns the number of scalar components in this element.
	 *
	 * <p>For example, {@link #FLOAT} returns 1 and {@link #VEC_3F} returns 3.</p>
	 *
	 * @return component count
	 */
	public int dimensions() {
		return dimensions;
	}

	/**
	 * Returns the byte size of this element within a vertex record.
	 *
	 * <p><b>Note:</b> currently assumes 4 bytes per component, i.e. all elements use
	 * {@code GL_FLOAT}. This will need revisiting if integer or half-float types are added.</p>
	 *
	 * @return byte size of one element
	 */
	public int byteSize() {
		// FIXME what to do for non FLOAT elements
		return dimensions * 4;
	}

	/**
	 * Returns the Java class corresponding to this element's type.
	 *
	 * @return Java type class
	 */
	public Class<T> getJavaType() {
		return javaType;
	}

	/**
	 * Serializes a typed value into a {@link ByteBuffer}.
	 *
	 * <p>Implementations must write exactly {@link #byteSize()} bytes into {@code buffer}.</p>
	 *
	 * @param obj    strongly-typed value to serialize
	 * @param buffer destination buffer
	 */
	protected abstract void serialize(T obj, ByteBuffer buffer);

	/**
	 * Deserializes a value from a {@link ByteBuffer} without type safety.
	 *
	 * @param buffer source buffer positioned at the start of this element's bytes
	 * @return deserialized value as {@code Object}
	 */
	public Object deserializeRaw(ByteBuffer buffer) {
		return deserialize(buffer);
	}

	/**
	 * Deserializes a strongly-typed value from a {@link ByteBuffer}.
	 *
	 * <p>Implementations must consume exactly {@link #byteSize()} bytes from {@code buffer}.</p>
	 *
	 * @param buffer source buffer positioned at the start of this element's bytes
	 * @return deserialized value
	 */
	protected abstract T deserialize(ByteBuffer buffer);

	/**
	 * Returns a non-null default value used when {@code null} is supplied during serialization.
	 *
	 * <p>The default implementation throws, indicating the type does not support null values.
	 * Subclasses should override to return a sensible zero-equivalent (e.g. {@code 0f},
	 * {@code new Vector3f(0)}).</p>
	 *
	 * @return null-replacement value
	 * @throws IllegalArgumentException always, unless overridden
	 */
	public Object nullReplacementValue() {
		throw new IllegalArgumentException("Serialized value of "+javaType.getName()+" cannot be null");
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass().equals(o.getClass())) return false;
		VertexElementType<?> that = (VertexElementType<?>) o;
		return glType == that.glType &&
						dimensions == that.dimensions &&
						Objects.equals(javaType, that.javaType);
	}

	@Override
	public int hashCode() {
		return Objects.hash(javaType, dimensions, glType);
	}

	@Override
	public String toString() {
		return "[%s, %dx%d]".formatted(javaType.getName(), dimensions, glType);
	}

	/**
	 * Creates a {@link VertexElementType} from a {@link ShaderVariableType}.
	 *
	 * @param svt shader variable type to map
	 * @return corresponding pre-built {@code VertexElementType} singleton
	 * @throws IllegalArgumentException if no mapping exists for {@code svt}
	 */
	public static VertexElementType<?> from(ShaderVariableType svt) {
		return switch (svt) {
			case FLOAT -> FLOAT;
			case FLOAT_VEC2 -> VEC_2F;
			case FLOAT_VEC3 -> VEC_3F;
			case FLOAT_VEC4 -> VEC_4F;
			default -> throw new IllegalArgumentException("No VertexElementType for ShaderVariableType: " + svt);
		};
	}

	// --- Pre-built singleton instances -----------------------------------------------------------

	/** Single {@code float} component. Null serializes as {@code 0f}. */
	public static VertexElementType<Float> FLOAT = new VertexElementType<>(Float.class, 1, GL_FLOAT) {

		public Float deserialize(ByteBuffer byteBuffer) {
			return byteBuffer.getFloat();
		}

		public void serialize(Float f, ByteBuffer target) {
			target.putFloat(f);
		}

		@Override
		public Object nullReplacementValue() {
			return 0f;
		}
	};

	/** Two-component float vector ({@link Vector2f}). Null serializes as {@code (0, 0)}. */
	public static VertexElementType<Vector2f> VEC_2F = new VertexElementType<>(Vector2f.class,  2, GL_FLOAT) {

		public Vector2f deserialize(ByteBuffer byteBuffer) {
			float x = byteBuffer.getFloat();
			float y = byteBuffer.getFloat();
			return new Vector2f(x, y);
		}

		public void serialize(Vector2f vec, ByteBuffer byteBuffer) {
			byteBuffer.putFloat(vec.x).putFloat(vec.y);
		}

		@Override
		public Object nullReplacementValue() {
			return new Vector2f(0f);
		}
	};

	/** Three-component float vector ({@link Vector3f}). Null serializes as {@code (0, 0, 0)}. */
	public static VertexElementType<Vector3f> VEC_3F = new VertexElementType<>(Vector3f.class, 3, GL_FLOAT) {

		public Vector3f deserialize(ByteBuffer byteBuffer) {
			float x = byteBuffer.getFloat();
			float y = byteBuffer.getFloat();
			float z = byteBuffer.getFloat();
			return new Vector3f(x,y,z);
		}

		public void serialize(Vector3f vec, ByteBuffer byteBuffer) {
			byteBuffer.putFloat(vec.x).putFloat(vec.y).putFloat(vec.z);
		}

		@Override
		public Object nullReplacementValue() {
			return new Vector3f(0f);
		}
	};

	/** Four-component float vector ({@link Vector4f}). Null serializes as {@code (0, 0, 0, 0)}. */
	public static final VertexElementType<Vector4f> VEC_4F = new VertexElementType<>(Vector4f.class,4,GL_FLOAT) {
		public Vector4f deserialize(ByteBuffer byteBuffer) {
			float x = byteBuffer.getFloat();
			float y = byteBuffer.getFloat();
			float z = byteBuffer.getFloat();
			float w = byteBuffer.getFloat();
			return new Vector4f(x,y,z,w);
		}

		public void serialize(Vector4f vec, ByteBuffer byteBuffer) {
			byteBuffer.putFloat(vec.x).putFloat(vec.y).putFloat(vec.z).putFloat(vec.w);
		}

		@Override
		public Object nullReplacementValue() {
			return new Vector4f(0f);
		}
	};


}
