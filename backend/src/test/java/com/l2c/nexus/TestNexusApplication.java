package com.l2c.nexus;

import org.springframework.boot.SpringApplication;

public class TestNexusApplication {

    public static void main(String[] args) {
        SpringApplication.from(NexusApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
