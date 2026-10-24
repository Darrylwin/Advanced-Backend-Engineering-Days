package com.darcode.day02.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "demo")
public record DemoProperties(
        @DefaultValue("20ms") Duration processingDelay,
        @DefaultValue("5") int optimisticMaxAttempts) {
}