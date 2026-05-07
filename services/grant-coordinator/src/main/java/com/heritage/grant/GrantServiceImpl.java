package com.heritage.grant;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.grpc.stub.StreamObserver;

public class GrantServiceImpl extends GrantServiceGrpc.GrantServiceImplBase {
    private final Map<String, List<GrantResponse>> availableGrants = new ConcurrentHashMap<>();
    private final Map<String, String> applicationStatus = new ConcurrentHashMap<>();

    public GrantServiceImpl(){
        availableGrants.put("ireland", new ArrayList<>(Arrays.asList(
            GrantResponse.newBuilder().setStatus("AVAILABLE").setFeedback("EU Restoration Fund - up to €500000").build(),
            GrantResponse.newBuilder().setStatus("AVAILABLE").setFeedback("National Monuments Grant - up to €100000").build()
        )));
        availableGrants.put("ukraine", new ArrayList<>(Arrays.asList(
            GrantResponse.newBuilder().setStatus("AVAILABLE").setFeedback("UNESCO Emergency Safeguarding Fund - up to $250000").build()
        )));
        availableGrants.put("default", new ArrayList<>(Arrays.asList(
            GrantResponse.newBuilder().setStatus("AVAILABLE").setFeedback("UN Sustainable Cities Fund - up to $150000").build()
        )));
    }

    @Override
    public StreamObserver<GrantRequest> negotiateGrant(StreamObserver<GrantResponse> responseObserver){
        return new StreamObserver<GrantRequest>(){

            @Override
            public void onNext(GrantRequest request){
                System.out.println("Negotiation request: " + request.getProjectTitle()+ " | Amount: $" + request.getAmountRequested());

                String feedback = generateFeedback(request.getProjectTitle(), request.getAmountRequested());
                String appId = "GRT-" + Math.abs(request.getProjectTitle().hashCode());
                applicationStatus.put(appId, "UNDER_REVIEW");

                responseObserver.onNext(GrantResponse.newBuilder().setStatus("UNDER_REVIEW").setFeedback("Application ID: " + appId + " | " + feedback).build());
            }

            @Override
            public void onError(Throwable t){
                System.err.println("Negotiation error: " + t.getMessage());
            }

            @Override
            public void onCompleted(){
                responseObserver.onCompleted();
                System.out.println("Negotiation session ended.");
            }

            private String generateFeedback(String title, double amount){
                if(amount > 1000000)
                    return "Amount exceeds standard limit. Additional review required for: " + title;
                if(amount > 500000)
                    return "Large grant detected. Review initiated for: " + title;
                return "Application for '" + title + "' is under review. Expected decision in 10 working days.";
            }
        };
    }

    @Override
    public void getApplicationStatus(TrackRequest request, StreamObserver<GrantResponse> responseObserver){
        String appId = request.getApplicationId();
        System.out.println("Status check for: " + appId);

        String status = applicationStatus.getOrDefault(appId, "NOT_FOUND");

        if("NOT_FOUND".equals(status)){
            responseObserver.onError(io.grpc.Status.NOT_FOUND.withDescription("No application found with ID: " + appId).asRuntimeException());
            return;
        }

        responseObserver.onNext(GrantResponse.newBuilder().setStatus(status).setFeedback("Application " + appId + " is currently: " + status).build());
        responseObserver.onCompleted();
    }

    @Override
    public void listAvailableGrants(CountryRequest request, StreamObserver<GrantResponse> responseObserver){
        String country = request.getCountry().toLowerCase().trim();
        System.out.println("Listing grants for country: " + country);

        List<GrantResponse> grants = availableGrants.getOrDefault(country,availableGrants.get("default"));

        for (GrantResponse grant : grants) {
            responseObserver.onNext(grant);
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
        }
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<DocumentChunk> uploadDocuments(StreamObserver<UploadSummary> responseObserver){
        return new StreamObserver<DocumentChunk>(){
            private int chunks = 0;
            private String filename = "";
            private String appId = "";

            @Override
            public void onNext(DocumentChunk chunk){
                chunks++;
                filename = chunk.getFilename();
                appId = chunk.getApplicationId();
                System.out.println("Received chunk " + chunk.getChunkIndex() + " for file: " + filename);
            }

            @Override
            public void onError(Throwable t){
                System.err.println("Upload error: " + t.getMessage());
            }

            @Override
            public void onCompleted(){
                responseObserver.onNext(UploadSummary.newBuilder().setChunksReceived(chunks).setFilename(filename).setSuccess(true).setMessage("'" + filename + "' uploaded for application " + appId + ".").build());
                responseObserver.onCompleted();
                System.out.println("Upload complete: " + filename);
            }
        };
    }
}