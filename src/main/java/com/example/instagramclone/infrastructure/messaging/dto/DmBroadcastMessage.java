package com.example.instagramclone.infrastructure.messaging.dto;

import com.example.instagramclone.domain.dm.dto.DirectMessageResponse;

/**
 * Day 24: DM 채널을 통해 서버 간 전달되는 메시지 봉투(envelope).
 *
 * <p>Redis Pub/Sub 은 "누구에게 보낼지"를 모르므로, 봉투에 수신자 ID 를 담아
 * 각 서버의 리스너가 자기 메모리의 세션을 조회할 때 사용한다.
 * payload 는 기존 {@link DirectMessageResponse} 를 그대로 재활용 — 클라이언트 스키마 변화 없음.
 */
public record DmBroadcastMessage(
        Long recipientId,
        DirectMessageResponse payload
) {}
