package io.github.duckasteroid.cthugha.audio.buffer;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MappedBufferTest {

  @Test
  void get() {
    byte[] raw = new byte[] { 0x00, (byte) 0xFF, 0x00, 0x10};
    ByteBuffer byteBuffer = new ByteBuffer(java.nio.ByteBuffer.wrap(raw));
    MappedBuffer<Short, Byte> wrapped = new MappedBuffer<>(Mapper.SHORT_BYTE_MAPPER, byteBuffer);

    short actual = wrapped.get();
    assertEquals(0xff, actual);
    actual = wrapped.get();
    assertEquals(0x10, actual);

    assertFalse(wrapped.hasRemaining());
    assertFalse(byteBuffer.hasRemaining());
  }

  @Test
  void getExotic() {
    byte[] raw = new byte[] { 0x00, (byte) 0xFF, 0x00, 0x10};
    {
      ByteBuffer byteBuffer = new ByteBuffer(java.nio.ByteBuffer.wrap(raw));
      MappedBuffer<Short, Byte> wrapped = new MappedBuffer<>(Mapper.SHORT_BYTE_MAPPER, byteBuffer);
      MappedBuffer<short[], Short> exotic = new MappedBuffer<>(Mapper.shortArrayMapper(2), wrapped);

      short[] actual = exotic.get();
      assertArrayEquals(new short[] {0xff, 0x10}, actual);

      assertFalse(exotic.hasRemaining());
      assertFalse(wrapped.hasRemaining());
      assertFalse(byteBuffer.hasRemaining());
    }
    {
      ByteBuffer byteBuffer = new ByteBuffer(java.nio.ByteBuffer.wrap(raw));
      MappedBuffer<short[], Byte> exotic = new MappedBuffer<>(Mapper.shortArrayFromByte(2), byteBuffer);

      short[] actual = exotic.get();
      assertArrayEquals(new short[] {0xff, 0x10}, actual);

      assertFalse(exotic.hasRemaining());
      assertFalse(byteBuffer.hasRemaining());
    }
  }

  @Test
  void put() {
    byte[] raw = new byte[4];
    ByteBuffer byteBuffer = new ByteBuffer(java.nio.ByteBuffer.wrap(raw));
    MappedBuffer<Short, Byte> wrapped = new MappedBuffer<>(Mapper.SHORT_BYTE_MAPPER, byteBuffer);

    wrapped.put((short)0xff);
    wrapped.put((short)0x10);

    assertArrayEquals(new byte[] { 0x00, (byte) 0xFF, 0x00, 0x10}, raw);
  }
}
