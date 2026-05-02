package com.heritage.naming;

import java.io.IOException;

import io.grpc.Server;
import io.grpc.ServerBuilder;

public class NamingServer {
    public static void main(String[] args) throws IOException, InterruptedException {
        int port = 50051;

        Server server = ServerBuilder.forPort(port).addService(new NamingServiceImpl()).build();
        System.out.println("Naming Service is starting");
        System.out.println("Listening on localhost:" + port);
        
        server.start();

        System.out.println("Service Discovery is active");
        server.awaitTermination();
    }
}