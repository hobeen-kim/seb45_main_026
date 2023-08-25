package com.server.domain.Order.entity;

import com.server.domain.entity.BaseEntity;
import com.server.domain.member.entity.Member;
import com.server.domain.video.entity.Video;
import lombok.Getter;

import javax.persistence.*;
@Getter
@Entity
public class Order extends BaseEntity {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long orderId;

    @Column(nullable = false)
    private String price;

    @Enumerated
    @Column(nullable = false)
    private String status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_Id")
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "video_Id")
    private Video video;


}
