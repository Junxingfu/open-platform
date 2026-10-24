# HANDOFF — 开放平台门户 MVP（前端 + 后端）

> **给下一个会话的开场白（请直接复制发送）：**
>
> **先读 HANDOFF.md，再继续这个任务。**
>
> 本文档最后复核时间：**2026-10-24 17:05（Asia/Shanghai）**
> 工作目录：`/Users/fujunxing/IdeaProjects/myproject/open-platform`

---

## 1. 当前任务

用户把项目类比为“银行开放平台”：

> 企业拿到开放平台账号 → 登录控制台 → 看接口文档/接口信息/接入方式 → 协商接入 → 正常调用服务。
> 因此 `/Users/fujunxing/IdeaProjects/myproject/open-platform` 下应该有一个前端服务和一个后端服务。
>
> 要求：从研发和高级产品经理角度评估需求，并实现 MVP。

已在当前目录完成 **前端 + 后端 MVP**，包含完整的“创建应用 → 申请 API 产品 → 管理员审批 → 自动开通 → 换 token → 调用 API → 查看统计”闭环。

产品评估详见 [`docs/mvp-product.md`](docs/mvp-product.md)。

---

## 2. 当前状态（一句话）

**MVP 已实现并验证：后端 9 个测试全绿，前端 `npm run build` 通过，前后端同时启动后经 Vite 代理实测登录/目录/产品/概览接口正常；没有代码层面的阻塞。**

尚未由我完成：

1. 在浏览器里对前端做最终视觉验收（我无法看到浏览器截图，只验证了构建、HTML 和接口）；
2. 真实企业注册/KYC、MySQL/Redis、真实网关、计费等生产化能力（明确不在 MVP 内）；
3. Git 提交（仓库已初始化，文件已 staged，但还没有 commit）。

---

## 3. 已完成内容

### 3.1 产品评估

新增 [`docs/mvp-product.md`](docs/mvp-product.md)，包含：

- 需求理解：API 开发者门户（Developer Portal）；
- 用户角色：企业开发者、企业技术负责人、平台运营、商务（后续）；
- 核心 Job：发现 → 评估 → 创建应用 → 申请产品 → 审批 → 接入 → 运营；
- MVP 范围 / 明确不做；
- 关键产品决策（API 产品为授权单元、申请必须审批、门户 token 与开放 API token 隔离等）；
- 成功指标、技术方案、风险、路线图、验收标准。

### 3.2 后端

新增 `com.crcit.openplatform.portal` 包：

| 类 | 作用 |
|:---|:---|
| `PortalUser` / `PortalRole` | 门户用户、开发者/管理员角色 |
| `PortalStore` | 内存用户、应用展示信息、接入申请、演示数据 |
| `PasswordHasher` | PBKDF2WithHmacSHA256 + 随机盐 |
| `PortalPrincipal` | 登录人上下文 |
| `PortalTokenService` | 门户 JWT（独立 RSA、iss/aud、8h） |
| `PortalAuthFilter` | `/portal/api/**` 登录校验（Order 15） |
| `PortalAuthController` | 登录 / me / logout |
| `PortalDashboardController` + `PortalDashboardService` | 概览统计、引导、公告 |
| `PortalCatalogController` + `PortalCatalogService` | API 目录/详情/产品 + 内置文档 |
| `PortalAppController` + `PortalAppService` | 创建应用、查看/轮换密钥 |
| `PortalAccessController` + `PortalAccessService` | 提交申请、管理员审批、开通产品 |
| `PortalUsageController` + `PortalUsageService` | 调用统计 |
| `UsageTracker` | `AuditFilter` 写入的调用记录（内存 5000 条） |
| `PortalCorsConfig` | 开发期 CORS |
| `PortalViews` / `PortalRequests` | DTO |

同时修改：

- `AppRegistry` 增加 `listProducts()/listApis()`；
- 新增 `MutableAppRegistry`：`registerApp/updateSecret/grantProduct`；
- `InMemoryAppRegistry` 增加产品 `enterprise-risk`、API `enterprise.basic` / `enterprise.risk-check`，实现可写方法；
- `EnterpriseController` 增加 `GET /open/v1/enterprise/basic`、`POST /open/v1/enterprise/risk-check`；
- `PublicPaths` 增加 `isPortal()`，开放 API 的所有过滤器跳过 `/portal/**`；
- `FilterOrder` 增加 `PORTAL_AUTH=15`；
- `AuditFilter` 接入 `UsageTracker`；
- `OpenPlatformProperties` 增加 `portal.token-ttl` / `portal.allowed-origins`；
- `StartupInfoLogger` 打印门户地址和演示账号。

### 3.3 前端

新增 `frontend/`（Vite + React 18 + TypeScript + React Router + 手写设计系统）：

| 页面 | 文件 | 功能 |
|:---|:---|:---|
| 登录 | `LoginPage.tsx` | 演示账号快速填充、错误提示 |
| 概览 | `DashboardPage.tsx` | 统计卡、三步引导、公告、最近调用、管理员待审批提醒 |
| API 目录 | `CatalogPage.tsx` | 搜索、方法/产品筛选、卡片 |
| API 详情 | `ApiDetailPage.tsx` | 文档、请求头/参数、示例、错误码、申请接入弹窗 |
| 应用 | `AppsPage.tsx` | 创建应用、查看/复制/轮换密钥、产品标签 |
| 申请/审批 | `RequestsPage.tsx` | 开发者提交、管理员通过/驳回 |
| 统计 | `UsagePage.tsx` | 趋势图、API 排行、最近调用 |
| 指南 | `GuidePage.tsx` | OAuth2/HMAC 步骤、代码示例、错误码 |

组件/基础：`Layout`、`Icon`、`Modal`、`Toast`、`Ui`、`auth.tsx`、`api.ts`、`hooks.ts`、`styles.css`。

特点：

- 深色侧边栏 + 浅色内容区，响应式（1024/960/640 断点）；
- 无重型 UI 库，构建产物约 227 KB JS / 37 KB CSS；
- Vite 代理 `/portal/api` → `http://127.0.0.1:8081`；
- 401 自动清理 token 并回到登录；
- 密钥只在创建/轮换时完整展示，列表掩码。

### 3.4 测试

新增 `PortalFlowTest`，覆盖：

- 未登录访问门户 API → 401；
- 开发者登录、目录（3 个 API）、详情、产品；
- 创建应用 → 提交 `enterprise-risk` 申请；
- 管理员登录 → 审批通过（审批人“李静”）；
- 用新应用换 OAuth2 token → 调用 `/open/v1/enterprise/risk-check` → `riskLevel=LOW`。

**当前测试总数：9 个（1 上下文 + 5 开放 API + 3 门户），全部通过。**

### 3.5 文档

- `README.md` 重写：两个服务、5 分钟启动、演示闭环、门户/开放 API 接口表、目录、IDEA、常见问题、测试、生产化清单、安全提示；
- `docs/mvp-product.md`：产品评估；
- `docs/code-walkthrough.md` 增加第 8 节“门户代码导览”；
- `HANDOFF.md`（本文）。

---

## 4. 验证记录

### 4.1 后端测试

```bash
cd /Users/fujunxing/IdeaProjects/myproject/open-platform
mvn -o -B test
```

结果：

```text
OpenPlatformApplicationTests：1 个通过
OpenPlatformFlowTest：      5 个通过
PortalFlowTest：            3 个通过
合计：Tests run: 9, Failures: 0, Errors: 0
BUILD SUCCESS
```

### 4.2 前端构建

```bash
cd frontend
npm run build
```

结果：

```text
✓ 51 modules transformed
dist/index.html                   0.45 kB
dist/assets/index-*.css          37.22 kB
dist/assets/index-*.js          226.60 kB
✓ built
```

### 4.3 前后端联调

同时启动：

```bash
java -jar target/open-platform.jar
cd frontend && npm run dev
```

验证结果：

| 检查 | 结果 |
|:---|:---|
| `http://127.0.0.1:5173/` | 返回 Vite HTML |
| 经 Vite 代理登录 `dev@demo.com` | `code=0`，张明 / DEVELOPER |
| `GET /portal/api/dashboard` | `appCount=2, approvedProductCount=1, pendingRequestCount=1, apiCount=3` |
| `GET /portal/api/apis` | `enterprise.verify:POST`、`enterprise.basic:GET`、`enterprise.risk-check:POST` |
| `GET /portal/api/products` | `enterprise-basic` 已开通 2 个应用；`enterprise-risk` 待审批 1 条 |
| 8081 / 5173 | 验证后已释放 |

另外用本机 Chrome headless 做了页面渲染验证：

- 登录页 DOM 正常渲染“登录开发者控制台/演示账号/企业开发者”；
- 通过临时 seed 页面注入 token 后，dashboard DOM 正常渲染“概览/下午好/我的应用/API 目录/平台公告/调用统计”，并成功请求 `/portal/api/dashboard`；
- 临时 seed 页面已删除，未留在仓库中。

---

## 5. 当前卡住 / 未闭环的问题

### 5.1 没有代码阻塞

后端测试、前端构建、代理联调均通过。

### 5.2 前端视觉/交互人工验收

没有在真实浏览器里逐页点击验证（尤其是弹窗、移动端布局、图表）。下一个会话如果继续，建议：

1. 启动前后端；
2. 用两个演示账号完整走一遍第 3 节演示闭环；
3. 用浏览器 DevTools 看 Console/Network 是否有报错；
4. 检查 375px / 768px / 1440px 三档布局。

### 5.3 Git 尚未提交

仓库已初始化，当前 `git status` 显示大量文件已 staged（`A`），新文件为 `??`，还没有 commit。下一步需要确认是否提交、提交信息、是否保留 `.idea`/`.m2` 忽略规则。

### 5.4 MVP 的天然限制

- 数据全在内存，重启重置；
- 门户 RSA 密钥启动时临时生成，重启后需重新登录；
- 应用 secret 明文存储在内存；
- 审批通过后，**旧 token 不会自动获得新 scope**，必须重新换 token；
- 用量统计只在当前进程、最多 5000 条；
- 未接真实网关、MySQL、Redis；
- 没有登录限流、验证码、MFA、审计归档；
- 管理员“查看全部应用/申请”是简化处理，没有组织/成员模型。

---

## 6. 踩过的坑（下一个会话务必知道）

1. **门户路径不能被开放 API 过滤器拦截**
   - 开放 API 的 `AuthModeResolverFilter` / `OAuthTokenFilter` / `SignatureAuthFilter` / `ApiMappingFilter` / `AuthorizationFilter` 都要跳过 `/portal/**`；
   - 已在 `PublicPaths.isPortal()` + 各 `shouldNotFilter` 中处理；
   - 新增门户路径时，不要把它加进 `PUBLIC_PATHS`（那是开放 API 的公开路径）。

2. **门户 token 与开放 API token 必须隔离**
   - 两套 RSA、issuer、audience 都不同；
   - 不要复用 `JwtService` 给门户签发，否则受众/权限容易混。

3. **审批通过后旧 token 不会自动变权限**
   - `grantProduct` 更新的是应用的产品和订阅；
   - 旧 OAuth2 token 的 scope 是签发时写死的，必须重新换 token；
   - 演示/测试时如果 403，先检查是不是拿了旧 token。

4. **创建应用初始不允许调用**
   - `registerApp` 的 `productCodes` 为空；
   - 必须走“提交申请 → 管理员审批 → grantProduct”；
   - 不要为了省事在创建应用时直接给产品，否则审批流程形同虚设。

5. **表单请求不能提前缓存 body**
   - `RequestCachingFilter` 仍跳过 `application/x-www-form-urlencoded` 和 `multipart/`；
   - 门户登录/创建应用都是 JSON，没问题；
   - 签名模式仍约定 JSON body。

6. **门户登录接口是唯一公开的门户 API**
   - `PortalAuthFilter.shouldNotFilter` 精确放行 `/portal/api/auth/login` 和 OPTIONS；
   - 新增公开门户接口必须显式放行。

7. **Vite 代理端口**
   - 前端 5173，后端 8081；
   - 改后端端口时同步改 `frontend/vite.config.ts` 的 proxy target；
   - 生产部署应使用 Nginx `/portal/api` 反代，不要依赖 Vite。

8. **沙箱内 `npm install` 可能 EPERM**
   - 本机 `~/.npm` 写过权限受限；本次安装使用了：
     ```bash
     cd frontend
     npm install --cache /tmp/npm-cache-op --no-audit --no-fund
     ```
   - 正常开发机直接 `npm install` 即可。

9. **`AppView` 加过字段**
   - 后端 `PortalViews.AppView` 增加了 `enterpriseId/enterpriseName`；
   - 前端 `types.ts` 已同步；
   - 修改 DTO 时前后端一起改，否则 TS 或页面取值为空。

10. **React 类型小坑**
    - `React.FormEvent` / `React.CSSProperties` 依赖全局 React 命名空间，本次编译通过；
    - `StatCard` 的 `icon` 已改为 `IconName`，新增图标时同步 `Icon.tsx` 的 paths。

11. **RSA 临时密钥**
    - 门户和开放 API 都是启动时生成；
    - 重启后旧 token 全失效，属预期，不是 bug。

12. **端口占用**
    - `lsof -nP -iTCP:8081 -sTCP:LISTEN`
    - `lsof -nP -iTCP:5173 -sTCP:LISTEN`

---

## 7. 下一步计划

### P0：浏览器人工验收

- 启动后端 + 前端；
- 开发者/管理员各走一遍；
- 检查创建应用、复制密钥、提交申请、审批、统计、移动端；
- 记录 Console/Network 报错。

### P1：纳入 Git

- `git status` 查看 staged/untracked；
- 确认 `.gitignore` 是否覆盖 `frontend/node_modules`、`frontend/dist`、`target`、`.m2`、`.idea`；
- 提交 MVP（建议信息：`feat: add developer portal MVP with frontend and backend`）；
- 不要提交 `node_modules`、`dist`、`target`、项目内 `.m2`、`.idea/workspace.xml`。

### P1：持久化与真实接入

- `PortalStore` / `InMemoryAppRegistry` → MySQL/MyBatis；
- `InMemoryReplayGuard` / 用量 → Redis；
- 应用 secret 加密存储；
- 门户 RSA → KMS/PEM + kid；
- 接真实 `crcit-gateway`，落地 `InternalTokenAuth` Filter。

### P2：产品增强

- 企业注册/KYC、成员/角色、SSO；
- 工单/评论/审批通知；
- 在线调试（沙箱凭证 + 服务端代理）；
- API 版本生命周期、公告、下线；
- 配额、限流、计费、发票；
- 登录限流/验证码/MFA；
- 前端自动化测试（Playwright）和视觉回归。

---

## 8. 下一个会话必须知道的信息

### 8.1 路径与工具链

| 项 | 值 |
|:---|:---|
| 工作目录 | `/Users/fujunxing/IdeaProjects/myproject/open-platform` |
| 后端 | Spring Boot 3.5.7 / Java 17 / Maven 3.9.9 |
| 前端 | React 18 / TypeScript / Vite 5 / Node 23 |
| 后端端口 | 8081（`SERVER_PORT` 可覆盖） |
| 前端端口 | 5173（Vite） |
| Maven 仓库 | `~/.m2/repository`（正常） |
| 前端依赖 | `frontend/node_modules`（已安装，69 MB，已 gitignore） |
| 构建产物 | `target/open-platform.jar`、`frontend/dist` |
| Git | 已初始化，未 commit；大量文件 staged |

### 8.2 常用命令

```bash
cd /Users/fujunxing/IdeaProjects/myproject/open-platform

# 后端
mvn -o -B test
mvn -o -DskipTests package
java -jar target/open-platform.jar

# 前端
cd frontend
npm install                 # 沙箱：--cache /tmp/npm-cache-op
npm run dev                 # http://localhost:5173
npm run build               # 产物 dist/

# 端口
lsof -nP -iTCP:8081 -sTCP:LISTEN
lsof -nP -iTCP:5173 -sTCP:LISTEN
```

### 8.3 演示账号

| 账号 | 密码 | 角色 |
|:---|:---|:---|
| `dev@demo.com` | `Demo@1234` | 企业开发者（ent-demo） |
| `admin@demo.com` | `Admin@1234` | 平台运营（ent-platform） |

### 8.4 演示数据

- 应用：`app-oauth/oauth-secret`（OAuth2）、`app-sign/sign-secret`（HMAC），均已开通 `enterprise-basic`；
- 产品：`enterprise-basic`（verify/basic，scope `enterprise.read`）、`enterprise-risk`（risk-check，scope `enterprise.risk`）；
- 预置 1 条 `app-sign` 申请 `enterprise-risk` 的待审批申请；
- API 目录 3 个接口。

### 8.5 关键代码入口

| 文件 | 作用 |
|:---|:---|
| `portal/PortalAuthFilter.java` | 门户登录校验 |
| `portal/PortalTokenService.java` | 门户 JWT |
| `portal/PortalStore.java` | 门户内存数据 + 演示账号 |
| `portal/PortalAccessService.java` | 申请/审批/开通 |
| `portal/PortalCatalogService.java` | 目录/文档 |
| `portal/PortalDashboardService.java` | 首页 |
| `portal/UsageTracker.java` | 调用采集 |
| `enterprise/MutableAppRegistry.java` | 应用/产品写接口 |
| `common/PublicPaths.java` | 门户/开放路径隔离 |
| `frontend/src/App.tsx` | 路由 |
| `frontend/src/components/Layout.tsx` | 控制台框架 |
| `frontend/src/pages/*` | 各页面 |
| `frontend/vite.config.ts` | 代理 |

### 8.6 绝对不要做的事

- 不要把门户路径加入 `PUBLIC_PATHS`；
- 不要复用开放 API 的 token 给门户；
- 不要让创建应用直接获得产品权限；
- 不要删除审批通过后的重新换 token 步骤；
- 不要提交 `node_modules`、`dist`、`target`、项目内 `.m2`、`.idea/workspace.xml`；
- 不要在未确认的情况下修改 `crcit-gateway`（本期边界）；
- 不要把演示 secret/密码当作生产配置。

---

## 9. 交接完成检查清单

- [ ] 已读 `HANDOFF.md`；
- [ ] `pwd` 正确；
- [ ] `java -version` 是 17；
- [ ] `mvn -o -B test` 通过（9 个测试）；
- [ ] `cd frontend && npm run build` 通过；
- [ ] 8081 / 5173 空闲；
- [ ] 明确用户是要“继续做产品功能”还是“修当前 bug”；
- [ ] 如果继续产品化，从第 7 节 P1 开始。

---

**再次提醒：新会话第一句话请直接说：**

> **先读 HANDOFF.md，再继续这个任务。**
