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

    // 수정: 하위부서 목록 조회 - EmpAttService 로직 적용
    public List<Department> getSubDepartments(String parentDeptCode) {
        try {
            log.debug("하위부서 조회 시작: parentDeptCode={}", parentDeptCode);

            // 수정: 전체 부서 구조를 기반으로 재귀적으로 하위부서 조회
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

    // 수정: 재귀적으로 모든 하위부서를 찾는 헬퍼 메서드 추가
    private void findAllSubDepartments(String parentDeptCode, List<Department> allDepartments, List<Department> result) {
        for (Department dept : allDepartments) {
            if (parentDeptCode.equals(dept.getParentDept()) && !isAlreadyInResult(dept.getDeptCode(), result)) {
                result.add(dept);
                // 재귀적으로 이 부서의 하위부서도 찾기
                findAllSubDepartments(dept.getDeptCode(), allDepartments, result);
            }
        }
    }

    // 수정: 중복 부서 체크 헬퍼 메서드 추가
    private boolean isAlreadyInResult(String deptCode, List<Department> result) {
        return result.stream().anyMatch(dept -> deptCode.equals(dept.getDeptCode()));
    }

    // 연차잔여 정보 조회
    public AnnualDetail getAnnualDetail(String empCode) {
        try {
            return annualDetailMapper.findByEmpCode(empCode);
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

    // 수정: 사원이 결근인지 확인하는 메서드 - 미래 날짜 결근 체크 개선
    public boolean isEmployeeAbsent(String empCode, String workDate) {
        try {
            // 수정: 미래 날짜는 결근 체크하지 않음
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate today = LocalDate.now();

            if (targetDate.isAfter(today)) {
                log.debug("미래 날짜는 결근 체크하지 않음: empCode={}, workDate={}", empCode, workDate);
                return false;
            }

            // 실적 조회
            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, workDate);

            // 출근 기록이 없는 경우 계획 확인
            if (attRecord == null || attRecord.getCheckInTime() == null) {
                EmpCalendar empCalendar = empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, workDate);
                if (empCalendar != null && empCalendar.getShiftCode() != null) {
                    String planShiftName = shiftMasterMapper.findShiftNameByShiftCode(empCalendar.getShiftCode());

                    // 휴무일/휴일이 아닌 근무일인데 출근 기록이 없으면 결근
                    if (!"휴무일".equals(planShiftName) && !"휴일".equals(planShiftName)) {
                        log.debug("결근 판정: empCode={}, workDate={}, plan={}", empCode, workDate, planShiftName);
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            log.error("결근 확인 실패: empCode={}, workDate={}", empCode, workDate, e);
            return false;
        }
    }

    // 수정: 기타근태 날짜 범위 검증 메서드 추가
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

    // *** 수정: 실적 표시 로직 개선 - 휴무일/휴일 처리 및 미래 날짜 처리 ***
    // 실적 표시
    public Map<String, Object> getWorkInfo(String empCode, String workDate) {
        Map<String, Object> workInfo = new HashMap<>();

        try {
            // 계획 조회
            EmpCalendar empCalendar = empCalendarMapper.getCodeAndHolidayByEmpCodeAndDate(empCode, workDate);
            String planShiftCode = empCalendar != null ? empCalendar.getShiftCode() : null;
            String planShiftName = "";
            if (planShiftCode != null) {
                planShiftName = shiftMasterMapper.findShiftNameByShiftCode(planShiftCode);
            }

            // 미래 날짜 체크
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));
            LocalDate today = LocalDate.now();
            boolean isFutureDate = targetDate.isAfter(today);

            // 실적 조회
            AttendanceRecord attRecord = attRecordMapper.getAttRecordByEmpCode(empCode, workDate);
            Map<String, String> record = new HashMap<>();

            if (attRecord != null && attRecord.getCheckInTime() != null) {
                String checkInTime = attRecord.getCheckInTime();
                String checkOutTime = attRecord.getCheckOutTime() != null ? attRecord.getCheckOutTime() : "-";

                record.put("checkInTime", checkInTime);
                record.put("checkOutTime", checkOutTime);
                record.put("shiftCode", planShiftCode);
                record.put("shiftName", planShiftName);

                log.debug("출근 시각 존재 - 계획 그대로: empCode={}, date={}, plan={}", empCode, workDate, planShiftName);
            } else {
                // *** 수정: 계획에 따른 실적 표시 로직 개선 ***
                if (isFutureDate) {
                    // 미래 날짜는 - 표시
                    record.put("checkInTime", "-");
                    record.put("checkOutTime", "-");
                    record.put("shiftCode", planShiftCode != null ? planShiftCode : "");
                    record.put("shiftName", "-");
                    log.debug("미래 날짜 - 표시: empCode={}, date={}", empCode, workDate);
                } else {
                    // 과거/현재 날짜 처리
                    if (planShiftCode != null && planShiftName != null) {
                        // *** 수정: 휴무일/휴일인 경우 계획 그대로 표시 ***
                        if ("휴무일".equals(planShiftName) || "휴일".equals(planShiftName)) {
                            // 휴무일/휴일인 경우 계획 그대로
                            record.put("checkInTime", "-");
                            record.put("checkOutTime", "-");
                            record.put("shiftCode", planShiftCode);
                            record.put("shiftName", planShiftName);
                            log.debug("휴무일/휴일 계획 그대로: empCode={}, date={}, plan={}", empCode, workDate, planShiftName);
                        } else {
                            // 근무일인데 출근 기록 없으면 결근
                            record.put("checkInTime", "-");
                            record.put("checkOutTime", "-");
                            record.put("shiftCode", "00");
                            record.put("shiftName", "결근");
                            log.debug("근무일 결근 처리: empCode={}, date={}", empCode, workDate);
                        }
                    } else {
                        // 계획 정보가 없으면 결근
                        record.put("checkInTime", "-");
                        record.put("checkOutTime", "-");
                        record.put("shiftCode", "00");
                        record.put("shiftName", "결근");
                    }
                }
            }

            // 일별 예상근로시간 계산
            String dailyExpectedHours = calculateDailyExpectedHours(empCode, workDate);

            workInfo.put("plan", planShiftName);
            workInfo.put("record", record);
            workInfo.put("expectedHours", dailyExpectedHours);

            log.debug("근무정보 조회 완료: empCode={}, workDate={}, plan={}, record={}, expectedHours={}",
                    empCode, workDate, planShiftName, record.get("shiftName"), dailyExpectedHours);
        } catch (Exception e) {
            log.error("근무정보 조회 실패: empCode={}, workDate={}", empCode, workDate, e);
            workInfo.put("plan", "");
            workInfo.put("record", Map.of("checkInTime", "-", "checkOutTime", "-", "shiftCode", "00", "shiftName", "결근"));
            workInfo.put("expectedHours", "0.00");
        }

        return workInfo;
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
                                leavePeriods.add(Pair.of(attendanceApplyGeneral.getStartTime(), attendanceApplyGeneral.getEndTime()));
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
                            log.debug("계획 기반 시간: date={}, hours={}", workDate, dailyHours.toMinutes() / 60.0);
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

    // 주 52시간 검증용 주간 예상근로시간 계산
    private String calculateWeeklyExpectedHours(String empCode, String workDate) {
        try {
            LocalDate targetDate = LocalDate.parse(workDate, DateTimeFormatter.ofPattern("yyyyMMdd"));

            // 해당 주의 월요일부터 일요일까지 계산
            LocalDate mondayOfWeek = targetDate.with(DayOfWeek.MONDAY);
            LocalDate sundayOfWeek = targetDate.with(DayOfWeek.SUNDAY);

            Duration totalWeekHours = Duration.ZERO;

            log.debug("주 예상근로시간 계산 시작: empCode={}, 주간={} ~ {}", empCode, mondayOfWeek, sundayOfWeek);

            // 주중 7일간 계산
            for (LocalDate date = mondayOfWeek; !date.isAfter(sundayOfWeek); date = date.plusDays(1)) {
                String dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"));

                // 해당 일자의 예상근로시간을 구해서 더하기
                String dailyHours = calculateDailyExpectedHours(empCode, dateStr);
                Duration dayDuration = Duration.ofMinutes((long)(Double.parseDouble(dailyHours) * 60));
                totalWeekHours = totalWeekHours.plus(dayDuration);
            }

            double weeklyHours = totalWeekHours.toMinutes() / 60.0;
            log.debug("주 예상근로시간 계산 완료: empCode={}, totalHours={}", empCode, weeklyHours);

            return String.format("%.2f", weeklyHours);
        } catch (Exception e) {
            log.error("주 예상근로시간 계산 실패: empCode={}, workDate={}", empCode, workDate, e);
            return "0.00";
        }
    }

    // 신청 시간 계산 (연장근로, 휴일근로)
    private Duration calculateApplyHours(AttendanceApplyGeneral apply) {
        try {
            if (apply.getStartTime() != null && apply.getEndTime() != null) {
                int startTime = Integer.parseInt(apply.getStartTime());
                int endTime = Integer.parseInt(apply.getEndTime());

                int startHour = startTime / 100;
                int startMin = startTime % 100;
                int endHour = endTime / 100;
                int endMin = endTime % 100;

                int totalMinutes = (endHour * 60 + endMin) - (startHour * 60 + startMin);
                return Duration.ofMinutes(totalMinutes);
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

    // 수정: 부서별 사원 조회 (부서장용) - 근태신청종류별 필터링 추가
    public List<Employee> getEmployeesByDept(String deptCode, String workDate, String workPlan, String sortBy) {
        try {
            log.debug("부서별 사원 조회 시작: deptCode={}, workDate={}, workPlan={}, sortBy={}", deptCode, workDate, workPlan, sortBy);

            // 수정: 정렬 조건 파라미터 추가
            List<Employee> employees = attendanceApplyMapper.findEmployeesByDeptWithSort(deptCode, workDate, workPlan, sortBy);

            log.debug("조회된 사원 수: {}", employees.size());

            // 각 사원의 기존 신청 내역 및 실적 정보 조회
            for (Employee emp : employees) {
                // 수정: 일반근태 신청 내역 조회 - 전체 조회 (근태신청종류 필터링은 프론트엔드에서 처리)
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

    // 수정: 근태신청종류별 사원 조회 (부서장용) - 오버로드 메서드 추가
    public List<Employee> getEmployeesByDeptWithApplyType(String deptCode, String workDate, String workPlan, String sortBy, String applyTypeCategory) {
        try {
            log.debug("부서별 사원 조회 (근태신청종류별) 시작: deptCode={}, workDate={}, applyTypeCategory={}", deptCode, workDate, applyTypeCategory);

            List<Employee> employees = attendanceApplyMapper.findEmployeesByDeptWithSort(deptCode, workDate, workPlan, sortBy);

            log.debug("조회된 사원 수: {}", employees.size());

            // 각 사원의 기존 신청 내역 및 실적 정보 조회
            for (Employee emp : employees) {
                // 수정: 근태신청종류별 일반근태 신청 내역 조회 (메소드명 변경)
                AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDateWithCategory(emp.getEmpCode(), workDate, applyTypeCategory);
                if (generalApply != null) {
                    emp.setApplyGeneralNo(generalApply.getApplyGeneralNo());
                    emp.setGeneralApplyStatus(generalApply.getStatus());
                } else {
                    // 해당 근태신청종류의 신청이 없으면 기본값 설정
                    emp.setApplyGeneralNo("");
                    emp.setGeneralApplyStatus("대기");
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

    // 수정: 현재 사원만 조회 (일반 사원용) - 오버로딩 추가
    public List<Employee> getEmployeesByDept(String deptCode, String workDate, String workPlan) {
        return getEmployeesByDept(deptCode, workDate, workPlan, null);
    }

    // 수정: 현재 사원만 조회 (일반 사원용) - 근태신청종류별 필터링 추가
    public List<Employee> getCurrentEmployeeList(String empCode, String workDate) {
        try {
            List<Employee> employees = attendanceApplyMapper.findCurrentEmployeeWithCalendar(empCode, workDate);

            // 기존 신청 내역 및 실적 정보 조회
            for (Employee emp : employees) {
                // 수정: 일반근태 신청 내역 조회 - 전체 조회 (근태신청종류 필터링은 프론트엔드에서 처리)
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

    // 수정: 현재 사원만 조회 (근태신청종류별) - 오버로드 메서드 추가
    public List<Employee> getCurrentEmployeeListWithApplyType(String empCode, String workDate, String applyTypeCategory) {
        try {
            List<Employee> employees = attendanceApplyMapper.findCurrentEmployeeWithCalendar(empCode, workDate);

            // 기존 신청 내역 및 실적 정보 조회
            for (Employee emp : employees) {
                // 수정: 근태신청종류별 일반근태 신청 내역 조회 (메소드명 변경)
                AttendanceApplyGeneral generalApply = attendanceApplyMapper.findGeneralApplyByEmpAndDateWithCategory(emp.getEmpCode(), workDate, applyTypeCategory);
                if (generalApply != null) {
                    emp.setApplyGeneralNo(generalApply.getApplyGeneralNo());
                    emp.setGeneralApplyStatus(generalApply.getStatus());
                } else {
                    // 해당 근태신청종류의 신청이 없으면 기본값 설정
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

    // 수정: 일반근태 신청 유효성 검증 강화
    public String validateGeneralApply(AttendanceApplyGeneral apply) {
        try {
            // 시간 검증
            if (apply.getStartTime() != null && apply.getEndTime() != null) {
                int startTime = Integer.parseInt(apply.getStartTime());
                int endTime = Integer.parseInt(apply.getEndTime());

                if (startTime >= endTime) {
                    return "시작시간이 종료시간보다 늦을 수 없습니다.";
                }

                // 수정: 정상근무시간 연장 신청 제한 검증
                String applyType = apply.getApplyType();
                if ("연장".equals(applyType)) {
                    // 일반 연장은 정상근무시간(16:20) 이후만 가능
                    if (startTime < 1620) {
                        return "정상근무시간(16:20) 이후에만 연장근무를 신청할 수 있습니다.";
                    }
                } else if ("조출연장".equals(applyType)) {
                    // 조출연장은 정상근무시간(07:30) 이전만 가능
                    if (startTime >= 730) {
                        return "정상근무시간(07:30) 이전에만 조출연장을 신청할 수 있습니다.";
                    }
                }
            }

            //  주 52시간 초과 검증
            String weeklyHours = calculateWeeklyExpectedHours(apply.getEmpCode(), apply.getTargetDate());
            double currentWeekHours = Double.parseDouble(weeklyHours);

            // 신청 시간 계산
            Duration applyHours = calculateApplyHours(apply);
            double applyHoursDecimal = applyHours.toMinutes() / 60.0;

            if (currentWeekHours + applyHoursDecimal > 52.0) {
                return "주 52시간을 초과할 수 없습니다. (현재: " + String.format("%.2f", currentWeekHours) + "시간)";
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

            // 수정: 휴일/휴무일 포함 여부 검증
            if (!validateDateRange(apply.getTargetStartDate(), apply.getTargetEndDate())) {
                return "신청 기간에 휴일/휴무일이 포함되어 있습니다.";
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

    // 수정: 일반근태 신청 상신 처리 강화 - 부서장 여부 파라미터 추가
    @Transactional
    public void submitGeneralApply(String applyGeneralNo, String applicantCode, String isHeader) {
        try {
            log.debug("일반근태 상신 시작: applyGeneralNo={}, applicantCode={}, isHeader={}", applyGeneralNo, applicantCode, isHeader);

            // 수정: 부서장 여부 확인 강화
            if ("Y".equals(isHeader)) {
                // 부서장인 경우 바로 승인완료 처리
                attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "승인완료");

                // 수정: 승인완료 시 실적 업데이트
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

    // 수정: 기타근태 신청 상신 처리 강화 - 부서장 여부 파라미터 추가
    @Transactional
    public void submitEtcApply(String applyEtcNo, String applicantCode, String isHeader) {
        try {
            log.debug("기타근태 상신 시작: applyEtcNo={}, applicantCode={}, isHeader={}", applyEtcNo, applicantCode, isHeader);

            AttendanceApplyEtc etcApply = attendanceApplyMapper.findEtcApplyByNo(applyEtcNo);
            if (etcApply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            // 수정: 부서장 여부 확인 강화
            if ("Y".equals(isHeader)) {
                // 부서장인 경우 바로 승인완료 처리
                attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "승인완료");

                // 수정: 연차 차감 및 실적 업데이트
                deductAnnualLeave(etcApply);
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

    // 수정: 승인완료 시 실적 업데이트 메서드 추가
    @Transactional
    private void updateAttendanceRecord(String applyNo, String applyType) {
        try {
            if ("general".equals(applyType)) {
                // 일반근태 승인완료 시 실적 업데이트 로직
                AttendanceApplyGeneral apply = attendanceApplyMapper.findGeneralApplyByNo(applyNo);
                if (apply != null) {
                    // 연장근무, 휴일근무 등의 경우 실적에 추가 시간 반영
                    // 실제 구현은 비즈니스 로직에 따라 달라질 수 있음
                    log.debug("일반근태 실적 업데이트: applyNo={}, empCode={}, targetDate={}",
                            applyNo, apply.getEmpCode(), apply.getTargetDate());
                }
            } else if ("etc".equals(applyType)) {
                // 기타근태 승인완료 시 실적 업데이트 로직
                AttendanceApplyEtc apply = attendanceApplyMapper.findEtcApplyByNo(applyNo);
                if (apply != null) {
                    // 연차, 반차 등의 경우 실적을 신청한 근태로 변경
                    attendanceApplyMapper.updateAttendanceRecordByEtcApply(apply.getEmpCode(),
                            apply.getTargetStartDate(), apply.getShiftCode());
                    log.debug("기타근태 실적 업데이트: applyNo={}, empCode={}, shiftCode={}",
                            applyNo, apply.getEmpCode(), apply.getShiftCode());
                }
            }
        } catch (Exception e) {
            log.error("실적 업데이트 실패: applyNo={}, applyType={}", applyNo, applyType, e);
            // 실적 업데이트 실패는 로그만 남기고 전체 트랜잭션을 롤백하지 않음
        }
    }

    // 연차 차감 로직
    @Transactional
    private void deductAnnualLeave(AttendanceApplyEtc etcApply) {
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
                        // 현재 연차 잔여량 조회
                        AnnualDetail currentAnnual = annualDetailMapper.findByEmpCode(etcApply.getEmpCode());
                        if (currentAnnual != null) {
                            // 새로운 연차 잔여량 계산
                            BigDecimal newBalance = currentAnnual.getBalanceDay().subtract(deductDays);

                            // 연차 잔여량 업데이트
                            annualDetailMapper.updateBalanceDay(etcApply.getEmpCode(), newBalance);

                            log.debug("연차 차감 완료: empCode={}, 차감일수={}, 기존잔여={}, 신규잔여={}",
                                    etcApply.getEmpCode(), deductDays, currentAnnual.getBalanceDay(), newBalance);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("연차 차감 실패: etcApply={}", etcApply, e);
            throw new RuntimeException("연차 차감에 실패했습니다.", e);
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

    // 기타근태 신청 상신취소 처리 (연차 복원 포함)
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

    // 연차 복원 로직 (상신취소 시)
    @Transactional
    private void restoreAnnualLeave(AttendanceApplyEtc etcApply) {
        try {
            String shiftCode = etcApply.getShiftCode();
            if (shiftCode != null) {
                ShiftMaster shift = shiftMasterMapper.findShiftByCode(shiftCode);
                if (shift != null) {
                    String shiftName = shift.getShiftName();
                    BigDecimal restoreDays = BigDecimal.ZERO;

                    // 연차 유형에 따른 복원 일수 계산
                    if ("연차".equals(shiftName)) {
                        restoreDays = BigDecimal.ONE; // 연차는 1일 복원
                    } else if ("전반차".equals(shiftName) || "후반차".equals(shiftName)) {
                        restoreDays = new BigDecimal("0.5"); // 반차는 0.5일 복원
                    }

                    // 연차 복원이 필요한 경우
                    if (restoreDays.compareTo(BigDecimal.ZERO) > 0) {
                        // 현재 연차 잔여량 조회
                        AnnualDetail currentAnnual = annualDetailMapper.findByEmpCode(etcApply.getEmpCode());
                        if (currentAnnual != null) {
                            // 새로운 연차 잔여량 계산
                            BigDecimal newBalance = currentAnnual.getBalanceDay().add(restoreDays);

                            // 연차 잔여량 업데이트
                            annualDetailMapper.updateBalanceDay(etcApply.getEmpCode(), newBalance);

                            log.debug("연차 복원 완료: empCode={}, 복원일수={}, 기존잔여={}, 신규잔여={}",
                                    etcApply.getEmpCode(), restoreDays, currentAnnual.getBalanceDay(), newBalance);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("연차 복원 실패: etcApply={}", etcApply, e);
            throw new RuntimeException("연차 복원에 실패했습니다.", e);
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
