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
    public Duration getWorkHoursForWeek(String empCode, LocalDate weekStart, LocalDate weekEnd, Employee emp) {
        Duration workHours = Duration.ZERO;
        double totalOvertimeHours = 0.0;
        double totalHolidayWorkHours = 0.0;

        for (LocalDate date = weekStart; !date.isAfter(weekEnd); date = date.plusDays(1)) {
            String ymd = DateUtil.reverseFormatDate(date);

            // 공휴일 && 휴일근무X 스킵
            String shiftCode = empCalendarMapper.findShiftCodeByEmpCodeAndDate(empCode, ymd);
            if ("Y".equals(empCalendarMapper.getHolidayYnByEmpCodeAndDate(empCode, ymd))
                    && !"14-1".equals(shiftCode)) continue;

            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, ymd);

            // 실적이 있는 경우
            if (attRecord != null && attRecord.getShiftCode() != null && !attRecord.getShiftCode().isEmpty()) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(attRecord.getShiftCode());

                // 결근 제외 (SHIFT_CODE가 00일 경우)
                if (shift != null && !"00".equals(shift.getShiftCode())) {
                    List<Pair<String, String>> leavePeriods = new ArrayList<>();
                    List<String> timeItemNames = attendanceApplyMapper.findApprovedTimeItemCode(empCode, ymd, "승인완료");

                    for (String timeItemName : timeItemNames) {
                        AttendanceApplyGeneral apply = attendanceApplyMapper.findStartTimeAndEndTime(empCode, ymd, "승인완료", timeItemName);
                        if (apply != null) {
                            leavePeriods.add(Pair.of(apply.getStartTime(), apply.getEndTime()));
                        }
                    }

                    Duration realWork = WorkHoursCalculator.getRealWorkTime(
                            attRecord.getCheckInTime(),
                            attRecord.getCheckOutTime(),
                            shift,
                            date,
                            leavePeriods
                    );
                    workHours = workHours.plus(realWork);
                }

            } else if (!date.isBefore(LocalDate.now()) && shiftCode != null && !shiftCode.isEmpty() && !"14-1".equals(shiftCode)) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(shiftCode);
                if (shift != null) {
                    workHours = workHours.plus(WorkHoursCalculator.getTotalWorkTime(shift));
                }
            }

            // 연장근무
            Duration overtime = getOvertimeHours(empCode, ymd);
            if (!overtime.isZero() && !overtime.isNegative() && attRecord == null) {
                workHours = workHours.plus(overtime);
            }
            totalOvertimeHours += overtime.toMinutes() / 60.0;

            // 휴일근무
            AttendanceRecord holidayAttRecord = attRecordMapper.getHolidayAttRecordByEmpCode(empCode, ymd);
            Duration holidayWork = getHolidayWorkHours(empCode, ymd);
            if (!holidayWork.isZero() && !holidayWork.isNegative() && holidayAttRecord == null) {
                workHours = workHours.plus(holidayWork);
            }
            totalHolidayWorkHours += holidayWork.toMinutes() / 60.0;
        }

        emp.setOverTime(String.format("%.2f", totalOvertimeHours));
        emp.setHolidayWork(String.format("%.2f", totalHolidayWorkHours));

        return workHours;
    }


    /* 연장근무 */
    public Duration getOvertimeHours(String empCode, String ymd) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");
        List<AttendanceApplyGeneral> overtimes = attendanceApplyMapper.findApprovedOverTimes(empCode, ymd);
        if (overtimes == null || overtimes.isEmpty()) return Duration.ZERO;

        Duration overtimeHours = Duration.ZERO;
        String shiftCode = empCalendarMapper.findShiftCodeByEmpCodeAndDate(empCode, ymd);
        ShiftMaster baseShift = shiftMasterMapper.findShiftByCode(shiftCode);

        for (AttendanceApplyGeneral overtime : overtimes) {
            if (baseShift == null) continue;

            // 새로운 객체로 구성 (공유 객체 보호)
            ShiftMaster shift = new ShiftMaster();
            shift.setWorkOnHhmm(overtime.getStartTime());
            shift.setWorkOffHhmm(overtime.getEndTime());
            shift.setBreak1StartHhmm(baseShift.getBreak1StartHhmm());
            shift.setBreak1EndHhmm(baseShift.getBreak1EndHhmm());
            shift.setBreak2StartHhmm(baseShift.getBreak2StartHhmm());
            shift.setBreak2EndHhmm(baseShift.getBreak2EndHhmm());
            shift.setWorkOnDayType("N0");
            LocalTime parsedWorkOn = LocalTime.parse(shift.getWorkOnHhmm(), formatter);
            LocalTime parsedWorkOff = LocalTime.parse(shift.getWorkOffHhmm(), formatter);
            if (parsedWorkOn.isAfter(parsedWorkOff)) {
                shift.setWorkOffDayType("N1");
            } else {
                shift.setWorkOffDayType("N0");
            }

            overtimeHours = overtimeHours.plus(WorkHoursCalculator.getTotalWorkTime(shift));
        }

        return overtimeHours;
    }


    /* 휴일근무 */
    public Duration getHolidayWorkHours(String empCode, String ymd) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");

        AttendanceApplyGeneral holidayWork = attendanceApplyMapper.findApprovedOverTime2(empCode, ymd);
        if (holidayWork == null) return Duration.ZERO;

        Duration holidayWorkHours = Duration.ZERO;
        ShiftMaster base = shiftMasterMapper.findShiftByCode("14-1");
        if (base == null) return Duration.ZERO;

        ShiftMaster shift = new ShiftMaster();
        shift.setWorkOnHhmm(holidayWork.getStartTime());
        shift.setWorkOffHhmm(holidayWork.getEndTime());
        shift.setBreak1StartHhmm(base.getBreak1StartHhmm());
        shift.setBreak1EndHhmm(base.getBreak1EndHhmm());
        shift.setBreak2StartHhmm(base.getBreak2StartHhmm());
        shift.setBreak2EndHhmm(base.getBreak2EndHhmm());
        shift.setWorkOnDayType("N0");
        LocalTime parsedWorkOn = LocalTime.parse(shift.getWorkOnHhmm(), formatter);
        LocalTime parsedWorkOff = LocalTime.parse(shift.getWorkOffHhmm(), formatter);
        if (parsedWorkOn.isAfter(parsedWorkOff)) {
            shift.setWorkOffDayType("N1");
        } else {
            shift.setWorkOffDayType("N0");
        }

        holidayWorkHours = holidayWorkHours.plus(WorkHoursCalculator.getTotalWorkTime(shift));
        return holidayWorkHours;
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
            } else if ("Y".equals(cal.getHolidayYn()) && (!Objects.equals(cal.getShiftCode(), "14-1") && !Objects.equals(cal.getShiftCode(), "00"))) {
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
                    // 실적이 변경된 경우 (근태 신청)
                    System.out.println("[DEBUG] nowDateTime");
                    if (!Objects.equals(cal.getShiftCode(), cal.getShiftCodeOrig())) {
                        emp.setShiftCode(cal.getShiftCode());
                        emp.setTimeItemNames(List.of(shiftMasterMapper.findShiftNameByShiftCode(cal.getShiftCode())));
                    }
                    else emp.setShiftCode(null);
                // 출근시간 후
                } else {
                    // 출퇴근 기록 없음
                    if (checkInDateTime == null && checkOutDateTime == null) {
                        if (nowDateTime.isAfter(workOnDateTime)) {
                            // 출근 지났는데 기록 없으면 결근 처리
                            attRecordMapper.updateAbsenceByEmpCodeAndWorkDate(empCode, workYmd, emp.getShiftCode());
                            emp.setShiftCode("00");
                            empCalendarMapper.updateShiftCodeByEmpCodeAndDate(empCode, workYmd, "00");
                            if (att == null) attRecordMapper.insertAttRecord(empCode, workYmd);
                        } else {
                            emp.setShiftCode(null);
                        }
                    // 출퇴근 기록 있음
                    } else {
                        // 지각 (휴일근무 X)
                        System.out.println("[DEBUG] 1");
                        if (checkInDateTime != null) {
                            if (checkInDateTime.isAfter(workOnDateTime)) {
                                emp.setTimeItemCode("3050");
                                emp.setTimeItemNames(List.of("지각"));
                            }
                            emp.setShiftCode(att.getAbsence());
                            empCalendarMapper.updateShiftCodeByEmpCodeAndDate(empCode, workYmd, emp.getShiftCode());
                        }
                        else emp.setShiftCode(cal.getShiftCode());
                    }
                }
            }

            // 3. 근무코드명 매핑
            emp.setShiftOrigName(shiftMasterMapper.findShiftNameByShiftCode(emp.getShiftCodeOrig()));
            emp.setShiftName(shiftMasterMapper.findShiftNameByShiftCode(emp.getShiftCode()));

            // 4. 가근태 조회
            List<String> timeItemNames = attendanceApplyMapper.findApprovedNotOvertime(empCode, workYmd, "승인완료");
            if (!timeItemNames.isEmpty()) {
                emp.setTimeItemNames(timeItemNames); // 연장, 조출연장, 기타근태를 제외한 모든근태
            }

            // 5. 주간 근무시간 및 잔여시간 계산
            Duration weeklyHours = getWorkHoursForWeek(empCode, weekStart, weekEnd, emp);
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
