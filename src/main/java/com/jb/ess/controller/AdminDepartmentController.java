package com.jb.ess.controller;

import com.jb.ess.service.DepartmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/department")
@RequiredArgsConstructor
public class AdminDepartmentController {
    private final DepartmentService departmentService;

    @GetMapping("/list")
    public String departmentList(Model model) {
        model.addAttribute("departments", departmentService.getAllDepartments());
        return "admin/department/list";
    }
}
