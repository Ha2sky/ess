package com.jb.ess.security;

import com.jb.ess.domain.Employee;
import com.jb.ess.mapper.EmployeeMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    @Autowired
    private EmployeeMapper employeeMapper;

    @Override
    public UserDetails loadUserByUsername(String empCode) throws UsernameNotFoundException {
        System.out.println("로그인 시도: " + empCode);
        Employee employee = employeeMapper.findByEmployeeName(empCode);

        if (employee == null) {
            throw new UsernameNotFoundException("User not found");
        } else System.out.println("DB에서 찾은 사용자: " + empCode);

        return new CustomUserDetails(employee);
    }
}