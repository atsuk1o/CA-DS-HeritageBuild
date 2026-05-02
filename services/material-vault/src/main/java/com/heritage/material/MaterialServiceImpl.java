package com.heritage.material;

import java.util.HashMap;
import java.util.Map;

import io.grpc.stub.StreamObserver;

public class MaterialServiceImpl extends VaultServiceGrpc.VaultServiceImplBase {
    private final Map<String, String> items = new HashMap<>();
    public MaterialServiceImpl() {
        items.put("H001", "Book of Kells");
        items.put("H002", "Tara Brooch");
        items.put("H003", "Ardagh Chalice");
    }

    @Override
    public void getItem(ItemRequest request, StreamObserver<ItemResponse> responseObserver) {
        String name = items.getOrDefault(request.getItemId(), "Item Not Found");
        ItemResponse response = ItemResponse.newBuilder().setName(name).setDescription("Official record from Heritage Vault").build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void listAllItems(Empty request, StreamObserver<ItemResponse> responseObserver) {
        for (Map.Entry<String, String> entry : items.entrySet()) {
            ItemResponse response = ItemResponse.newBuilder().setName(entry.getValue()).setDescription("ID: " + entry.getKey()).build();
            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }
}