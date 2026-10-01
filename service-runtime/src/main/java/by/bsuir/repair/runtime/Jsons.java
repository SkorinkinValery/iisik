package by.bsuir.repair.runtime;

import by.bsuir.repair.contract.JsonMessage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class Jsons {
    private final ObjectMapper mapper;
    public Jsons(ObjectMapper mapper) { this.mapper = mapper; }
    public Map<String,Object> read(JsonMessage value) {
        try { return mapper.readValue(value.getJson(), new TypeReference<>() {}); }
        catch (Exception e) { throw Status.INVALID_ARGUMENT.withDescription("Invalid JSON payload").withCause(e).asRuntimeException(); }
    }
    public Map<String,Object> read(String value) {
        try { return mapper.readValue(value, new TypeReference<>() {}); }
        catch (Exception e) { throw new IllegalArgumentException("Invalid JSON payload", e); }
    }
    public String writeString(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (Exception e) { throw new IllegalStateException("Cannot serialize payload", e); }
    }
    public JsonMessage write(Object value) { return JsonMessage.newBuilder().setJson(writeString(value)).build(); }
    public void reply(StreamObserver<JsonMessage> observer, Object value) { observer.onNext(write(value)); observer.onCompleted(); }
    public void fail(StreamObserver<?> observer, Throwable error) {
        if (error instanceof io.grpc.StatusRuntimeException status) observer.onError(status);
        else if (error instanceof IllegalArgumentException) observer.onError(Status.INVALID_ARGUMENT.withDescription(error.getMessage()).withCause(error).asRuntimeException());
        else if (error instanceof SecurityException) observer.onError(Status.PERMISSION_DENIED.withDescription(error.getMessage()).asRuntimeException());
        else observer.onError(Status.INTERNAL.withDescription(error.getMessage()).withCause(error).asRuntimeException());
    }
    public String string(Map<String,Object> map, String key) {
        Object value=map.get(key); if(value==null || value.toString().isBlank()) throw new IllegalArgumentException(key+" is required"); return value.toString();
    }
    public String optional(Map<String,Object> map, String key) { Object v=map.get(key); return v==null?null:v.toString(); }
    public int integer(Map<String,Object> map, String key, int defaultValue) { Object v=map.get(key); return v==null?defaultValue:Integer.parseInt(v.toString()); }
    public boolean bool(Map<String,Object> map, String key, boolean defaultValue) { Object v=map.get(key); return v==null?defaultValue:Boolean.parseBoolean(v.toString()); }
}
