package com.jb.ess.common.security;

import com.jb.ess.common.domain.Employee;

import com.jb.ess.common.mapper.EmployeeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final EmployeeMapper employeeMapper;

    @Autowired
    public CustomUserDetailsService(EmployeeMapper employeeMapper) {
        this.employeeMapper = employeeMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String empCode) throws UsernameNotFoundException {
        Employee employee = employeeMapper.findByEmployeeName(empCode);

        if (employee == null) {throw new UsernameNotFoundException("User not found");}

        return new CustomUserDetails(employee);
    }
}