package com.jb.ess.attendance.service;

import com.jb.ess.attendance.mapper.EmpAttendanceMapper;
import com.jb.ess.common.domain.Department;
import com.jb.ess.common.domain.Employee;
import com.jb.ess.depart.mapper.DepartmentMapper;
import com.jb.ess.depart.mapper.EmployeeMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmpAttService {
    private final EmpAttendanceMapper empAttendanceMapper;
    private final DepartmentMapper departmentMapper;
    private final EmployeeMapper employeeMapper;

    /* 사원 정보 및 실적 리스트 */
    public List<Employee> empAttandanceList(String deptCode) {
        return empAttendanceMapper.getEmpAttendanceByDeptCode(deptCode);
    }

    /* 로그인한 사원의 부서 정보 */
    public Department empDepartmentInfo(String empCode) {
        return departmentMapper.findByDeptCode(employeeMapper.getDeptCodeByEmpCode(empCode));
    }

    public List<Department> childDepartmentList(String deptCode) {
        List<Department> departments = empAttendanceMapper.getChildDepartmentsByDeptCode(deptCode);
        departments.add(departmentMapper.findByDeptCode(deptCode));
        return departments;
    }
}
