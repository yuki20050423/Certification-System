package com.swjtu.certification;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.swjtu.certification.mapper")
public class CertificationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CertificationApplication.class, args);
    }
}

