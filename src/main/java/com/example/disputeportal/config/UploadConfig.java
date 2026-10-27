package com.example.disputeportal.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Limits for multipart requests, taken from {@link EvidenceProperties} so the web server's limit and the service's
 * limit can never drift apart. Declaring this bean replaces Spring Boot's default multipart configuration.
 *
 * The container stops reading an upload once it passes the limit, so an oversized file is never written to disk.
 */
@Configuration
public class UploadConfig {

    /** Room for the form's other fields and the multipart framing on top of the file itself. */
    private static final DataSize REQUEST_OVERHEAD = DataSize.ofKilobytes(256);

    @Bean
    public MultipartConfigElement multipartConfigElement(EvidenceProperties properties) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(DataSize.ofBytes(properties.maxFileBytes()));
        factory.setMaxRequestSize(DataSize.ofBytes(properties.maxFileBytes() + REQUEST_OVERHEAD.toBytes()));
        return factory.createMultipartConfig();
    }
}
