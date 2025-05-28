package com.jb.ess.depart.service;

import com.jb.ess.common.domain.Department;
import com.jb.ess.common.domain.EmpCalendar;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.EmpCalendarMapper;
import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.pattern.service.PatternService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentMapper departmentMapper;
    private final EmpCalendarMapper empCalendarMapper;
    private final EmployeeMapper employeeMapper;
    private final PatternService patternService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    // 모든 부서
    public List<Department> getAllDepartments() {
        return departmentMapper.findAll();
    }

    // 부서 생성
    public void saveDepartment(Department department) {
        // 부서 코드 중복 처리
        if (departmentMapper.countByDeptCode(department.getDeptCode()) > 0) {
            throw new IllegalArgumentException("이미 존재하는 부서코드입니다.");
        }
        departmentMapper.insertDepartment(department);
        generateEmpCalendarByDept(department);
    }

    // 부서 수정
    public void updateDepartment(Department department, String originalDeptCode) {
        String originalWorkPatternCode = departmentMapper.findWorkPatternCodeByDeptCode(originalDeptCode);
        String newWorkPatternCode = department.getWorkPatternCode();

        /* 부서에 할당된 근태패턴코드가 변경된 경우 */
        if (!Objects.equals(originalWorkPatternCode, newWorkPatternCode)) {
            /* 부서에 소속된 사원들의 근태패턴캘린더가 존재하는경우 삭제 */
            if (originalWorkPatternCode != null) {
                empCalendarMapper.deleteEmpCalendarByDeptCode(originalWorkPatternCode, originalDeptCode);
            }
            /* 근태패턴캘린더 생성 */
            generateEmpCalendarByDept(department);
        }
        /* 부서 Update */
        departmentMapper.updateDepartment(department, originalDeptCode);
    }

    // 부서 코드로 부서 찾기
    public Department getDepartmentByDeptCode(String deptCode) {
        return departmentMapper.findByDeptCode(deptCode);
    }

    // 부서 삭제
    public void deleteDepartment(String deptCode) {
        // 부서에 소속된 사원들 근태패턴캘린더 삭제
        empCalendarMapper.deleteEmpCalendarByDeptCode(departmentMapper.findWorkPatternCodeByDeptCode(deptCode), deptCode);
        // 부서 삭제 처리
        departmentMapper.deleteDepartment(deptCode);
    }

    // 부서 코드 존재 여부 확인
    public boolean existsByDeptCode(String deptCode) {
        return departmentMapper.findByDeptCode(deptCode) != null;
    }

    /* 사원별 근태패턴캘린더 생성 */
    public void generateEmpCalendarByDept(Department department) {
        Map<Integer, String> shiftCodeMap = patternService.getShiftCodeMap(department.getWorkPatternCode());
        List<LocalDate> dates = patternService.getDatesInMonth(YearMonth.now());
        List<EmpCalendar> batchList = new ArrayList<>();
        List<String> empCodes = employeeMapper.findEmpCodesByDeptCode(department.getDeptCode());

        for (String empCode : empCodes) {
            for (LocalDate date : dates) {
                int dayOfWeek = date.getDayOfWeek().getValue();
                String shiftCode = shiftCodeMap.get(dayOfWeek);
                if (shiftCode != null) {
                    String holidayYn = (dayOfWeek == 6 || dayOfWeek == 7) ? "Y" : "N";
                    batchList.add(new EmpCalendar(
                        department.getWorkPatternCode(),
                        date.format(FORMATTER),
                        shiftCode,
                        empCode,
                        department.getDeptCode(),
                        holidayYn
                    ));
                }
            }
        }

        if (!batchList.isEmpty()) {
            empCalendarMapper.insertBatch(batchList);
        }
    }
}
