# oauth2-resource-server

Java 17 / Spring Boot 4.0.9 OAuth2 资源服务，默认端口 `9002`。

```powershell
mvn verify
java -jar target/oauth2-resource-server-0.0.1-SNAPSHOT.jar
```

启动不要求认证服务已在线；首次校验令牌时需要能够获取 JWKS。

| 环境变量 | 默认值 |
| --- | --- |
| `RESOURCE_ISSUER` | `http://localhost:9001` |
| `RESOURCE_JWK_SET_URI` | `http://localhost:9000/oauth2/jwks` |
| `RESOURCE_AUDIENCE` | `demo-api` |

`GET /api/resources/demo` 要求 `Authorization: Bearer <access_token>` 和 `demo.read`。
返回 message、JWT subject 和演示条目。无有效令牌返回 401，scope 不足返回 403。
服务无状态，不使用登录 Cookie；JWT 验证签名、类型、issuer、audience 和有效期。
退出登录或撤销授权不会立即撤销本地验证的 JWT。

先启动认证服务 9000、资源服务 9002、前端 9001，浏览器访问
`http://localhost:9001/app/`，完成登录后点击 **Load resource**。
前端通过同源代理调用资源服务；可用 `RESOURCE_SERVER_PROXY_TARGET` 修改 Vite 代理目标。
生产使用 HTTPS，并将 `/api/resources/**` 原路径反向代理到本服务。

详见 [设计文档](DESIGNS/oauth2-resource-server.md)。
