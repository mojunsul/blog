package io.github.mojunsul.blog.global.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("blog.admin")
public record AdminProperties(String username, String password, boolean required) {
}
