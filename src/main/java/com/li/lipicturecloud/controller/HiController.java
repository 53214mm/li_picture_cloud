package com.li.lipicturecloud.controller;


import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/hi")
public class HiController {

    @GetMapping("/")
    public String sayHi() {
        return "Hi, welcome to LiPictureCloud!";
    }
}
