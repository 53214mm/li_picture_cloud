package com.li.lipicturecloud.application.companion.view;

/** 前端展示的一项伙伴技能及其当前等级进度。 */
public record CompanionSkillView(String code, long experience, int level, long nextLevelExperience) {
}
