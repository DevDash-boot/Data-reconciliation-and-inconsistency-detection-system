package com.example.recon.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** recon.schedule.enabled=true 일 때만 스케줄링을 켠다. (기본값 꺼짐) */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "recon.schedule.enabled", havingValue = "true")
public class SchedulingConfig {
}
