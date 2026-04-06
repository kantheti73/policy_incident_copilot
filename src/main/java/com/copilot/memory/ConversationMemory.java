package com.copilot.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages conversation memory per session with safety filtering.
 * Only stores safe preferences; avoids persisting sensitive content.
 */
@Slf4j
@Component
public class ConversationMemory {

    private final Map<String, Deque<MemoryEntry>> sessions = new ConcurrentHashMap<>();
    private final SafeMemoryFilter safeMemoryFilter;

    @Value("${copilot.memory.max-conversation-turns:20}")
    private int maxTurns;

    public ConversationMemory(SafeMemoryFilter safeMemoryFilter) {
        this.safeMemoryFilter = safeMemoryFilter;
    }

    public void addUserMessage(String sessionId, String message) {
        String safeMessage = safeMemoryFilter.filter(message);
        addEntry(sessionId, MemoryEntry.builder()
                .role("user")
                .content(safeMessage)
                .timestamp(Instant.now())
                .build());
    }

    public void addAssistantMessage(String sessionId, String message) {
        String safeMessage = safeMemoryFilter.filter(message);
        addEntry(sessionId, MemoryEntry.builder()
                .role("assistant")
                .content(safeMessage)
                .timestamp(Instant.now())
                .build());
    }

    public List<MemoryEntry> getHistory(String sessionId) {
        Deque<MemoryEntry> history = sessions.get(sessionId);
        if (history == null) return Collections.emptyList();
        return List.copyOf(history);
    }

    public String getHistoryAsText(String sessionId) {
        List<MemoryEntry> history = getHistory(sessionId);
        if (history.isEmpty()) return "";

        StringBuilder sb = new StringBuilder("Conversation history:\n");
        for (MemoryEntry entry : history) {
            sb.append(entry.getRole()).append(": ").append(entry.getContent()).append("\n");
        }
        return sb.toString();
    }

    public void clearSession(String sessionId) {
        sessions.remove(sessionId);
        log.info("Cleared memory for session: {}", sessionId);
    }

    private void addEntry(String sessionId, MemoryEntry entry) {
        sessions.computeIfAbsent(sessionId, k -> new ArrayDeque<>());
        Deque<MemoryEntry> history = sessions.get(sessionId);

        history.addLast(entry);

        // Evict oldest entries beyond the limit
        while (history.size() > maxTurns * 2) {
            history.removeFirst();
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    public static class MemoryEntry {
        private String role;
        private String content;
        private Instant timestamp;
    }
}
