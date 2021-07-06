package org.starcoin.subscribe.config;

import org.quartz.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.starcoin.subscribe.handler.PendingTxnCleanHandle;

@Configuration
public class QuartzConfig {
    @Bean
    public JobDetail handlePendingTxnClean() {
        return JobBuilder.newJob(PendingTxnCleanHandle.class).withIdentity("pendingTxnClean").storeDurably().build();
    }

    @Bean
    public Trigger testQuartzTrigger() {
        SimpleScheduleBuilder scheduleBuilder = SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInSeconds(10)  //设置时间周期单位秒
                .repeatForever();
        return TriggerBuilder.newTrigger().forJob(handlePendingTxnClean())
                .withIdentity("pendingTxnClean")
                .withSchedule(scheduleBuilder)
                .build();
    }
}
