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
}
