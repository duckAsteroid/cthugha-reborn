package com.asteroid.duck.opengl.util.resources.buffer.vbo;

import java.util.*;
import java.util.stream.Stream;

/**
 * Describes the per-vertex data layout stored in a {@link VertexBufferObject}.
 *
 * <p>A structure is an ordered sequence of named {@link VertexElement}s. The order determines
 * the byte layout in the underlying native buffer: elements are packed contiguously in declaration
 * order, with no padding between them.</p>
 *
 * <p>Once constructed the structure is immutable. The byte size and per-element offsets are
 * pre-computed at construction time for efficient reuse during serialization and
 * vertex-attribute-pointer setup.</p>
 *
 * <p>Example – a structure carrying a 2-D position and an RGB colour per vertex:</p>
 * <pre>{@code
 * VertexDataStructure layout = new VertexDataStructure(
 *     new VertexElement(VertexElementType.VEC_2F, "position"),
 *     new VertexElement(VertexElementType.VEC_3F, "colour")
 * );
 * }</pre>
 *
 * @see VertexElement
 * @see VertexBufferObject
 */
public class VertexDataStructure implements Iterable<VertexElement> {
	private final List<VertexElement> structure;
	/** Cache of elements by name for O(1) look-up. */
	private final Map<String, VertexElement> namedElementCache;
	/** Pre-computed byte offset of each element within one vertex record. */
	private final Map<String, Long> namedOffsets;
	/** Total byte size of one vertex record (sum of all element sizes). */
	private final int size;
	/**
	 * When {@code true}, {@link #asMap(List)} silently fills missing trailing values with
	 * each element's {@link VertexElementType#nullReplacementValue()}.
	 */
	private final boolean acceptFewerValues;
	/**
	 * When {@code true}, {@link #asMap(List)} ignores excess values beyond the structure size.
	 */
	private final boolean acceptMoreValues;

	/**
	 * Creates a structure from varargs elements with lenient value-count handling
	 * (fewer values accepted, extra values rejected).
	 *
	 * @param elements one or more vertex elements in declaration order
	 * @throws NullPointerException if {@code elements} is null
	 * @throws IllegalArgumentException if {@code elements} is empty or contains duplicate names
	 */
	public VertexDataStructure(VertexElement ... elements) {
		this(Arrays.asList(elements));
	}

	/**
	 * Creates a structure from a list with lenient value-count handling
	 * (fewer values accepted, extra values rejected).
	 *
	 * @param elements ordered list of vertex elements
	 * @throws NullPointerException if {@code elements} is null
	 * @throws IllegalArgumentException if {@code elements} is empty or contains duplicate names
	 */
	public VertexDataStructure(List<VertexElement> elements) {
		this(elements, true, false);
	}

	/**
	 * Creates a structure with explicit control over value-count validation in
	 * {@link #asMap(List)}.
	 *
	 * @param elements ordered list of vertex elements; must be non-null, non-empty, unique names
	 * @param acceptFewerValues if {@code true}, fewer supplied values than elements is allowed
	 *                          (missing values use the type's null-replacement)
	 * @param acceptMoreValues  if {@code true}, extra supplied values beyond element count are ignored
	 * @throws NullPointerException if {@code elements} is null
	 * @throws IllegalArgumentException if {@code elements} is empty or contains duplicate names
	 */
	public VertexDataStructure(List<VertexElement> elements, boolean acceptFewerValues, boolean acceptMoreValues) {
		this.acceptFewerValues = acceptFewerValues;
		this.acceptMoreValues = acceptMoreValues;
		Objects.requireNonNull(elements, "elements");
		if (elements.isEmpty()) {
			throw new IllegalArgumentException("Vertex elements list must not be empty");
		}
		namedOffsets = new HashMap<>(elements.size());
		namedElementCache = new HashMap<>(elements.size());
		long offset = 0;
		for(VertexElement element : elements) {
			if (namedElementCache.containsKey(element.name())) {
				throw new IllegalArgumentException("Duplicate vertex element name: " + element.name());
			}
			namedElementCache.put(element.name(), element);
			// calculate and store offsets
			namedOffsets.put(element.name(), offset);
			offset += element.type().byteSize();
		}
		this.structure = elements;
		this.size = elements.stream().map(VertexElement::type).mapToInt(VertexElementType::byteSize).sum();
	}

	/**
	 * Convenience varargs overload of {@link #asMap(List)}.
	 *
	 * @param values element values in structure order
	 * @return mutable map from each {@link VertexElement} to its value
	 * @throws NullPointerException if {@code values} is null
	 */
	public Map<VertexElement, Object> asMap(Object ... values) {
		Objects.requireNonNull(values, "values");
		return asMap(Arrays.asList(values));
	}

	/**
	 * Builds a {@link VertexElement}-keyed map from a positional value list.
	 *
	 * <p>Values are matched to elements by index. If the list is shorter than the structure
	 * and {@link #acceptFewerValues} is set, missing entries fall back to
	 * {@link VertexElementType#nullReplacementValue()}. If the list is longer and
	 * {@link #acceptMoreValues} is set, excess values are silently ignored.</p>
	 *
	 * @param values positional values matching element order
	 * @return mutable map from element to value, ready to pass to
	 *         {@link VertexBufferObject#set(int, Map)}
	 * @throws NullPointerException     if {@code values} is null
	 * @throws IllegalArgumentException if value count violates the configured acceptance flags,
	 *                                  or if any value is the wrong Java type for its element
	 */
	public Map<VertexElement, Object> asMap(List<?> values) {
		Objects.requireNonNull(values, "values");
		if (!acceptMoreValues && values.size() > structure.size()) {
			throw new IllegalArgumentException("Too many values ("+values.size()+") for structure size="+structure.size());
		}
		if (!acceptFewerValues && values.size() < structure.size()) {
			throw new IllegalArgumentException("Too few values ("+values.size()+") for structure size="+structure.size());
		}
		Map<VertexElement, Object> result = new HashMap<>();
		for (int i = 0; i < structure.size(); i++) {
			VertexElement element = structure.get(i);
			Object value = element.type().nullReplacementValue();
			if (i < values.size()) {
				value = values.get(i);
			}
			element.checkInstanceOf(value);
			result.put(element, value);
		}
		return result;
	}

	/**
	 * Returns an iterator over elements in declaration order.
	 *
	 * @return element iterator
	 */
	@Override
	public Iterator<VertexElement> iterator() {
		return structure.iterator();
	}

	/**
	 * Returns a sequential stream of elements in declaration order.
	 *
	 * @return element stream
	 */
	public Stream<VertexElement> stream() {
    return structure.stream();
  }

	/**
	 * Returns the total byte size of one vertex record.
	 *
	 * <p>This equals the sum of {@link VertexElementType#byteSize()} for all elements and
	 * is used as the stride when configuring vertex attribute pointers.</p>
	 *
	 * @return stride in bytes
	 */
	public int size() {
		return size;
	}

	/**
	 * Looks up a {@link VertexElement} by its shader attribute name.
	 *
	 * @param name attribute name as used in the vertex shader
	 * @return the element, or {@code null} if the name is not in this structure
	 */
	public VertexElement get(String name) {
		return namedElementCache.get(name);
	}

	/**
	 * Returns the byte offset of the named element within a single vertex record.
	 *
	 * @param name attribute name as used in the vertex shader
	 * @return byte offset from the start of the vertex record
	 * @throws NoSuchElementException if the name is not in this structure
	 */
	public long getOffset(String name) {
    return Optional.ofNullable(namedOffsets.get(name)).orElseThrow();
  }

	/**
	 * Returns the byte offset of an element within a single vertex record.
	 *
	 * @param element the element to look up
	 * @return byte offset from the start of the vertex record
	 * @throws NoSuchElementException if the element is not in this structure
	 */
	public long getOffset(VertexElement element) {
		return getOffset(element.name());
	}

	/**
	 * Returns the element at the given declaration index.
	 *
	 * @param i zero-based index in declaration order
	 * @return element at that position
	 * @throws IndexOutOfBoundsException if {@code i} is out of range
	 */
	public VertexElement getIndex(int i) {
		return structure.get(i);
	}

	/**
	 * Validates that a vertex data map exactly matches this structure.
	 *
	 * <p>The map must contain exactly one entry for every element in this structure,
	 * with each value being an instance of the element's declared Java type.</p>
	 *
	 * @param vertexData map to validate
	 * @throws NullPointerException     if {@code vertexData} is null
	 * @throws IllegalArgumentException if the map is empty, has the wrong number of entries,
	 *                                  is missing a required element, or contains a value of
	 *                                  the wrong type
	 */
	public void checkStructure(Map<VertexElement,?> vertexData) {
		Objects.requireNonNull(vertexData, "vertexData");
		if (vertexData.isEmpty()) {
			throw new IllegalArgumentException("Vertex data map must not be empty");
		}
		if (vertexData.size() != structure.size()) {
			throw new IllegalArgumentException("Vertex data map size (" + vertexData.size() + ") does not match structure size (" + structure.size() + ")");
		}
		for (VertexElement element : structure) {
			if (!vertexData.containsKey(element)) {
				throw new IllegalArgumentException("Vertex data map is missing element: " + element.name());
			}
			element.checkInstanceOf(vertexData.get(element));
		}
	}
}
