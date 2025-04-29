package com.jb.ess.mapper;

import com.jb.ess.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper {
    @Select("""
        SELECT
            EMP_CODE AS empCode,
            PASSWORD AS password,
            EMP_STATE AS empState
        FROM HRIMASTER
        WHERE EMP_CODE = #{empCode}
        """)
    User findByUsername(String empCode);
}
