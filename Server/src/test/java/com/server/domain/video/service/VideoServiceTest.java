package com.server.domain.video.service;

import com.server.domain.category.entity.Category;
import com.server.domain.channel.entity.Channel;
import com.server.domain.member.entity.Member;
import com.server.domain.video.entity.Video;
import com.server.domain.video.service.dto.request.VideoCreateServiceRequest;
import com.server.domain.video.service.dto.request.VideoCreateUrlServiceRequest;
import com.server.domain.video.service.dto.response.VideoCreateUrlResponse;
import com.server.domain.video.service.dto.response.VideoDetailResponse;
import com.server.domain.video.service.dto.response.VideoPageResponse;
import com.server.domain.watch.entity.Watch;
import com.server.global.exception.businessexception.memberexception.MemberNotFoundException;
import com.server.global.exception.businessexception.videoexception.VideoFileNameNotMatchException;
import com.server.global.exception.businessexception.videoexception.VideoUploadNotRequestException;
import com.server.global.testhelper.ServiceTest;
import com.server.module.s3.service.dto.ImageType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.DynamicTest.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

class VideoServiceTest extends ServiceTest {

    @Autowired VideoService videoService;

    @TestFactory
    @DisplayName("page, size, sort, category, memberId, subscribe 를 받아서 비디오 리스트를 반환한다.")
    Collection<DynamicTest> getVideos() {
        //given
        Member owner1 = createAndSaveMember();
        Channel channel1 = createAndSaveChannel(owner1);

        Member owner2 = createAndSaveMember();
        Channel channel2 = createAndSaveChannel(owner2);

        Member loginMember = createAndSaveMember(); // 로그인한 회원

        createAndSaveSubscribe(loginMember, channel1); // loginMember 가 owner1 의 channel1 을 구독

        Video video1 = createAndSaveVideo(channel1);
        Video video2 = createAndSaveVideo(channel1);
        Video video3 = createAndSaveVideo(channel1);
        Video video4 = createAndSaveVideo(channel1);
        Video video5 = createAndSaveVideo(channel2);
        Video video6 = createAndSaveVideo(channel2);

        Category category1 = createAndSaveCategory("java");
        Category category2 = createAndSaveCategory("spring");

        createAndSaveVideoCategory(video1, category1); // video1 은 java, spring 카테고리
        createAndSaveVideoCategory(video1, category2);

        createAndSaveVideoCategory(video2, category1); // video2 는 java 카테고리
        createAndSaveVideoCategory(video3, category2); // video3 는 spring 카테고리
        createAndSaveVideoCategory(video4, category1); // video4 는 java 카테고리
        createAndSaveVideoCategory(video5, category2); // video5 는 spring 카테고리
        createAndSaveVideoCategory(video6, category1); // video6 는 java 카테고리

        createAndSaveOrderWithPurchase(loginMember, List.of(video1, video5), 0); // otherMember 가 video1, video5 를 구매

        em.flush();
        em.clear();

        return List.of(
                dynamicTest("조건 없이 video 를 검색한다. 최신순으로 검색되며 5, 1 은 구매했다고 정보가 나오며 4, 3, 2, 1 채널은 구독했다고 나온다.", () -> {
                    //when
                    Page<VideoPageResponse> videos = videoService.getVideos(loginMember.getMemberId(), 0, 10, null, null, false);

                    //then
                    assertThat(videos.getContent()).hasSize(6);
                    assertThat(videos.getContent())
                            .isSortedAccordingTo(Comparator.comparing(VideoPageResponse::getCreatedDate).reversed());

                    //구매 여부
                    videos.getContent().forEach(
                            video -> {
                                if(List.of(video1.getVideoId(), video5.getVideoId()).contains(video.getVideoId())){
                                    assertThat(video.getIsPurchased()).isTrue();
                                }else{
                                    assertThat(video.getIsPurchased()).isFalse();
                                }
                            }
                    );

                    //비디오가 속한 채널의 구독 여부
                    videos.getContent().forEach(
                            video -> {
                                if(List.of(video1.getVideoId(), video2.getVideoId(), video3.getVideoId(), video4.getVideoId())
                                        .contains(video.getVideoId())){
                                    assertThat(video.getChannel().getIsSubscribed()).isTrue();
                                }else{
                                    assertThat(video.getChannel().getIsSubscribed()).isFalse();
                                }
                            }
                    );

                    //카테고리가 잘 있는지
                    videos.getContent().forEach(
                            video -> {
                                if(List.of(video2.getVideoId(), video4.getVideoId(), video6.getVideoId())
                                        .contains(video.getVideoId())){
                                    assertThat(video.getCategories().get(0).getCategoryName()).isEqualTo(category1.getCategoryName());
                                }else if(List.of(video3.getVideoId(), video5.getVideoId())
                                        .contains(video.getVideoId())){
                                    assertThat(video.getCategories().get(0).getCategoryName()).isEqualTo(category2.getCategoryName());
                                }else {
                                    assertThat(video.getCategories().get(0).getCategoryName()).isEqualTo(category1.getCategoryName());
                                    assertThat(video.getCategories().get(1).getCategoryName()).isEqualTo(category2.getCategoryName());
                                }
                            }
                    );
                })
        );
    }

    @TestFactory
    @DisplayName("videoId 로 비디오의 세부정보를 조회한다.")
    Collection<DynamicTest> getVideo() {
        //given
        Member owner = createAndSaveMember();
        Channel channel = createAndSaveChannel(owner);

        Member loginMember = createAndSaveMember(); // 로그인한 회원
        Long anonymousMemberId = -1L; // 비회원

        createAndSaveSubscribe(loginMember, channel); // loginMember 가 owner1 의 channel1 을 구독

        Video video = createAndSaveVideo(channel);

        Category category1 = createAndSaveCategory("java");
        Category category2 = createAndSaveCategory("spring");

        createAndSaveVideoCategory(video, category1); // video1 은 java, spring 카테고리
        createAndSaveVideoCategory(video, category2);

        em.flush();
        em.clear();

        return List.of(
                dynamicTest("비회원이 비디오 세부정보를 조회한다. 구독여부, 댓글여부, 구매여부는 모두 false 이다.", () -> {
                    //when
                    VideoDetailResponse response = videoService.getVideo(anonymousMemberId, video.getVideoId());

                    //then
                    //비디오 정보
                    assertThat(response.getVideoId()).isEqualTo(video.getVideoId());
                    assertThat(response.getVideoName()).isEqualTo(video.getVideoName());
                    assertThat(response.getDescription()).isEqualTo(video.getDescription());
                    assertThat(response.getVideoUrl()).isNotNull();
                    assertThat(response.getThumbnailUrl()).isNotNull();
                    assertThat(response.getViews()).isEqualTo(video.getView() + 1); // 조회수는 1 증가
                    assertThat(response.getStar()).isEqualTo(video.getStar());
                    assertThat(response.getPrice()).isEqualTo(video.getPrice());
                    assertThat(response.getReward()).isEqualTo(video.getPrice() / 100);
                    assertThat(response.getCreatedDate().toString().substring(0, 21)).isEqualTo(video.getCreatedDate().toString().substring(0, 21));

                    //카테고리 정보
                    assertThat(response.getCategories()).hasSize(2)
                            .extracting("categoryName")
                            .containsExactlyInAnyOrder(category1.getCategoryName(), category2.getCategoryName());

                    //채널 정보
                    assertThat(response.getChannel().getMemberId()).isEqualTo(owner.getMemberId());
                    assertThat(response.getChannel().getChannelName()).isEqualTo(channel.getChannelName());
                    assertThat(response.getChannel().getImageUrl()).isNotNull();
                    assertThat(response.getChannel().getSubscribes()).isEqualTo(channel.getSubscribers());

                    //Watch 정보 (익명이므로 watch 정보는 없어야 한다.)
                    assertThat(watchRepository.findAll()).hasSize(0);

                    //로그인한 회원에 따라 달라지는 정보
                    assertThat(response.getChannel().getIsSubscribed()).isFalse();
                    assertThat(response.getIsPurchased()).isFalse();
                    assertThat(response.getIsReplied()).isFalse();
                }),
                dynamicTest("구독한 회원이 로그인을 하고 처음으로 조회를 하면 Watch 테이블이 생긴다.", ()-> {
                    //when
                    VideoDetailResponse response = videoService.getVideo(loginMember.getMemberId(), video.getVideoId());

                    //then
                    assertThat(watchRepository.findAll().get(0).getMember().getMemberId()).isEqualTo(loginMember.getMemberId());
                }),
                dynamicTest("구독한 회원이 로그인을 하고 조회를 하면 구독 여부가 true 가 된다.", ()-> {
                    //when
                    VideoDetailResponse response = videoService.getVideo(loginMember.getMemberId(), video.getVideoId());

                    //then
                    assertThat(response.getChannel().getIsSubscribed()).isTrue();
                    assertThat(response.getIsPurchased()).isFalse();
                    assertThat(response.getIsReplied()).isFalse();

                    //watch table 은 새로 만들어지지 않지만(1개로 고정) 조회수는 1 증가한다.
                    assertThat(watchRepository.findAll()).hasSize(1);
                    assertThat(response.getViews()).isEqualTo(video.getView() + 3);
                }),
                dynamicTest("구독한 회원이 video 를 구매하면 구매여부가 true 가 된다.", ()-> {
                    //given
                    createAndSaveOrderWithPurchase(loginMember, List.of(video), 0);

                    em.flush();
                    em.clear();

                    //when
                    VideoDetailResponse response = videoService.getVideo(loginMember.getMemberId(), video.getVideoId());

                    //then
                    assertThat(response.getChannel().getIsSubscribed()).isTrue();
                    assertThat(response.getIsPurchased()).isTrue();
                    assertThat(response.getIsReplied()).isFalse();
                }),
                dynamicTest("구독한 회원이 video 에 댓글을 남기면 댓글 여부가 true 가 된다.", ()-> {
                    //given
                    createAndSaveReply(loginMember, video);

                    em.flush();
                    em.clear();

                    //when
                    VideoDetailResponse response = videoService.getVideo(loginMember.getMemberId(), video.getVideoId());

                    //then
                    assertThat(response.getChannel().getIsSubscribed()).isTrue();
                    assertThat(response.getIsPurchased()).isTrue();
                    assertThat(response.getIsReplied()).isTrue();
                }),
                dynamicTest("구독한 회원이 구독을 취소하면 구독 여부만 false 가 된다.", ()-> {
                    //given
                    subscribeRepository.deleteAll();

                    em.flush();
                    em.clear();

                    //when
                    VideoDetailResponse response = videoService.getVideo(loginMember.getMemberId(), video.getVideoId());

                    //then
                    assertThat(response.getChannel().getIsSubscribed()).isFalse();
                    assertThat(response.getIsPurchased()).isTrue();
                    assertThat(response.getIsReplied()).isTrue();
                })
        );
    }

    @TestFactory
    @DisplayName("비디오를 생성한다.")
    Collection<DynamicTest> createVideo() {
        //given
        Member owner = createAndSaveMember();

        Category category1 = createAndSaveCategory("category1");
        Category category2 = createAndSaveCategory("category2");

        String videoName = "test";

        given(redisService.getData(anyString())).willReturn(videoName);

        return List.of(
                dynamicTest("imageType, fileName 을 받아서 파일을 저장할 수 있는 url 을 반환한다.", ()-> {
                    //given
                    VideoCreateUrlServiceRequest request = VideoCreateUrlServiceRequest.builder()
                            .imageType(ImageType.PNG)
                            .fileName(videoName)
                            .build();

                    //when
                    VideoCreateUrlResponse videoCreateUrl = videoService.getVideoCreateUrl(owner.getMemberId(), request);

                    //then
                    assertThat(videoCreateUrl.getVideoUrl()).matches("^https?://.+");
                    assertThat(videoCreateUrl.getThumbnailUrl()).matches("^https?://.+");
                }),
                dynamicTest("해당 fileName 으로 비디오를 생성한다.", ()-> {
                    //given
                    VideoCreateServiceRequest request = VideoCreateServiceRequest.builder()
                            .videoName(videoName)
                            .price(1000)
                            .description("test")
                            .categories(List.of(category1.getCategoryName(), category2.getCategoryName()))
                            .build();

                    //when
                    Long video = videoService.createVideo(owner.getMemberId(), request);

                    //then
                    Video createdVideo = videoRepository.findById(video).orElseThrow();

                    assertThat(createdVideo.getVideoName()).isEqualTo(videoName);
                    assertThat(createdVideo.getPrice()).isEqualTo(1000);
                    assertThat(createdVideo.getDescription()).isEqualTo("test");

                    //카테고리 확인
                    assertThat(createdVideo.getVideoCategories()).hasSize(2);
                    assertThat(createdVideo.getVideoCategories().get(0).getCategory().getCategoryName()).isEqualTo(category1.getCategoryName());
                    assertThat(createdVideo.getVideoCategories().get(1).getCategory().getCategoryName()).isEqualTo(category2.getCategoryName());
                })
        );
    }

    @TestFactory
    @DisplayName("비디오를 생성 시 최초 요청한 videoName 으로 요청하지 않으면 예외가 발생한다.")
    Collection<DynamicTest> createVideoException() {
        //given
        Member owner = createAndSaveMember();

        Category category1 = createAndSaveCategory("category1");
        Category category2 = createAndSaveCategory("category2");

        String videoName = "test";

        return List.of(
                dynamicTest("redis 에 videoName 이 저장되어 있지 않으면 VideoUploadNotRequestException 이 발생한다.", ()-> {
                    //given
                    VideoCreateServiceRequest request = VideoCreateServiceRequest.builder()
                            .videoName(videoName)
                            .price(1000)
                            .description("test")
                            .categories(List.of(category1.getCategoryName(), category2.getCategoryName()))
                            .build();

                    //when & then
                    assertThatThrownBy(()-> {
                        videoService.createVideo(owner.getMemberId(), request);
                    }).isInstanceOf(VideoUploadNotRequestException.class);
                }),
                dynamicTest("imageType, fileName 을 받아서 파일을 저장할 수 있는 url 을 반환한다.", ()-> {
                    //given
                    VideoCreateUrlServiceRequest request = VideoCreateUrlServiceRequest.builder()
                            .imageType(ImageType.PNG)
                            .fileName(videoName)
                            .build();

                    //when
                    VideoCreateUrlResponse videoCreateUrl = videoService.getVideoCreateUrl(owner.getMemberId(), request);

                    //then
                    assertThat(videoCreateUrl.getVideoUrl()).matches("^https?://.+");
                    assertThat(videoCreateUrl.getThumbnailUrl()).matches("^https?://.+");
                }),
                dynamicTest("해당 fileName 이 아닌 다른 이름으로 비디오를 생성하려고 하면 VideoFileNameNotMatchException 이 발생한다.", ()-> {
                    //given
                    given(redisService.getData(anyString())).willReturn(videoName);

                    VideoCreateServiceRequest request = VideoCreateServiceRequest.builder()
                            .videoName(videoName + "1")
                            .price(1000)
                            .description("test")
                            .categories(List.of(category1.getCategoryName(), category2.getCategoryName()))
                            .build();

                    //when & then
                    assertThatThrownBy(
                            ()-> videoService.createVideo(owner.getMemberId(), request))
                            .isInstanceOf(VideoFileNameNotMatchException.class);
                })
        );
    }

    @Test
    @DisplayName("존재하지 않는 memberId 면 MemberNotFoundException 이 발생한다.")
    void getVideoCreateUrlMemberNotFoundException() {
        //given
        Member owner = createAndSaveMember();

        VideoCreateUrlServiceRequest request = VideoCreateUrlServiceRequest.builder()
                .imageType(ImageType.PNG)
                .fileName("test")
                .build();

        Long requestMemberId = owner.getMemberId() + 999L; // 존재하지 않는 memberId

        //when & then
        assertThatThrownBy(() -> videoService.getVideoCreateUrl(requestMemberId, request))
                .isInstanceOf(MemberNotFoundException.class);
    }
}