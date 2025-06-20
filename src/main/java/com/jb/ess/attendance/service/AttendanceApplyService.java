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

    // 🔧 수정: 예상근로시간 캐시 시스템 강화 - 신청근무별 개별 캐시 추가
    private Map<String, String> expectedHoursCache = new HashMap<>();
    private Map<String, String> workTypeSpecificCache = new HashMap<>(); // 신청근무별 개별 캐시

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

            // 전체 부서 구조를 기반으로 재귀적으로 하위부서 조회
            List<Department> allDepartments = departmentMapper.findAllDepartments();
            List<Department> subDepartments = new ArrayList<>();

            // 현재 부서 포함
            Department currentDept = departmentMapper.findByDeptCode(parentDeptCode);
            if (currentDept != null) {
                subDepartments.add(currentDept);
            }

            // 재귀적으로 모든 하위부서 찾기
            findAllSubDepartments(parentDeptCode, allDepartments, subDepartments);

            log.debug("하위부서 조회 완료: parentDeptCode={}, 조회된 부서 수={}", parentDeptCode, subDepartments.size());
            return subDepartments;
        } catch (Exception e) {
            log.error("하위부서 조회 실패: parentDeptCode={}", parentDeptCode, e);
            return List.of();
        }
    }

    // 재귀적으로 모든 하위부서를 찾는 헬퍼 메서드
    private void findAllSubDepartments(String parentDeptCode, List<Department> allDepartments, List<Department> result) {
        for (Department dept : allDepartments) {
            if (parentDeptCode.equals(dept.getParentDept()) && !isAlreadyInResult(dept.getDeptCode(), result)) {
                result.add(dept);
                // 재귀적으로 이 부서의 하위부서도 찾기
                findAllSubDepartments(dept.getDeptCode(), allDepartments, result);
            }
        }
    }

    // 중복 부서 체크 헬퍼 메서드
    private boolean isAlreadyInResult(String deptCode, List<Department> result) {
        return result.stream().anyMatch(dept -> deptCode.equals(dept.getDeptCode()));
    }

    // 🔧 수정: 연차잔여 정보 조회 - 정확한 계산 보장
    public AnnualDetail getAnnualDetail(String empCode) {
        try {
            AnnualDetail annualDetail = annualDetailMapper.findByEmpCode(empCode);
            if (annualDetail != null) {
                // 🔧 추가: 조회 시에도 정확한 계산 보장 - 재계산 강제
                log.debug("연차 조회 정확성 검증: empCode={}, 조회된잔여={}, 조회된사용={}",
                        empCode, annualDetail.getBalanceDay(), annualDetail.getUseDay());

                // 🔧 중요: 조회 시에도 정확한 값 반환 보장
                AnnualDetail refreshedAnnual = annualDetailMapper.findByEmpCodeForceRefresh(empCode);
                if (refreshedAnnual != null) {
                    log.debug("연차 강제 재조회: empCode={}, 갱신된잔여={}, 갱신된사용={}",
                            empCode, refreshedAnnual.getBalanceDay(), refreshedAnnual.getUseDay());
                    return refreshedAnnual;
                }
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

    // 유효한 TIME_ITEM_CODE 조회
    public String getValidTimeItemCode() {
        try {
            return attendanceApplyMapper.getValidTimeItemCode();
        } catch (Exception e) {
            log.error("TIME_ITEM_CODE 조회 실패", e);
            return null;
        }
    }

    // 🔧 수정: 결근 처리 통일 - 일반근태와 기타근태 동일하게 처리
    public boolean isEmployeeAbsent(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate today = LocalDate.now();

            // 🔧 수정: 미래 날짜는 결근 체크하지 않음 (일반근태와 기타근태 모두 동일)
            if (targetDate.isAfter(today)) {
                log.debug("미래 날짜는 결근 체크하지 않음 (통일): empCode={}, workDate={}", empCode, workDate);
                return false;
            }

            // 실적 조회
            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, workDate);

            // 출근 기록이 없는 경우 계획 확인
            if (attRecord == null || attRecord.getCheckInTime() == null) {
                String originalShiftCode = getOriginalShiftCode(empCode, workDate);
                if (originalShiftCode != null) {
                    String planShiftName = shiftMasterMapper.findShiftNameByShiftCode(originalShiftCode);
                    // 휴무일/휴일이 아닌 근무일인데 출근 기록이 없으면 결근
                    if (!"휴무일".equals(planShiftName) && !"휴일".equals(planShiftName)) {
                        log.debug("결근 판정 (통일): empCode={}, workDate={}, plan={}", empCode, workDate, planShiftName);
                        return true;
                    }
                } else {
                    EmpCalendar empCalendar = empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, workDate);
                    if (empCalendar != null && empCalendar.getShiftCode() != null) {
                        String planShiftName = shiftMasterMapper.findShiftNameByShiftCode(empCalendar.getShiftCode());
                        if (!"휴무일".equals(planShiftName) && !"휴일".equals(planShiftName)) {
                            log.debug("결근 판정 (통일): empCode={}, workDate={}, plan={}", empCode, workDate, planShiftName);
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

    // 기타근태 날짜 범위 검증 메서드
    public boolean validateDateRange(String startDate, String endDate) {
        try {
            LocalDate start = LocalDate.parse(startDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate end = LocalDate.parse(endDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 날짜 범위 내에서 휴일/휴무일 체크
            for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                // 해당 날짜가 휴일/휴무일인지 확인 (일반적인 계획 조회)
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

    // 🔧 수정: 근무정보 조회 메서드 - 신청근무별 개별 캐시 강화
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

            // 미래 날짜 체크
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate today = LocalDate.now();
            boolean isFutureDate = targetDate.isAfter(today);

            // 실적 조회
            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, workDate);
            Map<String, String> record = new HashMap<>();

            // 승인된 신청을 먼저 확인하여 실적 결정
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

            // 🔧 수정: 휴일근무 후 예상근로시간 연계 - 신청근무별 개별 캐시 강화
            String weeklyExpectedHours = calculateWeeklyExpectedHoursUltraStable(empCode, workDate);

            workInfo.put("plan", empCalendarPlan);
            workInfo.put("empCalendarPlan", empCalendarPlan);
            workInfo.put("record", record);
            workInfo.put("appliedRecord", appliedRecord);
            workInfo.put("expectedHours", weeklyExpectedHours);

            log.debug("근무정보 조회 완료 (신청근무별 캐시 강화): empCode={}, workDate={}, plan={}, actual={}, weeklyHours={}",
                    empCode, workDate, empCalendarPlan, actualShiftName, weeklyExpectedHours);
        } catch (Exception e) {
            log.error("근무정보 조회 실패 (empCalendar 기반): empCode={}, workDate={}", empCode, workDate, e);
            workInfo.put("plan", "");
            workInfo.put("empCalendarPlan", "");
            workInfo.put("record", Map.of("checkInTime", "-", "checkOutTime", "-", "shiftCode", "00", "shiftName", "결근"));
            workInfo.put("appliedRecord", null);
            workInfo.put("expectedHours", "40.00"); // 기본 주간 예상근로시간
        }

        return workInfo;
    }

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
                // 다른 기타근태도 해당 근태명 반환
                if (shiftName != null) {
                    log.debug("승인된 기타근태 확인: empCode={}, date={}, 실적={}", empCode, workDate, shiftName);
                    return shiftName;
                }
            }

            // 승인된 일반근태 신청 확인 (휴일근무)
            AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(empCode, workDate);
            if (generalApply != null && "승인완료".equals(generalApply.getStatus())) {
                String applyType = generalApply.getApplyType();
                if ("휴일근무".equals(applyType)) {
                    log.debug("승인된 휴일근무 확인: empCode={}, date={}, 실적=휴일근무", empCode, workDate);
                    return "휴일근무"; // 승인된 휴일근무는 실적을 "휴일근무"로 표시
                }
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

    // 신청된 실적 조회 메서드
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

    private String calculateDailyExpectedHoursImproved(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            log.debug("개선된 일별 예상근로시간 계산 시작: empCode={}, workDate={}", empCode, workDate);

            String originalShiftCode = getOriginalShiftCode(empCode, workDate);
            EmpCalendar empCalendar = empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, workDate);

            String planShiftCode = originalShiftCode != null ? originalShiftCode :
                    (empCalendar != null ? empCalendar.getShiftCode() : null);

            // 공휴일 체크
            if (empCalendar != null && "Y".equals(empCalendar.getHolidayYn())) {
                log.debug("공휴일로 0시간: {}", workDate);
                return "0.00";
            }

            Duration dailyHours = Duration.ZERO;

            if (planShiftCode != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(planShiftCode);
                if (shift != null) {
                    log.debug("근태마스터 조회: date={}, shiftCode={}, shiftName={}", workDate, shift.getShiftCode(), shift.getShiftName());

                    // 연차, 결근, 휴일, 휴무일인 경우 0시간 처리
                    if ("연차".equals(shift.getShiftName()) ||
                            "결근".equals(shift.getShiftName()) ||
                            "휴일".equals(shift.getShiftName()) ||
                            "휴무일".equals(shift.getShiftName()) ||
                            "휴직".equals(shift.getShiftName())) {
                        log.debug("비근무일로 0시간: date={}, shiftName={}", workDate, shift.getShiftName());
                        return "0.00";
                    }

                    // 정상 근무일인 경우만 계산
                    if (!Objects.equals(shift.getShiftCode(), "00")) {
                        // 실적 확인
                        AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, workDate);

                        if (attRecord != null && attRecord.getCheckInTime() != null) {
                            List<Pair<String, String>> leavePeriods = new ArrayList<>();
                            List<String> timeItemNames = attendanceApplyMapper.findApprovedTimeItemCode(empCode, workDate, "승인완료");
                            for (String timeItemName : timeItemNames) {
                                AttendanceApplyGeneral attendanceApplyGeneral = attendanceApplyMapper.findStartTimeAndEndTime(empCode, workDate, "승인완료", timeItemName);
                                if (attendanceApplyGeneral != null) {
                                    leavePeriods.add(Pair.of(attendanceApplyGeneral.getStartTime(), attendanceApplyGeneral.getEndTime()));
                                }
                            }

                            dailyHours = WorkHoursCalculator.getRealWorkTime(
                                    attRecord.getCheckInTime(),
                                    attRecord.getCheckOutTime(),
                                    shift,
                                    targetDate,
                                    leavePeriods
                            );
                            log.debug("실적 기반 시간 (휴게시간 차감됨): date={}, hours={}", workDate, dailyHours.toMinutes() / 60.0);
                        } else {
                            dailyHours = WorkHoursCalculator.getTotalWorkTime(shift);
                            log.debug("계획 기준 시간 (휴게시간 차감됨): date={}, hours={}", workDate, dailyHours.toMinutes() / 60.0);
                        }
                    }
                }
            }

            // 해당 일자의 일반근태 신청 내역
            AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(empCode, workDate);
            if (generalApply != null && ("승인완료".equals(generalApply.getStatus()) || "상신".equals(generalApply.getStatus()))) {
                Duration applyHours = calculateApplyHours(generalApply);

                if ("휴일근무".equals(generalApply.getApplyType())) {
                    applyHours = calculateHolidayWorkHoursAccurate(generalApply);
                    dailyHours = dailyHours.plus(applyHours);
                } else {
                    dailyHours = dailyHours.plus(applyHours);
                }

                log.debug("일반근태 신청 시간 추가: date={}, applyType={}, hours={}", workDate, generalApply.getApplyType(), applyHours.toMinutes() / 60.0);
            }

            // 해당 일자의 기타근태 신청 내역 차감
            AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(empCode, workDate);
            if (etcApply != null && ("승인완료".equals(etcApply.getStatus()) || "상신".equals(etcApply.getStatus()))) {
                Duration deductHours = calculateDeductHours(etcApply);
                dailyHours = dailyHours.minus(deductHours);
                log.debug("기타근태 신청 시간 차감: date={}, hours={}", workDate, deductHours.toMinutes() / 60.0);
            }

            double hours = Math.max(0, dailyHours.toMinutes() / 60.0);
            log.debug("개선된 일별 예상근로시간 계산 완료: empCode={}, date={}, totalHours={}", empCode, workDate, hours);

            return String.format("%.2f", hours);
        } catch (Exception e) {
            log.error("개선된 일별 예상근로시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            return "0.00";
        }
    }

    // 🔧 수정: 휴일근무 후 예상근로시간 연계 - 울트라 안정화 (신청근무별 개별 캐시)
    private String calculateWeeklyExpectedHoursUltraStable(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 해당 주의 월요일부터 일요일까지 계산
            LocalDate mondayOfWeek = targetDate.with(DayOfWeek.MONDAY);
            LocalDate sundayOfWeek = targetDate.with(DayOfWeek.SUNDAY);

            String baseCacheKey = empCode + "_" + mondayOfWeek.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 🔧 울트라 안정화: 휴일근무가 있는지 먼저 확인
            boolean hasHolidayWorkThisWeek = checkHasHolidayWorkThisWeek(empCode, mondayOfWeek, sundayOfWeek);

            if (hasHolidayWorkThisWeek) {
                // 🔧 휴일근무가 있으면 신규 계산 (48시간 달성 가능)
                log.debug("휴일근무 발견 - 신규 계산 수행: empCode={}", empCode);
            } else {
                // 🔧 휴일근무가 없으면 캐시 사용 (40시간 기본)
                if (expectedHoursCache.containsKey(baseCacheKey)) {
                    String cachedHours = expectedHoursCache.get(baseCacheKey);
                    log.debug("휴일근무 없음 - 캐시에서 주 예상근로시간 반환: empCode={}, hours={}", empCode, cachedHours);
                    return cachedHours;
                }
            }

            Duration totalWeekHours = Duration.ZERO;

            log.debug("울트라 안정화된 주 예상근로시간 계산 시작: empCode={}, 주간={} ~ {}", empCode, mondayOfWeek, sundayOfWeek);

            // 주중 7일간 계산 - 개선된 메서드 사용
            for (LocalDate date = mondayOfWeek; !date.isAfter(sundayOfWeek); date = date.plusDays(1)) {
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                // 해당 일자의 예상근로시간을 구해서 더하기 - 개선된 메서드 사용
                String dailyHours = calculateDailyExpectedHoursImproved(empCode, dateStr);
                try {
                    Duration dayDuration = Duration.ofMinutes((long)(Double.parseDouble(dailyHours) * 60));
                    totalWeekHours = totalWeekHours.plus(dayDuration);
                    log.debug("일별 시간 누적 (울트라 안정화): date={}, dailyHours={}, 누적시간={}", dateStr, dailyHours, totalWeekHours.toMinutes() / 60.0);
                } catch (NumberFormatException e) {
                    log.warn("일별 시간 파싱 실패 (울트라 안정화): date={}, dailyHours={}", dateStr, dailyHours);
                    // 파싱 실패 시 0시간으로 처리하여 계속 진행
                }
            }

            double weeklyHours = totalWeekHours.toMinutes() / 60.0;
            String formattedHours = String.format("%.2f", weeklyHours);

            // 🔧 캐시에 저장 - 휴일근무 여부와 관계없이 항상 저장
            expectedHoursCache.put(baseCacheKey, formattedHours);

            log.debug("울트라 안정화된 주 예상근로시간 계산 완료: empCode={}, totalHours={}, hasHolidayWork={}",
                    empCode, weeklyHours, hasHolidayWorkThisWeek);

            return formattedHours;
        } catch (Exception e) {
            log.error("울트라 안정화된 주 예상근로시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            return "40.00"; // 기본 주 40시간
        }
    }

    // 🔧 추가: 해당 주에 휴일근무가 있는지 확인
    private boolean checkHasHolidayWorkThisWeek(String empCode, LocalDate monday, LocalDate sunday) {
        try {
            // 해당 주에 휴일근무 신청이 있는지 확인
            for (LocalDate date = monday; !date.isAfter(sunday); date = date.plusDays(1)) {
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(empCode, dateStr);
                if (generalApply != null && "휴일근무".equals(generalApply.getApplyType()) &&
                        ("승인완료".equals(generalApply.getStatus()) || "상신".equals(generalApply.getStatus()))) {
                    log.debug("주중 휴일근무 신청 발견: empCode={}, date={}, status={}", empCode, dateStr, generalApply.getStatus());
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("휴일근무 신청 확인 실패: empCode={}", empCode, e);
            return false;
        }
    }

    // 🔧 수정: 휴일근무 후 예상근로시간 연계 - 캐시 시스템 강화 (기존 호환성 유지)
    private String calculateWeeklyExpectedHoursWithCacheStable(String empCode, String workDate) {
        return calculateWeeklyExpectedHoursUltraStable(empCode, workDate);
    }

    // 🔧 추가: 새로운 휴일근무 신청이 있는지 확인
    private boolean checkHasNewHolidayWorkApply(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate mondayOfWeek = targetDate.with(DayOfWeek.MONDAY);
            LocalDate sundayOfWeek = targetDate.with(DayOfWeek.SUNDAY);

            return checkHasHolidayWorkThisWeek(empCode, mondayOfWeek, sundayOfWeek);
        } catch (Exception e) {
            log.error("휴일근무 신청 확인 실패: empCode={}, workDate={}", empCode, workDate, e);
            return true; // 오류 시 안전하게 새로 계산
        }
    }

    // 일별 예상근로시간 계산 메서드
    private String calculateDailyExpectedHours(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            log.debug("일별 예상근로시간 계산 시작: empCode={}, workDate={}", empCode, workDate);

            // 해당 일자의 계획 조회
            EmpCalendar empCalendar = empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, workDate);

            // 공휴일 체크
            if (empCalendar != null && "Y".equals(empCalendar.getHolidayYn())) {
                log.debug("공휴일로 0시간: {}", workDate);
                return "0.00";
            }

            Duration dailyHours = Duration.ZERO;

            if (empCalendar != null && empCalendar.getShiftCode() != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(empCalendar.getShiftCode());
                if (shift != null) {
                    log.debug("근태마스터 조회: date={}, shiftCode={}, shiftName={}", workDate, shift.getShiftCode(), shift.getShiftName());

                    // 결근이 아니고 정상 근무일인 경우만 계산
                    if (!Objects.equals(shift.getShiftCode(), "00") &&
                            !"휴일".equals(shift.getShiftName()) &&
                            !"휴무일".equals(shift.getShiftName()) &&
                            !"연차".equals(shift.getShiftName()) &&
                            !"휴직".equals(shift.getShiftName())) {

                        // 실적 확인
                        AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, workDate);

                        if (attRecord != null && attRecord.getCheckInTime() != null) {
                            // apply.txt: "출근 시각이 존재하면 계획 그대로의 값"
                            List<Pair<String, String>> leavePeriods = new ArrayList<>();
                            List<String> timeItemNames = attendanceApplyMapper.findApprovedTimeItemCode(empCode, workDate, "승인완료");
                            for (String timeItemName : timeItemNames) {
                                AttendanceApplyGeneral attendanceApplyGeneral = attendanceApplyMapper.findStartTimeAndEndTime(empCode, workDate, "승인완료", timeItemName);
                                if (attendanceApplyGeneral != null) {
                                    leavePeriods.add(Pair.of(attendanceApplyGeneral.getStartTime(), attendanceApplyGeneral.getEndTime()));
                                }
                            }
                            dailyHours = WorkHoursCalculator.getRealWorkTime(
                                    attRecord.getCheckInTime(),
                                    attRecord.getCheckOutTime(),
                                    shift,
                                    targetDate,
                                    leavePeriods
                            );
                            log.debug("실적 기반 시간: date={}, hours={}", workDate, dailyHours.toMinutes() / 60.0);
                        } else {
                            // 실적이 없으면 계획 시간으로 계산
                            dailyHours = WorkHoursCalculator.getTotalWorkTime(shift);
                            log.debug("계획 기준 시간: date={}, hours={}", workDate, dailyHours.toMinutes() / 60.0);
                        }
                    } else {
                        log.debug("비근무일로 0시간: date={}, shiftName={}", workDate, shift.getShiftName());
                    }
                }
            }

            // 해당 일자의 일반근태 신청 내역
            AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(empCode, workDate);
            if (generalApply != null && ("승인완료".equals(generalApply.getStatus()) || "상신".equals(generalApply.getStatus()))) {
                Duration applyHours = calculateApplyHours(generalApply);
                dailyHours = dailyHours.plus(applyHours);
                log.debug("일반근태 신청 시간 추가: date={}, hours={}", workDate, applyHours.toMinutes() / 60.0);
            }

            // 해당 일자의 기타근태 신청 내역 차감
            AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(empCode, workDate);
            if (etcApply != null && ("승인완료".equals(etcApply.getStatus()) || "상신".equals(etcApply.getStatus()))) {
                Duration deductHours = calculateDeductHours(etcApply);
                dailyHours = dailyHours.minus(deductHours);
                log.debug("기타근태 신청 시간 차감: date={}, hours={}", workDate, deductHours.toMinutes() / 60.0);
            }

            double hours = dailyHours.toMinutes() / 60.0;
            log.debug("일별 예상근로시간 계산 완료: empCode={}, date={}, totalHours={}", empCode, workDate, hours);

            return String.format("%.2f", hours);
        } catch (Exception e) {
            log.error("일별 예상근로시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            return "0.00";
        }
    }

    // 요구사항: 휴일근무 후 연장근로 신청 시 예상근로시간 연계 - 안정화된 계산
    private String calculateWeeklyExpectedHoursStable(String empCode, String workDate) {
        return calculateWeeklyExpectedHoursUltraStable(empCode, workDate);
    }

    // 요구사항: 휴일근무 후 연장근로 신청 시 예상근로시간 연계 - 48시간 기준 계산
    private String calculateWeeklyExpectedHoursWithHolidayWork(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 해당 주의 월요일부터 일요일까지 계산
            LocalDate mondayOfWeek = targetDate.with(DayOfWeek.MONDAY);
            LocalDate sundayOfWeek = targetDate.with(DayOfWeek.SUNDAY);

            Duration totalWeekHours = Duration.ZERO;

            log.debug("주 예상근로시간 계산 시작 (휴일근무 포함): empCode={}, 주간={} ~ {}", empCode, mondayOfWeek, sundayOfWeek);

            // 주중 7일간 계산 - 개선된 메서드 사용
            for (LocalDate date = mondayOfWeek; !date.isAfter(sundayOfWeek); date = date.plusDays(1)) {
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                // 해당 일자의 예상근로시간을 구해서 더하기 - 개선된 메서드 사용
                String dailyHours = calculateDailyExpectedHoursImproved(empCode, dateStr);
                Duration dayDuration = Duration.ofMinutes((long)(Double.parseDouble(dailyHours) * 60));
                totalWeekHours = totalWeekHours.plus(dayDuration);
            }

            double weeklyHours = totalWeekHours.toMinutes() / 60.0;
            log.debug("주 예상근로시간 계산 완료 (휴일근무 포함): empCode={}, totalHours={}", empCode, weeklyHours);

            return String.format("%.2f", weeklyHours);
        } catch (Exception e) {
            log.error("주 예상근로시간 계산 실패 (휴일근무 포함): empCode={}, workDate={}", empCode, workDate, e);
            return "40.00"; // 기본 주 40시간
        }
    }

    // 주 52시간 검증용 예상근로시간 계산
    private String calculateWeeklyExpectedHours(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 해당 주의 월요일부터 일요일까지 계산
            LocalDate mondayOfWeek = targetDate.with(DayOfWeek.MONDAY);
            LocalDate sundayOfWeek = targetDate.with(DayOfWeek.SUNDAY);

            Duration totalWeekHours = Duration.ZERO;

            log.debug("주 예상근로시간 계산 시작: empCode={}, 주간={} ~ {}", empCode, mondayOfWeek, sundayOfWeek);

            // 주중 7일간 계산 - 개선된 메서드 사용
            for (LocalDate date = mondayOfWeek; !date.isAfter(sundayOfWeek); date = date.plusDays(1)) {
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                // 해당 일자의 예상근로시간을 구해서 더하기 - 개선된 메서드 사용
                String dailyHours = calculateDailyExpectedHoursImproved(empCode, dateStr);
                Duration dayDuration = Duration.ofMinutes((long)(Double.parseDouble(dailyHours) * 60));
                totalWeekHours = totalWeekHours.plus(dayDuration);
            }

            double weeklyHours = totalWeekHours.toMinutes() / 60.0;
            log.debug("주 예상근로시간 계산 완료: empCode={}, totalHours={}", empCode, weeklyHours);

            return String.format("%.2f", weeklyHours);
        } catch (Exception e) {
            log.error("주 예상근로시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            return "40.00"; // 기본 주 40시간
        }
    }

    // 신청 시간 계산 - NumberFormatException 처리 강화
    private Duration calculateApplyHours(AttendanceApplyGeneral apply) {
        try {
            // 요구사항: NumberFormatException 처리 - 전반차/후반차 빈 문자열 처리
            if (apply.getStartTime() != null && apply.getEndTime() != null &&
                    !apply.getStartTime().trim().isEmpty() && !apply.getEndTime().trim().isEmpty()) {

                if ("휴일근무".equals(apply.getApplyType())) {
                    return calculateHolidayWorkHoursAccurate(apply);
                }

                try {
                    int startTime = Integer.parseInt(apply.getStartTime().trim());
                    int endTime = Integer.parseInt(apply.getEndTime().trim());

                    int startHour = startTime / 100;
                    int startMin = startTime % 100;
                    int endHour = endTime / 100;
                    int endMin = endTime % 100;

                    int totalMinutes = (endHour * 60 + endMin) - (startHour * 60 + startMin);
                    return Duration.ofMinutes(totalMinutes);
                } catch (NumberFormatException e) {
                    log.warn("시간 파싱 실패: startTime={}, endTime={}, applyType={}",
                            apply.getStartTime(), apply.getEndTime(), apply.getApplyType());
                    return Duration.ZERO;
                }
            }
        } catch (Exception e) {
            log.error("신청 시간 계산 실패", e);
        }
        return Duration.ZERO;
    }

    // 차감 시간 계산 (반차, 연차, 조퇴 등)
    private Duration calculateDeductHours(AttendanceApplyEtc apply) {
        try {
            String shiftCode = apply.getShiftCode();
            if (shiftCode != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(shiftCode);
                if (shift != null) {
                    // 근태 유형에 따른 차감 시간 계산
                    String shiftName = shift.getShiftName();
                    if ("연차".equals(shiftName)) {
                        return Duration.ofHours(8); // 연차는 8시간 차감
                    } else if ("전반차".equals(shiftName) || "후반차".equals(shiftName)) {
                        return Duration.ofHours(4); // 반차는 4시간 차감
                    } else if ("조퇴".equals(shiftName) || "외출".equals(shiftName)) {
                        // 조퇴/외출은 실제 차감 시간 계산 필요
                        return Duration.ofHours(2); // 임시로 2시간 설정
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

    private Duration calculateHolidayWorkHoursAccurate(AttendanceApplyGeneral apply) {
        try {
            // 요구사항: NumberFormatException 처리 강화
            if (apply.getStartTime() != null && apply.getEndTime() != null &&
                    !apply.getStartTime().trim().isEmpty() && !apply.getEndTime().trim().isEmpty()) {

                // 휴일근무 시프트 정보 가져오기 (14-1)
                ShiftMaster holidayShift = shiftMasterMapper.findShiftByCode("14-1");
                if (holidayShift != null) {
                    try {
                        String startTimeStr = String.format("%04d00", Integer.parseInt(apply.getStartTime().trim()));
                        String endTimeStr = String.format("%04d00", Integer.parseInt(apply.getEndTime().trim()));

                        LocalDate workDate = LocalDate.parse(apply.getTargetDate(), DateTimeFormatter.ofPattern("yyyyMMdd"));

                        // WorkHoursCalculator.getRealWorkTime 사용 (빈 leavePeriods)
                        List<Pair<String, String>> emptyLeavePeriods = new ArrayList<>();
                        Duration workDuration = WorkHoursCalculator.getRealWorkTime(
                                startTimeStr, endTimeStr, holidayShift, workDate, emptyLeavePeriods);

                        log.debug("휴일근로 정확한 시간 계산: start={}, end={}, duration={}시간",
                                apply.getStartTime(), apply.getEndTime(), workDuration.toMinutes() / 60.0);

                        return workDuration;
                    } catch (NumberFormatException e) {
                        log.warn("휴일근무 시간 파싱 실패: startTime={}, endTime={}",
                                apply.getStartTime(), apply.getEndTime());
                        return Duration.ZERO;
                    }
                }
            }
        } catch (Exception e) {
            log.error("휴일근로 정확한 시간 계산 실패", e);
        }

        // 기본 계산으로 폴백
        return calculateApplyHours(apply);
    }

    public Map<String, Object> calculateRealTimeWeeklyHours(String empCode, String workDate, String startTime, String endTime, String applyType) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.debug("실시간 주 52시간 계산 시작: empCode={}, workDate={}, applyType={}", empCode, workDate, applyType);

            // 해당 주의 기본 근무시간 계산 - 안정화된 메서드 사용
            double baseWeeklyHours = calculateCurrentWeeklyHoursStable(empCode, workDate);

            // 신청하려는 시간 계산
            double requestHours = 0.0;
            if (startTime != null && endTime != null && !startTime.isEmpty() && !endTime.isEmpty()) {
                // 요구사항: 조출연장 시간 제한 검증 - 매퍼 메서드 사용
                if ("조출연장".equals(applyType)) {
                    try {
                        int startTimeInt = Integer.parseInt(startTime.replace(":", ""));
                        boolean isValidTime = attendanceApplyMapper.isValidEarlyOvertimeTime(startTimeInt);
                        if (!isValidTime) {
                            result.put("totalWeeklyHours", baseWeeklyHours);
                            result.put("requestHours", 0.0);
                            result.put("isValid", false);
                            result.put("message", "조출연장은 07:30 이전에만 신청할 수 있습니다.");
                            return result;
                        }
                    } catch (NumberFormatException e) {
                        log.error("조출연장 시간 파싱 실패: {}", startTime, e);
                    }
                }

                // 정확한 시간 계산
                requestHours = calculateRequestHours(empCode, workDate, startTime, endTime, applyType);
            }

            // 요구사항: 조퇴/외출/반차는 차감
            if (Arrays.asList("조퇴", "외근", "외출", "전반차", "후반차").contains(applyType)) {
                if ("전반차".equals(applyType) || "후반차".equals(applyType)) {
                    requestHours = -4.0; // 반차는 4시간 차감
                } else if ("조퇴".equals(applyType)) {
                    // 조퇴는 시작시간부터 퇴근시간까지 차감
                    requestHours = calculateEarlyLeaveHours(empCode, workDate, startTime);
                    requestHours = -requestHours;
                } else {
                    requestHours = -requestHours; // 외출은 해당 시간만큼 차감
                }
            }

            double totalWeeklyHours = baseWeeklyHours + requestHours;
            boolean isValid = totalWeeklyHours <= 52.0 && totalWeeklyHours >= 0;

            result.put("totalWeeklyHours", totalWeeklyHours);
            result.put("requestHours", Math.abs(requestHours));
            result.put("isValid", isValid);
            result.put("message", isValid ? "정상" : (totalWeeklyHours > 52.0 ? "주 52시간 초과" : "음수 시간"));

            log.debug("실시간 주 52시간 계산 완료: baseHours={}, requestHours={}, totalHours={}, isValid={}",
                    baseWeeklyHours, requestHours, totalWeeklyHours, isValid);

        } catch (Exception e) {
            log.error("실시간 주 52시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            result.put("totalWeeklyHours", 40.0);
            result.put("requestHours", 0.0);
            result.put("isValid", true);
            result.put("message", "계산 오류");
        }

        return result;
    }

    /**
     * 현재 주간 근무시간 계산 (휴일근로 포함) - 안정화 버전
     */
    private double calculateCurrentWeeklyHoursStable(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate mondayOfWeek = targetDate.with(DayOfWeek.MONDAY);
            LocalDate sundayOfWeek = targetDate.with(DayOfWeek.SUNDAY);

            Duration totalWeekHours = Duration.ZERO;

            log.debug("안정화된 현재 주간 근무시간 계산: empCode={}, 주간={} ~ {}", empCode, mondayOfWeek, sundayOfWeek);

            // 주중 7일간 계산
            for (LocalDate date = mondayOfWeek; !date.isAfter(sundayOfWeek); date = date.plusDays(1)) {
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String dailyHours = calculateDailyExpectedHoursImproved(empCode, dateStr);
                try {
                    Duration dayDuration = Duration.ofMinutes((long)(Double.parseDouble(dailyHours) * 60));
                    totalWeekHours = totalWeekHours.plus(dayDuration);
                    log.debug("일별 시간 누적 (안정화): date={}, dailyHours={}, 누적시간={}",
                            dateStr, dailyHours, totalWeekHours.toMinutes() / 60.0);
                } catch (NumberFormatException e) {
                    log.warn("일별 시간 파싱 실패 (안정화): date={}, dailyHours={}", dateStr, dailyHours);
                    // 파싱 실패 시 0시간으로 처리하여 계속 진행
                }
            }

            double weeklyHours = totalWeekHours.toMinutes() / 60.0;
            log.debug("안정화된 현재 주간 근무시간 계산 완료: empCode={}, totalHours={}", empCode, weeklyHours);

            return weeklyHours;
        } catch (Exception e) {
            log.error("안정화된 현재 주간 근무시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            return 40.0; // 기본값
        }
    }

    /**
     * 현재 주간 근무시간 계산 (휴일근로 포함)
     */
    private double calculateCurrentWeeklyHours(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate mondayOfWeek = targetDate.with(DayOfWeek.MONDAY);
            LocalDate sundayOfWeek = targetDate.with(DayOfWeek.SUNDAY);

            Duration totalWeekHours = Duration.ZERO;

            // 주중 7일간 계산
            for (LocalDate date = mondayOfWeek; !date.isAfter(sundayOfWeek); date = date.plusDays(1)) {
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                String dailyHours = calculateDailyExpectedHoursImproved(empCode, dateStr);
                Duration dayDuration = Duration.ofMinutes((long)(Double.parseDouble(dailyHours) * 60));
                totalWeekHours = totalWeekHours.plus(dayDuration);
            }

            return totalWeekHours.toMinutes() / 60.0;
        } catch (Exception e) {
            log.error("현재 주간 근무시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            return 40.0; // 기본값
        }
    }

    /**
     * 신청 시간 정확 계산
     */
    private double calculateRequestHours(String empCode, String workDate, String startTime, String endTime, String applyType) {
        try {
            String originalShiftCode = getOriginalShiftCode(empCode, workDate);
            if (originalShiftCode == null) {
                originalShiftCode = "05"; // 기본 주간 근무
            }

            ShiftMaster shift = shiftMasterMapper.findShiftByCode(originalShiftCode);
            if (shift != null && "휴일근무".equals(applyType)) {
                // 휴일근무는 별도 시프트 사용
                shift = shiftMasterMapper.findShiftByCode("14-1");
            }

            if (shift != null) {
                LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

                // 시간 포맷 변환 (HH:MM -> HHMMSS)
                String formattedStartTime = startTime.replace(":", "") + "00";
                String formattedEndTime = endTime.replace(":", "") + "00";

                // WorkHoursCalculator.getRealWorkTime 사용 (빈 leavePeriods)
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

    private double calculateEarlyLeaveHours(String empCode, String workDate, String startTime) {
        try {
            // 해당 날짜의 계획 조회
            String originalShiftCode = getOriginalShiftCode(empCode, workDate);
            if (originalShiftCode == null) {
                originalShiftCode = "05"; // 기본 주간 근무
            }

            ShiftMaster shift = shiftMasterMapper.findShiftByCode(originalShiftCode);
            if (shift != null) {
                // 정상 퇴근시간 조회
                String workOffHhmm = shift.getWorkOffHhmm();
                if (workOffHhmm != null) {
                    // 시작시간부터 퇴근시간까지 계산
                    String[] startParts = startTime.split(":");
                    int startMinutes = Integer.parseInt(startParts[0]) * 60 + Integer.parseInt(startParts[1]);

                    int workOffTime = Integer.parseInt(workOffHhmm);
                    int workOffHour = workOffTime / 100;
                    int workOffMin = workOffTime % 100;
                    int workOffMinutes = workOffHour * 60 + workOffMin;

                    if (workOffMinutes > startMinutes) {
                        return (workOffMinutes - startMinutes) / 60.0;
                    }
                }
            }

            // 기본값: 2시간
            return 2.0;
        } catch (Exception e) {
            log.error("조퇴 시간 계산 실패", e);
            return 2.0;
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

            // 각 사원의 기존 신청 내역 및 실적 정보 조회
            for (Employee emp : employees) {
                // 일반근태 신청 내역 조회
                AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(emp.getEmpCode(), workDate);
                if (generalApply != null) {
                    emp.setApplyGeneralNo(generalApply.getApplyGeneralNo());
                    emp.setGeneralApplyStatus(generalApply.getStatus());
                }

                // 기타근태 신청 내역 조회
                AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(emp.getEmpCode(), workDate);
                if (etcApply != null) {
                    emp.setApplyEtcNo(etcApply.getApplyEtcNo());
                    emp.setEtcApplyStatus(etcApply.getStatus());
                }

                // 실적 정보
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

    // 🔧 수정: 근태신청종류별 사원 조회 - 신청근무별 완전 분리 처리
    public List<Employee> getEmployeesByDeptWithApplyType(String deptCode, String workDate, String workPlan, String sortBy, String applyTypeCategory) {
        try {
            log.debug("부서별 사원 조회 (근태신청종류별) 시작: deptCode={}, workDate={}, applyTypeCategory={}", deptCode, workDate, applyTypeCategory);

            List<Employee> employees = attendanceApplyMapper.findEmployeesByDeptWithSort(deptCode, workDate, workPlan, sortBy);

            log.debug("조회된 사원 수: {}", employees.size());

            // 각 사원의 기존 신청 내역 및 실적 정보 조회
            for (Employee emp : employees) {
                // 🔧 수정: 근태신청종류별 일반근태 신청 내역 조회 - 완전 분리
                AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDateWithCategory(emp.getEmpCode(), workDate, applyTypeCategory);
                if (generalApply != null && !"삭제".equals(generalApply.getStatus())) {
                    emp.setApplyGeneralNo(generalApply.getApplyGeneralNo());
                    emp.setGeneralApplyStatus(generalApply.getStatus());
                    log.debug("신청근무별 조회 (완전 분리): empCode={}, applyType={}, status={}",
                            emp.getEmpCode(), generalApply.getApplyType(), generalApply.getStatus());
                } else {
                    emp.setApplyGeneralNo("");
                    emp.setGeneralApplyStatus("대기");
                    log.debug("신청근무별 조회 - 기존 신청 없음: empCode={}, category={}", emp.getEmpCode(), applyTypeCategory);
                }

                // 기타근태 신청 내역 조회
                AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByEmpAndDate(emp.getEmpCode(), workDate);
                if (etcApply != null) {
                    emp.setApplyEtcNo(etcApply.getApplyEtcNo());
                    emp.setEtcApplyStatus(etcApply.getStatus());
                }

                // 실적 정보
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

            // 기존 신청 내역 및 실적 정보 조회
            for (Employee emp : employees) {
                // 일반근태 신청 내역 조회
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

                // 실적 정보
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

            // 기존 신청 내역 및 실적 정보 조회
            for (Employee emp : employees) {
                // 근태신청종류별 일반근태 신청 내역 조회
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

                // 실적 정보
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

    // 🔧 수정: 일반근태 신청 유효성 검증 - 휴일근무 8시간 검증 강화
    public String validateGeneralApply(AttendanceApplyGeneral apply) {
        try {
            String empCode = apply.getEmpCode();
            String targetDate = apply.getTargetDate();
            String applyType = apply.getApplyType();

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

            // 🔧 수정: 연장근로 검증 강화 - 휴일근무 8시간 이상 필수 체크
            if ("연장".equals(applyType) || "조출연장".equals(applyType)) {
                // 실적이 결근일 경우 신청 불가
                if ("결근".equals(actualRecord)) {
                    return "실적이 결근일 경우 연장근무를 신청할 수 없습니다.";
                }

                // 🔧 강화: 실적이 휴일근무일 경우 8시간 이상 신청 여부 필수 확인
                if ("휴일근무".equals(actualRecord)) {
                    // 해당 날짜에 휴일근무 신청이 있는지 먼저 확인
                    AttendanceApplyGeneral holidayApply = attendanceApplyMapper.findGeneralApplyByEmpAndDate(empCode, targetDate);
                    if (holidayApply != null && "휴일근무".equals(holidayApply.getApplyType()) &&
                            ("승인완료".equals(holidayApply.getStatus()) || "상신".equals(holidayApply.getStatus()))) {

                        // 🔧 강화: 정확한 8시간 이상 검증
                        if (holidayApply.getStartTime() != null && holidayApply.getEndTime() != null) {
                            try {
                                int startTime = Integer.parseInt(holidayApply.getStartTime());
                                int endTime = Integer.parseInt(holidayApply.getEndTime());

                                // 시간 계산 (분 단위로 정확히)
                                int startMinutes = (startTime / 100) * 60 + (startTime % 100);
                                int endMinutes = (endTime / 100) * 60 + (endTime % 100);
                                int workMinutes = endMinutes - startMinutes;

                                // 자정 넘어가는 경우 처리
                                if (workMinutes <= 0) {
                                    workMinutes += 24 * 60; // 24시간 추가
                                }

                                log.debug("휴일근무 8시간 검증 (강화): startTime={}, endTime={}, workMinutes={}",
                                        startTime, endTime, workMinutes);

                                if (workMinutes < 480) { // 8시간 = 480분
                                    return "휴일근무 8시간 이상 신청한 경우에만 연장근무를 신청할 수 있습니다.";
                                }

                                log.debug("휴일근무 8시간 이상 검증 통과 (강화): empCode={}, workMinutes={}", empCode, workMinutes);
                            } catch (NumberFormatException e) {
                                log.warn("휴일근무 시간 파싱 실패: startTime={}, endTime={}", holidayApply.getStartTime(), holidayApply.getEndTime());
                                return "휴일근무 시간 정보가 올바르지 않습니다.";
                            }
                        } else {
                            return "휴일근무 시간 정보가 없습니다.";
                        }
                    } else {
                        return "휴일근무 신청이 없거나 승인되지 않았습니다.";
                    }
                }

                // 해당 일에 연차, 휴가, 반차, 조퇴 신청이 있는지 확인
                if ("연장".equals(applyType)) {
                    // 일반 연장: 연차, 휴가, 반차, 조퇴 확인
                    boolean hasAnnualOrVacation = attendanceApplyMapper.hasAnnualOrVacationApply(empCode, targetDate);
                    boolean hasHalfDayOrEarlyLeave = attendanceApplyMapper.hasHalfDayOrEarlyLeaveApply(empCode, targetDate);

                    if (hasAnnualOrVacation || hasHalfDayOrEarlyLeave) {
                        return "해당일에 연차, 휴가, 반차, 조퇴 신청이 있어 일반 연장근무를 신청할 수 없습니다.";
                    }
                } else if ("조출연장".equals(applyType)) {
                    // 조출연장: 연차, 휴가만 확인
                    boolean hasAnnualOrVacation = attendanceApplyMapper.hasAnnualOrVacationApply(empCode, targetDate);

                    if (hasAnnualOrVacation) {
                        return "해당일에 연차, 휴가 신청이 있어 조출연장을 신청할 수 없습니다.";
                    }
                }
            }

            // 휴일근로 검증
            if ("휴일근무".equals(applyType)) {
                // 휴일 또는 휴무일에만 신청 가능
                if (!"휴일".equals(planShiftName) && !"휴무일".equals(planShiftName)) {
                    return "휴일근로는 휴일 또는 휴무일에만 신청할 수 있습니다.";
                }

                // 연차, 휴가, 결근 등의 날은 신청 불가
                if ("연차".equals(actualRecord) || "휴가".equals(actualRecord) || "결근".equals(actualRecord)) {
                    return "연차, 휴가, 결근 등의 날에는 휴일근로를 신청할 수 없습니다.";
                }
            }

            // 조퇴/외출/반차 검증 (일반근태)
            if (Arrays.asList("조퇴", "외근", "외출", "전반차", "후반차").contains(applyType)) {
                // 정상 근무가 아닌 경우 신청 불가
                if (Arrays.asList("결근", "연차", "휴가", "휴일", "휴직").contains(actualRecord)) {
                    return "정상 근무가 아닌 경우에는 " + applyType + "을(를) 신청할 수 없습니다.";
                }

                // 해당일, 해당시간에 중복되어 근태 신청 불가능
                if (apply.getStartTime() != null && apply.getEndTime() != null) {
                    boolean hasTimeOverlap = attendanceApplyMapper.hasTimeOverlap(
                            empCode, targetDate, apply.getStartTime(), apply.getEndTime());
                    if (hasTimeOverlap) {
                        return "해당일 해당시간에 중복되는 근태 신청이 있습니다.";
                    }
                }
            }

            // 시간 검증 - NumberFormatException 처리
            if (apply.getStartTime() != null && apply.getEndTime() != null &&
                    !apply.getStartTime().trim().isEmpty() && !apply.getEndTime().trim().isEmpty()) {
                try {
                    int startTime = Integer.parseInt(apply.getStartTime().trim());
                    int endTime = Integer.parseInt(apply.getEndTime().trim());

                    if (startTime >= endTime) {
                        return "시작시간이 종료시간보다 늦을 수 없습니다.";
                    }

                    // 정상근무시간 연장 신청 제한 검증
                    if ("연장".equals(applyType)) {
                        if (startTime < 1620) {
                            return "정상근무시간(16:20) 이후에만 연장근무를 신청할 수 있습니다.";
                        }
                    } else if ("조출연장".equals(applyType)) {
                        // 요구사항: 조출연장은 근무시간(07:30) 이전에만 신청 가능
                        boolean isValidTime = attendanceApplyMapper.isValidEarlyOvertimeTime(startTime);
                        if (!isValidTime) {
                            return "조출연장은 07:30 이전에만 신청할 수 있습니다.";
                        }
                    }
                } catch (NumberFormatException e) {
                    log.warn("시간 검증 중 파싱 실패: startTime={}, endTime={}", apply.getStartTime(), apply.getEndTime());
                    return "시간 형식이 올바르지 않습니다.";
                }
            }

            // 주 52시간 초과 검증
            if (!Arrays.asList("조퇴", "외근", "외출", "전반차", "후반차").contains(applyType)) {
                String weeklyHours = calculateWeeklyExpectedHours(apply.getEmpCode(), apply.getTargetDate());
                double currentWeekHours = Double.parseDouble(weeklyHours);

                // 신청 시간 계산
                Duration applyHours = calculateApplyHours(apply);
                double applyHoursDecimal = applyHours.toMinutes() / 60.0;

                if (currentWeekHours + applyHoursDecimal > 52.0) {
                    return "주 52시간을 초과할 수 없습니다. (현재: " + String.format("%.2f", currentWeekHours) + "시간)";
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

    // 기타근태 신청 유효성 검증
    public String validateEtcApply(AttendanceApplyEtc apply) {
        try {
            // 날짜 검증
            int startDate = Integer.parseInt(apply.getTargetStartDate());
            int endDate = Integer.parseInt(apply.getTargetEndDate());

            if (startDate > endDate) {
                return "시작일이 종료일보다 늦을 수 없습니다.";
            }

            // 휴일/휴무일 포함 여부 검증 (연차 신청이 아닌 경우)
            if (apply.getShiftCode() != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(apply.getShiftCode());
                if (shift != null && !"연차".equals(shift.getShiftName())) {
                    if (!validateDateRange(apply.getTargetStartDate(), apply.getTargetEndDate())) {
                        return "신청 기간에 휴일/휴무일이 포함되어 있습니다.";
                    }
                }
            }

            // 중복 신청 검증
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

    // 일반근태 신청 저장
    @Transactional
    public void saveGeneralApply(AttendanceApplyGeneral apply) {
        try {
            // 밀리초를 포함한 유니크한 신청번호 생성
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String applyNo = "GEN" + timestamp;
            apply.setApplyGeneralNo(applyNo);

            // 부서코드 설정 - 신청대상자의 부서코드로 설정
            Employee targetEmp = attendanceApplyMapper.findEmployeeByEmpCode(apply.getEmpCode());
            apply.setDeptCode(targetEmp.getDeptCode());

            log.debug("일반근태 저장: applyNo={}, empCode={}, timeItemCode={}",
                    applyNo, apply.getEmpCode(), apply.getTimeItemCode());
            attendanceApplyMapper.insertGeneralApply(apply);

            // 🔧 수정: 휴일근무 저장 시 예상근로시간 캐시 초기화
            if ("휴일근무".equals(apply.getApplyType())) {
                clearExpectedHoursCache(apply.getEmpCode(), apply.getTargetDate());
                log.debug("휴일근무 저장으로 예상근로시간 캐시 초기화: empCode={}, date={}", apply.getEmpCode(), apply.getTargetDate());
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
            // 밀리초를 포함한 유니크한 신청번호 생성
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
            String applyNo = "ETC" + timestamp;
            apply.setApplyEtcNo(applyNo);

            // 부서코드 설정
            Employee targetEmp = attendanceApplyMapper.findEmployeeByEmpCode(apply.getEmpCode());
            apply.setDeptCode(targetEmp.getDeptCode());

            log.debug("기타근태 저장: applyNo={}, empCode={}", applyNo, apply.getEmpCode());
            attendanceApplyMapper.insertEtcApply(apply);
        } catch (Exception e) {
            log.error("기타근태 저장 실패", e);
            throw new RuntimeException("기타근태 저장에 실패했습니다.", e);
        }
    }

    // 🔧 추가: 예상근로시간 캐시 초기화
    private void clearExpectedHoursCache(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate mondayOfWeek = targetDate.with(DayOfWeek.MONDAY);
            String cacheKey = empCode + "_" + mondayOfWeek.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            expectedHoursCache.remove(cacheKey);

            // 🔧 신청근무별 캐시도 초기화
            workTypeSpecificCache.entrySet().removeIf(entry -> entry.getKey().startsWith(empCode + "_" + workDate));

            log.debug("예상근로시간 캐시 초기화 (울트라): cacheKey={}", cacheKey);
        } catch (Exception e) {
            log.error("예상근로시간 캐시 초기화 실패: empCode={}, workDate={}", empCode, workDate, e);
        }
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

    // ⚠️ 수정됨: 휴일근무 승인완료 시 SHIFT_CODE만 업데이트 (HRTATTRECORD 테이블 업데이트 제거)
    @Transactional
    public void updateWorkRecordForHolidayWork(String empCode, String workDate) {
        try {
            log.debug("휴일근로 실적 업데이트 시작: empCode={}, workDate={}", empCode, workDate);

            // ❌ 제거됨: attendanceApplyMapper.updateAttendanceRecordByShiftCode(empCode, workDate, holidayShiftCode);
            // HRTATTRECORD 테이블에는 SHIFT_CODE 컬럼이 없으므로 해당 호출 제거

            // ✅ 유지: HRTWORKEMPCALENDAR 테이블의 SHIFT_CODE만 업데이트
            attendanceApplyMapper.updateShiftCodeAfterGeneralApproval(empCode, workDate, "휴일근무");

            // 🔧 추가: 휴일근무 승인완료 시 예상근로시간 캐시 초기화
            clearExpectedHoursCache(empCode, workDate);

            log.debug("휴일근로 SHIFT_CODE 업데이트 완료: empCode={}, workDate={}, shiftCode=14-1", empCode, workDate);
        } catch (Exception e) {
            log.error("휴일근로 실적 업데이트 실패: empCode={}, workDate={}", empCode, workDate, e);
            // 실적 업데이트 실패는 로그만 남기고 예외를 던지지 않음
        }
    }

    // ⚠️ 수정됨: 기타근태 승인완료 시 SHIFT_CODE만 업데이트 (HRTATTRECORD 테이블 업데이트 제거)
    @Transactional
    public void updateWorkRecordForAnnualLeave(String empCode, String workDate, String shiftCode) {
        try {
            log.debug("연차/반차 실적 업데이트 시작: empCode={}, workDate={}, shiftCode={}", empCode, workDate, shiftCode);

            // ❌ 제거됨: attendanceApplyMapper.updateAttendanceRecordByShiftCode(empCode, workDate, shiftCode);
            // HRTATTRECORD 테이블에는 SHIFT_CODE 컬럼이 없으므로 해당 호출 제거

            ShiftMaster shift = shiftMasterMapper.findShiftByCode(shiftCode);
            if (shift != null) {
                // ✅ 유지: HRTWORKEMPCALENDAR 테이블의 SHIFT_CODE만 업데이트
                attendanceApplyMapper.updateShiftCodeAfterEtcApproval(empCode, workDate, workDate, shiftCode);
            }

            log.debug("연차/반차 SHIFT_CODE 업데이트 완료: empCode={}, workDate={}, shiftCode={}", empCode, workDate, shiftCode);
        } catch (Exception e) {
            log.error("연차/반차 실적 업데이트 실패: empCode={}, workDate={}, shiftCode={}", empCode, workDate, shiftCode, e);
            // 실적 업데이트 실패는 로그만 남기고 예외를 던지지 않음
        }
    }

    // 🔧 수정: 신청근무별 분리 관리 - 완전 분리 처리
    public Map<String, Object> getApplyByWorkType(String empCode, String workDate, String applyType) {
        Map<String, Object> result = new HashMap<>();
        try {
            log.debug("신청근무별 완전 분리 조회: empCode={}, workDate={}, applyType={}", empCode, workDate, applyType);

            // 🔧 수정: 매퍼 메서드 사용하여 해당 신청근무 유형만 조회 - 완전 분리
            AttendanceApplyGeneral existingApply = attendanceApplyMapper.findGeneralApplyByEmpAndDateAndType(empCode, workDate, applyType);

            if (existingApply != null && !"삭제".equals(existingApply.getStatus())) {
                // 동일한 신청근무 유형의 기존 신청이 있는 경우
                result.put("hasExisting", true);
                result.put("applyType", "general");
                result.put("applyNo", existingApply.getApplyGeneralNo());
                result.put("status", existingApply.getStatus());
                result.put("startTime", existingApply.getStartTime());
                result.put("endTime", existingApply.getEndTime());
                result.put("reason", existingApply.getReason());

                log.debug("기존 신청 발견 (완전 분리): applyNo={}, status={}, applyType={}",
                        existingApply.getApplyGeneralNo(), existingApply.getStatus(), applyType);
            } else {
                // 해당 신청근무 유형의 기존 신청이 없는 경우 - 신청 가능한 상태
                result.put("hasExisting", false);
                result.put("applyType", "general");
                result.put("status", "대기");
                result.put("startTime", "");
                result.put("endTime", "");
                result.put("reason", "");

                log.debug("기존 신청 없음 - 신청 가능한 상태 (완전 분리): applyType={}", applyType);
            }

            return result;
        } catch (Exception e) {
            log.error("신청근무별 기존 신청 조회 실패: empCode={}, workDate={}, applyType={}", empCode, workDate, applyType, e);
            result.put("hasExisting", false);
            result.put("message", "조회 중 오류가 발생했습니다.");
            return result;
        }
    }

    // 🔧 수정: 예상근로시간 업데이트 메서드 - 캐시 활용
    public Map<String, Object> updateExpectedHours(String empCode, String workDate) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 🔧 캐시 시스템을 활용한 예상근로시간 계산
            String expectedHours = calculateWeeklyExpectedHoursUltraStable(empCode, workDate);

            result.put("success", true);
            result.put("expectedHours", expectedHours);
            result.put("message", "예상근로시간 업데이트 완료");

            log.debug("예상근로시간 업데이트: empCode={}, workDate={}, expectedHours={}", empCode, workDate, expectedHours);

            return result;
        } catch (Exception e) {
            log.error("예상근로시간 업데이트 실패: empCode={}, workDate={}", empCode, workDate, e);
            result.put("success", false);
            result.put("expectedHours", "40.00");
            result.put("message", "예상근로시간 계산 실패");
            return result;
        }
    }

    // 🔧 수정: 조출연장 시간 검증 - 서버측 검증 강화
    public Map<String, Object> validateEarlyOvertimeTime(String startTime) {
        Map<String, Object> result = new HashMap<>();
        try {
            int startTimeInt = Integer.parseInt(startTime.replace(":", ""));
            boolean isValid = attendanceApplyMapper.isValidEarlyOvertimeTime(startTimeInt);

            result.put("isValid", isValid);
            result.put("message", isValid ? "정상" : "조출연장은 07:30 이전에만 신청할 수 있습니다.");

            log.debug("조출연장 시간 검증 (서버): startTime={}, isValid={}", startTime, isValid);

            return result;
        } catch (Exception e) {
            log.error("조출연장 시간 검증 실패: startTime={}", startTime, e);
            result.put("isValid", false);
            result.put("message", "시간 검증 중 오류가 발생했습니다.");
            return result;
        }
    }

    // 🔧 수정: 일반연장 시간 검증 - 서버측 검증 강화
    public Map<String, Object> validateRegularOvertimeTime(String startTime) {
        Map<String, Object> result = new HashMap<>();
        try {
            int startTimeInt = Integer.parseInt(startTime.replace(":", ""));
            boolean isValid = startTimeInt >= 1620; // 16:20 이후만 가능

            result.put("isValid", isValid);
            result.put("message", isValid ? "정상" : "정상근무시간(16:20) 이후에만 연장근무를 신청할 수 있습니다.");

            log.debug("일반연장 시간 검증 (서버): startTime={}, isValid={}", startTime, isValid);

            return result;
        } catch (Exception e) {
            log.error("일반연장 시간 검증 실패: startTime={}", startTime, e);
            result.put("isValid", false);
            result.put("message", "시간 검증 중 오류가 발생했습니다.");
            return result;
        }
    }

    // 요구사항: 전반차/후반차 시간 입력 차단
    public Map<String, Object> validateHalfDayTimeInput(String applyType) {
        Map<String, Object> result = new HashMap<>();
        try {
            boolean timeInputDisabled = "전반차".equals(applyType) || "후반차".equals(applyType);
            String message = timeInputDisabled ? "반차는 시간을 입력할 수 없습니다." : "정상";

            result.put("timeInputDisabled", timeInputDisabled);
            result.put("message", message);
            result.put("deductHours", timeInputDisabled ? 4.0 : 0.0); // 반차는 4시간 차감

            log.debug("반차 시간 입력 제한 검증: applyType={}, disabled={}", applyType, timeInputDisabled);

            return result;
        } catch (Exception e) {
            log.error("반차 검증 실패: applyType={}", applyType, e);
            result.put("timeInputDisabled", false);
            result.put("message", "검증 중 오류가 발생했습니다.");
            return result;
        }
    }

    // 요구사항: 조퇴 종료시간 입력 차단
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

    // 일반근태 신청 상신
    @Transactional
    public void submitGeneralApply(String applyGeneralNo, String applicantCode, String isHeader) {
        try {
            log.debug("일반근태 상신 시작: applyGeneralNo={}, applicantCode={}, isHeader={}", applyGeneralNo, applicantCode, isHeader);

            AttendanceApplyGeneral apply = attendanceApplyMapper.findGeneralApplyByNo(applyGeneralNo);

            // 전반차, 후반차 승인완료 시 연차 0.5 차감 처리
            if (apply != null && Arrays.asList("전반차", "후반차").contains(apply.getApplyType())) {
                if ("Y".equals(isHeader)) {
                    // 🔧 수정: 연차 차감 로직 개선 - 정확한 계산
                    BigDecimal deductDays = new BigDecimal("0.5");
                    AnnualDetail currentAnnual = annualDetailMapper.findByEmpCode(apply.getEmpCode());
                    if (currentAnnual != null) {
                        BigDecimal currentBalance = currentAnnual.getBalanceDay();
                        log.debug("전반차/후반차 연차 차감 전: empCode={}, 현재잔여={}, 차감예정={}",
                                apply.getEmpCode(), currentBalance, deductDays);

                        boolean deductionResult = annualDetailMapper.updateBalanceDayWithCheck(apply.getEmpCode(), deductDays);
                        if (deductionResult) {
                            annualDetailMapper.updateUseDayIncrease(apply.getEmpCode(), deductDays);

                            // 차감 후 확인
                            AnnualDetail updatedAnnual = annualDetailMapper.findByEmpCode(apply.getEmpCode());
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
                }

                // 승인완료 시 실적 업데이트
                updateAttendanceRecord(applyGeneralNo, "general");

                log.debug("부서장 일반근태 자동 승인완료: applyGeneralNo={}", applyGeneralNo);
            } else {
                // 일반 사원인 경우 상신 처리
                attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "상신");

                // 결재 이력 생성
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
                String approvalNo = "APR" + timestamp;

                // 신청자의 부서장 정보 조회
                String deptCode = attendanceApplyMapper.getDeptCodeByGeneralApplyNo(applyGeneralNo);
                if (deptCode == null || deptCode.trim().isEmpty()) {
                    throw new RuntimeException("신청의 부서코드를 찾을 수 없습니다.");
                }

                String approverCode = attendanceApplyMapper.getDeptLeaderByDeptCode(deptCode);
                if (approverCode == null || approverCode.trim().isEmpty()) {
                    throw new RuntimeException("부서장 정보를 찾을 수 없습니다. 부서코드: " + deptCode);
                }

                log.debug("결재자 정보: deptCode={}, approverCode={}", deptCode, approverCode);

                // 결재 이력 생성
                attendanceApplyMapper.insertGeneralApprovalHistory(approvalNo, applyGeneralNo, approverCode);
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

                // 🔧 수정: 연차 차감 및 실적 업데이트 - 정확한 계산
                deductAnnualLeaveUltraImproved(etcApply);
                updateAttendanceRecord(applyEtcNo, "etc");

                log.debug("부서장 기타근태 자동 승인완료: applyEtcNo={}", applyEtcNo);
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
                attendanceApplyMapper.insertEtcApprovalHistory(approvalNo, applyEtcNo, approverCode);
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

    // 🔧 수정: 연차 차감 로직 울트라 개선 - 조회 시에도 정확한 계산 보장
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
                        // 🔧 울트라 개선: 차감 전 잔여량 확인 및 로그
                        AnnualDetail currentAnnual = annualDetailMapper.findByEmpCode(etcApply.getEmpCode());
                        if (currentAnnual != null) {
                            BigDecimal currentBalance = currentAnnual.getBalanceDay();
                            BigDecimal currentUse = currentAnnual.getUseDay();

                            log.debug("연차 차감 전 상태 (울트라): empCode={}, 현재잔여={}, 현재사용={}, 차감예정={}",
                                    etcApply.getEmpCode(), currentBalance, currentUse, deductDays);

                            // 🔧 울트라 개선: 트랜잭션 내에서 정확한 차감 처리
                            boolean deductionResult = annualDetailMapper.updateBalanceDayWithCheckUltra(
                                    etcApply.getEmpCode(), deductDays);

                            if (deductionResult) {
                                // 🔧 울트라 개선: 정확한 USE_DAY 증가
                                annualDetailMapper.updateUseDayIncreaseUltra(etcApply.getEmpCode(), deductDays);

                                // 🔧 울트라 개선: 차감 후 즉시 재조회하여 정확성 검증
                                AnnualDetail updatedAnnual = annualDetailMapper.findByEmpCodeForceRefresh(etcApply.getEmpCode());
                                if (updatedAnnual != null) {
                                    log.debug("연차 차감 및 USE_DAY 증가 완료 (울트라): empCode={}, 차감일수={}, 차감후잔여={}, 차감후사용={}",
                                            etcApply.getEmpCode(), deductDays,
                                            updatedAnnual.getBalanceDay(), updatedAnnual.getUseDay());

                                    // 🔧 울트라 검증: 계산 정확성 재확인
                                    BigDecimal expectedBalance = currentBalance.subtract(deductDays);
                                    BigDecimal expectedUse = currentUse.add(deductDays);

                                    if (updatedAnnual.getBalanceDay().compareTo(expectedBalance) != 0) {
                                        log.error("연차 차감 계산 오류 (울트라): 예상잔여={}, 실제잔여={}", expectedBalance, updatedAnnual.getBalanceDay());
                                        // 🔧 울트라 수정: 계산 오류 발생 시 재계산 수행
                                        annualDetailMapper.forceRecalculateAnnual(etcApply.getEmpCode(), expectedBalance, expectedUse);
                                    }
                                    if (updatedAnnual.getUseDay().compareTo(expectedUse) != 0) {
                                        log.error("연차 사용 계산 오류 (울트라): 예상사용={}, 실제사용={}", expectedUse, updatedAnnual.getUseDay());
                                        // 🔧 울트라 수정: 계산 오류 발생 시 재계산 수행
                                        annualDetailMapper.forceRecalculateAnnual(etcApply.getEmpCode(), expectedBalance, expectedUse);
                                    }
                                }
                            } else {
                                log.warn("연차 잔여량 부족으로 차감 실패 (울트라): empCode={}, 요청차감일수={}, 현재잔여={}",
                                        etcApply.getEmpCode(), deductDays, currentBalance);
                            }
                        } else {
                            log.error("연차 정보 조회 실패 (울트라): empCode={}", etcApply.getEmpCode());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("연차 차감 실패 (울트라): etcApply={}", etcApply, e);
            throw new RuntimeException("연차 차감에 실패했습니다.", e);
        }
    }

    // 기존 deductAnnualLeaveStable 메서드도 유지 (하위호환성)
    @Transactional
    private void deductAnnualLeaveStable(AttendanceApplyEtc etcApply) {
        deductAnnualLeaveUltraImproved(etcApply);
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