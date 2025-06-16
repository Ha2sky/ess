package com.jb.ess.attendance.service;

import com.jb.ess.common.domain.ApplyHistory;
import com.jb.ess.common.domain.AttendanceRecord;
import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.mapper.ApplyHistoryMapper;
import com.jb.ess.common.mapper.AttRecordMapper;
import com.jb.ess.common.mapper.DepartmentMapper;
import com.jb.ess.common.mapper.EmpCalendarMapper;
import com.jb.ess.common.mapper.EmployeeMapper;
import com.jb.ess.common.mapper.ShiftMasterMapper;
import com.jb.ess.common.util.DateUtil;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HistoryService {
    private final ApplyHistoryMapper applyHistoryMapper;
    private final EmployeeMapper employeeMapper;
    private final EmpCalendarMapper empCalendarMapper;
    private final ShiftMasterMapper shiftMasterMapper;
    private final DepartmentMapper departmentMapper;
    private final AttRecordMapper attRecordMapper;
    private final EmpAttService empAttService;

    public List<ApplyHistory> getApplyList(LocalDate startDate, LocalDate endDate,
                                       String applyType, String status, String empCode) {

        List<ApplyHistory> applyList;
        String formattedStartDate = DateUtil.reverseFormatDate(startDate);
        String formattedEndDate = DateUtil.reverseFormatDate(endDate);

        if (applyType == null || applyType.isEmpty()) {
            applyList = applyHistoryMapper.getAllApplyList(formattedStartDate,
                                                           formattedEndDate,
                                                           status, empCode);
        } else if (applyType.equals("연장근로")) {
            applyList = applyHistoryMapper.getApplyList(formattedStartDate,
                                                        formattedEndDate,
                                                        "연장", status, empCode);
        } else if (applyType.equals("휴일근로")) {
            applyList = applyHistoryMapper.getApplyList(formattedStartDate,
                                                        formattedEndDate,
                                                        "휴일근무", status, empCode);
        } else if (applyType.equals("조퇴외출반차")) {
            applyList = applyHistoryMapper.getApplyList2(formattedStartDate,
                                                         formattedEndDate,
                                                         status, empCode);
        } else {
            applyList = applyHistoryMapper.getApplyListEtc(formattedStartDate,
                                                           formattedEndDate,
                                                           status, empCode);
        }

        setApplyList(applyList);
        return applyList;
    }

    // 근태 신청 상세
    public List<ApplyHistory> setApplyList(List<ApplyHistory> applyList) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        for (ApplyHistory applyHistory : applyList) {
            String targetDate = applyHistory.getTargetDate();
            LocalDate localTargetDate = LocalDate.parse(targetDate, formatter);
            LocalDate monday = localTargetDate.with(DayOfWeek.MONDAY);
            LocalDate sunday = localTargetDate.with(DayOfWeek.SUNDAY);

            // 연장근로
            if (applyHistory.getApplyType().equals("연장") || applyHistory.getApplyType().equals("조출연장")) {
                Duration overtimeHours = Duration.ZERO;
                for (LocalDate date = monday; !date.isAfter(sunday); date = date.plusDays(1)) {
                    overtimeHours = overtimeHours.plus(empAttService.getOvertimeHours(applyHistory.getEmpCode(), DateUtil.reverseFormatDate(date)));
                }
                applyHistory.setOvertime(String.format("%.2f", overtimeHours.toMinutes() / 60.0));

                // 휴일근로
            } else if (applyHistory.getApplyType().equals("휴일근무")) {
                Duration holidayWorkHours = Duration.ZERO;
                for (LocalDate date = monday; !date.isAfter(sunday); date = date.plusDays(1)) {
                    holidayWorkHours = holidayWorkHours.plus(empAttService.getHolidayWorkHours(applyHistory.getEmpCode(), DateUtil.reverseFormatDate(date)));
                }
                applyHistory.setHoliday(String.format("%.2f", holidayWorkHours.toMinutes() / 60.0));

                // 조퇴 / 외출 / 반차
            } else if (applyHistory.getApplyType().equals("조퇴") || applyHistory.getApplyType().equals("외출") ||
                       applyHistory.getApplyType().equals("전반차") || applyHistory.getApplyType().equals("후반차")) {

                // 기타근태
            } else {
                ShiftMaster shift = shiftMasterMapper.findShiftByName(applyHistory.getShiftName());
                applyHistory.setStartTime(shift == null ? "-" : shift.getWorkOnHhmm());
                applyHistory.setEndTime(shift == null ? "-" : shift.getWorkOffHhmm());
            }

            // 신청자 사번, 이름, 부서명
            applyHistory.setApplyEmpName(
                employeeMapper.findEmpNameByEmpCode(applyHistory.getApplyEmpCode()));
            applyHistory.setApplyDeptName(
                departmentMapper.findDeptNameByEmpCode(applyHistory.getApplyEmpCode()));

            // 대상자 사번, 이름, 부서명
            applyHistory.setTargetEmpCode(applyHistory.getEmpCode());
            applyHistory.setTargetEmpName(employeeMapper.findEmpNameByEmpCode(applyHistory.getEmpCode()));
            applyHistory.setTargetDeptName(departmentMapper.findDeptNameByEmpCode(applyHistory.getEmpCode()));

            // 계획근무
            applyHistory.setShiftName(shiftMasterMapper.findShiftNameByEmpCodeAndDate(
                applyHistory.getEmpCode(), applyHistory.getTargetDate()));

            // 출근시간, 퇴근시간
            AttendanceRecord attrecord = attRecordMapper.getCheckInOutTimeByEmpCodeAndWorkDate(
                applyHistory.getEmpCode(), applyHistory.getTargetDate());
            if (attrecord == null) {
                applyHistory.setCheckInTime("-");
                applyHistory.setCheckOutTime("-");
            } else {
                applyHistory.setCheckInTime(attrecord.getCheckInTime());
                applyHistory.setCheckOutTime(attrecord.getCheckOutTime());
            }
            /*
            if (Objects.equals(applyHistory.getApplyEmpCode(), applyHistory.getEmpCode()) &&
                ) */
        }

        return applyList;
    }
}
