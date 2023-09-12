package com.server.domain.reply.service;

import com.server.domain.channel.entity.Channel;
import com.server.domain.member.entity.Member;
import com.server.domain.member.repository.MemberRepository;
import com.server.domain.order.entity.Order;
import com.server.domain.reply.controller.convert.ReplySort;
import com.server.domain.reply.dto.CreateReply;
import com.server.domain.reply.dto.ReplyInfo;
import com.server.domain.reply.dto.ReplyUpdateServiceApi;
import com.server.domain.reply.entity.Reply;
import com.server.domain.reply.repository.ReplyRepository;
import com.server.domain.video.entity.Video;
import com.server.domain.video.entity.VideoStatus;
import com.server.global.exception.businessexception.memberexception.MemberAccessDeniedException;
import com.server.global.exception.businessexception.replyException.ReplyDuplicateException;
import com.server.global.exception.businessexception.replyException.ReplyNotValidException;
import com.server.global.exception.businessexception.videoexception.VideoNotFoundException;
import com.server.global.exception.businessexception.videoexception.VideoNotPurchasedException;
import com.server.global.reponse.PageInfo;
import com.server.global.testhelper.ServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import org.junit.jupiter.api.Test;

class ReplyServiceTest extends ServiceTest {

    @Autowired
    private ReplyService replyService;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private ReplyRepository replyRepository;

    @Test
    @DisplayName("댓글과 별점을 수정한다.")
    void updateReply() {
        //given
        Member loginMember = createAndSaveMember();
        Channel channel = createAndSaveChannel(loginMember);
        Video video = createAndSaveVideo(channel);

        Long loginMemberId = loginMember.getMemberId();
        Long replyId = createAndSaveReply(loginMember, video).getReplyId();

        ReplyUpdateServiceApi updateInfo = ReplyUpdateServiceApi.builder()
                .content("updateContent")
                .star(5)
                .build();

        em.flush();
        em.clear();

        //when
        replyService.updateReply(loginMemberId, replyId, updateInfo);

        //then
        Reply reply = replyRepository.findById(replyId).orElse(null);

        em.flush();
        em.clear();

        assertThat(reply).isNotNull();
        assertThat(reply.getContent()).isEqualTo("updateContent");
        assertThat(reply.getStar()).isEqualTo(5);
    }

    @Test
    @DisplayName("댓글 한 건을 조회한다.")
    void getReply() {
        //given
        Member loginMember = createAndSaveMember();

        Channel channel = createAndSaveChannel(loginMember);
        Video video = createAndSaveVideo(channel);

        Long loginMemberId = loginMember.getMemberId();
        Long replyId = createAndSaveReply(loginMember, video).getReplyId();

        //when
        ReplyInfo reply = replyService.getReply(replyId);

        assertThat(reply.getContent()).isEqualTo("content");
        assertThat(reply.getStar()).isEqualTo(0);

        //then
        Reply reply2 = replyRepository.findById(replyId).orElse(null);
        assertThat(reply2).isNotNull();
        assertThat(reply2.getContent()).isEqualTo("content");
        assertThat(reply2.getStar()).isEqualTo(0);
    }

    @Test
    @DisplayName("댓글을 삭제한다.")
    public void testDeleteReply() {
        //given
        Member member = createAndSaveMember();
        Channel channel = createAndSaveChannel(member);

        Video video = Video.builder()
                .videoName("title")
                .description("description")
                .thumbnailFile("thumbnailFile")
                .videoFile("videoFile")
                .view(0)
                .star(7.0F)
                .price(1000)
                .videoStatus(VideoStatus.CREATED)
                .channel(member.getChannel())
                .build();

        Order order = Order.createOrder(member, List.of(video), 0);
        order.completeOrder(LocalDateTime.now(), "paymentKey");

        videoRepository.save(video);
        orderRepository.save(order);

        Reply reply = createAndSaveReply(member, video);

        em.flush();
        em.clear();

        //when
        replyService.deleteReply(reply.getReplyId(), member.getMemberId());

        //then
        Reply deletedReply = replyRepository.findById(reply.getReplyId()).orElse(null);

        assertThat(deletedReply).isNull();
    }


    @Test
    @DisplayName("댓글 목록을 페이징으로 찾아서 조회한다.")
    void getReplies() {
        //given
        Member member = createAndSaveMember();

        Channel channel = createAndSaveChannel(member);
        Video video = createAndSaveVideo(channel);

        List<Reply> replies = new ArrayList<>();

        //when
        for (int i = 1; i <= 100; i++) {
            Reply reply = Reply.builder()
                    .content("content" + i)
                    .star(i)
                    .member(member)
                    .video(video)
                    .build();
            replies.add(replyRepository.save(reply));
        }

        Page<ReplyInfo> repliesPage = replyService.getReplies(video.getVideoId(), 1, 100, ReplySort.STAR, null);

        //then
        assertThat(repliesPage.getTotalElements()).isEqualTo(100);
        assertThat(repliesPage.getNumber()).isEqualTo(1);
        assertThat(repliesPage.getSize()).isEqualTo(100);

        List<ReplyInfo> replyInfoList = repliesPage.getContent();
        for (int i = 0; i < replyInfoList.size() - 1; i++) {
            assertThat(replyInfoList.get(i).getStar()).isGreaterThanOrEqualTo(replyInfoList.get(i + 1).getStar());
        }
    }

    @Test
    @DisplayName("댓글을 작성한다.")
    void createReply() {
        //given
        Member member = createAndSaveMember();

        Channel channel = createAndSaveChannel(member);
        Video video = createAndSavePurchasedVideo(member);

        em.flush();
        em.clear();

        //when
        Long replyId = replyService.createReply(member.getMemberId(), video.getVideoId(), new CreateReply("description", 3));

        //then
        assertThat(replyId)
                .isNotNull();

        assertThat(replyRepository.findById(replyId))
                .isPresent()
                .get()
                .satisfies(createdReply -> {
                    assertThat(createdReply.getStar()).isEqualTo(3);
                });
    }

    @Test
    @DisplayName("별점이 8인 댓글을 조회한다")
    void getRepliesWithHighStarFilter() {
        //given
        Member member = createAndSaveMember();
        memberRepository.save(member);

        Channel channel = createAndSaveChannel(member);
        Video video = createAndSaveVideo(channel);

        List<Reply> replies = new ArrayList<>();

        //when
        for (int i = 8; i <= 10; i++) {
            Reply reply = Reply.builder()
                    .content("content" + i)
                    .star(i)
                    .member(member)
                    .video(video)
                    .build();
            replies.add(replyRepository.save(reply));
        }

        for (int i = 1; i <= 7; i++) {
            Reply reply = Reply.builder()
                    .content("content" + i)
                    .star(i)
                    .member(member)
                    .video(video)
                    .build();
            replies.add(replyRepository.save(reply));
        }

        Page<ReplyInfo> repliesPage = replyService.getReplies(video.getVideoId(), 1, 10, ReplySort.STAR, 8);

        //then
        // 별점이 8인 댓글만 조회되었는지 확인하는 코드
        List<ReplyInfo> replyInfoList = repliesPage.getContent();
        for (ReplyInfo replyInfo : replyInfoList) {
            assertTrue(replyInfo.getStar() == 8);
        }


        assertThat(repliesPage.getTotalElements()).isEqualTo(3);
        assertThat(repliesPage.getNumber()).isEqualTo(1);
        assertThat(repliesPage.getSize()).isEqualTo(10);

    }


    @Test
    @DisplayName("댓글을 최신순으로 조회한다")
    void getRepliesByCreatedDate() {
        //given
        Member member = createAndSaveMember();
        memberRepository.save(member);

        Channel channel = createAndSaveChannel(member);
        Video video = createAndSavePurchasedVideo(member);

        List<Reply> replies = new ArrayList<>();

        //when
        for (int i = 1; i <= 10; i++) {
            Reply reply = Reply.builder()
                    .content("content" + i)
                    .star(i)
                    .member(member)
                    .video(video)
                    .build();
            replies.add(replyRepository.save(reply));
        }

        Page<ReplyInfo> repliesPage = replyService.getReplies(video.getVideoId(), 1, 10, ReplySort.CREATED_DATE, null);

        //then
        assertThat(repliesPage.getTotalElements()).isEqualTo(10);
        assertThat(repliesPage.getNumber()).isEqualTo(1);
        assertThat(repliesPage.getSize()).isEqualTo(10);

        List<ReplyInfo> replyInfoList = repliesPage.getContent();
        for (int i = 0; i < replyInfoList.size() - 1; i++) {
            assertThat(replyInfoList.get(i).getCreatedDate()).isAfter(replyInfoList.get(i + 1).getCreatedDate());
        }
    }

    @Test
    @DisplayName("로그인한 회원만 댓글을 작성할 수 있다.")
    void createRepliesLoginUser() {
        //given
        Member member = createAndSaveMember();
        Channel channel = createAndSaveChannel(member);
        Video video = createAndSavePurchasedVideo(member);

        em.flush();
        em.clear();

        //when
        Long reply = replyService.createReply(member.getMemberId(),video.getVideoId(), new CreateReply("content", 9));

        //then
        assertThat(reply).isNotNull();

    }

    @Test
    @DisplayName("로그인한 회원이 아니면 자신의 댓글을 삭제할 수 없다.")
    void deleteRepliesOnlyLoginUser() {
        // given
        Member member = createAndSaveMember();
        memberRepository.save(member);

        Channel channel = createAndSaveChannel(member);
        Video video = createAndSavePurchasedVideo(member);
        Reply reply = createAndSaveReply(member, video);

        Long replyId = reply.getReplyId();
        Long loginMemberId = member.getMemberId();

        em.flush();
        em.clear();

        // when
        Member otherMember = createAndSaveMember();
        Long otherMemberId = otherMember.getMemberId();

        // then
        assertThrows(MemberAccessDeniedException.class, () -> {
            replyService.deleteReply(replyId, otherMemberId);
        });
    }

    @Test
    @DisplayName("별점을 초과해서 부여할 수 없다")
    void notExceedStar() {
        //given
        Member member = createAndSaveMember();
        Long loginMemberId = member.getMemberId();

        Channel channel = createAndSaveChannel(member);
        Video video = createAndSavePurchasedVideo(member);
        Long videoId = video.getVideoId();

        //when
        Reply reply = Reply.builder()
                .content("content")
                .star(11)
                .build();

        //then
        ReplyNotValidException exception = assertThrows(ReplyNotValidException.class, () -> {
            replyService.createReply(loginMemberId, videoId, new CreateReply("content", 11));
        });

        assertThat(exception).isInstanceOf(ReplyNotValidException.class);
    }

    @Test
    @DisplayName("댓글을 중복하여 작성할 수 없다")
    void cannotDuplicateReplies() {
        //given
        Member member = createAndSaveMember();
        Channel channel = createAndSaveChannel(member);
        Video video = createAndSaveVideo(channel);
        createAndSaveOrderWithPurchaseComplete(member, List.of(video), 0);
        Reply reply1 = createAndSaveReply(member, video);
        Reply reply = createAndSaveReply(member, video);

        //when & then
        if (reply.getMember().getMemberId() == reply1.getMember().getMemberId()) {
            assertThrows(ReplyDuplicateException.class, () -> {
                replyService.createReply(member.getMemberId(), video.getVideoId(), new CreateReply("content", 3));
            });
        }
    }


    @Test
    @DisplayName("로그인한 사용자만 댓글을 수정할 수 있다.")
    public void onlyLoginUserModifyReplies() {
        //given
        Member member = createAndSaveMember();
        Long loginMemberId = member.getMemberId();
        Channel channel = createAndSaveChannel(member);
        Video video = createAndSaveVideo(channel);

        Reply reply = Reply.builder()
                .content("content")
                .star(0)
                .member(member)
                .video(video)
                .build();
        replyRepository.save(reply);

        em.flush();
        em.clear();

        //when & then
        if (reply.getMember().getMemberId() == loginMemberId) {
            assertDoesNotThrow(() -> replyService.updateReply(loginMemberId, reply.getReplyId(), new ReplyUpdateServiceApi("modifyContent", 3)));
        } else {
            assertThrows(MemberAccessDeniedException.class, () ->
                    replyService.updateReply(loginMemberId, reply.getReplyId(), new ReplyUpdateServiceApi("modifyContent", 3)));
        }
    }

    @Test
    @DisplayName("별점이 같으면 최신순으로 정렬한다")
    void getRepliesByStarAndCreatedDate() {
        //given
        Member member = createAndSaveMember();

        Channel channel = createAndSaveChannel(member);
        Video video = createAndSaveVideo(channel);

        Reply reply = createAndSaveReply(member, video);
        Reply reply1 = createAndSaveReply(member, video);
        Reply reply2 = createAndSaveReply(member, video);
        Reply reply3 = createAndSaveReply(member, video);

        //when
        Page<ReplyInfo> replies = replyService.getReplies(video.getVideoId(), 1, 10, ReplySort.STAR, null);

        //then
        assertThat(replies.getTotalElements()).isEqualTo(4);
        assertThat(replies.getNumber()).isEqualTo(1);
        assertThat(replies.getSize()).isEqualTo(10);

        List<ReplyInfo> replyInfoList = replies.getContent();
        for (int i = 0; i < replyInfoList.size() - 1; i++) {
            if (replyInfoList.get(i).getStar().equals(replyInfoList.get(i + 1).getStar())) {

                assertThat(Sort.by(Sort.Order.desc("createdDate")));
            }
        }
    }

    @Test
    @DisplayName("비디오를 구매한 사람만 댓글을 남길 수 있다")
    void purchaseVideoOnly() {
        //given
        Member member = createAndSaveMember();
        Channel channel = createAndSaveChannel(member);
        Video video = createAndSaveVideo(channel);
        createAndSaveOrderWithPurchaseComplete(member, List.of(video), 0);

        em.flush();
        em.clear();

        //when
        replyService.createReply(member.getMemberId(), video.getVideoId(), new CreateReply("content", 9));

        //then
        assertThat(replyRepository.findAll().size()).isEqualTo(1);
    }

    @Test
    @DisplayName("비디오를 구매하지않은 사용자는 댓글을 남길 수 없다")
    void notPurchaseVideo() {
        //given
        Member member = createAndSaveMember();
        Channel channel = createAndSaveChannel(member);
        Video video = createAndSaveVideo(channel);

        Long loginMemberId = member.getMemberId();
        Long videoId = video.getVideoId();

        //when
        assertThrows(VideoNotPurchasedException.class, () -> {
            replyService.createReply(member.getMemberId(), video.getVideoId(), new CreateReply("content", 9));
            replyService.createReply(loginMemberId, videoId, new CreateReply("content", 5));
        });
    }

    @Test
    @DisplayName("비디오를 찾지 못하면 VideoNotFoundException이 발생한다")
    void getRepliesWithVideoNotFound() {
        //given
        Member member = createAndSaveMember();
        Channel channel = createAndSaveChannel(member);
        Video video = createAndSaveVideo(channel);
        createAndSaveOrderWithPurchaseComplete(member, List.of(video), 0);

        // When, Then
        assertThrows(VideoNotFoundException.class, () -> {
            replyService.createReply(member.getMemberId(), 999999999999L, new CreateReply("content", 1));
        });

        List<Reply> replies = replyRepository.findAllByMemberIdAndVideoId(member.getMemberId(), video.getVideoId());
        assertThat(replies, empty());
    }

    @Test
    @DisplayName("강의의 별점이 변경되었는지 확인한다")
    void calculateStar() {
        //given
        Member member = createAndSaveMember();

        Channel channel = createAndSaveChannel(member);

        Video video = Video.builder()
                .videoName("title")
                .description("description")
                .thumbnailFile("thumbnailFile")
                .videoFile("videoFile")
                .view(0)
                .star(7.0F)
                .price(1000)
                .videoStatus(VideoStatus.CREATED)
                .channel(member.getChannel())
                .build();

        Order order = Order.createOrder(member, List.of(video), 0);
        order.completeOrder(LocalDateTime.now(), "paymentKey");

        videoRepository.save(video);
        orderRepository.save(order);

        em.flush();
        em.clear();

        //when
        Long replyId = replyService.createReply(member.getMemberId(), video.getVideoId(), new CreateReply("content", 7));
        replyService.updateReply(member.getMemberId(), replyId, new ReplyUpdateServiceApi("content", 9));

        Video updatedVideo = videoRepository.findById(video.getVideoId()).orElseThrow(VideoNotFoundException::new);

        //then
        assertThat(updatedVideo.getStar()).isEqualTo(9.0F);
    }
}
