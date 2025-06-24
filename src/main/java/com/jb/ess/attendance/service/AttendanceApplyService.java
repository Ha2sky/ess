package com.jb.ess.attendance.service;

import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.domain.Department;
import com.jb.ess.common.domain.AnnualDetail;
import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.domain.AttendanceRecord;
import com.jb.ess.common.domain.EmpCalendar;
import com.jb.ess.common.mapper.AttendanceApplyMapper;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.AnnualDetailMapper;
import com.jb.ess.common.mapper.AttRecordMapper;
import com.jb.ess.common.mapper.EmpCalendarMapper;
import com.jb.ess.common.mapper.ShiftMasterMapper;
import com.jb.ess.common.util.WorkHoursCalculator;
import com.jb.ess.attendance.service.EmpAttService;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.Duration;
import java.time.DayOfWeek;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceApplyService {
    private final AttendanceApplyMapper attendanceApplyMapper;
    private final DepartmentMapper departmentMapper;
    private final AnnualDetailMapper annualDetailMapper;
    private final AttRecordMapper attRecordMapper;
    private final EmpCalendarMapper empCalendarMapper;
    private final ShiftMasterMapper shiftMasterMapper;
    private final EmpAttService empAttService;

    // 🔥 수정: 캐시 관련 변수 제거
    // private Map<String, String> expectedHoursCache = new HashMap<>();
    // private Map<String, String> workTypeSpecificCache = new HashMap<>();

    // 현재 사용자 정보 조회
    public Employee getCurrentEmployee(String empCode) {
        try {
            Employee employee = attendanceApplyMapper.findEmployeeByEmpCode(empCode);
            log.debug("사용자 정보 조회: empCode={}, employee={}", empCode, employee);
            return employee;
        } catch (Exception e) {
            log.error("사용자 정보 조회 실패: empCode={}", empCode, e);
            throw new RuntimeException("사용자 정보 조회에 실패했습니다.", e);
        }
    }

    // 부서 정보 조회
    public Department getDepartmentInfo(String deptCode) {
        try {
            return departmentMapper.findByDeptCode(deptCode);
        } catch (Exception e) {
            log.error("부서 정보 조회 실패: deptCode={}", deptCode, e);
            return null;
        }
    }

    // 하위부서 목록 조회
    public List<Department> getSubDepartments(String parentDeptCode) {
        try {
            log.debug("하위부서 조회 시작: parentDeptCode={}", parentDeptCode);

            List<Department> allDepartments = departmentMapper.findAllDepartments();
            List<Department> subDepartments = new ArrayList<>();

            Department currentDept = departmentMapper.findByDeptCode(parentDeptCode);
            if (currentDept != null) {
                subDepartments.add(currentDept);
            }

            findAllSubDepartments(parentDeptCode, allDepartments, subDepartments);

            log.debug("하위부서 조회 완료: parentDeptCode={}, 조회된 부서 수={}", parentDeptCode, subDepartments.size());
            return subDepartments;
        } catch (Exception e) {
            log.error("하위부서 조회 실패: parentDeptCode={}", parentDeptCode, e);
            return List.of();
        }
    }

    private void findAllSubDepartments(String parentDeptCode, List<Department> allDepartments, List<Department> result) {
        for (Department dept : allDepartments) {
            if (parentDeptCode.equals(dept.getParentDept()) && !isAlreadyInResult(dept.getDeptCode(), result)) {
                result.add(dept);
                findAllSubDepartments(dept.getDeptCode(), allDepartments, result);
            }
        }
    }

    private boolean isAlreadyInResult(String deptCode, List<Department> result) {
        return result.stream().anyMatch(dept -> deptCode.equals(dept.getDeptCode()));
    }

    // 연차잔여 정보 조회
    public AnnualDetail getAnnualDetail(String empCode) {
        try {
            AnnualDetail annualDetail = annualDetailMapper.findByEmpCode(empCode);

            if (annualDetail != null) {
                log.debug("연차 조회: empCode={}, BALANCE_DAY={}, USE_DAY={}",
                        empCode, annualDetail.getBalanceDay(), annualDetail.getUseDay());
            }
            return annualDetail;
        } catch (Exception e) {
            log.error("연차잔여 조회 실패: empCode={}", empCode, e);
            return null;
        }
    }

    // 필터링된 근태 마스터 목록 조회
    public List<ShiftMaster> getFilteredShiftMasters() {
        try {
            List<String> allowedShiftNames = Arrays.asList(
                    "결근", "주간", "현장실습", "연차", "출장", "휴일", "휴무일",
                    "4전", "4후", "4야", "3전", "3후", "휴직",
                    "사외교육", "육아휴직", "산재휴직", "대체휴무일"
            );
            return attendanceApplyMapper.findShiftMastersByNames(allowedShiftNames);
        } catch (Exception e) {
            log.error("근태 마스터 조회 실패", e);
            return List.of();
        }
    }

    public String getValidTimeItemCode() {
        try {
            return attendanceApplyMapper.getValidTimeItemCode();
        } catch (Exception e) {
            log.error("TIME_ITEM_CODE 조회 실패", e);
            return null;
        }
    }

    public boolean isEmployeeAbsent(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate today = LocalDate.now();

            if (targetDate.isAfter(today)) {
                log.debug("미래 날짜는 결근 체크하지 않음: empCode={}, workDate={}", empCode, workDate);
                return false;
            }

            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, workDate);

            if (attRecord == null || attRecord.getCheckInTime() == null) {
                String originalShiftCode = getOriginalShiftCode(empCode, workDate);
                if (originalShiftCode != null) {
                    String planShiftName = shiftMasterMapper.findShiftNameByShiftCode(originalShiftCode);
                    if (!"휴무일".equals(planShiftName) && !"휴일".equals(planShiftName)) {
                        log.debug("결근 판정: empCode={}, workDate={}, plan={}", empCode, workDate, planShiftName);
                        return true;
                    }
                } else {
                    EmpCalendar empCalendar = empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, workDate);
                    if (empCalendar != null && empCalendar.getShiftCode() != null) {
                        String planShiftName = shiftMasterMapper.findShiftNameByShiftCode(empCalendar.getShiftCode());
                        if (!"휴무일".equals(planShiftName) && !"휴일".equals(planShiftName)) {
                            log.debug("결근 판정: empCode={}, workDate={}, plan={}", empCode, workDate, planShiftName);
                            return true;
                        }
                    }
                }
            }

            return false;
        } catch (Exception e) {
            log.error("결근 확인 실패: empCode={}, workDate={}", empCode, workDate, e);
            return false;
        }
    }

    public boolean validateDateRange(String startDate, String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate end = LocalDate.parse(endDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                List<EmpCalendar> calendars = empCalendarMapper.getHolidayInfoByDate(dateStr);
                for (EmpCalendar calendar : calendars) {
                    if (calendar.getShiftCode() != null) {
                        String shiftName = shiftMasterMapper.findShiftNameByShiftCode(calendar.getShiftCode());
                        if ("휴일".equals(shiftName) || "휴무일".equals(shiftName)) {
                            log.debug("날짜 범위에 휴일/휴무일 포함: {}", dateStr);
                            return false;
                        }
                    }
                }
            }

            return true;
        } catch (Exception e) {
            log.error("날짜 범위 검증 실패: startDate={}, endDate={}", startDate, endDate, e);
            return false;
        }
    }

    public Map<String, Object> getWorkInfoWithEmpCalendar(String empCode, String workDate) {
        Map<String, Object> workInfo = new HashMap<>();
        try {
            String originalShiftCode = getOriginalShiftCode(empCode, workDate);
            String empCalendarPlan = "";

            if (originalShiftCode != null) {
                empCalendarPlan = shiftMasterMapper.findShiftNameByShiftCode(originalShiftCode);
                log.debug("SHIFT_CODE_ORIG 기반 계획 조회: empCode={}, workDate={}, shiftCode={}, shiftName={}",
                        empCode, workDate, originalShiftCode, empCalendarPlan);
            } else {
                EmpCalendar empCalendar = empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, workDate);
                if (empCalendar != null && empCalendar.getShiftCode() != null) {
                    originalShiftCode = empCalendar.getShiftCode();
                    empCalendarPlan = shiftMasterMapper.findShiftNameByShiftCode(originalShiftCode);
                    log.debug("기본 SHIFT_CODE 사용: empCode={}, workDate={}, shiftCode={}, shiftName={}",
                            empCode, workDate, originalShiftCode, empCalendarPlan);
                }
            }

            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate today = LocalDate.now();
            boolean isFutureDate = targetDate.isAfter(today);

            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, workDate);
            Map<String, String> record = new HashMap<>();

            String actualShiftName = calculateActualRecord(empCode, workDate, empCalendarPlan);

            if (attRecord != null && attRecord.getCheckInTime() != null) {
                String checkInTime = attRecord.getCheckInTime();
                String checkOutTime = attRecord.getCheckOutTime() != null ? attRecord.getCheckOutTime() : "-";

                record.put("checkInTime", checkInTime);
                record.put("checkOutTime", checkOutTime);
                record.put("shiftCode", originalShiftCode);
                record.put("shiftName", actualShiftName);

                log.debug("출근 시각 존재 - 실적 동적 계산: empCode={}, date={}, plan={}, actual={}",
                        empCode, workDate, empCalendarPlan, actualShiftName);
            } else {
                if (isFutureDate) {
                    record.put("checkInTime", "-");
                    record.put("checkOutTime", "-");
                    record.put("shiftCode", originalShiftCode != null ? originalShiftCode : "");
                    record.put("shiftName", "-");
                    log.debug("미래 날짜 - 표시: empCode={}, date={}", empCode, workDate);
                } else {
                    record.put("checkInTime", "-");
                    record.put("checkOutTime", "-");
                    record.put("shiftCode", originalShiftCode != null ? originalShiftCode : "00");
                    record.put("shiftName", actualShiftName);
                    log.debug("출근 기록 없음 - 실적 동적 계산: empCode={}, date={}, actual={}",
                            empCode, workDate, actualShiftName);
                }
            }

            Map<String, String> appliedRecord = getAppliedRecord(empCode, workDate);

            // 🔥 수정: 캐시 없이 항상 실시간으로 EmpAttService 호출
            String weeklyExpectedHours = calculateWeeklyExpectedHoursFollowEmpAttService(empCode, workDate);

            workInfo.put("plan", empCalendarPlan);
            workInfo.put("empCalendarPlan", empCalendarPlan);
            workInfo.put("record", record);
            workInfo.put("appliedRecord", appliedRecord);
            workInfo.put("expectedHours", weeklyExpectedHours);

            log.debug("근무정보 조회 완료 (캐시 없이 실시간): empCode={}, workDate={}, plan={}, actual={}, weeklyHours={}",
                    empCode, workDate, empCalendarPlan, actualShiftName, weeklyExpectedHours);
        } catch (Exception e) {
            log.error("근무정보 조회 실패: empCode={}, workDate={}", empCode, workDate, e);
            workInfo.put("plan", "");
            workInfo.put("empCalendarPlan", "");
            workInfo.put("record", Map.of("checkInTime", "-", "checkOutTime", "-", "shiftCode", "00", "shiftName", "결근"));
            workInfo.put("appliedRecord", null);
            workInfo.put("expectedHours", "ERROR");
        }
        return workInfo;
    }

    // 🔥 수정: 캐시 제거하고 항상 실시간으로 EmpAttService 호출
    private String calculateWeeklyExpectedHoursFollowEmpAttService(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate mondayOfWeek = targetDate.with(DayOfWeek.MONDAY);
            LocalDate sundayOfWeek = targetDate.with(DayOfWeek.SUNDAY);

            // 🔥 수정: 캐시 관련 코드 제거
            // String cacheKey = empCode + "_" + mondayOfWeek.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "_WEEK";
            // if (expectedHoursCache.containsKey(cacheKey)) {
            //     String cachedHours = expectedHoursCache.get(cacheKey);
            //     log.debug("캐시에서 예상근로시간 반환 (주간 통일): empCode={}, workDate={}, hours={}", empCode, workDate, cachedHours);
            //     return cachedHours;
            // }

            Employee dummyEmp = new Employee();
            dummyEmp.setEmpCode(empCode);

            Duration totalWeekDuration = null;
            int retryCount = 0;
            int maxRetries = 3;

            while (retryCount < maxRetries) {
                try {
                    log.debug("EmpAttService 호출 시도 {}/{}: empCode={}", retryCount + 1, maxRetries, empCode);
                    totalWeekDuration = empAttService.getWorkHoursForWeek(empCode, mondayOfWeek, sundayOfWeek, dummyEmp);

                    if (totalWeekDuration != null) {
                        break;
                    }

                } catch (Exception e) {
                    retryCount++;
                    log.warn("EmpAttService 호출 실패 {}/{}: empCode={}, error={}",
                            retryCount, maxRetries, empCode, e.getMessage());

                    if (retryCount >= maxRetries) {
                        log.error("EmpAttService 최대 재시도 횟수 초과: empCode={}", empCode);
                        break;
                    }

                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (totalWeekDuration == null) {
                log.error("EmpAttService에서 null Duration 반환: empCode={}", empCode);
                return "ERROR";
            }

            double totalWeekHours = totalWeekDuration.toMinutes() / 60.0;
            String formattedHours = String.format("%.2f", totalWeekHours);

            // 🔥 수정: 캐시 저장 제거
            // expectedHoursCache.put(cacheKey, formattedHours);

            log.debug("EmpAttService 결과 (캐시 없이 실시간): empCode={}, workDate={}, totalHours={}", empCode, workDate, totalWeekHours);
            return formattedHours;

        } catch (Exception e) {
            log.error("EmpAttService 계산: empCode={}, workDate={}", empCode, workDate, e);
            return "ERROR";
        }
    }

    // 실적 계산에서는 승인완료된 휴일근무만 인식
    private String calculateActualRecord(String empCode, String workDate, String originalPlan) {
        try {
            // 승인된 기타근태 신청 확인
            AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(empCode, workDate);
            if (etcApply != null && "승인완료".equals(etcApply.getStatus())) {
                String shiftName = shiftMasterMapper.findShiftNameByShiftCode(etcApply.getShiftCode());
                if ("연차".equals(shiftName)) {
                    log.debug("승인된 연차 확인: empCode={}, date={}, 실적=연차", empCode, workDate);
                    return "연차";
                }
                if (shiftName != null) {
                    log.debug("승인된 기타근태 확인: empCode={}, date={}, 실적={}", empCode, workDate, shiftName);
                    return shiftName;
                }
            }

            // 실적은 승인완료된 휴일근무만 인식
            AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(empCode, workDate);
            if (generalApply != null && "휴일근무".equals(generalApply.getApplyType()) && "승인완료".equals(generalApply.getStatus())) {
                log.debug("승인된 휴일근무 확인: empCode={}, date={}, 실적=휴일근무", empCode, workDate);
                return "휴일근무";
            }

            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, workDate);
            if (attRecord != null && attRecord.getCheckInTime() != null) {
                log.debug("출근 기록 존재: empCode={}, date={}, 실적=원본계획", empCode, workDate);
                return originalPlan;
            } else {
                if ("휴무일".equals(originalPlan) || "휴일".equals(originalPlan)) {
                    log.debug("휴무일/휴일: empCode={}, date={}, 실적={}", empCode, workDate, originalPlan);
                    return originalPlan;
                } else {
                    log.debug("결근 처리: empCode={}, date={}", empCode, workDate);
                    return "결근";
                }
            }
        } catch (Exception e) {
            log.error("실적 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            return originalPlan != null ? originalPlan : "결근";
        }
    }

    private Map<String, String> getAppliedRecord(String empCode, String workDate) {
        try {
            // 휴일근로 신청 확인
            AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(empCode, workDate);
            if (generalApply != null && "승인완료".equals(generalApply.getStatus()) && "휴일근무".equals(generalApply.getApplyType())) {
                Map<String, String> appliedRecord = new HashMap<>();
                appliedRecord.put("shiftName", "휴일근무");
                appliedRecord.put("shiftCode", "14-1");
                appliedRecord.put("workHours", "8");
                return appliedRecord;
            }

            // 연차신청 확인
            AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(empCode, workDate);
            if (etcApply != null && "승인완료".equals(etcApply.getStatus())) {
                String shiftCode = etcApply.getShiftCode();
                String shiftName = shiftMasterMapper.findShiftNameByShiftCode(shiftCode);
                if (shiftName != null) {
                    Map<String, String> appliedRecord = new HashMap<>();
                    appliedRecord.put("shiftName", shiftName);
                    appliedRecord.put("shiftCode", shiftCode);
                    return appliedRecord;
                }
            }
            return null;
        } catch (Exception e) {
            log.error("신청된 실적 조회 실패: empCode={}, workDate={}", empCode, workDate, e);
            return null;
        }
    }

    private int[] parseTimeString(String timeStr) {
        try {
            if (timeStr == null || timeStr.trim().isEmpty()) {
                log.warn("빈 시간 문자열: {}", timeStr);
                return null;
            }

            timeStr = timeStr.trim();

            // HH:MM 형식 체크
            if (timeStr.contains(":")) {
                String[] parts = timeStr.split(":");
                if (parts.length >= 2) {
                    int hour = Integer.parseInt(parts[0]);
                    int minute = Integer.parseInt(parts[1]);
                    return new int[]{hour, minute};
                }
            }

            // HHMM 형식 체크 (1630, 730, 1620, 1720 등)
            if (timeStr.matches("\\d{3,4}")) {
                if (timeStr.length() == 3) {
                    // 730 -> 07:30
                    int hour = Integer.parseInt(timeStr.substring(0, 1));
                    int minute = Integer.parseInt(timeStr.substring(1));
                    return new int[]{hour, minute};
                } else if (timeStr.length() == 4) {
                    // 1630 -> 16:30, 1720 -> 17:20
                    int hour = Integer.parseInt(timeStr.substring(0, 2));
                    int minute = Integer.parseInt(timeStr.substring(2));
                    return new int[]{hour, minute};
                }
            }

            log.warn("지원되지 않는 시간 형식: {}", timeStr);
            return null;
        } catch (Exception e) {
            log.error("시간 파싱 실패: timeStr={}", timeStr, e);
            return null;
        }
    }

    private boolean validate30MinuteInterval(String startTime, String endTime, String applyType) {
        try {
            if (Arrays.asList("연장", "조출연장", "휴일근무", "조퇴", "외출", "외근").contains(applyType)) {

                int[] startParts = parseTimeString(startTime);
                int[] endParts = parseTimeString(endTime);

                if (startParts == null || endParts == null) {
                    log.warn("시간 파싱 실패: startTime={}, endTime={}", startTime, endTime);
                    return false;
                }

                int startHour = startParts[0];
                int startMin = startParts[1];
                int endHour = endParts[0];
                int endMin = endParts[1];

                int startTotalMinutes = startHour * 60 + startMin;
                int endTotalMinutes = endHour * 60 + endMin;

                if (endTotalMinutes <= startTotalMinutes) {
                    endTotalMinutes += 24 * 60;
                }

                int totalMinutes = endTotalMinutes - startTotalMinutes;

                int breakTime = calculateOverlapWithBreakTimePerfect(startTotalMinutes, endTotalMinutes);
                int netWorkMinutes = totalMinutes - breakTime;

                boolean isValid = netWorkMinutes % 30 == 0 && netWorkMinutes > 0;

                log.debug("30분 단위 검증: applyType={}, start={}({}:{}), end={}({}:{}), 전체={}분, 휴게={}분, 순수={}분, valid={}",
                        applyType, startTime, startHour, startMin, endTime, endHour, endMin, totalMinutes, breakTime, netWorkMinutes, isValid);
                return isValid;
            }
            return true;
        } catch (Exception e) {
            log.error("30분 단위 검증 실패: startTime={}, endTime={}, applyType={}", startTime, endTime, applyType, e);
            return false;
        }
    }

    // 휴게시간 계산
    private int calculateOverlapWithBreakTimePerfect(int startMinutes, int endMinutes) {
        int breakTimeMinutes = 0;

        // 오전 휴게시간 11:30~12:20 (690~740분)
        int morningBreakStart = 11 * 60 + 30; // 690분
        int morningBreakEnd = 12 * 60 + 20;   // 740분

        // 오후 휴게시간 정의 (16:20~16:50)
        int afternoonBreakStart = 16 * 60 + 20; // 980분
        int afternoonBreakEnd = 16 * 60 + 50;   // 1010분

        // 오전 휴게시간과의 겹침 계산
        if (startMinutes < morningBreakEnd && endMinutes > morningBreakStart) {
            int overlapStart = Math.max(startMinutes, morningBreakStart);
            int overlapEnd = Math.min(endMinutes, morningBreakEnd);
            int overlap = Math.max(0, overlapEnd - overlapStart);
            breakTimeMinutes += overlap;
            log.debug("오전 휴게시간 겹침: {}분 (신청: {}~{}, 휴게: {}~{}, 겹침: {}~{})",
                    overlap, startMinutes, endMinutes, morningBreakStart, morningBreakEnd, overlapStart, overlapEnd);
        }

        // 오후 휴게시간과의 겹침 계산
        if (startMinutes < afternoonBreakEnd && endMinutes > afternoonBreakStart) {
            int overlapStart = Math.max(startMinutes, afternoonBreakStart);
            int overlapEnd = Math.min(endMinutes, afternoonBreakEnd);
            int overlap = Math.max(0, overlapEnd - overlapStart);
            breakTimeMinutes += overlap;
            log.debug("오후 휴게시간 겹침: {}분 (신청: {}~{}, 휴게: {}~{}, 겹침: {}~{})",
                    overlap, startMinutes, endMinutes, afternoonBreakStart, afternoonBreakEnd, overlapStart, overlapEnd);
        }

        log.debug("총 휴게시간 겹침: {}분 - 16:20(980)~17:20(1040)의 경우 30분 휴게시간 차감", breakTimeMinutes);
        return breakTimeMinutes;
    }

    // calculateApplyHours - null 체크 강화
    private Duration calculateApplyHours(AttendanceApplyGeneral apply) {
        try {
            if (apply.getStartTime() != null && apply.getEndTime() != null &&
                    !apply.getStartTime().trim().isEmpty() && !apply.getEndTime().trim().isEmpty()) {

                String empCode = apply.getEmpCode();
                String workDate = apply.getTargetDate();

                // null 체크 강화
                if (empCode == null || empCode.trim().isEmpty() ||
                        workDate == null || workDate.trim().isEmpty()) {
                    log.warn("empCode 또는 workDate가 null/빈값: empCode={}, workDate={}", empCode, workDate);
                    return Duration.ZERO;
                }

                String originalShiftCode = getOriginalShiftCode(empCode, workDate);
                if (originalShiftCode == null) {
                    originalShiftCode = "05";
                }

                ShiftMaster shift = shiftMasterMapper.findShiftByCode(originalShiftCode);
                if ("휴일근무".equals(apply.getApplyType())) {
                    shift = shiftMasterMapper.findShiftByCode("14-1");
                }

                if (shift != null) {
                    try {
                        LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

                        String formattedStartTime = formatTimeToHHMMSS(apply.getStartTime());
                        String formattedEndTime = formatTimeToHHMMSS(apply.getEndTime());

                        List<Pair<String, String>> emptyLeavePeriods = new ArrayList<>();
                        Duration workDuration = WorkHoursCalculator.getRealWorkTime(
                                formattedStartTime, formattedEndTime, shift, targetDate, emptyLeavePeriods);

                        log.debug("신청시간 계산 성공: applyType={}, start={}, end={}, duration={}시간",
                                apply.getApplyType(), apply.getStartTime(), apply.getEndTime(), workDuration.toMinutes() / 60.0);

                        return workDuration;
                    } catch (Exception dateParseError) {
                        log.error("날짜 파싱 오류: workDate={}", workDate, dateParseError);
                        return Duration.ZERO;
                    }
                }
            }
        } catch (Exception e) {
            log.error("신청시간 계산 실패", e);
        }

        return Duration.ZERO;
    }

    private String formatTimeToHHMMSS(String timeStr) {
        try {
            int[] timeParts = parseTimeString(timeStr);
            if (timeParts != null) {
                return String.format("%02d%02d00", timeParts[0], timeParts[1]);
            }

            if (timeStr.contains(":")) {
                return timeStr.replace(":", "") + "00";
            } else if (timeStr.length() == 4) {
                return timeStr + "00";
            } else if (timeStr.length() == 3) {
                return "0" + timeStr + "00";
            }

            return timeStr + "00";
        } catch (Exception e) {
            log.error("시간 형식 변환 실패: {}", timeStr, e);
            return timeStr + "00";
        }
    }

    private Duration calculateDeductHours(AttendanceApplyEtc apply) {
        try {
            String shiftCode = apply.getShiftCode();
            if (shiftCode != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(shiftCode);
                if (shift != null) {
                    String shiftName = shift.getShiftName();
                    if ("연차".equals(shiftName)) {
                        return Duration.ofHours(8);
                    } else if ("전반차".equals(shiftName) || "후반차".equals(shiftName)) {
                        return Duration.ofHours(4);
                    } else if ("조퇴".equals(shiftName) || "외출".equals(shiftName)) {
                        return Duration.ofHours(2);
                    }
                }
            }
        } catch (Exception e) {
            log.error("차감 시간 계산 실패", e);
        }
        return Duration.ZERO;
    }

    private String getOriginalShiftCode(String empCode, String workDate) {
        try {
            return attendanceApplyMapper.getOriginalShiftCode(empCode, workDate);
        } catch (Exception e) {
            log.error("원래 계획 조회 실패: empCode={}, workDate={}", empCode, workDate, e);
            return null;
        }
    }

    public Map<String, Object> calculateRealTimeWeeklyHours(String empCode, String workDate, String startTime, String endTime, String applyType) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.debug("실시간 주 52시간 계산 시작: empCode={}, workDate={}, applyType={}", empCode, workDate, applyType);

            if (empCode == null || empCode.trim().isEmpty()) {
                log.warn("empCode가 null/빈값 - 기본값 설정: empCode={}", empCode);
                result.put("totalWeeklyHours", 40.0);
                result.put("requestHours", 0.0);
                result.put("isValid", true);
                result.put("message", "empCode 누락으로 기본값 사용");
                return result;
            }

            if (workDate == null || workDate.trim().isEmpty()) {
                log.warn("workDate가 null/빈값 - 기본값 설정: workDate={}", workDate);
                result.put("totalWeeklyHours", 40.0);
                result.put("requestHours", 0.0);
                result.put("isValid", true);
                result.put("message", "workDate 누락으로 기본값 사용");
                return result;
            }

            double baseWeeklyHours = calculateCurrentWeeklyHoursFollowEmpAttService(empCode, workDate);

            if ("휴일근무".equals(applyType)) {
                List<AttendanceApplyGeneral> existingHolidays = findAllHolidayAppliesByEmpAndDateUltraEnhanced(empCode, workDate);

                for (AttendanceApplyGeneral existingHoliday : existingHolidays) {
                    if ("휴일근무".equals(existingHoliday.getApplyType()) &&
                            ("승인완료".equals(existingHoliday.getStatus()) || "상신".equals(existingHoliday.getStatus()))) {

                        Duration existingDuration = calculateApplyHours(existingHoliday);
                        double existingHours = existingDuration.toMinutes() / 60.0;
                        baseWeeklyHours -= existingHours;

                        log.debug("기존 휴일근무 시간 차감: empCode={}, 기존상태={}, 차감시간={}, 조정된기준시간={}",
                                empCode, existingHoliday.getStatus(), existingHours, baseWeeklyHours);
                    }
                }
            }

            double requestHours = 0.0;
            if (startTime != null && endTime != null && !startTime.isEmpty() && !endTime.isEmpty()) {
                // 30분 단위 검증
                if (!validate30MinuteInterval(startTime, endTime, applyType)) {
                    result.put("totalWeeklyHours", baseWeeklyHours);
                    result.put("requestHours", 0.0);
                    result.put("isValid", false);
                    result.put("message", "연장, 휴일근무, 조퇴, 외출, 외근은 휴게시간을 제외하고 30분 단위로만 신청할 수 있습니다.");
                    return result;
                }

                // 조출연장 07:30까지 허용
                if ("조출연장".equals(applyType)) {
                    try {
                        int[] timeParts = parseTimeString(startTime);
                        if (timeParts != null) {
                            int startTimeMinutes = timeParts[0] * 60 + timeParts[1];
                            if (startTimeMinutes > 451) { // > 450 (07:30 허용)
                                result.put("totalWeeklyHours", baseWeeklyHours);
                                result.put("requestHours", 0.0);
                                result.put("isValid", false);
                                result.put("message", "조출연장은 07:30까지만 신청할 수 있습니다.");
                                return result;
                            }
                        }
                    } catch (Exception e) {
                        log.error("조출연장 시간 파싱 실패: {}", startTime, e);
                    }
                }

                requestHours = calculateRequestHours(empCode, workDate, startTime, endTime, applyType);
            }

            // 외출/반차/조퇴 시간 계산
            if (Arrays.asList("조퇴", "외근", "외출", "전반차", "후반차").contains(applyType)) {
                if ("전반차".equals(applyType) || "후반차".equals(applyType)) {
                    requestHours = -4.0;
                    log.debug("반차 4시간 차감: applyType={}", applyType);
                } else if ("조퇴".equals(applyType)) {
                    if (startTime != null && !startTime.isEmpty()) {
                        try {
                            int[] startParts = parseTimeString(startTime);
                            if (startParts != null) {
                                int startMinutes = startParts[0] * 60 + startParts[1];
                                int endMinutes = 16 * 60 + 20; // 16:20 퇴근시간

                                if (endMinutes > startMinutes) {
                                    double earlyLeaveHours = (endMinutes - startMinutes) / 60.0;
                                    requestHours = -earlyLeaveHours;
                                    log.debug("조퇴 정확한 시간 계산: {}분→{}분, 차감={}시간",
                                            startMinutes, endMinutes, earlyLeaveHours);
                                } else {
                                    requestHours = 0.0;
                                    log.debug("조퇴 시간 오류: 시작시간이 퇴근시간보다 늦음");
                                }
                            } else {
                                requestHours = 0.0;
                                log.debug("조퇴 시간 파싱 실패: 0 차감");
                            }
                        } catch (Exception e) {
                            log.error("조퇴 시간 계산 실패", e);
                            requestHours = 0.0;
                        }
                    } else {
                        requestHours = 0.0;
                        log.debug("조퇴 시간 미입력: 0 차감");
                    }
                } else if ("외출".equals(applyType) || "외근".equals(applyType)) {
                    if (startTime != null && endTime != null && !startTime.isEmpty() && !endTime.isEmpty()) {
                        try {
                            int[] startParts = parseTimeString(startTime);
                            int[] endParts = parseTimeString(endTime);

                            if (startParts != null && endParts != null) {
                                int startMinutes = startParts[0] * 60 + startParts[1];
                                int endMinutes = endParts[0] * 60 + endParts[1];

                                if (endMinutes > startMinutes) {
                                    double outingHours = (endMinutes - startMinutes) / 60.0;
                                    requestHours = -outingHours;
                                    log.debug("외출/외근 정확한 시간 계산: {}분→{}분, 차감={}시간",
                                            startMinutes, endMinutes, outingHours);
                                } else if (endMinutes < startMinutes) {
                                    // 자정 넘어가는 경우 처리
                                    int nextDayEndMinutes = endMinutes + 24 * 60;
                                    double outingHours = (nextDayEndMinutes - startMinutes) / 60.0;
                                    requestHours = -outingHours;
                                    log.debug("외출/외근 자정넘김 계산: {}분→{}분(+24시간), 차감={}시간",
                                            startMinutes, nextDayEndMinutes, outingHours);
                                } else {
                                    requestHours = 0.0; // 시작시간 = 종료시간
                                    log.debug("외출/외근 시간 동일: 0 차감");
                                }
                            } else {
                                requestHours = 0.0;
                                log.debug("외출/외근 시간 파싱 실패: 0 차감");
                            }
                        } catch (Exception e) {
                            log.error("외출/외근 시간 계산 실패", e);
                            requestHours = 0.0;
                        }
                    } else {
                        requestHours = 0.0;
                        log.debug("외출/외근 시간 미입력: 0 차감");
                    }
                }
            }

            double totalWeeklyHours = baseWeeklyHours + requestHours;
            boolean isValid = totalWeeklyHours <= 52.0 && totalWeeklyHours >= 0;

            result.put("totalWeeklyHours", totalWeeklyHours);
            result.put("requestHours", Math.abs(requestHours));
            result.put("isValid", isValid);
            result.put("message", isValid ? "정상" : (totalWeeklyHours > 52.0 ? "주 52시간 초과" : "음수 시간"));

            log.debug("실시간 주 52시간 계산 완료 (캐시 없이): baseHours={}, requestHours={}, totalHours={}, isValid={}",
                    baseWeeklyHours, requestHours, totalWeeklyHours, isValid);

        } catch (Exception e) {
            log.error("실시간 주 52시간 계산 실패", e);
            result.put("totalWeeklyHours", 40.0);
            result.put("requestHours", 0.0);
            result.put("isValid", true);
            result.put("message", "계산 오류 - 기본값 사용");
        }

        return result;
    }

    // 주간 통일 계산 (캐시 없이)
    private double calculateCurrentWeeklyHoursFollowEmpAttService(String empCode, String workDate) {
        try {
            String weeklyHours = calculateWeeklyExpectedHoursFollowEmpAttService(empCode, workDate);

            if ("ERROR".equals(weeklyHours)) {
                log.error("EmpAttService 계산 오류로 인한 기본값 사용: empCode={}", empCode);
                return 0.0;
            }

            return Double.parseDouble(weeklyHours);
        } catch (Exception e) {
            log.error("현재 주간 근무시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            return 0.0;
        }
    }

    private double calculateRequestHours(String empCode, String workDate, String startTime, String endTime, String applyType) {
        try {
            String originalShiftCode = getOriginalShiftCode(empCode, workDate);
            if (originalShiftCode == null) {
                originalShiftCode = "05";
            }

            ShiftMaster shift = shiftMasterMapper.findShiftByCode(originalShiftCode);
            if (shift != null && "휴일근무".equals(applyType)) {
                shift = shiftMasterMapper.findShiftByCode("14-1");
            }

            if (shift != null) {
                LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

                String formattedStartTime = formatTimeToHHMMSS(startTime);
                String formattedEndTime = formatTimeToHHMMSS(endTime);

                List<Pair<String, String>> emptyLeavePeriods = new ArrayList<>();
                Duration workDuration = WorkHoursCalculator.getRealWorkTime(
                        formattedStartTime, formattedEndTime, shift, targetDate, emptyLeavePeriods);

                return workDuration.toMinutes() / 60.0;
            }

            return 0.0;
        } catch (Exception e) {
            log.error("신청 시간 정확 계산 실패", e);
            return 0.0;
        }
    }

    public AttendanceApplyGeneral findGeneralApplyByEmpAndDate(String empCode, String workDate) {
        return attendanceApplyMapper.findGeneralApplyByEmpAndDate(empCode, workDate);
    }

    public AttendanceApplyEtc findEtcApplyByEmpAndDate(String empCode, String workDate) {
        return attendanceApplyMapper.findEtcApplyByEmpAndDate(empCode, workDate);
    }

    // 부서별 사원 조회 (부서장용)
    public List<Employee> getEmployeesByDept(String deptCode, String workDate, String workPlan, String sortBy) {
        try {
            log.debug("부서별 사원 조회 시작: deptCode={}, workDate={}, workPlan={}, sortBy={}", deptCode, workDate, workPlan, sortBy);

            List<Employee> employees = attendanceApplyMapper.findEmployeesByDeptWithSort(deptCode, workDate, workPlan, sortBy);

            log.debug("조회된 사원 수: {}", employees.size());

            for (Employee emp : employees) {
                AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(emp.getEmpCode(), workDate);
                if (generalApply != null) {
                    emp.setApplyGeneralNo(generalApply.getApplyGeneralNo());
                    emp.setGeneralApplyStatus(generalApply.getStatus());
                }

                AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(emp.getEmpCode(), workDate);
                if (etcApply != null) {
                    emp.setApplyEtcNo(etcApply.getApplyEtcNo());
                    emp.setEtcApplyStatus(etcApply.getStatus());
                }

                AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(emp.getEmpCode(), workDate);
                if (attRecord != null && attRecord.getCheckInTime() != null) {
                    emp.setCheckInTime(attRecord.getCheckInTime());
                    emp.setCheckOutTime(attRecord.getCheckOutTime() != null ? attRecord.getCheckOutTime() : "-");
                } else {
                    emp.setCheckInTime("-");
                    emp.setCheckOutTime("-");
                }
            }

            return employees;
        } catch (Exception e) {
            log.error("부서별 사원 조회 실패: deptCode={}, workDate={}", deptCode, workDate, e);
            throw new RuntimeException("부서별 사원 조회에 실패했습니다.", e);
        }
    }

    public List<Employee> getEmployeesByDeptWithApplyType(String deptCode, String workDate, String workPlan, String sortBy, String applyTypeCategory) {
        try {
            log.debug("부서별 사원 조회 (근태신청종류별) 시작: deptCode={}, workDate={}, applyTypeCategory={}", deptCode, workDate, applyTypeCategory);

            List<Employee> employees = attendanceApplyMapper.findEmployeesByDeptWithSort(deptCode, workDate, workPlan, sortBy);

            log.debug("조회된 사원 수: {}", employees.size());

            for (Employee emp : employees) {
                AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDateWithCategory(emp.getEmpCode(), workDate, applyTypeCategory);
                if (generalApply != null && !"삭제".equals(generalApply.getStatus())) {
                    emp.setApplyGeneralNo(generalApply.getApplyGeneralNo());
                    emp.setGeneralApplyStatus(generalApply.getStatus());
                    log.debug("신청근무별 조회: empCode={}, applyType={}, status={}",
                            emp.getEmpCode(), generalApply.getApplyType(), generalApply.getStatus());
                } else {
                    emp.setApplyGeneralNo("");
                    emp.setGeneralApplyStatus("대기");
                    log.debug("신청근무별 조회 - 기존 신청 없음: empCode={}, category={}", emp.getEmpCode(), applyTypeCategory);
                }

                AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(emp.getEmpCode(), workDate);
                if (etcApply != null) {
                    emp.setApplyEtcNo(etcApply.getApplyEtcNo());
                    emp.setEtcApplyStatus(etcApply.getStatus());
                }

                AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(emp.getEmpCode(), workDate);
                if (attRecord != null && attRecord.getCheckInTime() != null) {
                    emp.setCheckInTime(attRecord.getCheckInTime());
                    emp.setCheckOutTime(attRecord.getCheckOutTime() != null ? attRecord.getCheckOutTime() : "-");
                } else {
                    emp.setCheckInTime("-");
                    emp.setCheckOutTime("-");
                }
            }

            return employees;
        } catch (Exception e) {
            log.error("부서별 사원 조회 (근태신청종류별) 실패: deptCode={}, workDate={}", deptCode, workDate, e);
            throw new RuntimeException("부서별 사원 조회에 실패했습니다.", e);
        }
    }

    // 현재 사원만 조회 (일반 사원용)
    public List<Employee> getCurrentEmployeeList(String empCode, String workDate) {
        try {
            List<Employee> employees = attendanceApplyMapper.findCurrentEmployeeWithCalendar(empCode, workDate);

            for (Employee emp : employees) {
                AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(emp.getEmpCode(), workDate);
                if (generalApply != null) {
                    emp.setApplyGeneralNo(generalApply.getApplyGeneralNo());
                    emp.setGeneralApplyStatus(generalApply.getStatus());
                }

                AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(emp.getEmpCode(), workDate);
                if (etcApply != null) {
                    emp.setApplyEtcNo(etcApply.getApplyEtcNo());
                    emp.setEtcApplyStatus(etcApply.getStatus());
                }

                AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(emp.getEmpCode(), workDate);
                if (attRecord != null && attRecord.getCheckInTime() != null) {
                    emp.setCheckInTime(attRecord.getCheckInTime());
                    emp.setCheckOutTime(attRecord.getCheckOutTime() != null ? attRecord.getCheckOutTime() : "-");
                } else {
                    emp.setCheckInTime("-");
                    emp.setCheckOutTime("-");
                }
            }

            return employees;
        } catch (Exception e) {
            log.error("현재 사원 조회 실패: empCode={}, workDate={}", empCode, workDate, e);
            throw new RuntimeException("사원 조회에 실패했습니다.", e);
        }
    }

    // 현재 사원만 조회 (근태신청종류별)
    public List<Employee> getCurrentEmployeeListWithApplyType(String empCode, String workDate, String applyTypeCategory) {
        try {
            List<Employee> employees = attendanceApplyMapper.findCurrentEmployeeWithCalendar(empCode, workDate);

            for (Employee emp : employees) {
                AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDateWithCategory(emp.getEmpCode(), workDate, applyTypeCategory);
                if (generalApply != null) {
                    emp.setApplyGeneralNo(generalApply.getApplyGeneralNo());
                    emp.setGeneralApplyStatus(generalApply.getStatus());
                } else {
                    emp.setApplyGeneralNo("");
                    emp.setGeneralApplyStatus("대기");
                }

                AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(emp.getEmpCode(), workDate);
                if (etcApply != null) {
                    emp.setApplyEtcNo(etcApply.getApplyEtcNo());
                    emp.setEtcApplyStatus(etcApply.getStatus());
                }

                AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(emp.getEmpCode(), workDate);
                if (attRecord != null && attRecord.getCheckInTime() != null) {
                    emp.setCheckInTime(attRecord.getCheckInTime());
                    emp.setCheckOutTime(attRecord.getCheckOutTime() != null ? attRecord.getCheckOutTime() : "-");
                } else {
                    emp.setCheckInTime("-");
                    emp.setCheckOutTime("-");
                }
            }

            return employees;
        } catch (Exception e) {
            log.error("현재 사원 조회 (근태신청종류별) 실패: empCode={}, workDate={}", empCode, workDate, e);
            throw new RuntimeException("사원 조회에 실패했습니다.", e);
        }
    }

    public String validateGeneralApply(AttendanceApplyGeneral apply) {
        try {
            String empCode = apply.getEmpCode();
            String targetDate = apply.getTargetDate();
            String applyType = apply.getApplyType();

            // 30분 단위 검증
            if (apply.getStartTime() != null && apply.getEndTime() != null &&
                    !apply.getStartTime().trim().isEmpty() && !apply.getEndTime().trim().isEmpty()) {
                if (!validate30MinuteInterval(apply.getStartTime(), apply.getEndTime(), applyType)) {
                    return "연장, 휴일근무, 조퇴, 외출, 외근은 휴게시간을 제외하고 30분 단위로만 신청할 수 있습니다.";
                }
            }

            // 해당 일자의 계획 및 실적 확인
            String originalShiftCode = getOriginalShiftCode(empCode, targetDate);
            String planShiftName = "";
            if (originalShiftCode != null) {
                planShiftName = shiftMasterMapper.findShiftNameByShiftCode(originalShiftCode);
            } else {
                EmpCalendar empCalendar = empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, targetDate);
                if (empCalendar != null && empCalendar.getShiftCode() != null) {
                    planShiftName = shiftMasterMapper.findShiftNameByShiftCode(empCalendar.getShiftCode());
                }
            }

            // 실적 확인
            String actualRecord = calculateActualRecord(empCode, targetDate, planShiftName);

            if ("연장".equals(applyType) || "조출연장".equals(applyType)) {
                // 실적이 결근일 경우 신청 불가
                if ("결근".equals(actualRecord)) {
                    return "실적이 결근일 경우 연장근무를 신청할 수 없습니다.";
                }

                if ("휴일근무".equals(actualRecord)) {
                    String currentShiftCode = getOriginalShiftCode(empCode, targetDate);
                    if ("14-1".equals(currentShiftCode)) {
                        log.debug("SHIFT_CODE 14-1 확인됨 - 휴일근무 승인완료: empCode={}", empCode);
                        return "valid"; // 즉시 통과
                    }
                }

                List<AttendanceApplyGeneral> holidayApplies = findHolidayWorkAppliesCompletely(empCode, targetDate);
                AttendanceApplyGeneral validHolidayApply = null;

                for (AttendanceApplyGeneral holidayApply : holidayApplies) {
                    if ("휴일근무".equals(holidayApply.getApplyType()) &&
                            ("승인완료".equals(holidayApply.getStatus()) || "상신".equals(holidayApply.getStatus()) || "저장".equals(holidayApply.getStatus()))) {
                        validHolidayApply = holidayApply;
                        log.debug("신청 테이블에서 휴일근무 발견: empCode={}, status={}, applyNo={}",
                                empCode, holidayApply.getStatus(), holidayApply.getApplyGeneralNo());
                        break;
                    }
                }

                if (validHolidayApply != null) {
                    if (validHolidayApply.getStartTime() != null && validHolidayApply.getEndTime() != null &&
                            !validHolidayApply.getStartTime().trim().isEmpty() && !validHolidayApply.getEndTime().trim().isEmpty()) {
                        try {
                            int[] startParts = parseTimeString(validHolidayApply.getStartTime());
                            int[] endParts = parseTimeString(validHolidayApply.getEndTime());

                            if (startParts == null || endParts == null) {
                                return "휴일근무 시간 형식이 올바르지 않습니다.";
                            }

                            int startTotalMinutes = startParts[0] * 60 + startParts[1];
                            int endTotalMinutes = endParts[0] * 60 + endParts[1];

                            if (endTotalMinutes <= startTotalMinutes) {
                                endTotalMinutes += 24 * 60;
                            }

                            int totalMinutes = endTotalMinutes - startTotalMinutes;
                            int breakTime = calculateOverlapWithBreakTimePerfect(startTotalMinutes, endTotalMinutes);
                            int netWorkMinutes = totalMinutes - breakTime;

                            if (netWorkMinutes < 480) {
                                return "휴일근무 순수 8시간 이상 신청한 경우에만 연장근무를 신청할 수 있습니다. (현재: " + String.format("%.1f", netWorkMinutes/60.0) + "시간)";
                            }

                            log.debug("휴일근무 8시간 검증: empCode={}, netWorkMinutes={}분", empCode, netWorkMinutes);
                            return "valid"; // 검증 통과
                        } catch (Exception e) {
                            log.warn("휴일근무 시간 파싱 실패: startTime={}, endTime={}", validHolidayApply.getStartTime(), validHolidayApply.getEndTime(), e);
                            return "휴일근무 시간 정보가 올바르지 않습니다.";
                        }
                    } else {
                        return "휴일근무 시간 정보가 없습니다.";
                    }
                } else {
                    // 3단계: 실적은 휴일근무인데 신청이 없는 경우만 오류
                    if ("휴일근무".equals(actualRecord)) {
                        log.warn("실적은 휴일근무인데 신청 테이블에서 찾을 수 없음: empCode={}, targetDate={}", empCode, targetDate);
                        return "동일 날짜에 휴일근무 신청이 없습니다.";
                    }
                }

                // 해당 일에 연차, 휴가, 반차, 조퇴 신청이 있는지 확인
                if ("연장".equals(applyType)) {
                    boolean hasAnnualOrVacation = attendanceApplyMapper.hasAnnualOrVacationApply(empCode, targetDate);
                    boolean hasHalfDayOrEarlyLeave = attendanceApplyMapper.hasHalfDayOrEarlyLeaveApply(empCode, targetDate);

                    if (hasAnnualOrVacation || hasHalfDayOrEarlyLeave) {
                        return "해당일에 연차, 휴가, 반차, 조퇴 신청이 있어 일반 연장근무를 신청할 수 없습니다.";
                    }
                } else if ("조출연장".equals(applyType)) {
                    boolean hasAnnualOrVacation = attendanceApplyMapper.hasAnnualOrVacationApply(empCode, targetDate);

                    if (hasAnnualOrVacation) {
                        return "해당일에 연차, 휴가 신청이 있어 조출연장을 신청할 수 없습니다.";
                    }
                }
            }

            // 휴일근로 검증
            if ("휴일근무".equals(applyType)) {
                if (!"휴일".equals(planShiftName) && !"휴무일".equals(planShiftName)) {
                    return "휴일근로는 휴일 또는 휴무일에만 신청할 수 있습니다.";
                }

                if ("연차".equals(actualRecord) || "휴가".equals(actualRecord) || "결근".equals(actualRecord)) {
                    return "연차, 휴가, 결근 등의 날에는 휴일근로를 신청할 수 없습니다.";
                }
            }

            // 조퇴/외출/반차 검증 (일반근태)
            if (Arrays.asList("조퇴", "외근", "외출", "전반차", "후반차").contains(applyType)) {
                if (Arrays.asList("결근", "연차", "휴가", "휴일", "휴직").contains(actualRecord)) {
                    return "정상 근무가 아닌 경우에는 " + applyType + "을(를) 신청할 수 없습니다.";
                }

                if (apply.getStartTime() != null && apply.getEndTime() != null) {
                    boolean hasTimeOverlap = attendanceApplyMapper.hasTimeOverlap(
                            empCode, targetDate, apply.getStartTime(), apply.getEndTime());
                    if (hasTimeOverlap) {
                        return "해당일 해당시간에 중복되는 근태 신청이 있습니다.";
                    }
                }
            }

            // 시간 검증 - 안전한 파싱
            if (apply.getStartTime() != null && apply.getEndTime() != null &&
                    !apply.getStartTime().trim().isEmpty() && !apply.getEndTime().trim().isEmpty()) {
                try {
                    int[] startParts = parseTimeString(apply.getStartTime());
                    int[] endParts = parseTimeString(apply.getEndTime());

                    if (startParts == null || endParts == null) {
                        return "시간 형식이 올바르지 않습니다.";
                    }

                    int startTimeMinutes = startParts[0] * 60 + startParts[1];
                    int endTimeMinutes = endParts[0] * 60 + endParts[1];

                    if (startTimeMinutes >= endTimeMinutes) {
                        return "시작시간이 종료시간보다 늦을 수 없습니다.";
                    }

                    if ("조출연장".equals(applyType)) {
                        if (startTimeMinutes > 451 || endTimeMinutes > 451) {
                            return "조출연장은 07:30까지만 신청할 수 있습니다.";
                        }
                    }

                    // 정상근무시간 연장 신청 제한 검증
                    if ("연장".equals(applyType)) {
                        if (startTimeMinutes < 980) { // 980분 = 16:20
                            return "정상근무시간(16:20) 이후에만 연장근무를 신청할 수 있습니다.";
                        }
                    }
                } catch (Exception e) {
                    log.warn("시간 검증 중 오류: startTime={}, endTime={}", apply.getStartTime(), apply.getEndTime(), e);
                    return "시간 형식이 올바르지 않습니다.";
                }
            }

            // 주 52시간 초과 검증 - 주간 통일 계산 (캐시 없이)
            if (!Arrays.asList("조퇴", "외근", "외출", "전반차", "후반차").contains(applyType)) {
                try {
                    double currentWeekHours = calculateCurrentWeeklyHoursFollowEmpAttService(empCode, targetDate);

                    Duration applyHours = calculateApplyHours(apply);
                    double applyHoursDecimal = applyHours.toMinutes() / 60.0;

                    if (currentWeekHours + applyHoursDecimal > 52.0) {
                        return "주 52시간을 초과할 수 없습니다. (현재: " + String.format("%.2f", currentWeekHours) + "시간)";
                    }
                } catch (Exception e) {
                    log.error("주 52시간 검증 중 EmpAttService 오류: empCode={}, targetDate={}", empCode, targetDate, e);
                    return "예상근로시간 계산 오류로 신청할 수 없습니다.";
                }
            }

            // 중복 신청 검증
            boolean hasDuplicate = attendanceApplyMapper.checkDuplicateGeneralApply(
                    apply.getEmpCode(), apply.getTargetDate(), apply.getApplyType());
            if (hasDuplicate) {
                return "해당 일자에 동일한 신청이 이미 존재합니다.";
            }

            return "valid";
        } catch (Exception e) {
            log.error("일반근태 유효성 검증 실패", e);
            return "유효성 검증 중 오류가 발생했습니다.";
        }
    }

    private List<AttendanceApplyGeneral> findHolidayWorkAppliesCompletely(String empCode, String workDate) {
        try {
            List<AttendanceApplyGeneral> applies = new ArrayList<>();

            AttendanceApplyGeneral basicApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(empCode, workDate);
            if (basicApply != null && "휴일근무".equals(basicApply.getApplyType()) &&
                    !"삭제".equals(basicApply.getStatus()) && !"취소".equals(basicApply.getStatus())) {
                applies.add(basicApply);
                log.debug("기본 휴일근무 발견: empCode={}, status={}, applyNo={}",
                        empCode, basicApply.getStatus(), basicApply.getApplyGeneralNo());
            }

            try {
                AttendanceApplyGeneral typeApply = attendanceApplyMapper.findGeneralApplyByEmpAndDateAndType(empCode, workDate, "휴일근무");
                if (typeApply != null &&
                        !"삭제".equals(typeApply.getStatus()) && !"취소".equals(typeApply.getStatus()) &&
                        !applies.stream().anyMatch(existing -> existing.getApplyGeneralNo().equals(typeApply.getApplyGeneralNo()))) {
                    applies.add(typeApply);
                    log.debug("타입별 휴일근무 발견: empCode={}, status={}, applyNo={}",
                            empCode, typeApply.getStatus(), typeApply.getApplyGeneralNo());
                }
            } catch (Exception e) {
                log.debug("타입별 조회 실패: {}", e.getMessage());
            }

            log.debug("휴일근무 조회 완료 (기존 메서드만 사용): empCode={}, workDate={}, 총 {}건", empCode, workDate, applies.size());
            return applies;

        } catch (Exception e) {
            log.error("휴일근무 조회 실패: empCode={}, workDate={}", empCode, workDate, e);
            return List.of();
        }
    }

    // 휴일근무 신청 찾기 (캐시 없이)
    private List<AttendanceApplyGeneral> findAllHolidayAppliesByEmpAndDateUltraEnhanced(String empCode, String workDate) {
        return findHolidayWorkAppliesCompletely(empCode, workDate);
    }

    // 기타근태 신청 유효성 검증
    public String validateEtcApply(AttendanceApplyEtc apply) {
        try {
            int startDate = Integer.parseInt(apply.getTargetStartDate());
            int endDate = Integer.parseInt(apply.getTargetEndDate());

            if (startDate > endDate) {
                return "시작일이 종료일보다 늦을 수 없습니다.";
            }

            if (apply.getShiftCode() != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(apply.getShiftCode());
                if (shift != null && !"연차".equals(shift.getShiftName())) {
                    if (!validateDateRange(apply.getTargetStartDate(), apply.getTargetEndDate())) {
                        return "신청 기간에 휴일/휴무일이 포함되어 있습니다.";
                    }
                }
            }

            boolean hasDuplicate = attendanceApplyMapper.checkDuplicateEtcApply(
                    apply.getEmpCode(), apply.getTargetStartDate(), apply.getTargetEndDate());
            if (hasDuplicate) {
                return "해당 기간에 중복된 신청이 존재합니다.";
            }

            return "valid";
        } catch (Exception e) {
            log.error("기타근태 유효성 검증 실패", e);
            return "유효성 검증 중 오류가 발생했습니다.";
        }
    }

    // 일반근태 신청 저장 (캐시 없이)
    @Transactional
    public void saveGeneralApply(AttendanceApplyGeneral apply) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String applyNo = "GEN" + timestamp;
            apply.setApplyGeneralNo(applyNo);

            Employee targetEmp = attendanceApplyMapper.findEmployeeByEmpCode(apply.getEmpCode());
            apply.setDeptCode(targetEmp.getDeptCode());

            log.debug("일반근태 저장: applyNo={}, empCode={}, timeItemCode={}",
                    applyNo, apply.getEmpCode(), apply.getTimeItemCode());
            attendanceApplyMapper.insertGeneralApply(apply);

            // 🔥 수정: 캐시 관련 코드 제거 (메서드 호출은 유지하되 빈 동작)
            if ("휴일근무".equals(apply.getApplyType())) {
                clearWeeklyExpectedHoursCache(apply.getEmpCode(), apply.getTargetDate());
            }
        } catch (Exception e) {
            log.error("일반근태 저장 실패", e);
            throw new RuntimeException("일반근태 저장에 실패했습니다.", e);
        }
    }

    // 기타근태 신청 저장
    @Transactional
    public void saveEtcApply(AttendanceApplyEtc apply) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String applyNo = "ETC" + timestamp;
            apply.setApplyEtcNo(applyNo);

            Employee targetEmp = attendanceApplyMapper.findEmployeeByEmpCode(apply.getEmpCode());
            apply.setDeptCode(targetEmp.getDeptCode());

            log.debug("기타근태 저장: applyNo={}, empCode={}", applyNo, apply.getEmpCode());
            attendanceApplyMapper.insertEtcApply(apply);
        } catch (Exception e) {
            log.error("기타근태 저장 실패", e);
            throw new RuntimeException("기타근태 저장에 실패했습니다.", e);
        }
    }

    // 🔥 수정: 캐시 초기화 메서드를 빈 메서드로 유지 (호출하는 곳이 많으므로)
    private void clearWeeklyExpectedHoursCache(String empCode, String workDate) {
        // 캐시 관련 로직 제거 - 빈 메서드로 유지하여 기존 호출 코드 보호
        log.debug("캐시 초기화 메서드 호출 (캐시 없음): empCode={}, workDate={}", empCode, workDate);
    }

    // 저장된 일반근태 신청 조회
    public AttendanceApplyGeneral getSavedGeneralApply(String applyGeneralNo) {
        try {
            return attendanceApplyMapper.findGeneralApplyByNo(applyGeneralNo);
        } catch (Exception e) {
            log.error("저장된 일반근태 신청 조회 실패: applyGeneralNo={}", applyGeneralNo, e);
            return null;
        }
    }

    // 저장된 기타근태 신청 조회
    public AttendanceApplyEtc getSavedEtcApply(String applyEtcNo) {
        try {
            return attendanceApplyMapper.findEtcApplyByNo(applyEtcNo);
        } catch (Exception e) {
            log.error("저장된 기타근태 신청 조회 실패: applyEtcNo={}", applyEtcNo, e);
            return null;
        }
    }

    // 휴일근무 승인완료 시 SHIFT_CODE 업데이트 (캐시 없이)
    @Transactional
    public void updateWorkRecordForHolidayWork(String empCode, String workDate) {
        try {
            log.debug("휴일근로 실적 업데이트 시작: empCode={}, workDate={}", empCode, workDate);

            attendanceApplyMapper.updateShiftCodeAfterGeneralApproval(empCode, workDate, "휴일근무");

            // 🔥 수정: 캐시 초기화 호출 제거하지 않고 유지 (빈 동작)
            clearWeeklyExpectedHoursCache(empCode, workDate);

            log.debug("휴일근로 SHIFT_CODE 업데이트 완료: empCode={}, workDate={}, shiftCode=14-1", empCode, workDate);
        } catch (Exception e) {
            log.error("휴일근로 실적 업데이트 실패: empCode={}, workDate={}", empCode, workDate, e);
        }
    }

    // 기타근태 승인완료 시 SHIFT_CODE 업데이트
    @Transactional
    public void updateWorkRecordForAnnualLeave(String empCode, String workDate, String shiftCode) {
        try {
            log.debug("연차/반차 실적 업데이트 시작: empCode={}, workDate={}, shiftCode={}", empCode, workDate, shiftCode);

            ShiftMaster shift = shiftMasterMapper.findShiftByCode(shiftCode);
            if (shift != null) {
                attendanceApplyMapper.updateShiftCodeAfterEtcApproval(empCode, workDate, workDate, shiftCode);
            }

            log.debug("연차/반차 SHIFT_CODE 업데이트 완료: empCode={}, workDate={}, shiftCode={}", empCode, workDate, shiftCode);
        } catch (Exception e) {
            log.error("연차/반차 실적 업데이트 실패: empCode={}, workDate={}, shiftCode={}", empCode, workDate, shiftCode, e);
        }
    }

    // 신청근무별 분리 조회
    public Map<String, Object> getApplyByWorkType(String empCode, String workDate, String applyType) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.debug("신청근무별 조회: empCode={}, workDate={}, applyType={}", empCode, workDate, applyType);

            AttendanceApplyGeneral existingApply = attendanceApplyMapper.findGeneralApplyByEmpAndDateAndType(empCode, workDate, applyType);

            if (existingApply != null && !"삭제".equals(existingApply.getStatus())) {
                result.put("hasExisting", true);
                result.put("applyType", "general");
                result.put("applyNo", existingApply.getApplyGeneralNo());
                result.put("status", existingApply.getStatus());
                result.put("startTime", existingApply.getStartTime());
                result.put("endTime", existingApply.getEndTime());
                result.put("reason", existingApply.getReason());

                log.debug("기존 신청: applyNo={}, status={}, applyType={}",
                        existingApply.getApplyGeneralNo(), existingApply.getStatus(), applyType);
            } else {
                result.put("hasExisting", false);
                result.put("applyType", "general");
                result.put("status", "대기");
                result.put("startTime", "");
                result.put("endTime", "");
                result.put("reason", "");

                log.debug("기존 신청 없음 - 신청 가능한 상태: applyType={}", applyType);
            }

            return result;
        } catch (Exception e) {
            log.error("신청근무별 기존 신청 조회 실패: empCode={}, workDate={}, applyType={}", empCode, workDate, applyType, e);
            result.put("hasExisting", false);
            result.put("message", "조회 중 오류가 발생했습니다.");
            return result;
        }
    }

    public Map<String, Object> validateHalfDayTimeInput(String applyType) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean timeInputDisabled = "전반차".equals(applyType) || "후반차".equals(applyType);
            String message = timeInputDisabled ? "반차는 시간을 입력할 수 없습니다." : "정상";

            result.put("timeInputDisabled", timeInputDisabled);
            result.put("message", message);
            result.put("deductHours", timeInputDisabled ? 4.0 : 0.0);

            log.debug("반차 시간 입력 제한 검증: applyType={}, disabled={}", applyType, timeInputDisabled);

            return result;
        } catch (Exception e) {
            log.error("반차 검증 실패: applyType={}", applyType, e);
            result.put("timeInputDisabled", false);
            result.put("message", "검증 중 오류가 발생했습니다.");
            return result;
        }
    }

    public Map<String, Object> validateEarlyLeaveTimeInput(String applyType) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean endTimeDisabled = "조퇴".equals(applyType);
            String message = endTimeDisabled ? "조퇴는 시작시간만 입력할 수 있습니다." : "정상";

            result.put("endTimeDisabled", endTimeDisabled);
            result.put("message", message);

            log.debug("조퇴 시간 입력 제한 검증: applyType={}, endTimeDisabled={}", applyType, endTimeDisabled);

            return result;
        } catch (Exception e) {
            log.error("조퇴 검증 실패: applyType={}", applyType, e);
            result.put("endTimeDisabled", false);
            result.put("message", "검증 중 오류가 발생했습니다.");
            return result;
        }
    }

    // 일반근태 신청 상신 (캐시 없이)
    @Transactional
    public void submitGeneralApply(String applyGeneralNo, String applicantCode, String isHeader) {
        try {
            log.debug("일반근태 상신 시작: applyGeneralNo={}, applicantCode={}, isHeader={}", applyGeneralNo, applicantCode, isHeader);

            AttendanceApplyGeneral apply = attendanceApplyMapper.findGeneralApplyByNo(applyGeneralNo);

            // 전반차, 후반차 승인완료 시 연차 0.5 차감 처리
            if (apply != null && Arrays.asList("전반차", "후반차").contains(apply.getApplyType())) {
                if ("Y".equals(isHeader)) {
                    BigDecimal deductDays = new BigDecimal("0.5");
                    AnnualDetail currentAnnual = annualDetailMapper.findByEmpCodeForceRefresh(apply.getEmpCode());
                    if (currentAnnual != null) {
                        BigDecimal currentBalance = currentAnnual.getBalanceDay();
                        log.debug("전반차/후반차 연차 차감 전: empCode={}, 현재잔여={}, 차감예정={}",
                                apply.getEmpCode(), currentBalance, deductDays);

                        boolean deductionResult = annualDetailMapper.updateBalanceDayWithCheckUltra(apply.getEmpCode(), deductDays);
                        if (deductionResult) {
                            annualDetailMapper.updateUseDayIncreaseUltra(apply.getEmpCode(), deductDays);

                            AnnualDetail updatedAnnual = annualDetailMapper.findByEmpCodeForceRefresh(apply.getEmpCode());
                            log.debug("전반차/후반차 연차 차감 완료: empCode={}, 차감일수={}, 차감후잔여={}",
                                    apply.getEmpCode(), deductDays,
                                    updatedAnnual != null ? updatedAnnual.getBalanceDay() : "조회실패");
                        } else {
                            log.warn("연차 잔여량 부족으로 차감 실패: empCode={}, 요청차감일수={}", apply.getEmpCode(), deductDays);
                        }
                    }
                }
            }

            if ("Y".equals(isHeader)) {
                // 부서장인 경우 바로 승인완료 처리
                attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "승인완료");

                if (apply != null) {
                    attendanceApplyMapper.updateShiftCodeAfterGeneralApproval(apply.getEmpCode(), apply.getTargetDate(), apply.getApplyType());

                    // 🔥 수정: 캐시 초기화 호출은 유지하되 빈 동작 (기존 기능 보호)
                    if ("조출연장".equals(apply.getApplyType()) || "연장".equals(apply.getApplyType())) {
                        clearWeeklyExpectedHoursCache(apply.getEmpCode(), apply.getTargetDate());
                        log.debug("조출연장/연장근무 승인완료 (캐시 없음): empCode={}, applyType={}",
                                apply.getEmpCode(), apply.getApplyType());
                    }
                }

                // 승인완료 시 실적 업데이트
                updateAttendanceRecord(applyGeneralNo, "general");

                // 부서장 자동승인 시 결재이력 생성
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                String approvalNo = "APR" + timestamp;
                attendanceApplyMapper.insertGeneralApprovalHistory(approvalNo, applyGeneralNo, applicantCode, "승인");

                log.debug("부서장 일반근태 자동 승인완료: applyGeneralNo={}, approvalNo={}", applyGeneralNo, approvalNo);
            } else {
                // 일반 사원인 경우 상신 처리
                attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "상신");

                // 결재 이력 생성
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                String approvalNo = "APR" + timestamp;

                String deptCode = attendanceApplyMapper.getDeptCodeByGeneralApplyNo(applyGeneralNo);
                if (deptCode == null || deptCode.trim().isEmpty()) {
                    throw new RuntimeException("신청의 부서코드를 찾을 수 없습니다.");
                }

                String approverCode = attendanceApplyMapper.getDeptLeaderByDeptCode(deptCode);
                if (approverCode == null || approverCode.trim().isEmpty()) {
                    throw new RuntimeException("부서장 정보를 찾을 수 없습니다. 부서코드: " + deptCode);
                }

                attendanceApplyMapper.insertGeneralApprovalHistory(approvalNo, applyGeneralNo, approverCode, "대기");
                log.debug("일반근태 상신 완료: applyGeneralNo={}, approvalNo={}, approverCode={}",
                        applyGeneralNo, approvalNo, approverCode);
            }
        } catch (Exception e) {
            log.error("일반근태 상신 실패: applyGeneralNo={}", applyGeneralNo, e);
            throw new RuntimeException("상신에 실패했습니다: " + e.getMessage(), e);
        }
    }

    // 기타근태 신청 상신
    @Transactional
    public void submitEtcApply(String applyEtcNo, String applicantCode, String isHeader) {
        try {
            log.debug("기타근태 상신 시작: applyEtcNo={}, applicantCode={}, isHeader={}", applyEtcNo, applicantCode, isHeader);

            AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByNo(applyEtcNo);
            if (etcApply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            if ("Y".equals(isHeader)) {
                // 부서장인 경우 바로 승인완료 처리
                attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "승인완료");

                attendanceApplyMapper.updateShiftCodeAfterEtcApproval(
                        etcApply.getEmpCode(),
                        etcApply.getTargetStartDate(),
                        etcApply.getTargetEndDate(),
                        etcApply.getShiftCode()
                );

                // 연차 차감 및 실적 업데이트
                deductAnnualLeaveUltraImproved(etcApply);
                updateAttendanceRecord(applyEtcNo, "etc");

                // 부서장 자동승인 시에도 결재이력 생성
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                String approvalNo = "APR" + timestamp;
                attendanceApplyMapper.insertEtcApprovalHistory(approvalNo, applyEtcNo, applicantCode, "승인");

                log.debug("부서장 기타근태 자동 승인완료: applyEtcNo={}, approvalNo={}", applyEtcNo, approvalNo);
            } else {
                // 일반 사원인 경우 상신 처리
                attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "상신");

                // 결재 이력 생성
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                String approvalNo = "APR" + timestamp;

                // 신청자의 부서장 정보 조회
                String deptCode = attendanceApplyMapper.getDeptCodeByEtcApplyNo(applyEtcNo);
                if (deptCode == null || deptCode.trim().isEmpty()) {
                    throw new RuntimeException("신청의 부서코드를 찾을 수 없습니다.");
                }

                String approverCode = attendanceApplyMapper.getDeptLeaderByDeptCode(deptCode);
                if (approverCode == null || approverCode.trim().isEmpty()) {
                    throw new RuntimeException("부서장 정보를 찾을 수 없습니다. 부서코드: " + deptCode);
                }

                log.debug("결재자 정보: deptCode={}, approverCode={}", deptCode, approverCode);

                // 결재 이력 생성
                attendanceApplyMapper.insertEtcApprovalHistory(approvalNo, applyEtcNo, approverCode, "대기");
                log.debug("기타근태 상신 완료: applyEtcNo={}, approvalNo={}, approverCode={}",
                        applyEtcNo, approvalNo, approverCode);
            }
        } catch (Exception e) {
            log.error("기타근태 상신 실패: applyEtcNo={}", applyEtcNo, e);
            throw new RuntimeException("상신에 실패했습니다: " + e.getMessage(), e);
        }
    }

    @Transactional
    private void updateAttendanceRecord(String applyNo, String applyType) {
        try {
            if ("general".equals(applyType)) {
                // 일반근태 승인완료 시 실적 업데이트
                AttendanceApplyGeneral apply = attendanceApplyMapper.findGeneralApplyByNo(applyNo);
                if (apply != null) {
                    if ("휴일근무".equals(apply.getApplyType())) {
                        updateWorkRecordForHolidayWork(apply.getEmpCode(), apply.getTargetDate());
                    }
                    log.debug("일반근태 실적 업데이트: applyNo={}, empCode={}, targetDate={}",
                            applyNo, apply.getEmpCode(), apply.getTargetDate());
                }
            } else if ("etc".equals(applyType)) {
                // 기타근태 승인완료 시 실적 업데이트 로직
                AttendanceApplyEtc apply = attendanceApplyMapper.findEtcApplyByNo(applyNo);
                if (apply != null) {
                    updateWorkRecordForAnnualLeave(apply.getEmpCode(), apply.getTargetStartDate(), apply.getShiftCode());
                    log.debug("기타근태 실적 업데이트: applyNo={}, empCode={}, shiftCode={}",
                            applyNo, apply.getEmpCode(), apply.getShiftCode());
                }
            }
        } catch (Exception e) {
            log.error("실적 업데이트 실패: applyNo={}, applyType={}", applyNo, applyType, e);
        }
    }

    @Transactional
    private void deductAnnualLeaveUltraImproved(AttendanceApplyEtc etcApply) {
        try {
            String shiftCode = etcApply.getShiftCode();
            if (shiftCode != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(shiftCode);
                if (shift != null) {
                    String shiftName = shift.getShiftName();
                    BigDecimal deductDays = BigDecimal.ZERO;

                    // 연차 유형에 따른 차감 일수 계산
                    if ("연차".equals(shiftName)) {
                        deductDays = BigDecimal.ONE; // 연차는 1일 차감
                    } else if ("전반차".equals(shiftName) || "후반차".equals(shiftName)) {
                        deductDays = new BigDecimal("0.5"); // 반차는 0.5일 차감
                    }

                    // 연차 차감이 필요한 경우
                    if (deductDays.compareTo(BigDecimal.ZERO) > 0) {
                        AnnualDetail currentAnnual = annualDetailMapper.findByEmpCodeForceRefresh(etcApply.getEmpCode());
                        if (currentAnnual != null) {
                            BigDecimal currentBalance = currentAnnual.getBalanceDay().setScale(1, RoundingMode.HALF_UP);
                            BigDecimal currentUse = currentAnnual.getUseDay().setScale(1, RoundingMode.HALF_UP);
                            BigDecimal deductDaysScaled = deductDays.setScale(1, RoundingMode.HALF_UP);

                            log.debug("연차 차감 전 상태 (캐시 없음): empCode={}, 현재잔여={}, 현재사용={}, 차감예정={}",
                                    etcApply.getEmpCode(), currentBalance, currentUse, deductDaysScaled);

                            boolean deductionResult = annualDetailMapper.updateBalanceDayWithCheckUltra(
                                    etcApply.getEmpCode(), deductDaysScaled);

                            if (deductionResult) {
                                annualDetailMapper.updateUseDayIncreaseUltra(etcApply.getEmpCode(), deductDaysScaled);

                                AnnualDetail updatedAnnual = annualDetailMapper.findByEmpCodeForceRefresh(etcApply.getEmpCode());
                                if (updatedAnnual != null) {
                                    BigDecimal updatedBalance = updatedAnnual.getBalanceDay().setScale(1, RoundingMode.HALF_UP);
                                    BigDecimal updatedUse = updatedAnnual.getUseDay().setScale(1, RoundingMode.HALF_UP);

                                    log.debug("연차 차감 및 USE_DAY 증가 완료 (캐시 없음): empCode={}, 차감일수={}, 차감후잔여={}, 차감후사용={}",
                                            etcApply.getEmpCode(), deductDaysScaled, updatedBalance, updatedUse);

                                    BigDecimal expectedBalance = currentBalance.subtract(deductDaysScaled).setScale(1, RoundingMode.HALF_UP);
                                    BigDecimal expectedUse = currentUse.add(deductDaysScaled).setScale(1, RoundingMode.HALF_UP);

                                    if (updatedBalance.compareTo(expectedBalance) != 0) {
                                        log.error("연차 차감 계산 오류 (캐시 없음): 예상잔여={}, 실제잔여={}", expectedBalance, updatedBalance);
                                        annualDetailMapper.forceRecalculateAnnual(etcApply.getEmpCode(), expectedBalance, expectedUse);
                                    }
                                    if (updatedUse.compareTo(expectedUse) != 0) {
                                        log.error("연차 사용 계산 오류 (캐시 없음): 예상사용={}, 실제사용={}", expectedUse, updatedUse);
                                        annualDetailMapper.forceRecalculateAnnual(etcApply.getEmpCode(), expectedBalance, expectedUse);
                                    }
                                }
                            } else {
                                log.warn("연차 잔여량 부족으로 차감 실패 (캐시 없음): empCode={}, 요청차감일수={}, 현재잔여={}",
                                        etcApply.getEmpCode(), deductDaysScaled, currentBalance);
                                throw new RuntimeException("연차 잔여량이 부족합니다. 현재 잔여: " + currentBalance + "일, 요청 차감: " + deductDaysScaled + "일");
                            }
                        } else {
                            log.error("연차 정보 조회 실패 (캐시 없음): empCode={}", etcApply.getEmpCode());
                            throw new RuntimeException("연차 정보를 찾을 수 없습니다.");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("연차 차감 실패 (캐시 없음): etcApply={}", etcApply, e);
            throw new RuntimeException("연차 차감에 실패했습니다: " + e.getMessage(), e);
        }
    }

    // 일반근태 신청 상신취소 처리
    @Transactional
    public void cancelGeneralApply(String applyGeneralNo, String applicantCode) {
        try {
            // 본인 신청건만 취소 가능하도록 검증
            boolean isOwner = attendanceApplyMapper.checkGeneralApplyOwnership(applyGeneralNo, applicantCode);
            if (!isOwner) {
                throw new RuntimeException("본인 신청건만 취소할 수 있습니다.");
            }

            // 상신 상태인 경우만 취소 가능
            String status = attendanceApplyMapper.getGeneralApplyStatus(applyGeneralNo);
            if (!"상신".equals(status)) {
                throw new RuntimeException("상신 상태인 신청건만 취소할 수 있습니다.");
            }

            // 결재 이력 삭제
            attendanceApplyMapper.deleteGeneralApprovalHistory(applyGeneralNo);

            // 상태를 '저장'으로 변경
            attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "저장");

            log.debug("일반근태 상신취소 완료: applyGeneralNo={}", applyGeneralNo);
        } catch (Exception e) {
            log.error("일반근태 상신취소 실패: applyGeneralNo={}", applyGeneralNo, e);
            throw new RuntimeException("상신취소에 실패했습니다.", e);
        }
    }

    // 기타근태 신청 상신취소 처리
    @Transactional
    public void cancelEtcApply(String applyEtcNo, String applicantCode) {
        try {
            // 본인 신청건만 취소 가능하도록 검증
            boolean isOwner = attendanceApplyMapper.checkEtcApplyOwnership(applyEtcNo, applicantCode);
            if (!isOwner) {
                throw new RuntimeException("본인 신청건만 취소할 수 있습니다.");
            }

            // 상신 상태인 경우만 취소 가능
            String status = attendanceApplyMapper.getEtcApplyStatus(applyEtcNo);
            if (!"상신".equals(status)) {
                throw new RuntimeException("상신 상태인 신청건만 취소할 수 있습니다.");
            }

            // 결재 이력 삭제
            attendanceApplyMapper.deleteEtcApprovalHistory(applyEtcNo);

            // 상태를 '저장'으로 변경
            attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "저장");

            log.debug("기타근태 상신취소 완료: applyEtcNo={}", applyEtcNo);
        } catch (Exception e) {
            log.error("기타근태 상신취소 실패: applyEtcNo={}", applyEtcNo, e);
            throw new RuntimeException("상신취소에 실패했습니다.", e);
        }
    }

    // 일반근태 신청 삭제 처리
    @Transactional
    public void deleteGeneralApply(String applyGeneralNo, String applicantCode) {
        try {
            // 본인 신청건만 삭제 가능하도록 검증
            boolean isOwner = attendanceApplyMapper.checkGeneralApplyOwnership(applyGeneralNo, applicantCode);
            if (!isOwner) {
                throw new RuntimeException("본인 신청건만 삭제할 수 있습니다.");
            }

            // 저장 상태인 경우만 삭제 가능
            String status = attendanceApplyMapper.getGeneralApplyStatus(applyGeneralNo);
            if (!"저장".equals(status)) {
                throw new RuntimeException("저장 상태인 신청건만 삭제할 수 있습니다.");
            }

            attendanceApplyMapper.deleteGeneralApply(applyGeneralNo);
            log.debug("일반근태 삭제 완료: applyGeneralNo={}", applyGeneralNo);
        } catch (Exception e) {
            log.error("일반근태 삭제 실패: applyGeneralNo={}", applyGeneralNo, e);
            throw new RuntimeException("삭제에 실패했습니다.", e);
        }
    }

    // 기타근태 신청 삭제 처리
    @Transactional
    public void deleteEtcApply(String applyEtcNo, String applicantCode) {
        try {
            // 본인 신청건만 삭제 가능하도록 검증
            boolean isOwner = attendanceApplyMapper.checkEtcApplyOwnership(applyEtcNo, applicantCode);
            if (!isOwner) {
                throw new RuntimeException("본인 신청건만 삭제할 수 있습니다.");
            }

            // 저장 상태인 경우만 삭제 가능
            String status = attendanceApplyMapper.getEtcApplyStatus(applyEtcNo);
            if (!"저장".equals(status)) {
                throw new RuntimeException("저장 상태인 신청건만 삭제할 수 있습니다.");
            }

            attendanceApplyMapper.deleteEtcApply(applyEtcNo);
            log.debug("기타근태 삭제 완료: applyEtcNo={}", applyEtcNo);
        } catch (Exception e) {
            log.error("기타근태 삭제 실패: applyEtcNo={}", applyEtcNo, e);
            throw new RuntimeException("삭제에 실패했습니다.", e);
        }
    }
}