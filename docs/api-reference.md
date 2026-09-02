# Winds JX3 服务接口文档

## 1. 文档说明

本文档依据当前仓库 Controller、DTO、异常处理器和 WebSocket 事件模型整理，覆盖当前服务全部对外接口：

- 16 个 HTTP 接口；
- 1 个 WebSocket 接入地址；
- 3 种 WebSocket 服务端推送事件。

文中的请求与响应 JSON 用于说明字段结构，不代表已经对 MongoDB、Redis、OSS、JX3API 或微博上游完成实时联调。代理接口的 `obj` 由调用方选择的上游路径决定，无法由本服务静态定义全部业务字段。

## 2. 基础约定

### 2.1 服务地址

| 项目 | 值 |
| --- | --- |
| 默认 HTTP 基础地址 | `http://<host>:9002` |
| 默认 WebSocket 地址 | `ws://<host>:9002/ws` |
| Swagger UI | `http://<host>:9002/swagger-ui.html` |
| OpenAPI JSON | `http://<host>:9002/v3/api-docs` |
| 字符编码 | UTF-8 |

生产环境如果在反向代理上启用 TLS，应将 `http`、`ws` 分别替换为 `https`、`wss`。项目未配置统一的 `context-path`。

### 2.2 HTTP 接口总览

| 模块 | 请求方式 | 路径 | 鉴权 | 描述 |
| --- | --- | --- | --- | --- |
| 成本计算 | POST | `/costing/one` | 无 | 查询单个技艺制品成本 |
| 成本计算 | POST | `/costing/list` | 无 | 查询多个技艺制品成本 |
| JX3API 代理 | POST | `/proxy/jx3api` | 无 | 代理请求配置的 JX3API 上游 |
| 黄历 | POST | `/thire/huangli/update` | 无 | 上传并更新指定日期的黄历图片 |
| 微博管理 | GET | `/scout/weibo/bloggers` | Bearer Token + scope | 查询监控博主列表 |
| 微博管理 | POST | `/scout/weibo/bloggers` | Bearer Token + scope | 创建监控博主 |
| 微博管理 | GET | `/scout/weibo/bloggers/{uid}` | Bearer Token + scope | 查询监控博主详情 |
| 微博管理 | PUT | `/scout/weibo/bloggers/{uid}` | Bearer Token + scope | 更新监控博主配置 |
| 微博管理 | PATCH | `/scout/weibo/bloggers/{uid}/status` | Bearer Token + scope | 修改监控博主状态 |
| 微博管理 | DELETE | `/scout/weibo/bloggers/{uid}` | Bearer Token + scope | 删除监控博主 |
| 微博管理 | GET | `/scout/weibo/accounts` | Bearer Token + scope | 查询微博抓取账号列表 |
| 微博管理 | POST | `/scout/weibo/accounts` | Bearer Token + scope | 创建微博抓取账号 |
| 微博管理 | GET | `/scout/weibo/accounts/{id}` | Bearer Token + scope | 查询微博抓取账号详情 |
| 微博管理 | PUT | `/scout/weibo/accounts/{id}/credentials` | Bearer Token + scope | 更新微博抓取账号凭据 |
| 微博管理 | PATCH | `/scout/weibo/accounts/{id}/status` | Bearer Token + scope | 修改微博抓取账号状态 |
| 微博管理 | DELETE | `/scout/weibo/accounts/{id}` | Bearer Token + scope | 删除微博抓取账号 |

### 2.3 统一 HTTP 响应

除微博管理接口的鉴权拦截响应外，HTTP 接口使用以下统一响应结构：

| 字段 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `success` | boolean | 否 | 是否成功。成功响应为 `true` |
| `code` | integer | 否 | 业务状态码 |
| `msg` | string | 否 | 响应说明 |
| `obj` | object、array 或 null | 是 | 业务数据 |

成功响应示例：

```json
{
  "success": true,
  "code": 200,
  "msg": "请求成功",
  "obj": {}
}
```

当前实现存在以下需要调用方特别注意的差异：

- 普通接口参数校验失败时，HTTP 状态为 `200`，业务 `code` 为 `101`。
- 普通接口的全局错误响应通过 `R.error(...)` 构造，但当前 `R` 默认未将 `success` 改为 `false`，因此错误响应中的 `success` 仍可能是 `true`。调用方应同时判断 `code`，不能只判断 `success`。
- 微博管理接口的业务错误和参数错误使用实际 HTTP 状态码，并明确返回 `success: false`。
- 微博管理鉴权拦截响应只有 `success`、`code`、`msg`，不包含 `obj`。

普通参数校验错误示例：

```json
{
  "success": true,
  "code": 101,
  "msg": "[formulaName]配方名称不能为空",
  "obj": null
}
```

## 3. 成本计算接口

### 3.1 成本响应公共字段

`/costing/one` 和 `/costing/list` 的 `obj` 均包含以下公共字段。

| 字段 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `server` | string | 是 | 服务器名称 |
| `energies` | integer | 是 | 预计总消耗精力 |
| `cost` | integer(int64) | 是 | 合计成本原始数值 |
| `costString` | string | 是 | 格式化后的合计成本 |
| `value` | integer(int64) | 是 | 合计交易行价原始数值 |
| `valueString` | string | 是 | 格式化后的合计交易行价 |
| `actualProfit` | integer(int64) | 是 | 实际利润原始数值 |
| `actualProfitString` | string | 是 | 格式化后的实际利润 |
| `requiredMap` | object(map) | 是 | 所需材料映射；键由成本构建器生成，值为材料对象 |
| `makeDetail` | array | 是 | 制作批次明细 |

`requiredMap.<key>` 材料对象：

| 字段 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `id` | string | 是 | 材料 ID |
| `name` | string | 是 | 材料名称 |
| `number` | integer | 是 | 所需材料数量 |
| `value` | integer(int64) | 是 | 材料价格原始数值 |
| `valueString` | string | 是 | 格式化后的材料价格 |

`makeDetail[]` 制作明细：

| 字段 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `no` | integer | 是 | 制作批次序号 |
| `name` | string | 是 | 制作物品名称 |
| `makeNumber` | integer | 是 | 本批次产出数量 |

### 3.2 查询单个技艺制品成本

**接口描述**：计算单个技艺制品的材料、精力、成本、交易行价和利润。

**请求方式**：`POST /costing/one`

**必须请求头**：

| 请求头 | 值 |
| --- | --- |
| `Content-Type` | `application/json` |

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 约束/默认值 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `formulaName` | body | string | 是 | 不得为空或全空白 | 技艺制品名称 |
| `number` | body | integer | 是 | 最小值 `1` | 需求数量 |
| `server` | body | string | 否 | 空值使用配置项 `box.default.server`，配置缺省值为“剑胆琴心” | 服务器名称 |
| `rangeCreate` | body | boolean | 否 | 默认 `true` | 是否按随机产出数量计算 |

**请求参数示例**：

```json
{
  "formulaName": "示例配方",
  "number": 2,
  "server": "剑胆琴心",
  "rangeCreate": true
}
```

**返回参数**：外层字段见“统一 HTTP 响应”，`obj` 除成本响应公共字段外还包含：

| 参数 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `type` | string(enum) | 是 | 技艺类型：`COOKING`、`TAILORING`、`MEDICINE`、`FOUNDING`、`FURNITURE` |
| `formulaName` | string | 是 | 技艺制品名称 |
| `materialId` | string | 是 | 成品材料 ID |
| `number` | integer | 是 | 请求的需求数量 |
| `actualNumber` | integer | 是 | 实际产出数量 |

**返回参数示例**：

```json
{
  "success": true,
  "code": 200,
  "msg": "请求成功",
  "obj": {
    "server": "剑胆琴心",
    "energies": 20,
    "cost": 12500,
    "costString": "1金25银",
    "value": 18000,
    "valueString": "1金80银",
    "actualProfit": 5300,
    "actualProfitString": "53银",
    "requiredMap": {
      "示例材料": {
        "id": "10001",
        "name": "示例材料",
        "number": 4,
        "value": 12500,
        "valueString": "1金25银"
      }
    },
    "makeDetail": [
      {
        "no": 1,
        "name": "示例配方",
        "makeNumber": 2
      }
    ],
    "type": "COOKING",
    "formulaName": "示例配方",
    "materialId": "20001",
    "number": 2,
    "actualNumber": 2
  }
}
```

### 3.3 查询多个技艺制品成本

**接口描述**：合并计算多个技艺制品的材料、精力、成本、交易行价和利润。

**请求方式**：`POST /costing/list`

**必须请求头**：

| 请求头 | 值 |
| --- | --- |
| `Content-Type` | `application/json` |

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 约束/默认值 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `server` | body | string | 否 | 空值使用配置项 `box.default.server`，配置缺省值为“剑胆琴心” | 服务器名称 |
| `rangeCreate` | body | boolean | 否 | 默认 `true` | 是否按随机产出数量计算 |
| `items` | body | array<object> | 是 | 当前只校验非 `null`，空数组未被拒绝 | 成本计算明细 |
| `items[].formulaName` | body | string | 是 | 字段声明不得为空；当前列表对象未启用级联校验 | 技艺制品名称 |
| `items[].number` | body | integer | 是 | 字段声明最小值 `1`；当前列表对象未启用级联校验 | 需求数量 |

**请求参数示例**：

```json
{
  "server": "剑胆琴心",
  "rangeCreate": true,
  "items": [
    {
      "formulaName": "示例配方A",
      "number": 1
    },
    {
      "formulaName": "示例配方B",
      "number": 2
    }
  ]
}
```

**返回参数**：外层字段见“统一 HTTP 响应”，`obj` 包含成本响应公共字段以及 `formulas`。

| 参数 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `formulas` | object(map) | 是 | 成品映射，键为请求中的 `formulaName`；同名键以后处理的结果为准 |
| `formulas.<name>.type` | string(enum) | 是 | 技艺类型，取值同单项接口 |
| `formulas.<name>.formulaName` | string | 是 | 技艺制品名称 |
| `formulas.<name>.materialId` | string | 是 | 成品材料 ID |
| `formulas.<name>.number` | integer | 是 | 请求的需求数量 |
| `formulas.<name>.actualNumber` | integer | 是 | 实际产出数量 |

**返回参数示例**：

```json
{
  "success": true,
  "code": 200,
  "msg": "请求成功",
  "obj": {
    "server": "剑胆琴心",
    "energies": 30,
    "cost": 22000,
    "costString": "2金20银",
    "value": 31000,
    "valueString": "3金10银",
    "actualProfit": 8600,
    "actualProfitString": "86银",
    "requiredMap": {},
    "makeDetail": [],
    "formulas": {
      "示例配方A": {
        "type": "COOKING",
        "formulaName": "示例配方A",
        "materialId": "20001",
        "number": 1,
        "actualNumber": 1
      },
      "示例配方B": {
        "type": "MEDICINE",
        "formulaName": "示例配方B",
        "materialId": "20002",
        "number": 2,
        "actualNumber": 2
      }
    }
  }
}
```

## 4. JX3API 代理接口

### 4.1 代理请求

**接口描述**：将请求参数转发给配置的 JX3API 上游。服务端会覆盖或补充 `params.ticket`、`params.token`，调用方不需要传入这两个凭据。

**请求方式**：`POST /proxy/jx3api`

**必须请求头**：

| 请求头 | 值 |
| --- | --- |
| `Content-Type` | `application/json` |

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 约束 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `url` | body | string | 是 | 不得为空或全空白 | 上游相对路径；模型说明要求以 `/data` 开头，但当前代码未执行此前缀校验 |
| `params` | body | object(map) | 实际必填 | 应至少传 `{}`；缺失会触发空指针错误 | 转发给上游的业务参数 |

**请求参数示例**：

```json
{
  "url": "/data/example",
  "params": {
    "server": "剑胆琴心",
    "name": "示例角色"
  }
}
```

**返回参数**：

| 参数 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `success` | boolean | 否 | 成功标记 |
| `code` | integer | 否 | 成功时为 `200` |
| `msg` | string | 否 | 成功时为“代理请求成功” |
| `obj` | object | 由上游决定 | 上游返回 JSON 对象的完整透传结果，字段由 `url` 对应的上游接口定义 |

**返回参数示例**：

```json
{
  "success": true,
  "code": 200,
  "msg": "代理请求成功",
  "obj": {
    "code": 200,
    "msg": "success",
    "data": {}
  }
}
```

上游网络异常、响应不是合法 JSON 或 `params` 为 `null` 时会进入全局异常响应；本接口未定义独立错误码。

## 5. 黄历接口

### 5.1 更新黄历图片

**接口描述**：上传 PNG、JPEG、GIF 或 WebP 图片到对象存储，以日期写入 MongoDB，并在成功后广播 `huangli.updated` WebSocket 事件。实际文件后缀根据文件内容识别，不信任原始文件名。

**请求方式**：`POST /thire/huangli/update`

**必须请求头**：

| 请求头 | 值 |
| --- | --- |
| `Content-Type` | `multipart/form-data; boundary=<boundary>` |

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 约束/默认值 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `file` | form-data | binary | 是 | 非空；Part Content-Type 必须以 `image/` 开头；内容必须为 PNG、JPEG、GIF 或 WebP | 黄历图片 |
| `date` | form-data | string(date) | 否 | 格式 `yyyy-MM-dd`；默认服务端当前日期 | 黄历日期 |

**请求参数示例**：

```powershell
curl.exe -X POST "http://localhost:9002/thire/huangli/update" `
  -F "file=@C:\images\huangli.png;type=image/png" `
  -F "date=2026-09-01"
```

**返回参数**：

| 参数 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `success` | boolean | 否 | 成功标记 |
| `code` | integer | 否 | 成功时为 `200` |
| `msg` | string | 否 | 成功时为“请求成功” |
| `obj.date` | string(date) | 否 | 黄历日期，格式 `yyyy-MM-dd` |
| `obj.url` | string(uri) | 否 | 对象存储返回的图片访问地址 |

**返回参数示例**：

```json
{
  "success": true,
  "code": 200,
  "msg": "请求成功",
  "obj": {
    "date": "2026-09-01",
    "url": "https://cdn.example.com/huangli/2026-09-01.png"
  }
}
```

图片类型不支持时，当前实现进入普通全局异常响应，业务 `code` 为 `502`，`msg` 为“仅支持上传图片”或“仅支持 PNG、JPEG、GIF 或 WebP 图片”。

## 6. 微博监控管理接口

### 6.1 鉴权与公共请求头

所有 `/scout/weibo/**` 接口必须携带：

| 请求头 | 必填 | 值/说明 |
| --- | --- | --- |
| `Authorization` | 是 | `Bearer <MANAGEMENT_TOKEN>` |
| `Content-Type` | 有 JSON 请求体时是 | `application/json` |

Token 必须来自 MongoDB 集合 `ws_auth_token` 中 `status=1` 的记录，并且 `scopes` 数组包含 `scout.weibo.manage`。

微博管理接口响应中的时间字段统一使用上海时区，格式为 `yyyy-MM-dd HH:mm:ss`；没有时间值时返回 `null`。

未授权响应：

```json
{
  "success": false,
  "code": 401,
  "msg": "未授权"
}
```

权限不足响应：

```json
{
  "success": false,
  "code": 403,
  "msg": "权限不足"
}
```

### 6.2 微博博主响应模型

`BloggerResponse` 字段：

| 字段 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `uid` | string | 否 | 微博 UID |
| `screenName` | string | 否 | 博主显示名称；创建/更新时未传则保存为空字符串 |
| `aliases` | array<string> | 否 | 博主别称；未传时为空数组 |
| `enabled` | boolean | 否 | 是否启用监控 |
| `createdAt` | string | 是 | 创建时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `updatedAt` | string | 是 | 更新时间，格式 `yyyy-MM-dd HH:mm:ss` |

单个博主成功响应示例：

```json
{
  "success": true,
  "code": 200,
  "msg": "请求成功",
  "obj": {
    "uid": "1761587065",
    "screenName": "剑网3官方微博",
    "aliases": ["官博"],
    "enabled": true,
    "createdAt": "2026-09-01 10:00:00",
    "updatedAt": "2026-09-01 10:00:00"
  }
}
```

### 6.3 查询监控博主列表

**接口描述**：查询全部监控博主。

**请求方式**：`GET /scout/weibo/bloggers`

**必须请求头**：见“鉴权与公共请求头”；本接口无请求体，只有 `Authorization` 必填。

**请求参数**：无。

**请求参数示例**：

```http
GET /scout/weibo/bloggers HTTP/1.1
Host: localhost:9002
Authorization: Bearer <MANAGEMENT_TOKEN>
```

**返回参数**：外层字段见“统一 HTTP 响应”，`obj` 为 `array<BloggerResponse>`，元素字段见“微博博主响应模型”。

**返回参数示例**：

```json
{
  "success": true,
  "code": 200,
  "msg": "请求成功",
  "obj": [
    {
      "uid": "1761587065",
      "screenName": "剑网3官方微博",
      "aliases": ["官博"],
      "enabled": true,
      "createdAt": "2026-09-01 10:00:00",
      "updatedAt": "2026-09-01 10:00:00"
    }
  ]
}
```

### 6.4 创建监控博主

**接口描述**：创建一个新的微博监控博主。重复 UID 返回 HTTP `409`。

**请求方式**：`POST /scout/weibo/bloggers`

**必须请求头**：`Authorization: Bearer <MANAGEMENT_TOKEN>`、`Content-Type: application/json`。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 约束/默认值 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `uid` | body | string | 是 | 仅允许数字，正则 `\d+` | 微博 UID |
| `screenName` | body | string | 否 | 默认空字符串 | 博主显示名称 |
| `aliases` | body | array<string> | 否 | 默认空数组；元素不得为 `null`、空或全空白 | 博主别称 |
| `enabled` | body | boolean | 否 | 默认 `true` | 是否启用监控 |

**请求参数示例**：

```json
{
  "uid": "1761587065",
  "screenName": "剑网3官方微博",
  "aliases": ["官博"],
  "enabled": true
}
```

**返回参数**：`obj` 为 `BloggerResponse`，完整字段见“微博博主响应模型”。

**返回参数示例**：见“微博博主响应模型”的单个博主成功响应示例。

### 6.5 查询监控博主详情

**接口描述**：按 UID 查询监控博主；不存在时返回 HTTP `404` 和“微博博主不存在”。

**请求方式**：`GET /scout/weibo/bloggers/{uid}`

**必须请求头**：仅 `Authorization: Bearer <MANAGEMENT_TOKEN>`。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 描述 |
| --- | --- | --- | --- | --- |
| `uid` | path | string | 是 | 微博 UID |

**请求参数示例**：`GET /scout/weibo/bloggers/1761587065`

**返回参数**：`obj` 为 `BloggerResponse`，完整字段见“微博博主响应模型”。

**返回参数示例**：见“微博博主响应模型”的单个博主成功响应示例。

### 6.6 更新监控博主配置

**接口描述**：替换可管理的博主配置，不修改 MongoDB 中的 `groups` 和 `frequency`。请求中未传 `screenName`、`aliases` 时会分别写入空字符串、空数组；未传 `enabled` 时保留原状态。

**请求方式**：`PUT /scout/weibo/bloggers/{uid}`

**必须请求头**：`Authorization: Bearer <MANAGEMENT_TOKEN>`、`Content-Type: application/json`。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 约束 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `uid` | path | string | 是 | 无额外格式校验 | 微博 UID |
| `screenName` | body | string | 否 | 未传时写入空字符串 | 博主显示名称 |
| `aliases` | body | array<string> | 否 | 未传时写入空数组；元素不得为空 | 博主别称 |
| `enabled` | body | boolean | 否 | 未传时保留原值 | 是否启用监控 |

**请求参数示例**：

```json
{
  "screenName": "剑网3官方微博",
  "aliases": ["官博", "剑三官博"],
  "enabled": true
}
```

**返回参数**：`obj` 为更新后的 `BloggerResponse`。

**返回参数示例**：见“微博博主响应模型”的单个博主成功响应示例。

### 6.7 修改监控博主状态

**接口描述**：启用或停用指定博主的监控。

**请求方式**：`PATCH /scout/weibo/bloggers/{uid}/status`

**必须请求头**：`Authorization: Bearer <MANAGEMENT_TOKEN>`、`Content-Type: application/json`。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 描述 |
| --- | --- | --- | --- | --- |
| `uid` | path | string | 是 | 微博 UID |
| `enabled` | body | boolean | 是 | 是否启用监控 |

**请求参数示例**：

```json
{
  "enabled": false
}
```

**返回参数**：`obj` 为修改后的 `BloggerResponse`。

**返回参数示例**：与单个博主示例相同，其中 `obj.enabled` 为请求值。

### 6.8 删除监控博主

**接口描述**：删除指定 UID 的监控博主；不存在时返回 HTTP `404`。

**请求方式**：`DELETE /scout/weibo/bloggers/{uid}`

**必须请求头**：仅 `Authorization: Bearer <MANAGEMENT_TOKEN>`。

**请求参数**：`uid`，path string，必填，表示微博 UID。

**请求参数示例**：`DELETE /scout/weibo/bloggers/1761587065`

**返回参数**：统一响应字段；`obj` 为 `null`，`msg` 为“删除成功”。

**返回参数示例**：

```json
{
  "success": true,
  "code": 200,
  "msg": "删除成功",
  "obj": null
}
```

### 6.9 微博抓取账号响应模型

管理响应不会返回 Cookie、XSRF Token 或最近错误详情。

| 字段 | 类型 | 是否为空 | 描述 |
| --- | --- | --- | --- |
| `id` | string | 否 | 抓取账号唯一 ID |
| `status` | string(enum) | 否 | `active` 或 `fail` |
| `failCount` | integer | 否 | 连续失败次数 |
| `requestCount` | integer(int64) | 否 | 累计请求次数 |
| `lastErrorAt` | string | 是 | 最近失败时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `lastUsedAt` | string | 是 | 最近使用时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `recoverAt` | string | 是 | 自动恢复时间，格式 `yyyy-MM-dd HH:mm:ss`；`null` 表示不自动恢复 |
| `createdAt` | string | 是 | 创建时间，格式 `yyyy-MM-dd HH:mm:ss` |
| `updatedAt` | string | 是 | 更新时间，格式 `yyyy-MM-dd HH:mm:ss` |

单个账号成功响应示例：

```json
{
  "success": true,
  "code": 200,
  "msg": "请求成功",
  "obj": {
    "id": "account-1",
    "status": "active",
    "failCount": 0,
    "requestCount": 12,
    "lastErrorAt": null,
    "lastUsedAt": "2026-09-01 10:30:00",
    "recoverAt": null,
    "createdAt": "2026-09-01 10:00:00",
    "updatedAt": "2026-09-01 10:30:00"
  }
}
```

### 6.10 查询微博抓取账号列表

**接口描述**：查询全部微博抓取账号的脱敏状态。

**请求方式**：`GET /scout/weibo/accounts`

**必须请求头**：仅 `Authorization: Bearer <MANAGEMENT_TOKEN>`。

**请求参数**：无。

**请求参数示例**：`GET /scout/weibo/accounts`

**返回参数**：`obj` 为 `array<AccountResponse>`，元素完整字段见“微博抓取账号响应模型”。

**返回参数示例**：

```json
{
  "success": true,
  "code": 200,
  "msg": "请求成功",
  "obj": [
    {
      "id": "account-1",
      "status": "active",
      "failCount": 0,
      "requestCount": 12,
      "lastErrorAt": null,
      "lastUsedAt": "2026-09-01 10:30:00",
      "recoverAt": null,
      "createdAt": "2026-09-01 10:00:00",
      "updatedAt": "2026-09-01 10:30:00"
    }
  ]
}
```

### 6.11 创建微博抓取账号

**接口描述**：保存新的微博抓取账号及登录 Cookie。Cookie 必须包含非空的 `XSRF-TOKEN=<value>`；重复 ID 返回 HTTP `409`。响应不回显凭据。

**请求方式**：`POST /scout/weibo/accounts`

**必须请求头**：`Authorization: Bearer <MANAGEMENT_TOKEN>`、`Content-Type: application/json`。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 约束 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `id` | body | string | 是 | 不得为空或全空白 | 抓取账号唯一 ID |
| `cookie` | body | string | 是 | 不得为空，且必须包含非空 `XSRF-TOKEN` 项 | 完整微博登录 Cookie |

**请求参数示例**：

```json
{
  "id": "account-1",
  "cookie": "SCF=<SCF>; SUB=<SUB>; XSRF-TOKEN=<XSRF_TOKEN>; MLOGIN=1"
}
```

**返回参数**：`obj` 为脱敏后的 `AccountResponse`，完整字段见“微博抓取账号响应模型”。

**返回参数示例**：见“微博抓取账号响应模型”的单个账号成功响应示例。

### 6.12 查询微博抓取账号详情

**接口描述**：按 ID 查询微博抓取账号的脱敏状态；不存在时返回 HTTP `404` 和“微博账号不存在”。

**请求方式**：`GET /scout/weibo/accounts/{id}`

**必须请求头**：仅 `Authorization: Bearer <MANAGEMENT_TOKEN>`。

**请求参数**：`id`，path string，必填，表示抓取账号 ID。

**请求参数示例**：`GET /scout/weibo/accounts/account-1`

**返回参数**：`obj` 为 `AccountResponse`。

**返回参数示例**：见“微博抓取账号响应模型”的单个账号成功响应示例。

### 6.13 更新微博抓取账号凭据

**接口描述**：更新账号 Cookie，并从中重新提取 XSRF Token。成功后账号状态重置为 `active`，连续失败次数清零，并清除失败与恢复时间。

**请求方式**：`PUT /scout/weibo/accounts/{id}/credentials`

**必须请求头**：`Authorization: Bearer <MANAGEMENT_TOKEN>`、`Content-Type: application/json`。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 约束 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `id` | path | string | 是 | 无额外格式校验 | 抓取账号 ID |
| `cookie` | body | string | 是 | 不得为空，且必须包含非空 `XSRF-TOKEN` 项 | 新的完整微博登录 Cookie |

**请求参数示例**：

```json
{
  "cookie": "SCF=<NEW_SCF>; SUB=<NEW_SUB>; XSRF-TOKEN=<NEW_XSRF_TOKEN>; MLOGIN=1"
}
```

**返回参数**：`obj` 为更新后的脱敏 `AccountResponse`。

**返回参数示例**：见“微博抓取账号响应模型”的单个账号成功响应示例，其中 `status` 为 `active`、`failCount` 为 `0`。

### 6.14 修改微博抓取账号状态

**接口描述**：将账号状态设置为 `active` 或 `fail`，并清除 `recoverAt`。

**请求方式**：`PATCH /scout/weibo/accounts/{id}/status`

**必须请求头**：`Authorization: Bearer <MANAGEMENT_TOKEN>`、`Content-Type: application/json`。

**请求参数**：

| 参数 | 位置 | 类型 | 必填 | 约束 | 描述 |
| --- | --- | --- | --- | --- | --- |
| `id` | path | string | 是 | 无额外格式校验 | 抓取账号 ID |
| `status` | body | string | 是 | 只允许 `active` 或 `fail` | 新账号状态 |

**请求参数示例**：

```json
{
  "status": "active"
}
```

**返回参数**：`obj` 为更新后的 `AccountResponse`。

**返回参数示例**：见“微博抓取账号响应模型”的单个账号成功响应示例，其中 `status` 为请求值、`recoverAt` 为 `null`。

### 6.15 删除微博抓取账号

**接口描述**：删除指定 ID 的抓取账号；不存在时返回 HTTP `404`。

**请求方式**：`DELETE /scout/weibo/accounts/{id}`

**必须请求头**：仅 `Authorization: Bearer <MANAGEMENT_TOKEN>`。

**请求参数**：`id`，path string，必填，表示抓取账号 ID。

**请求参数示例**：`DELETE /scout/weibo/accounts/account-1`

**返回参数**：统一响应字段；`obj` 为 `null`，`msg` 为“删除成功”。

**返回参数示例**：

```json
{
  "success": true,
  "code": 200,
  "msg": "删除成功",
  "obj": null
}
```

### 6.16 微博管理业务错误

| HTTP 状态 | code | msg | 触发条件 |
| --- | --- | --- | --- |
| 400 | 400 | `[字段]校验消息` | JSON 请求体字段校验失败 |
| 400 | 400 | `Cookie中缺少有效的XSRF-TOKEN` | 创建/更新账号时 Cookie 不合规 |
| 401 | 401 | `未授权` | 缺少 Bearer Token、格式错误或 Token 无效 |
| 403 | 403 | `权限不足` | Token 有效但缺少 `scout.weibo.manage` scope |
| 404 | 404 | `微博博主不存在` | 指定博主 UID 不存在 |
| 404 | 404 | `微博账号不存在` | 指定账号 ID 不存在 |
| 409 | 409 | `微博博主已存在` | 创建重复博主 UID |
| 409 | 409 | `微博账号已存在` | 创建重复账号 ID |

除鉴权拦截响应外，微博管理错误响应包含 `obj: null`。

## 7. WebSocket 接口

### 7.1 接口描述

WebSocket 用于服务端实时推送连接确认、黄历更新和微博更新。当前服务不定义客户端业务消息，也不提供历史消息补发、离线重放或逐客户端送达确认。

### 7.2 连接方式

**请求方式**：WebSocket Upgrade，路径 `/ws`。

以下两种鉴权方式任选其一：

1. 查询参数：`ws://<host>:9002/ws?token=<WS_TOKEN>`；
2. 请求头：`Authorization: Bearer <WS_TOKEN>`。

如果同时提供非空查询参数 `token` 和请求头，服务端优先使用查询参数，不会在查询参数无效时回退到请求头。

**必须请求头**：WebSocket 客户端自动产生的标准 Upgrade 请求头；业务鉴权头仅在不使用查询参数时必填。

| 请求头 | 必填条件 | 值 |
| --- | --- | --- |
| `Authorization` | 未使用 `token` 查询参数时必填 | `Bearer <WS_TOKEN>` |

Token 必须存在于 MongoDB 集合 `ws_auth_token`，且记录的 `status` 为 `1`。鉴权失败时握手返回 HTTP `401 Unauthorized`，不会建立 WebSocket 连接，也不会发送 JSON 错误事件。

### 7.3 推送事件总览

| type | 触发时机 | 推送方向 |
| --- | --- | --- |
| `connection.success` | WebSocket 握手成功后立即发送 | 服务端 → 当前连接 |
| `huangli.updated` | 黄历图片更新并持久化成功后 | 服务端 → 全部在线连接 |
| `weibo.updated` | 监控服务发现并确认需要广播的新微博后 | 服务端 → 全部在线连接 |

当前代码只生产以上三种消息类型。

### 7.4 `connection.success` 连接成功事件

**事件描述**：确认鉴权和握手成功。

**推送参数**：

| 字段 | JSON 类型 | 是否为空 | 格式/取值 | 描述 |
| --- | --- | --- | --- | --- |
| `type` | string | 否 | 固定 `connection.success` | 事件类型 |
| `message` | string | 否 | 固定 `连接成功` | 连接结果说明 |

**完整推送示例**：

```json
{
  "type": "connection.success",
  "message": "连接成功"
}
```

### 7.5 `huangli.updated` 黄历更新事件

**事件描述**：黄历图片上传、日期数据保存并发布领域事件后广播。

**推送参数**：

| 字段 | JSON 类型 | 是否为空 | 格式/取值 | 描述 |
| --- | --- | --- | --- | --- |
| `type` | string | 否 | 固定 `huangli.updated` | 事件类型 |
| `date` | string | 否 | `yyyy-MM-dd` | 黄历日期 |
| `url` | string | 取决于对象存储返回值 | URI 字符串 | 黄历图片访问地址 |

**完整推送示例**：

```json
{
  "type": "huangli.updated",
  "date": "2026-09-01",
  "url": "https://cdn.example.com/huangli/2026-09-01.png"
}
```

### 7.6 `weibo.updated` 微博更新事件

**事件描述**：推送最新微博内容。微博字段直接平铺在消息顶层，不存在额外的 `data` 或 `post` 包装。

**推送参数**：

| 字段 | JSON 类型 | 是否为空 | 格式/取值 | 描述 |
| --- | --- | --- | --- | --- |
| `type` | string | 否 | 固定 `weibo.updated` | 事件类型 |
| `uid` | string | 实际解析结果非空 | 微博数字 UID 字符串 | 博主 UID |
| `screenName` | string | 实际解析结果非 null，可为空字符串 | 普通文本 | 博主显示名称 |
| `weiboId` | string | 实际广播数据非空 | 微博 ID 字符串 | 微博唯一 ID |
| `publishedAt` | string | 实际解析结果非 null，可为空字符串 | 通常为 `yyyy-MM-dd HH:mm:ss`；无法解析上游时间时保留上游原值 | 微博发布时间 |
| `content` | string | 实际解析结果非 null，可为空字符串 | 移除 HTML 并保留段落、换行和有效空白；原创微博还会移除话题标记 | 可直接展示的微博纯文本正文 |
| `rawContent` | string 或 null | 是 | 微博上游返回的原始 HTML | 完整保留排版的原始正文；渲染前必须执行 HTML 白名单清理 |
| `source` | string 或 null | 是 | 已移除 HTML 的纯文本 | 发布来源 |
| `regionName` | string 或 null | 是 | 上游 `region_name` 原值去除首尾空白 | 发布地区 |
| `repostsCount` | integer(int64) 或 null | 是 | 上游整数或整数字符串 | 转发数 |
| `commentsCount` | integer(int64) 或 null | 是 | 上游整数或整数字符串 | 评论数 |
| `attitudesCount` | integer(int64) 或 null | 是 | 上游整数或整数字符串 | 点赞数 |
| `url` | string | 实际解析结果非 null，可为空字符串 | 通常为无查询参数的 URI | 微博详情地址 |
| `images` | array<string> | 否 | 空集合为 `[]` | 正文图片和视频封面地址；去重后输出 |
| `topics` | array<string> | 否 | 空集合为 `[]` | 微博话题，输出内容不带两侧 `#` |
| `videoCoverImages` | array<string> | 否 | 空集合为 `[]` | 卡片和视频封面地址 |
| `retweet` | object 或 null | 是 | 非转发微博为 `null` | 转发微博内容 |
| `retweet.screenName` | string | `retweet` 非空时非 null，可为空字符串 | 普通文本 | 原作者显示名称 |
| `retweet.content` | string | `retweet` 非空时非 null，可为空字符串 | 已清理 HTML | 转发微博正文 |
| `retweet.rawContent` | string 或 null | 是 | 上游原始 HTML | 完整保留排版的转发原文；渲染前必须执行 HTML 白名单清理 |
| `retweet.source` | string 或 null | 是 | 已移除 HTML 的纯文本 | 转发原文发布来源 |
| `retweet.regionName` | string 或 null | 是 | 上游原值去除首尾空白 | 转发原文发布地区 |
| `retweet.repostsCount` | integer(int64) 或 null | 是 | 上游整数或整数字符串 | 转发原文的转发数 |
| `retweet.commentsCount` | integer(int64) 或 null | 是 | 上游整数或整数字符串 | 转发原文的评论数 |
| `retweet.attitudesCount` | integer(int64) 或 null | 是 | 上游整数或整数字符串 | 转发原文的点赞数 |
| `retweet.images` | array<string> | `retweet` 非空时非 null | 空集合为 `[]` | 转发微博图片和视频封面地址；去重后输出 |
| `retweet.videoCoverImages` | array<string> | `retweet` 非空时非 null | 空集合为 `[]` | 转发微博视频封面地址 |

**完整推送示例（转发微博）**：

```json
{
  "type": "weibo.updated",
  "uid": "1761587065",
  "screenName": "剑网3官方微博",
  "weiboId": "5140000000000000",
  "publishedAt": "2026-09-01 12:01:00",
  "content": "微博正文",
  "rawContent": "<p>微博正文</p>",
  "source": "微博网页版",
  "regionName": "发布于 四川",
  "repostsCount": 12,
  "commentsCount": 34,
  "attitudesCount": 56,
  "url": "https://m.weibo.cn/detail/5140000000000000",
  "images": [
    "https://example.invalid/main.jpg",
    "https://example.invalid/video-cover.jpg"
  ],
  "topics": ["剑网3"],
  "videoCoverImages": ["https://example.invalid/video-cover.jpg"],
  "retweet": {
    "screenName": "原作者",
    "content": "原微博正文",
    "rawContent": "<p>原微博正文</p>",
    "source": "微博 iPhone客户端",
    "regionName": null,
    "repostsCount": 7,
    "commentsCount": 8,
    "attitudesCount": null,
    "images": [
      "https://example.invalid/origin.jpg",
      "https://example.invalid/origin-video-cover.jpg"
    ],
    "videoCoverImages": ["https://example.invalid/origin-video-cover.jpg"]
  }
}
```

**完整推送示例（非转发微博）**：

```json
{
  "type": "weibo.updated",
  "uid": "1761587065",
  "screenName": "剑网3官方微博",
  "weiboId": "5140000000000001",
  "publishedAt": "2026-09-01 12:05:00",
  "content": "另一条微博正文",
  "rawContent": "<p>另一条微博正文</p>",
  "source": null,
  "regionName": null,
  "repostsCount": null,
  "commentsCount": null,
  "attitudesCount": null,
  "url": "https://m.weibo.cn/detail/5140000000000001",
  "images": [],
  "topics": [],
  "videoCoverImages": [],
  "retweet": null
}
```

### 7.7 连接关闭与发送失败

- 客户端关闭连接后，服务端移除对应会话，不额外发送业务 JSON。
- 广播时发现连接已关闭，服务端直接移除会话。
- 发送发生 I/O 异常时，服务端尝试以 WebSocket `SERVER_ERROR` 状态关闭连接。
- 当前未定义 WebSocket 业务错误事件，也未定义客户端消息响应格式。

## 8. 实现差异与调用注意事项

| 项目 | 当前行为 |
| --- | --- |
| 普通错误响应 | `success` 可能仍为 `true`，应以 `code` 辅助判断 |
| 普通校验错误 | HTTP `200` + 业务 `code=101` |
| 微博管理错误 | 使用 HTTP `400/401/403/404/409`，且 `success=false` |
| 代理 `url` | 文档模型声明以 `/data` 开头，但代码当前未校验此前缀 |
| 代理 `params` | DTO 未标记必填，但实现直接写入该对象，因此实际必须传非 null 对象 |
| 成本列表子项 | 子项字段有校验声明，但列表当前缺少级联校验；调用方仍应按必填约束传值 |
| WebSocket 查询 Token | 非空查询 Token 优先于 `Authorization`，无效时不回退 |
| WebSocket 可靠性 | 实时尽力广播，无离线重放和逐客户端确认 |
