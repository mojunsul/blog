package io.github.mojunsul.blog.domain.tag.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.tag.dto.TagResponse;
import io.github.mojunsul.blog.domain.tag.repository.TagRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TagService {

	private final TagRepository tagRepository;

	@Transactional(readOnly = true)
	public List<TagResponse> getTags() {
		return tagRepository.findAllWithPublishedPostCount();
	}
}
