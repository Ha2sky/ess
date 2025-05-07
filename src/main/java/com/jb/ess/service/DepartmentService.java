package com.jb.ess.service;

import com.jb.ess.domain.Department;
import com.jb.ess.mapper.DepartmentMapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DepartmentService {
    private final DepartmentMapper departmentMapper;

    public List<Department> getAllDepartments() {
        return departmentMapper.findAll();
    }

    public void saveDepartment(Department department) {
        if (departmentMapper.countByDeptCode(department.getDeptCode()) > 0) {
            throw new IllegalArgumentException("이미 존재하는 부서코드입니다.");
        }
        departmentMapper.insertDepartment(department);
    }

    public void updateDepartment(Department department) {
        departmentMapper.updateDepartment(department);
    }

    public Department getDepartmentByDeptCode(String deptCode) {
        return departmentMapper.findByDeptCode(deptCode);
    }

    public void deleteDepartment(String deptCode) {
        // 부서가 존재하는지 확인하고 삭제 처리
        departmentMapper.deleteDepartment(deptCode);
    }

    public boolean existsByDeptCode(String deptCode) {
        return departmentMapper.findByDeptCode(deptCode) != null;
    }
}
