package com.darcode.day01;

import org.springframework.boot.SpringApplication;

public class TestDay01IdempotenceApplication {

    public static void main(String[] args) {
        SpringApplication.from(Day01IdempotenceApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
