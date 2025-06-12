package com.jb.ess.attendance.service;

import com.jb.ess.common.domain.AttHistory;
import com.jb.ess.common.mapper.AttendanceApplyMapper;
import com.jb.ess.common.util.DateUtil;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HistoryService {
    private final AttendanceApplyMapper attendanceApplyMapper;

    public List<AttHistory> setAttList(LocalDate startDate, LocalDate endDate,
                                       String workType, String status, String empCode) {
        if (workType == null || workType.isEmpty()) {
            return attendanceApplyMapper.getAllAttList(DateUtil.reverseFormatDate(startDate),
                                                       DateUtil.reverseFormatDate(endDate),
                                                       status, empCode);
        }
        else if (workType.equals("연장근로")) {

        }
        else if (workType.equals("휴일근로")) {

        }
        else if (workType.equals("기타근태")) {

        }
        else if (workType.equals("조퇴외출반차")) {

        }
        return null;
    }
}
