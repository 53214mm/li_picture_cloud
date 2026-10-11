package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.domain.companion.CompanionChatEpisode;
import com.li.lipicturecloud.domain.companion.CompanionChatEpisodeRepository;
import com.li.lipicturecloud.domain.companion.CompanionChatMessage;
import com.li.lipicturecloud.domain.companion.CompanionChatMessageRepository;
import com.li.lipicturecloud.domain.companion.CompanionRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Short, database-serialized writes. The originating message id is a durable clear token. */
@Component
public class CompanionChatWrites {
    private final CompanionRepository companions;
    private final CompanionChatMessageRepository messages;
    private final CompanionChatEpisodeRepository episodes;
    private final TransactionTemplate transactions;

    public CompanionChatWrites(CompanionRepository companions, CompanionChatMessageRepository messages,
                               CompanionChatEpisodeRepository episodes, PlatformTransactionManager manager) {
        this.companions = companions;
        this.messages = messages;
        this.episodes = episodes;
        transactions = new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public boolean isCurrentSource(long companionId, long subjectId, long sourceId) {
        return messages.findById(sourceId).filter(message -> message.companionId() == companionId
                && message.subjectId() == subjectId).isPresent();
    }

    public boolean appendReply(CompanionChatMessage reply, long sourceId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            lock(reply.companionId(), reply.subjectId());
            if (!isCurrentSource(reply.companionId(), reply.subjectId(), sourceId)) {
                return false;
            }
            messages.append(reply);
            return true;
        }));
    }

    public boolean appendEpisode(CompanionChatEpisode episode, long sourceId) {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            lock(episode.companionId(), episode.subjectId());
            if (!isCurrentSource(episode.companionId(), episode.subjectId(), sourceId)
                    || !isCurrentSource(episode.companionId(), episode.subjectId(), episode.firstMessageId())
                    || !isCurrentSource(episode.companionId(), episode.subjectId(), episode.lastMessageId())
                    || episodes.lastCoveredMessageId(episode.companionId()) >= episode.lastMessageId()) {
                return false;
            }
            return episodes.append(episode);
        }));
    }

    public int clear(long companionId, long subjectId) {
        return transactions.execute(status -> {
            lock(companionId, subjectId);
            int deleted = messages.deleteByCompanion(companionId);
            episodes.deleteByCompanion(companionId);
            return deleted;
        });
    }

    private void lock(long companionId, long subjectId) {
        if (companions.findByOwnerIdForUpdate(subjectId)
                .filter(companion -> companion.id() == companionId).isEmpty()) {
            throw new IllegalStateException("chat owner is unavailable");
        }
    }
}
