package com.jb.ess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 로그인
@Controller
public class LoginController {
    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "admin/admin";
    }

    @GetMapping("/user")
    public String userPage() {
        return "user/user";
    }
}
