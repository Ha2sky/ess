package com.jb.ess.controller;

import com.jb.ess.domain.Department;
import com.jb.ess.service.DepartmentService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/department")
@RequiredArgsConstructor
public class AdminDepartmentController {
    private final DepartmentService departmentService;

/* ===============================================================================================
    부서 목록
=============================================================================================== */
    @GetMapping("/list")
    public String departmentList(Model model) {
        model.addAttribute("departments", departmentService.getAllDepartments());
        return "admin/department/list";
    }

/* ===============================================================================================
    부서 등록(추가)
=============================================================================================== */
    @GetMapping("/add")
    public String addDepartmentForm(Model model) {
        model.addAttribute("departments", departmentService.getAllDepartments());
        return "admin/department/add";
    }

    @PostMapping("/add")
    public String addDepartment(@ModelAttribute Department department, Model model) {
        if (department.getDeptName() == null) department.setDeptName("");
        if (department.getParentDept() == null) department.setParentDept("");
        if (department.getDeptLeader() == null) department.setDeptLeader("");
        if (department.getUseYn() == null) department.setUseYn("N");
        if (department.getStartDate() == null) department.setStartDate("");
        if (department.getEndDate() == null || department.getEndDate().isBlank()) {
            department.setEndDate("99991231");
        }

        // 시작일, 종료일 하이픈 제거
        if (department.getStartDate() != null && !department.getStartDate().isBlank()) {
            department.setStartDate(department.getStartDate().replace("-", ""));
        }
        if (department.getEndDate() == null || department.getEndDate().isBlank()) {
            department.setEndDate("99991231");
        } else {
            department.setEndDate(department.getEndDate().replace("-", ""));
        }

        try { // 부서코드 중복체크
            departmentService.saveDepartment(department);
            return "redirect:/admin/department/list";
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            return "admin/department/add";
        }
    }

/* ===============================================================================================
    부서 수정
=============================================================================================== */
    @GetMapping("/edit/{deptCode}")
    public String editDepartmentForm(@PathVariable String deptCode, Model model) {
        Department department = departmentService.getDepartmentByDeptCode(deptCode);
        List<Department> departments = departmentService.getAllDepartments();
        model.addAttribute("department", department);
        model.addAttribute("departments", departmentService.getAllDepartments());
        return "admin/department/edit";
    }

    @PostMapping("/update")
    public String editDepartment(@ModelAttribute Department department) {
        if (department.getParentDept() == null) department.setParentDept("");
        if (department.getDeptLeader() == null) department.setDeptLeader("");
        if (department.getStartDate() == null) department.setStartDate("");
        if (department.getUseYn() == null) department.setUseYn("N");
        if (department.getEndDate() == null || department.getEndDate().isBlank()) {
            department.setEndDate("99991231"); // 종료일 없으면 99991231
        }

        // 시작일, 종료일 하이픈 제거
        if (department.getStartDate() != null && !department.getStartDate().isBlank()) {
            department.setStartDate(department.getStartDate().replace("-", ""));
        }
        if (department.getEndDate() == null || department.getEndDate().isBlank()) {
            department.setEndDate("99991231");
        } else {
            department.setEndDate(department.getEndDate().replace("-", ""));
        }
        departmentService.updateDepartment(department);
        return "redirect:/admin/department/list";
    }

/* ===============================================================================================
    부서 삭제
=============================================================================================== */
    @PostMapping("/delete/{deptCode}")
    public String deleteDepartment(@PathVariable String deptCode) {
        try {
            departmentService.deleteDepartment(deptCode); // 부서 삭제
            return "redirect:/admin/department"; // 부서 목록 페이지로 리다이렉트
        } catch (Exception e) {
            // 오류 처리 로직, 예: 부서 삭제 실패
            return "redirect:/admin/department?error=true";
        }
    }
}
