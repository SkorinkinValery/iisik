package by.bsuir.repair.runtime;

import io.grpc.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

public final class GrpcCorrelation {
    private static final Logger log = LoggerFactory.getLogger(GrpcCorrelation.class);
    private static final Metadata.Key<String> HEADER = Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER);
    private GrpcCorrelation() {}

    public static ClientInterceptor client() {
        return new ClientInterceptor() {
            @Override public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
                    MethodDescriptor<ReqT, RespT> method, CallOptions options, Channel next) {
                return new ForwardingClientCall.SimpleForwardingClientCall<>(next.newCall(method, options)) {
                    @Override public void start(Listener<RespT> listener, Metadata headers) {
                        String id = MDC.get("correlationId");
                        headers.put(HEADER, id == null ? UUID.randomUUID().toString() : id);
                        super.start(listener, headers);
                    }
                };
            }
        };
    }

    public static ServerInterceptor server() {
        return new ServerInterceptor() {
            @Override public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                    ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
                String id = headers.get(HEADER);
                if (id == null || id.isBlank()) id = UUID.randomUUID().toString();
                MDC.put("correlationId", id);
                long started = System.nanoTime();
                try {
                    log.info("grpc_request method={}", call.getMethodDescriptor().getFullMethodName());
                    return next.startCall(call, headers);
                } finally {
                    log.info("grpc_dispatched method={} durationMs={}", call.getMethodDescriptor().getFullMethodName(), (System.nanoTime()-started)/1_000_000);
                    MDC.remove("correlationId");
                }
            }
        };
    }
}
