package com.jb.ess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminMainController {
    @GetMapping("/admin/deptmanage")
    public String deptManagementPage() {
        return "admin/deptmanage";
    }

    @GetMapping("/admin/pattern")
    public String patternPage() {
        return "admin/pattern";
    }
}
