package com.jb.ess.service;

import com.jb.ess.domain.PatternDetail;
import com.jb.ess.domain.ShiftMaster;
import com.jb.ess.mapper.PatternMapper;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private final PatternMapper patternMapper;

    /* 근태패턴명으로 근태패턴 검색 */
    public List<Map<String, Object>> getPatternCalendar(YearMonth month, String patternName) {
        List<PatternDetail> patterns = patternMapper.findPatternsByName(patternName);
        List<Map<String, Object>> result = new ArrayList<>();

        for (PatternDetail pattern : patterns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("patternCode", pattern.getWorkPatternCode());
            row.put("patternName", pattern.getWorkPatternName());

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

    private String getShiftCodeByDayOfWeek(PatternDetail pattern, DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> pattern.getMonShiftCode();
            case TUESDAY -> pattern.getTueShiftCode();
            case WEDNESDAY -> pattern.getWedShiftCode();
            case THURSDAY -> pattern.getThuShiftCode();
            case FRIDAY -> pattern.getFriShiftCode();
            case SATURDAY -> pattern.getSatShiftCode();
            case SUNDAY -> pattern.getSunShiftCode();
        };
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

    /* 근태패턴 저장 */
    public void savePattern(PatternDetail pattern) {
        // 유효성 검사, 중복 확인 등 필요한 로직이 있다면 여기서 처리
        patternMapper.insertShiftPattern(pattern);
    }
}
