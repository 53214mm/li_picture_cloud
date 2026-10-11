package com.li.lipicturecloud.application.companion.observation;

import com.li.lipicturecloud.application.companion.*;
import com.li.lipicturecloud.domain.companion.*;
import com.li.lipicturecloud.manager.auth.SpaceAuthorizationAccessService;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;
import com.li.lipicturecloud.service.PictureService;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import static com.li.lipicturecloud.application.companion.observation.ObservationContracts.*;

@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(prefix="app.companion",name="enabled",havingValue="true",matchIfMissing=true)
public class ObservationContext {
    private final PictureService pictures;
    private final SpaceAuthorizationAccessService auth;
    private final CompanionRepository companions;
    private final CompanionChatMessageRepository messages;
    private final CompanionChatSessions sessions;
    private final CompanionMemoryService memories;
    private final CompanionMemoryRecall recall;
    private final ChatSessionBoundaryRepository boundaries;
    private final ObservationChatActivity activity;
    public ObservationContext(PictureService pictures, SpaceAuthorizationAccessService auth,CompanionRepository companions,
            CompanionChatMessageRepository messages,CompanionChatSessions sessions,CompanionMemoryService memories,
            CompanionMemoryRecall recall,ChatSessionBoundaryRepository boundaries,ObservationChatActivity activity) {
        this.pictures=pictures;this.auth=auth;this.companions=companions;this.messages=messages;this.sessions=sessions;
        this.memories=memories;this.recall=recall;this.boundaries=boundaries;
        this.activity=activity;
    }
    public Snapshot prepare(long owner,PageInput input,Instant now) {
        if(activity.busy(owner))throw new IllegalArgumentException("CHAT_BUSY");
        if(!PAGES.contains(input.page())) throw new IllegalArgumentException("UNSUPPORTED_PAGE");
        long epoch=boundaries.chatEpoch(owner);
        List<PictureRef> visible=input.pictureIds().stream().distinct().map(id->picture(owner,id)).toList();
        var companion=companions.findByOwnerId(owner).orElseThrow(()->new IllegalStateException("COMPANION_UNAVAILABLE"));
        var chat=sessions.current(owner,messages.findRecent(companion.id(),6),now).messagesNewestFirst().stream()
                .filter(m->m.subjectId()==owner && m.companionId()==companion.id())
                .map(m->new ChatLine(m.id(),m.role().name(),m.content(),m.createdTime())).toList();
        String query=visible.stream().map(p->"图片 "+p.id()+" "+p.name()+" "+p.category()+" "+p.tags()).reduce(input.page(),(a,b)->a+" "+b);
        List<String> userQueries=chat.stream().filter(c->c.role().equals("USER")).map(ChatLine::content).toList();
        if(!userQueries.isEmpty()) query += " "+userQueries.getFirst();
        var usable=memories.confirmedMemoriesUsableBy(companion,AuthorizationSubject.user(owner),30);
        var selected=recall.recallLocally(query,usable,companion.id(),owner,2,userQueries).recalled().stream()
                .map(r->new MemoryLine(r.memory().id(),r.memory().pictureId(),r.memory().content(),r.memory().updatedTime())).toList();
        if(activity.busy(owner) || epoch!=boundaries.chatEpoch(owner)) throw new IllegalArgumentException("CHAT_CHANGED");
        return new Snapshot(input.page(),visible,chat,selected,epoch,now);
    }
    public boolean current(long owner,Snapshot snapshot) {
        if(activity.busy(owner) || boundaries.chatEpoch(owner)!=snapshot.chatEpoch()) return false;
        try {
            for(var ref:snapshot.pictures()) if(!Objects.equals(picture(owner,ref.id()),ref)) return false;
            var companion=companions.findByOwnerId(owner).orElseThrow();
            var usable=memories.confirmedMemoriesUsableBy(companion,AuthorizationSubject.user(owner),100);
            for(var ref:snapshot.memories()) if(usable.stream().noneMatch(m->Objects.equals(m.id(),ref.id())
                    && Objects.equals(m.pictureId(),ref.pictureId()) && Objects.equals(m.content(),ref.content())
                    && Objects.equals(m.updatedTime(),ref.at()))) return false;
            return !activity.busy(owner) && boundaries.chatEpoch(owner)==snapshot.chatEpoch();
        } catch(RuntimeException unavailable){return false;}
    }
    private PictureRef picture(long owner,long id) {
        auth.checkForUser("picture:view",id,owner);
        var p=pictures.getById(id);
        if(p==null) throw new IllegalArgumentException("PICTURE_UNAVAILABLE");
        return new PictureRef(id,p.getName(),p.getCategory(),p.getTags(),
                p.getUpdateTime()==null?null:p.getUpdateTime().toInstant());
    }
}
