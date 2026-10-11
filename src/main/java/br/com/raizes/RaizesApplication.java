package br.com.raizes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(
    exclude =
        org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration
            .class)
public class RaizesApplication {
  public static void main(String[] args) {
    SpringApplication.run(RaizesApplication.class, args);
  }
}
