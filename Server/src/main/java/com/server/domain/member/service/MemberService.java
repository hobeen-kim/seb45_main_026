package com.server.domain.member.service;

import java.util.List;

import com.server.domain.cart.entity.Cart;
import com.server.domain.channel.entity.Channel;
import com.server.domain.channel.service.ChannelService;
import com.server.domain.member.entity.Member;
import com.server.domain.member.repository.MemberRepository;
import com.server.domain.member.repository.dto.MemberSubscribesData;
import com.server.domain.member.service.dto.request.MemberServiceRequest;
import com.server.domain.member.service.dto.response.*;
import com.server.domain.member.util.MemberResponseConverter;
import com.server.domain.order.entity.Order;
import com.server.domain.reward.entity.Reward;
import com.server.domain.video.entity.Video;
import com.server.domain.watch.entity.Watch;
import com.server.global.exception.businessexception.memberexception.MemberAccessDeniedException;
import com.server.global.exception.businessexception.memberexception.MemberDuplicateException;
import com.server.global.exception.businessexception.memberexception.MemberNotFoundException;
import com.server.global.exception.businessexception.memberexception.MemberPasswordException;
import com.server.module.email.service.MailService;
import com.server.module.s3.service.AwsService;
import com.server.module.s3.service.dto.FileType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final MailService mailService;
	private final ChannelService channelService;
	private final AwsService awsService;
	private final PasswordEncoder passwordEncoder;
	private final MemberResponseConverter converter;

	public MemberService(MemberRepository memberRepository, MailService mailService, ChannelService channelService,
		AwsService awsService, PasswordEncoder passwordEncoder, MemberResponseConverter converter) {
		this.memberRepository = memberRepository;
		this.mailService = mailService;
		this.channelService = channelService;
		this.awsService = awsService;
		this.passwordEncoder = passwordEncoder;
		this.converter = converter;
	}

	@Transactional
	public void signUp(MemberServiceRequest.Create create) {
		checkDuplicationEmail(create.getEmail());
		mailService.checkEmailCertify(create.getEmail());

		Member member = Member.createMember(create.getEmail(), passwordEncoder.encode(create.getPassword()),
				create.getNickname());

		Member signMember = memberRepository.save(member);
		channelService.createChannel(signMember);
	}

	public ProfileResponse getMember(Long loginId) {
		Member member = validateMember(loginId);

		if (member.getImageFile() == null) {
			return ProfileResponse.getMember(member,
				"프로필 이미지 미등록");
		}

		return ProfileResponse.getMember(member, getProfileUrl(member));
	}

	public Page<RewardsResponse> getRewards(Long loginId, int page, int size) {
		Member member = validateMember(loginId);

		Pageable pageable = PageRequest.of(page - 1, size);

		Page<Reward> rewards = memberRepository.findRewardsByMemberId(member.getMemberId(), pageable);

		return RewardsResponse.convert(rewards);
	}

	public Page<SubscribesResponse> getSubscribes(Long loginId, int page, int size) {
		Member member = validateMember(loginId);

		Pageable pageable = PageRequest.of(page - 1, size);

		Page<Channel> channels = memberRepository.findSubscribeWithChannelForMember(member.getMemberId(), pageable);

		return converter.convertSubscribesToSubscribesResponse(channels);
	}

	public Page<CartsResponse> getCarts(Long loginId, int page, int size) {
		Member member = validateMember(loginId);

		Pageable pageable = PageRequest.of(page - 1, size);

		Page<Cart> carts = memberRepository.findCartsOrderByCreatedDateForMember(member.getMemberId(), pageable);

		return converter.convertCartToCartResponse(carts);
	}

	public Page<OrdersResponse> getOrders(Long loginId, int page, int size, int month) {
		Member member = validateMember(loginId);

		Pageable pageable = PageRequest.of(page - 1, size);

		Page<Order> orders = memberRepository.findOrdersOrderByCreatedDateForMember(member.getMemberId(), pageable, month);

		return converter.convertOrdersToOrdersResponses(orders);
	}

	public Page<PlaylistsResponse> getPlaylists(Long loginId, int page, int size, String sort) {
		Member member = validateMember(loginId);

		Pageable pageable = PageRequest.of(page - 1, size);

		Page<Video> videos = memberRepository.findPlaylistsOrderBySort(member.getMemberId(), pageable, sort);

		return converter.convertVideosToPlaylistsResponses(videos);
	}

	public Page<WatchsResponse> getWatchs(Long loginId, int page, int size, int day) {
		Member member = validateMember(loginId);

		Pageable pageable = PageRequest.of(page - 1, size);

		Page<Watch> watches = memberRepository.findWatchesForMember(member.getMemberId(), pageable, day);

		return converter.convertWatchToWatchResponses(watches);
	}

	@Transactional
	public void updatePassword(MemberServiceRequest.Password request, Long loginId) {
		Member member = validateMember(loginId);

		String password = member.getPassword();
		String newPassword = passwordEncoder.encode(request.getNewPassword());

		validatePassword(request.getPrevPassword(), password);

		member.setPassword(newPassword);
	}

	@Transactional
	public void updateNickname(MemberServiceRequest.Nickname request, Long loginId) {
		Member member = validateMember(loginId);

		member.setNickname(request.getNickname());
	}

	@Transactional
	public void updateImage(Long loginId) {
		Member member = validateMember(loginId);

		member.updateImageFile(member.getEmail());
	}

	@Transactional
	public void deleteMember(Long loginId) {
		Member member = validateMember(loginId);

		memberRepository.delete(member);
	}

	@Transactional
	public void deleteImage(Long loginId) {
		Member member = validateMember(loginId);

		awsService.deleteFile(loginId, member.getImageFile(), FileType.PROFILE_IMAGE);
		member.deleteImageFile();
	}

	public Member findMemberBy(Long id) {
		return memberRepository.findById(id).orElseThrow(
				MemberNotFoundException::new
		);
	}

	public void validatePassword(String password, String encodedPassword) {
		if(!passwordEncoder.matches(password, encodedPassword)) {
			throw new MemberPasswordException();
		}
	}

	public Member validateMember(Long loginId) {
		if (loginId < 1) {
			throw new MemberAccessDeniedException();
		}

		return memberRepository.findById(loginId).orElseThrow(
				MemberNotFoundException::new
		);
	}

	public void checkDuplicationEmail(String email) {
		memberRepository.findByEmail(email).ifPresent(member -> {
			throw new MemberDuplicateException();
		});
	}

	private String getProfileUrl(Member member) {
		return awsService.getFileUrl(
			member.getMemberId(),
			member.getImageFile(),
			FileType.PROFILE_IMAGE);
	}

}
