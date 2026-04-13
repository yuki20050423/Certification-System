# 专业认证年度备案系统

## 项目简介

西南交通大学专业认证年度备案系统是一个面向高校专业认证管理的综合性Web应用。系统实现了从**管理员发布备案任务**、**教师上传备案材料**到**审核员在线审核**的全流程管理，并提供了**已备案档案库导出**、**审核任务分配**、**通知管理**、**个人中心**等辅助功能，帮助高校高效完成专业认证年度备案工作。

## 技术栈

| 层次       | 技术                                                         |
| ---------- | ------------------------------------------------------------ |
| 后端框架   | Spring Boot 2.7.14                                           |
| 数据库     | MySQL 8.0+，MyBatis-Plus 3.5.3.1                             |
| 前端       | HTML5 / CSS3 / JavaScript，原生 Fetch API，Font Awesome 图标 |
| 安全       | Spring Security Crypto（BCrypt密码加密）                     |
| 文档解析   | Apache POI（Word/Excel解析），Jsoup（HTML解析）               |
| 构建工具   | Maven 3.6+                                                   |

## 系统功能

系统为三种角色提供不同的功能视图：**管理员（ADMIN）**、**审核员（ASSESSOR）**、**教师（TEACHER）**。

### 管理员功能

| 功能模块       | 说明                                                         |
| -------------- | ------------------------------------------------------------ |
| 仪表盘         | 查看任务总数、各状态任务数量、任务状态分布图                 |
| 任务管理       | 查看所有备案任务，支持状态筛选、修改截止日期、删除任务、导出CSV、催办教师 |
| 发布任务       | 按年级/学期检索课程，选择课程/教学班，配置任务名称、起止时间、备案项目（12项）、指派审核员，批量生成任务，并自动生成教师账号清单Excel |
| 审核任务分配   | 为已提交的任务分配审核员，指定审核项目和截止日期（四步向导） |
| 成员管理       | 添加/删除/禁用成员，修改角色（教师⇄审核员），按角色/工号/姓名筛选 |
| 数据导入       | 上传Word文档自动解析课程数据，从教务系统抓取教师信息并自动创建账号 |
| 已备案档案库   | 查看所有已完成的备案课程（状态为审核通过），支持按学期/专业/课程名/教师/状态筛选，批量导出ZIP压缩包（包含课程文件及审核意见CSV） |
| 统计分析       | 系统整体统计：任务数、课程数、用户数、文件数等               |
| 通知管理       | 查看/编辑/删除系统通知，按类型/接收人/状态筛选               |

### 审核员功能

| 功能模块     | 说明                                                         |
| ------------ | ------------------------------------------------------------ |
| 审核任务     | 查看分配给自己的审核任务列表，支持状态筛选                   |
| 任务审核     | 查看教师上传的所有文件，支持PDF/图片在线预览及下载，填写审核意见、评分，提交“审核通过”或“需修改”（拒绝时必须填写修改建议） |
| 审核记录     | 查看自己所有的审核历史，包含课程信息、审核结果、修改建议等   |
| 审核统计     | 统计总任务数、待审核数、通过数、需修改数、通过率等           |
| 通知中心     | 接收新任务、审核结果等通知，支持标记已读                     |
| 个人中心     | 修改个人信息（院系、职称、邮箱、手机号）及登录密码           |

### 教师功能

| 功能模块     | 说明                                                         |
| ------------ | ------------------------------------------------------------ |
| 我的任务     | 查看分配给自己的备案任务，支持状态筛选和按截止日期排序       |
| 任务详情     | 查看任务说明、材料要求、截止日期，上传备案文件（支持批量上传），提交任务 |
| 文件管理     | 查看/下载/删除已上传的文件（仅在任务未截止且待上传/需修改状态时允许删除） |
| 审核结果     | 查看审核记录：审核状态、评分、审核意见、修改建议             |
| 通知中心     | 接收新任务、审核结果通知，支持标记已读                       |
| 任务统计     | 查看个人任务完成情况统计（总任务数、待上传数、通过数等）     |
| 个人中心     | 修改个人信息（院系、职称、邮箱、手机号）及登录密码           |

## 数据库设计

### 核心表结构

| 表名            | 说明                               | 主要字段                                                     |
| --------------- | ---------------------------------- | ------------------------------------------------------------ |
| `user`          | 用户表                             | id, username, work_id, real_name, email, phone, password, role, status, department, title |
| `teacher`       | 教师课程信息表（从教务系统抓取）   | id, semester, course_code, course_name, teaching_class, teacher_name, department, preferred（优选班信息） |
| `course`        | 课程表（未来课程/培养方案课程）    | id, course_name, grade, major, semester, status              |
| `task`          | 备案任务表                         | id, course_id, teacher_id, assessor_id, deadline, status, description, material_requirements |
| `file`          | 文件表                             | id, task_id, original_name, file_path, file_size, upload_time, upload_user_id, status |
| `review_record` | 审核记录表                         | id, task_id, assessor_id, review_status, score, comment, suggestions, review_time |
| `notification`  | 通知表                             | id, user_id, task_id, type, title, content, is_read, create_time |

### 任务状态流转

```
PENDING_UPLOAD（待上传）
    ↓ 教师上传文件并提交
SUBMITTED（已提交）
    ↓ 管理员分配审核员后
PENDING_REVIEW（待审核）或 REVIEWING（审核中）
    ↓ 审核员提交审核结果
APPROVED（审核通过） 或 NEED_REVISION（需修改）
    ↓ 如需修改，教师重新上传后再次进入 SUBMITTED
```

## 项目结构（含文件用途说明）

```
Certification-System/
├── backend/                          # 后端Spring Boot项目
│   ├── pom.xml                       # Maven依赖管理
│   ├── src/main/java/com/swjtu/certification/
│   │   ├── CertificationApplication.java   # Spring Boot启动类，包含@MapperScan
│   │   ├── config/                        # 配置类
│   │   │   └── MajorMappingConfig.java     # 专业映射配置（从application.yml读取，用于教师筛选匹配）
│   │   ├── controller/                    # 控制器层（接收HTTP请求）
│   │   │   ├── AdminController.java       # 管理员接口：仪表盘、任务管理、成员管理、数据导入、档案库导出等
│   │   │   ├── AssessorController.java    # 审核员接口：任务列表、文件预览、审核提交、统计
│   │   │   ├── AuthController.java        # 认证接口：登录、注册
│   │   │   ├── FileController.java        # 文件接口：上传、批量上传、下载、删除
│   │   │   ├── NotificationController.java# 通知接口：查询、标记已读、全部已读、编辑删除
│   │   │   ├── ProfileController.java     # 个人中心接口：获取/更新个人信息、修改密码
│   │   │   ├── ReviewController.java      # 审核记录接口：查询任务审核记录、最新记录
│   │   │   ├── TaskController.java        # 任务通用接口：列表、详情、状态更新
│   │   │   ├── TeacherTaskController.java # 教师任务接口：获取任务列表/详情、提交任务、统计
│   │   │   └── UserController.java        # 用户接口：查询教师/审核员列表
│   │   ├── service/                       # 业务逻辑层
│   │   │   ├── AdminService.java          # 管理员核心业务：仪表盘、任务发布、教师抓取、档案导出、审核分配
│   │   │   ├── CourseService.java         # 课程业务：查询课程、更新状态
│   │   │   ├── FileService.java           # 文件业务：上传、下载、删除、权限校验
│   │   │   ├── NotificationService.java   # 通知业务：创建、查询、标记已读、未读统计
│   │   │   ├── ReviewRecordService.java   # 审核记录业务：创建审核记录、查询记录、发送通知
│   │   │   ├── TaskService.java           # 任务业务：任务CRUD、教师/审核员任务查询、统计
│   │   │   └── UserService.java           # 用户业务：登录、注册、信息更新、密码修改
│   │   ├── mapper/                        # MyBatis-Plus Mapper接口
│   │   │   ├── CourseMapper.java          # 课程表数据访问
│   │   │   ├── FileMapper.java            # 文件表数据访问
│   │   │   ├── NotificationMapper.java    # 通知表数据访问
│   │   │   ├── ReviewRecordMapper.java    # 审核记录表数据访问（含自定义SQL）
│   │   │   ├── TaskMapper.java            # 任务表数据访问
│   │   │   ├── TeacherMapper.java         # 教师信息表数据访问
│   │   │   └── UserMapper.java            # 用户表数据访问
│   │   ├── entity/                        # 实体类（对应数据库表）
│   │   │   ├── Course.java                # 课程实体：课程名称、年级、专业、学期、状态
│   │   │   ├── File.java                  # 文件实体：任务ID、文件名、路径、大小、上传用户
│   │   │   ├── Notification.java          # 通知实体：用户ID、任务ID、类型、标题、内容、已读状态
│   │   │   ├── ReviewRecord.java          # 审核记录实体：任务ID、审核员ID、审核状态、评分、意见、建议
│   │   │   ├── Task.java                  # 任务实体：课程ID、教师ID、审核员ID、截止日期、状态、说明
│   │   │   ├── Teacher.java               # 教师信息实体（从教务系统抓取）：学期、课程代码、教学班、教师姓名等
│   │   │   └── User.java                  # 用户实体：用户名、工号、姓名、邮箱、手机、角色、院系、职称
│   │   ├── dto/                           # 数据传输对象（请求参数）
│   │   │   ├── AddMemberDTO.java          # 添加成员请求
│   │   │   ├── BatchExportDTO.java        # 批量导出请求
│   │   │   ├── BatchTaskDTO.java          # 批量创建任务请求（含内部TaskItem）
│   │   │   ├── ChangePasswordDTO.java     # 修改密码请求
│   │   │   ├── FileUploadDTO.java         # 文件上传请求（含字符集）
│   │   │   ├── GenerateTaskDTO.java       # 生成任务请求（任务名称、起止时间、审核员、项目等）
│   │   │   ├── LoginDTO.java              # 登录请求
│   │   │   ├── RegisterDTO.java           # 注册请求
│   │   │   ├── ReviewAssignmentDTO.java   # 审核任务分配请求
│   │   │   ├── ReviewDTO.java             # 审核提交请求
│   │   │   ├── TaskUpdateDTO.java         # 任务更新请求（说明、材料要求）
│   │   │   └── UpdateProfileDTO.java      # 个人信息更新请求
│   │   ├── vo/                            # 视图对象（响应数据）
│   │   │   ├── ArchiveTaskVO.java         # 已备案档案视图（含课程、教师、学期、文件数等）
│   │   │   ├── AssessorTaskVO.java        # 审核员任务视图（含课程名、教师、文件数、提交时间）
│   │   │   ├── CourseItemVO.java          # 课程项视图（用于发布任务时展示教学班/未来课程）
│   │   │   ├── CourseTeachingVO.java      # 教学班视图（课程代码、教学班号、教师等）
│   │   │   ├── CourseVO.java              # 课程视图（基础信息）
│   │   │   ├── FileVO.java                # 文件视图（含格式化大小、下载URL）
│   │   │   ├── LoginVO.java               # 登录响应（token、用户信息、跳转URL）
│   │   │   ├── NotificationVO.java        # 通知视图（含用户名、角色、类型描述）
│   │   │   ├── PageResult.java            # 分页结果封装
│   │   │   ├── Result.java                # 统一响应结果（code, message, data）
│   │   │   ├── ReviewRecordVO.java        # 审核记录视图（含课程名、教师名、审核员名）
│   │   │   ├── ReviewStatisticsVO.java    # 审核统计视图（总任务、待审核、通过率等）
│   │   │   ├── TaskVO.java                # 任务视图（管理员用）
│   │   │   ├── TeacherTaskStatisticsVO.java # 教师任务统计视图
│   │   │   ├── TeacherTaskVO.java         # 教师任务视图（含文件数、是否过期）
│   │   │   ├── TeacherVO.java             # 教师视图（工号、姓名、邮箱、手机）
│   │   │   ├── UserProfileVO.java         # 用户个人信息视图
│   │   │   └── UserVO.java                # 用户视图（含教师额外字段：teachingClass等）
│   │   ├── exception/                     # 异常处理
│   │   │   └── GlobalExceptionHandler.java # 全局异常处理器（参数验证、通用异常）
│   │   └── util/                          # 工具类
│   │       ├── GetCourseList.java         # 解析CSV文件为Course实体（从Word转换后的CSV）
│   │       ├── GetTeacherList.java        # 使用Jsoup从教务系统抓取教师信息（支持学期、课程名）
│   │       ├── PasswordGenerator.java     # BCrypt密码生成工具（用于生成测试数据）
│   │       ├── ReadWordToCsvUtil.java     # 将Word培养方案文档转换为CSV（处理合并单元格）
│   │       ├── SemesterUtils.java         # 学期格式转换（标准↔导出格式，获取当前学期）
│   │       └── TestTeacher.java           # 抓取测试类（调试用）
│   ├── src/main/resources/
│   │   ├── application.yml                # 主配置文件（端口、数据库、MyBatis、文件上传、专业映射）
│   │   └── application-dev.yml            # 开发环境配置（数据库密码等，实际使用时修改）
│   └── src/test/java/                     # 单元测试
│       ├── GetTeacherListTest.java        # 测试教务系统教师抓取
│       └── MajorMappingConfigTest.java    # 测试专业映射配置
├── database/                              # 数据库脚本
│   ├── schema.sql                         # 完整建表语句及测试数据（用户、课程、任务等）
│   ├── fix_password.sql                   # 修复测试账号密码为123456的BCrypt哈希
│   ├── update_password.sql                # 密码更新脚本（备用）
│   ├── migration_add_major.sql            # 为course表添加major列的迁移脚本
│   └── fix_major.sql                      # 添加major列的简化脚本
├── frontend/                              # 前端静态文件（位于项目根目录）
│   ├── index.html                         # 系统首页（含轮播图、流程介绍、资料列表、FAQ）
│   ├── login.html                         # 登录/注册页面（标签切换、表单验证）
│   ├── admin.html                         # 管理员控制台（侧边栏菜单、仪表盘、任务管理、发布任务等）
│   ├── assessor.html                      # 审核员工作台（任务审核、审核记录、统计、个人中心）
│   ├── teacher.html                       # 教师工作台（任务列表、文件上传、审核结果、通知）
│   ├── css/                               # 样式文件
│   │   ├── style.css                      # 全局样式（导航栏、banner、页脚、响应式）
│   │   ├── login.css                      # 登录/注册页面专用样式
│   │   ├── admin.css                      # 管理员控制台样式（侧边栏、表格、模态框、步骤向导）
│   │   ├── assessor.css                   # 审核员工作台样式（侧边栏、审核表单、通知列表）
│   │   └── teacher.css                    # 教师工作台样式（任务卡片、上传区、统计卡片）
│   ├── js/                                # JavaScript脚本
│   │   ├── script.js                      # 首页通用脚本（登录状态检查、轮播、FAQ折叠、回到顶部）
│   │   ├── auth.js                        # 登录/注册页面逻辑（表单提交、API调用、Token存储）
│   │   ├── admin.js                       # 管理员控制台核心逻辑（任务加载、发布任务、成员管理、档案导出）
│   │   ├── assessor.js                    # 审核员工作台逻辑（任务列表、审核提交、预览下载、统计）
│   │   └── teacher.js                     # 教师工作台逻辑（任务列表、文件上传/删除、提交审核、通知）
│   └── images/                            # 图片资源
│       ├── swjtu_school_emblem1.png       # 校徽（浅色）
│       ├── swjtu_school_emblem2.png       # 校徽（深色，用于侧边栏）
│       └── swjtu_sce1.png ~ swjtu_sce8.png # 轮播背景图（校园风景）
└── docs/                                  # 文档目录

```

## 核心模块说明

### 专业映射配置（MajorMappingConfig）
系统支持通过配置文件或代码初始化专业映射，用于在抓取教师信息时根据专业关键词筛选对应教学班。例如：`计算机科学与技术` 映射到关键词 `["计算机类","计算机"]`，系统会自动匹配 `preferred` 字段中包含这些关键词的教师。

配置位于 `application.yml` 的 `major.mapping.mappings` 下。

### 学期格式转换（SemesterUtils）
- 数据库存储：标准格式 `2025-2026-2`
- 前端显示/导出：`2025-2026学年第二学期`
- 提供 `toExportFormat()` 和 `fromExportFormat()` 双向转换方法，确保学期显示一致。

### 教师信息抓取（GetTeacherList）
使用 Jsoup 模拟 HTTP 请求，从学校教务系统抓取指定学期和课程名称的教师信息（包含教学班、教师姓名、院系等），存入 `teacher` 表。支持分页抓取和 Cookie 维持会话。注意：依赖校外VPN或校内网络环境。

### Word文档解析（ReadWordToCsvUtil）
将上传的 Word 培养方案文档（特定格式）转换为 CSV 文件，再通过 `GetCourseList` 解析为课程数据，存入 `course` 表。支持合并单元格处理。

### 全局异常处理（GlobalExceptionHandler）
统一处理参数验证异常（`MethodArgumentNotValidException`）和通用运行时异常，返回格式化的错误响应。

## 部署运行

### 环境要求

- JDK 21（与pom.xml中java.version一致）
- MySQL 8.0+
- Maven 3.6+

### 步骤

1. **创建数据库**  
   执行 `database/schema.sql` 创建数据库 `certification_system` 及所有表，并插入测试数据。

2. **修改数据库配置**  
   编辑 `backend/src/main/resources/application-dev.yml`，修改数据库用户名和密码：
   ```yaml
   spring:
     datasource:
       url: jdbc:mysql://localhost:3306/certification_system?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
       username: root
       password: your_password
   ```

3. **连接教务网**
   打开教务网，登录成功后查看Cookie，复制 JSESSIONID 和 TWFID 的值
   编辑`backend/src/main/java/com/swjtu/certification/util/GetTeacherList`，修改 JSESSIONID 和 TWFID 的值
   ```yaml
   private static final Map<String, String> COOKIES = new HashMap<>() {{
   put("JSESSIONID", "JSESSIONID的值");
   put("TWFID", "TWFID的值");
   put("platformMultilingual_-_edu.cn", "zh_CN");
   }};
   ```

4. **修改邮件地址**
   编辑`backend/src/main/resources/application.yml`，修改 发件邮箱账号 和 邮箱授权码
   ```yaml
     mail:
    host: smtp.qq.com                    
    port: 587                              
    username: your_email@qq.com            # 发件邮箱账号
    password: 你的邮箱授权码        # 邮箱授权码（不是登录密码）
   ```
   QQ邮箱：设置 → 账户 → POP3/IMAP/SMTP服务 → 开启并生成授权码
   163邮箱：设置 → POP3/SMTP/IMAP → 开启并获取客户端授权密码
   编辑`backend/src/main/java/com/swjtu/certification/service/EmailService`，修改 邮箱地址
   ```yaml
   helper.setFrom("924107131@qq.com");   // 必须与配置的 username 一致
   ```

4. **运行项目**
   在IDE中右键 `CertificationApplication.java` -> Run
   后端服务默认运行在 `http://localhost:8080`


5. **访问系统**  
   打开浏览器访问 `http://localhost:8080`（或你配置的地址）。

### 测试账号

| 角色   | 用户名       | 工号      | 密码   |
| ------ | ------------ | --------- | ------ |
| 管理员 | admin        | SWJTU001  | 123456 |
| 教师   | teacher01    | SWJTU002  | 123456 |
| 教师   | teacher02    | SWJTU003  | 123456 |
| 审核员 | assessor01   | SWJTU004  | 123456 |

> 密码已使用BCrypt加密，如需修改请执行 `database/fix_password.sql` 或通过系统“修改密码”功能。

## 使用说明

### 管理员操作流程

1. **导入课程数据**
    - 进入“数据导入”页面，上传Word文档（格式需符合系统要求）自动解析课程。
    - 或使用“从教务系统获取教师”功能，按课程名抓取教师信息。

2. **发布备案任务**
    - 进入“发布任务”页面，按年级或学期检索课程。
    - 勾选需要备案的课程（可筛选开课状态、备案状态）。
    - 点击“生成任务”，填写任务名称、起止时间、选择审核员、勾选备案项目（12项）。
    - 确认后系统批量创建任务，并生成教师账号清单Excel供下载。

3. **审核任务分配**
    - 进入“审核任务分配”页面，按状态筛选已提交的任务。
    - 选择任务 → 选择审核项目 → 选择审核员 → 设置审核截止日期 → 确认分配。

4. **任务管理**
    - 可修改任务截止日期、催办教师、删除任务、导出CSV。

5. **成员管理**
    - 添加/删除/禁用用户，修改用户角色。

6. **已备案档案库**
    - 按条件筛选已审核通过的课程，勾选后批量导出ZIP压缩包（包含所有上传文件及审核意见CSV）。

### 教师操作流程

1. 登录后进入“我的任务”页面，查看所有分配给自己的备案任务。
2. 点击“详情”进入任务页面，阅读任务说明和材料要求。
3. 在“待上传”或“需修改”状态下，点击“批量上传”按钮选择文件（支持多选），系统自动上传。
4. 上传完成后，点击“提交审核”按钮，任务状态变为“已提交”。
5. 等待审核员审核，可在“审核结果”页面查看审核意见和修改建议。
6. 若审核结果为“需修改”，根据建议修改后重新上传并提交。

### 审核员操作流程

1. 登录后进入“审核任务”页面，查看分配给自己的待审核任务。
2. 点击“审核”进入详情页，查看教师上传的文件（支持在线预览和下载）。
3. 填写审核意见、评分（可选）。
4. 选择“审核通过”或“需修改”（拒绝时必须填写修改建议）。
5. 提交审核，系统自动更新任务状态并发送通知给教师。

## 开发说明

### 注意事项

- 所有API接口均支持跨域（CORS），生产环境应限制具体域名。
- 当前用户认证使用Token（存储在localStorage/sessionStorage），实际项目中建议使用JWT并添加拦截器。
- 文件存储默认为本地 `./uploads` 目录，生产环境建议改用对象存储（如阿里云OSS）。
- 密码使用BCrypt加密，登录时通过 `BCryptPasswordEncoder` 验证。
- 事务管理使用 `@Transactional`，确保数据一致性。
- 教务系统抓取依赖校外VPN或校内网络环境，请确保网络可达。

## API接口概览

详细接口文档请参考 `docs/` 目录下的：
- `发布备案任务模块API文档.md`
- `填写备案信息模块API文档.md`
- `审核备案模块API文档.md`

主要接口列表：

| 模块         | 方法 | 路径                                      | 说明                     |
| ------------ | ---- | ----------------------------------------- | ------------------------ |
| 认证         | POST | `/api/auth/login`                         | 用户登录                 |
|              | POST | `/api/auth/register`                      | 用户注册                 |
| 管理员       | GET  | `/api/admin/dashboard`                    | 仪表盘数据               |
|              | GET  | `/api/admin/tasks`                        | 获取所有任务             |
|              | POST | `/api/admin/tasks/batch`                  | 批量创建任务             |
|              | POST | `/api/admin/generate-tasks`               | 生成任务（含教师清单）   |
|              | POST | `/api/admin/review-assignments`           | 审核任务分配             |
|              | GET  | `/api/admin/archive`                      | 已备案档案库列表         |
|              | POST | `/api/admin/archive/export`               | 批量导出档案ZIP          |
| 教师         | GET  | `/api/teacher/tasks`                      | 获取我的任务列表         |
|              | POST | `/api/teacher/tasks/{taskId}/submit`      | 提交任务                 |
|              | POST | `/api/files/upload`                       | 上传文件                 |
|              | POST | `/api/files/batch-upload`                 | 批量上传                 |
| 审核员       | GET  | `/api/assessor/tasks`                     | 获取审核任务列表         |
|              | POST | `/api/assessor/review`                    | 提交审核                 |
|              | GET  | `/api/assessor/statistics`                | 审核统计                 |
| 通用         | GET  | `/api/notifications`                      | 获取通知列表             |
|              | PUT  | `/api/notifications/{id}/read`            | 标记已读                 |
|              | GET  | `/api/profile`                            | 获取个人信息             |
|              | PUT  | `/api/profile/password`                   | 修改密码                 |

## 联系方式

- 开发团队：西南交通大学
- 项目用途：SRTP（大学生科研训练计划）项目成果