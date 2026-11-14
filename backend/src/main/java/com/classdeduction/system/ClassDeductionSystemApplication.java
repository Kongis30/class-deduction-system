package com.classdeduction.system;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 应用启动类。
 * 使用精确包路径扫描 Mapper，避免将同级的 Service 接口也当作 MyBatis Mapper 处理。
 */
@SpringBootApplication
@EnableScheduling
@MapperScan({
        "com.classdeduction.system.modules.auth.mapper",
        "com.classdeduction.system.modules.base.mapper",
        "com.classdeduction.system.modules.enrollment.mapper",
        "com.classdeduction.system.modules.schedule.mapper",
        "com.classdeduction.system.modules.lesson.mapper",
        "com.classdeduction.system.modules.systemconfig.mapper",
        "com.classdeduction.system.modules.finance.mapper",
        "com.classdeduction.system.modules.transport.mapper",
        "com.classdeduction.system.modules.report.mapper",
})
public class ClassDeductionSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(ClassDeductionSystemApplication.class, args);
    }
}
