package com.example.disputeportal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param windowDays how many days after a transaction one can still be dispute. */
@ConfigurationProperties(prefix = "app.dispute")
public record DisputeProperties(@DefaultValue("120") int windowDays) {
}
