package com.example;

import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;

public class Both {

	public static void main(String[] args) throws Exception {
		io.grpc.Server server = Server.createServer(50051);
		Runtime.getRuntime().addShutdownHook(new Thread(server::shutdownNow));

		Channel networkChannel = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext().build();

		io.grpc.Server clientServer = Client.createServer(networkChannel);
		Runtime.getRuntime().addShutdownHook(new Thread(clientServer::shutdownNow));

		server.start();
		System.out.printf("Server is listening on %s\n", server.getListenSockets().get(0));

		clientServer.start();

		server.awaitTermination();
	}
}
