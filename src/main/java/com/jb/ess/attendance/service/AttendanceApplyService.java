package com.jb.ess.attendance.service;

import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.mapper.AttendanceApplyMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AttendanceApplyService {
    private final AttendanceApplyMapper attendanceApplyMapper;

    // 현재 사용자 정보 조회
    public Employee getCurrentEmployee(String empCode) {
        return attendanceApplyMapper.findEmployeeByEmpCode(empCode);
    }

    // 부서별 사원 조회 (부서장용)
    public List<Employee> getEmployeesByDept(String deptCode, String workDate, String workPlan) {
        return attendanceApplyMapper.findEmployeesByDept(deptCode, workDate, workPlan);
    }

    // 현재 사원만 조회 (일반 사원용)
    public List<Employee> getCurrentEmployeeList(String empCode, String workDate) {
        return attendanceApplyMapper.findCurrentEmployeeWithCalendar(empCode, workDate);
    }

    // 일반근태 신청 유효성 검증
    public String validateGeneralApply(AttendanceApplyGeneral apply) {
        // 시간 검증
        if (apply.getStartTime() != null && apply.getEndTime() != null) {
            int startTime = Integer.parseInt(apply.getStartTime());
            int endTime = Integer.parseInt(apply.getEndTime());

            if (startTime >= endTime) {
                return "시작시간이 종료시간보다 늦을 수 없습니다.";
            }
        }

        // 중복 신청 검증
        boolean hasDuplicate = attendanceApplyMapper.checkDuplicateGeneralApply(
                apply.getEmpCode(), apply.getTargetDate(), apply.getApplyType());
        if (hasDuplicate) {
            return "해당 일자에 동일한 신청이 이미 존재합니다.";
        }

        return "valid";
    }

    // 기타근태 신청 유효성 검증
    public String validateEtcApply(AttendanceApplyEtc apply) {
        // 날짜 검증
        int startDate = Integer.parseInt(apply.getTargetStartDate());
        int endDate = Integer.parseInt(apply.getTargetEndDate());

        if (startDate > endDate) {
            return "시작일이 종료일보다 늦을 수 없습니다.";
        }

        // 중복 신청 검증
        boolean hasDuplicate = attendanceApplyMapper.checkDuplicateEtcApply(
                apply.getEmpCode(), apply.getTargetStartDate(), apply.getTargetEndDate());
        if (hasDuplicate) {
            return "해당 기간에 중복된 신청이 존재합니다.";
        }

        return "valid";
    }

    // 일반근태 신청 저장 - 수정: 저장 로직 개선
    @Transactional
    public void saveGeneralApply(AttendanceApplyGeneral apply) {
        // 신청번호 생성
        String applyNo = "GEN" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) +
                String.format("%04d", (int)(Math.random() * 10000));
        apply.setApplyGeneralNo(applyNo);

        // 부서코드 설정 - 신청대상자의 부서코드로 설정
        Employee targetEmp = attendanceApplyMapper.findEmployeeByEmpCode(apply.getEmpCode());
        apply.setDeptCode(targetEmp.getDeptCode());

        attendanceApplyMapper.insertGeneralApply(apply);
    }

    // 기타근태 신청 저장 - 수정: 저장 로직 개선
    @Transactional
    public void saveEtcApply(AttendanceApplyEtc apply) {
        // 신청번호 생성
        String applyNo = "ETC" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) +
                String.format("%04d", (int)(Math.random() * 10000));
        apply.setApplyEtcNo(applyNo);

        // 부서코드 설정
        Employee targetEmp = attendanceApplyMapper.findEmployeeByEmpCode(apply.getEmpCode());
        apply.setDeptCode(targetEmp.getDeptCode());

        attendanceApplyMapper.insertEtcApply(apply);
    }

    // 일반근태 신청 상신 처리 - 수정: applyNo -> applyGeneralNo 분리
    @Transactional
    public void submitGeneralApply(String applyGeneralNo, String applicantCode) {
        // 상태를 '상신'으로 변경
        attendanceApplyMapper.updateGeneralApplyStatus(applyGeneralNo, "상신");

        // 결재 이력 생성 - 부서장에게 결재 요청
        String approvalNo = "APPROVAL" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) +
                String.format("%04d", (int)(Math.random() * 10000));

        // 신청자의 부서장 정보 조회
        String deptCode = attendanceApplyMapper.getDeptCodeByGeneralApplyNo(applyGeneralNo);
        String approverCode = attendanceApplyMapper.getDeptLeaderByDeptCode(deptCode);

        if (approverCode != null) {
            attendanceApplyMapper.insertGeneralApprovalHistory(approvalNo, applyGeneralNo, approverCode);
        }
    }

    // 기타근태 신청 상신 처리 - 수정: applyNo -> applyEtcNo 분리
    @Transactional
    public void submitEtcApply(String applyEtcNo, String applicantCode) {
        // 상태를 '상신'으로 변경
        attendanceApplyMapper.updateEtcApplyStatus(applyEtcNo, "상신");

        // 결재 이력 생성 - 부서장에게 결재 요청
        String approvalNo = "APPROVAL" + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) +
                String.format("%04d", (int)(Math.random() * 10000));

        // 신청자의 부서장 정보 조회
        String deptCode = attendanceApplyMapper.getDeptCodeByEtcApplyNo(applyEtcNo);
        String approverCode = attendanceApplyMapper.getDeptLeaderByDeptCode(deptCode);

        if (approverCode != null) {
            attendanceApplyMapper.insertEtcApprovalHistory(approvalNo, applyEtcNo, approverCode);
        }
    }

    // 일반근태 신청 삭제 처리 - 수정: applyNo -> applyGeneralNo 분리
    @Transactional
    public void deleteGeneralApply(String applyGeneralNo, String applicantCode) {
        // 본인 신청건만 삭제 가능하도록 검증
        boolean isOwner = attendanceApplyMapper.checkGeneralApplyOwnership(applyGeneralNo, applicantCode);
        if (!isOwner) {
            throw new RuntimeException("본인 신청건만 삭제할 수 있습니다.");
        }

        // 대기 상태인 경우만 삭제 가능
        String status = attendanceApplyMapper.getGeneralApplyStatus(applyGeneralNo);
        if (!"대기".equals(status)) {
            throw new RuntimeException("대기 상태인 신청건만 삭제할 수 있습니다.");
        }

        attendanceApplyMapper.deleteGeneralApply(applyGeneralNo);
    }

    // 기타근태 신청 삭제 처리 - 수정: applyNo -> applyEtcNo 분리
    @Transactional
    public void deleteEtcApply(String applyEtcNo, String applicantCode) {
        // 본인 신청건만 삭제 가능하도록 검증
        boolean isOwner = attendanceApplyMapper.checkEtcApplyOwnership(applyEtcNo, applicantCode);
        if (!isOwner) {
            throw new RuntimeException("본인 신청건만 삭제할 수 있습니다.");
        }

        // 대기 상태인 경우만 삭제 가능
        String status = attendanceApplyMapper.getEtcApplyStatus(applyEtcNo);
        if (!"대기".equals(status)) {
            throw new RuntimeException("대기 상태인 신청건만 삭제할 수 있습니다.");
        }

        attendanceApplyMapper.deleteEtcApply(applyEtcNo);
    }

    // 신청자별 일반근태 신청 내역 조회
    public List<AttendanceApplyGeneral> getGeneralAppliesByApplicant(String applicantCode) {
        return attendanceApplyMapper.findGeneralAppliesByApplicant(applicantCode);
    }

    // 신청자별 기타근태 신청 내역 조회
    public List<AttendanceApplyEtc> getEtcAppliesByApplicant(String applicantCode) {
        return attendanceApplyMapper.findEtcAppliesByApplicant(applicantCode);
    }

    // 부서별 일반근태 신청 내역 조회 (부서장용)
    public List<AttendanceApplyGeneral> getGeneralAppliesByDept(String deptCode) {
        return attendanceApplyMapper.findGeneralAppliesByDept(deptCode);
    }

    // 부서별 기타근태 신청 내역 조회 (부서장용)
    public List<AttendanceApplyEtc> getEtcAppliesByDept(String deptCode) {
        return attendanceApplyMapper.findEtcAppliesByDept(deptCode);
    }
}
