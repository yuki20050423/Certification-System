# 填写备案信息模块 - API接口文档

## 概述
本文档描述了填写备案信息模块（普通教师角色使用）的所有API接口。

## 基础信息
- 基础URL: `http://localhost:8080`
- 所有接口支持跨域访问（CORS）

## 接口列表

### 1. 任务查看

#### 1.1 获取教师的任务列表
**接口地址**: `GET /api/teacher/tasks`

**功能描述**: 获取分配给当前教师的所有备案任务列表

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| teacherId | Long | 是 | 教师ID |
| status | String | 否 | 任务状态筛选：PENDING_UPLOAD-待上传，SUBMITTED-已提交，REVIEWING-审核中，APPROVED-审核通过，NEED_REVISION-需修改 |

**响应示例**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "courseId": 1,
      "courseCode": "CS101",
      "courseName": "计算机导论",
      "grade": "2021",
      "semester": "秋季",
      "deadline": "2024-12-31T23:59:59",
      "status": "PENDING_UPLOAD",
      "statusDesc": "待上传",
      "description": "任务说明",
      "materialRequirements": "材料要求说明",
      "fileCount": 0,
      "createTime": "2024-01-01T10:00:00",
      "isExpired": false
    }
  ]
}
```

#### 1.2 获取教师的任务详情
**接口地址**: `GET /api/teacher/tasks/{taskId}`

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| teacherId | Long | 是 | 教师ID |

**响应示例**: 同任务列表中的单个任务对象

#### 1.3 提交任务
**接口地址**: `POST /api/teacher/tasks/{taskId}/submit`

**功能描述**: 将任务状态改为"已提交"，进入审核流程

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| teacherId | Long | 是 | 教师ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "任务提交成功",
  "data": null
}
```

---

### 2. 文件上传与管理

#### 2.1 上传单个文件
**接口地址**: `POST /api/files/upload`

**功能描述**: 上传单个文件到指定任务

**请求参数** (multipart/form-data):
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |
| userId | Long | 是 | 用户ID |
| file | MultipartFile | 是 | 文件 |

**文件限制**:
- 支持格式：PDF、DOC、DOCX、XLS、XLSX、JPG、JPEG、PNG、GIF
- 单文件大小：≤ 50MB

**响应示例**:
```json
{
  "code": 200,
  "message": "文件上传成功",
  "data": {
    "id": 1,
    "taskId": 1,
    "fileName": "uuid-generated-name.pdf",
    "originalName": "课程材料.pdf",
    "fileSize": 1024000,
    "fileSizeFormatted": "1.00 MB",
    "fileType": "application/pdf",
    "fileExtension": "pdf",
    "uploadTime": "2024-01-01T10:00:00",
    "status": "UPLOADED",
    "downloadUrl": "/api/files/1/download"
  }
}
```

#### 2.2 批量上传文件
**接口地址**: `POST /api/files/batch-upload`

**功能描述**: 批量上传多个文件到指定任务

**请求参数** (multipart/form-data):
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |
| userId | Long | 是 | 用户ID |
| files | MultipartFile[] | 是 | 文件数组 |

**响应示例**: 返回文件列表数组

#### 2.3 获取任务的文件列表
**接口地址**: `GET /api/files/task/{taskId}`

**功能描述**: 获取指定任务的所有已上传文件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "taskId": 1,
      "fileName": "uuid-generated-name.pdf",
      "originalName": "课程材料.pdf",
      "fileSize": 1024000,
      "fileSizeFormatted": "1.00 MB",
      "fileType": "application/pdf",
      "fileExtension": "pdf",
      "uploadTime": "2024-01-01T10:00:00",
      "status": "UPLOADED",
      "downloadUrl": "/api/files/1/download"
    }
  ]
}
```

#### 2.4 删除文件
**接口地址**: `DELETE /api/files/{fileId}`

**功能描述**: 删除已上传的文件（仅在任务未截止且状态允许时）

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| fileId | Long | 是 | 文件ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "文件删除成功",
  "data": null
}
```

#### 2.5 下载文件
**接口地址**: `GET /api/files/{fileId}/download`

**功能描述**: 下载指定文件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| fileId | Long | 是 | 文件ID |

**响应**: 文件流（二进制数据）

---

### 3. 审核结果与反馈

#### 3.1 获取任务的审核记录列表
**接口地址**: `GET /api/reviews/task/{taskId}`

**功能描述**: 获取指定任务的所有审核记录（历史记录）

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "taskId": 1,
      "assessorId": 4,
      "assessorName": "王评审员",
      "reviewStatus": "APPROVED",
      "reviewStatusDesc": "审核通过",
      "score": 95.50,
      "comment": "材料完整，符合要求",
      "suggestions": null,
      "reviewTime": "2024-01-05T10:00:00"
    }
  ]
}
```

#### 3.2 获取最新的审核记录
**接口地址**: `GET /api/reviews/task/{taskId}/latest`

**功能描述**: 获取指定任务的最新审核记录

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |

**响应示例**: 同审核记录列表中的单个记录对象

---

### 4. 通知管理

#### 4.1 获取用户的通知列表
**接口地址**: `GET /api/notifications`

**功能描述**: 获取用户的所有通知或未读通知

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |
| unreadOnly | Boolean | 否 | 是否只获取未读通知，true-只获取未读，false/null-获取所有 |

**响应示例**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": [
    {
      "id": 1,
      "taskId": 1,
      "type": "NEW_TASK",
      "typeDesc": "新任务",
      "title": "新任务通知",
      "content": "您有一个新的备案任务需要完成",
      "isRead": 0,
      "createTime": "2024-01-01T10:00:00"
    }
  ]
}
```

#### 4.2 标记通知为已读
**接口地址**: `PUT /api/notifications/{notificationId}/read`

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| notificationId | Long | 是 | 通知ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "标记成功",
  "data": null
}
```

#### 4.3 标记所有通知为已读
**接口地址**: `PUT /api/notifications/read-all`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "标记成功",
  "data": null
}
```

#### 4.4 获取未读通知数量
**接口地址**: `GET /api/notifications/unread-count`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": 5
}
```

---

### 5. 个人中心

#### 5.1 获取用户个人信息
**接口地址**: `GET /api/profile`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "id": 2,
    "username": "teacher01",
    "workId": "SWJTU002",
    "realName": "张老师",
    "email": "teacher01@swjtu.edu.cn",
    "phone": "13800138001",
    "department": "计算机与人工智能学院",
    "title": "副教授",
    "role": "USER"
  }
}
```

#### 5.2 更新用户个人信息
**接口地址**: `PUT /api/profile`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |

**请求体**:
```json
{
  "realName": "张老师",
  "email": "teacher01@swjtu.edu.cn",
  "phone": "13800138001",
  "department": "计算机与人工智能学院",
  "title": "副教授"
}
```

**请求体字段说明**:
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| realName | String | 否 | 真实姓名 |
| email | String | 否 | 邮箱 |
| phone | String | 否 | 手机号 |
| department | String | 否 | 院系 |
| title | String | 否 | 职称 |

**响应示例**:
```json
{
  "code": 200,
  "message": "更新成功",
  "data": null
}
```

#### 5.3 修改密码
**接口地址**: `PUT /api/profile/password`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| userId | Long | 是 | 用户ID |

**请求体**:
```json
{
  "oldPassword": "123456",
  "newPassword": "newpassword123",
  "confirmPassword": "newpassword123"
}
```

**请求体字段说明**:
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| oldPassword | String | 是 | 旧密码 |
| newPassword | String | 是 | 新密码（6-20位） |
| confirmPassword | String | 是 | 确认新密码 |

**响应示例**:
```json
{
  "code": 200,
  "message": "密码修改成功",
  "data": null
}
```

---

## 任务状态说明

| 状态值 | 状态描述 | 说明 |
|--------|----------|------|
| PENDING_UPLOAD | 待上传 | 任务已创建，等待教师上传文件 |
| SUBMITTED | 已提交 | 教师已提交文件，等待审核 |
| REVIEWING | 审核中 | 审核员正在审核 |
| APPROVED | 审核通过 | 审核通过，任务完成 |
| NEED_REVISION | 需修改 | 审核未通过，需要教师重新上传文件 |

---

## 通知类型说明

| 类型值 | 类型描述 | 说明 |
|--------|----------|------|
| NEW_TASK | 新任务 | 管理员分配了新任务 |
| REVIEW_RESULT | 审核结果 | 审核员完成了审核 |
| TASK_REMINDER | 任务提醒 | 任务即将截止提醒 |

---

## 文件上传限制

1. **文件格式**: PDF、DOC、DOCX、XLS、XLSX、JPG、JPEG、PNG、GIF
2. **文件大小**: 单文件 ≤ 50MB
3. **上传时机**: 仅在任务状态为"待上传"或"需修改"时允许上传
4. **删除限制**: 仅在任务未截止且状态允许时才能删除文件

---

## 业务规则

1. **文件上传规则**:
   - 任务已截止时，无法上传或删除文件
   - 任务状态为"已提交"、"审核中"、"审核通过"时，无法上传或删除文件
   - 只有任务负责人才能上传和删除文件

2. **任务提交规则**:
   - 任务状态必须为"待上传"或"需修改"才能提交
   - 提交后任务状态自动变为"已提交"

3. **审核流程**:
   - 教师提交任务后，状态变为"已提交"
   - 审核员审核后，状态变为"审核通过"或"需修改"
   - 如果审核未通过，教师可以重新上传文件并再次提交

4. **通知机制**:
   - 新任务分配时，自动创建通知
   - 审核结果产生时，自动创建通知
   - 任务即将截止时，可以创建提醒通知

---

## 注意事项

1. 所有日期时间使用ISO 8601格式（如：2024-12-31T23:59:59）
2. 文件上传使用multipart/form-data格式
3. 文件下载返回二进制流，需要设置正确的Content-Type和Content-Disposition头
4. 文件存储路径在配置文件中设置，默认为`./uploads`
5. 用户ID需要通过请求参数传递（实际项目中应使用JWT Token从请求头获取）

