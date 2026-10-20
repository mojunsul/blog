package io.github.mojunsul.blog.domain.category.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.mojunsul.blog.domain.category.dto.AdminCategoryResponse;
import io.github.mojunsul.blog.domain.category.dto.CategoryResponse;
import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private static final Logger log = LoggerFactory.getLogger(CategoryService.class);

	private final CategoryRepository categoryRepository;

	@Transactional(readOnly = true)
	public List<CategoryResponse> getCategories() {
		return categoryRepository.findAllWithPublishedPostCount();
	}

	// 제약 위반이 커밋 시점에 서비스 밖에서 나오면 500이 되므로 saveAndFlush·flush로 여기서 잡는다
	@Transactional
	public AdminCategoryResponse create(String name, String slug) {
		Category category;
		try {
			category = categoryRepository.saveAndFlush(Category.builder().name(name).slug(slug).build());
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.DUPLICATE_SLUG);
		}
		log.info("Category created: id={}, slug={}", category.getId(), slug);
		return AdminCategoryResponse.from(category);
	}

	@Transactional
	public AdminCategoryResponse update(Long id, String name, String slug) {
		Category category = categoryRepository.findById(id)
			.orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));
		category.update(name, slug);
		try {
			categoryRepository.flush();
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.DUPLICATE_SLUG);
		} catch (ObjectOptimisticLockingFailureException e) {
			// 조회 뒤 flush 전에 다른 요청이 지운 경우
			throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
		}
		log.info("Category updated: id={}, slug={}", id, slug);
		return AdminCategoryResponse.from(category);
	}

	@Transactional
	public void delete(Long id) {
		if (categoryRepository.deleteByIdReturningCount(id) == 0) {
			throw new BusinessException(ErrorCode.CATEGORY_NOT_FOUND);
		}
		log.info("Category deleted: id={}", id);
	}
}
