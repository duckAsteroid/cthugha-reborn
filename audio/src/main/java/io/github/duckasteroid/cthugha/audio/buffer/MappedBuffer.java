package io.github.duckasteroid.cthugha.audio.buffer;

import java.nio.ByteBuffer;
import java.nio.InvalidMarkException;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A generic {@link java.nio.Buffer} that works by mapping and unmapping
 * to and from the backing buffer
 * @see Mapper
 */
public class MappedBuffer<T, S> implements Buffer<T> {

  private final Mapper<T, S> mapper;
  private final Buffer<S> backingBuffer;

  protected MappedBuffer(Mapper<T, S> mapper, Buffer<S> backingBuffer) {
    this.mapper = mapper;
    this.backingBuffer = backingBuffer;
  }

  /**
   * Returns this buffer's capacity.
   *
   * @return The capacity of this buffer
   */
  public final int capacity() {
    return backingBuffer.capacity() / mapper.size();
  }

  /**
   * Returns this buffer's position.
   *
   * @return The position of this buffer
   */
  public final int position() {
    return backingBuffer.position() / mapper.size();
  }

  /**
   * Sets this buffer's position.  If the mark is defined and larger than the
   * new position then it is discarded.
   *
   * @param newPosition The new position value; must be non-negative
   *                    and no larger than the current limit
   * @return This buffer
   * @throws IllegalArgumentException If the preconditions on {@code newPosition} do not hold
   */
  public Buffer<T> position(int newPosition) {
    backingBuffer.position(newPosition * mapper.size());
    return this;
  }


  /**
   * Returns this buffer's limit.
   *
   * @return The limit of this buffer
   */
  public final int limit() {
    return backingBuffer.limit() / mapper.size();
  }

  /**
   * Sets this buffer's limit.  If the position is larger than the new limit
   * then it is set to the new limit.  If the mark is defined and larger than
   * the new limit then it is discarded.
   *
   * @param newLimit The new limit value; must be non-negative
   *                 and no larger than this buffer's capacity
   * @return This buffer
   * @throws IllegalArgumentException If the preconditions on {@code newLimit} do not hold
   */
  public Buffer<T> limit(int newLimit) {
    backingBuffer.limit(newLimit * mapper.size());
    return this;
  }


  /**
   * Sets this buffer's mark at its position.
   *
   * @return This buffer
   */
  public Buffer<T> mark() {
    backingBuffer.mark();
    return this;
  }

  /**
   * Resets this buffer's position to the previously-marked position.
   *
   * <p> Invoking this method neither changes nor discards the mark's
   * value. </p>
   *
   * @return This buffer
   * @throws InvalidMarkException If the mark has not been set
   */
  public Buffer<T> reset() {
    backingBuffer.reset();
    return this;
  }

  /**
   * Clears this buffer.  The position is set to zero, the limit is set to
   * the capacity, and the mark is discarded.
   *
   * <p> Invoke this method before using a sequence of channel-read or
   * <i>put</i> operations to fill this buffer.  For example:
   *
   * <blockquote><pre>
   * buf.clear();     // Prepare buffer for reading
   * in.read(buf);    // Read data</pre></blockquote>
   *
   * <p> This method does not actually erase the data in the buffer, but it
   * is named as if it did because it will most often be used in situations
   * in which that might as well be the case. </p>
   *
   * @return This buffer
   */
  public Buffer<T> clear() {
    backingBuffer.clear();
    return this;
  }

  /**
   * Flips this buffer.  The limit is set to the current position and then
   * the position is set to zero.  If the mark is defined then it is
   * discarded.
   *
   * <p> After a sequence of channel-read or <i>put</i> operations, invoke
   * this method to prepare for a sequence of channel-write or relative
   * <i>get</i> operations.  For example:
   *
   * <blockquote><pre>
   * buf.put(magic);    // Prepend header
   * in.read(buf);      // Read data into rest of buffer
   * buf.flip();        // Flip buffer
   * out.write(buf);    // Write header + data to channel</pre></blockquote>
   *
   * <p> This method is often used in conjunction with the {@link
   * java.nio.ByteBuffer#compact compact} method when transferring data from
   * one place to another.  </p>
   *
   * @return This buffer
   */
  public Buffer<T> flip() {
    backingBuffer.flip();
    return this;
  }

  /**
   * Rewinds this buffer.  The position is set to zero and the mark is
   * discarded.
   *
   * <p> Invoke this method before a sequence of channel-write or <i>get</i>
   * operations, assuming that the limit has already been set
   * appropriately.  For example:
   *
   * <blockquote><pre>
   * out.write(buf);    // Write remaining data
   * buf.rewind();      // Rewind buffer
   * buf.get(array);    // Copy data into array</pre></blockquote>
   *
   * @return This buffer
   */
  public Buffer<T> rewind() {
    backingBuffer.rewind();
    return this;
  }

  /**
   * Returns the number of elements between the current position and the
   * limit.
   *
   * @return The number of elements remaining in this buffer
   */
  public final int remaining() {
    return backingBuffer.remaining() / mapper.size();
  }

  /**
   * Tells whether there are any elements between the current position and
   * the limit.
   *
   * @return {@code true} if, and only if, there is at least one element
   * remaining in this buffer
   */
  public final boolean hasRemaining() {
    return remaining() > 0;
  }

  /**
   * Tells whether or not this buffer is read-only.
   *
   * @return {@code true} if, and only if, this buffer is read-only
   */
  public boolean isReadOnly() {
    return backingBuffer.isReadOnly();
  }

  /**
   * Creates a new buffer whose content is a shared subsequence of
   * this buffer's content.
   *
   * <p> The content of the new buffer will start at this buffer's current
   * position.  Changes to this buffer's content will be visible in the new
   * buffer, and vice versa; the two buffers' position, limit, and mark
   * values will be independent.
   *
   * <p> The new buffer's position will be zero, its capacity and its limit
   * will be the number of elements remaining in this buffer, its mark will be
   * undefined. The new buffer will be direct if, and only if, this buffer is
   * direct, and it will be read-only if, and only if, this buffer is
   * read-only.  </p>
   *
   * @return The new buffer
   * @since 9
   */
  public Buffer<T> slice() {
    return new MappedBuffer<>(mapper, backingBuffer.slice());
  }

  /**
   * Creates a new buffer whose content is a shared subsequence of
   * this buffer's content.
   *
   * <p> The content of the new buffer will start at position {@code index}
   * in this buffer, and will contain {@code length} elements. Changes to
   * this buffer's content will be visible in the new buffer, and vice versa;
   * the two buffers' position, limit, and mark values will be independent.
   *
   * <p> The new buffer's position will be zero, its capacity and its limit
   * will be {@code length}, its mark will be undefined. The new buffer will
   * be direct if, and only if, this buffer is direct, and it will be
   * read-only if, and only if, this buffer is read-only.  </p>
   *
   * @param index  The position in this buffer at which the content of the new
   *               buffer will start; must be non-negative and no larger than
   *               {@link #limit() limit()}
   * @param length The number of elements the new buffer will contain; must be
   *               non-negative and no larger than {@code limit() - index}
   * @return The new buffer
   * @throws IndexOutOfBoundsException If {@code index} is negative or greater than {@code limit()},
   *                                   {@code length} is negative, or {@code length > limit() - index}
   * @since 13
   */
  public Buffer<T> slice(int index, int length) {
    return new MappedBuffer<>(mapper, backingBuffer.slice(index * mapper.size(), length * mapper.size()));
  }

  /**
   * Creates a new buffer that shares this buffer's content.
   *
   * <p> The content of the new buffer will be that of this buffer.  Changes
   * to this buffer's content will be visible in the new buffer, and vice
   * versa; the two buffers' position, limit, and mark values will be
   * independent.
   *
   * <p> The new buffer's capacity, limit, position and mark values will be
   * identical to those of this buffer. The new buffer will be direct if, and
   * only if, this buffer is direct, and it will be read-only if, and only if,
   * this buffer is read-only.  </p>
   *
   * @return The new buffer
   * @since 9
   */
  public Buffer<T> duplicate() {
    return new MappedBuffer<>(mapper, backingBuffer.duplicate());
  }

  public T get() {
    return mapper.get(backingBuffer);
  }

  public T get(int index) {
    return mapper.get(backingBuffer, index);
  }

  public Buffer<T> get(T[] dst) {
    return get(dst, 0, dst.length);
  }

  public Buffer<T> get(T[] dst, int offset, int length) {
    for (int i = offset; i < offset + length; i++) {
      dst[i] = get();
    }
    return this;
  }

  public Buffer<T> put(T src) {
    mapper.put(src, backingBuffer);
    return this;
  }

  public Buffer<T> put(int index, T src) {
    mapper.put(index, src, backingBuffer);
    return this;
  }

  public Buffer<T> put(T[] src) {
    return put(src, 0 , src.length);
  }

  public Buffer<T> put(T[] src, int offset, int length) {
    for (int i = offset; i < offset+length; i++) {
      put(src[i]);
    }
    return this;
  }

  public Buffer<T> put(Buffer<T> src) {
    while(src.hasRemaining()) {
      put(src.get());
    }
    return this;
  }

  public Iterator<T> iterator() {
    return new Iterator<T>() {
      private final Buffer<T> buffer = duplicate();
      @Override
      public boolean hasNext() {
        return buffer.hasRemaining();
      }

      @Override
      public T next() {
        return buffer.get();
      }
    };
  }

  @Override
  public Stream<T> toStream() {
    return StreamSupport.stream(Spliterators.spliterator(iterator(), remaining(), Spliterator.ORDERED),false );
  }

}
