package com.dataReconciliation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class dataReconciliation {

    public static void main(String[] args) {
        SpringApplication.run(dataReconciliation.class, args);
    }

}
