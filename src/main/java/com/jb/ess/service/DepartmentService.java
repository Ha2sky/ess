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
    }

    // 부서 수정
    public void updateDepartment(Department department) {
        departmentMapper.updateDepartment(department);
    }

    // 부서 코드로 부서 찾기
    public Department getDepartmentByDeptCode(String deptCode) {
        return departmentMapper.findByDeptCode(deptCode);
    }

    // 부서 삭제
    public void deleteDepartment(String deptCode) {
        // 부서가 존재하는지 확인하고 삭제 처리
        departmentMapper.deleteDepartment(deptCode);
    }

    // 부서 코드 존재 여부 확인
    public boolean existsByDeptCode(String deptCode) {
        return departmentMapper.findByDeptCode(deptCode) != null;
    }
}
