package io.github.mojunsul.blog.domain.tag.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mojunsul.blog.domain.tag.dto.TagResponse;
import io.github.mojunsul.blog.domain.tag.service.TagService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/tags")
@RequiredArgsConstructor
public class TagController {

	private final TagService tagService;

	@GetMapping
	public List<TagResponse> getTags() {
		return tagService.getTags();
	}
}
