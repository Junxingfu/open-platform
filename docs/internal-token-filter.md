# 内网网关 Token Filter 设计

> 版本 V0.2 · 设计阶段 · 2026-10-22  
> 本文只描述设计，不修改 `crcit-gateway` 现有代码。

## 1. 目标

开放平台调用 `crcit-gateway` 时，不再使用外部应用凭证，也不依赖网关的用户 token 认证，而是使用平台自签的**内部短时效 JWT**。网关新增一个 `InternalTokenAuth` Filter 做校验。

**解决**

- 开放平台以独立内部身份访问网关。
- 外部 token 不进入网关。
- 内部 token 短时效、可撤销、按 API 授权。
- 不复用 `ApiAuth` 的静态 `appId/appSecret`，避免长期密钥。

**不解决**

- 外部企业鉴权（由开放平台负责）。
- 企业级授权（由开放平台负责）。
- mTLS、IPsec、网络隔离（部署/运维范畴）。

## 2. 在网关中的位置

现有过滤器顺序（`FilterOrderConstant`）：

- `TRACE_ID = 1`
- `LOGGING = 2`
- `PARAM_VERIFY = 4`
- `HEADER_EXCHANGE = 9`
- `TOKEN_AUTH = 10`
- `API_AUTH = 11`
- `RATE_LIMIT = 19`

新增：

- `INTERNAL_TOKEN_AUTH = 5`

放在 `PARAM_VERIFY` 之后、`HEADER_EXCHANGE`/`TOKEN_AUTH` 之前。开放平台专用路由只配置 `InternalTokenAuth` 和 `RateLimit`，**不要同时配置** `TokenAuth` 或 `ApiAuth`，避免两套认证互相冲突。

## 3. Filter 设计

建议类名：`InternalTokenAuthGatewayFilterFactory`

- 包：`com.crcit.gateway.filter`
- 继承：`AbstractGatewayFilterFactory<Config>`
- 注解：`@Component`、`@Order(FilterOrderConstant.INTERNAL_TOKEN_AUTH)`
- 返回：`GatewayFilter`，在 `apply` 中做响应式校验，不阻塞事件循环。

### 3.1 配置项

| 配置 | 说明 |
|:---|:---|
| `issuer` | 必须等于 `open-platform` |
| `audience` | 必须等于 `internal-gateway` |
| `publicKey` | RSA 公钥（RS256，首版默认） |
| `kid` | 可选，密钥轮换时使用 |
| `requiredScope` | 默认 `api:invoke` |
| `routePermission` | 当前路由对应的 API 权限标识，如 `enterprise.verify` |
| `allowedSourceCidrs` | 允许调用网关的开放平台来源网段 |
| `clockSkewSeconds` | 允许时钟偏差，默认 60 秒 |

配置来源建议走 Nacos，和现有路由配置一起下发；公钥/密钥不放在代码仓库。

### 3.2 请求头

| 请求头 | 必填 | 说明 |
|:---|:---|:---|
| `X-Internal-Token` | 是 | 平台签发的内部 JWT |
| `X-Open-Request-Id` | 是 | 外部请求号，用于审计关联 |
| `X-Open-App-Id` | 是 | 外部应用标识，仅审计 |
| `X-Open-Api-Id` | 是 | API 标识，与 token/route 比对 |
| `X-Open-Trace-Id` | 是 | 全链路追踪号 |

`X-Open-App-Id` 只用于审计，**不能**作为授权依据。

### 3.3 校验顺序

1. 来源 IP 是否在 `allowedSourceCidrs` 内。
2. `X-Internal-Token` 是否存在、格式是否为 JWT。
3. 按 `kid` 选择公钥/密钥，验证签名。
4. 校验 `iss`、`aud`、`exp`、`iat`（允许 `clockSkewSeconds`）。
5. 可选：校验 `jti` 是否在 Redis 黑名单；严格一次性时用 `SETNX`。
6. 校验 `scope` 是否包含 `requiredScope`。
7. 校验 token 的 `api_id` 与 `routePermission`、`X-Open-Api-Id` 三者一致。
8. 校验路由是否在开放平台可调用白名单内。
9. 将 `app_id`、`api_id`、`jti`、`trace_id` 写入 exchange attributes，供审计和日志使用。

任一步失败返回 401/403，响应体复用网关 `BaseResponse`，不返回具体失败原因。

### 3.4 内部 token 的 claims

| claim | 值 |
|:---|:---|
| `iss` | `open-platform` |
| `sub` | `open-platform-service` |
| `aud` | `internal-gateway` |
| `exp` | 5 分钟后 |
| `iat` | 签发时间 |
| `jti` | 唯一 ID，可撤销 |
| `scope` | `api:invoke` |
| `api_id` | 目标 API 标识 |
| `ent_id` | 企业标识，仅审计 |

内部 token 不包含外部应用的 secret，也不包含用户身份。

## 4. 路由配置示例

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: open-enterprise-verify
          uri: lb://enterprise-service
          predicates:
            - Path=/internal/open/enterprise/verify
          filters:
            - InternalTokenAuth=issuer=open-platform,audience=internal-gateway,requiredScope=api:invoke,routePermission=enterprise.verify
            - RateLimit=resourceName=open-enterprise-verify
```

说明：

- `InternalTokenAuth` 是本设计新增的 GatewayFilterFactory。
- 该路由不再配置 `TokenAuth`/`ApiAuth`。
- 路由仍由 Nacos 下发，开放平台只按固定 route 调用，不能指定目标 URL。

## 5. 与现有 Filter 的关系

| 现有能力 | 是否复用 | 说明 |
|:---|:---|:---|
| `TraceIdGlobalFilter` | 复用 | 内部调用继续带 traceId |
| `TokenAuthGatewayFilterFactory` | 不复用 | 它面向平台用户 token，不适用于开放平台内部身份 |
| `ApiAuthGatewayFilterFactory` | 不复用 | 它使用静态 `appId/appSecret`，长期密钥不适合内部调用 |
| `ValidationGatewayFilterFactory` | 可复用 | 只做 `_t` 时间戳，不能替代开放平台防重放 |
| `RateLimitGatewayFilterFactory` | 复用 | 对内调用继续走 Sentinel 限流 |
| `HeaderExchangeGatewayFilterFactory` | 不复用 | 开放平台路由不需要用户 token 转换 |

## 6. 密钥与轮换

- **首版使用 RS256**：JWT header `alg=RS256`，平台保管私钥，网关只保存 RSA 公钥。这是业内最通用的服务间 JWT 方案。
- SM2/SM3 作为后续信创可选实现，通过 `TokenSigner` 接口切换，不改变 Filter 的校验流程。
- HS256 不作为长期方案，避免网关与平台共享签名密钥。
- JWT header 带 `kid`；网关维护 `kid -> 公钥` 映射，支持新旧密钥并存。
- 轮换分三步：新公钥发布并双验 → 平台切新私钥 → 旧公钥退役。
- 疑似泄露：立即移除旧公钥，平台换新私钥，网关拒绝旧 `kid`。

## 7. 错误与审计

- 401：无 token、签名失败、过期、iss/aud 不匹配。
- 403：scope 不足、api_id 不匹配、来源 IP 不在白名单、路由未授权。
- 429：网关限流。
- 审计：记录 `trace_id`、`app_id`、`api_id`、`jti`、结果、耗时；不记录 token 原文。

## 8. 测试用例

1. 合法 token + 正确 api_id + 合法来源 IP：放行。
2. token 过期：401。
3. `aud` 不是 `internal-gateway`：401。
4. `scope` 不含 `api:invoke`：403。
5. `api_id` 与路由不一致：403。
6. 来源 IP 不在白名单：403。
7. 带外部 `Authorization` 头：拒绝或剥离，不能透传。
8. `jti` 重放（开启严格一次性时）：401。
9. 密钥轮换：旧 `kid` 仍可验，退役后拒绝。
10. 路由未配置 `InternalTokenAuth`：默认拒绝开放平台调用。

## 9. 设计结论

- 新增 `InternalTokenAuth`，不改现有 `TokenAuth`/`ApiAuth` 的行为。
- 开放平台路由独立配置，只使用内部 token。
- 内部 token 5 分钟、按 API 授权、可撤销；网关不依赖静态 appSecret。
- 本期只交付设计，评审通过后再实现 Filter 与路由配置。
