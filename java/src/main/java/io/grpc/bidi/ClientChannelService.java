package io.grpc.bidi;

import io.grpc.BindableService;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.MethodDescriptor;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerServiceDefinition;
import io.grpc.Status;
import io.grpc.netty.NettyChannelBuilder;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultEventLoopGroup;
import io.netty.channel.local.LocalAddress;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class ClientChannelService implements BindableService {

	private static final Logger log = Logger.getLogger(TunnelHandler.class.getName());

	static final String TUNNEL_SERVICE = "io.grpc.Tunnel";

	public static final MethodDescriptor<ByteBuf, ByteBuf> NEW_TUNNEL_METHOD = MethodDescriptor
		.newBuilder(ByteBufMarshaller.INSTANCE, ByteBufMarshaller.INSTANCE)
		.setFullMethodName(TUNNEL_SERVICE + "/new")
		.setType(MethodDescriptor.MethodType.BIDI_STREAMING)
		.build();

	public abstract void onChannel(ManagedChannel channel, Metadata headers);

	@Override
	public final ServerServiceDefinition bindService() {
		return ServerServiceDefinition.builder(TUNNEL_SERVICE).addMethod(NEW_TUNNEL_METHOD, new TunnelHandler()).build();
	}

	public void tune(NettyChannelBuilder builder) {}

	class TunnelHandler implements ServerCallHandler<ByteBuf, ByteBuf> {

		private final AtomicLong id = new AtomicLong();

		@Override
		public ServerCall.Listener<ByteBuf> startCall(ServerCall<ByteBuf, ByteBuf> call, Metadata headers) {
			try {
				call.sendHeaders(new Metadata());
				call.request(Integer.MAX_VALUE);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

			DefaultEventLoopGroup eventLoopGroup = new DefaultEventLoopGroup();

			TunnelChannel channel = new TunnelChannel() {
				@Override
				protected void doConsume(ByteBuf byteBuf) {
					call.sendMessage(byteBuf.retain());
				}

				@Override
				public boolean isActive() {
					return !call.isCancelled();
				}

				@Override
				protected void doClose() {
					try {
						if (!call.isCancelled()) {
							call.close(Status.CANCELLED, new Metadata());
						}
					} catch (Exception e) {
						log.log(Level.INFO, "Call already closed");
					}
					super.doClose();
				}
			};

			NettyChannelBuilder channelBuilder = NettyChannelBuilder.forAddress(
				new LocalAddress("server-channel-" + id.incrementAndGet())
			);
			tune(channelBuilder);
			ManagedChannel grpcChannel = channelBuilder
				.disableRetry()
				.eventLoopGroup(eventLoopGroup)
				.channelFactory(() -> channel)
				.withOption(ChannelOption.AUTO_READ, false)
				.usePlaintext()
				.build();

			onChannel(grpcChannel, headers);

			return new ServerCall.Listener<ByteBuf>() {
				@Override
				public void onMessage(ByteBuf byteBuf) {
					if (byteBuf.readableBytes() > 0) {
						channel.pipeline().fireChannelRead(byteBuf);
					} else {
						byteBuf.release();
					}
				}

				@Override
				public void onHalfClose() {
					onCancel();
				}

				@Override
				public void onComplete() {
					onCancel();
				}

				@Override
				public void onCancel() {
					grpcChannel.shutdownNow();
					eventLoopGroup.shutdownGracefully();
				}
			};
		}
	}
}
