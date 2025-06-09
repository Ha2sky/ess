package com.jb.ess.attendance.service;

import com.jb.ess.common.mapper.ApprovalMapper;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.mapper.AttendanceApplyMapper;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.ShiftMasterMapper;
import com.jb.ess.common.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * 일반근태 상세 정보 조회
     */
    public Map<String, Object> getGeneralApplyDetail(String applyGeneralNo) {
        try {
            log.debug("일반근태 상세 정보 조회: applyGeneralNo={}", applyGeneralNo);

            Map<String, Object> detail = new HashMap<>();

            // 기본 신청 정보
            AttendanceApplyGeneral applyInfo = attendanceApplyMapper.findGeneralApplyByNo(applyGeneralNo);
            if (applyInfo == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            // 신청자 정보 (상신자)
            Employee applicantInfo = attendanceApplyMapper.findEmployeeByEmpCode(applyInfo.getApplicantCode());
            // 대상자 정보
            Employee targetInfo = attendanceApplyMapper.findEmployeeByEmpCode(applyInfo.getEmpCode());

            // Map 형태로 변환 - 강화된 로직
            Map<String, Object> applyInfoMap = convertGeneralToMap(applyInfo);
            Map<String, Object> applicantInfoMap = convertEmployeeToMapEnhanced(applicantInfo, "상신자");
            Map<String, Object> targetInfoMap = convertEmployeeToMapEnhanced(targetInfo, "대상자");

            // 결재 이력 - 완전 강화
            List<ApprovalHistory> approvalHistory = getGeneralApprovalHistory(applyGeneralNo);
            List<Map<String, Object>> historyList = new ArrayList<>();

            for (int i = 0; i < approvalHistory.size(); i++) {
                ApprovalHistory history = approvalHistory.get(i);
                Map<String, Object> historyMap = new HashMap<>();

                // 순번
                historyMap.put("ORDER_NUM", i + 1);
                historyMap.put("APPROVER_CODE", history.getApproverCode());

                // 결재자 정보
                Employee approverInfo = attendanceApplyMapper.findEmployeeByEmpCode(history.getApproverCode());
                if (approverInfo != null) {
                    historyMap.put("APPROVER_NAME", approverInfo.getEmpName());

                    // 부서명 조회
                    String deptName = getDepartmentNameEnhanced(approverInfo);
                    historyMap.put("DEPT_NAME", deptName);

                    // 직책명
                    String positionName = getPositionNameEnhanced(approverInfo);
                    historyMap.put("POSITION_NAME", positionName);

                    historyMap.put("DUTY_NAME", approverInfo.getDutyName() != null ? approverInfo.getDutyName() : "-");
                } else {
                    historyMap.put("APPROVER_NAME", history.getApproverCode());
                    historyMap.put("DEPT_NAME", "-");
                    historyMap.put("POSITION_NAME", "-");
                    historyMap.put("DUTY_NAME", "-");
                }

                // 구분 (일반결재/기타결재)
                historyMap.put("CATEGORY", "일반결재");

                // 결재완료일 실제 데이터 표시
                String approvalDate = history.getApprovalDate();
                if (approvalDate != null && !approvalDate.trim().isEmpty() && !"-".equals(approvalDate)) {
                    // 날짜 포맷팅 (yyyyMMddHHmmss -> yyyy-MM-dd HH:mm:ss)
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

                // 반려 사유 표시
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

            // 기본 신청 정보
            AttendanceApplyEtc applyInfo = attendanceApplyMapper.findEtcApplyByNo(applyEtcNo);
            if (applyInfo == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            // 신청자 정보 (상신자)
            Employee applicantInfo = attendanceApplyMapper.findEmployeeByEmpCode(applyInfo.getApplicantCode());
            // 대상자 정보
            Employee targetInfo = attendanceApplyMapper.findEmployeeByEmpCode(applyInfo.getEmpCode());

            // Map 형태로 변환 - 강화된 로직
            Map<String, Object> applyInfoMap = convertEtcToMap(applyInfo);
            Map<String, Object> applicantInfoMap = convertEmployeeToMapEnhanced(applicantInfo, "상신자");
            Map<String, Object> targetInfoMap = convertEmployeeToMapEnhanced(targetInfo, "대상자");

            // 결재 이력 - 완전 강화
            List<ApprovalHistory> approvalHistory = getEtcApprovalHistory(applyEtcNo);
            List<Map<String, Object>> historyList = new ArrayList<>();

            for (int i = 0; i < approvalHistory.size(); i++) {
                ApprovalHistory history = approvalHistory.get(i);
                Map<String, Object> historyMap = new HashMap<>();

                // 순번
                historyMap.put("ORDER_NUM", i + 1);
                historyMap.put("APPROVER_CODE", history.getApproverCode());

                // 결재자 정보 완전 강화
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

                // 구분 (기타결재)
                historyMap.put("CATEGORY", "기타결재");

                // 결재완료일 실제 데이터 표시
                String approvalDate = history.getApprovalDate();
                if (approvalDate != null && !approvalDate.trim().isEmpty() && !"-".equals(approvalDate)) {
                    // 날짜 포맷팅 (yyyyMMddHHmmss -> yyyy-MM-dd HH:mm:ss)
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

                // 반려 사유 표시
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

        // 실제 입력한 사유 표시 강화
        String reason = apply.getReason();
        if (reason == null || reason.trim().isEmpty()) {
            reason = "사무처리"; // 기본값
        }
        map.put("REASON", reason);

        map.put("TIME_ITEM_NAME", "주간");

        // 시간 범위 계산
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
        map.put("REASON", apply.getReason() != null ? apply.getReason() : "-");

        // 실제 근태명 조회 및 표시
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
            map.put("TYPE", type); // 상신자/대상자 구분
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

        // 1차: Employee 객체에서 직접 조회
        if (deptName != null && !deptName.trim().isEmpty() && !"-".equals(deptName)) {
            return deptName;
        }

        // 2차: 부서코드로 부서마스터에서 조회
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

        // 부서장 여부 확인하여 기본 직책명 설정
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
     * 일반근태 승인 처리
     */
    @Transactional
    public void approveGeneralApply(String applyGeneralNo, String approverCode) {
        try {
            log.debug("일반근태 승인 처리 시작: applyGeneralNo={}, approverCode={}", applyGeneralNo, approverCode);

            // 신청 상태 확인
            AttendanceApplyGeneral apply = attendanceApplyMapper.findGeneralApplyByNo(applyGeneralNo);
            if (apply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            if (!"상신".equals(apply.getStatus())) {
                throw new RuntimeException("상신 상태인 신청만 승인할 수 있습니다. 현재 상태: " + apply.getStatus());
            }

            // 결재 이력 업데이트
            ApprovalHistory currentHistory = approvalMapper.findGeneralApprovalHistoryByApprover(applyGeneralNo, approverCode);
            if (currentHistory == null) {
                throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
            }

            currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
            currentHistory.setApprovalStatus("승인");
            approvalMapper.updateApprovalHistory(currentHistory);

            // 신청 상태 업데이트
            attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "승인완료");

            log.info("일반근태 승인 처리 완료: applyGeneralNo={}", applyGeneralNo);
        } catch (Exception e) {
            log.error("일반근태 승인 처리 실패: applyGeneralNo={}", applyGeneralNo, e);
            throw new RuntimeException("승인 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 기타근태 승인 처리
     */
    @Transactional
    public void approveEtcApply(String applyEtcNo, String approverCode) {
        try {
            log.debug("기타근태 승인 처리 시작: applyEtcNo={}, approverCode={}", applyEtcNo, approverCode);

            // 신청 상태 확인
            AttendanceApplyEtc apply = attendanceApplyMapper.findEtcApplyByNo(applyEtcNo);
            if (apply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            if (!"상신".equals(apply.getStatus())) {
                throw new RuntimeException("상신 상태인 신청만 승인할 수 있습니다. 현재 상태: " + apply.getStatus());
            }

            // 결재 이력 업데이트
            ApprovalHistory currentHistory = approvalMapper.findEtcApprovalHistoryByApprover(applyEtcNo, approverCode);
            if (currentHistory == null) {
                throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
            }

            currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
            currentHistory.setApprovalStatus("승인");
            approvalMapper.updateApprovalHistory(currentHistory);

            // 신청 상태 업데이트
            attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "승인완료");

            log.info("기타근태 승인 처리 완료: applyEtcNo={}", applyEtcNo);
        } catch (Exception e) {
            log.error("기타근태 승인 처리 실패: applyEtcNo={}", applyEtcNo, e);
            throw new RuntimeException("승인 처리에 실패했습니다: " + e.getMessage(), e);
        }
    }

    /**
     * 일반근태 반려 처리
     */
    @Transactional
    public void rejectGeneralApply(String applyGeneralNo, String approverCode, String rejectReason) {
        try {
            log.debug("일반근태 반려 처리 시작: applyGeneralNo={}, approverCode={}", applyGeneralNo, approverCode);

            // 신청 상태 확인
            AttendanceApplyGeneral apply = attendanceApplyMapper.findGeneralApplyByNo(applyGeneralNo);
            if (apply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            if (!"상신".equals(apply.getStatus())) {
                throw new RuntimeException("상신 상태인 신청만 반려할 수 있습니다. 현재 상태: " + apply.getStatus());
            }

            // 결재 이력 업데이트
            ApprovalHistory currentHistory = approvalMapper.findGeneralApprovalHistoryByApprover(applyGeneralNo, approverCode);
            if (currentHistory == null) {
                throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
            }

            currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
            currentHistory.setApprovalStatus("반려");
            currentHistory.setRejectReason(rejectReason);
            approvalMapper.updateApprovalHistory(currentHistory);

            // 신청 상태 업데이트
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

            // 신청 상태 확인
            AttendanceApplyEtc apply = attendanceApplyMapper.findEtcApplyByNo(applyEtcNo);
            if (apply == null) {
                throw new RuntimeException("신청 정보를 찾을 수 없습니다.");
            }

            if (!"상신".equals(apply.getStatus())) {
                throw new RuntimeException("상신 상태인 신청만 반려할 수 있습니다. 현재 상태: " + apply.getStatus());
            }

            // 결재 이력 업데이트
            ApprovalHistory currentHistory = approvalMapper.findEtcApprovalHistoryByApprover(applyEtcNo, approverCode);
            if (currentHistory == null) {
                throw new RuntimeException("결재 이력을 찾을 수 없습니다.");
            }

            currentHistory.setApprovalDate(DateUtil.getDateTimeNow());
            currentHistory.setApprovalStatus("반려");
            currentHistory.setRejectReason(rejectReason);
            approvalMapper.updateApprovalHistory(currentHistory);

            // 신청 상태 업데이트
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