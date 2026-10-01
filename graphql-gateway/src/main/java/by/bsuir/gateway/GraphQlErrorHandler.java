package by.bsuir.gateway;

import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class GraphQlErrorHandler extends DataFetcherExceptionResolverAdapter {
    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        Throwable value = ex;
        while (value.getCause() != null && value != value.getCause()) value = value.getCause();
        String code = "INTERNAL_ERROR";
        String message = value.getMessage() == null ? "Operation failed" : value.getMessage();
        if (value instanceof StatusRuntimeException grpc) {
            Status status = grpc.getStatus();
            code = switch (status.getCode()) {
                case UNAUTHENTICATED -> "UNAUTHENTICATED";
                case PERMISSION_DENIED -> "FORBIDDEN";
                case NOT_FOUND -> "NOT_FOUND";
                case ALREADY_EXISTS -> "CONFLICT";
                case INVALID_ARGUMENT, FAILED_PRECONDITION -> "BAD_REQUEST";
                case DEADLINE_EXCEEDED, UNAVAILABLE -> "SERVICE_UNAVAILABLE";
                default -> "INTERNAL_ERROR";
            };
            if (status.getDescription() != null) message = status.getDescription();
        } else if (value instanceof SecurityException || value instanceof org.springframework.security.access.AccessDeniedException) {
            code = "FORBIDDEN";
        } else if (value instanceof IllegalArgumentException) {
            code = "BAD_REQUEST";
        }
        return GraphqlErrorBuilder.newError(env).message(message).extensions(Map.of("code", code)).build();
    }
}
