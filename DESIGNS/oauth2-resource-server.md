# OAuth2 Resource Server 设计

## 范围与边界

沿用认证服务和客户端设计中的 `demo-api` audience、`demo.read` scope，以及
Java 17 / Spring Boot 4.0.9。资源服务默认监听 9002，仅提供受保护的演示读取接口，
不实现登录、用户目录、令牌签发、刷新令牌或会话管理。

## 请求流程

1. 浏览器客户端完成授权码 + S256 PKCE，取得访问令牌。
2. 用户点击 **Load resource**，客户端向同源 `/api/resources/demo` 发送
   `Authorization: Bearer <access_token>`，不发送 Cookie，不缓存响应。
3. 开发环境由 Vite 将 `/api/resources/**` 原路径代理至 9002；生产环境配置同等反向代理。
4. Spring Security 从受信任 JWKS 获取公钥，验证 RS256 签名、`at+jwt` 类型、issuer、
   audience、有效期（包括存在时的 nbf），并要求非空 subject 和必须存在的 exp。
5. 只有具备 `SCOPE_demo.read` authority 的 GET 请求可访问演示资源。

签名和 JWT 校验采用 Spring Security 的 NimbusJwtDecoder；默认时间容差为 60 秒。
只信任 RS256，不根据令牌提供的地址下载密钥。JWKS 地址与 issuer 独立配置：
本地直接访问认证服务 9000 的公钥端点，同时仍要求 issuer 为公开地址 9001，
不依赖前端开发服务器获取公钥。公钥由框架缓存和按需更新。

## API 契约

`GET /api/resources/demo` 成功返回 200、`Cache-Control: no-store`：

```json
{
  "message": "You have successfully accessed the protected resource.",
  "subject": "user-001",
  "items": ["OAuth2 protected demo resource", "Requires demo.read scope"]
}
```

`subject` 来自通过校验的 JWT，不从浏览器会话或请求参数推断身份。
未携带、格式错误或校验失败的令牌返回 401 和 Bearer challenge；有效令牌缺少
scope 返回 403。错误使用框架的 Bearer 错误响应，客户端不依赖 JSON 错误体。
其他路径和方法默认拒绝，不提供公开业务端点。

## 安全与运维

无状态安全链不创建 HTTP 会话，不接受 Cookie 身份；关闭请求缓存和 CSRF（该服务只接受
Bearer 认证）。不启用跨来源 CORS，浏览器通过同源代理访问。生产必须使用 HTTPS，
保护 JWKS 传输并配置受信任 issuer、audience 和 JWKS 地址。

本地验证不查询认证服务会话或撤销状态，退出、禁用用户和撤销授权不会立即使已签发的
JWT 失效；它们会在到期后被拒绝。即时撤销、持久业务数据、多租户和写操作不在本次范围。
认证服务 dev 密钥重启后变化；重新授权取得新令牌，不将临时密钥用于生产。

## 客户端行为与验证

资源面板独立显示加载中、结果、401、403、服务错误和重试入口；过期令牌在发请求前拒绝。
失败时清除旧资源展示，重新授权复用现有 PKCE 流程。组件卸载时取消请求，避免退出后回填数据。
访问令牌不放入 URL、日志或资源结果，不向其他来源跟随重定向。

后端集成测试通过本地 HTTP JWKS 服务验证真实签名令牌，覆盖成功、缺失/畸形令牌、错误签名、
issuer、audience、类型、过期/缺少 exp、缺少 scope、未知路径和错误方法。
前端测试覆盖 Bearer 请求、不发送 Cookie、错误分支和资源面板交互。

参考：[Spring Security JWT Resource Server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)。
