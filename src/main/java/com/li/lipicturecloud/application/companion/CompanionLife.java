package com.li.lipicturecloud.application.companion;

import com.li.lipicturecloud.application.companion.view.CompanionHomeView;
import com.li.lipicturecloud.application.companion.view.FeedPictureResult;
import com.li.lipicturecloud.manager.auth.model.AuthorizationSubject;

/**
 * 伙伴模块提供给 HTTP 层的应用用例清单。
 *
 * <p>接口只声明“可以做什么”，实际的权限、幂等、分析与成长编排由
 * {@link CompanionLifeService} 完成。</p>
 */
public interface CompanionLife {
    /** 读取当前主体的伙伴主页快照；尚未唤醒时 companion 字段为空。 */
    CompanionHomeView home(AuthorizationSubject subject);

    /** 幂等地创建当前主体的伙伴，然后返回主页快照。 */
    CompanionHomeView awaken(AuthorizationSubject subject);

    /** 使用一张当前主体有权查看的图片执行一次可重试喂养。 */
    FeedPictureResult feed(FeedPictureCommand command);
}
