package com.jb.ess.depart.controller;

import com.jb.ess.depart.service.DepartmentService;
import com.jb.ess.depart.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/admin/department")
@RequiredArgsConstructor
public class AdminDeptMemberController {
    private final EmployeeService employeeService;
    private final DepartmentService departmentService;

    // 공통된 리다이렉트 URL 생성 메소드
    private String redirectToDeptMembers(String deptCode) {
        return "redirect:/admin/department/" + deptCode + "/members";
    }

    /* 부서별 사원 목록 페이지 */
    @GetMapping("/{deptCode}/members")
    public String viewDepartmentMembers(@PathVariable String deptCode, Model model) {
        model.addAttribute("department", departmentService.getDepartmentByDeptCode(deptCode));
        model.addAttribute("members", employeeService.getEmployeesByDeptCode(deptCode));
        model.addAttribute("availableUsers", employeeService.getEmployeesWithoutDepartment());
        return "admin/department/members";
    }

    /* 사원 부서 배정 */
    @PostMapping("/{deptCode}/members/add")
    public String addEmployeeToDepartment(@PathVariable String deptCode,
                                          @RequestParam("empCode") String empCode) {
        employeeService.assignEmployeeToDepartment(empCode, deptCode);
        return redirectToDeptMembers(deptCode);
    }

    /* 부서에서 사원 제거 */
    @PostMapping("/{deptCode}/members/remove")
    public String removeEmployeeFromDepartment(@PathVariable String deptCode,
                                               @RequestParam("empCode") String empCode) {
        employeeService.removeEmployeeFromDepartment(empCode, deptCode);
        return redirectToDeptMembers(deptCode);
    }

    /* 부서장 등록 */
    @PostMapping("/{deptCode}/setLeader")
    public String setDepartmentLeader(@PathVariable String deptCode,
                                      @RequestParam String empCode) {
        employeeService.assignHeader(empCode, deptCode);
        return redirectToDeptMembers(deptCode);
    }
}
