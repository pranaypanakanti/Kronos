package com.ProStriver.config;

import com.ProStriver.auth.OtpProperties;
import com.ProStriver.security.SecurityProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({SecurityProperties.class, OtpProperties.class})
public class PropertiesConfig {
}