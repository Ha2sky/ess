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
                                       String applyType, String status, String empCode) {

        if (applyType == null || applyType.isEmpty()) {
            return attendanceApplyMapper.getAllAttList(DateUtil.reverseFormatDate(startDate),
                                                       DateUtil.reverseFormatDate(endDate),
                                                       status, empCode);
        } else if (applyType.equals("연장근로")) {
            return attendanceApplyMapper.getAttList(DateUtil.reverseFormatDate(startDate),
                                                    DateUtil.reverseFormatDate(endDate),
                                                    "연장", status, empCode);
        } else if (applyType.equals("휴일근로")) {
            return attendanceApplyMapper.getAttList(DateUtil.reverseFormatDate(startDate),
                                                    DateUtil.reverseFormatDate(endDate),
                                                    "휴일근무", status, empCode);
        } else if (applyType.equals("조퇴외출반차")) {
            return attendanceApplyMapper.getAttList2(DateUtil.reverseFormatDate(startDate),
                                                    DateUtil.reverseFormatDate(endDate),
                                                    status, empCode);
        } else {
            return attendanceApplyMapper.getAttListEtc(DateUtil.reverseFormatDate(startDate),
                                                       DateUtil.reverseFormatDate(endDate),
                                                       status, empCode);
        }
    }
}
