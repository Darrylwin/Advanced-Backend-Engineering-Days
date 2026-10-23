package com.darcode.day02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Day02LostUpdateApplication {

    public static void main(String[] args) {
        SpringApplication.run(Day02LostUpdateApplication.class, args);
    }

}
