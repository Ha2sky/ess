package com.jb.ess.attendance.controller;

import com.jb.ess.attendance.service.EmpAttService;
import com.jb.ess.common.domain.AttendanceRecord;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.mapper.AttRecordMapper;
import com.jb.ess.common.security.CustomUserDetails;
import com.jb.ess.common.util.DateUtil;
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
    private final AttRecordMapper attRecordMapper;

    @GetMapping("/list")
    public String attendanceList(@AuthenticationPrincipal CustomUserDetails user,
        @RequestParam(value = "empCode", required = false) String empCode,
        @RequestParam(value = "workDate", required = false) LocalDate workDate,
        @RequestParam(value = "deptCode", required = false) String deptCode,
        @RequestParam(value = "weekStart", required = false) LocalDate weekStart,
        @RequestParam(value = "weekEnd", required = false) LocalDate weekEnd,
        Model model) {

        // 날짜 기본값 설정
        if (workDate == null) workDate = LocalDate.now();
        if (weekStart == null) weekStart = workDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (weekEnd == null) weekEnd = workDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        // 부서 코드가 없으면 로그인 사용자의 부서코드 사용
        if (deptCode == null || deptCode.isEmpty()) {
            deptCode = empAttService.empDepartmentInfo(user.getUsername()).getDeptCode();
        }

        // 모델에 공통값 세팅
        model.addAttribute("workDate", workDate);
        model.addAttribute("empCode", empCode);
        model.addAttribute("deptCode", deptCode);

        // 사원 리스트 조회
        List<Employee> empList = (empCode == null || empCode.isEmpty()) ?
            empAttService.empAttendanceList(deptCode) :
            empAttService.empAttendance(empCode);

        // 날짜 포맷 캐싱
        String workYmd = DateUtil.reverseFormatDate(workDate);

        // 사원별 정보 세팅
        for (Employee emp : empList) {
            String empCodeVal = emp.getEmpCode();

            // 주간 근무시간 계산
            String workHoursStr = empAttService.getWorkHoursForWeek(empCodeVal, weekStart, weekEnd);
            emp.setWorkHours(workHoursStr);

            // 잔여시간 계산
            try {
                double workHours = Double.parseDouble(workHoursStr);
                emp.setRemainWorkHours(String.format("%05.2f", 52.00 - workHours));
            } catch (NumberFormatException e) {
                emp.setRemainWorkHours("00.00");
            }

            // 출퇴근 정보
            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCodeVal, workYmd);
            if (attRecord != null) {
                emp.setCheckInTime(attRecord.getCheckInTime());
                emp.setCheckOutTime(attRecord.getCheckOutTime());
            } else {
                emp.setCheckInTime("-");
                emp.setCheckOutTime("-");
            }
        }

        model.addAttribute("employees", empList);
        model.addAttribute("departments", empAttService.childDepartmentList(deptCode));

        return "user/attendance/list";
    }

}
