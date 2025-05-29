package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.EmpAttService;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.common.security.CustomUserDetails;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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
/* 부서근태조회 */
public class AttendanceListController {
    private final EmpAttService empAttService;

    @GetMapping("/list")
    public String attendanceList(@AuthenticationPrincipal CustomUserDetails user,
                                 @RequestParam(value = "empCode", required = false) String empCode,
                                 @RequestParam(value = "workDate", required = false) LocalDate workDate,
                                 @RequestParam(value = "deptCode", required = false) String deptCode,
                                 @RequestParam(value = "weekStart", required = false) LocalDate weekStart,
                                 @RequestParam(value = "weekEnd", required = false) LocalDate weekEnd,
                                 Model model) {
        List<Employee> empList;

        if (deptCode == null || deptCode.isEmpty()) {
            deptCode = empAttService.empDepartmentInfo(user.getUsername()).getDeptCode();
        }

        if (workDate == null) workDate = LocalDate.now();
        if (weekStart == null) weekStart = workDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (weekEnd == null) weekEnd = workDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        model.addAttribute("workDate", workDate);
        model.addAttribute("empCode", empCode);
        model.addAttribute("deptCode", deptCode);

        if (empCode == null || empCode.isEmpty()) {
            empList = empAttService.empAttendanceList(deptCode);
        } else empList = empAttService.empAttendance(empCode);
        for (Employee emp : empList) {
            String workHours = empAttService.getWorkHoursForWeek(emp.getEmpCode(), weekStart, weekEnd);
            emp.setWorkHours(workHours);
            emp.setRemainWorkHours(String.format("%05.2f", 52.00 - Double.parseDouble(workHours)));
        }

        model.addAttribute("employees", empList);
        model.addAttribute("departments", empAttService.childDepartmentList(deptCode));

        return "user/attendance/list";
    }
}
