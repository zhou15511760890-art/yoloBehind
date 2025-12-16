package com.example.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaForwardController {

    @RequestMapping(value = {
            "/",
            "/login",
            "/app",
            "/map",
            "/error",
            "/{path:^(?!api$).*$}",
            "/{path:^(?!api$).*$}/**/{path2:[^\\.]*}"
    })
    public String forward() {
        return "forward:/index.html";
    }
}
