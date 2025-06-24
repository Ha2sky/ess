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
                    && !("14-1".equals(shiftCode) || "00".equals(shiftCode))) {
                continue;
            }

            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, ymd);

            // 실적이 있는 경우 (휴일근무 제외)
            if (attRecord != null && attRecord.getShiftCode() != null && !attRecord.getShiftCode().isEmpty() && !attRecord.getShiftCode().equals("14-1")) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(attRecord.getShiftCode());

                // 결근 제외 (SHIFT_CODE가 00일 경우)
                if (shift != null && !"00".equals(shift.getShiftCode()) || "14-1".equals(attRecord.getAbsence())) {
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
            // 실적이 없는날인 경우 (미래) 예상근무시간에 연장근무시간 포함
            if (!overtime.isZero() && !overtime.isNegative() && attRecord == null) {
                workHours = workHours.plus(overtime);
            }
            totalOvertimeHours += overtime.toMinutes() / 60.0;

            // 휴일근무
            Duration holidayWork = getHolidayWorkHours(empCode, ymd);
            // 휴일근무 실적이 없는경우 (미래) 예상근무시간에 휴일근무시간 포함
            workHours = workHours.plus(holidayWork);
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
        ShiftMaster baseShift;
        if (!Objects.equals(shiftCode, "00")) {
            // 결근이 아닌 경우
            baseShift = shiftMasterMapper.findShiftByCode(shiftCode);
            // 결근인 경우 본래의 근태 코드를 사용 ("00" X)
        } else baseShift = shiftMasterMapper.findShiftByCode(attRecordMapper.getAbsenceByEmpCodeAndWorkDate(empCode, ymd));

        for (AttendanceApplyGeneral overtime : overtimes) {
            if (baseShift == null) continue;

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
            // 익일 처리
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
        // 익일 처리
        if (parsedWorkOn.isAfter(parsedWorkOff)) {
            shift.setWorkOffDayType("N1");
        } else {
            shift.setWorkOffDayType("N0");
        }

        holidayWorkHours = holidayWorkHours.plus(WorkHoursCalculator.getTotalWorkTime(shift));
        return holidayWorkHours;
    }

    // Model에 보낼 empList 세팅
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
                // 근무계획표가 없으면 null 처리
                emp.setShiftCode(null);
                // 휴일근무 혹은 배정받은 근무가 없고 휴일인 경우 SHIFT_CODE ("13" or "12") 유지
            } else if ("Y".equals(cal.getHolidayYn()) && (!Objects.equals(cal.getShiftCode(), "14-1") && !Objects.equals(cal.getShiftCode(), "00"))) {
                emp.setShiftCode(cal.getShiftCode());
            } else { // 근무계획표가 존재하고 근무일인 경우
                // DB에 지정된 근태코드의 출근 시간
                String workOn = shiftMasterMapper.findWorkOnHourByShiftCode(cal.getShiftCode());

                LocalTime parsedWorkOnTime = null;
                LocalDateTime workOnDateTime = null;

                // HRTWORKEMPCALENDAR 테이블의 SHIFT_CODE 가 null 인 경우 예외처리
                if (workOn != null && !workOn.isBlank()) {
                    parsedWorkOnTime = LocalTime.parse(workOn, timeFormatter);
                    workOnDateTime = LocalDateTime.of(workLocalDate, parsedWorkOnTime);
                } else {
                    log.warn("해당 근무조의 시작 시간이 존재하지 않음: shiftCode=" + cal.getShiftCode());
                }

                // HRTATTRECORD 테이블 실제 출퇴근 시간 파싱
                LocalDateTime checkInDateTime = null;
                LocalDateTime checkOutDateTime = null;
                try {
                    if (!"-".equals(checkInStr)) {
                        checkInStr = checkInStr.trim();
                        if (checkInStr.length() >= 4) {
                            // HHmmSS -> HHmm
                            String checkInHHmm = checkInStr.substring(0, 4);
                            checkInDateTime = LocalDateTime.of(workLocalDate, LocalTime.parse(checkInHHmm, timeFormatter));
                        }
                    }

                    if (!"-".equals(checkOutStr)) {
                        checkOutStr = checkOutStr.trim();
                        if (checkOutStr.length() >= 4) {
                            // HHmmSS -> HHmm
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
                    if (!Objects.equals(cal.getShiftCode(), cal.getShiftCodeOrig()) && !Objects.equals(cal.getShiftCode(), "00")) {
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
                            if (att == null) attRecordMapper.insertAttRecord(empCode, workYmd, emp.getShiftCode());
                            else {
                                if (!Objects.equals(emp.getShiftCode(), "00")) {
                                    attRecordMapper.updateAbsenceByEmpCodeAndWorkDate(empCode, workYmd, emp.getShiftCode());
                                }
                            }
                            emp.setShiftCode("00");
                            empCalendarMapper.updateShiftCodeByEmpCodeAndDate(empCode, workYmd, "00");
                        } else {
                            emp.setShiftCode(null);
                        }
                    // 출퇴근 기록 있음
                    } else {
                        // 지각 (휴일근무 X)
                        if (checkInDateTime != null) {
                            if (Objects.equals(emp.getShiftCode(), "00")) {
                                String absence = attRecordMapper.getAbsenceByEmpCodeAndWorkDate(empCode, workYmd);
                                if (absence != null) {
                                    workOn = shiftMasterMapper.findWorkOnHourByShiftCode(absence);
                                    parsedWorkOnTime = LocalTime.parse(workOn, timeFormatter);
                                    workOnDateTime = LocalDateTime.of(workLocalDate, parsedWorkOnTime);
                                }
                            }
                            checkInDateTime = Objects.requireNonNull(checkInDateTime).withNano(0);
                            workOnDateTime = Objects.requireNonNull(workOnDateTime).withNano(0);
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
