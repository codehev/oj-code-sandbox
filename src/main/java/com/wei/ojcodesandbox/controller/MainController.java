package com.wei.ojcodesandbox.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController("/")
public class MainController {
    @GetMapping("index")
    public String index() {
        String str="12";
        return "Hello World!";
    }
}
