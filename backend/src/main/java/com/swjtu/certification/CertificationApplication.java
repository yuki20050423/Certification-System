package com.swjtu.certification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@MapperScan("com.swjtu.certification.mapper")
public class CertificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CertificationApplication.class, args);
    }
}

