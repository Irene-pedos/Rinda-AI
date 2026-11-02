package com.rinda.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RindaApplication {

    public static void main(String[] args) {
        SpringApplication.run(RindaApplication.class, args);
    }
}
