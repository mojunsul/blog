package io.github.mojunsul.blog.domain.tag.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mojunsul.blog.domain.tag.entity.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {}
