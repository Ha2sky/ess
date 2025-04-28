package com.jb.ess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// localhost:8080 -> localhost:8080/login 리다이렉트
@Controller
public class HomeController {
    @GetMapping("/")
    public String redirectToLogin() {
        return "redirect:/login";
    }
}