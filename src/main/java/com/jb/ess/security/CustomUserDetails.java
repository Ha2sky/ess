package com.jb.ess.security;

import com.jb.ess.domain.Employee;
import java.io.Serial;
import java.util.Collection;
import java.util.Collections;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

public class CustomUserDetails implements UserDetails {
    @Serial
    private static final long serialVersionUID = 1L;

    private final Employee employee;

    public CustomUserDetails(Employee employee){
        this.employee = employee;
    }

        @Override
        public String getUsername(){
            return employee.getEmpCode();
        }

        @Override
        public String getPassword(){
            return employee.getPassword();
        }

        @Override
        public Collection<? extends GrantedAuthority> getAuthorities() {
            String role = employee.getEmpState().equalsIgnoreCase("SYS") ? "ROLE_ADMIN" : "ROLE_USER";
        return Collections.singleton(new SimpleGrantedAuthority(role));
    }
}
