package com.heritage.monitor;

import java.util.ArrayList;
import java.util.List;

import io.grpc.stub.StreamObserver;

public class MonitorServiceImpl extends MonitorServiceGrpc.MonitorServiceImplBase{

    @Override
    public StreamObserver<StatusRequest> reportStatusBatch(StreamObserver<StatusSummary> responseObserver){
        return new StreamObserver<StatusRequest>(){
            private final List<String> statusLog = new ArrayList<>();
            private String lastStatusReceived = "None";

            @Override
            public void onNext(StatusRequest request){
                lastStatusReceived = request.getStatus();
                statusLog.add(request.getServiceName() + ": " + lastStatusReceived);
                System.out.println("📊 Status Update received from: " + request.getServiceName());
            }

            @Override
            public void onError(Throwable t){
                System.err.println("Monitor Stream Error: " + t.getMessage());
            }

            @Override
            public void onCompleted(){
                StatusSummary summary = StatusSummary.newBuilder().setReportsReceived(statusLog.size()).setLastStatus(lastStatusReceived).build();

                responseObserver.onNext(summary);
                responseObserver.onCompleted();
                System.out.println("Batch processing complete. Summary sent.");
            }
        };
    }
}