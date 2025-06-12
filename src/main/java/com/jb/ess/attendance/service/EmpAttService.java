package com.jb.ess.attendance.service;

import com.jb.ess.common.domain.*;
import com.jb.ess.common.mapper.AttRecordMapper;
import com.jb.ess.common.mapper.AttendanceApplyMapper;
import com.jb.ess.common.mapper.EmpAttendanceMapper;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.EmpCalendarMapper;
import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.common.mapper.ShiftMasterMapper;
import com.jb.ess.common.util.DateUtil;
import com.jb.ess.common.util.WorkHoursCalculator;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmpAttService {
    private final EmpAttendanceMapper empAttendanceMapper;
    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;
    private final EmpCalendarMapper empCalendarMapper;
    private final ShiftMasterMapper shiftMasterMapper;
    private final AttRecordMapper attRecordMapper;
    private final AttendanceApplyMapper attendanceApplyMapper;

    /* 사원 정보 및 실적 리스트 */
    public List<Employee> empAttendanceList(String deptCode, String workDate, String planType) {
        if (!(planType == null || planType.isEmpty())) {
            return empAttendanceMapper.getEmpAttendanceByDeptCodeAndWorkDateAndPlanType(deptCode, workDate, planType);
        } else {
            return empAttendanceMapper.getEmpAttendanceByDeptCodeAndWorkDate(deptCode, workDate);
        }
    }

    /* 사원 정보 및 실적 */
    public List<Employee> empAttendance(String empCode, String workDate, String planType) {
        if (!(planType == null || planType.isEmpty())) {
            return empAttendanceMapper.getEmpAttendanceByEmpCodeAndWorkDateAndPlanType(empCode, workDate, planType);
        } else {
            return empAttendanceMapper.getEmpAttendanceByEmpCodeAndWorkDate(empCode, workDate);
        }
    }

    /* 로그인한 사원의 부서 정보 */
    public Department empDepartmentInfo(String empCode) {
        return departmentMapper.findByDeptCode(employeeMapper.getDeptCodeByEmpCode(empCode));
    }

    /* 로그인한 사원의 부서 + 하위 부서 목록 */
    public List<Department> childDepartmentList(String deptCode) {
        LinkedHashSet<Department> departments = new LinkedHashSet<>();
        departments.addFirst(departmentMapper.findByDeptCode(deptCode)); // 자기 자신도 포함

        getChildDepartmentsRecursive(deptCode, departments);

        return new ArrayList<>(departments);
    }

    private void getChildDepartmentsRecursive(String deptCode, Set<Department> departments) {
        List<Department> children = empAttendanceMapper.getChildDepartmentsByDeptCode(deptCode);
        for (Department child : children) {
            if (departments.add(child)) {  // Set에 새로 추가되면 true
                getChildDepartmentsRecursive(child.getDeptCode(), departments);
            }
        }
    }


    /* 주단위 근무시간 계산 */
    public Duration getWorkHoursForWeek(String empCode, LocalDate weekStart, LocalDate weekEnd) {
        Duration workHours = Duration.ZERO;

        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            String ymd = DateUtil.reverseFormatDate(date);
            AttendanceApplyGeneral overtime = attendanceApplyMapper.findApprovedOverTime(empCode, ymd);
            AttendanceApplyGeneral overtime2 = attendanceApplyMapper.findApprovedOverTime2(empCode, ymd);
            // 공휴일 스킵
            if ("Y".equals(empCalendarMapper.getHolidayYnByEmpCodeAndDate(empCode, ymd)) && overtime2 == null)
                continue;

            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, ymd);
            ShiftMaster shift;

            // 실적이 있는 경우
            if (attRecord != null && attRecord.getShiftCode() != null && !attRecord.getShiftCode()
                .isEmpty()) {
                shift = shiftMasterMapper.findShiftByCode(attRecord.getShiftCode());

                // 결근이 아닐때
                if (shift != null && !Objects.equals(shift.getShiftCode(), "00")) {
                    List<Pair<String, String>> leavePeriods = new ArrayList<>();
                    List<String> timeItemNames = attendanceApplyMapper.findApprovedTimeItemCode(empCode, ymd, "승인완료");
                    for (String timeItemName : timeItemNames) {
                        AttendanceApplyGeneral attendanceApplyGeneral = attendanceApplyMapper.findStartTimeAndEndTime(empCode, ymd, "승인완료", timeItemName);
                        leavePeriods.add(Pair.of(attendanceApplyGeneral.getStartTime(), attendanceApplyGeneral.getEndTime()));
                    }
                    workHours = workHours.plus(WorkHoursCalculator.getRealWorkTime(
                            attRecord.getCheckInTime(),
                            attRecord.getCheckOutTime(),
                            shift,
                            date,
                            leavePeriods));
                }

            // 실적이 없고 미래일 경우: EmpCalendar에서 SHIFT_CODE로 예측
            } else if (!date.isBefore(LocalDate.now())) {
                String shiftCode = empCalendarMapper.findShiftCodeByEmpCodeAndDate(empCode, ymd);
                if (shiftCode != null && !shiftCode.isEmpty()) {
                    shift = shiftMasterMapper.findShiftByCode(shiftCode);
                    if (shift != null) {
                        workHours = workHours.plus(
                            WorkHoursCalculator.getTotalWorkTime(shift)); // 예측은 총 시간 그대로 사용
                    }
                }
            }

            if (overtime != null) {
                shift = shiftMasterMapper.findShiftByCode(empCalendarMapper.findShiftCodeByEmpCodeAndDate(empCode, ymd));
                shift.setWorkOnHhmm(overtime.getStartTime());
                shift.setWorkOffHhmm(overtime.getEndTime());
                workHours = workHours.plus(WorkHoursCalculator.getTotalWorkTime(shift));
            }

            if (overtime2 != null) {
                shift = shiftMasterMapper.findShiftByCode(empCalendarMapper.findShiftCodeByEmpCodeAndDate(empCode, ymd));
                shift.setWorkOnHhmm(overtime2.getStartTime());
                shift.setWorkOffHhmm(overtime2.getEndTime());
                workHours = workHours.plus(WorkHoursCalculator.getTotalWorkTime(shift));
            }
        }

        return workHours;
    }

    public List<Employee> setAttendanceInfo(List<Employee> empList, LocalDate weekStart, LocalDate weekEnd, LocalDate workDate) {
        String workYmd = DateUtil.reverseFormatDate(workDate); // yyyyMMdd
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HHmm");

        LocalDate workLocalDate = LocalDate.parse(workYmd, dateFormatter);
        LocalDateTime nowDateTime = LocalDateTime.now();

        for (Employee emp : empList) {
            String empCode = emp.getEmpCode();

            // 1. 출퇴근 정보 조회
            AttendanceRecord att = attRecordMapper.getAttRecordByEmpCode(empCode, workYmd);
            String checkInStr = att != null && att.getCheckInTime() != null ? att.getCheckInTime() : "-";
            String checkOutStr = att != null && att.getCheckOutTime() != null ? att.getCheckOutTime() : "-";
            emp.setCheckInTime(checkInStr);
            emp.setCheckOutTime(checkOutStr);

            // 2. 근무 계획/실적 조회
            EmpCalendar cal = empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, workYmd);
            emp.setShiftCodeOrig(cal != null ? cal.getShiftCodeOrig() : null);

            if (cal == null) {
                emp.setShiftCode(null);
            } else if ("Y".equals(cal.getHolidayYn())) {
                emp.setShiftCode(cal.getShiftCode()); // 휴일인 경우 실적 유지
            } else {
                // 실근무일인 경우
                String workOn = shiftMasterMapper.findWorkOnHourByShiftCode(cal.getShiftCode());

                LocalTime parsedWorkOnTime = LocalTime.parse(workOn, timeFormatter);
                LocalDateTime workOnDateTime = LocalDateTime.of(workLocalDate, parsedWorkOnTime);

                // 출퇴근 시간 파싱
                LocalDateTime checkInDateTime = null;
                LocalDateTime checkOutDateTime = null;
                try {
                    if (!"-".equals(checkInStr)) {
                        checkInStr = checkInStr.trim();
                        if (checkInStr.length() >= 4) {
                            String checkInHHmm = checkInStr.substring(0, 4); // 앞 4자리만 자름
                            checkInDateTime = LocalDateTime.of(workLocalDate, LocalTime.parse(checkInHHmm, timeFormatter));
                        }
                    }

                    if (!"-".equals(checkOutStr)) {
                        checkOutStr = checkOutStr.trim();
                        if (checkOutStr.length() >= 4) {
                            String checkOutHHmm = checkOutStr.substring(0, 4);
                            checkOutDateTime = LocalDateTime.of(workLocalDate, LocalTime.parse(checkOutHHmm, timeFormatter));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    checkInDateTime = null;
                }


                // 출근시간 전
                if (nowDateTime.isBefore(workOnDateTime)) {
                    emp.setShiftCode(null);
                // 출근시간 후
                } else {
                    // 출퇴근 기록 없음
                    if (checkInDateTime == null && checkOutDateTime == null) {
                        if (nowDateTime.isAfter(workOnDateTime)) {
                            // 출근 지났는데 기록 없으면 결근 처리
                            emp.setShiftCode("00");
                            empCalendarMapper.updateShiftCodeByEmpCodeAndDate(empCode, workYmd, "00");
                            if (att == null) attRecordMapper.insertAttRecord(empCode, workYmd);
                        } else {
                            emp.setShiftCode(null);
                        }
                    // 출퇴근 기록 있음
                    } else {
                        // 지각
                        if (checkInDateTime != null && checkInDateTime.isAfter(workOnDateTime)) {
                            emp.setTimeItemCode("3050");
                            emp.setTimeItemNames(List.of("지각"));

                            emp.setShiftCode(emp.getShiftCodeOrig());
                            empCalendarMapper.updateShiftCodeByEmpCodeAndDate(empCode, workYmd, emp.getShiftCodeOrig());
                        }
                        emp.setShiftCode(cal.getShiftCode());
                    }
                }
            }

            // 3. 근무코드명 매핑
            emp.setShiftOrigName(shiftMasterMapper.findShiftNameByShiftCode(emp.getShiftCodeOrig()));
            emp.setShiftName(shiftMasterMapper.findShiftNameByShiftCode(emp.getShiftCode()));

            // 4. 가근태 조회
            List<String> timeItemNames = attendanceApplyMapper.findApprovedTimeItemCode(empCode, workYmd, "승인완료");
            if (!timeItemNames.isEmpty()) {
                emp.setTimeItemNames(timeItemNames); // 예: "조퇴, 외출"
            }

            // 5. 연장근무 조회
            AttendanceApplyGeneral overtime = attendanceApplyMapper.findApprovedOverTime(empCode, workYmd);
            Duration totalOverTime = Duration.ZERO;
            if (overtime != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(emp.getShiftCode());
                shift.setWorkOnHhmm(overtime.getStartTime());
                shift.setWorkOffHhmm(overtime.getEndTime());
                Duration workTime = WorkHoursCalculator.getTotalWorkTime(shift);
                emp.setOverTime(String.format("%05.2f", workTime.toMinutes() / 60.0));
            } else emp.setOverTime("-");

            // 6. 휴일근무 조회
            AttendanceApplyGeneral overtime2 = attendanceApplyMapper.findApprovedOverTime2(empCode, workYmd);
            Duration totalOverTime2 = Duration.ZERO;
            if (overtime2 != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(emp.getShiftCode());
                shift.setWorkOnHhmm(overtime2.getStartTime());
                shift.setWorkOffHhmm(overtime2.getEndTime());
                Duration workTime = WorkHoursCalculator.getTotalWorkTime(shift);
                emp.setOverTime2(String.format("%05.2f", workTime.toMinutes() / 60.0));
                emp.setShiftCode("14-1");
                emp.setShiftName("휴일근무");
            } else emp.setOverTime2("-");
            // 7. 주간 근무시간 및 잔여시간 계산
            Duration weeklyHours = getWorkHoursForWeek(empCode, weekStart, weekEnd);
            weeklyHours = weeklyHours.plus(totalOverTime);
            weeklyHours = weeklyHours.plus(totalOverTime2);
            String formattedHours = String.format("%.2f", weeklyHours.toMinutes() / 60.0);
            emp.setWorkHours(formattedHours);
            try {
                double remain = 52.00 - Double.parseDouble(formattedHours);
                emp.setRemainWorkHours(String.format("%05.2f", remain));
            } catch (NumberFormatException e) {
                emp.setRemainWorkHours("00.00");
            }
        }

        return empList;
    }
}
