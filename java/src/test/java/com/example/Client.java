package com.example;

import io.grpc.CallOptions;
import io.grpc.Channel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.bidi.ChannelServerBuilder;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthCheckResponse.ServingStatus;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Client {

	public static void main(String[] args) throws Exception {
		Channel networkChannel = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext().build();

		Server server = createServer(networkChannel);

		server.start().awaitTermination();
	}

	static Server createServer(Channel networkChannel) {
		return ChannelServerBuilder
			.forChannel(networkChannel)
			// Everything else is optional
			.withCallOptions(CallOptions.DEFAULT)
			.withMetadata(new Metadata())
			.withRetryBackoff(Duration.ofMillis(500), Duration.ofSeconds(5))
			.permitKeepAliveWithoutCalls(true)
			.directExecutor()
			.addService(
				new HealthGrpc.HealthImplBase() {
					final ScheduledExecutorService scheduledExecutor = Executors.newSingleThreadScheduledExecutor();

					@Override
					public void watch(HealthCheckRequest request, StreamObserver<HealthCheckResponse> responseObserver) {
						System.out.println("Received request: " + request);

						HealthCheckResponse response = HealthCheckResponse.newBuilder().setStatus(ServingStatus.SERVING).build();
						scheduledExecutor.scheduleAtFixedRate(() -> responseObserver.onNext(response), 0, 1, TimeUnit.SECONDS);
					}
				}
			)
			.build();
	}
}
