# 开放平台（open-platform）

面向企业开发者的 **API 开放平台 / 开发者门户**。企业登录控制台后可以浏览 API 文档、创建应用、获取调用凭证、申请 API 产品、等待平台审批，并按 OAuth2 或 HMAC 签名接入开放 API。

当前包含 **前端服务 + 后端服务**，是可直接运行、可演示完整接入闭环的 MVP。

- 文档：
  - [产品评估与 MVP 说明](docs/mvp-product.md)
  - [代码走读（新手先看）](docs/code-walkthrough.md)
  - [后端简要设计](docs/backend-design.md)
  - [内网网关 Token Filter 设计](docs/internal-token-filter.md)
  - [交接文档](HANDOFF.md)

## 1. 两个服务

| 服务 | 技术栈 | 地址 | 作用 |
|:---|:---|:---|:---|
| 后端 | Java 17 + Spring Boot 3.5.7 | `http://127.0.0.1:8081` | 门户 API、开放 API、认证/授权/审计、演示数据 |
| 前端 | React 18 + TypeScript + Vite | `http://127.0.0.1:5173` | 开发者/运营控制台 |

前端开发服务器会把 `/portal/api/**` 代理到后端 8081，所以本地不需要处理跨域。

## 2. 五分钟启动

环境要求：

- JDK 17
- Maven 3.9+
- Node.js 18+（当前验证版本 23）
- npm 9+

### 2.1 启动后端

```bash
cd open-platform
mvn -DskipTests package
java -jar target/open-platform.jar
```

看到以下日志说明成功：

```text
开放平台启动成功  profile=default  port=8081
门户 API   http://127.0.0.1:8081/portal/api
门户前端   http://localhost:5173（另开终端 cd frontend && npm run dev）
```

### 2.2 启动前端

另开一个终端：

```bash
cd open-platform/frontend
npm install
npm run dev
```

打开 <http://localhost:5173>。

> 也可以用 IDEA 分别运行后端 `OpenPlatformApplication` 和前端 npm 配置。

### 2.3 演示账号

| 账号 | 密码 | 角色 | 能做什么 |
|:---|:---|:---|:---|
| `dev@demo.com` | `Demo@1234` | 企业开发者 | 浏览目录、创建应用、申请产品、看统计 |
| `admin@demo.com` | `Admin@1234` | 平台运营 | 查看全部申请、审批通过/驳回 |

演示数据每次重启后端会重置。

## 3. 五分钟演示闭环

1. 用 **开发者** 登录，进入“概览”，看到 2 个应用、1 个已开通产品、1 条待处理申请；
2. 进入“API 目录”，打开“企业风险核验”详情，查看参数、示例、错误码；
3. 在“我的应用”创建一个应用（选择 OAuth2），保存弹出的 AppSecret；
4. 在“接入申请”选择刚创建的应用 + “企业风险核验”产品，填写场景并提交；
5. 退出，用 **平台运营** 登录，在“审批中心”通过该申请（通过后系统自动创建订阅并授予 `enterprise.risk`）；
6. 回到开发者账号，用新应用换 `access_token`，调用 `/open/v1/enterprise/risk-check`；
7. 在“调用统计”看到刚才的调用记录。

自动化测试 `PortalFlowTest` 覆盖了同样的闭环。

## 4. MVP 功能

- 登录/退出、开发者/运营两种角色；
- 概览：应用数、已开通产品、近 7 日调用、待处理申请、接入引导、公告、最近调用；
- API 目录：搜索、方法筛选、产品筛选；
- API 详情：说明、认证方式、限流、请求头、参数、请求/响应示例、错误码、相关接口；
- 应用管理：创建应用、OAuth2/HMAC 选择、查看/复制/轮换密钥；
- 接入申请：按“应用 + API 产品”提交，查看审批进度；
- 审批中心：管理员通过/驳回，通过后自动开通产品；
- 调用统计：总量、成功率、平均耗时、按天趋势、按 API 排行、最近调用；
- 接入指南：OAuth2 与 HMAC 签名步骤、代码示例、错误码；
- 开放 API 运行时：OAuth2 JWT、HMAC 签名、防重放、企业级授权、审计。

### 不在 MVP 内

企业自助注册/KYC、合同/计费、工单评论、SSO、生产级 MySQL/Redis、真实网关联调、在线任意请求调试。详见 [docs/mvp-product.md](docs/mvp-product.md)。

## 5. 架构与请求链路

```text
┌──────────────────────────────┐
│ frontend (Vite + React)      │
│ localhost:5173               │
│  登录/概览/目录/详情/应用     │
│  申请/审批/统计/指南          │
└──────────────┬───────────────┘
               │ /portal/api/**（Vite proxy）
               ▼
┌──────────────────────────────────────────────────────┐
│ backend (Spring Boot, 8081)                           │
│                                                      │
│ /portal/api/**  → PortalAuthFilter → PortalController │
│                   PortalStore / PortalService         │
│                                                      │
│ /open/**        → TraceId → 缓存 → OAuth/HMAC          │
│                   → API 映射 → 企业授权 → Controller    │
│                   → AuditFilter → UsageTracker         │
└──────────────────────────────────────────────────────┘
```

### 门户 API

除 `POST /portal/api/auth/login` 外，全部需要：

```text
Authorization: Bearer <portal-token>
```

| 方法 | 路径 | 说明 |
|:---|:---|:---|
| POST | `/portal/api/auth/login` | 登录 |
| GET | `/portal/api/auth/me` | 当前用户 |
| POST | `/portal/api/auth/logout` | 退出（前端清 token） |
| GET | `/portal/api/dashboard` | 概览 |
| GET | `/portal/api/apis` | API 目录，支持 `keyword/product/method` |
| GET | `/portal/api/apis/{apiId}` | API 文档详情 |
| GET | `/portal/api/products` | API 产品及开通情况 |
| GET | `/portal/api/apps` | 应用列表 |
| POST | `/portal/api/apps` | 创建应用 |
| GET | `/portal/api/apps/{appId}/credentials` | 查看密钥 |
| POST | `/portal/api/apps/{appId}/rotate-secret` | 轮换密钥 |
| GET | `/portal/api/access-requests` | 申请列表（开发者本企业/管理员全部） |
| POST | `/portal/api/access-requests` | 提交接入申请 |
| POST | `/portal/api/access-requests/{id}/decision` | 审批（仅管理员） |
| GET | `/portal/api/usage?days=7` | 调用统计 |

### 开放 API

| 方法 | 路径 | 所需 scope | 产品 |
|:---|:---|:---|:---|
| POST | `/open/v1/enterprise/verify` | `enterprise.read` | enterprise-basic |
| GET | `/open/v1/enterprise/basic` | `enterprise.read` | enterprise-basic |
| POST | `/open/v1/enterprise/risk-check` | `enterprise.risk` | enterprise-risk |

认证方式二选一：

- **OAuth2**：`POST /open/oauth2/token`（client_credentials）→ `Authorization: Bearer <token>`；
- **HMAC 签名**：`X-App-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature-Method`、`X-Signature`、`Content-SHA256`。

详细签名规则见 [docs/backend-design.md](docs/backend-design.md) 第 3.2 节，示例见 [scripts/demo-signature.py](scripts/demo-signature.py)。

## 6. 目录结构

```text
open-platform/
├── frontend/                        # 前端控制台（Vite + React + TS）
│   ├── src/
│   │   ├── components/              # 布局、图标、弹窗、Toast、通用 UI
│   │   ├── pages/                   # 登录/概览/目录/详情/应用/申请/统计/指南
│   │   ├── api.ts                   # 请求封装、401 处理、格式化
│   │   ├── auth.tsx                 # 登录态
│   │   ├── hooks.ts                 # useApi
│   │   └── styles.css               # 设计系统与响应式
│   └── vite.config.ts               # 5173 + /portal/api 代理
│
├── src/main/java/com/crcit/openplatform/
│   ├── auth/                        # 开放 API 认证（OAuth2/签名）
│   ├── authorization/               # API 映射、企业级授权
│   ├── common/                      # 统一响应、异常、TraceId、请求体缓存
│   ├── config/                      # 配置、启动日志、CORS
│   ├── enterprise/                  # 企业/应用/API 产品/订阅
│   ├── internal/                    # 内部令牌、网关客户端
│   ├── oauth/                       # 开放 API JWT 与 token 端点
│   ├── portal/                      # 门户：用户/应用/目录/申请/统计/认证
│   ├── replay/                      # jti / nonce 防重放
│   ├── signature/                   # HMAC-SHA256
│   └── web/                         # 开放 API 示例接口
└── docs/
```

## 7. 在 IDEA 中启动

### 后端

1. `File → Open` 选中 `open-platform/pom.xml`，以项目方式打开；
2. `Project Structure → Project`，SDK 和 Language level 都选 **17**；
3. Maven 面板点击 **Reload All Maven Projects**；
4. 运行共享配置 **OpenPlatformApplication**（[.run/OpenPlatformApplication.run.xml](.run/OpenPlatformApplication.run.xml)），或直接运行 `OpenPlatformApplication.java`。

### 前端

1. 在 IDEA 里打开 `frontend` 目录；
2. 终端执行 `npm install`、`npm run dev`；
3. 浏览器打开 <http://localhost:5173>。

### 常见问题

| 现象 | 处理 |
|:---|:---|
| 代码报红 / `jakarta.validation` 不存在 | IDEA 的 Maven 本地仓库不完整；执行 `mvn -DskipTests package` 后 Reload |
| Maven 导入报 `CannotStartServerException` / exit 137 | Maven Runner 改 JDK 17，importer VM options 调大（如 `-Xmx1024m`），重启 IDEA |
| 8081 端口被占用 | `lsof -nP -iTCP:8081 -sTCP:LISTEN`，结束旧进程，或 `SERVER_PORT=8082` |
| 5173 端口被占用 | `npm run dev -- --port 5174`，同时在 `vite.config.ts` 调整代理 |
| 前端页面能开但接口 502 | 后端未启动或不是 8081；检查 `vite.config.ts` 的 proxy target |
| 重启后旧 token 401 | 骨架阶段 RSA 密钥每次启动重新生成，属预期 |
| 审批通过后仍 403 | 需要用“开通后新换的 token”；旧 token 的 scope 不会自动更新 |

## 8. 测试

```bash
# 后端：9 个测试（含门户完整闭环）
mvn -o -B test

# 前端：类型检查 + 生产构建
cd frontend
npm run build
```

测试覆盖：

- `OpenPlatformApplicationTests`：Spring 上下文启动；
- `OpenPlatformFlowTest`：OAuth2 / HMAC / 参数校验 / nonce 重放；
- `PortalFlowTest`：门户登录、目录、创建应用、提交申请、管理员审批、换 token、调用刚开通的 API。

## 9. 关键实现说明

### 门户认证与开放 API 认证隔离

- 门户 token：独立 RSA 密钥、`iss=open-platform-portal`、`aud=portal-console`、8 小时；
- 开放 API token：`iss=open-platform`、`aud=open-api`、15 分钟；
- 两套 token 不能互相使用。

### 密钥与密码

- 门户密码：PBKDF2WithHmacSHA256 + 独立随机盐；
- 应用 secret：创建/轮换时完整展示一次，列表只展示掩码；
- 日志不打印 token、secret、私钥；信用代码脱敏。

### 授权模型

```text
企业 → 应用 → API 产品 → scope → 具体 API
```

审批通过 = 为应用创建产品订阅 + 更新应用 `productCodes`；开放 API 请求时同时校验企业/应用/订阅/scope。

### 用量统计

`AuditFilter` 在每次已认证的开放 API 调用结束后把应用、API、状态、耗时写入
`UsageTracker`（内存，最多 5000 条），门户按企业聚合展示。重启后清零。

## 10. 从 MVP 到生产

- 内存 → MySQL/MyBatis（`AppRegistry`、`PortalStore` 拆表）；
- 防重放/用量 → Redis；
- RSA 临时密钥 → KMS/PEM + `kid` 轮换；
- 应用 secret → 加密存储 + 定期轮换；
- 企业注册/KYC、成员管理、SSO、审批通知、工单；
- 接真实 `crcit-gateway`，落地内部 Token Filter；
- 网关/接口限流、配额、计费；
- 生产前端由 Nginx 托管 `frontend/dist`，`/portal/api` 反向代理到后端。

## 11. 安全提示

本项目是演示型 MVP，**不要直接用于生产**：

- 演示账号密码、演示应用 secret 写在代码/文档中；
- 数据在内存，重启重置；
- 没有登录限流、验证码、MFA、审计留痕归档；
- 门户“查看密钥”接口没有二次认证；
- 没有 API 版本生命周期、配额和计费。

生产前请至少完成第 10 节的持久化、密钥管理、限流、审计与合规改造。
