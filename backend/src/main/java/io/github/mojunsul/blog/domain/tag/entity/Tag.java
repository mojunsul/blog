package io.github.mojunsul.blog.domain.tag.entity;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Tag {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 50)
	private String name;

	@Builder
	private Tag(String name) {
		this.name = normalize(name);
	}

	// Locale.ROOT: 서버 기본 로케일이 터키어여도 I가 ı로 바뀌지 않게 한다.
	public static String normalize(String name) {
		return name.strip().toLowerCase(Locale.ROOT);
	}
}
