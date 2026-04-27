package com.example.instagramclone.infrastructure.messaging.dto;

import com.example.instagramclone.domain.notification.dto.NotificationResponse;

/**
 * Day 24: 알림 채널 봉투.
 */
public record NotificationBroadcastMessage(
        Long receiverId,
        NotificationResponse payload
) {}
