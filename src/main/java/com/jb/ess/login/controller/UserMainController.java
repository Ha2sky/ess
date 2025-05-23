package com.jb.ess.login.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

// 유저 메인페이지
@Controller
@RequestMapping("/user")
public class UserMainController {
    // 근태신청
    @GetMapping("/apply")
    public String attendanceApply() {
        return "/user/apply";
    }

    // 근태신청내역
    @GetMapping("/history")
    public String attendanceHistory() {
        return "/user/history";
    }

    // 부서근태조회
    @GetMapping("/attendance")
    public String departmentAttendance() {
        return "redirect:/user/attendance/list";
    }

    // 부서원근태승인(부서장 전용)
    @GetMapping("/approval")
    public String approvalPage() {
        return "/user/approval";
    }
}
