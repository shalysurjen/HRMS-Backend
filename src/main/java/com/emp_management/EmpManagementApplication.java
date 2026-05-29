package com.emp_management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class EmpManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(EmpManagementApplication.class, args);
    }

}
