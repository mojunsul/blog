package io.github.mojunsul.blog.domain.tag.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.mojunsul.blog.domain.tag.entity.Tag;

public interface TagRepository extends JpaRepository<Tag, Long> {

	List<Tag> findAllByNameIn(Collection<String> names);
}
