package com.li.lipicturecloud;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@MapperScan("com.li.lipicturecloud.mapper")
@EnableAspectJAutoProxy(exposeProxy = true)
public class LiPictureCloudApplication {

    public static void main(String[] args) {
        SpringApplication.run(LiPictureCloudApplication.class, args);
    }

}
