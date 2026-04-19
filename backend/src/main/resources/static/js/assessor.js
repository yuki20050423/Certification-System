// assessor.js - 审核员端核心逻辑
const API_BASE_URL = 'http://localhost:8080/api';

let currentView = 'tasks';
let currentTaskId = null;
let currentTaskRequiredItems = [];
let currentAssignedReviewProjects = [];

const ITEM_DESC_MAP = {
    "1": "教材封面及目录",
    "2": "课程大纲",
    "3": "电子教案",
    "4": "课程评分标准",
    "5": "课程目标达成度评价表",
    "6": "空白试卷",
    "7": "试卷参考答案及评分标准",
    "8": "15份学生试卷",
    "9": "成绩单(平时成绩、总评成绩)",
    "10": "成绩分析表",
    "11": "课程设计报告",
    "12": "作业"
};

// ==================== 工具函数 ====================
function getCurrentAssessorId() {
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
    const toast = $('#toastMessage');
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
        'APPROVED': '审核通过',
        'NEED_REVISION': '需修改'
    };
    return map[status] || status;
}

function escapeHtml(value) {
    if (value === null || value === undefined) return '';
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function parseRequiredItems(materialReq) {
    if (!materialReq) return [];

    const items = [];
    const added = new Set();

    materialReq.split(/[,，;；\n]+/).forEach(token => {
        const match = token.trim().match(/(1[0-2]|[1-9])/);
        const code = match ? match[1] : null;
        if (code && ITEM_DESC_MAP[code] && !added.has(code)) {
            items.push({ code, name: ITEM_DESC_MAP[code] });
            added.add(code);
        }
    });

    if (items.length === 0) {
        Object.entries(ITEM_DESC_MAP).forEach(([code, name]) => {
            if (materialReq.includes(name) && !added.has(code)) {
                items.push({ code, name });
                added.add(code);
            }
        });
    }

    return items;
}

function formatMaterialRequirements(materialReq) {
    if (!materialReq) return '无';
    const items = parseRequiredItems(materialReq);
    if (!items.length) return materialReq;
    return items.map(item => `${item.code}-${item.name}`).join('；');
}

function groupFilesByItem(files) {
    const grouped = new Map();
    (files || []).forEach(file => {
        const key = file.itemCode || '__default__';
        if (!grouped.has(key)) {
            grouped.set(key, []);
        }
        grouped.get(key).push(file);
    });
    return grouped;
}

// ==================== 页面初始化 ====================
$(document).ready(function () {
    console.log('assessor.js 已加载，初始化页面...');
    initAssessorPage();
});

function initAssessorPage() {
    checkAssessorAuth();
    initNavigation();
    initUserMenu();
    loadTasks();
    loadUnreadCount();
}

function checkAssessorAuth() {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');
    const userInfoStr = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo');
    if (!token || !userInfoStr) {
        window.location.href = 'login.html';
        return;
    }
    const user = JSON.parse(userInfoStr);
    if (!user.role || user.role.toUpperCase() !== 'ASSESSOR') {
        alert('您没有审核员权限，请使用审核员账号登录');
        window.location.href = 'index.html';
        return;
    }
    $('#userName').text(user.realName || user.username);
    $('#userRole').text('审核员');
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
        case 'statistics': loadStatistics(); break;
        case 'notifications': loadNotifications(); break;
        case 'profile': loadProfile(); break;
    }
}

// ==================== 任务列表 ====================
async function loadTasks() {
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    const status = $('#taskStatusFilter').val();

    $('#tasksTableBody').html(`
        <tr><td colspan="7" class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载中...</td></tr>
    `);

    try {
        let url = `${API_BASE_URL}/assessor/tasks?assessorId=${assessorId}`;
        if (status) url += `&status=${status}`;

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            renderTasks(data.data || []);
        } else {
            $('#tasksTableBody').html(`<tr><td colspan="7" class="loading-row"><i class="fas fa-exclamation-circle"></i> ${data.message || '加载失败'}</td></tr>`);
        }
    } catch (error) {
        console.error('加载任务失败:', error);
        $('#tasksTableBody').html('<tr><td colspan="7" class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误，请稍后重试</td></tr>');
    }
}

function renderTasks(tasks) {
    const tbody = $('#tasksTableBody');
    if (!tasks || tasks.length === 0) {
        tbody.html('<tr><td colspan="7" class="loading-row"><i class="fas fa-inbox"></i> 暂无任务</td></tr>');
        return;
    }
    let html = '';
    tasks.forEach(task => {
        html += `
            <tr>
                <td>${task.courseName || '-'}</td>
                <td>${task.teachingClass || '-'}</td>
                <td>${task.teacherName || '-'}</td>
                <td>${formatDateTime(task.deadline)}</td>
                <td><span class="status-badge ${getStatusClass(task.status)}">${getStatusDesc(task.status)}</span></td>
                <td>${task.fileCount || 0}</td>
                <td>
                    <button class="btn primary-btn btn-sm" onclick="viewTaskForReview(${task.id})">
                        <i class="fas fa-edit"></i> 审核
                    </button>
                </td>
            </tr>
        `;
    });
    tbody.html(html);
}

// ==================== 任务审核详情 ====================
async function viewTaskForReview(taskId) {
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    currentTaskId = taskId;
    $('#tasks-view').hide();
    $('#task-detail-view').show();

    try {
        const response = await fetch(`${API_BASE_URL}/assessor/tasks/${taskId}?assessorId=${assessorId}`);
        const data = await response.json();

        if (data.code === 200) {
            const task = data.data;
            currentTaskRequiredItems = parseRequiredItems(task.materialRequirements);
            currentAssignedReviewProjects = task.assignedReviewProjects || [];
            $('#detailCourseName').text(task.courseName || '-');
            $('#detailTeachingClass').text(task.teachingClass || '-');
            $('#detailTeacherName').text(task.teacherName || '-');
            $('#detailDeadline').text(formatDateTime(task.deadline));
            $('#detailStatus').text(getStatusDesc(task.status)).attr('class', 'status-badge ' + getStatusClass(task.status));
            $('#detailDescription').text(task.description || '无');
            $('#detailMaterialReq').text(formatMaterialRequirements(task.materialRequirements));
            renderReviewProjectSelector(currentAssignedReviewProjects);

            // 清空审核表单
            $('#reviewStatus').val('');
            $('#reviewScore').val('');
            $('#reviewComment').val('');
            $('#reviewSuggestions').val('');
            $('#suggestionsGroup').hide();

            // 加载任务信息
            loadTaskFiles(taskId);
            loadReviewHistory(taskId);

            // 如果任务是待上传状态，禁用审核按钮
            $('.review-section .section-warning').remove();
            if (task.status === 'PENDING_UPLOAD') {
                $('.review-section').addClass('disabled').prepend('<div class="section-warning"><i class="fas fa-exclamation-triangle"></i> 该任务处于待上传状态，教师尚未提交材料，无法进行审核操作</div>');
                toggleReviewActions(false);
            } else {
                $('.review-section').removeClass('disabled');
            }
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

// ==================== 文件查看 ====================
async function loadTaskFiles(taskId) {
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    $('#filesGroupsContainer').html('<div class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载文件列表...</div>');

    try {
        const response = await fetch(`${API_BASE_URL}/assessor/tasks/${taskId}/files?assessorId=${assessorId}`);
        const data = await response.json();

        if (data.code === 200) {
            renderFiles(data.data || []);
        } else {
            $('#filesGroupsContainer').html('<div class="loading-row"><i class="fas fa-exclamation-circle"></i> 加载失败</div>');
        }
    } catch (error) {
        console.error('加载文件列表失败:', error);
        $('#filesGroupsContainer').html('<div class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误</div>');
    }
}

function renderFiles(files) {
    const container = $('#filesGroupsContainer');
    const requiredItems = currentTaskRequiredItems.length
        ? currentTaskRequiredItems
        : [{ code: '__default__', name: '未分类材料' }];
    const groupedFiles = groupFilesByItem(files);

    let html = '';
    requiredItems.forEach(item => {
        const itemFiles = groupedFiles.get(item.code) || [];
        html += buildFileGroupHtml(item.name, itemFiles);
    });

    const uncategorizedFiles = groupedFiles.get('__default__') || [];
    if (uncategorizedFiles.length && currentTaskRequiredItems.length) {
        html += buildFileGroupHtml('未分类材料', uncategorizedFiles, '这些文件不在当前备案目录配置中');
    }

    container.html(html || '<div class="loading-row"><i class="fas fa-inbox"></i> 暂无文件</div>');
}

function buildFileGroupHtml(title, files, description = '') {
    return `
        <div class="directory-card ${files.length ? 'has-files' : 'is-empty'}">
            <div class="directory-card-header">
                <div>
                    <div class="directory-title">${escapeHtml(title)}</div>
                    <div class="directory-meta">${description || (files.length ? `共 ${files.length} 个文件` : '当前目录暂无文件')}</div>
                </div>
            </div>
            ${files.length ? `
                <div class="table-container">
                    <table class="data-table">
                        <thead>
                            <tr>
                                <th>文件名</th>
                                <th>文件大小</th>
                                <th>上传时间</th>
                                <th>操作</th>
                            </tr>
                        </thead>
                        <tbody>
                            ${files.map(file => {
                                const fileName = (file.originalName || file.fileName || '').toLowerCase();
                                const canPreview = fileName.endsWith('.pdf') || fileName.endsWith('.jpg') ||
                                    fileName.endsWith('.jpeg') || fileName.endsWith('.png') ||
                                    fileName.endsWith('.gif');
                                return `
                                    <tr>
                                        <td><i class="fas fa-file"></i> ${escapeHtml(file.originalName || file.fileName)}</td>
                                        <td>${file.fileSizeFormatted || formatFileSize(file.fileSize)}</td>
                                        <td>${formatDateTime(file.uploadTime)}</td>
                                        <td>
                                            ${canPreview ? `
                                                <button class="btn-icon" onclick="previewFile(${file.id})" title="预览">
                                                    <i class="fas fa-eye"></i>
                                                </button>
                                            ` : ''}
                                            <button class="btn-icon" onclick="downloadFile(${file.id})" title="下载">
                                                <i class="fas fa-download"></i>
                                            </button>
                                        </td>
                                    </tr>
                                `;
                            }).join('')}
                        </tbody>
                    </table>
                </div>
            ` : '<div class="directory-empty"><i class="fas fa-folder-open"></i> 暂未上传到该目录</div>'}
        </div>
    `;
}

function renderReviewProjectSelector(projects) {
    const container = $('#reviewItemsContainer');
    const hint = $('#reviewItemHint');
    currentAssignedReviewProjects = projects || [];

    if (!currentAssignedReviewProjects.length) {
        container.html('<div class="loading-row"><i class="fas fa-info-circle"></i> 暂无待处理项目</div>');
        hint.text('当前没有分配到可审核的项目，或该项目已经由其他审核员处理完成。');
        toggleReviewActions(false);
        return;
    }

    const activeProjects = currentAssignedReviewProjects.filter(project =>
        project.status === 'PENDING' || project.status === 'REVIEWING'
    );

    container.html(currentAssignedReviewProjects.map(project => {
        const disabled = !(project.status === 'PENDING' || project.status === 'REVIEWING');
        const statusClass = (project.status || '').toLowerCase();
        return `
            <div class="review-item-option ${disabled ? 'is-disabled' : ''}">
                <label>
                    <input type="checkbox" class="review-item-checkbox" value="${project.code}" ${disabled ? 'disabled' : ''}>
                    <span class="review-item-name">${escapeHtml(project.name || project.code)}</span>
                </label>
                <span class="review-item-status ${statusClass}">${escapeHtml(project.statusDesc || project.status)}</span>
            </div>
        `;
    }).join(''));

    hint.text(activeProjects.length
        ? '请选择一个或多个待处理的备案项目，已完成项目不会再次提交审核。'
        : '当前分配项目都已处理完成。');
    toggleReviewActions(activeProjects.length > 0);
}

function toggleReviewActions(enabled) {
    $('#reviewStatus').prop('disabled', !enabled);
    $('#reviewScore').prop('disabled', !enabled);
    $('#reviewComment').prop('disabled', !enabled);
    $('#reviewSuggestions').prop('disabled', !enabled);
    $('#saveReviewBtn').prop('disabled', !enabled);
    $('#submitReviewBtn').prop('disabled', !enabled);
}

function getSelectedReviewProjects() {
    const checkedCodes = $('#reviewItemsContainer .review-item-checkbox:checked').map(function () {
        return $(this).val();
    }).get();
    return currentAssignedReviewProjects.filter(project => checkedCodes.includes(project.code));
}

window.downloadFile = function (fileId) {
    const assessorId = getCurrentAssessorId();
    window.open(`${API_BASE_URL}/assessor/files/${fileId}/download?assessorId=${assessorId}`, '_blank');
};

window.previewFile = function (fileId) {
    const assessorId = getCurrentAssessorId();
    window.open(`${API_BASE_URL}/assessor/files/${fileId}/preview?assessorId=${assessorId}`, '_blank');
};

// ==================== 提交审核 ====================
$('#reviewStatus').on('change', function () {
    const status = $(this).val();
    if (status === 'REJECTED') {
        $('#suggestionsGroup').show();
    } else {
        $('#suggestionsGroup').hide();
    }
});

// 保存审核（进入审核中状态）
$('#saveReviewBtn').on('click', async function () {
    const assessorId = getCurrentAssessorId();
    if (!assessorId || !currentTaskId) return;

    const reviewStatus = $('#reviewStatus').val();
    const score = $('#reviewScore').val();
    const comment = $('#reviewComment').val();
    const selectedProjects = getSelectedReviewProjects();

    if (!selectedProjects.length) {
        showToast('请先选择至少一个待审核项目', 'error');
        return;
    }

    if (!reviewStatus) {
        showToast('保存时请先选择审核结果', 'error');
        return;
    }

    if (reviewStatus === 'REJECTED') {
        showToast('保存功能仅用于暂存审核进度，请使用"提交审核"来完成需修改的审核', 'warning');
        return;
    }

    if (score && (score < 0 || score > 100)) {
        showToast('评分必须在0-100之间', 'error');
        return;
    }

    $('#saveReviewBtn').prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 保存中...');

    try {
        const response = await fetch(`${API_BASE_URL}/assessor/review/save/batch?assessorId=${assessorId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                taskId: currentTaskId,
                itemCodes: selectedProjects.map(project => project.code),
                reviewStatus: reviewStatus,
                score: score ? parseFloat(score) : null,
                comment: comment || null
            })
        });
        const data = await response.json();
        if (data.code !== 200) {
            throw new Error(data.message || '保存失败');
        }
        showToast(`已保存 ${selectedProjects.length} 个项目的审核进度`);
        await viewTaskForReview(currentTaskId);
        loadTaskFiles(currentTaskId);
        loadReviewHistory(currentTaskId);
        loadTasks();
    } catch (error) {
        console.error('保存审核异常:', error);
        showToast('网络错误或服务器异常: ' + error.message, 'error');
    } finally {
        $('#saveReviewBtn').html('<i class="fas fa-save"></i> 保存');
        toggleReviewActions(currentAssignedReviewProjects.some(project => project.status === 'PENDING' || project.status === 'REVIEWING')
            && !$('.review-section').hasClass('disabled'));
    }
});

$('#submitReviewBtn').on('click', async function () {
    const assessorId = getCurrentAssessorId();
    if (!assessorId || !currentTaskId) return;

    const reviewStatus = $('#reviewStatus').val();
    const score = $('#reviewScore').val();
    const comment = $('#reviewComment').val();
    const suggestions = $('#reviewSuggestions').val();
    const selectedProjects = getSelectedReviewProjects();

    if (!selectedProjects.length) {
        showToast('请先选择至少一个待审核项目', 'error');
        return;
    }

    if (!reviewStatus) {
        showToast('请选择审核结果', 'error');
        return;
    }

    if (reviewStatus === 'REJECTED' && (!suggestions || suggestions.trim() === '')) {
        showToast('审核拒绝时必须填写修改建议', 'error');
        return;
    }

    if (score && (score < 0 || score > 100)) {
        showToast('评分必须在0-100之间', 'error');
        return;
    }

    $('#submitReviewBtn').prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 提交中...');

    try {
        const response = await fetch(`${API_BASE_URL}/assessor/review/batch?assessorId=${assessorId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                taskId: currentTaskId,
                itemCodes: selectedProjects.map(project => project.code),
                reviewStatus: reviewStatus,
                score: score ? parseFloat(score) : null,
                comment: comment || null,
                suggestions: suggestions || null
            })
        });
        const data = await response.json();
        if (data.code !== 200) {
            throw new Error(data.message || '提交失败');
        }
        showToast(`已提交 ${selectedProjects.length} 个项目的审核结果`);
        await viewTaskForReview(currentTaskId);
        loadTasks();
    } catch (error) {
        console.error('提交审核异常:', error);
        showToast('网络错误或服务器异常: ' + error.message, 'error');
    } finally {
        $('#submitReviewBtn').html('<i class="fas fa-check"></i> 提交审核');
        toggleReviewActions(currentAssignedReviewProjects.some(project => project.status === 'PENDING' || project.status === 'REVIEWING')
            && !$('.review-section').hasClass('disabled'));
    }
});

$('#cancelReviewBtn').on('click', function () {
    $('#task-detail-view').hide();
    $('#tasks-view').show();
    currentTaskId = null;
});

// ==================== 审核历史记录 ====================
async function loadReviewHistory(taskId) {
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    $('#reviewHistoryContent').html('<div class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载审核记录...</div>');

    try {
        const response = await fetch(`${API_BASE_URL}/assessor/tasks/${taskId}/reviews?assessorId=${assessorId}`);
        const data = await response.json();

        if (data.code === 200) {
            const reviews = data.data || [];
            if (reviews.length === 0) {
                $('#reviewHistoryContent').html('<div class="loading-row"><i class="fas fa-info-circle"></i> 暂无审核记录</div>');
                return;
            }

            let html = '';
            reviews.forEach(review => {
                html += `
                    <div class="review-record">
                        <div class="review-record-header">
                            <span class="review-status ${review.reviewStatus === 'APPROVED' ? 'approved' : 'need-revision'}">
                                ${review.reviewStatusDesc || (review.reviewStatus === 'APPROVED' ? '审核通过' : '需修改')}
                            </span>
                            <span class="review-time">${formatDateTime(review.reviewTime)}</span>
                        </div>
                        <div class="review-record-content">
                            ${review.itemName ? `<div class="review-record-row"><strong>审核项目：</strong>${escapeHtml(review.itemName)}</div>` : ''}
                            ${review.score ? `<div class="review-record-row"><strong>评分：</strong>${review.score}</div>` : ''}
                            ${review.comment ? `<div class="review-record-row"><strong>审核意见：</strong>${review.comment}</div>` : ''}
                            ${review.suggestions ? `<div class="review-record-row"><strong>修改建议：</strong>${review.suggestions}</div>` : ''}
                        </div>
                    </div>
                `;
            });
            $('#reviewHistoryContent').html(html);
        } else {
            $('#reviewHistoryContent').html('<div class="loading-row"><i class="fas fa-exclamation-circle"></i> 加载失败</div>');
        }
    } catch (error) {
        console.error('加载审核记录失败:', error);
        $('#reviewHistoryContent').html('<div class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误</div>');
    }
}

// ==================== 审核记录列表 ====================
async function loadReviews() {
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    $('#reviewsTableBody').html('<tr><td colspan="9" class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载中...</td></tr>');

    try {
        const response = await fetch(`${API_BASE_URL}/assessor/reviews?assessorId=${assessorId}`);
        const data = await response.json();

        if (data.code === 200) {
            renderReviews(data.data || []);
        } else {
            $('#reviewsTableBody').html('<tr><td colspan="9" class="loading-row"><i class="fas fa-exclamation-circle"></i> 加载失败</td></tr>');
        }
    } catch (error) {
        console.error('加载审核记录失败:', error);
        $('#reviewsTableBody').html('<tr><td colspan="9" class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误</td></tr>');
    }
}

function renderReviews(reviews) {
    const tbody = $('#reviewsTableBody');
    if (!reviews || reviews.length === 0) {
        tbody.html('<tr><td colspan="9" class="loading-row"><i class="fas fa-inbox"></i> 暂无审核记录</td></tr>');
        return;
    }

    let html = '';
    reviews.forEach(r => {
        html += `
            <tr>
                <td>${r.courseName || '-'}</td>
                <td>${escapeHtml(r.itemName || r.itemCode || '-')}</td>
                <td>${r.teachingClass || '-'}</td>
                <td>${r.teacherName || '-'}</td>
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

// ==================== 审核统计 ====================
async function loadStatistics() {
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/assessor/statistics?assessorId=${assessorId}`);
        const data = await response.json();

        if (data.code === 200) {
            const stats = data.data;
            $('#statTotal').text(stats.totalTasks || 0);
            $('#statPending').text(stats.pendingTasks || 0);
            $('#statApproved').text(stats.approvedTasks || 0);
            $('#statNeedRevision').text(stats.needRevisionTasks || 0);
            $('#statApprovalRate').text((stats.approvalRate || 0).toFixed(2) + '%');
            $('#statProcessed').text(stats.processedTasks || 0);
        } else {
            showToast('加载统计信息失败', 'error');
        }
    } catch (error) {
        console.error('加载统计信息失败:', error);
        showToast('网络错误', 'error');
    }
}

// ==================== 通知中心 ====================
async function loadNotifications() {
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    $('#notificationsList').html('<div class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载通知列表...</div>');

    try {
        const response = await fetch(`${API_BASE_URL}/notifications?teacherId=${assessorId}`);
        const data = await response.json();

        if (data.code === 200) {
            renderNotifications(data.data || []);
        } else {
            $('#notificationsList').html('<div class="loading-row"><i class="fas fa-exclamation-circle"></i> 加载失败: ' + (data.msg || '未知错误') + '</div>');
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
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/notifications/${notificationId}/read?teacherId=${assessorId}`, {
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
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/notifications/read-all?teacherId=${assessorId}`, {
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
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/notifications/unread-count?teacherId=${assessorId}`);
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
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

    try {
        const response = await fetch(`${API_BASE_URL}/profile?teacherId=${assessorId}`);
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
            showToast('加载个人信息失败: ' + (data.msg || '未知错误'), 'error');
        }
    } catch (error) {
        console.error('加载个人信息失败:', error);
        showToast('网络错误', 'error');
    }
}

$('#saveProfileBtn').on('click', async function () {
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

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
        const response = await fetch(`${API_BASE_URL}/profile?teacherId=${assessorId}`, {
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
    const assessorId = getCurrentAssessorId();
    if (!assessorId) return;

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
        const response = await fetch(`${API_BASE_URL}/profile/password?teacherId=${assessorId}`, {
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

// ==================== 事件绑定 ====================
$('#taskStatusFilter').on('change', loadTasks);
$('#refreshTasksBtn').on('click', loadTasks);
