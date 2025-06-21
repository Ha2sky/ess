package com.jb.ess.attendance.service;

import com.jb.ess.common.mapper.ApprovalMapper;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.domain.AnnualDetail;
import com.jb.ess.common.mapper.AttendanceApplyMapper;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.ShiftMasterMapper;
import com.jb.ess.common.mapper.AnnualDetailMapper;
import com.jb.ess.common.mapper.AttRecordMapper;
import com.jb.ess.common.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalMapper approvalMapper;
    private final AttendanceApplyMapper attendanceApplyMapper;
    private final DepartmentMapper departmentMapper;
    private final ShiftMasterMapper shiftMasterMapper;
    private final AnnualDetailMapper annualDetailMapper;
    private final AttRecordMapper attRecordMapper;

    /**
     * 현재 사용자 정보 조회
     */
    public Employee getCurrentEmployee(String empCode) {
        try {
            Employee employee = attendanceApplyMapper.findEmployeeByEmpCode(empCode);
            log.debug("사용자 정보 조회: empCode={}, isHeader={}", empCode,
                    employee != null ? employee.getIsHeader() : null);
            return employee;
        } catch (Exception e) {
            log.error("사용자 정보 조회 실패: empCode={}", empCode, e);
            return null;
        }
    }

    /**
     * 근태기 정보 조회 메서드 - 시간 포맷팅 개선
     */
    public List<Map<String, Object>> getAttendanceInfo(String type, String applyNo) {
        try {
            List<Map<String, Object>> attendanceList = new ArrayList<>();

            if ("general".equals(type)) {
                AttendanceApplyGeneral apply = attendanceApplyMapper.findGeneralApplyByNo(applyNo);
                if (apply != null) {
                    Map<String, Object> attendance = attRecordMapper.getAttendanceRecordInfo(apply.getEmpCode(), apply.getTargetDate());
                    if (attendance != null) {
                        Map<String, Object> attendanceInfo = new HashMap<>();
                        attendanceInfo.put("WORK_DATE", formatDateString(apply.getTargetDate()));
                        attendanceInfo.put("CHECK_IN_TIME", formatTimeForDisplay(attendance.get("CHECK_IN_TIME")));
                        attendanceInfo.put("CHECK_OUT_TIME", formatTimeForDisplay(attendance.get("CHECK_OUT_TIME")));
                        attendanceList.add(attendanceInfo);
                    }
                }
            } else if ("etc".equals(type)) {
                AttendanceApplyEtc apply = attendanceApplyMapper.findEtcApplyByNo(applyNo);
                if (apply != null) {
                    String startDate = apply.getTargetStartDate();
                    String endDate = apply.getTargetEndDate();
                    List<String> dateRange = getDateRange(startDate, endDate);
                    for (String date : dateRange) {
                        Map<String, Object> attendance = attRecordMapper.getAttendanceRecordInfo(apply.getEmpCode(), date);
                        Map<String, Object> attendanceInfo = new HashMap<>();
                        attendanceInfo.put("WORK_DATE", formatDateString(date));
                        if (attendance != null) {
                            attendanceInfo.put("CHECK_IN_TIME", formatTimeForDisplay(attendance.get("CHECK_IN_TIME")));
                            attendanceInfo.put("CHECK_OUT_TIME", formatTimeForDisplay(attendance.get("CHECK_OUT_TIME")));
                        } else {
                            attendanceInfo.put("CHECK_IN_TIME", "-");
                            attendanceInfo.put("CHECK_OUT_TIME", "-");
                        }
                        attendanceList.add(attendanceInfo);
                    }
                }
            }

            return attendanceList;
        } catch (Exception e) {
            log.error("근태기 정보 조회 실패: type={}, applyNo={}", type, applyNo, e);
            return List.of();
        }
    }

    /**
     * 시간 포맷팅 메서드 (HHMMSS -> HH:MM)
     */
    private String formatTimeForDisplay(Object timeObj) {
        if (timeObj == null) {
            return "-";
        }
        String timeStr = timeObj.toString().trim();
        if (timeStr.isEmpty() || "-".equals(timeStr)) {
            return "-";
        }
        try {
            if (timeStr.length() == 6 && timeStr.matches("\\d{6}")) {
                return timeStr.substring(0, 2) + ":" + timeStr.substring(2, 4);
            } else if (timeStr.length() == 4 && timeStr.matches("\\d{4}")) {
                return timeStr.substring(0, 2) + ":" + timeStr.substring(2, 4);
            } else if (timeStr.matches("\\d{2}:\\d{2}")) {
                return timeStr;
            } else {
                return timeStr;
            }
        } catch (Exception e) {
            log.warn("시간 포맷팅 실패: timeStr={}", timeStr, e);
            return timeStr;
        }
    }

    /**
     * 날짜 범위 생성 메서드
     */
    private List<String> getDateRange(String startDate, String endDate) {
        List<String> dateList = new ArrayList<>();
        try {
            int start = Integer.parseInt(startDate);
            int end = Integer.parseInt(endDate);
            for (int date = start; date <= end; date++) {
                dateList.add(String.valueOf(date));
            }
        } catch (Exception e) {
            log.warn("날짜 범위 생성 실패: startDate={}, endDate={}", startDate, endDate, e);
            dateList.add(startDate);
        }
        return dateList;
    }

    /**
     * 일반근태 상세 정보 조회
     */
    public Map<String, Object> getGeneralApplyDetail(String applyGeneralNo) {
        try {
            log.debug("일반근태 상세 정보 조회: applyGeneralNo={}", applyGeneralNo);
            Map<String, Object> detail = new HashMap<>();

            AttendanceApplyGeneral applyInfo = attendanceApplyMapper.findGeneralApplyByNo(applyGeneralNo);
            if (applyInfo == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            Employee applicantInfo = attendanceApplyMapper.findEmployeeByEmpCode(applyInfo.getApplicantCode());
            Employee targetInfo = attendanceApplyMapper.findEmployeeByEmpCode(applyInfo.getEmpCode());

            Map<String, Object> applyInfoMap = convertGeneralToMap(applyInfo);
            Map<String, Object> applicantInfoMap = convertEmployeeToMapEnhanced(applicantInfo, "상신자");
            Map<String, Object> targetInfoMap = convertEmployeeToMapEnhanced(targetInfo, "대상자");

            List<ApprovalHistory> approvalHistory = getGeneralApprovalHistory(applyGeneralNo);
            List<Map<String, Object>> historyList = new ArrayList<>();

            for (int i = 0; i < approvalHistory.size(); i++) {
                ApprovalHistory history = approvalHistory.get(i);
                Map<String, Object> historyMap = new HashMap<>();

                historyMap.put("ORDER_NUM", i + 1);
                historyMap.put("APPROVER_CODE", history.getApproverCode());

                Employee approverInfo = attendanceApplyMapper.findEmployeeByEmpCode(history.getApproverCode());
                if (approverInfo != null) {
                    historyMap.put("APPROVER_NAME", approverInfo.getEmpName());
                    String deptName = getDepartmentNameEnhanced(approverInfo);
                    historyMap.put("DEPT_NAME", deptName);
                    String positionName = getPositionNameEnhanced(approverInfo);
                    historyMap.put("POSITION_NAME", positionName);
                    historyMap.put("DUTY_NAME", approverInfo.getDutyName() != null ? approverInfo.getDutyName() : "-");
                } else {
                    historyMap.put("APPROVER_NAME", history.getApproverCode());
                    historyMap.put("DEPT_NAME", "-");
                    historyMap.put("POSITION_NAME", "-");
                    historyMap.put("DUTY_NAME", "-");
                }

                historyMap.put("CATEGORY", "일반결재");

                String approvalStatus = history.getApprovalStatus();
                if (approvalStatus != null && !approvalStatus.trim().isEmpty()) {
                    historyMap.put("APPROVAL_STATUS", approvalStatus);
                } else {
                    historyMap.put("APPROVAL_STATUS", "대기");
                }

                String approvalDate = history.getApprovalDate();
                if (approvalDate != null && !approvalDate.trim().isEmpty() && !"-".equals(approvalDate)) {
                    try {
                        if (approvalDate.length() >= 8) {
                            String formattedDate = approvalDate.substring(0, 4) + "-" +
                                    approvalDate.substring(4, 6) + "-" +
                                    approvalDate.substring(6, 8);
                            if (approvalDate.length() >= 14) {
                                formattedDate += " " + approvalDate.substring(8, 10) + ":" +
                                        approvalDate.substring(10, 12) + ":" +
                                        approvalDate.substring(12, 14);
                            }
                            historyMap.put("APPROVAL_DATE", formattedDate);
                        } else {
                            historyMap.put("APPROVAL_DATE", approvalDate);
                        }
                    } catch (Exception e) {
                        historyMap.put("APPROVAL_DATE", approvalDate);
                    }
                } else {
                    historyMap.put("APPROVAL_DATE", "-");
                }

                String rejectReason = history.getRejectReason();
                if ("반려".equals(history.getApprovalStatus())) {
                    historyMap.put("REJECT_REASON", rejectReason != null ? rejectReason : "-");
                } else {
                    historyMap.put("REJECT_REASON", "-");
                }

                historyList.add(historyMap);
            }

            detail.put("applyInfo", applyInfoMap);
            detail.put("applicantInfo", applicantInfoMap);
            detail.put("targetInfo", targetInfoMap);
            detail.put("approvalHistory", historyList);

            return detail;
        } catch (Exception e) {
            log.error("일반근태 상세 정보 조회 실패: {}", applyGeneralNo, e);
            throw new RuntimeException("상세 정보 조회에 실패했습니다.", e);
        }
    }

    /**
     * 기타근태 상세 정보 조회
     */
    public Map<String, Object> getEtcApplyDetail(String applyEtcNo) {
        try {
            log.debug("기타근태 상세 정보 조회: applyEtcNo={}", applyEtcNo);
            Map<String, Object> detail = new HashMap<>();

            AttendanceApplyEtc applyInfo = attendanceApplyMapper.findEtcApplyByNo(applyEtcNo);
            if (applyInfo == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            Employee applicantInfo = attendanceApplyMapper.findEmployeeByEmpCode(applyInfo.getApplicantCode());
            Employee targetInfo = attendanceApplyMapper.findEmployeeByEmpCode(applyInfo.getEmpCode());

            Map<String, Object> applyInfoMap = convertEtcToMap(applyInfo);
            Map<String, Object> applicantInfoMap = convertEmployeeToMapEnhanced(applicantInfo, "상신자");
            Map<String, Object> targetInfoMap = convertEmployeeToMapEnhanced(targetInfo, "대상자");

            List<ApprovalHistory> approvalHistory = getEtcApprovalHistory(applyEtcNo);
            List<Map<String, Object>> historyList = new ArrayList<>();

            for (int i = 0; i < approvalHistory.size(); i++) {
                ApprovalHistory history = approvalHistory.get(i);
                Map<String, Object> historyMap = new HashMap<>();

                historyMap.put("ORDER_NUM", i + 1);
                historyMap.put("APPROVER_CODE", history.getApproverCode());

                Employee approverInfo = attendanceApplyMapper.findEmployeeByEmpCode(history.getApproverCode());
                if (approverInfo != null) {
                    historyMap.put("APPROVER_NAME", approverInfo.getEmpName());
                    String deptName = getDepartmentNameEnhanced(approverInfo);
                    historyMap.put("DEPT_NAME", deptName);
                    String positionName = getPositionNameEnhanced(approverInfo);
                    historyMap.put("POSITION_NAME", positionName);
                    historyMap.put("DUTY_NAME", approverInfo.getDutyName() != null ? approverInfo.getDutyName() : "-");
                } else {
                    historyMap.put("APPROVER_NAME", history.getApproverCode());
                    historyMap.put("DEPT_NAME", "-");
                    historyMap.put("POSITION_NAME", "-");
                    historyMap.put("DUTY_NAME", "-");
                }

                historyMap.put("CATEGORY", "기타결재");

                String approvalStatus = history.getApprovalStatus();
                if (approvalStatus != null && !approvalStatus.trim().isEmpty()) {
                    historyMap.put("APPROVAL_STATUS", approvalStatus);
                } else {
                    historyMap.put("APPROVAL_STATUS", "대기");
                }

                String approvalDate = history.getApprovalDate();
                if (approvalDate != null && !approvalDate.trim().isEmpty() && !"-".equals(approvalDate)) {
                    try {
                        if (approvalDate.length() >= 8) {
                            String formattedDate = approvalDate.substring(0, 4) + "-" +
                                    approvalDate.substring(4, 6) + "-" +
                                    approvalDate.substring(6, 8);
                            if (approvalDate.length() >= 14) {
                                formattedDate += " " + approvalDate.substring(8, 10) + ":" +
                                        approvalDate.substring(10, 12) + ":" +
                                        approvalDate.substring(12, 14);
                            }
                            historyMap.put("APPROVAL_DATE", formattedDate);
                        } else {
                            historyMap.put("APPROVAL_DATE", approvalDate);
                        }
                    } catch (Exception e) {
                        historyMap.put("APPROVAL_DATE", approvalDate);
                    }
                } else {
                    historyMap.put("APPROVAL_DATE", "-");
                }

                String rejectReason = history.getRejectReason();
                if ("반려".equals(history.getApprovalStatus())) {
                    historyMap.put("REJECT_REASON", rejectReason != null ? rejectReason : "-");
                } else {
                    historyMap.put("REJECT_REASON", "-");
                }

                historyList.add(historyMap);
            }

            detail.put("applyInfo", applyInfoMap);
            detail.put("applicantInfo", applicantInfoMap);
            detail.put("targetInfo", targetInfoMap);
            detail.put("approvalHistory", historyList);

            return detail;
        } catch (Exception e) {
            log.error("기타근태 상세 정보 조회 실패: {}", applyEtcNo, e);
            throw new RuntimeException("상세 정보 조회에 실패했습니다.", e);
        }
    }

    /**
     * 일반근태 사유 표시 로직 개선
     */
    private Map<String, Object> convertGeneralToMap(AttendanceApplyGeneral apply) {
        Map<String, Object> map = new HashMap<>();
        map.put("APPLY_GENERAL_NO", apply.getApplyGeneralNo());
        map.put("EMP_CODE", apply.getEmpCode());
        map.put("APPLICANT_CODE", apply.getApplicantCode());
        map.put("APPLY_DATE", formatDateString(apply.getApplyDate()));
        map.put("TARGET_DATE", formatDateString(apply.getTargetDate()));
        map.put("START_TIME", apply.getStartTime());
        map.put("END_TIME", apply.getEndTime());
        map.put("APPLY_TYPE", apply.getApplyType());

        String reason = apply.getReason();
        if (reason == null || reason.trim().isEmpty()) {
            map.put("REASON", "-");
        } else {
            map.put("REASON", reason);
        }

        map.put("TIME_ITEM_NAME", "주간");

        if (apply.getStartTime() != null && apply.getEndTime() != null) {
            String startFormatted = formatTime(apply.getStartTime());
            String endFormatted = formatTime(apply.getEndTime());
            map.put("WORK_TIME_RANGE", startFormatted + " ~ " + endFormatted);
        } else {
            map.put("WORK_TIME_RANGE", "-");
        }

        return map;
    }

    private Map<String, Object> convertEtcToMap(AttendanceApplyEtc apply) {
        Map<String, Object> map = new HashMap<>();
        map.put("APPLY_ETC_NO", apply.getApplyEtcNo());
        map.put("EMP_CODE", apply.getEmpCode());
        map.put("APPLICANT_CODE", apply.getApplicantCode());
        map.put("APPLY_DATE", formatDateString(apply.getApplyDate()));
        map.put("TARGET_START_DATE", formatDateString(apply.getTargetStartDate()));
        map.put("TARGET_END_DATE", formatDateString(apply.getTargetEndDate()));

        String reason = apply.getReason();
        map.put("REASON", reason != null && !reason.trim().isEmpty() ? reason : "-");

        if (apply.getShiftCode() != null) {
            try {
                String shiftName = shiftMasterMapper.findShiftNameByShiftCode(apply.getShiftCode());
                map.put("SHIFT_NAME", shiftName != null ? shiftName : "기타근태");
            } catch (Exception e) {
                log.warn("근태명 조회 실패: shiftCode={}", apply.getShiftCode());
                map.put("SHIFT_NAME", "기타근태");
            }
        } else {
            map.put("SHIFT_NAME", "기타근태");
        }

        return map;
    }

    /**
     * Employee -> Map 변환
     */
    private Map<String, Object> convertEmployeeToMapEnhanced(Employee emp, String type) {
        Map<String, Object> map = new HashMap<>();
        if (emp != null) {
            map.put("EMP_CODE", emp.getEmpCode());
            map.put("EMP_NAME", emp.getEmpName());
            map.put("DEPT_CODE", emp.getDeptCode());

            String deptName = getDepartmentNameEnhanced(emp);
            map.put("DEPT_NAME", deptName);

            String positionName = getPositionNameEnhanced(emp);
            map.put("POSITION_NAME", positionName);

            map.put("DUTY_NAME", emp.getDutyName() != null ? emp.getDutyName() : "-");
            map.put("TYPE", type);
        } else {
            map.put("EMP_CODE", "-");
            map.put("EMP_NAME", "-");
            map.put("DEPT_CODE", "-");
            map.put("DEPT_NAME", "-");
            map.put("POSITION_NAME", "-");
            map.put("DUTY_NAME", "-");
            map.put("TYPE", type);
        }
        return map;
    }

    /**
     * 부서명 조회 로직 완전 강화
     */
    private String getDepartmentNameEnhanced(Employee emp) {
        if (emp == null) return "-";

        String deptName = emp.getDeptName();
        if (deptName != null && !deptName.trim().isEmpty() && !"-".equals(deptName)) {
            return deptName;
        }

        if (emp.getDeptCode() != null && !emp.getDeptCode().trim().isEmpty()) {
            try {
                var dept = departmentMapper.findByDeptCode(emp.getDeptCode());
                if (dept != null && dept.getDeptName() != null) {
                    return dept.getDeptName();
                }
            } catch (Exception e) {
                log.warn("부서명 조회 실패: empCode={}, deptCode={}", emp.getEmpCode(), emp.getDeptCode(), e);
            }
        }

        return "-";
    }

    /**
     * 직책명 조회 로직 강화
     */
    private String getPositionNameEnhanced(Employee emp) {
        if (emp == null) return "-";

        String positionName = emp.getPositionName();
        if (positionName != null && !positionName.trim().isEmpty() && !"-".equals(positionName)) {
            return positionName;
        }

        if ("Y".equals(emp.getIsHeader())) {
            return "부서장";
        }

        return "-";
    }

    /**
     * 날짜 포맷팅 헬퍼 메서드
     */
    private String formatDateString(String dateStr) {
        if (dateStr == null || dateStr.length() < 8) return "-";
        try {
            return dateStr.substring(0, 4) + "-" +
                    dateStr.substring(4, 6) + "-" +
                    dateStr.substring(6, 8);
        } catch (Exception e) {
            return dateStr;
        }
    }

    private String formatTime(String time) {
        if (time == null || time.length() < 4) return "-";
        try {
            return time.substring(0, 2) + ":" + time.substring(2, 4);
        } catch (Exception e) {
            return time;
        }
    }

    /**
     * 결재할 일반근태 문서 조회
     */
    public List<AttendanceApplyGeneral> getPendingGeneralApprovals(String approverCode, String startDate, String endDate, String applyType, String empCode) {
        try {
            log.debug("결재할 일반근태 문서 조회: approverCode={}, startDate={}, endDate={}, applyType={}, empCode={}",
                    approverCode, startDate, endDate, applyType, empCode);
            List<AttendanceApplyGeneral> result = approvalMapper.findPendingGeneralApprovals(approverCode, startDate, endDate, applyType, empCode);
            log.debug("결재할 일반근태 문서 수: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("결재할 일반근태 문서 조회 실패", e);
            return List.of();
        }
    }

    /**
     * 결재할 기타근태 문서 조회
     */
    public List<AttendanceApplyEtc> getPendingEtcApprovals(String approverCode, String startDate, String endDate, String empCode) {
        try {
            log.debug("결재할 기타근태 문서 조회: approverCode={}, startDate={}, endDate={}, empCode={}",
                    approverCode, startDate, endDate, empCode);
            List<AttendanceApplyEtc> result = approvalMapper.findPendingEtcApprovals(approverCode, startDate, endDate, empCode);
            log.debug("결재할 기타근태 문서 수: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("결재할 기타근태 문서 조회 실패", e);
            return List.of();
        }
    }

    /**
     * 승인된 일반근태 문서 조회
     */
    public List<AttendanceApplyGeneral> getApprovedGeneralApprovals(String approverCode, String startDate, String endDate, String applyType, String empCode) {
        try {
            log.debug("승인된 일반근태 문서 조회: approverCode={}, startDate={}, endDate={}, applyType={}, empCode={}",
                    approverCode, startDate, endDate, applyType, empCode);
            List<AttendanceApplyGeneral> result = approvalMapper.findApprovedGeneralApprovals(approverCode, startDate, endDate, applyType, empCode);
            log.debug("승인된 일반근태 문서 수: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("승인된 일반근태 문서 조회 실패", e);
            return List.of();
        }
    }

    /**
     * 반려된 일반근태 문서 조회
     */
    public List<AttendanceApplyGeneral> getRejectedGeneralApprovals(String approverCode, String startDate, String endDate, String applyType, String empCode) {
        try {
            log.debug("반려된 일반근태 문서 조회: approverCode={}, startDate={}, endDate={}, applyType={}, empCode={}",
                    approverCode, startDate, endDate, applyType, empCode);
            List<AttendanceApplyGeneral> result = approvalMapper.findRejectedGeneralApprovals(approverCode, startDate, endDate, applyType, empCode);
            log.debug("반려된 일반근태 문서 수: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("반려된 일반근태 문서 조회 실패", e);
            return List.of();
        }
    }

    /**
     * 🔧 수정: 일반근태 승인 처리 - HRTATTRECORD 테이블 호환성 수정
     */
    @Transactional
    public void approveGeneralApply(String applyGeneralNo, String approverCode) {
        try {
            log.debug("일반근태 승인 처리 시작: applyGeneralNo={}, approverCode={}", applyGeneralNo, approverCode);

            AttendanceApplyGeneral apply = attendanceApplyMapper.findGeneralApplyByNo(applyGeneralNo);
            if (apply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            if (!"상신".equals(apply.getStatus())) {
                throw new RuntimeException("상신 상태인 신청만 승인할 수 있습니다. 현재 상태: " + apply.getStatus());
            }

            ApprovalHistory currentHistory = approvalMapper.findGeneralApprovalHistoryByApprover(applyGeneralNo, approverCode);
            if (currentHistory == null) {
                throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
            }

            currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
            currentHistory.setApprovalStatus("승인");
            approvalMapper.updateApprovalHistory(currentHistory);

            attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "승인완료");

            String applyType = apply.getApplyType();
            if ("휴일근무".equals(applyType)) {
                // 🔧 수정: HRTATTRECORD 테이블 호환성 - updateAttendanceRecordByShiftCode 제거
                // ❌ 제거됨: attendanceApplyMapper.updateAttendanceRecordByShiftCode(apply.getEmpCode(), apply.getTargetDate(), "14-1");
                // ✅ 교체됨: HRTWORKEMPCALENDAR 테이블의 SHIFT_CODE만 업데이트
                attendanceApplyMapper.updateShiftCodeAfterGeneralApproval(apply.getEmpCode(), apply.getTargetDate(), applyType);
                log.debug("휴일근무 승인 완료: SHIFT_CODE 업데이트 (14-1)");
            } else if ("전반차".equals(applyType) || "후반차".equals(applyType)) {
                // 🔧 수정: 연차 차감 로직 개선 - 정확한 계산
                deductAnnualLeaveImproved(apply.getEmpCode(), new BigDecimal("0.5"));
                // SHIFT_CODE 업데이트도 추가
                attendanceApplyMapper.updateShiftCodeAfterGeneralApproval(apply.getEmpCode(), apply.getTargetDate(), applyType);
                log.debug("전반차/후반차 승인 완료: 연차 차감 및 SHIFT_CODE 업데이트");
            }

            log.info("일반근태 승인 처리 완료: applyGeneralNo={}", applyGeneralNo);
        } catch (Exception e) {
            log.error("일반근태 승인 처리 실패: applyGeneralNo={}", applyGeneralNo, e);
            throw new RuntimeException("승인 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 🔧 수정: 기타근태 승인 처리 - HRTATTRECORD 테이블 호환성 수정
     */
    @Transactional
    public void approveEtcApply(String applyEtcNo, String approverCode) {
        try {
            log.debug("기타근태 승인 처리 시작: applyEtcNo={}, approverCode={}", applyEtcNo, approverCode);

            AttendanceApplyEtc apply = attendanceApplyMapper.findEtcApplyByNo(applyEtcNo);
            if (apply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            if (!"상신".equals(apply.getStatus())) {
                throw new RuntimeException("상신 상태인 신청만 승인할 수 있습니다. 현재 상태: " + apply.getStatus());
            }

            ApprovalHistory currentHistory = approvalMapper.findEtcApprovalHistoryByApprover(applyEtcNo, approverCode);
            if (currentHistory == null) {
                throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
            }

            currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
            currentHistory.setApprovalStatus("승인");
            approvalMapper.updateApprovalHistory(currentHistory);

            attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "승인완료");

            if (apply.getShiftCode() != null) {
                String shiftName = shiftMasterMapper.findShiftNameByShiftCode(apply.getShiftCode());
                if ("연차".equals(shiftName)) {
                    // 🔧 수정: 연차 차감 로직 개선 - 정확한 계산
                    deductAnnualLeaveImproved(apply.getEmpCode(), BigDecimal.ONE);
                    log.debug("연차 승인 완료: 연차 차감 완료");
                } else if ("전반차".equals(shiftName) || "후반차".equals(shiftName)) {
                    // 🔧 수정: 반차 처리 추가
                    deductAnnualLeaveImproved(apply.getEmpCode(), new BigDecimal("0.5"));
                    log.debug("반차 승인 완료: 연차 0.5일 차감 완료");
                }

                // 🔧 수정: HRTWORKEMPCALENDAR 테이블의 SHIFT_CODE 업데이트
                attendanceApplyMapper.updateShiftCodeAfterEtcApproval(
                        apply.getEmpCode(),
                        apply.getTargetStartDate(),
                        apply.getTargetEndDate(),
                        apply.getShiftCode()
                );
                log.debug("기타근태 승인 완료: SHIFT_CODE 업데이트 (shiftName={})", shiftName);
            }

            log.info("기타근태 승인 처리 완료: applyEtcNo={}", applyEtcNo);
        } catch (Exception e) {
            log.error("기타근태 승인 처리 실패: applyEtcNo={}", applyEtcNo, e);
            throw new RuntimeException("승인 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 🔧 수정: 연차 차감 메서드 개선 - 정확한 계산
     */
    @Transactional
    private void deductAnnualLeaveImproved(String empCode, BigDecimal deductDays) {
        try {
            AnnualDetail currentAnnual = annualDetailMapper.findByEmpCode(empCode);
            if (currentAnnual != null) {
                BigDecimal currentBalance = currentAnnual.getBalanceDay();
                BigDecimal currentUse = currentAnnual.getUseDay();

                log.debug("연차 차감 전 상태: empCode={}, 현재잔여={}, 현재사용={}, 차감예정={}",
                        empCode, currentBalance, currentUse, deductDays);

                // 🔧 수정: 정확한 연차 차감 계산
                boolean deductionResult = annualDetailMapper.updateBalanceDayWithCheck(empCode, deductDays);

                if (deductionResult) {
                    // 🔧 수정: USE_DAY도 정확히 증가
                    annualDetailMapper.updateUseDayIncrease(empCode, deductDays);

                    // 차감 후 확인
                    AnnualDetail updatedAnnual = annualDetailMapper.findByEmpCode(empCode);
                    log.debug("연차 차감 완료: empCode={}, 차감일수={}, 차감후잔여={}, 차감후사용={}",
                            empCode, deductDays,
                            updatedAnnual != null ? updatedAnnual.getBalanceDay() : "조회실패",
                            updatedAnnual != null ? updatedAnnual.getUseDay() : "조회실패");

                    // 🔧 계산 검증: 16 - 0.5 - 1 = 14.5 가 맞는지 확인
                    if (updatedAnnual != null) {
                        BigDecimal expectedBalance = currentBalance.subtract(deductDays);
                        BigDecimal expectedUse = currentUse.add(deductDays);

                        if (updatedAnnual.getBalanceDay().compareTo(expectedBalance) != 0) {
                            log.error("연차 차감 계산 오류: 예상잔여={}, 실제잔여={}", expectedBalance, updatedAnnual.getBalanceDay());
                        }
                        if (updatedAnnual.getUseDay().compareTo(expectedUse) != 0) {
                            log.error("연차 사용 계산 오류: 예상사용={}, 실제사용={}", expectedUse, updatedAnnual.getUseDay());
                        }
                    }
                } else {
                    log.warn("연차 잔여량 부족으로 차감 실패: empCode={}, 요청차감일수={}, 현재잔여={}",
                            empCode, deductDays, currentBalance);
                }
            } else {
                log.warn("연차 정보가 없습니다: empCode={}", empCode);
            }
        } catch (Exception e) {
            log.error("연차 차감 실패: empCode={}, deductDays={}", empCode, deductDays, e);
            throw new RuntimeException("연차 차감에 실패했습니다.", e);
        }
    }

    /**
     * 연차 차감 메서드 (기존 호환성 유지)
     */
    @Transactional
    private void deductAnnualLeave(String empCode, BigDecimal deductDays) {
        deductAnnualLeaveImproved(empCode, deductDays);
    }

    /**
     * 일반근태 반려 처리
     */
    @Transactional
    public void rejectGeneralApply(String applyGeneralNo, String approverCode, String rejectReason) {
        try {
            log.debug("일반근태 반려 처리 시작: applyGeneralNo={}, approverCode={}", applyGeneralNo, approverCode);

            AttendanceApplyGeneral apply = attendanceApplyMapper.findGeneralApplyByNo(applyGeneralNo);
            if (apply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            if (!"상신".equals(apply.getStatus())) {
                throw new RuntimeException("상신 상태인 신청만 반려할 수 있습니다. 현재 상태: " + apply.getStatus());
            }

            ApprovalHistory currentHistory = approvalMapper.findGeneralApprovalHistoryByApprover(applyGeneralNo, approverCode);
            if (currentHistory == null) {
                throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
            }

            currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
            currentHistory.setApprovalStatus("반려");
            currentHistory.setRejectReason(rejectReason);
            approvalMapper.updateApprovalHistory(currentHistory);

            attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "반려");

            log.info("일반근태 반려 처리 완료: applyGeneralNo={}", applyGeneralNo);
        } catch (Exception e) {
            log.error("일반근태 반려 처리 실패: applyGeneralNo={}", applyGeneralNo, e);
            throw new RuntimeException("반려 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 기타근태 반려 처리
     */
    @Transactional
    public void rejectEtcApply(String applyEtcNo, String approverCode, String rejectReason) {
        try {
            log.debug("기타근태 반려 처리 시작: applyEtcNo={}, approverCode={}", applyEtcNo, approverCode);

            AttendanceApplyEtc apply = attendanceApplyMapper.findEtcApplyByNo(applyEtcNo);
            if (apply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            if (!"상신".equals(apply.getStatus())) {
                throw new RuntimeException("상신 상태인 신청만 반려할 수 있습니다. 현재 상태: " + apply.getStatus());
            }

            ApprovalHistory currentHistory = approvalMapper.findEtcApprovalHistoryByApprover(applyEtcNo, approverCode);
            if (currentHistory == null) {
                throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
            }

            currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
            currentHistory.setApprovalStatus("반려");
            currentHistory.setRejectReason(rejectReason);
            approvalMapper.updateApprovalHistory(currentHistory);

            attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "반려");

            log.info("기타근태 반려 처리 완료: applyEtcNo={}", applyEtcNo);
        } catch (Exception e) {
            log.error("기타근태 반려 처리 실패: applyEtcNo={}", applyEtcNo, e);
            throw new RuntimeException("반려 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 일반근태 결재 이력 조회
     */
    public List<ApprovalHistory> getGeneralApprovalHistory(String applyGeneralNo) {
        try {
            log.debug("일반근태 결재 이력 조회: applyGeneralNo={}", applyGeneralNo);
            List<ApprovalHistory> result = approvalMapper.findApprovalHistoryByGeneralNo(applyGeneralNo);
            log.debug("일반근태 결재 이력 수: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("일반근태 결재 이력 조회 실패: applyGeneralNo={}", applyGeneralNo, e);
            return List.of();
        }
    }

    /**
     * 기타근태 결재 이력 조회
     */
    public List<ApprovalHistory> getEtcApprovalHistory(String applyEtcNo) {
        try {
            log.debug("기타근태 결재 이력 조회: applyEtcNo={}", applyEtcNo);
            List<ApprovalHistory> result = approvalMapper.findApprovalHistoryByEtcNo(applyEtcNo);
            log.debug("기타근태 결재 이력 수: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("기타근태 결재 이력 조회 실패: applyEtcNo={}", applyEtcNo, e);
            return List.of();
        }
    }

    /**
     * 승인된 기타근태 문서 조회
     */
    public List<AttendanceApplyEtc> getApprovedEtcApprovals(String approverCode, String startDate, String endDate, String empCode) {
        try {
            log.debug("승인된 기타근태 문서 조회: approverCode={}, startDate={}, endDate={}, empCode={}",
                    approverCode, startDate, endDate, empCode);
            List<AttendanceApplyEtc> result = approvalMapper.findApprovedEtcApprovals(approverCode, startDate, endDate, empCode);
            log.debug("승인된 기타근태 문서 수: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("승인된 기타근태 문서 조회 실패", e);
            return List.of();
        }
    }

    /**
     * 반려된 기타근태 문서 조회
     */
    public List<AttendanceApplyEtc> getRejectedEtcApprovals(String approverCode, String startDate, String endDate, String empCode) {
        try {
            log.debug("반려된 기타근태 문서 조회: approverCode={}, startDate={}, endDate={}, empCode={}",
                    approverCode, startDate, endDate, empCode);
            List<AttendanceApplyEtc> result = approvalMapper.findRejectedEtcApprovals(approverCode, startDate, endDate, empCode);
            log.debug("반려된 기타근태 문서 수: {}", result.size());
            return result;
        } catch (Exception e) {
            log.error("반려된 기타근태 문서 조회 실패", e);
            return List.of();
        }
    }
}
