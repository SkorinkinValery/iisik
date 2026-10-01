package by.bsuir.identity;
import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/auth") public class AuthRestController {
 private final IdentityGrpcService service; public AuthRestController(IdentityGrpcService service){this.service=service;}
 @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED) Map<String,Object> register(@RequestBody Map<String,Object> body){return service.register(body);}
 @PostMapping("/login") Map<String,Object> login(@RequestBody Map<String,Object> body){return service.login(body);}
}
