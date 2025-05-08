package com.jb.ess.controller;

import com.jb.ess.domain.Department;
import com.jb.ess.service.DepartmentService;
import com.jb.ess.util.DateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/admin/department")
@RequiredArgsConstructor
public class AdminDepartmentController {
    private final DepartmentService departmentService;

    /* 부서 목록 */
    @GetMapping("/list")
    public String departmentList(Model model) {
        model.addAttribute("departments", departmentService.getAllDepartments());
        return "admin/department/list";
    }

    /* 부서 등록 폼 */
    @GetMapping("/add")
    public String addDepartmentForm(Model model) {
        model.addAttribute("departments", departmentService.getAllDepartments());
        return "admin/department/add";
    }

    /* 부서 등록 */
    @PostMapping("/add")
    public String addDepartment(@ModelAttribute Department department, Model model) {
        sanitizeDepartment(department);

        try {
            departmentService.saveDepartment(department);
            return "redirect:/admin/department/list";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("departments", departmentService.getAllDepartments());
            return "admin/department/add";
        }
    }

    /* 부서 수정 폼 */
    @GetMapping("/edit/{deptCode}")
    public String editDepartmentForm(@PathVariable String deptCode, Model model) {
        Department department = departmentService.getDepartmentByDeptCode(deptCode);
        department.setStartDate(DateUtil.formatDate(department.getStartDate()));
        department.setEndDate(DateUtil.formatDate(department.getEndDate()));
        model.addAttribute("department", department);
        model.addAttribute("departments", departmentService.getAllDepartments());
        model.addAttribute("originalDeptCode", deptCode);
        return "admin/department/edit";
    }

    /* 부서 수정 */
    @PostMapping("/edit")
    public String editDepartment(@ModelAttribute Department department,
        @RequestParam("originalDeptCode") String originalDeptCode,
        Model model) {

        if (!originalDeptCode.equals(department.getDeptCode()) &&
            departmentService.existsByDeptCode(department.getDeptCode())) {
            model.addAttribute("errorMessage", "이미 존재하는 부서코드입니다.");
            model.addAttribute("department", department);
            model.addAttribute("departments", departmentService.getAllDepartments());
            model.addAttribute("originalDeptCode", originalDeptCode);
            return "admin/department/edit";
        }

        sanitizeDepartment(department);
        departmentService.updateDepartment(department);
        return "redirect:/admin/department/list";
    }

    /* 부서 삭제 */
    @PostMapping("/delete/{deptCode}")
    public String deleteDepartment(@PathVariable String deptCode) {
        try {
            departmentService.deleteDepartment(deptCode);
            return "redirect:/admin/department";
        } catch (Exception e) {
            return "redirect:/admin/department?error=true";
        }
    }

    /* 공통 처리 메서드 */
    private void sanitizeDepartment(Department department) {
        department.setDeptName(defaultIfNull(department.getDeptName()));
        department.setParentDept(defaultIfNull(department.getParentDept()));
        department.setDeptLeader(defaultIfNull(department.getDeptLeader()));
        department.setUseYn(department.getUseYn() == null ? "N" : department.getUseYn());

        String start = defaultIfNull(department.getStartDate()).replace("-", "");
        String end = defaultIfNull(department.getEndDate(), "99991231").replace("-", "");

        department.setStartDate(start);
        department.setEndDate(end);
    }

    /* 시작일 */
    private String defaultIfNull(String value) {
        return value == null ? "" : value;
    }

    /* 종료일 */
    private String defaultIfNull(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}