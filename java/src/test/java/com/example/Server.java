package com.example;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.ServerBuilder;
import io.grpc.bidi.ClientChannelService;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;

public class Server {

	public static void main(String[] args) throws Exception {
		io.grpc.Server server = createServer(50051);
		server.start();
		System.out.println("Server started on " + server.getListenSockets().get(0));
		server.awaitTermination();
	}

	static io.grpc.Server createServer(int port) {
		return ServerBuilder
			.forPort(port)
			.addService(
				new ClientChannelService() {
					@Override
					public void tune(ManagedChannelBuilder<?> builder) {
						builder.keepAliveWithoutCalls(true);
					}

					@Override
					public void onChannel(ManagedChannel channel, Metadata headers) {
						HealthGrpc.HealthStub healthStub = HealthGrpc.newStub(channel);

						healthStub.watch(
							HealthCheckRequest.getDefaultInstance(),
							new StreamObserver<HealthCheckResponse>() {
								@Override
								public void onNext(HealthCheckResponse healthCheckResponse) {
									System.out.println("Health response: " + healthCheckResponse);
								}

								@Override
								public void onError(Throwable e) {
									e.printStackTrace();
								}

								@Override
								public void onCompleted() {
									System.out.println("Completed");
								}
							}
						);
					}
				}
			)
			.build();
	}
}
