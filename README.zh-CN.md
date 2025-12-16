# YOLO 推理排队后端使用说明（中文）

本文档介绍仓库中的 Spring Boot + MySQL 推理排队后台（`yoloBehind`）的使用方式与核心技术架构，便于快速部署和二次开发。

## 架构与目录速览
- **后端框架**：Spring Boot 3（Java 17），提供 JWT 登录、业务 API、静态资源托管和 SPA 路由回退。
- **安全与认证**：`/api/auth/login` 发行 JWT，`SecurityConfig` 保护除 `/api/auth/**` 外的 API，未登录返回 401。
- **队列与数据库**：MySQL 保存两张表：
  - `detect_record`：记录原始图、推理结果、状态、错误信息等。
  - `detect_task`：充当队列表，包含 `status`/`retry_count`/`locked_by`/`next_run_at` 等调度字段。
- **任务调度 Worker**：`DetectWorker` 每秒锁取 1 条 `PENDING` 任务（`SELECT ... FOR UPDATE`），调 Flask 推理机 `/api/identify` 并获取 `/api/result_image`，落盘后更新记录。
- **存储结构**：`storage.base-dir`（默认 `files`）下按 `original/`、`result/` 分目录存放上传原图与推理结果图，Spring 通过 `/files/**` 直接映射文件访问。
- **前端托管**：打包后的静态资源放入 `src/main/resources/static/`，`SpaForwardController` 将除 `/api/**` 与静态文件外的路径转发到 `index.html`，适配 SPA 路由（`/login`、`/app`、`/map`、`/404`）。
- **Flask 推理机**：`yoloapi/` 提供示例 `app.py` 与 `predict.py`，Spring 侧通过配置的 `yolo.api.base-url` 调用。

## 运行前准备
1. **安装依赖**：JDK 17、Maven 3.9+、MySQL 8、Python 3（运行 `yoloapi`）。
2. **创建数据库**：
   ```sql
   CREATE DATABASE yolo DEFAULT CHARACTER SET utf8mb4;
   ```
3. **配置后端**：如需修改数据库、JWT 或存储路径，可在 `src/main/resources/application.properties` 中调整：
   - `spring.datasource.url/username/password`
   - `security.jwt.secret`（Base64 编码的密钥）与 `security.jwt.expiration-ms`
   - `storage.base-dir`（文件保存根目录）、`worker.delay-ms`（轮询间隔）、`worker.retry-delay-seconds`（失败重试间隔）
   - `yolo.api.base-url`（Flask 推理机地址，默认 `http://localhost:5000`）
   - 默认管理员账号：`app.default-user.username=admin`，`app.default-user.password=change-me`
4. **启动 Flask 推理机**（单实例）：
   ```bash
   cd yoloapi
   python app.py
   ```
5. **启动 Spring Boot**：
   ```bash
   ./mvnw spring-boot:run
   ```
   应用启动后监听 `http://localhost:8080`。

## 使用流程
1. **登录获取 JWT**
   - 请求：`POST /api/auth/login`，JSON 体 `{ "username": "admin", "password": "<修改后的密码>" }`
   - 响应：`{"token":"..."}`，前端将其放入请求头：`Authorization: Bearer <token>`。
2. **上传推理任务**
   - 请求：`POST /api/detect`，`multipart/form-data`，字段 `file` 为图片。需要带 JWT。
   - 响应：`{ "requestId": "<uuid>" }`，任务写入 `detect_record` 与 `detect_task`，状态为 `PENDING`。
3. **轮询任务状态**
   - 请求：`GET /api/detect/{requestId}`。
   - 响应：包含 `status`（`PENDING/RUNNING/SUCCESS/FAILED`）、`resultPath`、`diseaseName`、`confidence`、`advice` 等字段。
4. **查看结果图片**
   - 当 `status=SUCCESS` 时，`resultPath` 指向 `/files/result/<requestId>.jpg`，前端可直接展示；或按需新增 `/api/detect/{requestId}/result.jpg` 读取文件。

## 核心技术要点
- **一次仅处理 1 个推理请求**：Worker 使用事务 + `SELECT ... FOR UPDATE` 锁定单条任务，串行调用 Flask，避免并发串图或覆盖。
- **失败重试与削峰**：当 Flask 调用失败时，任务状态标记为 `FAILED`，`next_run_at` 向后平移 `worker.retry-delay-seconds`（默认 30 秒），实现削峰与自动重试。
- **文件路径映射**：上传文件写入 `files/original/<requestId>.<ext>`；推理结果写入 `files/result/<requestId>.jpg`，并通过 `/files/**` 公开访问。
- **前后端分离**：未登录访问 `/api/**` 返回 401；前端路由守卫跳转 `/login`。刷新 `/login`、`/app`、`/map` 等路径不会 404，因 Spring 会 forward 到 SPA 入口。

## 典型二次开发入口
- **安全与鉴权**：`SecurityConfig`、`JwtAuthFilter`、`AuthController`、`AuthService`。
- **业务与队列**：`DetectController`、`DetectService`、`DetectTaskRepository`、`DetectWorker`。
- **实体与存储**：`DetectRecord`、`DetectTask` 定义了状态流转字段，可根据业务扩展字段（如用户备注、批次号）。
- **前端资源**：将打包产物（如 Vue/React）放入 `src/main/resources/static/`，后端无需改动即可托管。

## 常见问题
- **Maven 依赖下载失败**：若处于无外网或镜像受限环境，可在 `~/.m2/settings.xml` 配置内部仓库或预先下载依赖。
- **推理接口地址不通**：检查 `yolo.api.base-url` 与 Flask 监听端口；必要时在 Worker 日志中查看请求报错。
- **文件权限**：`storage.base-dir` 目录需有读写权限；容器部署时请挂载持久卷。
