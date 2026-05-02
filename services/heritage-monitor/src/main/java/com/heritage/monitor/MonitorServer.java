package com.heritage.monitor;

import com.heritage.naming.NamingServiceGrpc;
import com.heritage.naming.RegisterRequest;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Server;
import io.grpc.ServerBuilder;

public class MonitorServer {
    public static void main(String[] args) throws Exception{
        int port = 50053;
        Server server = ServerBuilder.forPort(port).addService(new MonitorServiceImpl()).build();

        server.start();
        System.out.println("Heritage Monitor Service starting on port " + port);
        registerToNamingService("Heritage-Monitor", "localhost", port);
        server.awaitTermination();
    }

    private static void registerToNamingService(String name, String host, int port){
    ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 50051).usePlaintext().build();
    NamingServiceGrpc.NamingServiceBlockingStub stub = NamingServiceGrpc.newBlockingStub(channel);

    try{
        stub.registerService(RegisterRequest.newBuilder().setServiceName(name).setAddress(host) .setPort(port).build());
        System.out.println("Successfully registered with Naming Service.");
    }catch (Exception e){
        System.out.println("Could not register: " + e.getMessage());
    }finally{
        channel.shutdown();
    }
}
}