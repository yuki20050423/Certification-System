// teacher.js - 教师端核心逻辑
const API_BASE_URL = 'http://localhost:8080/api';

let currentView = 'tasks';
let currentTaskId = null;
let currentTaskStatus = null;
let currentTaskDeadline = null;

// ==================== 工具函数 ====================
function getCurrentTeacherId() {
    const userInfoStr = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo');
    if (!userInfoStr) {
        console.error('未找到用户信息，请重新登录');
        setTimeout(() => window.location.href = 'login.html', 2000);
        return null;
    }
    try {
        const userInfo = JSON.parse(userInfoStr);
        return userInfo.id;
    } catch (e) {
        console.error('解析用户信息失败', e);
        return null;
    }
}

function showToast(message, type = 'success') {
    console.log('showToast:', message, type); // 调试日志
    const toast = $('#toastMessage');
    if (toast.length === 0) {
        alert(message);
        return;
    }
    toast.text(message).removeClass().addClass('toast-message show ' + type).fadeIn(200);
    setTimeout(() => toast.fadeOut(200), 3000);
}

function formatDateTime(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleString('zh-CN', {
        year: 'numeric', month: '2-digit', day: '2-digit',
        hour: '2-digit', minute: '2-digit'
    });
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
}

function getStatusClass(status) {
    switch (status) {
        case 'PENDING_UPLOAD': return 'pending';
        case 'SUBMITTED': return 'submitted';
        case 'REVIEWING': return 'reviewing';
        case 'PENDING_REVIEW': return 'submitted';
        case 'APPROVED': return 'approved';
        case 'NEED_REVISION': return 'need-revision';
        default: return '';
    }
}

function getStatusDesc(status) {
    const map = {
        'PENDING_UPLOAD': '待上传',
        'SUBMITTED': '待审核',
        'REVIEWING': '审核中',
        'PENDING_REVIEW': '待审核',
        'APPROVED': '审核通过',
        'NEED_REVISION': '需修改'
    };
    return map[status] || status;
}

// ==================== 页面初始化 ====================
$(document).ready(function () {
    console.log('teacher.js 已加载，初始化页面...');
    initTeacherPage();
});

function initTeacherPage() {
    checkTeacherAuth();
    initNavigation();
    initUserMenu();
    loadTasks();
    loadUnreadCount();

    // 批量上传按钮点击事件 - 动态创建原生 input
    $('#batchUploadBtn').off('click').on('click', function (e) {
        e.preventDefault();
        console.log('【批量上传按钮点击】时间戳:', Date.now());

        const teacherId = getCurrentTeacherId();
        if (!teacherId) {
            showToast('未获取到教师ID，请重新登录', 'error');
            return;
        }
        if (!currentTaskId) {
            showToast('请先选择任务', 'error');
            return;
        }

        // 创建原生文件输入框
        const input = document.createElement('input');
        input.type = 'file';
        input.multiple = true;
        input.accept = '.pdf,.doc,.docx,.xls,.xlsx,.jpg,.jpeg,.png';
        input.style.display = 'none';
        document.body.appendChild(input);

        // 监听 change 事件（选择文件后触发）
        input.addEventListener('change', function (e) {
            console.log('【input change 触发】时间戳:', Date.now(), '事件对象:', e);
            const files = Array.from(this.files);
            if (files.length === 0) {
                console.warn('未选择任何文件');
                document.body.removeChild(input);
                return;
            }
            console.log('选择的文件数:', files.length);
            // 调用上传函数
            batchUploadFiles(files);
            // 移除 input 元素，避免累积
            document.body.removeChild(input);
        });

        // 模拟点击打开文件选择器
        input.click();
    });
}

function checkTeacherAuth() {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');
    const userInfoStr = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo');
    if (!token || !userInfoStr) {
        window.location.href = 'login.html';
        return;
    }
    const user = JSON.parse(userInfoStr);
    if (!user.role || user.role.toUpperCase() !== 'TEACHER') {
        alert('您没有教师权限，请使用教师账号登录');
        window.location.href = 'index.html';
        return;
    }
    $('#userName').text(user.realName || user.username);
    $('#userRole').text('教师');
}

function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('userInfo');
    window.location.href = 'login.html';
}

function initUserMenu() {
    $('.user-avatar').on('click', function (e) {
        e.stopPropagation();
        $('#userDropdown').toggleClass('show');
    });
    $(document).on('click', function (e) {
        if (!$(e.target).closest('.user-menu').length) {
            $('#userDropdown').removeClass('show');
        }
    });
    $('#logoutBtn').on('click', function (e) {
        e.preventDefault();
        logout();
    });
}

function initNavigation() {
    $('.menu-item').on('click', function () {
        const view = $(this).data('view');
        switchView(view);
    });
}

function switchView(view) {
    currentView = view;
    $('.menu-item').removeClass('active');
    $(`.menu-item[data-view="${view}"]`).addClass('active');
    $('.content-view').hide();
    $(`#${view}-view`).show();

    switch (view) {
        case 'tasks': loadTasks(); break;
        case 'reviews': loadReviews(); break;
        case 'notifications': loadNotifications(); break;
        case 'profile': loadProfile(); break;
        case 'statistics': loadStatistics(); break;
    }
}

async function loadStatistics() {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/teacher/tasks/statistics?teacherId=${teacherId}`);
        const data = await response.json();
        if (data.code === 200) {
            const stats = data.data;
            $('#statTotalTasks').text(stats.totalTasks || 0);
            $('#statPendingUpload').text(stats.pendingUploadTasks || 0);
            $('#statApproved').text(stats.approvedTasks || 0);
            $('#statNeedRevision').text(stats.needRevisionTasks || 0);
            $('#statCompletionRate').text((stats.completionRate || 0) + '%');
            $('#statReviewing').text(stats.reviewingTasks || 0);
        } else {
            console.error('获取统计失败:', data.msg);
        }
    } catch (error) {
        console.error('加载统计失败:', error);
    }
}

// ==================== 任务列表 ====================
async function loadTasks() {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    const status = $('#taskStatusFilter').val();
    const sortBy = $('#taskSortBy').val();
    const [sortField, sortOrder] = sortBy.split('_');

    $('#tasksTableBody').html(`
        <tr><td colspan="6" class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载中...</td></tr>
    `);

    try {
        const url = new URL(`${API_BASE_URL}/teacher/tasks`);
        url.searchParams.append('teacherId', teacherId);
        if (status) url.searchParams.append('status', status);
        if (sortField) url.searchParams.append('sortBy', sortField);
        if (sortOrder) url.searchParams.append('order', sortOrder);

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            renderTasks(data.data || []);
        } else {
            $('#tasksTableBody').html(`<tr><td colspan="6" class="loading-row"><i class="fas fa-exclamation-circle"></i> ${data.msg || '加载失败'}</td></tr>`);
        }
    } catch (error) {
        console.error('加载任务失败:', error);
        $('#tasksTableBody').html('<tr><td colspan="6" class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误，请稍后重试</td></tr>');
    }
}

function renderTasks(tasks) {
    const tbody = $('#tasksTableBody');
    if (!tasks || tasks.length === 0) {
        tbody.html('<tr><td colspan="6" class="loading-row"><i class="fas fa-inbox"></i> 暂无任务</td></tr>');
        return;
    }
    let html = '';
    tasks.forEach(task => {
        html += `
            <tr>
                <td>${task.courseName || '-'}</td>
                <td>${task.teachingClass || '-'}</td>
                <td>${task.assessorName || '-'}</td>
                <td>${formatDateTime(task.deadline)}</td>
                <td><span class="status-badge ${getStatusClass(task.status)}">${getStatusDesc(task.status)}</span></td>
                <td>
                    <button class="btn secondary-btn btn-sm" onclick="viewTaskDetail(${task.id})">
                        <i class="fas fa-eye"></i> 详情
                    </button>
                    ${task.status === 'PENDING_UPLOAD' || task.status === 'NEED_REVISION' ? `
                        <button class="btn primary-btn btn-sm" onclick="viewTaskDetail(${task.id})">
                            <i class="fas fa-upload"></i> 上传
                        </button>
                    ` : ''}
                </td>
            </tr>
        `;
    });
    tbody.html(html);
}

// ==================== 任务详情 ====================
async function viewTaskDetail(taskId) {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    currentTaskId = taskId;
    $('#tasks-view').hide();
    $('#task-detail-view').show();

    try {
        const response = await fetch(`${API_BASE_URL}/teacher/tasks/${taskId}?teacherId=${teacherId}`);
        const data = await response.json();

        if (data.code === 200) {
            const task = data.data;
            currentTaskStatus = task.status;
            currentTaskDeadline = task.deadline;

            $('#detailCourseName').text(task.courseName || '-');
            $('#detailTeachingClass').text(task.teachingClass || '-');
            $('#detailAssessor').text(task.assessorName || '-');
            $('#detailDeadline').text(formatDateTime(task.deadline));
            $('#detailStatus').text(getStatusDesc(task.status)).attr('class', 'status-badge ' + getStatusClass(task.status));
            $('#detailDescription').text(task.description || '无');
            $('#detailMaterialReq').text(task.materialRequirements || '无');

            const canUpload = task.status === 'PENDING_UPLOAD' || task.status === 'NEED_REVISION';
            $('#uploadSection').toggle(canUpload);
            $('#submitSection').toggle(canUpload);

            loadTaskFiles(taskId);
            loadLatestReview(taskId);
        } else {
            showToast('加载任务详情失败', 'error');
        }
    } catch (error) {
        console.error('加载任务详情失败:', error);
        showToast('网络错误，请稍后重试', 'error');
    }
}

$('#backToTasksBtn').on('click', function () {
    $('#task-detail-view').hide();
    $('#tasks-view').show();
    currentTaskId = null;
});

// ==================== 文件管理 ====================
async function loadTaskFiles(taskId) {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    $('#filesTableBody').html('<tr><td colspan="4" class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载文件列表...</td></tr>');

    try {
        const response = await fetch(`${API_BASE_URL}/files/task/${taskId}?teacherId=${teacherId}`);
        const data = await response.json();

        if (data.code === 200) {
            renderFiles(data.data || []);
            const canSubmit = (currentTaskStatus === 'PENDING_UPLOAD' || currentTaskStatus === 'NEED_REVISION') && data.data.length > 0;
            $('#submitTaskBtn').prop('disabled', !canSubmit);
        } else {
            $('#filesTableBody').html('<tr><td colspan="4" class="loading-row"><i class="fas fa-exclamation-circle"></i> 加载失败</td></tr>');
        }
    } catch (error) {
        console.error('加载文件列表失败:', error);
        $('#filesTableBody').html('<tr><td colspan="4" class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误</td></tr>');
    }
}

function renderFiles(files) {
    const tbody = $('#filesTableBody');
    if (!files || files.length === 0) {
        tbody.html('<tr><td colspan="4" class="loading-row"><i class="fas fa-inbox"></i> 暂无文件</td></tr>');
        return;
    }

    const now = new Date();
    const deadline = new Date(currentTaskDeadline);
    const canDelete = (currentTaskStatus === 'PENDING_UPLOAD' || currentTaskStatus === 'NEED_REVISION') && now <= deadline;

    let html = '';
    files.forEach(file => {
        html += `
            <tr>
                <td><i class="fas fa-file"></i> ${file.fileName}</td>
                <td>${file.fileSizeFormatted || formatFileSize(file.fileSize)}</td>
                <td>${formatDateTime(file.uploadTime)}</td>
                <td>
                    <button class="btn-icon" onclick="downloadFile(${file.id}, '${file.fileName}')" title="下载">
                        <i class="fas fa-download"></i>
                    </button>
                    ${canDelete ? `
                        <button class="btn-icon danger" onclick="deleteFile(${file.id})" title="删除">
                            <i class="fas fa-trash"></i>
                        </button>
                    ` : ''}
                </td>
            </tr>
        `;
    });
    tbody.html(html);
}

window.downloadFile = function (fileId, fileName) {
    const teacherId = getCurrentTeacherId();
    window.open(`${API_BASE_URL}/files/${fileId}/download?teacherId=${teacherId}`, '_blank');
};

window.deleteFile = async function (fileId) {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;
    if (!confirm('确定要删除该文件吗？此操作不可恢复。')) return;

    try {
        const response = await fetch(`${API_BASE_URL}/files/${fileId}?teacherId=${teacherId}`, {
            method: 'DELETE'
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('文件删除成功');
            loadTaskFiles(currentTaskId);
        } else {
            showToast(data.msg || '删除失败', 'error');
        }
    } catch (error) {
        console.error('删除文件失败:', error);
        showToast('网络错误，请稍后重试', 'error');
    }
};

// ==================== 文件上传（单文件）====================
async function uploadFile(file) {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;
    if (!currentTaskId) {
        showToast('请先选择任务', 'error');
        return;
    }
    if (file.size > 50 * 1024 * 1024) {
        showToast('文件大小不能超过50MB', 'error');
        return;
    }

    const formData = new FormData();
    formData.append('file', file);
    formData.append('taskId', currentTaskId);
    formData.append('teacherId', teacherId);

    $('#singleUploadBtn').prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 上传中...');

    try {
        console.log('开始上传文件:', file.name);
        console.log('taskId:', currentTaskId, 'teacherId:', teacherId);

        const response = await fetch(`${API_BASE_URL}/files/upload`, {
            method: 'POST',
            body: formData
        });
        console.log('上传响应状态:', response.status);

        let data;
        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            data = await response.json();
            console.log('上传响应数据:', data);
        } else {
            const text = await response.text();
            console.error('非JSON响应:', text);
            showToast('服务器返回格式错误，请查看控制台', 'error');
            return;
        }

        if (data.code === 200) {
            showToast('文件上传成功');
            loadTaskFiles(currentTaskId);
        } else {
            showToast('上传失败: ' + (data.msg || '未知错误'), 'error');
            console.error('上传失败详情:', data);
        }
    } catch (error) {
        console.error('上传异常:', error);
        showToast('网络错误或服务器异常: ' + error.message, 'error');
    } finally {
        $('#singleUploadBtn').prop('disabled', false).html('<i class="fas fa-file-upload"></i> 上传选中文件');
    }
}

// ==================== 批量上传（增强版，带重试和详细日志）====================
async function batchUploadFiles(files) {
    console.log('【batchUploadFiles 被调用】时间戳:', Date.now(), '文件数:', files.length);
    files.forEach((f, i) => console.log(`  文件[${i}]: ${f.name}, 大小: ${f.size} bytes`));

    const teacherId = getCurrentTeacherId();
    if (!teacherId) {
        console.error('teacherId 为空');
        showToast('未获取到教师ID', 'error');
        return;
    }
    if (!currentTaskId) {
        console.error('currentTaskId 为空');
        showToast('请先选择任务', 'error');
        return;
    }

    // 过滤超限文件（50MB）
    const MAX_SIZE = 50 * 1024 * 1024;
    const validFiles = files.filter(f => f.size <= MAX_SIZE);
    if (validFiles.length === 0) {
        console.warn('所有文件均超过50MB');
        showToast('所有文件均超过50MB限制', 'error');
        return;
    }
    if (validFiles.length < files.length) {
        console.warn(`过滤掉 ${files.length - validFiles.length} 个超限文件`);
        showToast(`已过滤 ${files.length - validFiles.length} 个超限文件`, 'warning');
    }

    // 准备 FormData
    const formData = new FormData();
    validFiles.forEach(file => formData.append('files', file));
    formData.append('taskId', currentTaskId);
    formData.append('teacherId', teacherId);

    // 禁用按钮，显示上传中
    const $btn = $('#batchUploadBtn');
    const originalText = $btn.html();
    $btn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 上传中...');

    try {
        console.log('发送请求至 /files/batch-upload');
        const response = await fetch(`${API_BASE_URL}/files/batch-upload`, {
            method: 'POST',
            body: formData
        });
        console.log('响应状态:', response.status);

        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            const data = await response.json();
            console.log('响应数据:', data);
            if (data.code === 200) {
                showToast(data.msg || '批量上传成功');
                // 刷新文件列表
                loadTaskFiles(currentTaskId);
            } else {
                showToast('上传失败: ' + (data.msg || '未知错误'), 'error');
            }
        } else {
            const text = await response.text();
            console.error('非JSON响应:', text);
            showToast('服务器返回格式错误', 'error');
        }
    } catch (error) {
        console.error('上传异常:', error);
        showToast('网络错误: ' + error.message, 'error');
    } finally {
        $btn.prop('disabled', false).html(originalText);
    }
}

// ==================== 提交任务 ====================
$('#submitTaskBtn').on('click', async function () {
    const teacherId = getCurrentTeacherId();
    if (!teacherId || !currentTaskId) return;

    console.log('开始提交任务:', currentTaskId, '教师ID:', teacherId);

    $('#submitTaskBtn').prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 提交中...');

    try {
        const response = await fetch(`${API_BASE_URL}/teacher/tasks/${currentTaskId}/submit?teacherId=${teacherId}`, {
            method: 'POST'
        });
        console.log('提交响应状态:', response.status);

        let errorMsg = '未知错误';
        const contentType = response.headers.get('content-type');

        if (contentType && contentType.includes('application/json')) {
            const data = await response.json();
            console.log('提交响应数据:', data);
            if (data.code === 200) {
                showToast('任务提交成功，等待审核');
                viewTaskDetail(currentTaskId);
                loadTasks();
                return;
            } else {
                errorMsg = data.msg || errorMsg;
            }
        } else {
            const text = await response.text();
            console.error('非JSON响应:', text);
            if (text) {
                errorMsg = text;
            }
        }

        if (!response.ok) {
            errorMsg = `HTTP ${response.status}: ${response.statusText}`;
        }

        showToast('提交失败: ' + errorMsg, 'error');
    } catch (error) {
        console.error('提交任务异常:', error);
        showToast('网络错误或服务器异常: ' + error.message, 'error');
    } finally {
        $('#submitTaskBtn').prop('disabled', false).html('<i class="fas fa-paper-plane"></i> 提交审核');
    }
});

// ==================== 审核记录 ====================
async function loadLatestReview(taskId) {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    $('#latestReviewContent').html('<div class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载审核记录...</div>');

    try {
        const response = await fetch(`${API_BASE_URL}/reviews/task/${taskId}/latest?teacherId=${teacherId}`);
        const data = await response.json();

        if (data.code === 200) {
            const review = data.data;
            if (review) {
                let html = `
                    <div class="review-info-row">
                        <div class="info-label">审核时间：</div>
                        <div class="info-value">${formatDateTime(review.reviewTime)}</div>
                    </div>
                    <div class="review-info-row">
                        <div class="info-label">审核结果：</div>
                        <div class="info-value"><span class="status-badge ${review.reviewStatus === 'APPROVED' ? 'approved' : 'need-revision'}">${review.reviewStatusDesc || (review.reviewStatus === 'APPROVED' ? '审核通过' : '需修改')}</span></div>
                    </div>
                `;
                if (review.score) {
                    html += `<div class="review-info-row"><div class="info-label">评分：</div><div class="info-value">${review.score}</div></div>`;
                }
                if (review.comment) {
                    html += `<div class="review-info-row"><div class="info-label">审核意见：</div><div class="info-value">${review.comment}</div></div>`;
                }
                if (review.suggestions) {
                    html += `<div class="review-info-row"><div class="info-label">修改建议：</div><div class="info-value">${review.suggestions}</div></div>`;
                }
                $('#latestReviewContent').html(html);
            } else {
                $('#latestReviewContent').html('<div class="loading-row"><i class="fas fa-info-circle"></i> 暂无审核记录</div>');
            }
        } else {
            $('#latestReviewContent').html('<div class="loading-row"><i class="fas fa-exclamation-circle"></i> 加载失败</div>');
        }
    } catch (error) {
        console.error('加载审核记录失败:', error);
        $('#latestReviewContent').html('<div class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误</div>');
    }
}

async function loadReviews() {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    $('#reviewsTableBody').html('<tr><td colspan="7" class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载中...</td></tr>');

    try {
        // 1. 获取教师的任务列表（用于填充筛选下拉框）
        const tasksRes = await fetch(`${API_BASE_URL}/teacher/tasks?teacherId=${teacherId}`);
        const tasksData = await tasksRes.json();
        const tasks = tasksData.data || [];

        // 2. 获取该教师的所有审核记录（新接口）
        const reviewsRes = await fetch(`${API_BASE_URL}/reviews/teacher?teacherId=${teacherId}`);
        const reviewsData = await reviewsRes.json();

        if (reviewsData.code === 200) {
            let allReviews = reviewsData.data || [];

            // 3. 根据筛选框的值过滤（如果选择了特定任务）
            const selectedTaskId = $('#reviewTaskFilter').val();
            if (selectedTaskId) {
                allReviews = allReviews.filter(r => r.taskId == selectedTaskId);
            }

            // 4. 按审核时间倒序排列
            allReviews.sort((a, b) => new Date(b.reviewTime) - new Date(a.reviewTime));

            // 5. 渲染表格
            renderReviews(allReviews);
        } else {
            $('#reviewsTableBody').html(`<tr><td colspan="7" class="loading-row"><i class="fas fa-exclamation-circle"></i> ${reviewsData.msg || '加载失败'}</td></tr>`);
        }

        // 6. 更新任务筛选下拉框
        const taskOptions = ['<option value="">全部任务</option>'];
        tasks.forEach(t => {
            taskOptions.push(`<option value="${t.id}">${t.courseName} (${t.teachingClass})</option>`);
        });
        $('#reviewTaskFilter').html(taskOptions.join(''));

    } catch (error) {
        console.error('加载审核记录失败:', error);
        $('#reviewsTableBody').html('<tr><td colspan="7" class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误</td></tr>');
    }
}

function renderReviews(reviews) {
    const tbody = $('#reviewsTableBody');
    if (!reviews || reviews.length === 0) {
        tbody.html('<tr><td colspan="7" class="loading-row"><i class="fas fa-inbox"></i> 暂无审核记录</td></tr>');
        return;
    }

    let html = '';
    reviews.forEach(r => {
        html += `
            <tr>
                <td>${r.courseName || '-'}</td>
                <td>${r.teachingClass || '-'}</td>
                <td>${formatDateTime(r.reviewTime)}</td>
                <td><span class="status-badge ${r.reviewStatus === 'APPROVED' ? 'approved' : 'need-revision'}">${r.reviewStatusDesc || (r.reviewStatus === 'APPROVED' ? '审核通过' : '需修改')}</span></td>
                <td>${r.score || '-'}</td>
                <td>${r.comment || '-'}</td>
                <td>${r.suggestions || '-'}</td>
            </tr>
        `;
    });
    tbody.html(html);
}

$('#reviewTaskFilter').on('change', loadReviews);
$('#refreshReviewsBtn').on('click', loadReviews);

// ==================== 通知中心 ====================
async function loadNotifications() {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    $('#notificationsList').html('<div class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载通知列表...</div>');

    try {
        const response = await fetch(`${API_BASE_URL}/notifications?teacherId=${teacherId}`);
        const data = await response.json();

        if (data.code === 200) {
            renderNotifications(data.data || []);
        } else {
            $('#notificationsList').html('<div class="loading-row"><i class="fas fa-exclamation-circle"></i> 加载失败</div>');
        }
    } catch (error) {
        console.error('加载通知失败:', error);
        $('#notificationsList').html('<div class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误</div>');
    }
}

function renderNotifications(notifications) {
    const container = $('#notificationsList');
    if (!notifications || notifications.length === 0) {
        container.html('<div class="loading-row"><i class="fas fa-inbox"></i> 暂无通知</div>');
        return;
    }

    let html = '';
    notifications.forEach(n => {
        const typeClass = n.type === 'NEW_TASK' ? 'new-task' : (n.type === 'REVIEW_RESULT' ? 'review-result' : 'task-reminder');
        html += `
            <div class="notification-item ${n.isRead === 0 ? 'unread' : ''}" data-id="${n.id}">
                <div class="notification-header">
                    <span class="notification-type ${typeClass}">${n.typeDesc || n.type}</span>
                    <span class="notification-time">${formatDateTime(n.createTime)}</span>
                </div>
                <div class="notification-title">${n.title}</div>
                <div class="notification-content">${n.content}</div>
                <div class="notification-footer">
                    ${n.isRead === 0 ? `<button class="btn btn-sm secondary-btn mark-read-btn" data-id="${n.id}">标记已读</button>` : ''}
                </div>
            </div>
        `;
    });
    container.html(html);

    $('.mark-read-btn').on('click', function () {
        const notiId = $(this).data('id');
        markAsRead(notiId);
    });
}

async function markAsRead(notificationId) {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/notifications/${notificationId}/read?teacherId=${teacherId}`, {
            method: 'PUT'
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('已标记已读');
            loadNotifications();
            loadUnreadCount();
        } else {
            showToast(data.msg || '标记失败', 'error');
        }
    } catch (error) {
        console.error('标记已读失败:', error);
        showToast('网络错误', 'error');
    }
}

$('#markAllReadBtn').on('click', async function () {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/notifications/read-all?teacherId=${teacherId}`, {
            method: 'PUT'
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('全部通知已标记已读');
            loadNotifications();
            loadUnreadCount();
        } else {
            showToast(data.msg || '操作失败', 'error');
        }
    } catch (error) {
        console.error('全部标记已读失败:', error);
        showToast('网络错误', 'error');
    }
});

async function loadUnreadCount() {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/notifications/unread-count?teacherId=${teacherId}`);
        const data = await response.json();
        if (data.code === 200) {
            const count = data.data || 0;
            $('#unreadCount').text(count > 0 ? count : '0');
            // 控制导航栏小红点
            if (count > 0) {
                $('#navUnreadDot').addClass('visible');
            } else {
                $('#navUnreadDot').removeClass('visible');
            }
        }
    } catch (error) {
        console.error('获取未读数量失败:', error);
    }
}

// ==================== 个人中心 ====================
async function loadProfile() {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/profile?teacherId=${teacherId}`);
        const data = await response.json();

        if (data.code === 200) {
            const profile = data.data;
            $('#profileRealName').val(profile.name || '');
            $('#profileWorkId').val(profile.workNo || '');
            $('#profileDepartment').val(profile.department || '');
            $('#profileTitle').val(profile.title || '');
            $('#profileEmail').val(profile.email || '');
            $('#profilePhone').val(profile.phone || '');
        } else {
            showToast('加载个人信息失败', 'error');
        }
    } catch (error) {
        console.error('加载个人信息失败:', error);
        showToast('网络错误', 'error');
    }
}

$('#saveProfileBtn').on('click', async function () {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    const email = $('#profileEmail').val().trim();
    const phone = $('#profilePhone').val().trim();
    const department = $('#profileDepartment').val().trim();
    const title = $('#profileTitle').val();

    if (email && !/^\S+@\S+\.\S+$/.test(email)) {
        showToast('邮箱格式不正确', 'error');
        return;
    }
    if (phone && !/^1[3-9]\d{9}$/.test(phone)) {
        showToast('手机号格式不正确', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/profile?teacherId=${teacherId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, phone, department, title })
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('个人信息更新成功');
            loadProfile();
        } else {
            showToast(data.msg || '更新失败', 'error');
        }
    } catch (error) {
        console.error('更新个人信息失败:', error);
        showToast('网络错误', 'error');
    }
});

$('#changePasswordBtn').on('click', async function () {
    const teacherId = getCurrentTeacherId();
    if (!teacherId) return;

    const oldPwd = $('#oldPassword').val();
    const newPwd = $('#newPassword').val();
    const confirmPwd = $('#confirmPassword').val();

    if (!oldPwd || !newPwd || !confirmPwd) {
        showToast('请完整填写密码', 'error');
        return;
    }
    if (newPwd.length < 6) {
        showToast('新密码至少6位', 'error');
        return;
    }
    if (newPwd !== confirmPwd) {
        showToast('两次输入的新密码不一致', 'error');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/profile/password?teacherId=${teacherId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                oldPassword: oldPwd,
                newPassword: newPwd,
                confirmPassword: confirmPwd
            })
        });
        const data = await response.json();

        if (data.code === 200) {
            showToast('密码修改成功，请重新登录');
            setTimeout(() => logout(), 2000);
        } else {
            showToast(data.msg || '修改失败', 'error');
        }
    } catch (error) {
        console.error('修改密码失败:', error);
        showToast('网络错误', 'error');
    }
});

$('.profile-tab').on('click', function () {
    const tab = $(this).data('tab');
    $('.profile-tab').removeClass('active');
    $(this).addClass('active');
    $('.profile-panel').removeClass('active');
    $(`#${tab}-profile`).addClass('active');
});

// ==================== 事件绑定（筛选、刷新） ====================
$('#taskStatusFilter, #taskSortBy').on('change', loadTasks);
$('#refreshTasksBtn').on('click', loadTasks);


