package com.jb.ess.attendance.service;

import com.jb.ess.attendance.mapper.AttendanceApplyMapper;
import com.jb.ess.attendance.mapper.ApprovalMapper;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.ApprovalHistory;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.util.DateUtil;
import com.jb.ess.depart.mapper.DepartmentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AttendanceApplyService {

    private final AttendanceApplyMapper attendanceApplyMapper;
    private final ApprovalMapper approvalMapper;
    private final DepartmentMapper departmentMapper;

    /**
     * 근태신청 가능한 사원 조회
     */
    public List<Employee> getApplicableEmployees(String deptCode, String workDate, String empCode, String isLeader) {
        return attendanceApplyMapper.findApplicableEmployees(deptCode, workDate, empCode, isLeader);
    }

    /**
     * 일반근태 신청 저장
     */
    @Transactional
    public void saveGeneralApply(AttendanceApplyGeneral apply) {
        // 주 52시간 검증
        validateWeeklyWorkingHours(apply);

        if (apply.getApplyGeneralNo() == null) {
            // 신규 등록
            apply.setApplyGeneralNo(generateApplyNo("GA"));
            apply.setApplyDate(DateUtil.getDateTimeNow());
            apply.setStatus("대기");
            attendanceApplyMapper.insertGeneralApply(apply);
        } else {
            // 수정
            attendanceApplyMapper.updateGeneralApply(apply);
        }
    }

    /**
     * 기타근태 신청 저장
     */
    @Transactional
    public void saveEtcApply(AttendanceApplyEtc apply) {
        if (apply.getApplyEtcNo() == null) {
            // 신규 등록
            apply.setApplyEtcNo(generateApplyNo("EA"));
            apply.setApplyDate(DateUtil.getDateTimeNow());
            apply.setStatus("대기");
            attendanceApplyMapper.insertEtcApply(apply);
        } else {
            // 수정
            attendanceApplyMapper.updateEtcApply(apply);
        }
    }

    /**
     * 근태신청 상신 처리
     */
    @Transactional
    public void submitApply(String applyNo, String applyType, String applicantCode) {
        // 결재선 생성
        String deptLeader = getDeptLeader(applicantCode);

        if (applicantCode.equals(deptLeader)) {
            // 부서장이 신청한 경우 자동 승인
            if ("general".equals(applyType)) {
                attendanceApplyMapper.updateGeneralStatus(applyNo, "승인완료");
            } else {
                attendanceApplyMapper.updateEtcStatus(applyNo, "승인완료");
            }

            // 자동 승인 이력 생성
            ApprovalHistory history = new ApprovalHistory();
            history.setApprovalNo(generateApplyNo("AH"));
            history.setApproverCode(applicantCode);
            history.setApprovalDate(DateUtil.getDateTimeNow());
            history.setApprovalStatus("승인");

            if ("general".equals(applyType)) {
                history.setApplyGeneralNo(applyNo);
            } else {
                history.setApplyEtcNo(applyNo);
            }

            approvalMapper.insertApprovalHistory(history);
        } else {
            // 일반 상신 처리
            if ("general".equals(applyType)) {
                attendanceApplyMapper.updateGeneralStatus(applyNo, "승인중");
            } else {
                attendanceApplyMapper.updateEtcStatus(applyNo, "승인중");
            }

            // 결재선 생성
            ApprovalHistory history = new ApprovalHistory();
            history.setApprovalNo(generateApplyNo("AH"));
            history.setApproverCode(deptLeader);

            if ("general".equals(applyType)) {
                history.setApplyGeneralNo(applyNo);
            } else {
                history.setApplyEtcNo(applyNo);
            }

            approvalMapper.insertApprovalHistory(history);
        }
    }

    /**
     * 신청 내역 조회
     */
    public List<AttendanceApplyGeneral> getGeneralAppliesByApplicant(String applicantCode) {
        return attendanceApplyMapper.findGeneralAppliesByApplicant(applicantCode);
    }

    public List<AttendanceApplyEtc> getEtcAppliesByApplicant(String applicantCode) {
        return attendanceApplyMapper.findEtcAppliesByApplicant(applicantCode);
    }

    /**
     * 근태신청 삭제
     */
    @Transactional
    public void deleteApply(String applyNo, String applyType) {
        if ("general".equals(applyType)) {
            attendanceApplyMapper.deleteGeneralApply(applyNo);
        } else {
            attendanceApplyMapper.deleteEtcApply(applyNo);
        }
    }

    private String generateApplyNo(String prefix) {
        return prefix + System.currentTimeMillis();
    }

    private String getDeptLeader(String empCode) {
        // 사원의 부서장 조회 로직
        return departmentMapper.findDepartmentLeader(empCode);
    }

    private void validateWeeklyWorkingHours(AttendanceApplyGeneral apply) {
        // 주 52시간 검증 로직 구현
        // 계획된 근무시간 + 연장근로 + 휴일근로 + 신청하는 시간 <= 52시간
    }
}
