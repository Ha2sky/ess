package com.jb.ess.controller;

import com.jb.ess.security.CustomUserDetails;
import com.jb.ess.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// 로그인
@Controller
@RequiredArgsConstructor
public class LoginController {
    private final EmployeeService employeeService;

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/admin")
    public String adminPage() {
        return "admin/admin";
    }

    @GetMapping("/user")
    public String userPage(Model model, @AuthenticationPrincipal CustomUserDetails user) {
        String empCode = user.getUsername();
        model.addAttribute("emp", employeeService.findIsHeader(empCode));
        return "user/user";
    }
}
