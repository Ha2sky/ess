package com.jb.ess.pattern.service;

import com.jb.ess.common.domain.ShiftCalendar;
import com.jb.ess.common.domain.ShiftMaster;
import com.jb.ess.common.domain.ShiftPattern;
import com.jb.ess.pattern.mapper.ShiftCalendarMapper;
import com.jb.ess.pattern.mapper.ShiftPatternDtlMapper;
import com.jb.ess.pattern.mapper.ShiftPatternMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PatternService {
    private final ShiftPatternMapper shiftPatternMapper;
    private final ShiftPatternDtlMapper shiftPatternDtlMapper;
    private final ShiftCalendarMapper shiftCalendarMapper;

    public void generateShiftCalendar(String workPatternCode, YearMonth yearMonth) {
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            int dayOfWeek = date.getDayOfWeek().getValue(); // 1: 월 ~ 7: 일
            System.out.println("workPatternCode: " + workPatternCode + ", dayOfWeek: " + dayOfWeek + ", date: " + date.format(formatter));
            String shiftCode = shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, dayOfWeek);

            if (shiftCode != null) {
                String shiftDate = date.format(formatter); // → "20250601" 형식으로 변환
                shiftCalendarMapper.insertShiftCalendar(new ShiftCalendar(
                    workPatternCode,
                    shiftDate,
                    shiftCode
                ));
            }
        }
    }
    /* 각 근태코드에 색상 부여 */
    public Map<String, String> generateShiftCodeColors(List<ShiftMaster> shiftCodes) {
        String[] colors = {
            "bg-red-200", "bg-green-200", "bg-blue-200", "bg-yellow-200",
            "bg-purple-200", "bg-pink-200", "bg-orange-200", "bg-indigo-200",
            "bg-teal-200", "bg-lime-200", "bg-amber-200"
        };

        Map<String, String> colorMap = new HashMap<>();
        int i = 0;
        for (ShiftMaster sm : shiftCodes) {
            colorMap.put(sm.getShiftCode(), colors[i % colors.length]);
            i++;
        }
        return colorMap;
    }

    /* 근태패턴명으로 근태패턴 검색 */
    public List<Map<String, Object>> getPatternCalendar(YearMonth month, String workPatternCode) {
        List<ShiftPattern> patterns = shiftPatternMapper.findPatternsByCode(workPatternCode);
        List<Map<String, Object>> result = new ArrayList<>();

        for (ShiftPattern pattern : patterns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("workPatternCode", pattern.getWorkPatternCode());
            row.put("workPatternName", pattern.getWorkPatternName());

            for (int day = 1; day <= month.lengthOfMonth(); day++) {
                LocalDate date = month.atDay(day);
                DayOfWeek dow = date.getDayOfWeek();
                String shiftCode = getShiftCodeByDayOfWeek(pattern, dow);
                row.put(String.valueOf(day), shiftCode);
            }

            result.add(row);
        }

        return result;
    }

    /* 근태패턴 삭제 */
    public void deletePatternsByCodes(List<String> workPatternCodes) {
        if (workPatternCodes == null) return;
        for (String code : workPatternCodes) {
            if (code == null || code.isEmpty()) continue;
            shiftCalendarMapper.deleteShiftCalendar(code);
            shiftPatternDtlMapper.deletePatternDtl(code);
            shiftPatternMapper.deletePattern(code);
        }
    }

//    /* 근태패턴 저장 */
//    public void savePattern(PatternDetail pattern) {
//        // 유효성 검사, 중복 확인 등 필요한 로직이 있다면 여기서 처리
//        patternMapper.insertShiftPattern(pattern);
//    }

    private String getShiftCodeByDayOfWeek(ShiftPattern pattern, DayOfWeek dow) {
        String workPatternCode = pattern.getWorkPatternCode();
        return switch (dow) {
            case MONDAY -> shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, 1);
            case TUESDAY -> shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, 2);
            case WEDNESDAY -> shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, 3);
            case THURSDAY -> shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, 4);
            case FRIDAY -> shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, 5);
            case SATURDAY -> shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, 6);
            case SUNDAY -> shiftPatternDtlMapper.getShiftCodeByPatternAndDay(workPatternCode, 7);
        };
    }
}