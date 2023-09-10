package com.server.domain.member.service;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.server.domain.cart.entity.Cart;
import com.server.domain.channel.entity.Channel;
import com.server.domain.member.entity.Authority;
import com.server.domain.member.entity.Member;
import com.server.domain.member.service.dto.request.MemberServiceRequest;
import com.server.domain.member.service.dto.response.CartsResponse;
import com.server.domain.member.service.dto.response.OrdersResponse;
import com.server.domain.member.service.dto.response.PlaylistChannelResponse;
import com.server.domain.member.service.dto.response.PlaylistsResponse;
import com.server.domain.member.service.dto.response.RewardsResponse;
import com.server.domain.member.service.dto.response.SubscribesResponse;
import com.server.domain.member.service.dto.response.WatchsResponse;
import com.server.domain.order.entity.Order;
import com.server.domain.question.entity.Question;
import com.server.domain.reward.entity.RewardType;
import com.server.domain.video.entity.Video;
import com.server.domain.watch.entity.Watch;
import com.server.global.exception.businessexception.memberexception.MemberAccessDeniedException;
import com.server.global.exception.businessexception.memberexception.MemberDuplicateException;
import com.server.global.exception.businessexception.memberexception.MemberNotFoundException;
import com.server.global.exception.businessexception.memberexception.MemberNotUpdatedException;
import com.server.global.exception.businessexception.memberexception.MemberPasswordException;
import com.server.global.testhelper.ServiceTest;
import com.server.module.s3.service.AwsService;
import com.server.module.s3.service.dto.FileType;

public class MemberServiceTest extends ServiceTest {

	@Autowired PasswordEncoder passwordEncoder;
	@Autowired MemberService memberService;
	@Autowired AwsService awsService;

	@Test
	@DisplayName("로그인한 회원의 프로필 조회가 성공적으로 되는지 검증한다.")
	void getMember() {
		Member member = Member.builder()
			.email("test@gmail.com")
			.password(passwordEncoder.encode("1q2w3e4r!"))
			.nickname("test")
			.authority(Authority.ROLE_USER)
			.reward(1000)
			.imageFile("imageName")
			.build();

		em.persist(member);
		em.flush();

		Long id = member.getMemberId();

		String fileUrl = awsService.getFileUrl(id, member.getImageFile(), FileType.PROFILE_IMAGE);

		assertThat(memberService.getMember(id).getImageUrl()).isEqualTo(fileUrl);
	}

	@Test
	@DisplayName("로그인한 회원의 구독 목록 조회가 제대로 수행되는지 검증한다.")
	void getSubscribes() throws InterruptedException {
		Member owner1 = createAndSaveMember();
		Channel channel1 = createAndSaveChannel(owner1);

		Member owner2 = createAndSaveMember();
		Channel channel2 = createAndSaveChannel(owner2);

		Member owner3 = createAndSaveMember();
		Channel channel3 = createAndSaveChannel(owner3);

		Member loginMember = createAndSaveMember();

		createAndSaveSubscribe(loginMember, channel1);
		Thread.sleep(100L);
		createAndSaveSubscribe(loginMember, channel2);
		Thread.sleep(100L);
		createAndSaveSubscribe(loginMember, channel3);

		Page<SubscribesResponse> responses =
			memberService.getSubscribes(loginMember.getMemberId(), 1, 10);

		assertThat(responses.getTotalElements()).isEqualTo(3);
		assertThat(responses.getTotalPages()).isEqualTo(1);

		Iterator<SubscribesResponse> responseIterator = responses.iterator();

		assertThat(responseIterator.next().getMemberId()).isEqualTo(channel3.getMember().getMemberId());
		assertThat(responseIterator.next().getMemberId()).isEqualTo(channel2.getMember().getMemberId());
		assertThat(responseIterator.next().getMemberId()).isEqualTo(channel1.getMember().getMemberId());
	}

	@Test
	@DisplayName("로그인한 사용자의 리워드 목록을 조회한다.")
	void getRewards() {
		Member member = createAndSaveMember();
		Channel channel = createAndSaveChannel(member);
		Video video = createAndSaveVideo(channel);
		Question question = createAndSaveQuestion(video);

		Member user = createAndSaveMember();

		createAndSaveVideoReward(user, video);
		createAndSaveQuestionReward(user, question);

		Page<RewardsResponse> page = memberService.getRewards(user.getMemberId(), 1, 10);

		assertThat(page.getTotalElements()).isEqualTo(2);
		assertThat(page.getTotalPages()).isEqualTo(1);

		Iterator<RewardsResponse> pageIterator = page.iterator();

		assertThat(pageIterator.next().getRewardType()).isEqualTo(RewardType.QUIZ);
		assertThat(pageIterator.next().getRewardType()).isEqualTo(RewardType.VIDEO);
	}

	@Test
	@DisplayName("로그인한 회원의 장바구니 목록을 조회한다.")
	void getCarts() throws InterruptedException {
		Member user = createAndSaveMember();

		List<Cart> carts = new ArrayList<>();
		List<Video> videos = new ArrayList<>();

		for (int i = 0; i < 20; i++) {
			Member member = createAndSaveMember();
			Channel channel = createAndSaveChannel(member);
			Video video = createAndSaveVideo(channel);

			videos.add(video);
			carts.add(createAndSaveCart(user, video));
			Thread.sleep(50);
		}

		Page<CartsResponse> page = memberService.getCarts(user.getMemberId(), 1, 10);

		assertThat(page.getTotalElements()).isEqualTo(20);
		assertThat(page.getTotalPages()).isEqualTo(2);

		assertThat(page.getContent())
			.isSortedAccordingTo(Comparator.comparing(CartsResponse::getCreatedDate).reversed());
	}

	@Test
	@DisplayName("로그인한 회원의 결제 목록을 조회한다.")
	void getOrders() {
		Member user = createAndSaveMember();
		List<Order> firstlast = new ArrayList<>();

		int pages = 1;
		int size = 10;

		createOrders(user, firstlast);

		em.flush();
		em.clear();

		Page<OrdersResponse> page = memberService.getOrders(user.getMemberId(), pages, size, 6);

		assertThat(page.getContent().size()).isEqualTo(size);
		assertThat(page.getTotalElements()).isEqualTo(20);
		assertThat(page.getTotalPages()).isEqualTo(page.getTotalElements()/size);

		assertThat(page.getContent().get(0).getOrderId()).isEqualTo(firstlast.get(1).getOrderId());
		assertThat(page.getContent().get(9).getOrderId()).isEqualTo(firstlast.get(0).getOrderId());
	}

	@Test
	@DisplayName("보관함 목록을 채널별로 그룹화해서 조회한다")
	void getGroupPlaylists() {
		Member user = createAndSaveMember();
		Member member1 = createAndSaveMember();
		Member member2 = createAndSaveMember();
		Channel channel1 = createAndSaveChannelWithName(member1, "1aaaaaaaaa");
		Channel channel2 = createAndSaveChannelWithName(member2, "0aaaaaaaa");
		createAndSaveVideo(channel1);
		createAndSaveVideo(channel1);
		createAndSaveVideo(channel1);
		createAndSaveVideo(channel1);
		createAndSaveSubscribe(user, channel1);

		for (int x = 1; x < 21; x++) {
			List<Video> videos = new ArrayList<>();

			Channel channel;
			Video video;

			Member member = createAndSaveMember();
			if (x < 5) {
				video = createAndSaveVideo(channel1);
			} else if (x > 4 && x < 10) {
				video = createAndSaveVideo(channel2);
			} else {
				channel = createAndSaveChannelWithName(member, generateRandomString());
				video = createAndSaveVideo(channel);
			}

			videos.add(video);

			createAndSaveOrderWithPurchaseComplete(user, videos, 0);
		}

		Page<PlaylistChannelResponse> responses =
			memberService.getChannelForPlaylist(user.getMemberId(), 1, 10);

		List<PlaylistChannelResponse> playlistChannelResponses = responses.getContent();

		assertThat(playlistChannelResponses).isSortedAccordingTo(
			Comparator.comparing(PlaylistChannelResponse::getChannelName)
		);
	}

	@Test
	@DisplayName("로그인한 사용자의 구매한 강의 목록 보관함을 조회한다.")
	void getPlaylists() {
		Member user = createAndSaveMember();
		List<Video> firstlast = new ArrayList<>();

		for (int x = 1; x < 21; x++) {
			List<Video> videos = new ArrayList<>();

			String name = generateRandomString();

			Member member = createAndSaveMember();
			Channel channel = createAndSaveChannelWithName(member, name);
			Video video = createAndSaveVideo(channel);

			videos.add(video);

			createAndSaveOrderWithPurchaseComplete(user, videos, 0);

			if(x == 11 || x == 20) {
				firstlast.add(video);
			}
		}

		Page<PlaylistsResponse> page = memberService.getPlaylists(user.getMemberId(), 1, 10, "createdDate");

		assertThat(page.getTotalElements()).isEqualTo(20);
		assertThat(page.getContent().size()).isEqualTo(10);
		assertThat(page.getTotalPages()).isEqualTo(2);

		assertThat(page.getContent().get(0).getVideoId()).isEqualTo(firstlast.get(1).getVideoId());
		assertThat(page.getContent().get(9).getVideoId()).isEqualTo(firstlast.get(0).getVideoId());
	}

	@Test
	@DisplayName("로그인한 사용자의 시청기록을 최신순으로 조회한다.")
	void getWatchs() {
		Member user = createAndSaveMember();

		for (int i = 0; i < 20; i++) {
			String name = generateRandomString();

			Member member = createAndSaveMember();
			Channel channel = createAndSaveChannelWithName(member, name);
			Video video = createAndSaveVideo(channel);

			createAndSaveWatch(user, video);
		}

		em.flush();
		em.clear();

		Page<WatchsResponse> page = memberService.getWatchs(user.getMemberId(), 1, 10, 7);

		assertThat(page.getTotalElements()).isEqualTo(20);
		assertThat(page.getTotalPages()).isEqualTo(2);

		assertThat(page.getContent()).isSortedAccordingTo(
			Comparator.comparing(WatchsResponse::getModifiedDate).reversed()
		);
	}

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
		String imageName = "profile20230907140835";

		memberService.updateImage(loginId, imageName);

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

		memberService.deleteImage(loginId);

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

	private void createOrders(Member user, List<Order> firstlast) {
		for (int x = 1; x < 21; x++) {
			List<Video> videos = new ArrayList<>();

			for (int i = 0; i < 3; i++) {
				Member member = createAndSaveMember();
				Channel channel = createAndSaveChannel(member);
				Video video = createAndSaveVideo(channel);

				videos.add(video);
			}

			Order order = createAndSaveOrderWithPurchaseComplete(user, videos, 0);
			if(x == 11 || x == 20) {
				firstlast.add(order);
			}
		}
	}

	private String generateRandomString() {
		String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
		StringBuilder randomString = new StringBuilder(10);
		Random random = new SecureRandom();

		for (int i = 0; i < 10; i++) {
			int randomIndex = random.nextInt(characters.length());
			char randomChar = characters.charAt(randomIndex);
			randomString.append(randomChar);
		}

		return randomString.toString();
	}

	private Watch createAndSaveWatch(Member loginMember, Video video) {
		Watch watch = Watch.createWatch(loginMember, video);
		em.persist(watch);

		return watch;
	}
}
