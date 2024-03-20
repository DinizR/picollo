/*
* Picollo.java
 */
package org.picollo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.ApplicationPidFileWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Entry point for Picollo Service
 * @author rod
 * @since 2018-04
 */
@SpringBootApplication
@EnableScheduling
public class Picollo {
    private static final Logger log = LoggerFactory.getLogger(Picollo.class);

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(Picollo.class);

        log.info("Starting Picollo Core...");
        application.addListeners(new ApplicationPidFileWriter("./picollo-shutdown.pid"));
        application.run();
        log.info("Picollo started...");
    }

    @Bean
    public TaskScheduler threadPoolTaskScheduler() {
        return new ThreadPoolTaskScheduler();
    }
}