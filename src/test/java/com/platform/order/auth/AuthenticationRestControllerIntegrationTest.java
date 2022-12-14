package com.platform.order.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.servlet.http.Cookie;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;

import com.auth0.jwt.exceptions.JWTDecodeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.platform.order.auth.domain.entity.Role;
import com.platform.order.auth.domain.entity.User;
import com.platform.order.auth.view.dto.AuthDto;
import com.platform.order.security.JwtProviderManager;
import com.platform.order.security.WithJwtMockUser;
import com.platform.order.security.property.JwtConfig;

@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationRestControllerIntegrationTest {

	static final String URI_PREFIX = "/api/users";

	@Container
	static final GenericContainer<?> redis = new GenericContainer<>(
		DockerImageName.parse("redis:latest")).withExposedPorts(6379);

	static {
		redis.start();
		System.setProperty("spring.redis.host", redis.getHost());
		System.setProperty("spring.redis.port", redis.getMappedPort(6379).toString());
	}

	@Autowired
	MockMvc mockMvc;

	@PersistenceContext
	EntityManager entityManager;
	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	JwtProviderManager jwtProviderManager;

	@Autowired
	JwtConfig jwtConfig;

	@Test
	@Transactional
	@DisplayName("???????????? ???????????????.")
	void testLogin() throws Exception {
		//given
		String rawPassword = "1";
		User user = User.builder()
			.username("whyWhale")
			.password(passwordEncoder.encode(rawPassword))
			.nickName("whale")
			.role(Role.USER)
			.build();

		entityManager.persist(user);
		entityManager.clear();

		AuthDto.LoginRequest request = new AuthDto.LoginRequest(user.getUsername(), rawPassword);
		String requestBody = objectMapper.writeValueAsString(request);
		//when
		ResultActions perform = mockMvc.perform(
			post(URI_PREFIX + "/login")
				.content(requestBody)
				.contentType(MediaType.APPLICATION_JSON)
		);

		//then
		MvcResult result = perform.andExpect(status().isOk())
			.andExpect(cookie().exists(jwtConfig.accessToken().header()))
			.andExpect(cookie().maxAge(jwtConfig.accessToken().header(), jwtConfig.accessToken().expirySeconds()))
			.andExpect(cookie().exists(jwtConfig.refreshToken().header()))
			.andExpect(cookie().maxAge(jwtConfig.refreshToken().header(), jwtConfig.refreshToken().expirySeconds()))
			.andReturn();

		Cookie[] cookies = result.getResponse().getCookies();

		String accessToken = getCookieToken(cookies, jwtConfig.accessToken().header());
		String refreshToken = getCookieToken(cookies, jwtConfig.refreshToken().header());

		jwtProviderManager.verify(accessToken);
		jwtProviderManager.verifyRefreshToken(accessToken, refreshToken);
	}

	@WithJwtMockUser
	@Transactional
	@Test
	@DisplayName("??????????????? ??????")
	void testLogout() throws Exception {
		//given
		int expectedExpirySeconds = 0;
		String rawPassword = "1";
		User user = User.builder()
			.username("whyWhale")
			.password(passwordEncoder.encode(rawPassword))
			.nickName("whale")
			.role(Role.USER)
			.build();

		entityManager.persist(user);
		entityManager.clear();

		AuthDto.LoginRequest request = new AuthDto.LoginRequest(user.getUsername(), rawPassword);
		String requestBody = objectMapper.writeValueAsString(request);
		ResultActions loginPerform = mockMvc.perform(
			post(URI_PREFIX + "/login")
				.content(requestBody)
				.contentType(MediaType.APPLICATION_JSON)
		);

		Cookie[] authenticatedCookies = loginPerform.andReturn().getResponse().getCookies();

		//when
		ResultActions perform = mockMvc.perform(
			delete(URI_PREFIX + "/logout").cookie(authenticatedCookies)
		);

		//then
		MvcResult result = perform.andExpect(status().isOk())
			.andExpect(cookie().exists(jwtConfig.accessToken().header()))
			.andExpect(cookie().maxAge(jwtConfig.accessToken().header(), expectedExpirySeconds))
			.andExpect(cookie().exists(jwtConfig.refreshToken().header()))
			.andExpect(cookie().maxAge(jwtConfig.refreshToken().header(), expectedExpirySeconds))
			.andReturn();

		Cookie[] cookies = result.getResponse().getCookies();

		String accessToken = getCookieToken(cookies, jwtConfig.accessToken().header());
		String refreshToken = getCookieToken(cookies, jwtConfig.refreshToken().header());

		Assertions.assertThatThrownBy(() ->
			jwtProviderManager.verify(accessToken)
		).isInstanceOf(JWTDecodeException.class);

		Assertions.assertThatThrownBy(() ->
			jwtProviderManager.verifyRefreshToken(accessToken, refreshToken)
		).isInstanceOf(JWTDecodeException.class);
	}

	private String getCookieToken(Cookie[] cookies, String headerName) {
		return Arrays.stream(cookies)
			.filter(cookie -> cookie.getName().equals(headerName))
			.findFirst()
			.orElseThrow(RuntimeException::new)
			.getValue();
	}

}