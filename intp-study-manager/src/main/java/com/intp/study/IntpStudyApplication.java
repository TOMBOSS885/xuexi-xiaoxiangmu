package com.intp.study;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class IntpStudyApplication {
    public static void main(String[] args) {
        SpringApplication.run(IntpStudyApplication.class, args);
    }
}
