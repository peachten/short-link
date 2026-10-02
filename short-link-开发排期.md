# 分布式短链接系统 · 四周开发排期（逐日对照清单）

> 配套文档：[short-link-开发文档.md](./short-link-开发文档.md)（设计原理、DDL、技术方案、部署、简历包装）
> 本文档是**开发时的操作手册**：每天照着 checklist 打勾，做完当天的「验收」再往下走。
> 节奏假设：每天 3~4 小时有效开发时间。**进度落后不要跳验收**，宁可砍功能（见附录 A 降级策略）。

---

## 阅读说明

每一天的卡片固定包含 5 个部分：

| 部分 | 含义 |
| --- | --- |
| **目标** | 今天结束时应该达成的一句话结论 |
| **任务清单** | 具体的编码/配置动作，逐项打勾 |
| **验收** | 必须亲手执行、能观察到结果的验证步骤。**不打勾不算完成** |
| **常见坑** | 大概率会踩的问题，提前看能省下大量调试时间 |
| **产出** | 今天新增/修改的文件清单 |

约定：

- 包名统一用 `com.xxx.shortlink`，实际替换成你自己的（如 `com.zhangsan.shortlink`）
- 所有 Redis Key 必须走 `RedisKeyConstant`，禁止硬编码
- 每天的代码当天提交一次 Git，commit message 用 `feat(w1d3): 接入 Knife4j`
- 接口自测全部用 Knife4j（`http://localhost:8000/doc.html`），不用 Postman 也行

**贯穿全程的约定（每天都要遵守）**

- [ ] 新增接口必须在 Knife4j 中标注清楚（`@Tag` / `@Operation` / `@Schema`），第 4 周就不用回头补
- [ ] 新增 Redis Key 必须在 `RedisKeyConstant` 中登记，并同步更新主文档第 5 章
- [ ] 新增表或字段必须同步更新 `sql/schema.sql`
- [ ] 核心 Service 方法写完立刻补最小单测（不要攒到周末）

---

## 全局依赖关系（哪些能并行、哪些必须串行）

```
W1D1 环境
  └─ W1D2 建库建表
       └─ W1D3 工程骨架
            └─ W1D4 公共组件（Result/异常/MP配置/Redis/Redisson）
                 ├─ W1D5 用户模块 + Sa-Token
                 │    └─ W1D6 分组模块 ──┐
                 └─ W2D1 工具类          │
                      └─ W2D2 创建短链 ←┘（依赖分组）
                           ├─ W2D3 布隆过滤器
                           │    └─ W2D4 跳转 + 缓存
                           │         ├─ W2D5 限流
                           │         └─ W3D2 MQ 生产者
                           │              └─ W3D3 MQ 消费者 ──┐
                           └─ W2D6 短链管理                  │
                                └─ W3D4 实时统计 ────────────┘
                                     └─ W3D5 定时聚合
                                          └─ W3D6 统计查询接口
                                               └─ W4D3 前端看板
```

**关键路径**：环境 → 骨架 → 公共组件 → 创建短链 → 跳转 → MQ → 统计 → 前端看板。
**可以压缩的**：W2D6 短链管理（接口多但技术含量低，可用 AI 快速生成）、W4D2 前端页面、W4D4 联调。

---

# 第 1 周：地基（环境 + 骨架 + 用户/分组）

> 本周核心目标：**跑通「注册 → 登录 → 建分组 → 查分组」，并且工程骨架具备后续所有能力（统一响应、异常、MyBatis-Plus、Redis、Redisson、Knife4j、鉴权）**。
> 本周不做短链业务。地基没打好，第 2 周会一直在补窟窿。

---

## 第 1 周 · D1 | 中间件与开发环境安装

**目标**：JDK17 + Maven + IDEA + MySQL8 + Redis + Erlang/RabbitMQ 全部装好并验证连通，能连上客户端工具。

**任务清单**

- [ ] 安装 JDK 17（推荐 Temurin 或 Oracle），配置 `JAVA_HOME` 与 `PATH`
- [ ] 安装 Maven 3.9.x，配置阿里云镜像（`conf/settings.xml` 的 `<mirrors>`）
- [ ] 安装 IDEA，装插件：Lombok、MyBatisX、Rainbow Brackets（可选）
- [ ] 安装 MySQL 8（本机已有 8.4 则跳过，确认服务已启动且记得 root 密码）
- [x] 安装 Redis 5.0.14 Windows 版（tporadowski 移植版），注册为 Windows 服务
- [x] 安装 Erlang/OTP 26.x（**必须先装**），再安装 RabbitMQ 3.13.x
- [x] 开启 RabbitMQ 管理插件，浏览器能打开 `http://localhost:15672`
- [x] 安装客户端工具：Navicat / DBeaver（连 MySQL）、RedisInsight 或直接用 `redis-cli`、RabbitMQ 自带 Web 控制台
- [x] 安装 JMeter 5.6（第 2 周就要用，别等到第 4 周才装）
- [x] 安装 Git，初始化仓库 `git init`

**验收（逐条执行并观察结果）**

```powershell
java -version                 # 输出 17.x
mvn -v                        # 输出 Maven 3.9.x 且 Java version: 17
mysql --version               # 8.x
redis-cli ping                # 返回 PONG（先确认 Redis 服务已启动）
rabbitmqctl status            # 输出 RabbitMQ 版本与 Erlang 版本，无报错
```

- [ ] `http://localhost:15672` 能用 `guest/guest` 登录（本地开发阶段允许，部署时必须改）
- [ ] IDEA 能正常创建 Java 17 项目

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 先装 RabbitMQ 后装 Erlang | RabbitMQ 服务启动失败，日志报找不到 Erlang | 必须先装 Erlang，装完 RabbitMQ 要重新注册服务 |
| Erlang 与 RabbitMQ 版本不匹配 | 启动报 `Unable to load emulator DLL` | 3.13.x 配 OTP 26.x，严格按官方兼容表 |
| Redis Windows 版注册服务后启动失败 | 服务闪退 | 用 `redis-server --service-install redis.windows.conf`，且 conf 路径用绝对路径 |
| Maven 下载依赖极慢 | 卡在 downloading | 配置阿里云镜像，IDEA 里同步修改 Maven 的 settings.xml 路径 |
| MySQL 8 时区/驱动问题 | 后续连库报 `server time zone` | JDBC url 里加 `serverTimezone=Asia/Shanghai` |

**产出**：环境自检清单全部通过，无代码产出。

---

## 第 1 周 · D2 | 数据库设计与建表

**目标**：11 张表全部建成（含可选的 `t_link_goto`），`RedisKeyConstant` 常量类写到位。

**任务清单**

- [ ] 新建工程目录结构：`short-link-project/`（后端）、`sql/`、`docs/`
- [ ] 创建 `sql/schema.sql`：从主文档 [第 4 章](./short-link-开发文档.md#4-数据库设计) 复制全部 DDL
- [ ] 建库 `short_link`（`utf8mb4` / `utf8mb4_general_ci`），执行 `schema.sql`
- [ ] 手工插入一条测试用户和一条测试分组，确认字段可用
- [ ] 创建 `RedisKeyConstant`：把主文档 [第 5 章](./short-link-开发文档.md#5-redis-key-设计) 的 17 个 Key 全部写成常量 + 拼接方法
- [ ] 更新 Redis 配置文件：`maxmemory 256mb`、`maxmemory-policy volatile-lru`、`appendonly yes`

`RedisKeyConstant` 参考写法：

```java
public final class RedisKeyConstant {

    private RedisKeyConstant() {}

    private static final String PREFIX = "short-link:";

    /* ---------- 短链跳转 ---------- */
    public static String gotoCache(String domain, String shortUri) {
        return PREFIX + "goto:" + domain + ":" + shortUri;
    }

    /* ---------- 布隆过滤器 ---------- */
    public static String bloomFilter(String gid) {
        return PREFIX + "bloom-filter:" + gid;
    }

    /* ---------- 分组短链集合 ---------- */
    public static String gidSet(String gid) {
        return PREFIX + "gid:set:" + gid;
    }

    /* ---------- 统计 ---------- */
    public static String statsPv(String date, String fullShortUrl) {
        return PREFIX + "stats:pv:" + date + ":" + fullShortUrl;
    }
    public static String statsUv(String date, String fullShortUrl) {
        return PREFIX + "stats:uv:" + date + ":" + fullShortUrl;
    }
    public static String statsUip(String date, String fullShortUrl) {
        return PREFIX + "stats:uip:" + date + ":" + fullShortUrl;
    }
    /** dimension: browser / os / device / locale */
    public static String statsDimension(String dimension, String date, String fullShortUrl) {
        return PREFIX + "stats:" + dimension + ":" + date + ":" + fullShortUrl;
    }
    public static String statsHour(String date, String fullShortUrl) {
        return PREFIX + "stats:hour:" + date + ":" + fullShortUrl;
    }

    /* ---------- 锁与限流 ---------- */
    public static String lockCreate(String gid, String urlHash) {
        return PREFIX + "lock:create:" + gid + ":" + urlHash;
    }
    public static String lockCustomUri(String gid, String shortUri) {
        return PREFIX + "lock:custom-uri:" + gid + ":" + shortUri;
    }
    public static String lockJob(String jobName) {
        return PREFIX + "lock:job:" + jobName;
    }
    public static String rateLimit(String type, String key) {
        return PREFIX + "rate-limit:" + type + ":" + key;
    }
}
```

**验收**

```sql
USE short_link;
SHOW TABLES;                    -- 应有 11 张表
SHOW CREATE TABLE t_link\G      -- 确认 uk_gid_origin / uk_short_uri 两个唯一索引都在
```

```powershell
redis-cli -a 你的密码 config get maxmemory-policy   # 返回 volatile-lru
```

- [ ] 11 张表全部存在，字段注释完整
- [ ] `t_link` 的 `uk_gid_origin`、`uk_short_uri` 唯一索引确认存在（后续幂等靠它兜底）

**常见坑**

| 坑 | 说明 |
| --- | --- |
| 唯一索引建不上 | `uk_gid_origin (gid, origin_url_hash)` 长度 = 32×4 + 32×4 = 256 字节，远小于 3072 限制，正常可建。若报错检查字符集 |
| `t_link_access_logs` 的 `uk_msg_id` 漏建 | 这是第 3 周消费幂等的唯一依据，**漏了会导致重复数据**，务必建上 |
| Oracle 用 `DATE` 类型混淆 | MySQL 用 `DATE`（精确到天）和 `DATETIME`（精确到秒），统计表 `date` 字段用 `DATE` |
| 忘记设置 `del_flag` 默认值 | 所有表 `del_flag` 必须 `NOT NULL DEFAULT 0`，否则 MyBatis-Plus 逻辑删除会查不到数据 |

**产出**

- `sql/schema.sql`
- `com.xxx.shortlink.common.constant.RedisKeyConstant`

---

## 第 1 周 · D3 | Maven 工程搭建 + Knife4j 接入

**目标**：`mvn spring-boot:run` 能启动，访问 `http://localhost:8000/doc.html` 能看到 Knife4j 页面。

**任务清单**

- [ ] 用 IDEA 创建 Spring Boot 工程（Spring Initializr 或手写 pom）
- [ ] 写 `pom.xml`：按主文档 [8.1 节](./short-link-开发文档.md#81-maven-依赖关键坐标) 引入全部依赖
- [ ] 建立分包结构（controller / service / dao / entity / dto / vo / common / mq / job / util）
- [ ] 写启动类 `ShortLinkApplication`（`@SpringBootApplication`、`@MapperScan`、`@EnableScheduling`、`@EnableAsync`）
- [ ] 写 `application.yml` + `application-dev.yml`（数据库、Redis、RabbitMQ 连接信息，密码先用本地值）
- [ ] 写 `Knife4jConfig`：OpenAPI 信息 + 分组 + `Authorization` 鉴权头
- [ ] 写一个 `HealthController`（`GET /api/short-link/admin/v1/health`）用于验证链路

`Knife4jConfig` 参考：

```java
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info().title("短链接系统 API")
                        .description("分布式短链接生成与访问统计系统")
                        .version("v1.0"))
                .components(new Components().addSecuritySchemes("Authorization",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .in(SecurityScheme.In.HEADER)
                                .name("Authorization")));
    }

    @Bean
    public GroupedOpenApi adminApi() {
        return GroupedOpenApi.builder()
                .group("01-管理端接口")
                .pathsToMatch("/api/short-link/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi projectApi() {
        return GroupedOpenApi.builder()
                .group("02-短链跳转接口")
                .pathsToMatch("/**")
                .pathsToExclude("/api/**", "/doc.html", "/webjars/**", "/v3/**", "/swagger-ui/**", "/error")
                .build();
    }
}
```

**验收**

- [ ] `http://localhost:8000/doc.html` 打开，能看到「01-管理端接口」「02-短链跳转接口」两个分组
- [ ] 在线调用 `/health` 返回 `200`
- [ ] 启动日志中没有 `Failed to start bean 'documentationPluginsBootstrapper'` 之类报错

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 用错 starter | 启动报 `ClassNotFoundException: javax.servlet.*` | Spring Boot 3 必须用 `knife4j-openapi3-jakarta-spring-boot-starter` |
| MyBatis-Plus starter 用错 | 启动报 `Invalid value type for attribute 'factoryBeanObjectType'` | 必须用 `mybatis-plus-spring-boot3-starter` |
| Sa-Token 与 Knife4j 路径冲突 | 后续接入 Sa-Token 后 doc.html 打不开 | 拦截器里放行 `/doc.html`、`/webjars/**`、`/v3/**`、`/swagger-ui/**`、`/favicon.ico` |
| JDK 版本不匹配 | 编译报 `release version 17 not supported` | IDEA 里确认 Project SDK、Language level、Maven compiler 三处都是 17 |

**产出**

- `pom.xml`、`application.yml`、`application-dev.yml`
- `ShortLinkApplication`、`Knife4jConfig`、`HealthController`

---

## 第 1 周 · D4 | 公共组件（统一响应 / 异常 / MyBatis-Plus / Redis / Redisson）

**目标**：任意接口抛异常都能返回统一 JSON；MyBatis-Plus 分页、逻辑删除、字段自动填充生效；Redis 中存的是可读 JSON；Redisson 可获取锁。

**任务清单**

- [ ] `Result<T>` 统一响应体 + `ResultCode` 枚举（0 成功，`A0001` 客户端错误，`B0001` 系统错误……）
- [ ] `BizException`（业务异常，带 message + code）
- [ ] `GlobalExceptionHandler`：处理 `BizException`、`MethodArgumentNotValidException`、`BindException`、`DuplicateKeyException`、兜底 `Exception`
- [ ] `TraceIdFilter`：每个请求生成 `requestId` 放入 MDC，响应头带回，日志格式 `%X{requestId}`
- [ ] `MybatisPlusConfig`：分页插件 `PaginationInnerInterceptor`（**注意指定 DbType.MYSQL**）+ 乐观锁插件
- [ ] `MyMetaObjectHandler`：`createTime` / `updateTime` 自动填充
- [ ] 全局配置逻辑删除字段 `delFlag`（写在 yml 的 `mybatis-plus.global-config`）
- [ ] `RedisTemplateConfig`：`StringRedisTemplate` 直接用 + 自定义 `RedisTemplate<String, Object>` 用 `GenericJackson2JsonRedisSerializer`
- [ ] `RedissonConfig`：单节点 `RedissonClient`
- [ ] `logback-spring.xml`：控制台 + 文件，按天切割，保留 15 天

`GlobalExceptionHandler` 关键片段（`DuplicateKeyException` 必须处理，第 2 周靠它兜底）：

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public Result<Void> handleBiz(BizException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Results.failure(e.getCode(), e.getMessage());
    }

    /** 唯一索引冲突：并发创建短链时可能出现，交由上层业务转成幂等返回 */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e) {
        log.warn("唯一索引冲突", e);
        return Results.failure(ResultCode.DUPLICATE_KEY, "数据已存在，请勿重复提交");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValid(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + " " + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return Results.failure(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleAll(Exception e) {
        log.error("系统异常", e);
        return Results.failure(ResultCode.SYSTEM_ERROR, "系统繁忙，请稍后重试");
    }
}
```

**验收**

- [ ] 写一个临时接口 `throw new BizException("测试异常")`，Knife4j 调用返回 `{"code":"A0001","message":"测试异常","requestId":"xxx"}`，HTTP 状态仍是 200
- [ ] 日志文件中每行都带 `requestId`
- [ ] 用 RedisInsight / `redis-cli` 查看 set 进去的对象，是**可读 JSON** 而不是二进制乱码
- [ ] 单测或临时接口验证：`redissonClient.getLock("test").tryLock()` 返回 true，`unlock()` 成功

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| Redis 存的是乱码 | `\xac\xed\x00\x05sr...` | 默认 `JdkSerializationRedisSerializer`，换成 `GenericJackson2JsonRedisSerializer` |
| JSON 序列化 `LocalDateTime` 报错 | `Java 8 date/time type not supported` | 注册 `JavaTimeModule`，并关闭 `WRITE_DATES_AS_TIMESTAMPS` |
| 反序列化得到 `LinkedHashMap` | 取出来强转报 `ClassCastException` | `GenericJackson2JsonRedisSerializer` 需要类上有 `@class` 信息，或改用 `StringRedisTemplate` 手动 JSON 转换（**推荐后者，更可控**） |
| 分页插件不生效 | 查询返回全部数据 | `PaginationInnerInterceptor` 必须设置 `DbType.MYSQL`，且 `mybatis-plus` 配置在 starter 正确时自动装配 |
| 逻辑删除后查不到数据 | 明明有数据却查不出来 | yml 中 `logic-delete-field` 名字要和你实体字段一致（`delFlag`） |

**产出**

- `common/result/Result.java`、`Results.java`、`ResultCode.java`
- `common/exception/BizException.java`、`GlobalExceptionHandler.java`
- `common/config/MybatisPlusConfig.java`、`RedisTemplateConfig.java`、`RedissonConfig.java`
- `common/handler/MyMetaObjectHandler.java`、`filter/TraceIdFilter.java`
- `resources/logback-spring.xml`

---

## 第 1 周 · D5 | 用户模块 + Sa-Token 鉴权

**目标**：注册 → 登录拿到 token → 带 token 访问 `/user/current` 返回用户信息；不带 token 返回未登录错误。

**任务清单**

- [ ] `TUser` 实体（`@TableName("t_user")`、`@TableLogic` 标注 `delFlag`）+ `@TableField(fill = ...)` 标注时间字段
- [ ] `UserMapper extends BaseMapper<TUser>`
- [ ] DTO：`UserRegisterReqDTO`、`UserLoginReqDTO`（都加 `@NotBlank` / `@Size` 校验注解）
- [ ] VO：`UserRespVO`（**绝不返回 password 字段**）
- [ ] `UserService` / `UserServiceImpl`
  - [ ] `hasUsername(username)`：查重
  - [ ] `register(req)`：查重 → BCrypt 加密密码 → 保存
  - [ ] `login(req)`：查用户 → BCrypt 校验 → `StpUtil.login(username)` → 返回 token
  - [ ] `getCurrentUser()`：从 `StpUtil.getLoginId()` 取用户名查用户
  - [ ] `logout()`：`StpUtil.logout()`
- [ ] `UserController`：注册、登录、登出、当前用户、用户名是否可用
- [ ] `SaTokenConfig`：注册 `SaInterceptor`，放行登录/注册/跳转/Knife4j 资源，其余需登录
- [ ] `SaTokenExceptionHandler`：处理 `NotLoginException`（返回「未登录」）、`NotPermissionException`
- [ ] Sa-Token 接入 Redis：`sa-token-redis-jackson` 依赖 + yml 配置

BCrypt 直接使用 Hutool（避免为加密单独引入 Spring Security）：

```java
String hash = BCrypt.hashpw(rawPassword);        // cn.hutool.crypto.digest.BCrypt
boolean ok  = BCrypt.checkpw(rawPassword, hash);
```

`SaTokenConfig` 参考：

```java
@Configuration
public class SaTokenConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/short-link/admin/v1/user/login",
                        "/api/short-link/admin/v1/user/register",
                        "/api/short-link/admin/v1/user/has-username",
                        "/api/short-link/admin/v1/health",
                        "/doc.html", "/webjars/**", "/v3/api-docs/**",
                        "/swagger-ui/**", "/favicon.ico", "/error"
                );
    }
}
```

**验收**

1. Knife4j 调用注册接口 → 返回成功；查库确认 `password` 字段是 `$2a$10$...` 格式的 BCrypt 密文
2. 用相同用户名再注册 → 返回「用户名已存在」
3. 调用登录接口 → 返回 token 字符串
4. 复制 token 到 Knife4j 的 `Authorization` 请求头（**注意 Sa-Token 配置的 `token-name` 与这里保持一致**）→ 调用 `/user/current` 返回用户信息且**不含 password**
5. 清空请求头再调用 `/user/current` → 返回「未登录」
6. 用错误密码登录 → 返回「用户名或密码错误」（**不要提示"用户不存在"，防用户名枚举**）

- [ ] 上述 6 条全部通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| token 存不到 Redis | `sa-token-redis-jackson` 没引或配置不对 | 引依赖后 Sa-Token 自动使用 Redis 存储，用 `redis-cli keys "satoken*"` 验证 |
| 拦截器把 doc.html 也拦了 | 登录页都打不开 | 放行列表必须包含 Knife4j 全部静态资源路径 |
| 登录后仍提示未登录 | token 名不一致 | `sa-token.token-name` 若设为 `Authorization`，前端/文档请求头就必须叫 `Authorization`；若用 `satoken` 则要改前端（建议统一 `Authorization`） |
| 密码明文入库 | 忘记加密 | 注册方法里一定要 `BCrypt.hashpw`，验收第 1 条就是查这个 |
| 返回 VO 里带了密码 | 直接返回实体 | 必须转 VO，或用 `@JsonIgnore` 标注 password 字段 |

**产出**

- `entity/TUser.java`、`dao/UserMapper.java`
- `dto/UserRegisterReqDTO.java`、`UserLoginReqDTO.java`、`vo/UserRespVO.java`
- `service/UserService.java`、`service/impl/UserServiceImpl.java`、`controller/UserController.java`
- `common/config/SaTokenConfig.java`、`common/exception/SaTokenExceptionHandler.java`

---

## 第 1 周 · D6 | 分组模块

**目标**：分组的增删改查可用，且每个接口都做了**数据归属校验**（不能操作别人的分组）。

**任务清单**

- [ ] `TGroup` 实体 + `GroupMapper`（含自定义 SQL：查分组并统计分组内短链数量）
- [ ] `GroupService` / `GroupServiceImpl`
  - [ ] `saveGroup(name)`：生成 `gid`（`Base62Util.random(6)`，此时可先临时用 UUID 前 6 位，W2D1 再换）→ 保存，绑定当前登录用户
  - [ ] `listGroup()`：查当前用户分组 + 短链数量（此时 `t_link` 无数据，SQL 先跑通返回 0）
  - [ ] `updateGroup(gid, name)`：**校验分组属于当前用户**
  - [ ] `deleteGroup(gid)`：**校验归属** + 校验分组下无短链（有则拒绝）
  - [ ] `countGroup()`：当前用户分组数量
- [ ] `GroupController` 5 个接口
- [ ] 抽一个 `checkGroupOwnership(gid)` 私有方法，所有涉及分组的操作统一调用

分页/归属校验写法参考：

```java
private TGroup checkOwnership(String gid) {
    TGroup group = groupMapper.selectOne(new LambdaQueryWrapper<TGroup>()
            .eq(TGroup::getGid, gid));
    if (group == null) {
        throw new BizException("分组不存在");
    }
    if (!group.getUsername().equals(StpUtil.getLoginIdAsString())) {
        // 不要提示"无权访问他人分组"，避免暴露分组存在性
        throw new BizException("分组不存在");
    }
    return group;
}
```

分组内短链数量 SQL（W2 之后才能真正有数据，先写好放 XML 里）：

```xml
<select id="countLinkByGid" resultType="com.xxx.shortlink.dao.entity.TGroup">
    SELECT g.*, COUNT(l.id) AS linkCount
    FROM t_group g
    LEFT JOIN t_link l ON g.gid = l.gid AND l.del_flag = 0
    WHERE g.username = #{username} AND g.del_flag = 0
    GROUP BY g.id
    ORDER BY g.sort_order ASC, g.create_time DESC
</select>
```

**验收**

1. 新建 3 个分组（名称：工作、学习、测试）→ 列表返回 3 条，且每条 `linkCount = 0`
2. 重命名「测试」→「暂存」→ 列表显示新名称
3. 删除「暂存」→ 列表只剩 2 条
4. **越权测试**：注册第二个用户 → 用第二个用户的 token 去改第一个用户的分组 gid → 返回「分组不存在」（而不是成功）
5. 重复名称分组 → 允许（分组名不要求唯一，但 gid 必须唯一）

- [ ] 上述 5 条通过

**常见坑**

| 坑 | 说明 |
| --- | --- |
| 忘记归属校验 | 所有带 `gid` 参数的接口都必须校验，这是简历上「数据隔离」的落点 |
| gid 重复 | gid 要有唯一索引 + 生成时查重，W2 用 Redis Set 优化，现在先靠唯一索引 |
| 分组下有短链仍可删除 | 删除前 `COUNT` 一次 `t_link`，非 0 则抛业务异常 |

**产出**

- `entity/TGroup.java`、`dao/GroupMapper.java` + `resources/mapper/GroupMapper.xml`
- `service/GroupService.java` + 实现类、`controller/GroupController.java`
- `dto/GroupSaveReqDTO.java`、`GroupUpdateReqDTO.java`、`vo/GroupRespVO.java`

---

## 第 1 周 · D7 | 单测 + 周验收 + 复盘

**目标**：第 1 周的所有功能在**清空 Redis、重启应用**后仍能全流程跑通；核心 Service 有单测。

**任务清单**

- [ ] `UserServiceImplTest`：重复注册抛异常、BCrypt 加密后可校验、错误密码登录失败
- [ ] `GroupServiceImplTest`：越权访问抛「分组不存在」、分组下有短链时删除失败
- [ ] 用 Knife4j 把第 1 周所有接口按顺序完整走一遍并截图（后面写 README 要用）
- [ ] 检查 Git 提交记录是否每天一次
- [ ] 更新 `docs/进度.md`：记录本周遇到的问题与解决方式（**这些就是你面试时「项目难点」的素材**）

**验收（本周总验收，必须全部通过）**

```
1. 清空 Redis（flushdb）→ 重启应用 → 无报错
2. 注册新用户 zhangsan/123456 → 成功
3. 登录 zhangsan → 拿到 token
4. 带 token 新建分组「工作」→ 成功，返回 gid
5. 查分组列表 → 有「工作」，linkCount=0
6. 不带 token 查分组列表 → 返回「未登录」
7. 用 lisi 的 token 改 zhangsan 的分组 → 返回「分组不存在」
```

- [ ] `mvn test` 全部通过
- [ ] 上述 7 步全部通过

**每周固定检查清单（每周日都要过一遍）**

- [ ] 本周功能能在干净环境（清 Redis、重启应用）复现吗？
- [ ] 核心 Service 有单测吗？（覆盖率目标 ≥ 50%，只测核心逻辑）
- [ ] Knife4j 文档是否随代码更新？
- [ ] 有没有阻塞超过 4 小时的问题？（有则记录下来，并在附录 A 决定是否降级）
- [ ] `sql/schema.sql` 是否与数据库实际结构一致？

**产出**：单测文件、`docs/进度.md`、本周接口截图。

---

# 第 2 周：核心（短链创建 + 跳转 + 缓存三件套 + 限流）

> 本周核心目标：**这是整个项目最值钱的一周**。跑通「创建短链 → 浏览器 302 跳转 → 拦截不存在的短链 → 限流生效」四条主线。
> 本周结束你应该能在简历上写出「缓存三件套 / 分布式锁幂等 / Lua 滑动窗口限流」三条要点了。

---

## 第 2 周 · D1 | Base62 与短链生成策略

**目标**：`Base62Util` 有单测覆盖，短链后缀生成器可用且具备查重能力。

**任务清单**

- [ ] `Base62Util`：`encode(long)` / `decode(String)` / `random(int length)`（按主文档 [7.1](./short-link-开发文档.md#71-短链生成算法)）
- [ ] `HashUtil.md5(String)`：生成 `originUrlHash`
- [ ] `ShortUriGenerator`：生成 6 位后缀 + 查重逻辑
- [ ] `LinkUtil`：拼接完整短链 `domain + "/" + gid + "/" + shortUri`
- [ ] 把 W1D6 分组 gid 生成逻辑换成 `Base62Util.random(6)`
- [ ] 单测 `Base62UtilTest`：编解码互逆、`random(6)` 全部字符属于字符集、10 万次随机结果去重率 > 99.99%

查重逻辑（先用 DB，W2D3 加布隆后优化为「先布隆再 DB」）：

```java
public String generateUniqueShortUri(String gid) {
    for (int i = 0; i < MAX_RETRY; i++) {           // MAX_RETRY = 3
        String shortUri = Base62Util.random(6);
        boolean exists = linkMapper.exists(new LambdaQueryWrapper<TLink>()
                .eq(TLink::getGid, gid)
                .eq(TLink::getShortUri, shortUri));
        if (!exists) {
            return shortUri;
        }
        log.warn("短链后缀冲突, gid={}, shortUri={}, 重试第 {} 次", gid, shortUri, i + 1);
    }
    throw new BizException("短链生成失败，请重试");
}
```

**验收**

- [ ] `mvn test -Dtest=Base62UtilTest` 通过
- [ ] 控制台打印 10 个随机后缀，肉眼确认都是 6 位且混合数字大小写字母
- [ ] 思考并回答（写进 `docs/进度.md`）：6 位 Base62 能表示多少条？什么情况下会不够用？

**常见坑**

| 坑 | 说明 |
| --- | --- |
| `ThreadLocalRandom` 用成 `Random` | 单线程测试没问题，但并发下有竞争；用 `ThreadLocalRandom.current()` |
| 用 `Math.random()` 拼字符 | 精度差、可读性差，统一走 `Base62Util.random()` |
| `decode` 实现时字符集顺序不一致 | encode/decode 必须引用同一个 `CHARS` 常量，且注意 encode 是从低位往高位 append（返回的是逆序结果），本项目不需要反解，只用 encode 做演示即可 |

**产出**：`util/Base62Util.java`、`util/HashUtil.java`、`util/LinkUtil.java`、`test/Base62UtilTest.java`

---

## 第 2 周 · D2 | 创建短链（本周最重要的一天）

**目标**：同一分组下同一长链**并发创建 N 次只产生 1 条记录**，其余请求返回同一个短链。这是幂等的核心验收点。

**任务清单**

- [ ] `TLink` 实体 + `LinkMapper`
- [ ] `ShortLinkCreateReqDTO`：`originUrl`（`@NotBlank` + `@URL` 或自定义正则）、`gid`、`createdType`、`validDateType`、`validDate`、`describe`、`customShortUri`
- [ ] `ShortLinkCreateRespDTO`：`fullShortUrl`、`shortUri`、`originUrl`、`idempotent`
- [ ] `ShortLinkService.create(req)` 实现三段式：
  - [ ] **① 快路径**：按 `(gid, originUrlHash)` 查库，存在则直接返回 `idempotent=true`
  - [ ] **② 慢路径**：Redisson 加锁（key = `RedisKeyConstant.lockCreate(gid, urlHash)`）→ 锁内**双重检查**再查一次
  - [ ] **③ 兜底**：捕获 `DuplicateKeyException` 转成查库返回
- [ ] `createBatch(List<req>)`：循环调用 `create`，单个失败不影响其他
- [ ] 自定义后缀分支：走 `lockCustomUri` 锁 + `t_link` 的 `uk_short_uri` 唯一索引
- [ ] 参数校验：gid 归属（复用 W1D6 的 `checkOwnership`）、有效期不能早于当前时间、URL 必须是 http/https
- [ ] 创建成功后写入 Redis 分组 Set（`SADD RedisKeyConstant.gidSet(gid) shortUri`）并设 1 天过期

创建主流程参考实现：

```java
@Override
public ShortLinkCreateRespDTO create(ShortLinkCreateReqDTO req) {
    // 0. 基础校验
    checkGroupOwnership(req.getGid());
    validCreateParam(req);

    String originUrlHash = HashUtil.md5(req.getOriginUrl());

    // 1. 快路径：命中直接返回（幂等）
    TLink exist = findExist(req.getGid(), originUrlHash);
    if (exist != null) {
        return buildResp(exist, true);
    }

    // 2. 慢路径：分布式锁 + 双重检查
    String lockKey = RedisKeyConstant.lockCreate(req.getGid(), originUrlHash);
    RLock lock = redissonClient.getLock(lockKey);
    boolean locked = false;
    try {
        // 注意：不指定 leaseTime，启用看门狗自动续期
        locked = lock.tryLock(3, TimeUnit.SECONDS);
        if (!locked) {
            throw new BizException("创建请求过于频繁，请稍后重试");
        }

        exist = findExist(req.getGid(), originUrlHash);   // 双重检查
        if (exist != null) {
            return buildResp(exist, true);
        }

        return doCreate(req, originUrlHash);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BizException("创建被中断，请重试");
    } finally {
        // 必须判断持有者，避免释放别人的锁
        if (locked && lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}

private ShortLinkCreateRespDTO doCreate(ShortLinkCreateReqDTO req, String urlHash) {
    String shortUri = StringUtils.hasText(req.getCustomShortUri())
            ? req.getCustomShortUri()
            : generateUniqueShortUri(req.getGid());

    TLink link = TLink.builder()
            .domain(defaultDomain)
            .shortUri(shortUri)
            .fullShortUrl(LinkUtil.build(defaultDomain, req.getGid(), shortUri))
            .originUrl(req.getOriginUrl())
            .originUrlHash(urlHash)
            .gid(req.getGid())
            .enableStatus(0)
            .createdType(req.getCreatedType())
            .validDateType(req.getValidDateType())
            .validDate(req.getValidDate())
            .description(req.getDescribe())
            .clickNum(0)
            .build();

    try {
        linkMapper.insert(link);      // 唯一索引兜底
    } catch (DuplicateKeyException e) {
        // 并发下被其他线程抢先插入，转为幂等返回
        TLink dup = findExist(req.getGid(), urlHash);
        if (dup != null) {
            return buildResp(dup, true);
        }
        throw new BizException("短链创建失败，请重试");
    }

    // 写入分组 Set，供后缀查重使用
    String setKey = RedisKeyConstant.gidSet(req.getGid());
    stringRedisTemplate.opsForSet().add(setKey, shortUri);
    stringRedisTemplate.expire(setKey, Duration.ofDays(1));

    return buildResp(link, false);
}
```

**验收（三条都要做）**

1. Knife4j 连续调用两次创建接口，传**完全相同的 `originUrl` + `gid`** → 两次返回的 `fullShortUrl` **完全相同**，第二次 `idempotent=true`，数据库只有 1 条
2. 传相同 `originUrl` 但**不同 `gid`** → 生成两条不同的短链（不同分组互不影响）
3. **并发验收**：JMeter 建 100 线程 × 1 次循环，全部请求同一 `originUrl` + `gid`：
   - [ ] 最终 `SELECT COUNT(*) FROM t_link WHERE gid='xxx' AND origin_url_hash='xxx'` 结果为 **1**
   - [ ] 无 500 错误抛出到前端（`DuplicateKeyException` 被吃掉了）
   - [ ] 日志中能看到锁等待与双重检查命中的记录

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| `tryLock(3, 30, SECONDS)` 指定了 leaseTime | 看门狗失效，锁 30s 后自动释放，长任务并发问题 | 用 `tryLock(3, TimeUnit.SECONDS)` 二参版本 |
| 忘记 `isHeldByCurrentThread()` | 业务超时后释放了别人的锁，抛 `IllegalMonitorStateException` | finally 里必须判断持有者 |
| 锁 key 里没带 gid | 不同分组下同一长链互相阻塞 | key 必须包含 gid |
| 锁内查询漏了 `del_flag` | 删除过的记录被当成不存在，重复插入撞唯一索引 | 用 LambdaQueryWrapper 时注意逻辑删除自动追加，**但删除记录仍占唯一索引**（见 W2D6 坑） |
| `@URL` 注解校验太宽松 | 允许 `ftp://` 等协议 | 自定义正则 `^https?://.+` |
| 批量创建用 `saveBatch` | 循环里调用单条 create 更安全（每条独立事务与幂等） | 本项目用循环，可接受；量大再优化 |

**产出**

- `entity/TLink.java`、`dao/LinkMapper.java`
- `dto/ShortLinkCreateReqDTO.java`、`vo/ShortLinkCreateRespDTO.java`
- `service/ShortLinkService.java` + 实现类、`controller/ShortLinkController.java`（创建接口部分）
- `test/ShortLinkServiceImplTest.java`（幂等、过期、禁用三类场景）

---

## 第 2 周 · D3 | 布隆过滤器（防缓存穿透）

**目标**：访问一个**不存在**的短链时，日志里**看不到任何 SQL 查询**，直接返回 404。

**任务清单**

- [ ] `RedissonConfig` 中增加 `RBloomFilter<String>` Bean：`tryInit(1_000_000, 0.01)`
- [ ] `BloomFilterService`：
  - [ ] `add(gid, shortUri)`
  - [ ] `mightContain(gid, shortUri)`
  - [ ] `rebuild(gid)`：清空 + 从 DB 全量重灌
- [ ] 启动预热：实现 `ApplicationRunner`，应用启动后分页扫描 `t_link` 中 `enable_status=0 AND del_flag=0` 的记录灌入布隆
- [ ] W2D2 的创建逻辑中，创建成功后调用 `bloomFilterService.add(gid, shortUri)`
- [ ] 单测：加入元素后 `mightContain` 为 true；未加入的元素 `mightContain` 为 false

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class BloomFilterInitializer implements ApplicationRunner {

    private final BloomFilterService bloomFilterService;

    @Override
    public void run(ApplicationArguments args) {
        long start = System.currentTimeMillis();
        int total = bloomFilterService.rebuildAll();
        log.info("布隆过滤器预热完成, 共 {} 条, 耗时 {} ms", total, System.currentTimeMillis() - start);
    }
}
```

**验收**

1. 重启应用，日志出现「布隆过滤器预热完成, 共 N 条」
2. 用 `redis-cli keys "short-link:bloom-filter:*"` 能看到布隆 key
3. **打开 MyBatis 的 SQL 日志**（yml 中 `mybatis-plus.configuration.log-impl: org.apache.ibatis.logging.stdout.StdOutImpl`）
4. 访问 `http://localhost:8000/zzzzzz`（一个肯定不存在的 6 位短链，注意先确认它真的不存在）
   - [ ] 返回 404 页面
   - [ ] **控制台没有任何 `SELECT` 语句输出** ← 这是关键验收点
5. 访问一个真实存在的短链 → 控制台**有** SQL 输出（缓存未命中时）
6. 再访问一次同一个真实短链 → **没有 SQL 输出**（缓存命中）

- [ ] 6 条全部通过，并截图保存 SQL 日志对比（简历演示素材）

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| `tryInit` 第二次调用报错 | 应用重启时 `Bloom filter config has been changed` | Redisson 的 `tryInit` 是幂等的，已初始化返回 false 不抛异常；但**如果改了期望插入量或误判率**会抛异常，此时需先 `delete()` 再 `tryInit` |
| 布隆过滤器被 Redis 淘汰 | 重启后布隆为空，穿透防护失效 | 必须 `maxmemory-policy volatile-lru`（布隆 key 无 TTL，不会被淘汰）；千万别用 `allkeys-lru` |
| 预热太慢 | 数据量百万级启动卡住 | 分批（每批 1000 条）扫描，观察日志；本项目数据量小，不是问题 |
| 误判率设置过低 | 内存占用过高 | 100 万条 + 0.01 误判率约 1.2MB，完全够用 |
| 用布隆判断"存在"后仍查库 | 误判会走到 DB | 这是正常的：布隆判断 true 只代表"可能存在"，仍需查库；判断 false 才可以直接返回 |

**产出**：`common/config/RedissonConfig.java`（加 Bean）、`service/BloomFilterService.java` + 实现、`component/BloomFilterInitializer.java`

---

## 第 2 周 · D4 | 短链跳转 + 缓存（本周第二个核心）

**目标**：浏览器访问短链能 302 跳转；缓存命中无 SQL；不存在的短链被布隆拦截；过期/禁用的短链返回 404。

**任务清单**

- [ ] `RedirectController`：
  - [ ] `GET /{shortUri}` → 使用默认分组 gid
  - [ ] `GET /{gid}/{shortUri}`
  - [ ] `GET /page/notfound` → 返回自定义 404 页面（静态 HTML）
- [ ] `ShortLinkService.restoreUrl(gid, shortUri)` 完整流程：

```
1. 格式预校验：shortUri 长度必须是 6 且字符集合法，否则 404（连 Redis 都不查）
2. 布隆过滤器 mightContain？false → 404
3. 查缓存 RedisKeyConstant.gotoCache(domain, shortUri)
   - 命中且值为空字符串 "" → 404（空值缓存）
   - 命中且有值 → 跳到第 5 步
4. 未命中 → 查 DB
   - 不存在 / 已删除 / 已禁用 → 写空值缓存(TTL 60s) → 404
   - 已过期（valid_date_type=1 且 valid_date < now）→ 写空值缓存 → 404
   - 正常 → 回写缓存(TTL = 1天 + 随机0~1小时) → 继续
5. 采集访问日志（今天先只 log.info 打印，第 3 周接 MQ）
6. 返回 302，Location = originUrl
```

- [ ] 缓存回写（防雪崩的随机 TTL）：

```java
private void cacheOriginUrl(String shortUri, String originUrl) {
    long ttl = Duration.ofDays(1).plusSeconds(ThreadLocalRandom.current().nextInt(3600)).getSeconds();
    stringRedisTemplate.opsForValue().set(
            RedisKeyConstant.gotoCache(defaultDomain, shortUri), originUrl, ttl, TimeUnit.SECONDS);
}

private void cacheNull(String shortUri) {
    stringRedisTemplate.opsForValue().set(
            RedisKeyConstant.gotoCache(defaultDomain, shortUri), "", 60, TimeUnit.SECONDS);
}
```

- [ ] Controller 返回 302：

```java
@GetMapping("/{shortUri:[0-9a-zA-Z]{6}}")
public void redirect(@PathVariable String shortUri, HttpServletResponse response) throws IOException {
    String originUrl = shortLinkService.restoreUrl(defaultGid, shortUri);
    response.setStatus(HttpServletResponse.SC_FOUND);          // 302，不是 301
    response.setHeader("Location", originUrl);
    response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
}
```

**验收**

1. 创建一个短链指向 `https://www.baidu.com`
2. 浏览器访问 `http://localhost:8000/{shortUri}` → **成功跳转到百度**
3. 按 F12 → Network → 该请求状态码是 **302**，Response Headers 有 `Location`
4. 第二次访问同一个短链 → 控制台**无 SQL 输出**（缓存命中）
5. `redis-cli get "short-link:goto:http://localhost:8000:{shortUri}"` → 返回原始链接
6. `redis-cli ttl` 该 key → 在 86400 ~ 90000 秒之间的随机值（防雪崩生效）
7. 访问不存在的短链 → 404 页面；重复访问 → 仍 404 且有 `""` 空值缓存
8. 把某短链 `valid_date` 改成昨天（或 `enable_status` 改成 1）后访问 → 404

- [ ] 8 条全部通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 用了 301 | 后续访问不再请求服务器，**统计不到数据** | 必须 302 |
| `/{shortUri}` 与静态资源冲突 | 访问 `/favicon.ico` 报错 | 正则约束 `{shortUri:[0-9a-zA-Z]{6}}`，并把 `/page/notfound` 这种固定路径路由写在前面 |
| 缓存了空值但不区分"空"和"未命中" | 空值被当成命中后取到 null 又去查库 | 空值统一写 `""`，读到 `""` 直接返回 404 |
| 大小写敏感 | `AbC12x` 和 `abc12x` 是两条不同短链 | Base62 本身大小写敏感，属于正常设计；文档里说明即可 |
| 逻辑删除的记录仍能跳转 | 查询没带 `del_flag` | MyBatis-Plus 逻辑删除会自动追加条件，确认实体上有 `@TableLogic` |
| 缓存 key 里没带 domain | 多域名时互相覆盖 | key 必须含 domain |

**产出**：`controller/RedirectController.java`、`service` 中的 `restoreUrl`、`resources/static/page/notfound.html`

---

## 第 2 周 · D5 | 限流防刷（Redis + Lua 滑动窗口）

**目标**：超过阈值后请求被拒绝并返回友好提示；每天/每分钟的计数准确（并发下不超发）。

**任务清单**

- [ ] 写 `resources/lua/rate_limit_sliding_window.lua`（主文档 [7.5](./short-link-开发文档.md#75-限流防刷redis--lua-滑动窗口)）
- [ ] `RateLimitProperties` / `LimitType` 枚举（`IP` / `USER`）
- [ ] `@RateLimit` 注解：`type`、`window`（秒）、`limit`、`message`
- [ ] `RateLimitAspect`：
  - [ ] 从注解取配置
  - [ ] 按 `LimitType` 取限流维度值（IP 走 `IpUtil.getRealIp(request)`；USER 走 `StpUtil.getLoginIdAsString()`）
  - [ ] 调用 `RedisLuaExecutor` 执行脚本，返回 0 则抛 `BizException(message)`
- [ ] `RateLimitLuaConfig`：把 Lua 脚本加载为 `DefaultRedisScript<Long>` Bean（**不要每次请求都读文件**）
- [ ] `IpUtil`：优先取 `X-Forwarded-For` 第一个非 unknown IP，其次 `X-Real-IP`，最后 `getRemoteAddr()`
- [ ] 应用限流：
  - [ ] 创建短链接口：`@RateLimit(type = USER, window = 60, limit = 30)`
  - [ ] 跳转接口：`@RateLimit(type = IP, window = 60, limit = 300)`
- [ ] 单测 / 手测：`tryAcquire` 第 limit+1 次返回 false

```java
@Configuration
public class RateLimitLuaConfig {

    @Bean
    public DefaultRedisScript<Long> rateLimitScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/rate_limit_sliding_window.lua"));
        script.setResultType(Long.class);       // 必须是 Long，Lua 返回的是整数
        return script;
    }
}
```

**验收**

1. 用 JMeter 或 PowerShell 循环请求创建接口 40 次（阈值 30/60s）
   - [ ] 前 30 次成功，第 31 次返回「访问过于频繁，请稍后重试」
   - [ ] 等 60 秒后再请求 → 恢复成功
2. `redis-cli zcard "short-link:rate-limit:user:zhangsan"` 在窗口内返回的计数 ≤ 30
3. **并发准确性验证**：JMeter 50 线程同时打创建接口
   - [ ] Redis 中 ZSET 成员数不超过阈值（证明 Lua 原子性生效）
   - [ ] 如果不用 Lua（改成三次独立命令）会超发，**这个对比实验做一次，面试讲起来很有说服力**
4. 跳转接口按 IP 限流：本机反复刷新短链 300 次后返回限流提示

- [ ] 4 条通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| Lua 脚本返回值类型不对 | `ClassCastException` 或返回 null | Redis 的 Lua number 映射为 Java `Long`，`setResultType(Long.class)` |
| ZSET member 用时间戳 | 同一毫秒的多个请求覆盖成同一成员，计数偏小 | member 用 `UUID.randomUUID().toString()` |
| 忘了 `PEXPIRE` | ZSET 永不过期，内存泄漏 | 每次执行都 `PEXPIRE`（或仅在 count==1 时设置） |
| AOP 切不到 | 注解加在同类内部调用的方法上，Spring AOP 失效 | 注解加在 Controller 方法或跨 Bean 调用的 Service 方法上 |
| 拿不到真实 IP | 全部限成 127.0.0.1，一个用户被限流全班受影响 | `IpUtil` 优先读代理头；本机直连时就是 127.0.0.1，属正常 |
| 顺序问题 | 限流切面在事务切面之前/之后 | 加 `@Order` 控制，限流应该在业务逻辑之前执行 |

**产出**

- `resources/lua/rate_limit_sliding_window.lua`
- `common/annotation/RateLimit.java`、`common/enums/LimitType.java`
- `common/aspect/RateLimitAspect.java`、`common/config/RateLimitLuaConfig.java`
- `util/IpUtil.java`

---

## 第 2 周 · D6 | 短链管理接口（分页 / 编辑 / 启用禁用 / 删除）

**目标**：管理端接口齐全，且**更新和删除会正确清理缓存**（缓存一致性）。

**任务清单**

- [ ] 分页查询 `pageLink(req)`：
  - [ ] 支持 `gid`、`keyword`（描述或原始链接模糊）、`orderTag`（`createTime` / `clickNum`）、`current`、`size`
  - [ ] **排序字段白名单校验**，禁止把用户输入直接拼进 `orderBy`（SQL 注入）
  - [ ] 返回 `PageResult<ShortLinkPageRespVO>`，含 `fullShortUrl`、`originUrl`、`clickNum`、`validDate`、`enableStatus` 等
- [ ] 编辑 `updateLink(req)`：可改描述、有效期、分组
  - [ ] 若改了 `gid`，需要同时更新 Redis 分组 Set（旧 gid 移除、新 gid 加入）
  - [ ] **更新 DB 后删除缓存**（延迟双删：删 → 更新 → 延迟 500ms 再删）
- [ ] 批量修改 `updateBatch(list)`
- [ ] 启用/禁用 `enableLink(fullShortUrl, enableStatus)` → 更新 DB + **删除缓存**
- [ ] 删除 `deleteLink(fullShortUrl)` → 逻辑删除 + **删除缓存**
- [ ] 补全 W1D6 遗留的「分组内短链数量」统计（此时 `t_link` 有数据了，SQL 可以真实验证）
- [ ] 统计接口 `countLink(gid)`

缓存删除 + 延迟双删参考：

```java
private void deleteCacheAndDoubleDelete(String gid, String shortUri) {
    String key = RedisKeyConstant.gotoCache(defaultDomain, shortUri);
    stringRedisTemplate.delete(key);
    // 延迟双删：覆盖「读线程在删缓存后、更新DB前把旧值回填」的窗口
    CompletableFuture.runAsync(() -> {
        try {
            Thread.sleep(500);
            stringRedisTemplate.delete(key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }, delayedDeleteExecutor);   // 用自定义线程池，别用默认 ForkJoinPool
}
```

**验收**

1. 分页查询：3 个分组各 5 条短链，按 gid 筛选 → 每个分组返回 5 条
2. 关键词搜索 `baidu` → 只返回原始链接含 baidu 的记录
3. 排序 `orderTag=clickNum` → 按点击数降序
4. **SQL 注入测试**：`orderTag=create_time;DROP TABLE t_link` → 返回参数错误（被白名单拦截），表还在
5. 编辑短链有效期 → `redis-cli get` 该缓存 → key **不存在**（已删除）
6. 禁用短链 → 访问该短链 → 404；缓存中该 key 已被删除
7. 删除短链 → 分页列表不再出现；`SELECT * FROM t_link` 中 `del_flag=1`
8. 分组内短链数量接口返回真实数量

- [ ] 8 条全部通过

**常见坑（有一个大坑，必看）**

| 坑 | 说明 |
| --- | --- |
| **逻辑删除 + 唯一索引冲突（重点）** | `t_link` 有 `uk_gid_origin(gid, origin_url_hash)`。逻辑删除后记录仍在表里（`del_flag=1`），唯一索引仍被占用。用户删除短链后**再创建同一个长链会直接 `DuplicateKeyException`**。三种解法：① 删除时把 `origin_url_hash` 改写为 `hash + "_deleted_" + id`，腾出唯一索引位（**推荐，改动最小**）；② 该表改为物理删除（丢掉"删除可追溯"能力）；③ 唯一索引改为包含 `del_flag`（但 MySQL 唯一索引对多列含删除标记的处理很别扭，`del_flag=0` 有两条也不行）。第 1 周如果没想清楚，今天务必补上并在验收里加一条：**删除后重新创建同一长链必须成功** |
| 排序字段直接拼 SQL | `last("ORDER BY " + orderTag)` 是典型注入点，必须白名单 |
| 删除缓存失败 | Redis 抖动导致缓存未删，出现脏数据 | TTL 兜底；删除失败记日志告警，可用 `@Retryable` 重试一次 |
| 延迟双删用默认线程池 | `ForkJoinPool.commonPool` 被占满影响其他任务 | 自定义 `ThreadPoolExecutor` |
| 改了 gid 没同步 Set | 旧分组的 Set 里残留该后缀，新分组查重不到 | 编辑分组时同步维护两个 Set |

**产出**：短链管理的全部 DTO/VO、Service 方法、Controller 接口、`dao/mapper/LinkMapper.xml`（统计 SQL）

---

## 第 2 周 · D7 | 单测 + 压测基线 + 周验收

**目标**：拿到第一份压测数据（缓存命中 vs 未命中的对比），完成第 2 周总验收。

**任务清单**

- [ ] 补全 `ShortLinkServiceImplTest`：幂等、过期 404、禁用 404、短链不存在 404
- [ ] `RateLimitAspectTest`：超阈值抛异常
- [ ] 用 JMeter 建三个测试计划并保存到 `jmeter/` 目录：
  - [ ] `redirect-hit.jmx`：跳转（先请求一次预热缓存）
  - [ ] `redirect-miss.jmx`：跳转（先 `flushdb` 清缓存）
  - [ ] `create-concurrent.jmx`：并发创建同一长链
- [ ] 跑压测并**记录到 `docs/压测报告.md`**：

| 场景 | 线程数 × 循环 | QPS | 平均 RT | P99 | 错误率 |
| --- | --- | --- | --- | --- | --- |
| 跳转（缓存命中） | 200 × 100 | | | | |
| 跳转（缓存未命中） | 200 × 100 | | | | |
| 并发创建 | 100 × 1 | | | | |

**验收（第 2 周总验收，必须全部通过）**

```
1. 创建短链 → 浏览器 302 跳转成功
2. 同一长链 + 同一分组并发创建 100 次 → 数据库只有 1 条记录
3. 访问不存在的短链 → 404，且控制台无 SQL 输出（布隆拦截）
4. 第二次访问已存在的短链 → 控制台无 SQL 输出（缓存命中）
5. 1 分钟内请求创建接口 40 次 → 第 31 次起被限流
6. 删除短链后再创建同一长链 → 成功（验证唯一索引坑已解决）
7. 编辑/禁用/删除后 → 对应缓存 key 已被清理
8. 压测报告三个场景数据齐全，缓存命中 QPS 明显高于未命中
```

- [ ] 8 条全部通过
- [ ] 本周固定检查清单（同 W1D7）

**产出**：单测文件、`jmeter/*.jmx`、`docs/压测报告.md`（第一版）

---

# 第 3 周：统计 + 消息队列 + 定时任务

> 本周核心目标：**把访问数据流打通**——跳转时异步发消息 → 消费者落明细表 → Redis 实时计数 → 定时任务聚合成统计表 → 接口产出看板数据。
> 本周的技术亮点是「异步与可靠性」，重点是**消费幂等**和**消息不丢**这两个可验证的验收点。

---

## 第 3 周 · D1 | RabbitMQ 拓扑与配置

**目标**：应用启动后自动声明交换机、业务队列、死信交换机、死信队列，RabbitMQ 控制台能看到完整拓扑。

**任务清单**

- [ ] `MQConstant`：交换机名、队列名、routing key、死信相关常量
- [ ] `RabbitMQConfig`：
  - [ ] `TopicExchange` 业务交换机（durable）
  - [ ] `Queue` 业务队列（durable + 绑定死信参数）
  - [ ] `DirectExchange` 死信交换机（durable）
  - [ ] `Queue` 死信队列（durable）
  - [ ] 两组 `Binding`
  - [ ] `Jackson2JsonMessageConverter`（**必须配，否则消息体是二进制**）
  - [ ] `RabbitTemplate` 设置 `setMandatory(true)` 以触发 ReturnsCallback
- [ ] `application.yml` 补全 `spring.rabbitmq` 配置（主文档 [8.2](./short-link-开发文档.md#82-applicationyml-关键片段)）
- [ ] `AccessLogMessage` 消息体（含 `msgId`、`linkId`、`fullShortUrl`、`gid`、`shortUri`、`user`、`ip`、`browser`、`os`、`deviceType`、`locale`、`accessTime`）

```java
@Configuration
@RequiredArgsConstructor
public class RabbitMQConfig {

    @Bean
    public TopicExchange shortLinkExchange() {
        return ExchangeBuilder.topicExchange(MQConstant.EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue accessLogQueue() {
        return QueueBuilder.durable(MQConstant.ACCESS_LOG_QUEUE)
                .deadLetterExchange(MQConstant.DLX_EXCHANGE)
                .deadLetterRoutingKey(MQConstant.DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding accessLogBinding() {
        return BindingBuilder.bind(accessLogQueue()).to(shortLinkExchange())
                .with(MQConstant.ACCESS_LOG_ROUTING_KEY);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(MQConstant.DLX_EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(MQConstant.DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue()).to(dlxExchange()).with(MQConstant.DLX_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setCreateMessageIds(true);       // 自动带 messageId
        return converter;
    }
}
```

**验收**

- [ ] 启动应用，RabbitMQ 控制台（`http://localhost:15672` → Queues）能看到 `short-link.access-log.queue` 与 `short-link.access-log.dlq`
- [ ] 点开业务队列 → Features 列显示 `DLX`，Arguments 里有 `x-dead-letter-exchange`
- [ ] Exchange 页能看到 `short-link.topic` 与 `short-link.dlx` 两个交换机
- [ ] 用控制台的 Publish message 往业务队列发一条 JSON 测试消息 → 消息进入队列（数量 +1），先不消费（把 `@RabbitListener` 注释掉或暂不写）

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| Queue 与 Exchange 已存在但参数不同 | 启动报 `PRECONDITION_FAILED - inequivalent arg` | 去控制台删掉旧队列重建；开发期频繁改配置很正常 |
| 忘配 JSON 转换器 | 消息体是 Java 序列化二进制，控制台看不到内容 | 必须配 `Jackson2JsonMessageConverter` |
| `LocalDateTime` 序列化失败 | 消费端报 `Java 8 date/time type not supported` | 转换器里注册 `JavaTimeModule`，或消息体 `accessTime` 用 `String`/`Long`（**推荐用 Long 时间戳，最稳**） |
| 队列名/交换机名硬编码在 `@RabbitListener` 里 | 与 Config 不一致导致监听失败 | 全部引用 `MQConstant` |
| 手动 ACK 与自动 ACK 混用 | 报 `channel error` | yml 里 `acknowledge-mode: manual` 全局生效，别在注解上再写 |

**产出**：`common/constant/MQConstant.java`、`common/config/RabbitMQConfig.java`、`mq/message/AccessLogMessage.java`、yml 补充

---

## 第 3 周 · D2 | 生产者：跳转后异步发送

**目标**：每次跳转都产生一条消息进入队列，且发送失败不影响跳转（降级同步落库）。

**任务清单**

- [ ] `AccessLogProducer.send(AccessLogMessage msg)`：
  - [ ] 生成 `msgId`（`UUID.randomUUID().toString()`）
  - [ ] `rabbitTemplate.convertAndSend(exchange, routingKey, msg)`
- [ ] `RabbitMQConfig` 中补 `ConfirmCallback` 与 `ReturnsCallback`：
  - [ ] Confirm：`ack=true` 记 debug 日志；`ack=false` 记 error 日志（可落 DB 或本地表待补偿）
  - [ ] Returns：路由失败（队列不存在）时记 error 日志
- [ ] 改造 W2D4 的跳转链路第 5 步：把 `log.info` 换成「发 MQ + try-catch 降级」
- [ ] 采集逻辑 `AccessLogCollector.build(request, link)`：
  - [ ] IP：`IpUtil.getRealIp(request)`
  - [ ] UA：`UserAgentUtil.parse(ua)` → browser / os / deviceType
  - [ ] `user`：`DigestUtil.md5Hex(ip + "|" + userAgent)`（UV 去重键）
  - [ ] `locale`：先用 `request.getLocale()` 或 Accept-Language 粗略取值（不用第三方 IP 库，避免引入外部依赖）
  - [ ] `accessTime`：`System.currentTimeMillis()`

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class AccessLogProducer {

    private final RabbitTemplate rabbitTemplate;

    public void send(AccessLogMessage message) {
        if (message.getMsgId() == null) {
            message.setMsgId(UUID.randomUUID().toString());
        }
        try {
            rabbitTemplate.convertAndSend(
                    MQConstant.EXCHANGE,
                    MQConstant.ACCESS_LOG_ROUTING_KEY,
                    message,
                    msg -> {
                        // 消息级持久化
                        msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                        return msg;
                    });
        } catch (Exception e) {
            // 绝不能因为发消息失败影响跳转；降级为同步落库
            log.error("访问日志消息发送失败, 降级同步落库, msgId={}", message.getMsgId(), e);
            accessLogService.saveSync(message);
        }
    }
}
```

**验收**

1. 用浏览器访问一个短链 1 次 → RabbitMQ 控制台该队列 `Ready` 数量 +1
2. 点开队列 → `Get messages` → 能看到完整的 JSON 消息体，字段齐全（`msgId` / `ip` / `browser` / `os` / `deviceType`）
3. **降级验证**：把 RabbitMQ 服务停掉 → 访问短链 → 跳转**依然成功**（302 正常），日志中有「发送失败，降级同步落库」，`t_link_access_logs` 有数据
4. 重启 RabbitMQ，恢复异步发送

- [ ] 4 条通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 发消息没有 try-catch | MQ 挂了导致跳转 500，**这是最严重的问题** | 必须 try-catch 包裹，失败降级 |
| ConfirmCallback 不生效 | 回调从不触发 | 需要 `publisher-confirm-type: correlated`，且 `RabbitTemplate` 用的是配置了连接工厂的那个实例 |
| 消息未持久化 | MQ 重启后队列消息丢失 | `QueueBuilder.durable` + `MessageDeliveryMode.PERSISTENT` |
| UA 解析抛异常（某些畸形 UA） | 跳转链路报错 | UA 解析结果做空判断，异常时置为 `Unknown` |
| MQ 消费失败重试次数过多 | 队列堆积 | 交给死信队列，不配本地 retry 或限制 `max-attempts: 3` |

**产出**：`mq/producer/AccessLogProducer.java`、`service/AccessLogService.java`（先实现 `saveSync`）、`util/AccessLogCollector.java`

---

## 第 3 周 · D3 | 消费者：手动 ACK + 消费幂等 + 死信

**目标**：重复消息只落一条记录；处理失败的进死信队列；服务重启后消息不丢。

**任务清单**

- [ ] `TLinkAccessLogs` 实体 + `AccessLogMapper`
- [ ] `AccessLogConsumer`：
  - [ ] `@RabbitListener(queues = MQConstant.ACCESS_LOG_QUEUE)`
  - [ ] 方法签名带 `Channel channel` 与 `@Header(AmqpHeaders.DELIVERY_TAG) long tag`
  - [ ] 业务成功 → `basicAck`
  - [ ] 异常 → `basicNack(tag, false, false)`（requeue=false，进死信）
- [ ] `AccessLogService.saveIdempotent(msg)`：
  - [ ] 插入 `t_link_access_logs`，依赖 `uk_msg_id` 唯一索引
  - [ ] 捕获 `DuplicateKeyException` → debug 日志 → **当作成功**（直接 ACK）
- [ ] `AccessLogService.saveSync(msg)`（生产端降级用，同样是上面这个方法的复用）
- [ ] 增加 `t_link` 的 `click_num` 累加（可选放在消费者里做，`UPDATE t_link SET click_num = click_num + 1 WHERE id = ?`）

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessLogConsumer {

    private final AccessLogService accessLogService;

    @RabbitListener(queues = MQConstant.ACCESS_LOG_QUEUE)
    public void consume(AccessLogMessage message,
                        Channel channel,
                        @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            accessLogService.saveIdempotent(message);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("访问日志落库失败, 进入死信队列, msgId={}", message.getMsgId(), e);
            // requeue=false：避免无限重试打爆队列
            channel.basicNack(tag, false, false);
        }
    }
}
```

```java
@Override
@Transactional(rollbackFor = Exception.class)
public void saveIdempotent(AccessLogMessage msg) {
    try {
        accessLogMapper.insert(convert(msg));
    } catch (DuplicateKeyException e) {
        // MQ 至少投递一次，重复消息靠 uk_msg_id 唯一索引保证幂等
        log.debug("重复消息已忽略, msgId={}", msg.getMsgId());
    }
}
```

**验收（本周最关键的验收，三条都要做）**

1. **幂等验证**：用 RabbitMQ 控制台，把同一条 JSON 消息（`msgId` 不变）**连续 Publish 2 次**
   - [ ] `SELECT COUNT(*) FROM t_link_access_logs WHERE msg_id = 'xxx'` 结果为 **1**
   - [ ] 无异常抛出，两批消息都被 ACK（队列 Ready 归零）
2. **死信验证**：
   - [ ] 临时在 `saveIdempotent` 里抛一个 RuntimeException（或临时把 `t_link_access_logs` 表改名制造 SQL 异常）
   - [ ] 发一条消息 → 业务队列消息消失，`short-link.access-log.dlq` 数量 +1
   - [ ] 点开死信队列，能看到 `x-death` 头信息，包含 `reason: rejected`
   - [ ] 恢复代码
3. **消息不丢验证**：
   - [ ] 停掉应用 → 用控制台发 5 条消息（队列 Ready = 5）→ 启动应用 → 消息被消费完，明细表 +5，队列归零
4. **性能验证**：用 JMeter 压 1000 次跳转 → 明细表记录数 == 1000（考虑跳转次数），无消费失败

- [ ] 4 条通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 捕获异常后又 ACK | 数据丢失且无人知晓 | 只有业务成功才 ACK，异常必须 Nack 进死信 |
| `basicNack(requeue=true)` | 失败消息反复回队，CPU 打满 | 固定用 `false`，重试交给死信队列 |
| 消费方法里自己 try-catch 吞掉异常 | 死信队列永远收不到消息，问题被隐藏 | 只让「重复消息」被吞，「写库失败」必须抛出去 |
| 消费端 `@Transactional` 与 ACK 顺序 | 事务未提交就 ACK，提交失败数据丢失 | 事务提交成功后再 ACK（Spring 里方法返回即表示事务已提交，注意 AOP 顺序） |
| 手动 ACK 下 Channel 拿不到 | 方法参数签名不对 | 参数必须是 `com.rabbitmq.client.Channel`（不是 `io.netty` 之类） |
| 死信队列没有消费者 | 死信堆积无人处理 | 本项目死信只做「人工排查 + 手动重放」，文档里说明即可；有余力再加定时重放任务 |

**产出**：`entity/TLinkAccessLogs.java`、`dao/AccessLogMapper.java`、`mq/consumer/AccessLogConsumer.java`、`AccessLogServiceImpl`

---

## 第 3 周 · D4 | 实时统计：Redis 计数接入

**目标**：每次跳转后，Redis 中出现 PV / UV / UIP / 维度 Hash / 小时分布五类 key，且数值正确。

**任务清单**

- [ ] `ShortLinkStatsService.recordAccess(AccessLogMessage msg)`：
  - [ ] PV：`INCR RedisKeyConstant.statsPv(today, fullShortUrl)`
  - [ ] UV：`PFADD RedisKeyConstant.statsUv(today, fullShortUrl) msg.getUser()`
  - [ ] UIP：`PFADD RedisKeyConstant.statsUip(today, fullShortUrl) msg.getIp()`
  - [ ] 浏览器：`HINCRBY RedisKeyConstant.statsDimension("browser", today, url) browser 1`
  - [ ] OS / 设备 / 地区同理
  - [ ] 小时：`HINCRBY RedisKeyConstant.statsHour(today, url) String.valueOf(hour) 1`
  - [ ] 所有 key 统一 `EXPIRE 2 天`
- [ ] 在跳转链路中调用（放在 `restoreUrl` 内、返回 302 之前）
- [ ] 统一封装 key 的 TTL 设置，避免每个方法都忘了 expire
- [ ] （可选优化）用 `Pipeline` 把 8 次 Redis 调用合并为 1 次 RTT，**测出优化前后 RT 差异并记录**（这是很好的简历素材）

```java
@Override
public void recordAccess(AccessLogMessage msg) {
    String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    String url = msg.getFullShortUrl();
    int hour = LocalDateTime.now().getHour();

    // 用 Pipeline 减少 RTT：把多次 Redis 调用合并成一次网络往返
    stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
        StringRedisSerializer s = new StringRedisSerializer();

        byte[] pvKey = s.serialize(RedisKeyConstant.statsPv(date, url));
        connection.stringCommands().incr(pvKey);
        connection.keyCommands().expire(pvKey, Duration.ofDays(2).getSeconds());

        byte[] uvKey = s.serialize(RedisKeyConstant.statsUv(date, url));
        connection.hyperLogLogCommands().pfAdd(uvKey, s.serialize(msg.getUser()));
        connection.keyCommands().expire(uvKey, Duration.ofDays(2).getSeconds());

        byte[] uipKey = s.serialize(RedisKeyConstant.statsUip(date, url));
        connection.hyperLogLogCommands().pfAdd(uipKey, s.serialize(msg.getIp()));
        connection.keyCommands().expire(uipKey, Duration.ofDays(2).getSeconds());

        byte[] browserKey = s.serialize(RedisKeyConstant.statsDimension("browser", date, url));
        connection.hashCommands().hIncrBy(browserKey, s.serialize(msg.getBrowser()), 1);
        connection.keyCommands().expire(browserKey, Duration.ofDays(2).getSeconds());

        byte[] hourKey = s.serialize(RedisKeyConstant.statsHour(date, url));
        connection.hashCommands().hIncrBy(hourKey, s.serialize(String.valueOf(hour)), 1);
        connection.keyCommands().expire(hourKey, Duration.ofDays(2).getSeconds());
        return null;
    });
}
```

**验收**

1. 先 `redis-cli flushdb`（清掉缓存，确认后续统计值从 0 开始），再访问某短链 **5 次**（同一浏览器同一 IP）
   - [ ] `GET short-link:stats:pv:2026-09-16:{fullShortUrl}` → `5`
   - [ ] `PFCOUNT short-link:stats:uv:2026-09-16:{fullShortUrl}` → `1`（同 IP + 同 UA 视为同一访客）
   - [ ] `PFCOUNT short-link:stats:uip:...` → `1`
   - [ ] `HGETALL short-link:stats:browser:...` → `{Chrome: 5}`（或你实际用的浏览器）
   - [ ] `HGETALL short-link:stats:hour:...` → 当前小时字段值为 5
2. 换一个浏览器访问 3 次（UA 不同、IP 相同）
   - [ ] UV → `2`（UA 变了，user 标识变了）
   - [ ] UIP → `1`（IP 没变）
   - [ ] browser Hash 里多出一个新浏览器，计数为 3
3. 所有统计 key 的 `TTL` 都在 2 天以内

- [ ] 3 条通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| `PFADD` 参数写错 | 编译不过 | `opsForHyperLogLog().add(key, values...)` 或 Pipeline 里用 `pfAdd` |
| Pipeline 里返回值 | `executePipelined` 返回 List，元素可能为 null | 不关心返回值时直接 `return null` |
| 统计代码抛异常影响跳转 | 跳转 500 | 统计也包在 try-catch 中，失败只记日志 |
| 忘记设 TTL | Redis 内存持续增长 | 所有统计 key 统一 2 天过期；也可在定时任务里兜底清理 |
| 用 `LocalDate.now()` 但统计时间跨天 | 23:59 访问算到当天，00:01 算到次日，符合预期 | 定时任务的覆盖写入要能正确处理跨天边界 |
| 命中率验证不严谨 | 分不清 PV 是缓存命中还是 DB 查询产生的 | 统计放在**返回 302 之前**，无论缓存是否命中都计数（这是正确的） |

**产出**：`service/ShortLinkStatsService.java` + 实现

---

## 第 3 周 · D5 | 定时任务：Redis 统计聚合落库

**目标**：统计表有正确数据；任务重复执行**数据不翻倍**（幂等）；多实例只有一个执行。

**任务清单**

- [ ] `t_link_access_stats` / `*_hour_stats` / `*_browser_stats` 等 6 张统计表的实体 + Mapper
- [ ] `LinkMapper.xml` 中手写 `upsert` SQL（`INSERT ... ON DUPLICATE KEY UPDATE`）
- [ ] `ShortLinkStatsJob.aggregateToday()`：
  - [ ] `@Scheduled(cron = "0 0 * * * ?")` 每小时执行
  - [ ] Redisson 加锁 `lockJob("aggregate-stats")`，未抢到直接 return
  - [ ] 取出「当天有统计 key 的短链列表」（用 `SCAN` + 前缀匹配，**不要用 KEYS**）
  - [ ] 对每条短链：读 Redis 全部指标 → upsert 到 6 张表
  - [ ] 释放锁（判断持有者）
- [ ] `ShortLinkStatsJob.dailyCleanup()`：`@Scheduled(cron = "0 0 3 * * ?")` 每天凌晨 3 点
  - [ ] 清理 90 天前的 `t_link_access_logs`
  - [ ] 清理过期的统计 Redis key（TTL 已自动清理，这里做兜底）
- [ ] `@Scheduled` 线程池配置（`ThreadPoolTaskScheduler`，池大小 2），避免任务互相阻塞
- [ ] 手动触发方法（加一个 `@PostMapping("/stats/job/trigger")` 仅用于测试，第 4 周上线前删掉或加开关）

`upsert` SQL 参考：

```xml
<insert id="upsertAccessStats">
    INSERT INTO t_link_access_stats (full_short_url, date, pv, uv, uip)
    VALUES (#{fullShortUrl}, #{date}, #{pv}, #{uv}, #{uip})
    ON DUPLICATE KEY UPDATE
        pv = VALUES(pv),
        uv = VALUES(uv),
        uip = VALUES(uip),
        update_time = NOW()
</insert>
```

**验收**

1. 访问某短链 10 次 → 手动触发聚合接口
   - [ ] `SELECT * FROM t_link_access_stats WHERE full_short_url = 'xxx'` → `pv=10, uv=1, uip=1`
   - [ ] `t_link_browser_stats` 有对应浏览器记录，`cnt=10`
   - [ ] `t_link_access_hour_stats` 当前小时列为 10
2. **幂等验证（关键）**：再**连续触发两次**聚合
   - [ ] 统计表数值**仍然是 10**，没有变成 20 或 30
3. 再访问 5 次 → 触发聚合 → `pv=15`（累加正确）
4. 跨天验证（可选，改系统时间或手动构造昨天的 key）：昨天的数据被写入 `date=昨天` 的行，今天的不受影响
5. 清空 `t_link_access_logs` 中的旧数据，执行清理任务 → 只删 90 天前的记录

- [ ] 5 条通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 用 `KEYS short-link:stats:*` 扫描 | 数据量大时阻塞 Redis（生产事故级） | 用 `SCAN` 游标遍历 |
| 忘了分布式锁 | 多实例/多线程重复执行 | Redisson 锁 + 判断持有者；本项目单机也要写，面试时讲"为多实例预留" |
| 累加而不是覆盖 | 任务重跑数据翻倍 | 统一 `ON DUPLICATE KEY UPDATE` 覆盖写；**HLL 不支持取增量，只能覆盖** |
| `@Scheduled` 默认单线程 | 一个任务卡住导致其他任务不执行 | 配置 `ThreadPoolTaskScheduler` |
| 跨天时读到了昨天的 key | 统计错位 | 聚合方法显式传入日期参数（默认今天），不要依赖「当前时间」隐式推断 |
| 定时任务异常中断 | 后续链表未处理 | 单条短链的异常 try-catch 隔离，不让一条失败影响整批 |

**产出**：6 个统计实体 + Mapper + XML、`job/ShortLinkStatsJob.java`、`common/config/ScheduleConfig.java`

---

## 第 3 周 · D6 | 统计查询接口（供前端看板使用）

**目标**：接口返回的数据结构能直接喂给 ECharts，且**今日实时数据（Redis）+ 历史数据（DB）正确合并**。

**任务清单**

- [ ] `ShortLinkStatsService` 查询方法：
  - [ ] `getGroupStats(fullShortUrl, gid, startDate, endDate)`：汇总 `pv/uv/uip` + 24 小时分布 + 按天趋势
  - [ ] `getDimensionStats(fullShortUrl, dimension, startDate, endDate)`：浏览器/OS/设备/地区占比
  - [ ] `pageAccessRecords(...)`：访问明细分页（含 IP、浏览器、时间等）
- [ ] **今日数据合并逻辑（重点）**：
  - [ ] 查询范围包含今天时，今天的数据从 Redis 实时读取（`PV = GET`、`UV = PFCOUNT`、小时分布 = `HGETALL`）
  - [ ] 历史日期（< 今天）从统计表读
  - [ ] 合并成一个统一的时间序列返回
- [ ] `gid=all` 支持：不传 `fullShortUrl` 时按当前用户所有分组聚合
- [ ] 返回 VO 设计（示例）：

```json
{
  "pv": 1234, "uv": 567, "uip": 480,
  "hourStats": [0, 3, 12, 45, "... 共 24 个值"],
  "daily": [{"date": "2026-09-14", "pv": 300, "uv": 120}, {"date": "2026-09-15", "pv": 400, "uv": 150}],
  "browserStats": [{"name": "Chrome", "value": 800}, {"name": "Edge", "value": 300}]
}
```

- [ ] 时间范围校验：最大跨度限制（如 90 天），防止一次查全表

**验收**

1. 今天访问短链 20 次 → 调用汇总接口
   - [ ] `pv=20`（**注意：此时数据库统计表可能还没有今天的记录，数值必须由 Redis 提供**）
2. 触发聚合任务后再调一次 → `pv` 仍然是 20（不是 40）← **这是最容易出 bug 的地方，必须验证**
3. `hourStats` 长度为 24，当前小时位置的值 = 实际访问次数
4. 查昨天到今天两天的趋势 → `daily` 返回两条数据，昨天来自 DB，今天来自 Redis
5. 浏览器维度接口返回的 `value` 之和 == 总 PV
6. `gid=all` 返回该用户所有短链的汇总
7. 明细分页返回字段齐全，倒序按时间排列
8. 查 200 天范围 → 返回参数错误（超过最大跨度）

- [ ] 8 条通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 今天数据 DB 和 Redis 重复累加 | PV 翻倍 | 明确规则：**今天只读 Redis，历史只读 DB**，日期边界用 `isToday()` 判断 |
| 聚合任务执行后今天的数据同时在 DB 和 Redis | 合并逻辑混乱 | 判断条件始终以「日期是否等于今天」为准，与任务是否执行无关 |
| `PFCOUNT` 跨多 key 求并集 | 结果偏差 | `PFCOUNT key1 key2 ...` 是求并集基数（不是相加），**要多天合并时不能相加**；本项目按天分别统计，不做跨天 UV 合并（文档说明：UV 是「日独立访客」，跨天不累加） |
| 大数据量查询慢 | 接口超时 | 统计表加 `(full_short_url, date)` 索引；明细分页强制走 `idx_full_short_url_time` |
| 时间参数格式不统一 | 前端传 `2026-09-16` 后端要 `2026-09-16 00:00:00` | 统一用 `@DateTimeFormat(pattern = "yyyy-MM-dd")` 接收 `LocalDate` |

**产出**：统计相关 DTO/VO、`ShortLinkStatsController.java`

---

## 第 3 周 · D7 | 全链路自测 + 一致性校验 + 周验收

**目标**：证明统计数据的准确性（可写进简历的"数据一致性"）、MQ 可靠性，完成第 3 周总验收。

**任务清单**

- [ ] 写校验脚本 `sql/verify.sql`（或用接口 + SQL 手动对比）：

```sql
-- 校验：明细表条数 vs 统计表 PV
SELECT
    l.full_short_url,
    COUNT(*) AS log_count,
    s.pv AS stats_pv,
    (COUNT(*) - s.pv) AS diff
FROM t_link_access_logs l
LEFT JOIN t_link_access_stats s
    ON s.full_short_url = l.full_short_url
    AND s.date = DATE(l.access_time)
WHERE l.del_flag = 0
GROUP BY l.full_short_url, s.pv
HAVING diff <> 0;
-- 期望：查询结果为空
```

- [ ] 全链路演示走一遍并录屏（第 4 周做演示视频可复用）
- [ ] 更新 `docs/进度.md`，记录本周踩到的坑（**「HLL 无法取增量」和「消费幂等」是很好的面试素材**）

**验收（第 3 周总验收，必须全部通过）**

```
1. 消费者停掉，跳转 5 次 → 队列堆积 5 条 → 启动消费者 → 明细表 +5，队列归零
2. 同一条消息重复投递 2 次 → 明细表只有 1 条（uk_msg_id 生效）
3. 制造消费异常 → 消息进入死信队列，`x-death` 头可见
4. 跳转 100 次 → 聚合任务执行 → 统计表 pv=100；再次执行任务 → 仍是 100
5. 统计接口返回的 PV == 明细表条数 == Redis PV 计数（三者一致）
6. 校验 SQL 查询结果为空
7. MQ 停掉后跳转依然 302 成功（降级同步落库生效）
```

- [ ] 7 条全部通过
- [ ] 本周固定检查清单（同 W1D7）

**产出**：`sql/verify.sql`、`docs/进度.md` 更新、全链路录屏

---

# 第 4 周：前端 + 压测 + 部署 + 简历

> 本周核心目标：**把项目变成「能给别人看、能写进简历」的成品**。
> 技术工作只占一半，另一半是压测调优拿到真实数据、部署上线、把项目讲清楚。
> 本周所有工作都可以砍，但**压测报告 + 部署 + 简历定稿这三件事不能砍**。

---

## 第 4 周 · D1 | Vue3 前端工程搭建

**目标**：前端工程能启动，能通过代理调通后端接口，登录后能跳到主页面。

**任务清单**

- [ ] 创建工程：`npm create vite@latest short-link-web -- --template vue`
- [ ] 安装依赖：`vue-router`、`pinia`、`axios`、`element-plus`、`echarts`、`dayjs`
- [ ] 目录结构：

```
src/
├── api/            # 按模块拆：user.js / group.js / link.js / stats.js
├── assets/
├── components/
├── router/index.js # 路由 + 登录守卫
├── stores/         # pinia：user store（token、用户信息）
├── utils/request.js# axios 封装
├── views/          # Login.vue / Layout.vue / LinkList.vue / GroupList.vue / Dashboard.vue
└── main.js
```

- [ ] `utils/request.js`：
  - [ ] `baseURL = '/api'`（交给 Vite 代理）
  - [ ] 请求拦截器：自动带上 `Authorization: token值`
  - [ ] 响应拦截器：`code !== '0'` 时 `ElMessage.error(message)`；`401/未登录` 时跳登录页
- [ ] `vite.config.js` 配置代理解决跨域：

```js
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8000',
        changeOrigin: true
      }
    }
  }
})
```

- [ ] 路由 + 登录守卫：`/login` 免登录，其余路由跳转前校验 pinia 中的 token

**验收**

- [ ] `npm run dev` 启动，浏览器打开 `http://localhost:5173` 无报错
- [ ] 控制台执行一次代理请求（如登录接口）→ Network 中请求地址是 `localhost:5173/api/...`，返回正常数据（**没有 CORS 报错**）
- [ ] 未登录直接访问 `/link` → 被重定向到 `/login`

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 后端 CORS 报错 | `has been blocked by CORS policy` | 开发期用 Vite 代理（不用后端配 CORS）；上线用 Nginx 同源反代，**两种环境都不需要写 CORS 配置** |
| token 请求头名不匹配 | 后端一直返回未登录 | 后端 `sa-token.token-name` 与前端请求头名必须完全一致（建议都叫 `Authorization`） |
| 路由 history 模式刷新 404 | 部署后刷新页面 404 | Nginx 配 `try_files $uri $uri/ /index.html`（W4D6 会做） |
| Element Plus 样式丢失 | 组件没样式 | 按需引入或全量引入要配置正确，别只引组件不引样式 |

**产出**：`short-link-web/` 工程骨架、`request.js`、`router/index.js`、`stores/user.js`

---

## 第 4 周 · D2 | 登录注册页 + 短链管理页

**目标**：能从页面完成「注册 → 登录 → 创建短链 → 查看列表 → 编辑 → 删除」全流程。

**任务清单**

- [ ] `Login.vue`：登录/注册切换（Tab），表单校验（用户名 4~20 位、密码 6~20 位），登录成功存 token 并跳转
- [ ] `Layout.vue`：侧边栏菜单（短链管理 / 分组管理 / 数据看板）+ 顶栏（用户名、退出登录）
- [ ] `LinkList.vue`：
  - [ ] 顶部筛选：分组下拉、关键词搜索、排序方式
  - [ ] 表格列：短链（带**一键复制**按钮）、原始链接（超长省略 + Tooltip）、点击次数、有效期、状态、操作
  - [ ] 创建短链弹窗（Dialog）：
    - [ ] 原始链接（必填 + 正则校验）
    - [ ] 所属分组（下拉）
    - [ ] 有效期类型（单选：永久 / 自定义）+ 日期时间选择器
    - [ ] 自定义后缀（选填，6 位以内字母数字）
    - [ ] 备注（选填）
    - [ ] 提交后展示结果短链 + 复制按钮
  - [ ] 操作列：编辑（改描述/有效期/分组）、启用禁用开关、删除（二次确认）
  - [ ] 分页组件
- [ ] `GroupList.vue`：分组列表（名称、短链数量）、新建、重命名、删除
- [ ] 通用：`ElMessage` 成功/失败提示、按钮 loading 防重复点击

**验收**

1. 页面上注册一个新用户 → 登录 → 进入主页面
2. 新建分组「演示」→ 创建短链（原始链接填 `https://www.baidu.com`，选自定义有效期）→ 列表出现该记录
3. 复制按钮 → 剪贴板拿到完整短链 → 浏览器新标签打开 → **跳转成功**
4. 编辑该短链的备注 → 列表显示新备注
5. 禁用开关 → 关闭后访问短链 → 404；打开后恢复
6. 删除 → 列表消失
7. 提交一个非法原始链接（如 `abc`）→ 前端提示格式错误，**不发请求**

- [ ] 7 条通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 复制功能用 `document.execCommand` | 新版浏览器废弃警告 | 用 `navigator.clipboard.writeText()`，http 环境下可能受限（localhost 可用），兜底用输入框选中复制 |
| 日期格式不一致 | 后端接收失败 | 前端统一 `dayjs().format('YYYY-MM-DD HH:mm:ss')`，与后端 DTO 的 `@JsonFormat` 对齐 |
| 短链 Base62 大小写被转小写 | 复制后跳转 404 | 复制时不要做任何大小写转换；表格里也别加 CSS `text-transform` |
| 表单弹窗关闭后数据残留 | 二次打开有上次内容 | 关闭时 `resetFields()` |

**产出**：`Login.vue`、`Layout.vue`、`LinkList.vue`、`GroupList.vue`、`api/*.js`

---

## 第 4 周 · D3 | 数据看板（ECharts）

**目标**：看板图表数据全部来自真实接口，且与数据库/Redis 中的数值一致。

**任务清单**

- [ ] `Dashboard.vue` 布局：
  - [ ] 顶部 4 个指标卡片：PV（访问量）、UV（独立访客）、UIP（独立 IP）、短链总数
  - [ ] 筛选：分组下拉 + 时间范围选择（最近 7 天 / 30 天 / 自定义）；选择**单条短链**或「全部分组」
  - [ ] 折线图：近 N 天 PV/UV 趋势（双系列）
  - [ ] 柱状图：24 小时访问分布
  - [ ] 饼图：浏览器占比 / 操作系统占比（两个饼图并排，或用 Tab 切换）
  - [ ] 表格：最近访问明细（时间、IP、浏览器、OS、设备）
- [ ] ECharts 按需引入 + `resize` 监听（窗口缩放自适应）
- [ ] 数据为空时显示空状态（`ElEmpty`），不要显示空白图表

**验收**

1. 先在浏览器访问某些短链若干次（如 20 次，换 2 个浏览器）
2. 打开看板 → 卡片 PV = 实际访问次数；UV ≥ 2（换了浏览器）
3. 24 小时柱状图 → 当前小时有柱子
4. 浏览器饼图 → 显示两个浏览器及占比
5. **数据一致性**：卡片 PV 数 == `SELECT SUM(pv) FROM t_link_access_stats WHERE full_short_url='xxx'` + 今日 Redis PV
6. 切换「全部分组」→ 数据变为所有短链的汇总
7. 缩放浏览器窗口 → 图表自适应，不变形

- [ ] 7 条通过
- [ ] 截图保存到 `docs/screenshots/`（写 README 和演示视频要用）

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 图表容器高度为 0 | 图表不显示 | 给图表容器显式设置 `height`（如 `height: 320px`） |
| ECharts 重复初始化 | 内存泄漏、图重叠 | 用 `shallowRef` 存实例，`onUnmounted` 时 `dispose()` |
| 时间筛选传参格式 | 后端接收为 null | 统一传 `YYYY-MM-DD` 字符串 |
| 今日数据比看板少 | 聚合任务未执行 | 后端已做「今天读 Redis」逻辑（W3D6），若不一致先查后端接口原始返回，再查前端数据处理 |

**产出**：`Dashboard.vue`

---

## 第 4 周 · D4 | 前后端联调与体验打磨

**目标**：从注册到看板的完整流程无报错、无卡顿、提示友好。

**任务清单**

- [ ] 逐接口核对：前端调用参数与后端 DTO 字段名**完全一致**（这是联调 80% 的问题来源）
- [ ] 统一错误处理：后端返回的 `message` 直接展示给用户；网络异常提示「网络异常，请稍后重试」
- [ ] 加载状态：所有表格/图表请求期间显示 loading
- [ ] 空数据状态：列表、图表、明细表都要有
- [ ] 表单校验：必填、长度、格式（URL、自定义后缀字符集）
- [ ] 防重复提交：提交按钮 loading + 禁用
- [ ] 体验细节：创建成功后自动刷新列表；删除后回到合理页码（删完最后一页要回退页码）
- [ ] 浏览器兼容性快速检查：Chrome + Edge 各走一遍

**验收**

- [ ] 完整流程走 3 遍：注册 → 登录 → 建分组 → 建短链 → 跳转 → 看板 → 编辑 → 禁用 → 删除
- [ ] 每遍都没有控制台报错（F12 Console 无红色）
- [ ] Network 中无 4xx/5xx（除故意测试的场景）
- [ ] 断网后再操作 → 提示友好，不是白屏

**常见坑**

| 坑 | 说明 |
| --- | --- |
| 字段名不一致（前后端各写各的） | 统一以 Knife4j 文档为准，前端照文档写 |
| 后端返回 `code` 是字符串 `"0"`，前端用 `=== 0` 判断 | 约定好类型，前后端都用字符串或都用数字 |
| 分页参数名不同（`current` vs `page`） | 后端用 `current/size`，前端照抄 |
| 异常导致白屏 | 加 `app.config.errorHandler` 兜底 |

**产出**：联调修复提交、`docs/screenshots/` 补充

---

## 第 4 周 · D5 | 压测与性能调优（拿到简历数据）

**目标**：产出正式的压测报告，包含**优化前 vs 优化后**的对比数据。

**任务清单**

- [ ] 完善 JMeter 测试计划（每个计划都要有 `聚合报告` 和 `响应时间图` 监听器）：
  - [ ] `redirect-hit.jmx`：跳转 - 缓存命中（200 线程 × 100 循环，加 60 秒 ramp-up）
  - [ ] `redirect-miss.jmx`：跳转 - 缓存未命中（同样的线程配置，先 flushdb）
  - [ ] `create-concurrent.jmx`：并发创建同一长链（100 线程 × 1）
  - [ ] `create-rate-limit.jmx`：单用户高频创建（验证限流阈值）
- [ ] 记录**基准数据**（优化前）：把 Redis 缓存逻辑临时关掉（或直接压 DB 查询接口）拿一组数
- [ ] 逐项调优并**每调一项记录一次数据**：

| 调优项 | 现状 | 调整 | 预期效果 |
| --- | --- | --- | --- |
| 缓存开关 | 无缓存直查 DB | 加 Redis 缓存 | QPS 大幅提升（**核心对比数据**） |
| Hikari 连接池 | 默认 10 | 调到 20 | DB 并发能力提升 |
| Redis 统计写法 | 8 次独立命令 | Pipeline 合并 | RT 降低 |
| Tomcat 线程数 | 默认 200 | 视压测结果调整 | 高并发下减少排队 |
| 日志级别 | debug | info/warn | 减少磁盘 IO 与字符串拼接 |
| JVM 参数 | 默认 | G1 + 合理堆大小 | 减少 Full GC |
| MyBatis 日志 | 打印 SQL | 关闭 | 减少 IO（**压测时必须关**） |

- [ ] 把结果填进 `docs/压测报告.md`，包含：环境、工具、场景、参数、结果表、瓶颈分析、调优前后对比
- [ ] 压测过程中用 `jvisualvm` 或 `jconsole` 观察 GC 与线程（可选，但**面试讲起来有说服力**）

**验收**

- [ ] 压测报告包含至少 3 个场景的真实数据（QPS / 平均 RT / P99 / 错误率）
- [ ] 有明确的「优化前 vs 优化后」对比（如缓存带来的提升倍数）
- [ ] 能说出瓶颈在哪里（如：缓存命中场景下瓶颈在 Redis 网络 RTT；未命中场景瓶颈在 DB 连接池）
- [ ] 压测时错误率 < 1%

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 压测时开着 SQL 日志 | QPS 极低，数据不可用 | 压测前必关 MyBatis 日志，把 root 日志级别调到 warn |
| JMeter 自身成为瓶颈 | 压测机 CPU 打满 | JMeter 用 GUI 时不要跑大并发，用命令行 `jmeter -n -t x.jmx -l result.jtl` |
| 没用 ramp-up | 瞬间 200 线程导致连接被拒 | 设置 ramp-up 60 秒 |
| 数据不真实 | 只用 1 条短链压 | 准备 1000 条短链，用 CSV 参数化随机请求（更真实） |
| 忽略了限流 | 压测被自己写的限流拦住，错误率飙升 | 压测前临时调高阈值（或用内网 IP 白名单），压完改回 |

**产出**：`jmeter/*.jmx`、`docs/压测报告.md`（完整版）

---

## 第 4 周 · D6 | Windows 服务器部署

**目标**：服务器上通过 IP（或域名）能完整访问系统，重启服务器后所有服务自动拉起。

**任务清单**

- [ ] 服务器基础环境：安装 JDK17、Git（可选）
- [ ] MySQL：安装 + 建库 + 导入 `schema.sql` + 限制内存参数
- [ ] Redis：安装服务 + 改密码 + `maxmemory 256mb` + `volatile-lru`
- [ ] RabbitMQ：装 Erlang + RabbitMQ + 启用管理插件 + **删除 guest 用户** + 建业务用户
- [ ] 后端：`mvn clean package -DskipTests` → 上传 jar → 写 `application-prod.yml` → **WinSW 注册为 Windows 服务**
- [ ] 前端：`npm run build` → 上传 `dist` → Nginx 配置（静态托管 + `/api` 反代 + `try_files`）
- [ ] Nginx 也用 WinSW 注册为服务（或至少做成开机自启）
- [ ] 防火墙放行 80 端口；**封禁 3306 / 6379 / 5672 / 15672 的公网访问**
- [ ] 把 `short-link.domain` 配置改成服务器真实地址，**历史短链的 `full_short_url` 需要批量更新**（见下方坑）
- [ ] 冒烟测试（见下方清单）

**冒烟测试清单（部署后必做）**

```
1. 浏览器访问 http://服务器IP/ → 前端页面正常加载
2. 注册 + 登录 → 成功
3. 创建短链 → 返回 http://服务器IP/xxx/xxxxxx 格式
4. 新标签打开该短链 → 302 跳转成功
5. 访问不存在的短链 → 404 页面
6. 看板 → 有刚才的访问数据（PV ≥ 1）
7. 打开 http://服务器IP/doc.html → 接口文档可用（如不想暴露可关闭）
8. 重启服务器 → 等 2 分钟 → 上述 1~6 全部仍可用（验证开机自启）
9. 查看各服务内存占用，确认总内存没有超过物理内存
```

- [ ] 9 条全部通过

**常见坑**

| 坑 | 现象 | 解决 |
| --- | --- | --- |
| 2G 内存 OOM | 进程被系统杀掉，服务时好时坏 | 严格按主文档 [11.1](./short-link-开发文档.md#111-资源规划2-核-2g非常紧张必须限制内存) 限制内存；必要时把 RabbitMQ 换成 Redis Stream |
| 代码里写死了 `localhost:8000` 作为短链域名 | 生成的短链指向 localhost，别人打不开 | 域名走配置项 `short-link.domain`，部署时改为服务器 IP |
| 历史数据域名没更新 | 老短链仍是 localhost | 执行 `UPDATE t_link SET full_short_url = REPLACE(full_short_url, 'http://localhost:8000', 'http://服务器IP');` 并清理旧缓存 |
| Nginx 反代后 IP 全是 127.0.0.1 | 统计和限流失真 | 必配 `X-Real-IP` / `X-Forwarded-For` |
| Redis 未设密码且对公网开放 | 被挖矿（真实高发事故） | `requirepass` + `bind 127.0.0.1` + 防火墙禁止 6379 |
| 时间不对 | 统计日期错乱 | 启动参数加 `-Duser.timezone=Asia/Shanghai`，并确认服务器时区 |
| 服务没设开机自启 | 服务器重启后系统挂了 | 全部用 Windows 服务方式注册，`Set-Service -StartupType Automatic` |

**产出**：服务器可访问的系统、`docs/部署手册.md`（把实际命令记下来，面试可能问）

---

## 第 4 周 · D7 | 收尾：README、简历、演示视频

**目标**：项目对外呈现完整，能在 3 分钟内讲清楚并演示。

**任务清单**

- [ ] `README.md`：
  - [ ] 项目简介 + 在线访问地址（如果服务器还在）
  - [ ] 技术栈表格
  - [ ] 架构图（从主文档复制 mermaid）
  - [ ] 核心功能截图（从 `docs/screenshots/` 选 5~6 张）
  - [ ] 本地启动步骤（三分钟能跑起来）
  - [ ] 压测数据摘要
  - [ ] 目录结构说明
- [ ] 代码清理：
  - [ ] 删除测试用的临时接口（如手动触发聚合的接口加开关或删除）
  - [ ] 删除无用的 `System.out.println`
  - [ ] 确认没有把密码、服务器 IP 提交到 Git
  - [ ] 补充关键代码注释（尤其是并发、缓存、幂等相关逻辑，面试官可能直接看代码）
- [ ] 简历描述定稿（用主文档 [13.1](./short-link-开发文档.md#131-简历写法可直接改数字使用) 的模板，**把括号里的 xx 换成真实压测数据**）
- [ ] 面试稿自测：把主文档 [13.2 的 15 个问题](./short-link-开发文档.md#132-高频面试问答提前背熟) 逐条口头讲一遍，**录下来回听**
- [ ] 演示视频录制（3 分钟内，按主文档 [13.3](./short-link-开发文档.md#133-演示准备) 的顺序）
- [ ] Git 整理：确认提交历史清晰，主分支可运行

**验收（第 4 周暨项目总验收）**

```
1. 服务器（或本地）能完整演示：创建 → 跳转 → 拦截 → 看板
2. 拿一个别人不知道的短链，能在浏览器里跳转成功
3. README 让一个陌生人能在 10 分钟内跑起来项目
4. 压测报告有真实数据，能解释每个数字怎么来的
5. 15 个面试问题能流畅回答，其中「项目难点」「缓存三件套」「MQ 可靠性」「幂等设计」必须能讲 3 分钟以上
6. 代码里没有明文密码、没有调试残留
```

- [ ] 6 条全部通过
- [ ] 本周固定检查清单（同 W1D7）

**产出**：`README.md`、简历描述、演示视频、`docs/部署手册.md`、`docs/压测报告.md`（终版）

---

# 附录 A · 降级策略（进度落后时按此顺序砍）

**先明确底线：这四件事一定不能砍**

1. 创建短链（含幂等）
2. 跳转 + 缓存 + 布隆过滤器
3. 压测报告（哪怕数据不漂亮，也要有真实数字和优化对比）
4. 部署上线 + 简历定稿

**功能优先级**

| 优先级 | 内容 | 说明 |
| --- | --- | --- |
| P0 必做 | 创建短链、跳转、缓存、布隆过滤器、分布式锁幂等、限流、登录鉴权、部署 | 简历主线，缺一个就讲不完整 |
| P1 应做 | MQ 异步落库 + 消费幂等 + 死信、PV/UV 统计、定时聚合、统计接口、前端 4 个核心页面 | 决定项目是否"完整" |
| P2 加分 | 24 小时分布、浏览器/OS/设备维度、明细分页、EasyExcel 导出、批量创建、自定义后缀 | 有富余时间再做 |
| P3 可选 | 前端 ECharts 图表美化、死信重放任务、Pipeline 优化对比 | 锦上添花 |

**按顺序砍（砍到某一步就够了就停）**

1. 砍 EasyExcel 导出（P2）
2. 砍设备/地区维度统计，只保留浏览器 + OS（P2）
3. 砍前端看板的饼图，只保留卡片 + 折线 + 表格（P3）
4. 砍短链批量创建、自定义后缀（P2）
5. 砍 24 小时分布，只保留按天趋势（P2）
6. 砍明细分页表格（保留接口，前端不做页面）（P2）
7. 砍前端页面美化，用 Element Plus 默认样式（P3）
8. **最后才考虑**：把 RabbitMQ 换成 Redis Stream（功能等价，少维护一个中间件，2G 内存更安全）

**绝对不能砍的验收动作**（砍了功能，这些也要做）

- 并发创建同一长链只产生 1 条记录
- 不存在的短链不查数据库
- 缓存命中无 SQL
- 消息重复投递不产生重复数据（如果做了 MQ）
- 统计任务重复执行数据不翻倍
- 压测数据真实

---

# 附录 B · 每日打卡表

> 每天完成后打勾，并写下当天实际耗时与卡点。**卡点超过 4 小时未解决就记下来，周末复盘时决定是否降级。**

| 周次 | 天 | 任务 | 完成 | 实际耗时 | 卡点 |
| --- | --- | --- | --- | --- | --- |
| W1 | D1 | 环境安装 | [ ] | | |
| W1 | D2 | 建库建表 + Redis Key 常量 | [ ] | | |
| W1 | D3 | 工程骨架 + Knife4j | [ ] | | |
| W1 | D4 | 公共组件 | [ ] | | |
| W1 | D5 | 用户模块 + Sa-Token | [ ] | | |
| W1 | D6 | 分组模块 | [ ] | | |
| W1 | D7 | 单测 + 周验收 | [ ] | | |
| W2 | D1 | Base62 + 生成策略 | [ ] | | |
| W2 | D2 | 创建短链 + 分布式锁幂等 | [ ] | | |
| W2 | D3 | 布隆过滤器 | [ ] | | |
| W2 | D4 | 跳转 + 缓存 | [ ] | | |
| W2 | D5 | 限流 Lua 滑动窗口 | [ ] | | |
| W2 | D6 | 短链管理接口 | [ ] | | |
| W2 | D7 | 单测 + 压测基线 + 周验收 | [ ] | | |
| W3 | D1 | RabbitMQ 拓扑 | [ ] | | |
| W3 | D2 | MQ 生产者 + 降级 | [ ] | | |
| W3 | D3 | MQ 消费者 + 幂等 + 死信 | [ ] | | |
| W3 | D4 | Redis 实时统计 | [ ] | | |
| W3 | D5 | 定时聚合任务 | [ ] | | |
| W3 | D6 | 统计查询接口 | [ ] | | |
| W3 | D7 | 一致性校验 + 周验收 | [ ] | | |
| W4 | D1 | 前端工程 | [ ] | | |
| W4 | D2 | 登录 + 短链管理页 | [ ] | | |
| W4 | D3 | 数据看板 | [ ] | | |
| W4 | D4 | 联调 + 体验打磨 | [ ] | | |
| W4 | D5 | 压测与调优 | [ ] | | |
| W4 | D6 | 服务器部署 | [ ] | | |
| W4 | D7 | README + 简历 + 演示 | [ ] | | |

---

# 附录 C · 每日复盘模板

> 复制到 `docs/进度.md`，每天 5 分钟。**这些记录最后会变成你面试时的「项目难点」答案。**

```markdown
## 2026-09-16（W2 D2）创建短链 + 分布式锁幂等

### 今天完成了什么
- 实现了 create() 三段式：快路径查询 / Redisson 锁 + DCL / 唯一索引兜底
- 写了 ShortLinkServiceImplTest 的幂等用例

### 遇到的问题与解决
1. 问题：JMeter 100 并发时仍偶发 DuplicateKeyException 抛到接口层
   原因：全局异常处理器里把 DuplicateKeyException 直接返回错误，没做「转查库返回」的兜底
   解决：在 doCreate 里 try-catch 并查库返回已有记录
   收获：分布式锁不能保证 100% 不重复，唯一索引才是最后一道防线

2. 问题：tryLock 传了 leaseTime 导致看门狗失效
   收获：看门狗只在「不指定 leaseTime」时启用，这是面试高频追问点

### 明天的 TODO
- [ ] 布隆过滤器初始化 + 启动预热

### 面试可用素材（今天攒到的）
- 为什么用 Redisson 而不是 SETNX 手写
- 分布式锁的失效边界与唯一索引兜底
```

---

# 附录 D · 常用命令速查（Windows PowerShell）

**启动与检查中间件**

```powershell
# MySQL
net start MySQL84
net stop MySQL84
mysql -uroot -p -e "show databases;"

# Redis
redis-server --service-start
redis-server --service-stop
redis-cli -a 你的密码 ping
redis-cli -a 你的密码 keys "short-link:*"                # 仅开发期排查用，生产禁用 KEYS
redis-cli -a 你的密码 scan 0 match "short-link:stats:*" count 100
redis-cli -a 你的密码 pfcount "short-link:stats:uv:2026-09-16:http://localhost:8000/xxx"
redis-cli -a 你的密码 zcard "short-link:rate-limit:user:zhangsan"
redis-cli -a 你的密码 ttl "short-link:goto:http://localhost:8000/abc123"

# RabbitMQ
net start RabbitMQ
rabbitmqctl status
rabbitmqctl list_queues name messages consumers
rabbitmqctl list_exchanges name type
rabbitmq-plugins enable rabbitmq_management

# Nginx
start nginx
nginx -s reload
nginx -s stop
nginx -t                                  # 检查配置语法
```

**项目构建与运行**

```powershell
mvn clean package -DskipTests             # 打包
java -jar target/short-link-1.0.0.jar --spring.profiles.active=dev
java -Xms256m -Xmx512m -XX:+UseG1GC -Duser.timezone=Asia/Shanghai -jar short-link.jar --spring.profiles.active=prod

mvn test                                  # 全部单测
mvn test -Dtest=Base62UtilTest            # 单个测试类

# 前端
npm install
npm run dev
npm run build
```

**WinSW 服务管理（部署用）**

```powershell
.\shortlink-service.exe install
.\shortlink-service.exe start
.\shortlink-service.exe stop
.\shortlink-service.exe uninstall
Get-Service -Name shortlink | Format-List          # 查看服务状态
Set-Service -Name shortlink -StartupType Automatic # 设置开机自启
```

**JMeter 命令行压测**

```powershell
jmeter -n -t jmeter\redirect-hit.jmx -l jmeter\result.jtl -e -o jmeter\report
# -n 非 GUI  -t 测试计划  -l 结果文件  -e -o 生成 HTML 报告
```

**排查常用**

```powershell
netstat -ano | findstr :8000              # 查端口占用
Get-Process -Id <PID>                     # 查进程（找占用端口的进程）
Get-Process java, mysqld, redis-server, erl | Select-Object Name, WS   # 看各进程内存占用
```

---

# 附录 E · 三分钟自检（每天开工前问自己）

1. 昨天的验收标准都打勾了吗？没打勾的先补，别往前赶。
2. 今天要做的事，设计文档里对应的章节我已经看过了吗？（先看设计再做，比边做边改快得多）
3. 今天做完，能演示给别人看吗？如果不能，是不是任务切得太大了？
4. 有阻塞吗？超过 4 小时没解决的，记进 `docs/进度.md`，周末决定降级。
