package io.github.mojunsul.blog.domain.auth.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mojunsul.blog.domain.auth.entity.Admin;

public interface AdminRepository extends JpaRepository<Admin, Long> {}
