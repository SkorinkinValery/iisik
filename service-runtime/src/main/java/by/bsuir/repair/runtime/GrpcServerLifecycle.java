package by.bsuir.repair.runtime;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerInterceptors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
public class GrpcServerLifecycle implements SmartLifecycle {
    private final int port; private final List<BindableService> services; private Server server; private volatile boolean running;
    public GrpcServerLifecycle(@Value("${grpc.server.port}") int port, List<BindableService> services) { this.port=port; this.services=services; }
    @Override public void start() { try { var builder=ServerBuilder.forPort(port); services.forEach(service -> builder.addService(ServerInterceptors.intercept(service,GrpcCorrelation.server()))); server=builder.build().start(); running=true; } catch(Exception e){ throw new IllegalStateException("Cannot start gRPC server",e); } }
    @Override public void stop() { if(server!=null){ try { server.shutdown().awaitTermination(5, TimeUnit.SECONDS); } catch(InterruptedException e){ Thread.currentThread().interrupt(); } } running=false; }
    @Override public boolean isRunning(){ return running; }
    @Override public int getPhase(){ return Integer.MAX_VALUE; }
}
