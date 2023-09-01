package com.server.domain.reply.dto;

import com.server.domain.member.entity.Member;
import com.server.domain.reply.entity.Reply;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Builder
@Getter
@AllArgsConstructor
public class ReplyResponse {
    private Long replyId;
    private Long memberId;
    private Long videoId;
    private String content;
    private int star;
    private Member member;
    private LocalDateTime createdAt;

    public static ReplyResponse of(Reply reply) {
        return ReplyResponse.builder()
                .content(reply.getContent())
                .star(reply.getStar())
                .build();
    }
}