package com.server.domain.video.service;

import com.server.domain.cart.entity.Cart;
import com.server.domain.cart.repository.CartRepository;
import com.server.domain.category.entity.Category;
import com.server.domain.category.repository.CategoryRepository;
import com.server.domain.member.entity.Member;
import com.server.domain.member.repository.MemberRepository;
import com.server.domain.reply.controller.convert.ReplySort;
import com.server.domain.reply.dto.ReplyCreateResponse;
import com.server.domain.reply.dto.ReplyCreateServiceApi;
import com.server.domain.reply.dto.ReplyInfo;
import com.server.domain.reply.entity.Reply;
import com.server.domain.reply.repository.ReplyRepository;
import com.server.domain.video.entity.Video;
import com.server.domain.video.entity.VideoStatus;
import com.server.domain.video.repository.VideoRepository;
import com.server.domain.video.service.dto.request.VideoCreateServiceRequest;
import com.server.domain.video.service.dto.request.VideoCreateUrlServiceRequest;
import com.server.domain.video.service.dto.request.VideoGetServiceRequest;
import com.server.domain.video.service.dto.request.VideoUpdateServiceRequest;
import com.server.domain.video.service.dto.response.VideoCreateUrlResponse;
import com.server.domain.video.service.dto.response.VideoDetailResponse;
import com.server.domain.video.service.dto.response.VideoPageResponse;
import com.server.domain.watch.entity.Watch;
import com.server.domain.watch.repository.WatchRepository;
import com.server.global.exception.businessexception.categoryexception.CategoryNotFoundException;
import com.server.global.exception.businessexception.memberexception.MemberAccessDeniedException;
import com.server.global.exception.businessexception.memberexception.MemberNotFoundException;
import com.server.global.exception.businessexception.replyException.ReplyNotValidException;
import com.server.global.exception.businessexception.videoexception.*;
import com.server.module.s3.service.AwsService;
import com.server.module.s3.service.dto.FileType;
import com.server.module.s3.service.dto.ImageType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Transactional(readOnly = true)
public class VideoService {

    private final VideoRepository videoRepository;
    private final MemberRepository memberRepository;
    private final WatchRepository watchRepository;
    private final CategoryRepository categoryRepository;
    private final CartRepository cartRepository;
    private final AwsService awsService;
    private final ReplyRepository replyRepository;

    public VideoService(VideoRepository videoRepository, MemberRepository memberRepository,
                        WatchRepository watchRepository, CategoryRepository categoryRepository,
                        CartRepository cartRepository, AwsService awsService, ReplyRepository replyRepository) {
        this.videoRepository = videoRepository;
        this.memberRepository = memberRepository;
        this.watchRepository = watchRepository;
        this.categoryRepository = categoryRepository;
        this.cartRepository = cartRepository;
        this.awsService = awsService;
        this.replyRepository = replyRepository;
    }

    public Page<VideoPageResponse> getVideos(Long loginMemberId, VideoGetServiceRequest request) {

        Member member = verifiedMemberOrNull(loginMemberId);

        Page<Video> videos = videoRepository.findAllByCond(request.toDataRequest());

        List<Boolean> isPurchaseInOrder = isPurchaseInOrder(member, videos.getContent());

        List<Boolean> isSubscribeInOrder = isSubscribeInOrder(member, videos.getContent(), request.isSubscribe());

        List<String[]> urlsInOrder = getThumbnailAndImageUrlsInOrder(videos.getContent());

        List<Long> videoIdsInCart = getVideoIdsInCart(member, videos.getContent());

        return VideoPageResponse.of(videos,
                isPurchaseInOrder,
                isSubscribeInOrder,
                urlsInOrder,
                videoIdsInCart);
    }

    @Transactional
    public VideoDetailResponse getVideo(Long loginMemberId, Long videoId) {

        Video video = existVideo(videoId);

        Member member = verifiedMemberOrNull(loginMemberId);

        Map<String, Boolean> isPurchaseAndIsReplied = getIsPurchaseAndIsReplied(member, videoId);

        checkIfVideoClosed(isPurchaseAndIsReplied.get("isPurchased"), video);

        watch(member, video);

        List<Long> videoIdsInCart = getVideoIdsInCart(member, List.of(video));

        return VideoDetailResponse.of(video,
                isSubscribed(member, video),
                getVideoUrls(video),
                isPurchaseAndIsReplied,
                !videoIdsInCart.isEmpty());
    }

    @Transactional
    public VideoCreateUrlResponse getVideoCreateUrl(Long loginMemberId, VideoCreateUrlServiceRequest request) {

        checkValidVideoName(request.getFileName());

        Member member = verifiedMemberWithChannel(loginMemberId);

        checkDuplicateVideoNameInChannel(loginMemberId, request.getFileName());

        Video video = Video.createVideo(
                member.getChannel(),
                request.getFileName(),
                0,
                "uploading",
                new ArrayList<>());

        videoRepository.save(video);

        String location = video.getVideoId() + "/" + request.getFileName();

        return VideoCreateUrlResponse.builder()
                .videoUrl(getUploadVideoUrl(loginMemberId, location))
                .thumbnailUrl(getUploadThumbnailUrl(loginMemberId, location, request.getImageType()))
                .build();
    }

    private void checkValidVideoName(String fileName) {
        if (fileName.contains("/")) {
            throw new VideoNameNotValidException("/");
        }
    }

    @Transactional
    public Long createVideo(Long loginMemberId, VideoCreateServiceRequest request) {

        Video video = verifedVideo(loginMemberId, request.getVideoName());

        additionalCreateProcess(request, video);

        return video.getVideoId();
    }

    private void additionalCreateProcess(VideoCreateServiceRequest request, Video video) {

        List<Category> categories = verifiedCategories(request.getCategories());

        video.additionalCreateProcess(request.getPrice(), request.getDescription());

        video.updateCategory(categories);
    }

    @Transactional
    public void updateVideo(Long loginMemberId, VideoUpdateServiceRequest request) {

        Video video = verifedVideo(loginMemberId, request.getVideoId());

        video.updateVideo(request.getDescription());
    }

    @Transactional
    public Boolean changeCart(Long loginMemberId, Long videoId) {

        Video video = existVideo(videoId);

        Member member = verifiedMember(loginMemberId);

        return createOrDeleteCart(member, video);
    }

    @Transactional
    public void deleteCarts(Long loginMemberId, List<Long> videoIds) {

        Member member = verifiedMember(loginMemberId);

        cartRepository.deleteByMemberAndVideoIds(member, videoIds);
    }

    @Transactional
    public void deleteVideo(Long loginMemberId, Long videoId) {

        Video video = verifedVideo(loginMemberId, videoId);

        video.close();
    }

    private List<Boolean> isPurchaseInOrder(Member loginMember, List<Video> content) {

        if(loginMember == null) {
            return IntStream.range(0, content.size())
                    .mapToObj(i -> false)
                    .collect(Collectors.toList());
        }

        List<Long> videoIds = content.stream()
                .map(Video::getVideoId)
                .collect(Collectors.toList());

        return memberRepository.checkMemberPurchaseVideos(loginMember.getMemberId(), videoIds);
    }

    private List<Boolean> isSubscribeInOrder(Member loginMember, List<Video> content, boolean subscribe) {

        if(loginMember == null) {
            return IntStream.range(0, content.size())
                    .mapToObj(i -> false)
                    .collect(Collectors.toList());
        }

        if (subscribe) {
            return IntStream.range(0, content.size())
                    .mapToObj(i -> true)
                    .collect(Collectors.toList());
        }

        List<Long> memberIds = content.stream()
                .map(video -> video.getChannel().getMember().getMemberId())
                .collect(Collectors.toList());

        return memberRepository.checkMemberSubscribeChannel(loginMember.getMemberId(), memberIds);
    }

    private List<String[]> getThumbnailAndImageUrlsInOrder(List<Video> content) {

        return content.stream()
                .map(video -> {
                    Member member = video.getChannel().getMember();
                    String[] urls = new String[2];
                    urls[0] = getThumbnailUrl(member.getMemberId(), video);
                    urls[1] = getImageUrl(member);
                    return urls;
                })
                .collect(Collectors.toList());
    }

    private Map<String, String> getVideoUrls(Video video) {

        Map<String, String> urls = new HashMap<>();

        Member owner = video.getChannel().getMember();

        urls.put("videoUrl", awsService.getFileUrl(owner.getMemberId(), video.getVideoFile(), FileType.VIDEO));
        urls.put("thumbnailUrl", getThumbnailUrl(owner.getMemberId(), video));
        urls.put("imageUrl", getImageUrl(owner));

        return urls;
    }

    private String getImageUrl(Member member) {
        return awsService.getFileUrl(member.getMemberId(), member.getImageFile(), FileType.PROFILE_IMAGE);
    }

    private String getThumbnailUrl(Long memberId, Video video) {
        return awsService.getFileUrl(memberId, video.getThumbnailFile(), FileType.THUMBNAIL);
    }


    private String getUploadVideoUrl(Long loginMemberId, String location) {
        return awsService.getUploadVideoUrl(loginMemberId, location);
    }

    private String getUploadThumbnailUrl(Long loginMemberId, String location, ImageType imageType) {
        return awsService.getImageUploadUrl(loginMemberId,
                location,
                FileType.THUMBNAIL,
                imageType);
    }

    private List<Long> getVideoIdsInCart(Member member, List<Video> videos) {

        if(member == null) {
            return Collections.emptyList();
        }

        List<Long> videoIds = videos.stream()
                .map(Video::getVideoId)
                .collect(Collectors.toList());

        return videoRepository.findVideoIdInCart(member.getMemberId(), videoIds);
    }

    private Member verifiedMemberOrNull(Long loginMemberId) {
        return memberRepository.findById(loginMemberId)
                .orElse(null);
    }

    private Member verifiedMember(Long loginMemberId) {
        return memberRepository.findById(loginMemberId).orElseThrow(MemberNotFoundException::new);
    }

    private Member verifiedMemberWithChannel(Long loginMemberId) {
        return memberRepository.findByIdWithChannel(loginMemberId).orElseThrow(MemberNotFoundException::new);
    }

    private Video existVideo(Long videoId) {
        return videoRepository.findVideoDetail(videoId)
                .orElseThrow(VideoNotFoundException::new);
    }

    private Video verifedVideo(Long memberId, Long videoId) {
        Video video = videoRepository.findVideoDetail(videoId)
                .orElseThrow(VideoNotFoundException::new);

        if(!video.getChannel().getMember().getMemberId().equals(memberId)) {
            throw new VideoAccessDeniedException();
        }

        return video;
    }

    private Video verifedVideo(Long memberId, String videoName) {

        return videoRepository.findVideoByNameWithMember(memberId, videoName)
                .orElseThrow(VideoNotFoundException::new);
    }

    private void checkDuplicateVideoNameInChannel(Long memberId, String videoName) {

        videoRepository.findVideoByNameWithMember(memberId, videoName)
                .ifPresent(video -> {
                    throw new VideoNameDuplicateException();
                });
    }

    private void watch(Member member, Video video) {

        video.addView();
        if(member == null) return;

        getOrCreateWatch(member, video);
    }

    private void getOrCreateWatch(Member member, Video video) {
        Watch watch = watchRepository.findByMemberAndVideo(member, video)
                .orElseGet(() -> watchRepository.save(Watch.createWatch(member, video)));

        watch.setLastWatchedTime(LocalDateTime.now());
    }

    private void checkIfVideoClosed(boolean isPurchased, Video video) {
        if(!isPurchased && video.getVideoStatus().equals(VideoStatus.CLOSED)) {
            throw new VideoClosedException(video.getVideoName());
        }
    }

    private Map<String, Boolean> getIsPurchaseAndIsReplied(Member member, Long videoId) {

        if(member == null) {
            return Map.of("isPurchased", false, "isReplied", false);
        }

        Long loginMemberId = member.getMemberId();

        List<Boolean> purchasedAndIsReplied = videoRepository.isPurchasedAndIsReplied(loginMemberId, videoId);

        HashMap<String, Boolean> isPurchaseAndIsReplied = new HashMap<>();
        isPurchaseAndIsReplied.put("isPurchased", purchasedAndIsReplied.get(0));
        isPurchaseAndIsReplied.put("isReplied", purchasedAndIsReplied.get(1));

        return isPurchaseAndIsReplied;
    }

    private Boolean isSubscribed(Member member, Video video) {

        if(member == null) return false;

        return memberRepository.checkMemberSubscribeChannel(
                member.getMemberId(),
                List.of(video.getChannel().getMember().getMemberId())).get(0);
    }

    private List<Category> verifiedCategories(List<String> categoryNames) {
        List<Category> categories = categoryRepository.findByCategoryNameIn(categoryNames);

        if(categories.size() != categoryNames.size()) {
            throw new CategoryNotFoundException();
        }

        return categories;
    }

    private Boolean createOrDeleteCart(Member member, Video video) {

        Cart cart = cartRepository.findByMemberAndVideo(member, video).orElse(null);

        return cart == null ? createCart(member, video) : deleteCart(cart);
    }

    private boolean deleteCart(Cart cart) {
        cartRepository.delete(cart);
        return false;
    }

    private boolean createCart(Member member, Video video) {

        if(video.getVideoStatus().equals(VideoStatus.CLOSED)) {
            throw new VideoClosedException(video.getVideoName());
        }

        cartRepository.save(Cart.createCart(member, video, video.getPrice()));
        return true;
    }

    public Page<ReplyInfo> getReplies(Long videoId, int page, int size, ReplySort replySort, Integer star) {
        Sort sort = Sort.by(Sort.Direction.DESC, replySort.getSort());
        PageRequest pageRequest = PageRequest.of(page, size, sort); //

        if (star != null) { //(별점 필터링 o)
            return replyRepository.findAllByVideoIdAndStarOrStarIsNull(videoId, star, pageRequest);
        } else { //(별점 필터링 x)
            return replyRepository.findAllByVideoIdPaging(videoId, pageRequest);
        }
    }

    public Long createReply(Long loginMemberId, Long videoId, ReplyCreateServiceApi request) {
        Member member = memberRepository.findById(loginMemberId).orElseThrow(() -> new MemberAccessDeniedException());
        Integer star = request.getStar();

        if (star < 1 || star > 10) {
            throw new ReplyNotValidException();
        }

        Video video = videoRepository.findById(videoId).orElseThrow(() -> new VideoNotFoundException());

        ReplyCreateResponse response = ReplyCreateResponse.builder()
                .content(request.getContent())
                .star(request.getStar())
                .member(member)
                .video(video)
                .build();

        Reply savedReply = replyRepository.save(response.toEntity());

        return savedReply.getReplyId();
    }
}
