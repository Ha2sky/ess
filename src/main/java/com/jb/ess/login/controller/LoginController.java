package com.jb.ess.login.controller;

import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

// 로그인
@Controller
@RequiredArgsConstructor
public class LoginController {
    private final EmployeeMapper employeeMapper;

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
        model.addAttribute("deptCode", employeeMapper.getDeptCodeByEmpCode(user.getUsername()));
        model.addAttribute("isHeader", employeeMapper.findIsHeader(user.getUsername()));
        return "user/user";
    }
}
