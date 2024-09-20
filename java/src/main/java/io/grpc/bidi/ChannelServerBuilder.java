package io.grpc.bidi;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ForwardingServerBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.DefaultEventLoopGroup;

import java.time.Duration;

public final class ChannelServerBuilder extends ForwardingServerBuilder<ChannelServerBuilder> {

	public static ChannelServerBuilder forChannel(Channel channel) {
		return new ChannelServerBuilder(NettyServerBuilder.forAddress(new ChannelAddress(channel)));
	}

	CallOptions callOptions = CallOptions.DEFAULT;

	Metadata metadata = new Metadata();

	final NettyServerBuilder nettyServerBuilder;

	ChannelServerBuilder(NettyServerBuilder nettyServerBuilder) {
		this.nettyServerBuilder = nettyServerBuilder;
	}

	public ChannelServerBuilder withRetryBackoff(Duration minBackoff, Duration maxBackoff) {
		withMinBackoff(minBackoff);
		withMaxBackoff(maxBackoff);
		return this;
	}

	public ChannelServerBuilder withMinBackoff(Duration minBackoff) {
		nettyServerBuilder.withOption(TunneledServerChannel.MIN_BACKOFF, minBackoff);
		return this;
	}

	public ChannelServerBuilder withMaxBackoff(Duration maxBackoff) {
		nettyServerBuilder.withOption(TunneledServerChannel.MAX_BACKOFF, maxBackoff);
		return this;
	}

	public ChannelServerBuilder withCallOptions(CallOptions callOptions) {
		this.callOptions = callOptions;
		return this;
	}

	public ChannelServerBuilder withMetadata(Metadata metadata) {
		this.metadata = metadata;
		return this;
	}

	@Override
	protected NettyServerBuilder delegate() {
		return nettyServerBuilder;
	}

	@Override
	public Server build() {
		delegate()
			.channelFactory(() -> new TunneledServerChannel(callOptions, metadata))
			.workerEventLoopGroup(new DefaultEventLoopGroup())
			.bossEventLoopGroup(new DefaultEventLoopGroup());

		return super.build();
	}
}
