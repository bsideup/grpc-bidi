package io.grpc.bidi;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.Metadata;

import java.net.SocketAddress;

public class ChannelAddress extends SocketAddress {

	public static ChannelAddress of(Channel channel, CallOptions callOptions, Metadata headers) {
		return new ChannelAddress(channel, callOptions, headers);
	}

	final Channel channel;

	final CallOptions callOptions;

	final Metadata headers;

	private ChannelAddress(Channel channel, CallOptions callOptions, Metadata headers) {
		this.channel = channel;
		this.callOptions = callOptions;
		this.headers = headers;
	}
}
