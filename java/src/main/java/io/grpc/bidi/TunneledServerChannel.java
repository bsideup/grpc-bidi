package io.grpc.bidi;

import io.grpc.ClientCall;
import io.grpc.Metadata;
import io.grpc.Status;
import io.netty.buffer.ByteBuf;
import io.netty.channel.AbstractServerChannel;
import io.netty.channel.ChannelConfig;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.DefaultChannelConfig;
import io.netty.channel.EventLoop;
import io.netty.channel.SingleThreadEventLoop;

import java.net.SocketAddress;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class TunneledServerChannel extends AbstractServerChannel {

	private static final Logger log = Logger.getLogger(TunneledServerChannel.class.getName());

	enum State {
		INITIAL,
		OPEN,
		ACTIVE,
		CLOSED,
	}

	private final ChannelConfig config = new DefaultChannelConfig(this);

	private volatile State state = State.INITIAL;

	private volatile ChannelAddress channelAddress;

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
			channelAddress.callOptions
		);

		ChannelPipeline channelPipeline = pipeline();

		CallChannel callChannel = new CallChannel(call, channelAddress.headers);
		// Retry the connection
		// TODO retry delay?
		callChannel
			.pipeline()
			.addLast(
				new ChannelInboundHandlerAdapter() {
					@Override
					public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
						super.channelReadComplete(ctx);

						log.log(Level.INFO, "Channel closure");
						channelPipeline.fireChannelReadComplete();
					}

					@Override
					public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
						super.exceptionCaught(ctx, cause);

						log.log(Level.WARNING, cause, () -> "Unexpected channel closure");
						channelPipeline.fireChannelReadComplete();
					}
				}
			);
		channelPipeline.fireChannelRead(callChannel);
	}

	class CallChannel extends TunnelChannel {

		final ClientCall<ByteBuf, ByteBuf> call;

		final Metadata headers;

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

		private class CallListener extends ClientCall.Listener<ByteBuf> {

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
				switch (status.getCode()) {
					case CANCELLED:
					case OK:
					case UNAVAILABLE:
						pipeline().fireChannelReadComplete();
						break;
					default:
						pipeline().fireExceptionCaught(status.asException());
				}
			}
		}
	}
}
