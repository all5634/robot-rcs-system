package com._project4.project4.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class FrontendPageController {

    @GetMapping("/frontend")
    public String frontendRoot() {
        return "redirect:/frontend/index.html";
    }

    @GetMapping("/frontend/index")
    public String frontendIndexAlias() {
        return "redirect:/frontend/index.html";
    }
}