package com.jb.ess.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// 유저 메인페이지
@Controller
public class UserMainController {
    // 근태신청
    @GetMapping("/user/apply")
    public String attendanceApply() {
        return "user/apply";
    }

    // 근태신청내역
    @GetMapping("/user/history")
    public String attendanceHistory() {
        return "user/history";
    }

    // 부서근태조회
    @GetMapping("/user/deptattendance")
    public String departmentAttendance() {
        return "user/deptattendance";
    }

    // 부서원근태승인(부서장 전용)
    @GetMapping("/user/approval")
    public String approvalPage() {
        return "user/approval";
    }
}
