package com.heritage.naming;

import java.util.concurrent.ConcurrentHashMap;

import io.grpc.stub.StreamObserver;

public class NamingServiceImpl extends NamingServiceGrpc.NamingServiceImplBase {

    private final ConcurrentHashMap<String, String> serviceRegistry = new ConcurrentHashMap<>();

    @Override
    public void registerService(RegisterRequest request, StreamObserver<RegisterResponse> responseObserver) {
        String name = request.getServiceName();
        String fullAddress = request.getAddress() + ":" + request.getPort();

        serviceRegistry.put(name, fullAddress);
        System.out.println("Registered Service: " + name + " at " + fullAddress);

        RegisterResponse response = RegisterResponse.newBuilder().setSuccess(true).setMessage("Service " + name + " is now online.").build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void discoverService(DiscoverRequest request, StreamObserver<DiscoverResponse> responseObserver) {
        String name = request.getServiceName();
        String addressInfo = serviceRegistry.getOrDefault(name, "NOT_FOUND");

        DiscoverResponse.Builder responseBuilder = DiscoverResponse.newBuilder();

        if (!addressInfo.equals("NOT_FOUND")) {
            String[] parts = addressInfo.split(":");
            responseBuilder.setAddress(parts[0]);
            responseBuilder.setPort(Integer.parseInt(parts[1]));
        }

        responseObserver.onNext(responseBuilder.build());
        responseObserver.onCompleted();
    }
}