package com.jb.ess.common.util;

import com.jb.ess.common.domain.ShiftMaster;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.tuple.Pair;

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
    public static Duration getRealWorkTime(String checkIn, String checkOut,
        ShiftMaster shift, LocalDate workDate,
        List<Pair<String, String>> leavePeriods) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");

        if (checkIn == null || checkOut == null) return Duration.ZERO;

        LocalTime inTime = LocalTime.parse(checkIn.substring(0, 4), formatter);
        LocalTime outTime = LocalTime.parse(checkOut.substring(0, 4), formatter);
        if (outTime.isBefore(inTime)) outTime = outTime.plusHours(24); // 다음날 처리

        LocalTime workOn = LocalTime.parse(shift.getWorkOnHhmm(), formatter);
        LocalTime workOff = LocalTime.parse(shift.getWorkOffHhmm(), formatter);

        // 실제 근무 시간 범위 설정
        LocalTime adjustedStart = inTime.isBefore(workOn) ? workOn : inTime;
        LocalTime adjustedEnd = outTime.isAfter(workOff) ? workOff : outTime;

        LocalDateTime checkInTime = LocalDateTime.of(workDate, adjustedStart);
        LocalDateTime checkOutTime = LocalDateTime.of(workDate, adjustedEnd);
        if (checkOutTime.isBefore(checkInTime)) checkOutTime = checkOutTime.plusDays(1);

        Duration totalWorked = Duration.between(checkInTime, checkOutTime);

        // 휴게시간 차감
        totalWorked = subtractBreakTime(totalWorked, shift, workDate, checkInTime, checkOutTime);

        // 전반차, 후반차, 외출, 조퇴 등 차감
        for (Pair<String, String> period : leavePeriods) {
            LocalDateTime leaveStart = LocalDateTime.of(workDate, LocalTime.parse(period.getLeft(), formatter));
            LocalDateTime leaveEnd = LocalDateTime.of(workDate, LocalTime.parse(period.getRight(), formatter));
            if (leaveEnd.isBefore(leaveStart)) leaveEnd = leaveEnd.plusDays(1);

            Duration overlap = getOverlapDuration(checkInTime, checkOutTime, leaveStart, leaveEnd);
            totalWorked = totalWorked.minus(overlap);
        }

        System.out.println("[DEBUG] " + workDate + " Adjusted Work: " + totalWorked);
        return totalWorked.isNegative() ? Duration.ZERO : totalWorked;
    }



    // 휴게시간 차감
    private static Duration subtractBreakTime(Duration original, ShiftMaster shift,
        LocalDate date, LocalDateTime inTime, LocalDateTime outTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");
        Duration result = original;

        String[][] breaks = {
            {shift.getBreak1StartHhmm(), shift.getBreak1EndHhmm()},
            {shift.getBreak2StartHhmm(), shift.getBreak2EndHhmm()}
        };

        for (String[] br : breaks) {
            if (br[0] != null && br[1] != null) {
                LocalTime brStart = LocalTime.parse(br[0], formatter);
                LocalTime brEnd = LocalTime.parse(br[1], formatter);
                LocalDateTime brStartTime = LocalDateTime.of(date, brStart);
                LocalDateTime brEndTime = LocalDateTime.of(date, brEnd);
                if (brEndTime.isBefore(brStartTime)) brEndTime = brEndTime.plusDays(1);

                Duration overlap = getOverlapDuration(inTime, outTime, brStartTime, brEndTime);
                result = result.minus(overlap);
            }
        }

        return result;
    }

    // 구간겹침 계산
    private static Duration getOverlapDuration(LocalDateTime start1, LocalDateTime end1,
        LocalDateTime start2, LocalDateTime end2) {
        LocalDateTime maxStart = start1.isAfter(start2) ? start1 : start2;
        LocalDateTime minEnd = end1.isBefore(end2) ? end1 : end2;

        return minEnd.isAfter(maxStart) ? Duration.between(maxStart, minEnd) : Duration.ZERO;
    }
}
