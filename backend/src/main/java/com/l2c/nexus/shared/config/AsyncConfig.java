package com.l2c.nexus.shared.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Enables @Async. The pool is bounded through spring.task.execution.pool.* in application.yaml. */
@Configuration
@EnableAsync
class AsyncConfig {}
