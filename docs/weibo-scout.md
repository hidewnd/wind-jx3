# 微博监控服务

`winds-scout` 每 4 分钟从 MongoDB 读取启用的微博博主，顺序抓取最新一条非置顶微博，并通过共享 WebSocket `/ws` 推送 `weibo.updated`。相邻请求随机间隔 5～20 秒。

微博 HTTP 客户端连接超时为 10 秒、读取超时为 15 秒。多实例部署通过 MongoDB `weibo_monitor_lock` 租约保证同一时刻只有一个轮询实例；租约有效期 6 分钟，并在处理博主之间续租。

首次监控某个 UID 时只保存当前微博作为基线；服务重启后，发布时间早于本次启动时间的微博也只入库，不补推。已存在的微博 ID 不会重复广播。

## MongoDB 集合

### `weibo_subscription`

```json
{
  "_id": "1761587065",
  "uid": "1761587065",
  "screen_name": "剑网3官方微博",
  "aliases": ["官博"],
  "enabled": true,
  "frequency": 60,
  "groups": [],
  "created_at": "2026-08-28T04:00:00Z",
  "updated_at": "2026-08-28T04:00:00Z"
}
```

Java 管理接口不会覆盖参考 Python 服务使用的 `groups` 和 `frequency`。

### `weibo_posts`

微博 ID 作为 `_id`。纯文本正文与上游原始 HTML 分开保存；上游未返回的来源、地区和计数字段保存为 `null`：

```json
{
  "_id": "5140000000000000",
  "uid": "1761587065",
  "title": "剑网3官方微博",
  "content": "微博正文",
  "raw_content": "<p>微博正文</p>",
  "source": "微博网页版",
  "region_name": "发布于 四川",
  "reposts_count": 12,
  "comments_count": 34,
  "attitudes_count": 56,
  "imgs": [],
  "topics": ["剑网3"],
  "video_cover_imgs": [],
  "date": "2026-08-28 12:01:00",
  "url": "https://m.weibo.cn/detail/5140000000000000",
  "no_push": false,
  "sent_bots": []
}
```

转发微博使用对应的 `retweet_*` 字段。重复抓取只同步正文、媒体和元数据，不覆盖 `no_push`、`sent_bots` 等推送状态；历史文档不批量迁移，在后续重新抓取时自然补齐新增字段。

### `weibo_account_pool`

```json
{
  "_id": "account-1",
  "cookie": "<COOKIE>",
  "xsrf_token": "<XSRF_TOKEN>",
  "status": "active",
  "fail_count": 0,
  "request_count": 0,
  "recover_at": null
}
```

第一次请求失败后 1 小时自动恢复，第二次失败后 3 小时自动恢复；第三次及以后保持 `fail`，需要通过管理接口人工启用。Cookie 和 XSRF 不会出现在管理接口响应或日志中。

### `ws_auth_token`

管理接口复用现有 WS token，但需要额外 scope：

```json
{
  "token": "<MANAGEMENT_TOKEN>",
  "status": 1,
  "scopes": ["scout.weibo.manage"]
}
```

### `weibo_monitor_lock`

该集合由服务自动维护，不需要人工创建：

```json
{
  "_id": "weibo-poll",
  "owner": "<INSTANCE_UUID>",
  "lease_until": "2026-08-31T01:30:00Z",
  "updated_at": "2026-08-31T01:24:00Z"
}
```

## 管理接口

请求统一携带：

```http
Authorization: Bearer <MANAGEMENT_TOKEN>
```

- `GET /scout/weibo/bloggers`
- `POST /scout/weibo/bloggers`
- `GET /scout/weibo/bloggers/{uid}`
- `PUT /scout/weibo/bloggers/{uid}`
- `PATCH /scout/weibo/bloggers/{uid}/status`
- `DELETE /scout/weibo/bloggers/{uid}`
- `GET /scout/weibo/accounts`
- `POST /scout/weibo/accounts`
- `GET /scout/weibo/accounts/{id}`
- `PUT /scout/weibo/accounts/{id}/credentials`
- `PATCH /scout/weibo/accounts/{id}/status`
- `DELETE /scout/weibo/accounts/{id}`

创建博主：

```json
{
  "uid": "1761587065",
  "screenName": "剑网3官方微博",
  "aliases": ["官博"],
  "enabled": true
}
```

创建账号时传入账号 ID 和完整 Cookie，更新账号凭据时只传入完整 Cookie。服务端统一从 Cookie 的 `XSRF-TOKEN` 项解析 XSRF Token，不再接收独立的 `xsrfToken` 字段：

```json
{
  "id": "account-1",
  "cookie": "SCF=<SCF>; SUB=<SUB>; XSRF-TOKEN=<XSRF_TOKEN>; MLOGIN=1"
}
```

## WebSocket 消息

```json
{
  "type": "weibo.updated",
  "uid": "1761587065",
  "screenName": "剑网3官方微博",
  "weiboId": "5140000000000000",
  "publishedAt": "2026-08-28 12:01:00",
  "content": "微博正文",
  "rawContent": "<p>微博正文</p>",
  "source": "微博网页版",
  "regionName": "发布于 四川",
  "repostsCount": 12,
  "commentsCount": 34,
  "attitudesCount": 56,
  "url": "https://m.weibo.cn/detail/5140000000000000",
  "images": ["https://example.invalid/main.jpg"],
  "topics": ["剑网3"],
  "videoCoverImages": [],
  "retweet": {
    "screenName": "原作者",
    "content": "原微博正文",
    "rawContent": "<p>原微博正文</p>",
    "source": "微博 iPhone客户端",
    "regionName": null,
    "repostsCount": 7,
    "commentsCount": 8,
    "attitudesCount": null,
    "images": [],
    "videoCoverImages": []
  }
}
```

`content` 是保留段落和换行的纯文本，适合直接展示；`rawContent` 是微博上游返回的原始 HTML，属于不可信外部内容，客户端渲染前必须执行 HTML 白名单清理。非转发微博的 `retweet` 为 `null`。消息为实时尽力广播，不提供离线重放和逐客户端送达确认。
