package io.github.mojunsul.blog.domain.category.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.mojunsul.blog.domain.category.dto.AdminCategoryResponse;
import io.github.mojunsul.blog.domain.category.dto.CategoryRequest;
import io.github.mojunsul.blog.domain.category.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/categories")
@RequiredArgsConstructor
public class AdminCategoryController {

	private final CategoryService categoryService;

	@PostMapping
	public ResponseEntity<AdminCategoryResponse> create(
		@Valid
		@RequestBody
		CategoryRequest request) {
		AdminCategoryResponse response = categoryService.create(request.name(), request.slug());
		return ResponseEntity.created(URI.create("/api/v1/admin/categories/" + response.id())).body(response);
	}

	@PutMapping("/{id}")
	public AdminCategoryResponse update(
		@PathVariable
		Long id,
		@Valid
		@RequestBody
		CategoryRequest request) {
		return categoryService.update(id, request.name(), request.slug());
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(
		@PathVariable
		Long id) {
		categoryService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
