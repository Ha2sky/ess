package com.jb.ess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 관리자 메인페이지
@Controller
@RequestMapping("/admin")
public class AdminMainController {
    @GetMapping("/department")
    public String deptManagementPage() {
        return "redirect:/admin/department/list";
    }

    @GetMapping("/pattern")
    public String patternPage() {
        return "admin/pattern";
    }
}
