package com.example.instagramclone.domain.notification.application;

import com.example.instagramclone.domain.member.domain.Member;
import com.example.instagramclone.domain.member.domain.MemberRepository;
import com.example.instagramclone.domain.notification.domain.Notification;
import com.example.instagramclone.domain.notification.domain.NotificationRepository;
import com.example.instagramclone.domain.notification.domain.NotificationType;
import com.example.instagramclone.domain.notification.dto.NotificationResponse;
import com.example.instagramclone.domain.notification.event.NotificationEvent;
import com.example.instagramclone.infrastructure.messaging.NotificationMessagePublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 알림 이벤트 핸들러.
 *
 * <p>{@code @TransactionalEventListener}: 트랜잭션 커밋 후에만 실행
 * <p>{@code @Async("notificationExecutor")}: Step 2에서 만든 전용 스레드 풀에서 실행
 *
 * <p>Day 23: DB 저장 성공 후 WebSocket 으로 실시간 Push.
 * <p>Day 24: 멀티 서버 환경 대응 — Push 는 {@link NotificationMessagePublisher} 를 통해
 * Redis Pub/Sub 으로 발행되고, 각 서버의 {@code NotificationMessageListener} 가 자기
 * 메모리의 세션에만 전달한다. Push 실패는 DB 저장에 영향을 주지 않는다 — Source of Truth 는 DB.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventHandler {

    private final NotificationRepository notificationRepository;
    private final MemberRepository memberRepository;
    private final NotificationMessagePublisher notificationMessagePublisher;

    @Async("notificationExecutor")
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotification(NotificationEvent event) {
        if (event.isSelfNotification()) {
            log.debug("[알림 무시] 자기 자신에 대한 {} 알림 (senderId={})",
                    event.type(), event.senderId());
            return;
        }

        try {
            boolean alreadyExists = notificationRepository
                    .existsByTypeAndReceiverIdAndSenderIdAndTargetIdAndIsReadFalse(
                            event.type(), event.receiverId(), event.senderId(), event.targetId());
            if (alreadyExists) {
                log.debug("[알림 중복] 이미 동일한 읽지 않은 알림 존재 (type={}, sender={}, target={})",
                        event.type(), event.senderId(), event.targetId());
                return;
            }

            Member receiver = memberRepository.getReferenceById(event.receiverId());
            Member sender = memberRepository.getReferenceById(event.senderId());

            String message = buildMessage(event.type(), sender);

            Notification notification = Notification.create(
                    event.type(),
                    receiver,
                    sender,
                    event.targetId(),
                    message
            );

            notificationRepository.save(notification);

            // DB 저장에 성공한 경우에만 Push — 저장 실패 시 Push 하지 않는다
            pushNotification(event.receiverId(), notification);

            log.info("[알림 저장+Push] {} → {} (type={}, targetId={})",
                    sender.getUsername(), event.receiverId(),
                    event.type(), event.targetId());

        } catch (Exception e) {
            log.error("[알림 저장 실패] type={}, receiver={}, sender={}: {}",
                    event.type(), event.receiverId(), event.senderId(), e.getMessage());
        }
    }

    /**
     * WebSocket 으로 알림 Push (best-effort).
     * 연결되어 있지 않은 유저의 Push 는 유실되지만, DB 에 이미 저장되어 있으므로
     * 다음에 REST 로 조회하면 놓친 알림을 복구할 수 있다.
     */
    private void pushNotification(Long receiverId, Notification notification) {
        try {
            NotificationResponse response = NotificationResponse.from(notification, null);
            notificationMessagePublisher.publish(receiverId, response);
        } catch (Exception e) {
            log.warn("[알림 Push 실패] receiverId={}: {}", receiverId, e.getMessage());
        }
    }

    private String buildMessage(NotificationType type, Member sender) {
        String username = sender.getUsername();
        return switch (type) {
            case LIKE -> username + "님이 회원님의 게시물을 좋아합니다.";
            case FOLLOW -> username + "님이 회원님을 팔로우하기 시작했습니다.";
            case COMMENT -> username + "님이 댓글을 남겼습니다.";
            case MENTION -> username + "님이 댓글에서 회원님을 언급했습니다.";
            case DM -> username + "님이 메시지를 보냈습니다.";
        };
    }
}
