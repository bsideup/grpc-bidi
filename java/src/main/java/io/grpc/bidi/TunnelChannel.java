package io.grpc.bidi;

import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelMetadata;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelOutboundBuffer;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;

import java.net.SocketAddress;
import java.util.concurrent.atomic.AtomicBoolean;

abstract class TunnelChannel extends AbstractChannel {

	private final ChannelConfig config = new DefaultChannelConfig(this) {
		@Override
		public <T> boolean setOption(ChannelOption<T> option, T value) {
			if (option == ChannelOption.SO_KEEPALIVE) {
				return true;
			}
			return super.setOption(option, value);
		}
	};

	private final AtomicBoolean closed = new AtomicBoolean(false);

	public TunnelChannel() {
		this(null);
	}

	public TunnelChannel(Channel parent) {
		super(parent);
	}

	protected abstract void doConsume(ByteBuf byteBuf);

	@Override
	protected void doWrite(ChannelOutboundBuffer in) {
		Object msg;
		while ((msg = in.current()) != null) {
			doConsume((ByteBuf) msg);
			if (!in.remove()) {
				break;
			}
		}
	}

	@Override
	protected AbstractUnsafe newUnsafe() {
		return new AbstractUnsafe() {
			@Override
			public void connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
				promise.setSuccess();
			}
		};
	}

	@Override
	public boolean isOpen() {
		return !closed.get();
	}

	@Override
	public boolean isActive() {
		return isOpen();
	}

	@Override
	protected void doClose() {
		closed.set(true);
	}

	//////////////////////////////////

	@Override
	protected void doBeginRead() {}

	@Override
	protected boolean isCompatible(EventLoop loop) {
		return true;
	}

	@Override
	public ChannelConfig config() {
		return config;
	}

	@Override
	public ChannelMetadata metadata() {
		return new ChannelMetadata(false);
	}

	@Override
	protected void doBind(SocketAddress localAddress) {}

	@Override
	protected void doDisconnect() {}

	@Override
	protected SocketAddress localAddress0() {
		return null;
	}

	@Override
	protected SocketAddress remoteAddress0() {
		return null;
	}
}
