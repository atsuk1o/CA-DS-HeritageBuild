package com.heritage.material;

import java.io.IOException;

import com.heritage.naming.NamingServiceGrpc;
import com.heritage.naming.RegisterRequest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;

public class MaterialServer {
    public static void main(String[] args) throws IOException, InterruptedException{
        int port = 50052;
        Server server = ServerBuilder.forPort(port).addService(new MaterialServiceImpl()).build();
        System.out.println("Material Vault Service starting on port " + port);
        server.start();

        registerWithNamingService("Material-Vault", port);
        server.awaitTermination();
    }

    private static void registerWithNamingService(String name, int port){
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext().build();
        NamingServiceGrpc.NamingServiceBlockingStub stub = NamingServiceGrpc.newBlockingStub(channel);
        RegisterRequest request = RegisterRequest.newBuilder().setServiceName(name).setAddress("localhost").setPort(port).build();

        try{
            stub.registerService(request);
            System.out.println("Successfully registered with Naming Service.");
        }catch(StatusRuntimeException e){
            System.err.println("Failed to register: " + e.getStatus());
        }
    }
}