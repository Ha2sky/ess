package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.EmpAttService;
import com.jb.ess.common.domain.Department;
import com.jb.ess.common.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/user/attendance")
@RequiredArgsConstructor
public class AttendanceListController {
    private final EmpAttService empAttService;

    @GetMapping("/list")
    public String attendanceList(@AuthenticationPrincipal CustomUserDetails user,
                                 @RequestParam(value = "workDate", required = false) String date,
                                 Model model) {

        String empCode = user.getUsername();
        Department department = empAttService.empDepartmentInfo(empCode);
        model.addAttribute("employees", empAttService.empAttandanceList(department.getDeptCode()));
        model.addAttribute("departments", empAttService.childDepartmentList(department.getDeptCode()));
        return "user/attendance/list";
    }
}
