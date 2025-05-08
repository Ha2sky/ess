package com.jb.ess.service;

import com.jb.ess.domain.Employee;
import com.jb.ess.mapper.EmployeeMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeMapper employeeMapper;

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
    public void removeEmployeeFromDepartment(String empCode) {
        employeeMapper.updateEmployeeDepartment(empCode, null); // 또는 "" 처리
    }

    /* 부서에 소속되지 않은 모든 사원 조회 (선택 리스트용) */
    public List<Employee> getEmployeesWithoutDepartment() {
        return employeeMapper.findEmployeesWithoutDepartment();
    }
}
