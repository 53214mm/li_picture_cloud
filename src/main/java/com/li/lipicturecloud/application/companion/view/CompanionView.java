package com.li.lipicturecloud.application.companion.view;

import java.util.List;

public record CompanionView(Long id, long lifeExperience, int level, String lifeStage,
                            long levelStartExperience, long nextLevelExperience,
                            CompanionTraitsView traits, List<CompanionSkillView> skills,
                            String balanceVersion, long revision) {
}
