package com.jb.ess.attendance.service;

import com.jb.ess.common.domain.AttendanceApplyEtc;
import com.jb.ess.common.domain.AttendanceApplyGeneral;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.common.domain.Department;
import com.jb.ess.common.domain.AnnualDetail;
import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.mapper.AttendanceApplyMapper;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.AnnualDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AttendanceApplyService {
    private final AttendanceApplyMapper attendanceApplyMapper;
    private final DepartmentMapper departmentMapper;
    private final AnnualDetailMapper annualDetailMapper;

    // 현재 사용자 정보 조회
    public Employee getCurrentEmployee(String empCode) {
        return attendanceApplyMapper.findEmployeeByEmpCode(empCode);
    }

    // 부서 정보 조회 메서드
    public Department getDepartmentInfo(String deptCode) {
        return departmentMapper.findByDeptCode(deptCode);
    }

    // 수정: 하위부서 목록 조회 메서드 추가 (요청사항 5)
    public List<Department> getSubDepartments(String parentDeptCode) {
        return attendanceApplyMapper.findSubDepartments(parentDeptCode);
    }

    // 수정: 연차잔여 정보 조회 메서드 추가 (요청사항 2)
    public AnnualDetail getAnnualDetail(String empCode) {
        return annualDetailMapper.findByEmpCode(empCode);
    }

    // 수정: 필터링된 근태 마스터 목록 조회 (요청사항 3)
    public List<ShiftMaster> getFilteredShiftMasters() {
        List<String> allowedShiftNames = Arrays.asList(
                "결근", "주간", "현장실습", "연차", "출장", "휴일", "휴무일",
                "4전", "4후", "4야", "3전", "3후", "휴직",
                "사외교육", "육아휴직", "산재휴직", "대체휴무일"
        );
        return attendanceApplyMapper.findShiftMastersByNames(allowedShiftNames);
    }

    // 수정: 근무정보 조회 메서드 추가 (요청사항 4)
    public Map<String, Object> getWorkInfo(String empCode, String workDate) {
        Map<String, Object> workInfo = new HashMap<>();

        // 계획 조회
        String planShiftCode = attendanceApplyMapper.getPlannedShiftCode(empCode, workDate);
        String planShiftName = planShiftCode != null ?
                attendanceApplyMapper.getShiftNameByCode(planShiftCode) : "";

        // 실적 조회
        Map<String, String> recordInfo = attendanceApplyMapper.getAttendanceRecord(empCode, workDate);

        // 예상근로시간 계산
        String expectedWorkHours = planShiftCode != null ?
                attendanceApplyMapper.getExpectedWorkHours(planShiftCode) : "0";

        workInfo.put("plan", planShiftName);
        workInfo.put("record", recordInfo);
        workInfo.put("expectedHours", expectedWorkHours);

        return workInfo;
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

    // 일반근태 신청 저장 (요청사항 6)
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

    // 기타근태 신청 저장 (요청사항 6)
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

    // 일반근태 신청 상신 처리 (요청사항 6)
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

    // 기타근태 신청 상신 처리 (요청사항 6)
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

    // 수정: 일반근태 신청 상신취소 처리 추가 (요청사항 6)
    @Transactional
    public void cancelGeneralApply(String applyGeneralNo, String applicantCode) {
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
    }

    // 수정: 기타근태 신청 상신취소 처리 추가 (요청사항 6)
    @Transactional
    public void cancelEtcApply(String applyEtcNo, String applicantCode) {
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
    }

    // 일반근태 신청 삭제 처리 (요청사항 6)
    @Transactional
    public void deleteGeneralApply(String applyGeneralNo, String applicantCode) {
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
    }

    // 기타근태 신청 삭제 처리 (요청사항 6)
    @Transactional
    public void deleteEtcApply(String applyEtcNo, String applicantCode) {
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
    }
}
