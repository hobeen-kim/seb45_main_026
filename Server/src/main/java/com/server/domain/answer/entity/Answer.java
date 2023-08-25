package com.server.domain.answer.entity;

import com.server.domain.entity.BaseEntity;
import com.server.domain.member.entity.Member;
import com.server.domain.question.entity.Question;
import lombok.Getter;

import javax.persistence.*;

import static javax.persistence.FetchType.LAZY;

@Getter
@Entity
public class Answer extends BaseEntity {
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    private Long answerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AnswerStatus answerStatus;

    private String myAnswer;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "member_Id")
    private Member member;

    @ManyToOne(fetch = LAZY)
    @JoinColumn(name = "question_id")
    private Question question;



    public enum AnswerStatus{

    }














}