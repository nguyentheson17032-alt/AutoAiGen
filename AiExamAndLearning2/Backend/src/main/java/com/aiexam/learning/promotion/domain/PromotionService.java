package com.aiexam.learning.promotion.domain;

import com.aiexam.learning.attempt.api.AttemptResponse;
import com.aiexam.learning.attempt.domain.Attempt;
import com.aiexam.learning.attempt.domain.AttemptStatus;
import com.aiexam.learning.attempt.infrastructure.AttemptRepository;
import com.aiexam.learning.catalog.domain.Subject;
import com.aiexam.learning.catalog.infrastructure.SubjectRepository;
import com.aiexam.learning.common.exception.BusinessRuleException;
import com.aiexam.learning.common.exception.ResourceNotFoundException;
import com.aiexam.learning.paper.domain.Paper;
import com.aiexam.learning.paper.domain.PaperKind;
import com.aiexam.learning.paper.domain.PaperService;
import com.aiexam.learning.promotion.api.PromotionStatusResponse;
import com.aiexam.learning.user.domain.RankCode;
import com.aiexam.learning.user.domain.User;
import com.aiexam.learning.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PromotionService {

    private final UserRepository userRepository;
    private final AttemptRepository attemptRepository;
    private final PaperService paperService;
    private final SubjectRepository subjectRepository;

    public record PromotionSubjectProgress(
            boolean mathPassed,
            boolean physicsPassed,
            int totalWins
    ) {}

    public PromotionStatusResponse getStatus(UUID userId) {
        User user = getUser(userId);
        RankCode currentRank = user.getRankCode();
        RankCode.PromotionRequirement req = currentRank.promotionRequirement();

        if (req == null) {
            return PromotionStatusResponse.maxRank(currentRank, user.getEloRating());
        }

        PromotionSubjectProgress progress = getPromotionSubjectProgress(userId, req.minEloThreshold());
        boolean eligible = user.getEloRating() >= req.minEloThreshold();
        String description = "Cần đỗ 2 bài thi (1 môn Toán, 1 môn Vật lý) đạt >= " + Math.round(req.minPassingRatio() * 100) +
                "% số câu đúng để thăng hạng " + req.targetRank();

        String nextSubjectName = !progress.mathPassed() ? "Toán" : (!progress.physicsPassed() ? "Vật lý" : "Đã hoàn thành");

        return new PromotionStatusResponse(
                currentRank,
                req.targetRank(),
                user.getEloRating(),
                req.minEloThreshold(),
                eligible,
                req.difficulty(),
                req.questionCount(),
                req.durationMinutes(),
                req.minPassingRatio(),
                req.requiredWins(),
                progress.totalWins(),
                progress.mathPassed(),
                progress.physicsPassed(),
                nextSubjectName,
                false,
                description
        );
    }

    @Transactional
    public AttemptResponse startPromotionExam(UUID userId, UUID subjectId) {
        User user = getUser(userId);
        RankCode currentRank = user.getRankCode();
        RankCode.PromotionRequirement req = currentRank.promotionRequirement();

        if (req == null) {
            throw new BusinessRuleException("MAX_RANK_REACHED", "Bạn đã đạt bậc xếp hạng cao nhất (DIAMOND)!");
        }

        if (user.getEloRating() < req.minEloThreshold()) {
            throw new BusinessRuleException(
                    "PROMOTION_NOT_ELIGIBLE",
                    "Bạn cần đạt tối thiểu " + req.minEloThreshold() + " Elo để tham gia bài thi thăng hạng " + req.targetRank()
            );
        }

        PromotionSubjectProgress progress = getPromotionSubjectProgress(userId, req.minEloThreshold());

        Subject subject;
        if (subjectId != null) {
            subject = subjectRepository.findById(subjectId)
                    .orElseThrow(() -> new ResourceNotFoundException("SUBJECT_NOT_FOUND", "Subject not found: " + subjectId));
        } else {
            if (!progress.mathPassed()) {
                subject = resolveMathSubject();
            } else if (!progress.physicsPassed()) {
                subject = resolvePhysicsSubject();
            } else {
                subject = resolveMathSubject();
            }
        }

        String subjectLabel = isPhysicsSubject(subject) ? "Vật lý" : "Toán";
        int examIndex = Math.min(req.requiredWins(), progress.totalWins() + 1);

        String title = "Thử thách thăng hạng " + req.targetRank() + " (Bài " + examIndex + "/2: Môn " + subjectLabel + ")";
        String desc = "Đề tổng hợp " + req.questionCount() + " câu độ khó " + req.difficulty() +
                " · Đạt >= " + Math.round(req.minPassingRatio() * 100) + "% số câu đúng để đỗ bài thi môn " + subjectLabel;

        int preferredMin = Math.max(100, req.minEloThreshold() - 150);
        int preferredMax = req.minEloThreshold() + 150;

        Paper paper = paperService.generatePromotionPaper(
                user,
                subject,
                title,
                desc,
                req.questionCount(),
                req.durationMinutes(),
                preferredMin,
                preferredMax
        );

        Attempt attempt = attemptRepository.save(Attempt.start(user, paper));
        return AttemptResponse.from(attempt);
    }

    @Transactional
    public void checkAndApplyPromotion(User user, Attempt attempt, double scoreRatio) {
        if (attempt.getPaper() == null || attempt.getPaper().getKind() != PaperKind.PROMOTION) {
            return;
        }

        RankCode currentRank = user.getRankCode();
        RankCode.PromotionRequirement req = currentRank.promotionRequirement();
        if (req == null) {
            return;
        }

        if (scoreRatio >= req.minPassingRatio()) {
            PromotionSubjectProgress progress = getPromotionSubjectProgress(user.getId(), req.minEloThreshold());
            if (progress.mathPassed() && progress.physicsPassed()) {
                user.promoteTo(req.targetRank());
            }
        }
    }

    public PromotionSubjectProgress getPromotionSubjectProgress(UUID userId, int minEloThreshold) {
        List<Attempt> gradedAttempts = attemptRepository.findByUserId(userId, org.springframework.data.domain.Pageable.unpaged()).getContent();
        boolean mathPassed = false;
        boolean physicsPassed = false;
        for (Attempt a : gradedAttempts) {
            if (a.getStatus() == AttemptStatus.GRADED
                    && a.getPaper() != null
                    && a.getPaper().getKind() == PaperKind.PROMOTION
                    && a.getPaper().getTargetEloMin() >= minEloThreshold - 300
                    && a.getScore() != null
                    && a.getMaxScore() != null
                    && a.getMaxScore().signum() > 0) {
                double ratio = a.getScore().doubleValue() / a.getMaxScore().doubleValue();
                if (ratio >= 0.80) {
                    Subject subject = a.getPaper().getSubject();
                    if (isPhysicsSubject(subject)) {
                        physicsPassed = true;
                    } else {
                        // Default / Math
                        mathPassed = true;
                    }
                }
            }
        }
        int totalWins = (mathPassed ? 1 : 0) + (physicsPassed ? 1 : 0);
        return new PromotionSubjectProgress(mathPassed, physicsPassed, totalWins);
    }

    public int countRecentPassedPromotionAttempts(UUID userId, RankCode targetRank, int minEloThreshold) {
        return getPromotionSubjectProgress(userId, minEloThreshold).totalWins();
    }

    public boolean isMathSubject(Subject s) {
        if (s == null) return false;
        String code = s.getCode() != null ? s.getCode().toUpperCase() : "";
        String name = s.getName() != null ? s.getName().toLowerCase() : "";
        return code.contains("MATH") || name.contains("toán");
    }

    public boolean isPhysicsSubject(Subject s) {
        if (s == null) return false;
        String code = s.getCode() != null ? s.getCode().toUpperCase() : "";
        String name = s.getName() != null ? s.getName().toLowerCase() : "";
        return code.contains("PHYSIC") || name.contains("lý") || name.contains("vật");
    }

    private Subject resolveMathSubject() {
        return subjectRepository.findByCode("MATH")
                .or(() -> subjectRepository.findByNameIgnoreCase("Toán"))
                .orElseGet(() -> subjectRepository.save(Subject.create("MATH", "Toán", "Môn Toán học")));
    }

    private Subject resolvePhysicsSubject() {
        return subjectRepository.findByCode("PHYSICS")
                .or(() -> subjectRepository.findByNameIgnoreCase("Vật lý"))
                .or(() -> subjectRepository.findByNameIgnoreCase("Vật Lí"))
                .orElseGet(() -> subjectRepository.save(Subject.create("PHYSICS", "Vật lý", "Môn Vật lý")));
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userId));
    }
}

