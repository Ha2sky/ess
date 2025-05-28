package com.jb.ess.depart.service;

import com.jb.ess.common.domain.EmpCalendar;
import com.jb.ess.common.domain.Employee;
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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;
    private final EmpCalendarMapper empCalendarMapper;
    private final PatternService patternService;
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /* 특정 부서에 소속된 사원 목록 조회 */
    public List<Employee> getEmployeesByDeptCode(String deptCode) {
        return employeeMapper.findEmployeesByDeptCode(deptCode);
    }

    /* 기존 사원을 특정 부서에 배정 (추가) */
    public void assignEmployeeToDepartment(String empCode, String deptCode) {
        employeeMapper.updateEmployeeDepartment(empCode, deptCode);

        /* 사원의 근태패턴캘린더 생성 */
        String workPatternCode = departmentMapper.findWorkPatternCodeByDeptCode(deptCode);
        Map<Integer, String> shiftCodeMap = patternService.getShiftCodeMap(workPatternCode);
        List<LocalDate> dates = patternService.getDatesInMonth(YearMonth.now());
        List<EmpCalendar> batchList = new ArrayList<>();

        for (LocalDate date : dates) {
            int dayOfWeek = date.getDayOfWeek().getValue();
            String shiftCode = shiftCodeMap.get(dayOfWeek);
            if (shiftCode != null) {
                String holidayYn = (dayOfWeek == 6 || dayOfWeek == 7) ? "Y" : "N";
                batchList.add(new EmpCalendar(
                    workPatternCode,
                    date.format(FORMATTER),
                    shiftCode,
                    empCode,
                    deptCode,
                    holidayYn
                ));
            }
        }

        if (!batchList.isEmpty()) {
            empCalendarMapper.insertBatch(batchList);
        }
    }

    public void assignHeader(String empCode, String deptCode) {
        employeeMapper.updateIsHeader(empCode);
        departmentMapper.updateDeptLeader(deptCode, empCode);
    }

    /* 기존 사원을 부서에서 제거 (삭제 = 부서코드를 NULL 처리) */
    @Transactional
    public void removeEmployeeFromDepartment(String empCode, String deptCode) {
        // 1. 해당 사원이 현재 부서장인지 확인
        String currentLeader = departmentMapper.findDepartmentLeader(deptCode);

        if (empCode.equals(currentLeader)) {
            // 2. 부서장일 경우, 부서장 해제 (null 처리)
            departmentMapper.updateDepartmentLeader(deptCode, null);
            employeeMapper.updateNotHeader(empCode);
        }

        // 3. 사원의 근태패턴캘린더 삭제
        empCalendarMapper.deleteEmpCalendarByEmpCode(deptCode, empCode);

        // 4. 사원의 부서코드 null 처리 (부서 제외)
        employeeMapper.updateEmployeeDepartment(empCode, null);
    }

    /* 부서에 소속되지 않은 모든 사원 조회 (선택 리스트용) */
    public List<Employee> getEmployeesWithoutDepartment() {
        return employeeMapper.findEmployeesWithoutDepartment();
    }
}
