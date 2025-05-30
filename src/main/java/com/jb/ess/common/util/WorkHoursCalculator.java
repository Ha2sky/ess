package com.jb.ess.common.util;

import com.jb.ess.common.domain.ShiftMaster;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class WorkHoursCalculator {
    private WorkHoursCalculator() {}
    /* 총 근무시간 계산 */
    public static Duration getTotalWorkTime(ShiftMaster shift) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");
        LocalDate today = LocalDate.now(); // 기준

        // 출근/퇴근 시간 처리
        LocalTime workOn = LocalTime.parse(shift.getWorkOnHhmm(), formatter);
        LocalTime workOff = LocalTime.parse(shift.getWorkOffHhmm(), formatter);
        LocalDateTime onTime = LocalDateTime.of(today, workOn);
        LocalDateTime offTime = LocalDateTime.of(today, workOff);
        if (Objects.equals(shift.getWorkOffDayType(), "N1")) {
            offTime = offTime.plusDays(1); // 익일 퇴근
        }
        Duration total = Duration.between(onTime, offTime);

        // 휴게1 처리
        Duration break1 = Duration.ZERO;
        if (!shift.getBreak1StartHhmm().isEmpty() && !shift.getBreak1EndHhmm().isEmpty()
            && shift.getBreak1StartHhmm().compareTo(shift.getWorkOnHhmm()) > 0) {
                LocalTime break1Start = LocalTime.parse(shift.getBreak1StartHhmm(), formatter);
                LocalTime break1End = LocalTime.parse(shift.getBreak1EndHhmm(), formatter);
                LocalDateTime b1Start = LocalDateTime.of(today, break1Start);
                LocalDateTime b1End = LocalDateTime.of(today, break1End);
                if (!Objects.equals(shift.getBreak1StartDayType(), shift.getBreak1EndDayType())) {
                    b1End = b1End.plusDays(1); // 익일 종료
                }
                break1 = Duration.between(b1Start, b1End);
            }

        // 휴게2 처리
        Duration break2 = Duration.ZERO;
        if (!shift.getBreak2StartHhmm().isEmpty() && !shift.getBreak2EndHhmm().isEmpty()
            && shift.getBreak2StartHhmm().compareTo(shift.getWorkOffHhmm()) < 0) {
            LocalTime break2Start = LocalTime.parse(shift.getBreak2StartHhmm(), formatter);
            LocalTime break2End = LocalTime.parse(shift.getBreak2EndHhmm(), formatter);
            LocalDateTime b2Start = LocalDateTime.of(today, break2Start);
            LocalDateTime b2End = LocalDateTime.of(today, break2End);
            if (!Objects.equals(shift.getBreak2StartDayType(), shift.getBreak2EndDayType())) {
                b2End = b2End.plusDays(1); // 익일 종료
            }
            break2 = Duration.between(b2Start, b2End);
        }

        return total.minus(break1).minus(break2);
    }

    /* 실적 근무 시간 계산 */
    public static Duration getRealWorkTime(String checkIn, String checkOut, ShiftMaster shift, LocalDate workDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");

        Duration planned = getTotalWorkTime(shift);

        if (checkIn == null) return planned;

        LocalTime workOn = LocalTime.parse(shift.getWorkOnHhmm(), formatter);
        LocalTime workOff = LocalTime.parse(shift.getWorkOffHhmm(), formatter);
        LocalTime inTime = LocalTime.parse(checkIn.substring(0, 4), formatter);
        LocalTime outTime = (checkOut != null) ? LocalTime.parse(checkOut.substring(0, 4), formatter) : null;

        // 출근, 퇴근 시간을 LocalDateTime 으로 변환
        LocalDateTime inDateTime = LocalDateTime.of(workDate, inTime);

        // 퇴근 시간이 출근 시간보다 이전이라면 다음날로 처리
        LocalDateTime outDateTime = null;
        if (outTime != null) {
            outDateTime = LocalDateTime.of(workDate, outTime);
            if (outDateTime.isBefore(inDateTime)) {
                outDateTime = outDateTime.plusDays(1);
            }
        }

        LocalDateTime workOnDateTime = LocalDateTime.of(workDate, workOn);
        LocalDateTime workOffDateTime = LocalDateTime.of(workDate, workOff);
        if (workOffDateTime.isBefore(workOnDateTime)) {
            workOffDateTime = workOffDateTime.plusDays(1);
        }

        // 지각 시간 계산
        if (inDateTime.isAfter(workOnDateTime)) {
            Duration late = Duration.between(workOnDateTime, inDateTime);
            planned = planned.minus(late);
        }

        // 조기 퇴근 계산
        if (outDateTime != null && outDateTime.isBefore(workOffDateTime)) {
            Duration earlyLeave = Duration.between(outDateTime, workOffDateTime);
            planned = planned.minus(earlyLeave);
        }

        // 음수 방지
        return planned.isNegative() ? Duration.ZERO : planned;
    }
}
