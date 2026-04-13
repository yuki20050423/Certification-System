# 审核备案模块 - API接口文档

## 概述
本文档描述了审核备案模块（审核员角色使用）的所有API接口。

## 基础信息
- 基础URL: `http://localhost:8080`
- 所有接口支持跨域访问（CORS）

## 接口列表

### 1. 审核任务管理

#### 1.1 获取审核员的任务列表
**接口地址**: `GET /api/assessor/tasks`

**功能描述**: 获取分配给当前审核员的所有任务列表，支持按状态、教师筛选

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| assessorId | Long | 是 | 审核员ID |
| status | String | 否 | 任务状态筛选：PENDING_UPLOAD-待上传，SUBMITTED-待审核，REVIEWING-审核中，APPROVED-审核通过，NEED_REVISION-需修改 |
| teacherId | Long | 否 | 教师ID（按教师筛选） |

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
      "teacherId": 2,
      "teacherWorkId": "SWJTU002",
      "teacherName": "张老师",
      "deadline": "2024-12-31T23:59:59",
      "status": "SUBMITTED",
      "statusDesc": "待审核",
      "description": "任务说明",
      "materialRequirements": "材料要求说明",
      "fileCount": 5,
      "submitTime": "2024-01-02T10:00:00",
      "createTime": "2024-01-01T10:00:00"
    }
  ]
}
```

#### 1.2 获取审核员的任务详情
**接口地址**: `GET /api/assessor/tasks/{taskId}`

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| assessorId | Long | 是 | 审核员ID |

**响应示例**: 同任务列表中的单个任务对象

---

### 2. 审核材料查看

#### 2.1 获取任务的文件列表
**接口地址**: `GET /api/assessor/tasks/{taskId}/files`

**功能描述**: 查看教师上传的所有文件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| assessorId | Long | 是 | 审核员ID |

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
      "downloadUrl": "/api/assessor/files/1/download"
    }
  ]
}
```

#### 2.2 下载文件
**接口地址**: `GET /api/assessor/files/{fileId}/download`

**功能描述**: 下载指定文件

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| fileId | Long | 是 | 文件ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| assessorId | Long | 是 | 审核员ID |

**响应**: 文件流（二进制数据）

#### 2.3 预览文件
**接口地址**: `GET /api/assessor/files/{fileId}/preview`

**功能描述**: 在线预览文件（支持PDF、图片）

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| fileId | Long | 是 | 文件ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| assessorId | Long | 是 | 审核员ID |

**响应**: 文件流（二进制数据，Content-Disposition: inline）

**支持的文件类型**:
- PDF文件
- 图片文件（JPG、JPEG、PNG、GIF）

---

### 3. 审核操作功能

#### 3.1 提交审核
**接口地址**: `POST /api/assessor/review`

**功能描述**: 提交审核结果（审核通过/需修改）

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| assessorId | Long | 是 | 审核员ID |

**请求体**:
```json
{
  "taskId": 1,
  "reviewStatus": "APPROVED",
  "score": 95.50,
  "comment": "材料完整，符合要求，审核通过",
  "suggestions": null
}
```

**请求体字段说明**:
| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |
| reviewStatus | String | 是 | 审核状态：APPROVED-审核通过，REJECTED-需修改 |
| score | BigDecimal | 否 | 评分（0-100） |
| comment | String | 否 | 审核意见 |
| suggestions | String | 否 | 修改建议（当reviewStatus为REJECTED时必填） |

**业务规则**:
1. 审核状态为"REJECTED"时，必须填写修改建议
2. 提交审核后，任务状态自动更新：
   - APPROVED -> 任务状态变为"审核通过"
   - REJECTED -> 任务状态变为"需修改"
3. 审核完成后，自动发送通知给教师

**响应示例**:
```json
{
  "code": 200,
  "message": "审核提交成功",
  "data": null
}
```

**错误响应示例**:
```json
{
  "code": 500,
  "message": "审核拒绝时必须填写修改建议",
  "data": null
}
```

---

### 4. 审核历史管理

#### 4.1 获取审核员的审核记录列表
**接口地址**: `GET /api/assessor/reviews`

**功能描述**: 获取审核员的所有审核记录（历史记录）

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| assessorId | Long | 是 | 审核员ID |

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

#### 4.2 获取任务的审核记录列表
**接口地址**: `GET /api/assessor/tasks/{taskId}/reviews`

**功能描述**: 获取指定任务的所有审核记录（支持重新审核场景）

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| taskId | Long | 是 | 任务ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| assessorId | Long | 是 | 审核员ID |

**响应示例**: 同审核记录列表中的单个记录对象数组

---

### 5. 审核统计功能

#### 5.1 获取审核统计信息
**接口地址**: `GET /api/assessor/statistics`

**功能描述**: 获取审核员的审核统计信息（通过率、处理数量等）

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| assessorId | Long | 是 | 审核员ID |

**响应示例**:
```json
{
  "code": 200,
  "message": "查询成功",
  "data": {
    "totalTasks": 50,
    "pendingTasks": 10,
    "reviewingTasks": 5,
    "approvedTasks": 25,
    "needRevisionTasks": 10,
    "approvalRate": 71.43,
    "processedTasks": 35
  }
}
```

**统计字段说明**:
| 字段名 | 说明 |
|--------|------|
| totalTasks | 总任务数（分配给该审核员的所有任务） |
| pendingTasks | 待审核任务数（状态为"已提交"） |
| reviewingTasks | 审核中任务数（状态为"审核中"） |
| approvedTasks | 审核通过任务数（状态为"审核通过"） |
| needRevisionTasks | 需修改任务数（状态为"需修改"） |
| approvalRate | 通过率（百分比，已处理任务中通过的比例） |
| processedTasks | 已处理任务数（审核通过 + 需修改） |

---

## 审核状态说明

| 审核状态值 | 状态描述 | 说明 |
|-----------|----------|------|
| APPROVED | 审核通过 | 材料符合要求，审核通过 |
| REJECTED | 需修改 | 材料不符合要求，需要教师修改后重新提交 |

---

## 文件验证规则

审核员在查看文件时，系统会自动验证：

1. **文件格式验证**:
   - 支持格式：PDF、DOC、DOCX、XLS、XLSX、JPG、JPEG、PNG、GIF
   - 不符合格式的文件会在上传时被拒绝

2. **文件大小验证**:
   - 单文件大小限制：≤ 50MB
   - 超过限制的文件会在上传时被拒绝

3. **文件预览支持**:
   - PDF文件：支持在线预览
   - 图片文件（JPG、JPEG、PNG、GIF）：支持在线预览
   - 其他格式：仅支持下载

---

## 审核流程说明

1. **任务分配**: 管理员创建任务并指派审核员
2. **教师提交**: 教师上传文件并提交任务，状态变为"已提交"
3. **审核员审核**: 
   - 查看任务详情和上传的文件
   - 预览或下载文件进行审核
   - 填写审核意见、评分、修改建议
   - 提交审核结果
4. **审核结果处理**:
   - 审核通过：任务状态变为"审核通过"，通知教师
   - 需修改：任务状态变为"需修改"，通知教师并附上修改建议
5. **重新审核**: 教师修改后重新提交，审核员可以再次审核

---

## 通知机制

审核完成后，系统会自动发送通知给教师：

1. **审核通过通知**:
   - 通知类型：REVIEW_RESULT
   - 通知内容：包含审核结果和审核意见

2. **需修改通知**:
   - 通知类型：REVIEW_RESULT
   - 通知内容：包含审核结果和修改建议

---

## 业务规则

1. **权限验证**:
   - 审核员只能查看和审核分配给自己的任务
   - 审核员只能查看自己审核过的记录

2. **审核限制**:
   - 只有状态为"已提交"或"需修改"的任务才能审核
   - 审核拒绝时必须填写修改建议

3. **重新审核**:
   - 对于"需修改"后重新提交的任务，审核员可以再次审核
   - 每次审核都会创建新的审核记录，保留审核历史

4. **统计计算**:
   - 通过率 = 审核通过任务数 / 已处理任务数 × 100%
   - 已处理任务数 = 审核通过任务数 + 需修改任务数

---

## 注意事项

1. 所有日期时间使用ISO 8601格式（如：2024-12-31T23:59:59）
2. 文件预览仅支持PDF和图片格式，其他格式需要下载查看
3. 审核员ID需要通过请求参数传递（实际项目中应使用JWT Token从请求头获取）
4. 审核提交后，任务状态会自动更新，无需额外操作
5. 审核记录会永久保存，支持查看历史审核详情

