package com.darcode.day02.service;

import com.darcode.day02.config.DemoProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Stands for the business logic that runs between reading and writing an account. */
@Component
public class SimulatedProcessing {

    private final Duration delay;

    SimulatedProcessing(DemoProperties properties) {
        this.delay = properties.processingDelay();
    }

    public void pause() {
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while processing", e);
        }
    }
}
