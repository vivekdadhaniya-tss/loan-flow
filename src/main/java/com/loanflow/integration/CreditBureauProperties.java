package com.loanflow.integration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "credit-bureau")
@Getter
@Setter
public class CreditBureauProperties {
    private String baseUrl;
    private int    timeoutMs;
}
