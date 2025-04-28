package com.jb.ess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UserController {
    @GetMapping("/user")
    public String userPage() {
        return "user/user";
    }
}
