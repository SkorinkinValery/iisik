package by.bsuir.gateway;

import by.bsuir.repair.contract.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class GrpcClientsTest {
    private Server server;
    private GrpcClients clients;

    @AfterEach void close() throws Exception {
        if (clients != null) clients.close();
        if (server != null) server.shutdownNow().awaitTermination();
    }

    @Test void performsRealGrpcCallAndRetriesTransientFailure() throws Exception {
        AtomicInteger attempts = new AtomicInteger();
        server = ServerBuilder.forPort(0)
                .addService(new DeviceServiceGrpc.DeviceServiceImplBase() {
                    @Override public void getDevice(JsonMessage request, StreamObserver<JsonMessage> observer) {
                        observer.onNext(JsonMessage.newBuilder().setJson("{\"id\":\"device-1\"}").build());
                        observer.onCompleted();
                    }
                })
                .addService(new IdentityServiceGrpc.IdentityServiceImplBase() {
                    @Override public void getUserAccess(JsonMessage request, StreamObserver<JsonMessage> observer) {
                        if (attempts.incrementAndGet() < 3) {
                            observer.onError(Status.UNAVAILABLE.asRuntimeException());
                            return;
                        }
                        observer.onNext(JsonMessage.newBuilder().setJson("{\"id\":\"user-1\"}").build());
                        observer.onCompleted();
                    }
                }).build().start();
        String address = "localhost:" + server.getPort();
        clients = new GrpcClients(new ObjectMapper().findAndRegisterModules(), address, address, address, address, address, address, address, address);

        Map<String,Object> device = clients.map(clients.call(() -> clients.deadline(clients.device).getDevice(clients.message(Map.of("id", "device-1")))));
        Map<String,Object> user = clients.map(clients.call(() -> clients.deadline(clients.identity).getUserAccess(clients.message(Map.of("id", "user-1")))));
        assertThat(device.get("id")).isEqualTo("device-1");
        assertThat(user.get("id")).isEqualTo("user-1");
        assertThat(attempts).hasValue(3);
    }
}
