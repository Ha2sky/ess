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
        LocalDate today = LocalDate.now(); // 기준 날짜

        // 출근/퇴근 시간 설정
        LocalTime workOn = LocalTime.parse(shift.getWorkOnHhmm(), formatter);
        LocalTime workOff = LocalTime.parse(shift.getWorkOffHhmm(), formatter);
        LocalDateTime onTime = LocalDateTime.of(today, workOn);
        LocalDateTime offTime = LocalDateTime.of(today, workOff);

        if ("N1".equals(shift.getWorkOffDayType())) {
            offTime = offTime.plusDays(1); // 익일 퇴근 처리
        }

        Duration total = Duration.between(onTime, offTime);

        // 휴게시간 계산
        Duration break1 = calculateBreakDuration(shift.getBreak1StartHhmm(), shift.getBreak1EndHhmm(),
            shift.getBreak1StartDayType(), shift.getBreak1EndDayType(),
            onTime, offTime, formatter, today);

        Duration break2 = calculateBreakDuration(shift.getBreak2StartHhmm(), shift.getBreak2EndHhmm(),
            shift.getBreak2StartDayType(), shift.getBreak2EndDayType(),
            onTime, offTime, formatter, today);

        return total.minus(break1).minus(break2);
    }

    // 휴게시간 차감
    private static Duration calculateBreakDuration(String startHhmm, String endHhmm,
        String startDayType, String endDayType,
        LocalDateTime onTime, LocalDateTime offTime,
        DateTimeFormatter formatter, LocalDate baseDate) {
        if (startHhmm == null || endHhmm == null || startHhmm.isEmpty() || endHhmm.isEmpty()) {
            return Duration.ZERO;
        }

        LocalTime startTime = LocalTime.parse(startHhmm, formatter);
        LocalTime endTime = LocalTime.parse(endHhmm, formatter);

        LocalDateTime breakStart = LocalDateTime.of(baseDate, startTime);
        LocalDateTime breakEnd = LocalDateTime.of(baseDate, endTime);

        if (!Objects.equals(startDayType, endDayType)) {
            breakEnd = breakEnd.plusDays(1); // 익일 종료
        }

        // 휴게시간이 근무시간 구간과 겹치는 경우만 계산
        if (breakEnd.isBefore(onTime) || breakStart.isAfter(offTime)) {
            return Duration.ZERO;
        }

        // 겹치는 실제 휴게 구간 계산
        LocalDateTime effectiveStart = breakStart.isBefore(onTime) ? onTime : breakStart;
        LocalDateTime effectiveEnd = breakEnd.isAfter(offTime) ? offTime : breakEnd;

        return Duration.between(effectiveStart, effectiveEnd);
    }

    /* 실적 근무 시간 계산 */
    public static Duration getRealWorkTime(String checkIn, String checkOut,
        ShiftMaster shift, LocalDate workDate,
        List<Pair<String, String>> leavePeriods) {

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HHmm");

        if (checkIn == null || checkOut == null) return Duration.ZERO;

        LocalTime inTime = LocalTime.parse(checkIn.substring(0, 4), formatter);
        LocalTime outTime = LocalTime.parse(checkOut.substring(0, 4), formatter);
        if (outTime.isBefore(inTime)) outTime = outTime.plusHours(24); // 야간근무 처리

        LocalTime workOn = LocalTime.parse(shift.getWorkOnHhmm(), formatter);
        LocalTime workOff = LocalTime.parse(shift.getWorkOffHhmm(), formatter);

        // 실제 근무시간 범위 보정
        LocalTime adjustedStart = inTime.isBefore(workOn) ? workOn : inTime;
        LocalTime adjustedEnd = outTime.isAfter(workOff) ? workOff : outTime;

        LocalDateTime checkInTime = LocalDateTime.of(workDate, adjustedStart);
        LocalDateTime checkOutTime = LocalDateTime.of(workDate, adjustedEnd);

        LocalDateTime workOnTime = LocalDateTime.of(workDate, workOn);
        LocalDateTime workOffTime = LocalDateTime.of(workDate, workOff);
        if (workOff.isBefore(workOn)) workOffTime = workOffTime.plusDays(1);
        if (checkOutTime.isBefore(checkInTime)) checkOutTime = checkOutTime.plusDays(1);

        Duration totalWorked = Duration.between(checkInTime, checkOutTime);

        // 1. 휴게시간 차감
        totalWorked = subtractBreakTime(totalWorked, shift, workDate, checkInTime, checkOutTime);

        // 2. 연차 구간 차감 (단, 휴게시간과 겹치는 시간은 제외)
        for (Pair<String, String> period : leavePeriods) {
            LocalDateTime leaveStart = LocalDateTime.of(workDate, LocalTime.parse(period.getLeft(), formatter));
            LocalDateTime leaveEnd = LocalDateTime.of(workDate, LocalTime.parse(period.getRight(), formatter));
            if (leaveEnd.isBefore(leaveStart)) leaveEnd = leaveEnd.plusDays(1);

            // 퇴근 이후는 무시
            if (leaveStart.isAfter(workOffTime)) continue;
            if (leaveEnd.isAfter(workOffTime)) leaveEnd = workOffTime;
            if (leaveStart.isBefore(workOnTime)) leaveStart = workOnTime;

            Duration leaveDuration = Duration.between(leaveStart, leaveEnd);

            // 연차 시간 중 휴게시간과 겹치는 구간 제외
            for (String[] br : new String[][] {
                {shift.getBreak1StartHhmm(), shift.getBreak1EndHhmm()},
                {shift.getBreak2StartHhmm(), shift.getBreak2EndHhmm()}
            }) {
                if (br[0] != null && br[1] != null) {
                    LocalTime brStart = LocalTime.parse(br[0], formatter);
                    LocalTime brEnd = LocalTime.parse(br[1], formatter);
                    LocalDateTime brStartTime = LocalDateTime.of(workDate, brStart);
                    LocalDateTime brEndTime = LocalDateTime.of(workDate, brEnd);
                    if (brEndTime.isBefore(brStartTime)) brEndTime = brEndTime.plusDays(1);

                    Duration overlap = getOverlapDuration(leaveStart, leaveEnd, brStartTime, brEndTime);
                    leaveDuration = leaveDuration.minus(overlap);
                }
            }

            if (leaveDuration.isNegative()) leaveDuration = Duration.ZERO;

            totalWorked = totalWorked.minus(leaveDuration);
        }

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

    // 구간 겹침 계산
    private static Duration getOverlapDuration(LocalDateTime start1, LocalDateTime end1,
        LocalDateTime start2, LocalDateTime end2) {
        LocalDateTime maxStart = start1.isAfter(start2) ? start1 : start2;
        LocalDateTime minEnd = end1.isBefore(end2) ? end1 : end2;
        return minEnd.isAfter(maxStart) ? Duration.between(maxStart, minEnd) : Duration.ZERO;
    }

}
