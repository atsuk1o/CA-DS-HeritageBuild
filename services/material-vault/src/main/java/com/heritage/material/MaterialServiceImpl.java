package com.heritage.material;

import java.util.HashMap;
import java.util.Map;

import io.grpc.stub.StreamObserver;

public class MaterialServiceImpl extends VaultServiceGrpc.VaultServiceImplBase{
    private final Map<String, String> items = new HashMap<>();
    public MaterialServiceImpl() {
        items.put("H001", "Book of Kells");
        items.put("H002", "Tara Brooch");
        items.put("H003", "Ardagh Chalice");
    }

    @Override
    public void getItem(ItemRequest request, StreamObserver<ItemResponse> responseObserver){
        String name = items.getOrDefault(request.getItemId(), "Item Not Found");
        ItemResponse response = ItemResponse.newBuilder().setName(name).setDescription("Official record from Heritage Vault").build();
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void listAllItems(Empty request, StreamObserver<ItemResponse> responseObserver){
        for (Map.Entry<String, String> entry : items.entrySet()) {
            ItemResponse response = ItemResponse.newBuilder().setName(entry.getValue()).setDescription("ID: " + entry.getKey()).build();
            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }

    @Override
    public StreamObserver<SubmitRequest> submitMaterials(StreamObserver<SubmitSummary> responseObserver){
        return new StreamObserver<SubmitRequest>(){
            private int received = 0;
            private int accepted = 0;

            @Override
            public void onNext(SubmitRequest req){
                received++;
                if(!req.getName().isEmpty()){
                    String newId = "H00" + (items.size() + 1);
                    items.put(newId, req.getName());
                    accepted++;
                    System.out.println("Added: " + req.getName() + " → " + newId);
                }
            }

            @Override
            public void onError(Throwable t){
                System.err.println("Submit error: " + t.getMessage());
            }

            @Override
            public void onCompleted(){
                responseObserver.onNext(SubmitSummary.newBuilder().setTotalReceived(received).setTotalAccepted(accepted).setMessage(accepted + " materials added to vault.").build());
                responseObserver.onCompleted();
            }
        };
    }
}