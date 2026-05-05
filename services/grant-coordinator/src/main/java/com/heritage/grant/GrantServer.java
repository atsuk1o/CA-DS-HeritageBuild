package com.heritage.grant;

import com.heritage.naming.NamingServiceGrpc;
import com.heritage.naming.RegisterRequest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;

public class GrantServer{
    public static void main(String[] args) throws Exception{
        int port = 50054;
        Server server = ServerBuilder.forPort(port).addService(new GrantServiceImpl()).build();

        System.out.println("Grant Coordinator Service starting on port " + port);
        server.start();

        registerToNamingService("Grant-Coordinator", "localhost", port);
        server.awaitTermination();
    }

    private static void registerToNamingService(String name, String host, int port){
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext().build();
        NamingServiceGrpc.NamingServiceBlockingStub stub = NamingServiceGrpc.newBlockingStub(channel);

        try{
            stub.registerService(RegisterRequest.newBuilder().setServiceName(name).setAddress(host).setPort(port).build());
            System.out.println("Successfully registered with Naming Service.");
        }catch (StatusRuntimeException e){
            System.err.println("Failed to register: " + e.getStatus());
        }finally{
            channel.shutdown();
        }
    }
}