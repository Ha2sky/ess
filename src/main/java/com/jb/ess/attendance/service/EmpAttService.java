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
    public List<Employee> empAttendanceList(String deptCode, String workDate) {
        return empAttendanceMapper.getEmpAttendanceByDeptCode(deptCode, workDate);
    }

    /* 사원 정보 및 실적 */
    public List<Employee> empAttendance(String empCode, String workDate) {
        return empAttendanceMapper.getEmpAttendanceByEmpCode(empCode, workDate);
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
    public String getWorkHoursForWeek(String empCode, LocalDate weekStart, LocalDate weekEnd) {
        Duration workHours = Duration.ZERO;

        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            String ymd = DateUtil.reverseFormatDate(date);

            // 공휴일 스킵
            if ("Y".equals(empCalendarMapper.getHolidayYnByEmpCodeAndDate(empCode, ymd)))
                continue;

            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, ymd);
            ShiftMaster shift;

            if (attRecord != null && attRecord.getShiftCode() != null && !attRecord.getShiftCode()
                .isEmpty()) {
                shift = shiftMasterMapper.findShiftByCode(attRecord.getShiftCode());

                if (shift != null) {
                    workHours = workHours.plus(WorkHoursCalculator.getRealWorkTime(
                        attRecord.getCheckInTime(),
                        attRecord.getCheckOutTime(),
                        shift,
                        date));
                }
            } else if (date.isAfter(LocalDate.now())) {
                // 실적이 없고 미래일 경우: EmpCalendar에서 SHIFT_CODE로 예측
                String shiftCode = empCalendarMapper.findShiftCodeByEmpCodeAndDate(empCode, ymd);
                if (shiftCode != null && !shiftCode.isEmpty()) {
                    shift = shiftMasterMapper.findShiftByCode(shiftCode);
                    if (shift != null) {
                        workHours = workHours.plus(
                            WorkHoursCalculator.getTotalWorkTime(shift)); // 예측은 총 시간 그대로 사용
                    }
                }
            }
        }

        return String.format("%.2f", workHours.toMinutes() / 60.0);
    }

    public List<Employee> setAttendanceInfo(List<Employee> empList, LocalDate weekStart, LocalDate weekEnd, LocalDate workDate) {
        String workYmd = DateUtil.reverseFormatDate(workDate);

        for (Employee emp : empList) {
            String empCode = emp.getEmpCode();

            // 주간 근무시간 계산
            String weeklyHours = getWorkHoursForWeek(empCode, weekStart, weekEnd);
            emp.setWorkHours(weeklyHours);

            // 잔여시간 계산
            try {
                double remain = 52.00 - Double.parseDouble(weeklyHours);
                emp.setRemainWorkHours(String.format("%05.2f", remain));
            } catch (NumberFormatException e) {
                emp.setRemainWorkHours("00.00");
            }

            // 출퇴근 정보
            AttendanceRecord att = attRecordMapper.getAttRecordByEmpCode(empCode, workYmd);
            emp.setCheckInTime(att != null ? att.getCheckInTime() : "-");
            emp.setCheckOutTime(att != null ? att.getCheckOutTime() : "-");
        }

        return empList;
    }
}
