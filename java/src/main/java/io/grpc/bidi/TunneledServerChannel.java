package io.grpc.bidi;

import io.grpc.CallOptions;
import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.bidi.TunneledServerChannel.RetryingChannelHandler.RetryOption;
import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractServerChannel;
import io.netty.channel.Channel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;

import java.net.SocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

final class TunneledServerChannel extends AbstractServerChannel {

	private static final Logger log = Logger.getLogger(TunneledServerChannel.class.getName());

	enum State {
		INITIAL,
		OPEN,
		ACTIVE,
		CLOSED,
	}

	private final RetryingChannelHandler retryingChannelHandler = new RetryingChannelHandler();

	private final ChannelConfig config = new DefaultChannelConfig(this) {
		@Override
		public <T> boolean setOption(ChannelOption<T> option, T value) {
			if (option instanceof RetryOption) {
				return retryingChannelHandler.setOption(option, value);
			}

			return super.setOption(option, value);
		}

		@Override
		public <T> T getOption(ChannelOption<T> option) {
			if (option instanceof RetryOption) {
				return retryingChannelHandler.getOption(option);
			}

			return super.getOption(option);
		}
	};

	private final CallOptions callOptions;

	private final Metadata metadata;

	private volatile State state = State.INITIAL;

	private volatile ChannelAddress channelAddress;

	public TunneledServerChannel(CallOptions callOptions, Metadata metadata) {
		this.callOptions = callOptions;
		this.metadata = metadata;
	}

	@Override
	protected void doBind(SocketAddress address) {
		this.channelAddress = (ChannelAddress) address;

		state = State.ACTIVE;
	}

	@Override
	public ChannelConfig config() {
		return config;
	}

	@Override
	public boolean isOpen() {
		switch (state) {
			case INITIAL:
			case OPEN:
			case ACTIVE:
				return true;
			default:
				return false;
		}
	}

	@Override
	public boolean isActive() {
		return state == State.ACTIVE;
	}

	@Override
	protected boolean isCompatible(EventLoop loop) {
		return loop instanceof SingleThreadEventLoop;
	}

	@Override
	protected SocketAddress localAddress0() {
		return channelAddress;
	}

	@Override
	protected void doClose() {
		state = State.CLOSED;
	}

	@Override
	protected void doBeginRead() {
		ClientCall<ByteBuf, ByteBuf> call = channelAddress.channel.newCall(
			ClientChannelService.NEW_TUNNEL_METHOD,
			callOptions
		);

		CallChannel callChannel = new CallChannel(call, metadata);
		callChannel.pipeline().addLast(retryingChannelHandler);

		pipeline().fireChannelRead(callChannel);
	}

	class CallChannel extends TunnelChannel {

		final ClientCall<ByteBuf, ByteBuf> call;

		final Metadata headers;

		volatile boolean ready;

		CallChannel(ClientCall<ByteBuf, ByteBuf> call, Metadata headers) {
			super(TunneledServerChannel.this);
			this.call = call;
			this.headers = headers;
		}

		@Override
		protected void doRegister() {
			call.start(new CallListener(), headers);
		}

		@Override
		protected void doBeginRead() {
			call.request(Integer.MAX_VALUE);
		}

		@Override
		protected void doConsume(ByteBuf byteBuf) {
			call.sendMessage(byteBuf.retain());
		}

		@Override
		protected void doDeregister() {
			call.cancel("Deregistered", null);
		}

		@Override
		public boolean isActive() {
			return ready;
		}

		private class CallListener extends ClientCall.Listener<ByteBuf> {

			@Override
			public void onHeaders(Metadata headers) {
				ready = true;
				pipeline().fireChannelActive();
			}

			@Override
			public void onMessage(ByteBuf bytes) {
				if (bytes.readableBytes() > 0) {
					pipeline().fireChannelRead(bytes);
				} else {
					bytes.release();
				}
			}

			@Override
			public void onClose(Status status, Metadata trailers) {
				pipeline().fireExceptionCaught(status.asException());
			}
		}
	}

	@ChannelHandler.Sharable
	static class RetryingChannelHandler extends ChannelInboundHandlerAdapter {

		private static final int BACKOFF_MULTIPLIER = 2;

		static class RetryOption<T> extends ChannelOption<T> {

			@SuppressWarnings("deprecation")
			RetryOption(String name) {
				super(name);
			}
		}

		private Duration minBackoff = Duration.ofMillis(100);

		private Duration maxBackoff = Duration.ofSeconds(5);

		private long backoffMs = minBackoff.toMillis();

		@Override
		public void channelActive(ChannelHandlerContext ctx) throws Exception {
			// Reset
			backoffMs = minBackoff.toMillis();
			super.channelActive(ctx);
		}

		@Override
		public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
			if (cause instanceof StatusException) {
				switch (((StatusException) cause).getStatus().getCode()) {
					case CANCELLED:
					case OK:
						log.log(Level.INFO, "Bidi channel closed");
						break;
					case UNAVAILABLE:
						log.log(Level.WARNING, cause.getCause(), () -> "Bidi channel is unavailable");
						break;
					default:
						log.log(Level.WARNING, cause, () -> "Unexpected bidi channel exception");
						break;
				}
			} else {
				super.exceptionCaught(ctx, cause);
			}

			Channel parent = ctx.channel().parent();

			backoffMs = Math.min(backoffMs * BACKOFF_MULTIPLIER, maxBackoff.toMillis());
			parent.eventLoop().schedule(parent.pipeline()::fireChannelReadComplete, backoffMs, TimeUnit.MILLISECONDS);
		}

		public <T> boolean setOption(ChannelOption<T> option, T value) {
			if (option == ChannelServerBuilder.MIN_BACKOFF) {
				Duration duration = (Duration) value;
				if (duration.toMillis() <= 0) {
					throw new IllegalArgumentException(option + " must be positive!");
				}
				minBackoff = duration.dividedBy(BACKOFF_MULTIPLIER);
				backoffMs = minBackoff.toMillis();
				return true;
			}

			if (option == ChannelServerBuilder.MAX_BACKOFF) {
				Duration duration = (Duration) value;
				if (duration.toMillis() <= 0) {
					throw new IllegalArgumentException(option + " must be positive!");
				}
				maxBackoff = duration;
				return true;
			}

			return false;
		}

		public <T> T getOption(ChannelOption<T> option) {
			if (option == ChannelServerBuilder.MIN_BACKOFF) {
				return (T) minBackoff.multipliedBy(BACKOFF_MULTIPLIER);
			}

			if (option == ChannelServerBuilder.MAX_BACKOFF) {
				return (T) maxBackoff;
			}

			return null;
		}
	}
}
