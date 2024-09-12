package io.grpc.bidi;

import io.grpc.Drainable;
import io.grpc.MethodDescriptor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;

import java.io.InputStream;
import java.io.OutputStream;

class ByteBufMarshaller implements MethodDescriptor.Marshaller<ByteBuf> {

	public static ByteBufMarshaller INSTANCE = new ByteBufMarshaller();

	@Override
	public InputStream stream(ByteBuf value) {
		return new DrainableInputStream(value);
	}

	@Override
	public ByteBuf parse(InputStream stream) {
		// TODO use KnownLength, Detachable & HasByteBuffer for zero copy
		// See https://github.com/GoogleCloudPlatform/grpc-gcp-java/pull/77
		try {
			ByteBuf buf = Unpooled.buffer(stream.available());
			try {
				buf.writeBytes(stream, stream.available());
			} catch (Exception e) {
				buf.release();
				throw e;
			}

			return buf;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	static class DrainableInputStream extends ByteBufInputStream implements Drainable {

		private final ByteBuf buffer;

		public DrainableInputStream(ByteBuf buffer) {
			super(buffer, true);
			this.buffer = buffer;
		}

		@Override
		public int drainTo(OutputStream target) {
			int capacity = buffer.readableBytes();
			try {
				buffer.getBytes(buffer.readerIndex(), target, capacity);
				buffer.skipBytes(capacity);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
			return capacity;
		}
	}
}
