package com.classdeduction.system.common.util;

import com.classdeduction.system.common.time.AppTime;
import com.classdeduction.system.modules.base.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 学号生成器，使用 synchronized 保证并发安全。
 */
@Component
@RequiredArgsConstructor
public class StudentNoGenerator {

    private final StudentMapper studentMapper;

    /**
     * 生成唯一学号，格式：STU + yyyyMMdd + 4位序列号
     */
    public synchronized String generate() {
        String todayPrefix = "STU" + LocalDate.now(AppTime.APP_ZONE_ID)
                .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String maxStudentNo = studentMapper.selectMaxStudentNoByPrefix(todayPrefix);

        int nextSeq = 1;
        if (StringUtils.hasText(maxStudentNo) && maxStudentNo.startsWith(todayPrefix)) {
            try {
                String seqStr = maxStudentNo.substring(todayPrefix.length());
                nextSeq = Integer.parseInt(seqStr) + 1;
            } catch (NumberFormatException e) {
                nextSeq = 1;
            }
        }

        return String.format("%s%04d", todayPrefix, nextSeq);
    }
}
