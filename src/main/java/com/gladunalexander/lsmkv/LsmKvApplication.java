package com.gladunalexander.lsmkv;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the LSM-tree based key-value storage engine.
 *
 * <p>This is an incremental implementation of The Coder Cafe's "Build Your Own
 * Key-Value Storage Engine" series. Each week of the series is a separate commit.
 *
 * <p>Week 1: a simple in-memory store exposed over REST (PUT/GET).
 *
 * <p>The component scan is intentionally rooted at this package so the many
 * unrelated experiments living under {@code com.gladunalexander} are not wired in.
 */
@SpringBootApplication(scanBasePackages = "com.gladunalexander.lsmkv")
public class LsmKvApplication {

    public static void main(String[] args) {
        SpringApplication.run(LsmKvApplication.class, args);
    }
}
