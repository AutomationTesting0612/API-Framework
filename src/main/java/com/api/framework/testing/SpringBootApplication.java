package com.api.framework.testing;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;

import java.util.TimeZone;


@org.springframework.boot.autoconfigure.SpringBootApplication
public class SpringBootApplication {


    public static void main(String[] args) {

        SpringApplication.run(SpringBootApplication.class, args);

    }
}