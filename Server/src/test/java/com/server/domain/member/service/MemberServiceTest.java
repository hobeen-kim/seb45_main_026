package com.server.domain.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import com.server.module.s3.service.dto.FileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.server.domain.member.entity.Authority;
import com.server.domain.member.entity.Member;
import com.server.domain.member.repository.MemberRepository;
import com.server.domain.member.service.dto.request.MemberServiceRequest;
import com.server.global.exception.businessexception.memberexception.MemberAccessDeniedException;
import com.server.global.exception.businessexception.memberexception.MemberDuplicateException;
import com.server.global.exception.businessexception.memberexception.MemberNotFoundException;
import com.server.global.exception.businessexception.memberexception.MemberNotUpdatedException;
import com.server.global.exception.businessexception.memberexception.MemberPasswordException;
import com.server.global.testhelper.ServiceTest;
import com.server.module.s3.service.AwsService;

public class MemberServiceTest extends ServiceTest {

	@Autowired PasswordEncoder passwordEncoder;
	@Autowired MemberService memberService;

	@Mock
	private MemberRepository mockMemberRepository;

	@Mock
	private AwsService awsService;

	@InjectMocks
	private MemberService mockMemberService;

	@Test
	@DisplayName("로그인한 사용자의 ID에 맞는 회원 테이블을 삭제한다.")
	void deleteMember() {
		Member member = createAndSaveMember();
		Long id = member.getMemberId();

		memberService.deleteMember(id);

		assertThrows(MemberNotFoundException.class, () -> memberRepository.findById(id).orElseThrow(MemberNotFoundException::new));
	}

	@Test
	@DisplayName("로그인 아이디가 1보다 작지 않은지 로그인한 회원이 맞는지 검증한다.")
	void validateLoginId() {
		Member member = createMember();

		Long validLoginId = member.getMemberId();
		Long invalidLoginIdA = -1L;
		Long invalidLoginIdB = 423515L;

		assertDoesNotThrow(() -> memberService.validateMember(validLoginId));

		assertThrows(MemberAccessDeniedException.class, () -> memberService.validateMember(invalidLoginIdA));
		assertThrows(MemberNotFoundException.class, () -> memberService.validateMember(invalidLoginIdB));
	}

	@Test
	@DisplayName("입력한 패스워드가 일치하는지 검증한다.")
	void validatePassword() {
		Member member = createMember();
		String password = member.getPassword();

		String validPassword = "1q2w3e4r!";
		String invalidPassword = "4q3w2e1r!";

		assertDoesNotThrow(() -> memberService.validatePassword(validPassword, password));

		assertThrows(MemberPasswordException.class, () -> memberService.validatePassword(invalidPassword, password));
	}

	@Test
	@DisplayName("프로필 이미지가 저장되는지 검증한다.")
	void updateImage() {
		Member member = createNoImageMember();

		Long loginId = member.getMemberId();
		String imageName = member.getEmail();

		memberService.updateImage(loginId);

		assertThat(imageName).isEqualTo("test@gmail.com");
		assertThat(member.getImageFile()).isNotNull().isEqualTo(imageName);
	}

	@Test
	@DisplayName("닉네임 변경이 정상적으로 수행되는지 검증한다.")
	void updateNickname() {
		Member member = createMember();
		Long loginId = member.getMemberId();

		MemberServiceRequest.Nickname valid =
			MemberServiceRequest.Nickname.builder().nickname("이름 바꾸기").build();
		MemberServiceRequest.Nickname invalid =
			MemberServiceRequest.Nickname.builder().build();

		assertThrows(MemberNotUpdatedException.class, () -> memberService.updateNickname(invalid, loginId));

		memberService.updateNickname(valid, loginId);
		assertThat(member.getNickname()).isEqualTo(valid.getNickname());
	}

	@Test
	@DisplayName(("패스워드 변경이 정상적으로 수행되는지 검증한다."))
	void updatePassword() {
		Member member = createMember();
		Long loginId = member.getMemberId();

		MemberServiceRequest.Password request = MemberServiceRequest.Password.builder()
			.prevPassword("1q2w3e4r!")
			.newPassword("qwer1234!")
			.build();

		memberService.updatePassword(request, loginId);
		assertThat(passwordEncoder.matches(request.getNewPassword(), member.getPassword())).isTrue();
	}

	@Test
	@DisplayName("이메일 중복 검증이 정상적으로 수행되는지 확인한다.")
	void checkDuplicationEmail() {
		Member member = createMember();

		String existEmail = member.getEmail();
		String notExistEmail = "notexist@email.com";

		assertThrows(MemberDuplicateException.class, () -> memberService.checkDuplicationEmail(existEmail));
		assertDoesNotThrow(() -> memberService.checkDuplicationEmail(notExistEmail));
	}

	@Test
	@DisplayName("프로필 이미지 삭제 테스트")
	void deleteImage() {
		Member member = createMember();
		Long loginId = member.getMemberId();

		given(mockMemberRepository.findById(Mockito.anyLong())).willReturn(Optional.of(member));
		doNothing().when(awsService).deleteFile(anyLong(), anyString(), any(FileType.class));

		mockMemberService.deleteImage(loginId);

		verify(awsService).deleteFile(loginId, "imageName", FileType.PROFILE_IMAGE);
		assertNull(member.getImageFile());
	}

	private Member createMember() {
		Member member = Member.builder()
			.email("test@gmail.com")
			.password(passwordEncoder.encode("1q2w3e4r!"))
			.nickname("test")
			.authority(Authority.ROLE_USER)
			.reward(1000)
			.imageFile("imageName")
			.build();

		memberRepository.save(member);

		return member;
	}

	private Member createNoImageMember() {
		Member member = Member.builder()
			.email("test@gmail.com")
			.password(passwordEncoder.encode("1q2w3e4r!"))
			.nickname("test")
			.authority(Authority.ROLE_USER)
			.reward(1000)
			.build();

		memberRepository.save(member);

		return member;
	}
}
