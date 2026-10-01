package by.bsuir.identity;
import org.springframework.boot.SpringApplication; import org.springframework.boot.autoconfigure.SpringBootApplication; import org.springframework.scheduling.annotation.EnableScheduling;
@EnableScheduling @SpringBootApplication(scanBasePackages="by.bsuir") public class IdentityApplication { public static void main(String[] args){SpringApplication.run(IdentityApplication.class,args);} }
