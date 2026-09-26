package com.li.lipicturecloud.application.companion.view;

import java.util.List;

/**
 * 当前伙伴快照的前端视图。
 *
 * <p>等级起点、下一等级经验等派生展示值由 {@code CompanionViewAssembler} 统一计算，
 * 避免 Controller 了解领域对象内部细节。</p>
 */
public record CompanionView(Long id, long lifeExperience, int level, String lifeStage,
                            long levelStartExperience, long nextLevelExperience,
                            CompanionTraitsView traits, List<CompanionSkillView> skills,
                            String balanceVersion, long revision) {
}
