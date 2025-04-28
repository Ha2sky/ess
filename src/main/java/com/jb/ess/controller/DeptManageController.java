package com.jb.ess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DeptManageController {
    @GetMapping("/admin/deptmanage")
    public String deptManagePage() {
        return "deptmanage";
    }
}
