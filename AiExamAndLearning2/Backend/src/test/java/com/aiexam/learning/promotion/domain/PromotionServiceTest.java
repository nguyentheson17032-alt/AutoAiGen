package com.aiexam.learning.promotion.domain;

import com.aiexam.learning.attempt.domain.Attempt;
import com.aiexam.learning.attempt.domain.AttemptStatus;
import com.aiexam.learning.attempt.infrastructure.AttemptRepository;
import com.aiexam.learning.catalog.domain.Subject;
import com.aiexam.learning.catalog.infrastructure.SubjectRepository;
import com.aiexam.learning.paper.domain.Paper;
import com.aiexam.learning.paper.domain.PaperKind;
import com.aiexam.learning.paper.domain.PaperService;
import com.aiexam.learning.promotion.api.PromotionStatusResponse;
import com.aiexam.learning.question.domain.Difficulty;
import com.aiexam.learning.question.domain.Question;
import com.aiexam.learning.user.domain.RankCode;
import com.aiexam.learning.user.domain.User;
import com.aiexam.learning.user.domain.UserRole;
import com.aiexam.learning.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private PaperService paperService;

    @Mock
    private SubjectRepository subjectRepository;

    private PromotionService promotionService;

    @BeforeEach
    void setUp() {
        promotionService = new PromotionService(userRepository, attemptRepository, paperService, subjectRepository);
    }

    @Test
    void getStatus_bronzeUserWith1050Elo_isEligibleForSilverWith20EasyQuestions() {
        UUID userId = UUID.randomUUID();
        User user = User.register("bronze@example.com", "hash", "Bronze Guy", UserRole.STUDENT, 800);
        // User currently BRONZE, elo is capped at 1000
        user.applyElo(1050);
        assertThat(user.getEloRating()).isEqualTo(1000);
        assertThat(user.getRankCode()).isEqualTo(RankCode.BRONZE);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attemptRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        PromotionStatusResponse status = promotionService.getStatus(userId);

        assertThat(status.currentRank()).isEqualTo(RankCode.BRONZE);
        assertThat(status.targetRank()).isEqualTo(RankCode.SILVER);
        assertThat(status.eligible()).isTrue();
        assertThat(status.difficulty()).isEqualTo(Difficulty.BEGINNER);
        assertThat(status.questionCount()).isEqualTo(20);
        assertThat(status.durationMinutes()).isEqualTo(10);
        assertThat(status.minPassingRatio()).isEqualTo(0.80);
        assertThat(status.requiredWins()).isEqualTo(2);
        assertThat(status.currentWins()).isEqualTo(0);
        assertThat(status.mathPassed()).isFalse();
        assertThat(status.physicsPassed()).isFalse();
    }

    @Test
    void getStatus_silverUserWith1250Elo_requires20MediumQuestionsForGold() {
        UUID userId = UUID.randomUUID();
        User user = User.register("silver@example.com", "hash", "Silver Guy", UserRole.STUDENT, 1000);
        user.applyElo(1250);
        assertThat(user.getEloRating()).isEqualTo(1200);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attemptRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        PromotionStatusResponse status = promotionService.getStatus(userId);

        assertThat(status.currentRank()).isEqualTo(RankCode.SILVER);
        assertThat(status.targetRank()).isEqualTo(RankCode.GOLD);
        assertThat(status.eligible()).isTrue();
        assertThat(status.difficulty()).isEqualTo(Difficulty.INTERMEDIATE);
        assertThat(status.questionCount()).isEqualTo(20);
        assertThat(status.durationMinutes()).isEqualTo(10);
        assertThat(status.minPassingRatio()).isEqualTo(0.80);
        assertThat(status.requiredWins()).isEqualTo(2);
    }

    @Test
    void getStatus_goldUserWith1450Elo_requires15HardQuestionsForPlatinum() {
        UUID userId = UUID.randomUUID();
        User user = User.register("gold@example.com", "hash", "Gold Guy", UserRole.STUDENT, 1000);
        user.promoteTo(RankCode.GOLD);
        user.applyElo(1450);
        assertThat(user.getEloRating()).isEqualTo(1400);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attemptRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        PromotionStatusResponse status = promotionService.getStatus(userId);

        assertThat(status.currentRank()).isEqualTo(RankCode.GOLD);
        assertThat(status.targetRank()).isEqualTo(RankCode.PLATINUM);
        assertThat(status.eligible()).isTrue();
        assertThat(status.difficulty()).isEqualTo(Difficulty.ADVANCED);
        assertThat(status.questionCount()).isEqualTo(15);
        assertThat(status.durationMinutes()).isEqualTo(8);
    }

    @Test
    void getStatus_platinumUserWith1650Elo_requires20HardQuestionsForDiamond() {
        UUID userId = UUID.randomUUID();
        User user = User.register("plat@example.com", "hash", "Plat Guy", UserRole.STUDENT, 1000);
        user.promoteTo(RankCode.PLATINUM);
        user.applyElo(1650);
        assertThat(user.getEloRating()).isEqualTo(1600);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(attemptRepository.findByUserId(eq(userId), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        PromotionStatusResponse status = promotionService.getStatus(userId);

        assertThat(status.currentRank()).isEqualTo(RankCode.PLATINUM);
        assertThat(status.targetRank()).isEqualTo(RankCode.DIAMOND);
        assertThat(status.eligible()).isTrue();
        assertThat(status.difficulty()).isEqualTo(Difficulty.ADVANCED);
        assertThat(status.questionCount()).isEqualTo(20);
        assertThat(status.durationMinutes()).isEqualTo(10);
    }

    @Test
    void checkAndApplyPromotion_whenMathAndPhysicsPassed_promotesUser() {
        UUID userId = UUID.randomUUID();
        User user = User.register("silver@example.com", "hash", "Silver Guy", UserRole.STUDENT, 1000);
        user.applyElo(1200);

        Subject math = Subject.create("MATH", "Toán 10", "Môn Toán 10");
        Subject physics = Subject.create("PHYSICS", "Vật lý 10", "Môn Vật lý 10");

        Paper mathPaper = Paper.create(
                user,
                math,
                "Thử thách thăng hạng Vàng - Toán",
                "desc",
                PaperKind.PROMOTION,
                com.aiexam.learning.paper.domain.PaperSource.AI_GENERATED,
                10,
                1100,
                1300,
                com.aiexam.learning.question.domain.ContentStatus.PUBLISHED
        );
        Question q1 = Question.create(
                user, math, null, com.aiexam.learning.question.domain.QuestionType.MULTIPLE_CHOICE,
                "1+1=?", "2", "exp", Difficulty.INTERMEDIATE, 1100, null,
                com.aiexam.learning.question.domain.QuestionSource.MANUAL,
                com.aiexam.learning.question.domain.ContentStatus.PUBLISHED, null
        );
        mathPaper.addQuestion(q1, 1, BigDecimal.valueOf(20.0));

        Paper physicsPaper = Paper.create(
                user,
                physics,
                "Thử thách thăng hạng Vàng - Vật lý",
                "desc",
                PaperKind.PROMOTION,
                com.aiexam.learning.paper.domain.PaperSource.AI_GENERATED,
                10,
                1100,
                1300,
                com.aiexam.learning.question.domain.ContentStatus.PUBLISHED
        );
        Question q2 = Question.create(
                user, physics, null, com.aiexam.learning.question.domain.QuestionType.MULTIPLE_CHOICE,
                "v=s/t?", "v", "exp", Difficulty.INTERMEDIATE, 1100, null,
                com.aiexam.learning.question.domain.QuestionSource.MANUAL,
                com.aiexam.learning.question.domain.ContentStatus.PUBLISHED, null
        );
        physicsPaper.addQuestion(q2, 1, BigDecimal.valueOf(20.0));

        Attempt mathAttempt = Attempt.start(user, mathPaper);
        mathAttempt.markGraded(BigDecimal.valueOf(18), 1200, 1200); // 18/20 = 90% (>= 80%)

        Attempt physicsAttempt = Attempt.start(user, physicsPaper);
        physicsAttempt.markGraded(BigDecimal.valueOf(17), 1200, 1200); // 17/20 = 85% (>= 80%)


        when(attemptRepository.findByUserId(eq(user.getId()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(mathAttempt, physicsAttempt)));

        promotionService.checkAndApplyPromotion(user, physicsAttempt, 0.85);

        // User should be promoted to GOLD!
        assertThat(user.getRankCode()).isEqualTo(RankCode.GOLD);
    }
}
