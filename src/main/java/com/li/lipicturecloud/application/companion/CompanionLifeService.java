package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.CompanionHomeView;
import com.li.lipicturecloud.application.companion.view.CompanionView;
import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.application.companion.view.GrowthRecordView;
import com.li.lipicturecloud.config.CompanionFeatureProperties;
import com.li.lipicturecloud.domain.companion.Companion;
import com.li.lipicturecloud.domain.companion.CompanionBalance;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import com.li.lipicturecloud.domain.companion.GrowthRecordRepository;
import com.li.lipicturecloud.domain.companion.PictureNutrition;
import com.li.lipicturecloud.exception.BusinessException;
import com.li.lipicturecloud.exception.ErrorCode;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import static com.li.lipicturecloud.manager.auth.model.SpaceUserPermissionConstant.PICTURE_VIEW;

@Service
@ConditionalOnProperty(prefix = "app.companion", name = "enabled",
        havingValue = "true", matchIfMissing = true)
/**
 * 伙伴用例入口：负责把权限、幂等、图片分析和领域成长串成一条安全流程。
 *
 * <p>领域对象不直接查询图片库；这里始终先保留/读取幂等 run，再在每次请求（包括回放）前
 * 重新校验图片查看权限。因此撤销共享后，旧幂等键也不能继续读取历史喂养结果。</p>
 */
public class CompanionLifeService implements CompanionLife {

    private static final Logger log = LoggerFactory.getLogger(CompanionLifeService.class);
    private static final Pattern FEED_KEY = Pattern.compile("^[a-z0-9_-]{16,64}$");

    private final CompanionRepository companionRepository;
    private final GrowthRecordRepository growthRepository;
    private final CompanionFeedingCoordinator coordinator;
    private final SpaceAuthorizationAccessService authorization;
    private final PictureNutritionAnalyzer analyzer;
    private final CompanionViewAssembler assembler;
    private final CompanionFeatureProperties properties;
    private final CompanionBalance balance;
    private final TransactionTemplate homeReadTransaction;

    public CompanionLifeService(CompanionRepository companionRepository,
                                GrowthRecordRepository growthRepository,
                                CompanionFeedingCoordinator coordinator,
                                SpaceAuthorizationAccessService authorization,
                                PictureNutritionAnalyzer analyzer,
                                CompanionViewAssembler assembler,
                                CompanionFeatureProperties properties,
                                CompanionBalance balance,
                                PlatformTransactionManager transactionManager) {
        this.companionRepository = companionRepository;
        this.growthRepository = growthRepository;
        this.coordinator = coordinator;
        this.authorization = authorization;
        this.analyzer = analyzer;
        this.assembler = assembler;
        this.properties = properties;
        this.balance = balance;
        this.homeReadTransaction = new TransactionTemplate(transactionManager);
        this.homeReadTransaction.setReadOnly(true);
        this.homeReadTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    }

    @Override
    public CompanionHomeView home(AuthorizationSubject subject) {
        Objects.requireNonNull(subject, "subject");
        // 主页由伙伴、技能和成长记录组成，使用可重复读快照避免并发喂养时看到“半新半旧”的页面。
        return homeReadTransaction.execute(status -> readHome(subject));
    }

    @Override
    public CompanionHomeView awaken(AuthorizationSubject subject) {
        Objects.requireNonNull(subject, "subject");
        companionRepository.createIfAbsent(subject.userId(), balance);
        return home(subject);
    }

    @Override
    public FeedPictureResult feed(FeedPictureCommand command) {
        Objects.requireNonNull(command, "command");
        if (!properties.isFeedingEnabled()) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "伙伴喂养已暂停");
        }
        if (command.pictureId() <= 0 || command.idempotencyKey() == null
                || !FEED_KEY.matcher(command.idempotencyKey()).matches()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "喂养请求标识不合法");
        }
        Companion companion = companionRepository.findByOwnerId(command.subject().userId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND_ERROR, "请先唤醒伙伴"));
        // reserve 在分析前落库；浏览器或网关超时后可带着同一 key 重放，而不是重复投喂。
        FeedReservation reservation = coordinator.reserve(companion, command.subject(), command.pictureId(),
                command.idempotencyKey(), fingerprint(command.pictureId()), UUID.randomUUID().toString(),
                analyzer.mode(), analyzer.contentUnderstood());

        // 授权放在回放判断之前：历史结果不是绕过空间权限的旁路。
        checkAuthorization(command, reservation);
        if (reservation.kind() == FeedReservation.Kind.REPLAY) {
            return reservation.replay();
        }
        if (reservation.kind() == FeedReservation.Kind.REJECTED) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, reservation.run().safeErrorMessage());
        }
        if (reservation.kind() == FeedReservation.Kind.IN_PROGRESS) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "这次喂养还在消化中，请稍后重试");
        }

        PictureNutrition nutrition;
        try {
            nutrition = analyzer.analyze(new AuthorizedPictureRef(command.subject(), command.pictureId()));
        } catch (RuntimeException error) {
            // 对外不给出底层异常；run 中只保存可安全展示的失败文案，源图片从不被修改或删除。
            coordinator.fail(reservation.run(), "NUTRITION_FAILED", "本次没有消化成功，图片未被消耗");
            log.warn("companion_feed_nutrition_failed correlationId={} subjectId={} pictureId={} exceptionType={}",
                    reservation.run().correlationId(), command.subject().userId(), command.pictureId(),
                    error.getClass().getName());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "本次没有消化成功，图片未被消耗");
        }
        try {
            return coordinator.complete(reservation.run(), nutrition);
        } catch (RuntimeException error) {
            coordinator.fail(reservation.run(), "FEED_COMMIT_FAILED", "本次没有消化成功，图片未被消耗");
            if (error instanceof BusinessException businessException) {
                throw businessException;
            }
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "本次没有消化成功，图片未被消耗");
        }
    }

    private CompanionHomeView readHome(AuthorizationSubject subject) {
        Companion companion = companionRepository.findByOwnerId(subject.userId()).orElse(null);
        if (companion == null) {
            return new CompanionHomeView(null, assembler.nutritionStatus(), List.of());
        }
        CompanionView companionView = assembler.companion(companion);
        List<GrowthRecordView> growth = growthRepository.findRecent(companion.id(), 20)
                .stream().map(assembler::growth).toList();
        return new CompanionHomeView(companionView, assembler.nutritionStatus(), growth);
    }

    private void checkAuthorization(FeedPictureCommand command, FeedReservation reservation) {
        try {
            authorization.checkForUser(PICTURE_VIEW, command.pictureId(), command.subject().userId());
        } catch (BusinessException error) {
            if (error.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()
                    || error.getCode() == ErrorCode.NO_AUTH_ERROR.getCode()) {
                if (reservation.kind() == FeedReservation.Kind.STARTED) {
                    coordinator.reject(reservation.run(), "PICTURE_UNAVAILABLE", "图片不可用或无权访问");
                }
                log.warn("companion_feed_denied correlationId={} subjectId={} pictureId={} reason=PICTURE_UNAVAILABLE",
                        reservation.run().correlationId(), command.subject().userId(), command.pictureId());
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "图片不可用或无权访问");
            }
            if (error.getCode() == ErrorCode.NOT_LOGIN_ERROR.getCode()) {
                failAuthorizationCheckIfStarted(reservation, command, error.getClass());
                throw error;
            }
            failAuthorizationCheckIfStarted(reservation, command, error.getClass());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "暂时无法校验图片访问权限，请重试");
        } catch (RuntimeException error) {
            failAuthorizationCheckIfStarted(reservation, command, error.getClass());
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "暂时无法校验图片访问权限，请重试");
        }
    }

    private void failAuthorizationCheckIfStarted(FeedReservation reservation, FeedPictureCommand command,
                                                   Class<?> exceptionType) {
        if (reservation.kind() != FeedReservation.Kind.STARTED) {
            return;
        }
        coordinator.fail(reservation.run(), "AUTHORIZATION_CHECK_FAILED", "暂时无法校验图片访问权限，请重试");
        log.warn("companion_feed_authorization_failed correlationId={} subjectId={} pictureId={} exceptionType={}",
                reservation.run().correlationId(), command.subject().userId(), command.pictureId(),
                exceptionType.getName());
    }

    private static String fingerprint(long pictureId) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(("pictureId=" + pictureId).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 unavailable", error);
        }
    }
}
