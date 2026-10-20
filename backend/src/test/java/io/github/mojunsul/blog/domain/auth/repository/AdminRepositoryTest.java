package io.github.mojunsul.blog.domain.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.mojunsul.blog.domain.auth.entity.Admin;
import io.github.mojunsul.blog.fixture.AdminFixture;
import io.github.mojunsul.blog.support.RepositoryTestSupport;

class AdminRepositoryTest extends RepositoryTestSupport {

	@Autowired
	AdminRepository adminRepository;

	@Test
	void 아이디가_정확히_같은_관리자를_찾는다() {
		// given
		adminRepository.save(AdminFixture.admin("admin"));

		// when
		Admin found = adminRepository.findByUsername("admin").orElseThrow();

		// then
		assertThat(found.getUsername()).isEqualTo("admin");
	}

	@ParameterizedTest
	@ValueSource(strings = {"Admin", "ADMIN", "admin ", " admin", "admin\n"})
	void 대소문자나_앞뒤_공백이_다르면_찾지_못한다(String username) {
		// given
		adminRepository.save(AdminFixture.admin("admin"));

		// when & then
		assertThat(adminRepository.findByUsername(username)).isEmpty();
	}
}
