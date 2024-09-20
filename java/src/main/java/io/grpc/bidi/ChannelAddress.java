package io.grpc.bidi;

import io.grpc.Channel;

import java.net.SocketAddress;

class ChannelAddress extends SocketAddress {

	final Channel channel;

	ChannelAddress(Channel channel) {
		this.channel = channel;
	}
}
