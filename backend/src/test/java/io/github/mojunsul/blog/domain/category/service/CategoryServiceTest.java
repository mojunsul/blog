package io.github.mojunsul.blog.domain.category.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import io.github.mojunsul.blog.domain.category.dto.AdminCategoryResponse;
import io.github.mojunsul.blog.domain.category.entity.Category;
import io.github.mojunsul.blog.domain.category.repository.CategoryRepository;
import io.github.mojunsul.blog.fixture.CategoryFixture;
import io.github.mojunsul.blog.global.exception.BusinessException;
import io.github.mojunsul.blog.global.exception.ErrorCode;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

	@Mock
	CategoryRepository categoryRepository;

	@InjectMocks
	CategoryService categoryService;

	@Test
	void 생성하면_저장한_값을_응답한다() {
		// given
		given(categoryRepository.saveAndFlush(any(Category.class))).willAnswer(i -> i.getArgument(0));

		// when
		AdminCategoryResponse result = categoryService.create("Backend", "backend");

		// then
		assertThat(result.name()).isEqualTo("Backend");
		assertThat(result.slug()).isEqualTo("backend");
	}

	@Test
	void 생성_flush에서_제약_위반이_나면_DUPLICATE_SLUG다() {
		// given
		given(categoryRepository.saveAndFlush(any(Category.class)))
			.willThrow(new DataIntegrityViolationException("dup"));

		// when & then
		assertThatThrownBy(() -> categoryService.create("Backend", "backend")).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.DUPLICATE_SLUG);
	}

	@Test
	void 수정하면_이름과_slug를_바꾼다() {
		// given
		given(categoryRepository.findById(1L)).willReturn(Optional.of(CategoryFixture.category("old")));

		// when
		AdminCategoryResponse result = categoryService.update(1L, "Infra", "infra");

		// then
		assertThat(result.name()).isEqualTo("Infra");
		assertThat(result.slug()).isEqualTo("infra");
	}

	@Test
	void 없는_id를_수정하면_CATEGORY_NOT_FOUND다() {
		// given
		given(categoryRepository.findById(1L)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> categoryService.update(1L, "Infra", "infra")).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
	}

	@Test
	void 수정_flush에서_제약_위반이_나면_DUPLICATE_SLUG다() {
		// given
		given(categoryRepository.findById(1L)).willReturn(Optional.of(CategoryFixture.category("old")));
		willThrow(new DataIntegrityViolationException("dup")).given(categoryRepository).flush();

		// when & then
		assertThatThrownBy(() -> categoryService.update(1L, "Infra", "infra")).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.DUPLICATE_SLUG);
	}

	@Test
	void 수정_flush에서_낙관적_락_예외가_나면_CATEGORY_NOT_FOUND다() {
		// given
		given(categoryRepository.findById(1L)).willReturn(Optional.of(CategoryFixture.category("old")));
		willThrow(new ObjectOptimisticLockingFailureException(Category.class, 1L)).given(categoryRepository).flush();

		// when & then
		assertThatThrownBy(() -> categoryService.update(1L, "Infra", "infra")).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
	}

	@Test
	void 삭제된_행이_없으면_CATEGORY_NOT_FOUND다() {
		// given
		given(categoryRepository.deleteByIdReturningCount(1L)).willReturn(0);

		// when & then
		assertThatThrownBy(() -> categoryService.delete(1L)).isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).errorCode())
			.isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
	}

	@Test
	void 삭제된_행이_있으면_예외_없이_끝난다() {
		// given
		given(categoryRepository.deleteByIdReturningCount(1L)).willReturn(1);

		// when & then
		assertThatCode(() -> categoryService.delete(1L)).doesNotThrowAnyException();
	}
}
