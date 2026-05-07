package com.heritage.grant;

import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class AuthInterceptor implements ServerInterceptor {

    private static final String VALID_TOKEN = "heritage-secret-2025";

    public static final Metadata.Key<String> TOKEN_KEY =
            Metadata.Key.of("auth-token", Metadata.ASCII_STRING_MARSHALLER);

    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {

        String token = headers.get(TOKEN_KEY);

        if (token == null || !token.equals(VALID_TOKEN)) {
            System.out.println("[AuthInterceptor] Rejected request — invalid or missing token.");
            call.close(Status.UNAUTHENTICATED.withDescription("Invalid or missing auth token."), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }

        System.out.println("[AuthInterceptor] Request authorised.");
        return next.startCall(call, headers);
    }
}