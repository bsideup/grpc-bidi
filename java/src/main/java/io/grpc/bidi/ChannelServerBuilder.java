package io.grpc.bidi;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.grpc.CallOptions;
import io.grpc.ForwardingServerBuilder;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.bidi.TunneledServerChannel.RetryingChannelHandler.RetryOption;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.ChannelOption;
import io.netty.channel.DefaultEventLoopGroup;

import java.time.Duration;

public final class ChannelServerBuilder extends ForwardingServerBuilder<ChannelServerBuilder> {

	public static ChannelOption<Duration> MIN_BACKOFF = new RetryOption<>("minBackoff");

	public static ChannelOption<Duration> MAX_BACKOFF = new RetryOption<>("maxBackoff");

	public static ChannelServerBuilder forChannel(ManagedChannel channel) {
		return new ChannelServerBuilder(NettyServerBuilder.forAddress(new ChannelAddress(channel)));
	}

	CallOptions callOptions = CallOptions.DEFAULT;

	Metadata metadata = new Metadata();

	final NettyServerBuilder nettyServerBuilder;

	ChannelServerBuilder(NettyServerBuilder nettyServerBuilder) {
		this.nettyServerBuilder = nettyServerBuilder;
	}

	@CanIgnoreReturnValue
	public <T> ChannelServerBuilder withOption(ChannelOption<T> option, T value) {
		this.nettyServerBuilder.withOption(option, value);
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
