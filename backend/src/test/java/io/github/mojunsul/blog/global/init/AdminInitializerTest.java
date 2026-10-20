package io.github.mojunsul.blog.global.init;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.mojunsul.blog.domain.auth.entity.Admin;
import io.github.mojunsul.blog.domain.auth.repository.AdminRepository;
import io.github.mojunsul.blog.global.config.AdminProperties;

@ExtendWith(MockitoExtension.class)
class AdminInitializerTest {

	private static final String PASSWORD = "password1234";

	@Mock
	AdminRepository adminRepository;

	@Mock
	PasswordEncoder passwordEncoder;

	private AdminInitializer initializer(String username, String password, boolean required) {
		return new AdminInitializer(adminRepository, passwordEncoder,
			new AdminProperties(username, password, required));
	}

	@Test
	void 관리자가_있으면_환경변수와_무관하게_저장하지_않는다() {
		// given
		given(adminRepository.count()).willReturn(1L);

		// when
		initializer("other", PASSWORD, true).run(null);

		// then
		then(adminRepository).should(never()).save(any());
	}

	@Test
	void 관리자가_없고_값이_있으면_인코딩된_비밀번호로_저장한다() {
		// given
		given(adminRepository.count()).willReturn(0L);
		given(passwordEncoder.encode(PASSWORD)).willReturn("encoded");

		// when
		initializer("admin", PASSWORD, false).run(null);

		// then
		ArgumentCaptor<Admin> captor = ArgumentCaptor.forClass(Admin.class);
		then(adminRepository).should().save(captor.capture());
		assertThat(captor.getValue().getUsername()).isEqualTo("admin");
		assertThat(captor.getValue().getPassword()).isEqualTo("encoded").isNotEqualTo(PASSWORD);
	}

	@ParameterizedTest
	@CsvSource(value = {"'',''", "'  ','  '"})
	void 둘_다_비어_있고_required가_false면_저장하지_않고_예외도_없다(String username, String password) {
		// given
		given(adminRepository.count()).willReturn(0L);

		// when & then
		assertThatCode(() -> initializer(username, password, false).run(null)).doesNotThrowAnyException();
		then(adminRepository).should(never()).save(any());
	}

	@Test
	void 둘_다_비어_있고_required가_true면_예외가_발생한다() {
		// given
		given(adminRepository.count()).willReturn(0L);

		// when & then
		assertThatThrownBy(() -> initializer("", "", true).run(null)).isInstanceOf(IllegalStateException.class);
	}

	@ParameterizedTest
	@CsvSource(value = {"'',password1234,blog.admin.username", "'  ',password1234,blog.admin.username",
		"admin,'',blog.admin.password"})
	void 한쪽만_비어_있으면_required와_무관하게_빠진_키를_담아_예외가_발생한다(String username, String password, String missingKey) {
		// given
		given(adminRepository.count()).willReturn(0L);

		// when & then
		assertThatThrownBy(() -> initializer(username, password, false).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(missingKey);
	}

	@ParameterizedTest
	@ValueSource(strings = {" admin", "admin ", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
	void username이_잘못되면_예외가_발생한다(String username) {
		// given
		given(adminRepository.count()).willReturn(0L);

		// when & then
		assertThatThrownBy(() -> initializer(username, PASSWORD, false).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageNotContaining(PASSWORD);
	}

	@ParameterizedTest
	@ValueSource(strings = {"short123456", "가나다라마바사아자차카타파하가나다라마바사아자차a"})
	void password가_잘못되면_비밀번호_값이_없는_메시지로_예외가_발생한다(String password) {
		// given
		given(adminRepository.count()).willReturn(0L);

		// when & then
		assertThatThrownBy(() -> initializer("admin", password, false).run(null))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageNotContaining(password);
	}

	@ParameterizedTest
	@CsvSource(value = {"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa,password1234",
		"admin,passwordpass", "admin,가나다라마바사아자차카타파하가나다라마바사아자차", "admin,'pass word  12'"})
	void 경계값은_저장한다(String username, String password) {
		// given
		given(adminRepository.count()).willReturn(0L);
		given(passwordEncoder.encode(password)).willReturn("encoded");

		// when
		initializer(username, password, false).run(null);

		// then
		then(adminRepository).should().save(any(Admin.class));
	}
}
