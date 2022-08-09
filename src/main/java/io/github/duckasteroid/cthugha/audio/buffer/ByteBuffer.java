package io.github.duckasteroid.cthugha.audio.buffer;

import java.nio.ByteOrder;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A generic {@link Buffer} that wraps a NIO ByteBuffer and works with the boxed
 * {@link Byte} type.
 */
public class ByteBuffer implements Buffer<Byte> , Iterable<Byte> {

  protected final java.nio.ByteBuffer backingBuffer;

  protected ByteBuffer(java.nio.ByteBuffer backingBuffer) {
    this.backingBuffer = backingBuffer;
  }

  @Override
  public final int capacity() {
    return backingBuffer.capacity();
  }

  @Override
  public final int position() {
    return backingBuffer.position();
  }

  @Override
  public ByteBuffer position(int newPosition) {
    backingBuffer.position(newPosition);
    return this;
  }


  @Override
  public final int limit() {
    return backingBuffer.limit();
  }

  @Override
  public ByteBuffer limit(int newLimit) {
    backingBuffer.limit(newLimit);
    return this;
  }


  @Override
  public ByteBuffer mark() {
    backingBuffer.mark();
    return this;
  }

  @Override
  public ByteBuffer reset() {
    backingBuffer.reset();
    return this;
  }

  @Override
  public ByteBuffer clear() {
    backingBuffer.clear();
    return this;
  }

  @Override
  public ByteBuffer flip() {
    backingBuffer.flip();
    return this;
  }

  @Override
  public ByteBuffer rewind() {
    backingBuffer.rewind();
    return this;
  }

  @Override
  public final int remaining() {
    return backingBuffer.remaining();
  }

  @Override
  public final boolean hasRemaining() {
    return backingBuffer.hasRemaining();
  }

  @Override
  public boolean isReadOnly() {
    return backingBuffer.isReadOnly();
  }

  @Override
  public ByteBuffer slice() {
    return new ByteBuffer(backingBuffer.slice());
  }

  @Override
  public ByteBuffer slice(int index, int length) {
    return new ByteBuffer(backingBuffer.slice(index , length ));
  }

  @Override
  public ByteBuffer duplicate() {
    return new ByteBuffer(backingBuffer.duplicate());
  }


  /**
   * Retrieves this buffer's byte order.
   *
   * <p> The byte order is used when reading or writing multibyte values, and
   * when creating buffers that are views of this byte buffer.  The order of
   * a newly-created byte buffer is always {@link ByteOrder#BIG_ENDIAN
   * BIG_ENDIAN}.  </p>
   *
   * @return  This buffer's byte order
   */
  public final ByteOrder order() {
    return backingBuffer.order();
  }

  /**
   * Modifies this buffer's byte order.
   *
   * @param  bo
   *         The new byte order,
   *         either {@link ByteOrder#BIG_ENDIAN BIG_ENDIAN}
   *         or {@link ByteOrder#LITTLE_ENDIAN LITTLE_ENDIAN}
   *
   * @return  This buffer
   */
  public final Buffer<Byte> order(ByteOrder bo) {
    backingBuffer.order(bo);
    return this;
  }

  @Override
  public Byte get() {
    return backingBuffer.get();
  }

  @Override
  public Byte get(int index) {
    return backingBuffer.get(index);
  }

  @Override
  public ByteBuffer get(Byte[] dst) {
    return get(dst, 0 , dst.length);
  }

  @Override
  public ByteBuffer get(Byte[] dst, int offset, int length) {
    for (int i = offset; i < offset + length; i++) {
      dst[i] = get();
    }
    return this;
  }

  @Override
  public ByteBuffer put(Byte src) {
    backingBuffer.put(src);
    return this;
  }

  @Override
  public ByteBuffer put(int index, Byte src) {
    backingBuffer.put(index, src);
    return this;
  }

  @Override
  public ByteBuffer put(Byte[] src) {
    return put(src, 0 , src.length);
  }

  @Override
  public ByteBuffer put(Byte[] src, int offset, int length) {
    for (int i = offset; i < offset+length; i++) {
      put(src[i]);
    }
    return this;
  }

  @Override
  public ByteBuffer put(Buffer<Byte> src) {
    while(src.hasRemaining()) {
      put(src.get());
    }
    return this;
  }

  /**
   * An iterator over the bytes in the buffer from current {@link #position()} while
   * {@link #hasRemaining()}
   * @return
   */
  public Iterator<Byte> iterator() {
    return new Iterator<Byte>() {
      private final ByteBuffer buffer = duplicate();
      @Override
      public boolean hasNext() {
        return buffer.hasRemaining();
      }

      @Override
      public Byte next() {
        return buffer.get();
      }
    };
  }
  /**
   * Provide access to the underlying (backing) NIO ByteBuffer
   */
  public java.nio.ByteBuffer backingBuffer() {
    return backingBuffer;
  }

  /**
   * Wrap a NIO ByteBuffer into one of our generic buffers
   */
  public static java.nio.ByteBuffer asNioBuffer(Buffer<Byte> b) {
    if(b instanceof ByteBuffer) {
      return ((ByteBuffer) b).backingBuffer;
    }
    else {
      throw new IllegalArgumentException("Must be a ByteBuffer");
    }
  }

  @Override
  public Stream<Byte> toStream() {
    return StreamSupport.stream(Spliterators.spliterator(iterator(), remaining(), Spliterator.ORDERED),false );
  }
}
