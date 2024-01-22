package io.github.duckasteroid.cthugha.audio.buffer;

import java.nio.ShortBuffer;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Provides the ability to map to a target type T from a source type S (and back again).
 * Each T comprises {@link #size()} S elements.
 * @param <T> the target type
 * @param <S> the source type
 */
public class Mapper<T, S> {
  private final int size;

  /**
   * Provide reverse mapping back from target to a buffer of source type
   */
  private final BiConsumer<T, Buffer<S>> reverse;
  /**
   * Provid mapping to the target type
   */
  private final Function<Buffer<S>, T> forward;

  public Mapper(int size, BiConsumer<T, Buffer<S>> reverse, Function<Buffer<S>, T> forward) {
    this.size = size;
    this.forward = Objects.requireNonNull(forward);
    this.reverse = Objects.requireNonNull(reverse);
  }

  public int size() { return size;}

  public T get(Buffer<S> backingBuffer) {
    return forward.apply(backingBuffer);
  }

  public T get(Buffer<S> backingBuffer, int index) {
    return forward.apply(backingBuffer.duplicate().position(index));
  }

  public void put(T src, Buffer<S> backingBuffer) {
    reverse.accept(src, backingBuffer);
  }

  public void put(int index, T src, Buffer<S> backingBuffer) {
    Buffer<S> buffer = backingBuffer.duplicate().position(index);
    reverse.accept(src, buffer);
  }

  /**
   * A mapper for short values from a buffer of bytes
   */
  public static final Mapper<Short, Byte> SHORT_BYTE_MAPPER = new Mapper<>(2,
    (s, buffer) -> {
      ByteBuffer.asNioBuffer(buffer).putShort(s);
    },
    (buffer) -> ByteBuffer.asNioBuffer(buffer).getShort());

  /**
   * A mapper from bytes to arrays of shorts
   * @param size the number of short elements in each array
   * @return a mapper for byte to short[]
   */
  public static final Mapper<short[], Byte> shortArrayFromByte(final int size) {
    return new Mapper<>(size,
      (s, buffer) -> {
        ByteBuffer.asNioBuffer(buffer).asShortBuffer().put(s, 0, size);
        buffer.position(buffer.position() + (size * 2));
      },
      (buffer) -> {
        ShortBuffer shortBuffer = ByteBuffer.asNioBuffer(buffer).asShortBuffer();
        short[] result = new short[size];
        shortBuffer.get(result);
        buffer.position(buffer.position() + (size * 2));
        return result;
      });
  }


  public static final Mapper<short[], Short> shortArrayMapper(final int size) {
    return new Mapper<>(size, (src, buff) -> {
      for (int i = 0; i < size; i++) {
        buff.put(src[0]);
        buff.put(src[1]);
      }
    }, (buff) -> {
      short[] result = new short[size];
      for (int i = 0; i < size; i++) {
        result[i] = buff.get();
      }
      return result;
    });
  }
}
