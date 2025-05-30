package com.jb.ess.attendance.service;

import com.jb.ess.common.domain.AttendanceRecord;
import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.mapper.AttRecordMapper;
import com.jb.ess.common.mapper.EmpAttendanceMapper;
import com.jb.ess.common.domain.Department;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.EmpCalendarMapper;
import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.common.mapper.ShiftMasterMapper;
import com.jb.ess.common.util.DateUtil;
import com.jb.ess.common.util.WorkHoursCalculator;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmpAttService {
    private final EmpAttendanceMapper empAttendanceMapper;
    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final EmpCalendarMapper empCalendarMapper;
    private final ShiftMasterMapper shiftMasterMapper;
    private final AttRecordMapper attRecordMapper;

    /* 사원 정보 및 실적 리스트 */
    public List<Employee> empAttendanceList(String deptCode) {
        return empAttendanceMapper.getEmpAttendanceByDeptCode(deptCode);
    }

    /* 사원 정보 및 실적 */
    public List<Employee> empAttendance(String empCode) {
        return empAttendanceMapper.getEmpAttendanceByEmpCode(empCode);
    }

    /* 로그인한 사원의 부서 정보 */
    public Department empDepartmentInfo(String empCode) {
        return departmentMapper.findByDeptCode(employeeMapper.getDeptCodeByEmpCode(empCode));
    }

    /* 로그인한 사원의 부서 + 하위 부서 목록 */
    public List<Department> childDepartmentList(String deptCode) {
        List<Department> departments = empAttendanceMapper.getChildDepartmentsByDeptCode(deptCode);
        departments.addFirst(departmentMapper.findByDeptCode(deptCode));
        return departments;
    }

    /* 주단위 근무시간 계산 */
    public String getWorkHoursForWeek(String empCode, LocalDate weekStart, LocalDate weekEnd){
        Duration workHours = Duration.ZERO;

        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            String ymd = DateUtil.reverseFormatDate(date);

            // 공휴일 스킵
            if ("Y".equals(empCalendarMapper.getHolidayYnByEmpCodeAndDate(empCode, ymd))) continue;

            // 실적기록 X
            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, ymd);
            if (attRecord == null) continue;

            String shiftCode = attRecord.getShiftCode();
            if (shiftCode == null || shiftCode.isEmpty()) continue;

            ShiftMaster shift = shiftMasterMapper.findShiftByCode(shiftCode);
            if (shift == null) continue;

            workHours = workHours.plus(WorkHoursCalculator.getRealWorkTime(
                attRecord.getCheckInTime(),
                attRecord.getCheckOutTime(),
                shift));
        }

        return String.format("%.2f", workHours.toMinutes() / 60.0);
    }
}
