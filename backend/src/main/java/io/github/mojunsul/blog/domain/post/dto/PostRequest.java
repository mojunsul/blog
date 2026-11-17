package io.github.mojunsul.blog.domain.post.dto;

import java.util.List;

import io.github.mojunsul.blog.domain.post.entity.PostStatus;
import io.github.mojunsul.blog.domain.tag.entity.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PostRequest(
	@NotBlank(message = "제목을 입력하세요") @Size(max = 200, message = "제목은 200자 이하여야 합니다") @Pattern(regexp = "^\\P{Cc}+$", message = "제목에 제어 문자를 쓸 수 없습니다") String title,
	@NotBlank(message = "slug를 입력하세요") @Size(max = 200, message = "slug는 200자 이하여야 합니다") @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$", message = "slug는 영문 소문자·숫자·하이픈만 쓸 수 있습니다") String slug,
	@Size(max = 500, message = "요약은 500자 이하여야 합니다") @Pattern(regexp = "^[^\\u0000]*$", message = "요약에 NUL 문자를 쓸 수 없습니다") String summary,
	@NotBlank(message = "본문을 입력하세요") @Size(max = 100000, message = "본문은 100,000자 이하여야 합니다") @Pattern(regexp = "^[^\\u0000]*$", message = "본문에 NUL 문자를 쓸 수 없습니다") String content,
	@Size(max = 500, message = "썸네일 URL은 500자 이하여야 합니다") @Pattern(regexp = "^https?://[^\\s\\p{Cc}]+$", message = "썸네일 URL은 공백 없는 http(s) 주소여야 합니다") String thumbnailUrl,
	Long categoryId,
	@NotNull(message = "상태를 선택하세요") PostStatus status,
	@Size(max = 10, message = "태그는 10개 이하여야 합니다") List<@NotBlank(message = "빈 태그는 쓸 수 없습니다") @Size(max = 50, message = "태그는 50자 이하여야 합니다") @Pattern(regexp = "^[\\p{L}\\p{N}]+(?:-[\\p{L}\\p{N}]+)*$", message = "태그는 문자·숫자·하이픈만 쓸 수 있습니다") String> tags) {

	// 검증·저장·응답이 모두 제거된 값을 쓰도록 검증 전에 제거하고, 빈 입력이 DB에 빈 문자열로 남지 않게 null로 바꾼다
	public PostRequest {
		title = title == null ? null : title.strip();
		summary = blankToNull(summary);
		thumbnailUrl = blankToNull(thumbnailUrl);
		tags = tags == null ? List.of() : tags.stream().map(PostRequest::normalizeTag).distinct().toList();
	}

	// 검증이 저장될 값을 보게 정규화·중복 제거를 검증 전에 한다. null 항목은 @NotBlank가 거절한다.
	private static String normalizeTag(String tag) {
		return tag == null ? null : Tag.normalize(tag);
	}

	private static String blankToNull(String value) {
		if (value == null) {
			return null;
		}
		String stripped = value.strip();
		return stripped.isEmpty() ? null : stripped;
	}
}
