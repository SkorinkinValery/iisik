package by.bsuir.repair.runtime;

import jakarta.servlet.*; import jakarta.servlet.http.*; import org.slf4j.MDC; import org.springframework.stereotype.Component;
import java.io.IOException; import java.util.UUID;

@Component
public class CorrelationIdFilter extends GenericFilter {
    @Override public void doFilter(ServletRequest request,ServletResponse response,FilterChain chain)throws IOException,ServletException{
        var http=(HttpServletRequest)request; String id=http.getHeader("X-Correlation-Id"); if(id==null||id.isBlank())id=UUID.randomUUID().toString();
        ((HttpServletResponse)response).setHeader("X-Correlation-Id",id); try(var ignored=MDC.putCloseable("correlationId",id)){chain.doFilter(request,response);}
    }
}
