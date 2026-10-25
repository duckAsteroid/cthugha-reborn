package com.asteroid.duck.opengl.util.resources.buffer.debug;

import com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject;
import com.asteroid.duck.opengl.util.resources.buffer.ebo.ElementBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexBufferObject;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexDataStructure;
import com.asteroid.duck.opengl.util.resources.buffer.vbo.VertexElement;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * A class that can "visualise" (print out) the contents of a
 * {@link com.asteroid.duck.opengl.util.resources.buffer.VertexArrayObject} (VAO).
 */
public class VertexBufferVisualiser {
	private final VertexArrayObject vao;


	public VertexBufferVisualiser(VertexArrayObject buffer) {
		this.vao = buffer;
	}

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
		sb.append("VertexArrayObject:\n");
		if (vao.hasVbo()) {
			VertexBufferObject vbo = vao.getVbo();
			sb.append('\t').append("VBO:\n");
			sb.append('\t').append(headerString(vbo)).append('\n');
			sb.append('\t').append(verticeString(vbo)).append('\n');
			sb.append('\t').append(dataString(vbo)).append('\n');
		}
		if (vao.hasEbo()) {
			ElementBufferObject ebo = vao.getEbo();
			sb.append('\t').append("EBO:\n");
			sb.append('\t').append(ebo).append('\n');
		}
        return sb.toString();
    }


    public String byteString() {
		return vao.getVbo().byteStream()
						.map(VertexBufferVisualiser::hex)
						.collect(Collectors.joining(","));
	}

	private static String hex(Byte b) {
		var hex = Integer.toHexString(b & 0xFF).toUpperCase();
		if (hex.length() == 1) {
			hex = '0' + hex;
		}
		return hex;
	}

	public static String verticeString(VertexBufferObject buffer) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < buffer.size(); i++) {
			Map<VertexElement, ?> data = buffer.get(i);
			for(VertexElement ve : buffer.getStructure()) {
				String text = "^" + i ;
				text = truncateAndPad(text, ve.type().byteSize() * 3);
				text = text.substring(0, text.length() - 2) + "^";
				result.append(text).append(' ');
			}
		}
		return result.toString();
	}

	public static String dataString(VertexBufferObject buffer) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < buffer.size(); i++) {
			Map<VertexElement, ?> data = buffer.get(i);
			for(VertexElement ve : buffer.getStructure()) {
				Object value = data.get(ve);
				String text = "^" + dataString(value);
				text = truncateAndPad(text, ve.type().byteSize() * 3);
				text = text.substring(0, text.length() - 2) + "^";
				result.append(text).append(' ');
			}
		}
		return result.toString();
	}

	public static String floatToString(float f) {
		return String.format("%.4f", f);
	}

	public static String dataString(Object dataValue) {
        if (dataValue instanceof Float f) {
            return floatToString(f);
        } else if (dataValue instanceof Vector2f v) {
            return "[" + floatToString(v.x) + "," + floatToString(v.y) + "]";
        } else if (dataValue instanceof Vector3f v) {
            return "[" + floatToString(v.x) + "," + floatToString(v.y) + "," + floatToString(v.z) + "]";
        } else if (dataValue instanceof Vector4f v) {
            return "[" + floatToString(v.x) + "," + floatToString(v.y) + "," + floatToString(v.z) + "," + floatToString(v.w) + "]";
        } else {
            return String.valueOf(dataValue);
        }
	}

	public static String headerString(VertexBufferObject buffer) {
		return IntStream.range(0, buffer.size())
						.mapToObj((i) -> headerString(buffer.getStructure()))
						.collect(Collectors.joining(" "));
	}

    public static String headerString(VertexElement e) {
        String text = "^" + e.name();
        int maxLength = e.type().byteSize() * 3;
        String header;
        if (text.length() > maxLength) {
            header = text.substring(0, maxLength);
        } else {
            header = String.format("%-" + maxLength + "s", text).replace(' ', '-');
        }
        return header.substring(0, header.length() - 2) + "^";
    }

    public static String headerString(VertexDataStructure vds) {
        return vds.stream().map(VertexBufferVisualiser::headerString).collect(Collectors.joining(" "));
    }

	private static String truncateAndPad(String text, int maxLength) {
		if (text.length() > maxLength) {
			return text.substring(0, maxLength);
		} else {
			return String.format("%-" + maxLength + "s", text).replace(' ', '-');
		}
	}
}
