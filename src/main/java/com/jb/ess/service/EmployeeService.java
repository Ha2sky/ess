package com.jb.ess.service;

import com.jb.ess.domain.Employee;
import com.jb.ess.mapper.DepartmentMapper;
import com.jb.ess.mapper.EmployeeMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeMapper employeeMapper;
    private final DepartmentMapper departmentMapper;

    /* 특정 부서에 소속된 사원 목록 조회 */
    public List<Employee> getEmployeesByDeptCode(String deptCode) {
        System.out.println("[DEBUG] 부서 사원 조회 요청: " + deptCode);
        List<Employee> employees = employeeMapper.findEmployeesByDeptCode(deptCode);
        System.out.println("[DEBUG] 조회된 사원 수: " + employees.size());  // << 로그 추가
        for (Employee e : employees) {
            System.out.println("[DEBUG] 사원: " + e.getEmpCode() + " - " + e.getEmpName());
        }
        return employees;
    }

    /* 기존 사원을 특정 부서에 배정 (추가) */
    public void assignEmployeeToDepartment(String empCode, String deptCode) {
        employeeMapper.updateEmployeeDepartment(empCode, deptCode);
    }

    /* 기존 사원을 부서에서 제거 (삭제 = 부서코드를 NULL 처리) */
    @Transactional
    public void removeEmployeeFromDepartment(String empCode, String deptCode) {
        // 1. 해당 사원이 현재 부서장인지 확인
        String currentLeader = departmentMapper.findDepartmentLeader(deptCode);

        if (empCode.equals(currentLeader)) {
            // 2. 부서장일 경우, 부서장 해제 (null 처리)
            departmentMapper.updateDepartmentLeader(deptCode, null);
        }

        // 3. 사원의 부서코드 null 처리 (부서 제외)
        employeeMapper.updateEmployeeDepartment(empCode, null);
    }

    /* 부서에 소속되지 않은 모든 사원 조회 (선택 리스트용) */
    public List<Employee> getEmployeesWithoutDepartment() {
        return employeeMapper.findEmployeesWithoutDepartment();
    }
}
