package com.example;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.ServerBuilder;
import io.grpc.bidi.ClientChannelService;
import io.grpc.health.v1.HealthCheckRequest;
import io.grpc.health.v1.HealthCheckResponse;
import io.grpc.health.v1.HealthGrpc;
import io.grpc.stub.StreamObserver;

public class Server {

	public static void main(String[] args) throws Exception {
		ServerBuilder
			.forPort(50051)
			.addService(
				new ClientChannelService() {
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
			.build()
			.start()
			.awaitTermination();
	}
}
