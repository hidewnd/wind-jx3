package com.hidewnd.winds.scout.event;

import com.hidewnd.winds.scout.model.WeiboPost;

/**
 * 新微博成功入库后发布的领域事件。
 *
 * @param post 需要通过共享 WebSocket 广播的微博内容
 */
public record WeiboUpdatedEvent(WeiboPost post) {
}
