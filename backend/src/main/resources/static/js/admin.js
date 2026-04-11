const API_BASE_URL = 'http://localhost:8080/api';

let currentView = 'dashboard';
let currentStep = 1;
let selectedCourses = [];
let currentItems = [];
let selectedTeacher = null;
let selectedAssessor = null;
let currentEditingTaskId = null;
let currentEditingMemberId = null;
let courseTeacherMap = new Map();
let courseTeachersMap = new Map();
let currentCoursePage = 1;
let savedScrollPosition = 0;
let currentSearchType = '';
let currentGrade = '';

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

document.addEventListener('DOMContentLoaded', function () {
    initAdminPage();
});

function debounce(func, wait) {
    let timeout;
    return function executedFunction(...args) {
        const later = () => {
            clearTimeout(timeout);
            func(...args);
        };
        clearTimeout(timeout);
        timeout = setTimeout(later, wait);
    };
}

function initAdminPage() {
    checkAuth();
    initNavigation();
    initUserMenu();
    initTaskFilters();
    initAssignSteps();
    initModal();
    initDeleteModal();
    initMemberManagement();
    initAddMemberModal();
    initDeleteMemberModal();
    initEditRoleModal();
    initImportFunctions();
    initNotificationFilters();
    initEditNotificationModal();
    initCourseSearch();


    // 复制培养方案按钮事件
    $('#copyPlanBtn').off('click').on('click', async function () {
        const major = $('#grade-major').val();
        const targetGrade = $('#grade-year').val();
        const sourceGrade = $('#copy-plan-grade').val();

        if (!major || !targetGrade || !sourceGrade) {
            alert('请先选择专业、目标年级和源培养方案');
            return;
        }
        if (targetGrade === sourceGrade) {
            alert('源年级和目标年级不能相同');
            return;
        }
        if (!confirm(`确定要将 ${sourceGrade} 级的培养方案复制到 ${targetGrade} 级吗？`)) {
            return;
        }
        try {
            const res = await fetch(`${API_BASE_URL}/admin/copy-course-plan?major=${encodeURIComponent(major)}&sourceGrade=${sourceGrade}&targetGrade=${targetGrade}`, {
                method: 'POST'
            });
            const data = await res.json();
            if (data.code === 200) {
                alert('复制成功，请重新查询课程');
                // 可选：自动重新查询
                $('#search-by-grade').click();
            } else {
                alert('复制失败：' + data.message);
            }
        } catch (error) {
            console.error('复制培养方案失败', error);
            alert('网络错误，请稍后重试');
        }
    });
}

function checkAuth() {
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');
    const userInfoStr = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo');

    console.log('检查管理员权限:');
    console.log('Token:', token ? '存在' : '不存在');
    console.log('UserInfo:', userInfoStr);

    if (!token || !userInfoStr) {
        console.log('未登录，跳转到登录页');
        window.location.href = 'login.html';
        return;
    }

    const user = JSON.parse(userInfoStr);
    console.log('解析用户信息:', user);
    console.log('用户角色:', user.role);
    console.log('用户角色类型:', typeof user.role);
    console.log('角色比较 user.role === "ADMIN":', user.role === 'ADMIN');
    console.log('角色比较 user.role === "admin":', user.role === 'admin');
    console.log('角色比较 user.role.toUpperCase() === "ADMIN":', user.role.toUpperCase() === 'ADMIN');

    if (!user.role || user.role.toUpperCase() !== 'ADMIN') {
        console.log('非管理员用户，跳转到首页');
        alert('您没有管理员权限');
        window.location.href = 'index.html';
        return;
    }

    document.getElementById('userName').textContent = user.realName || user.username;
    console.log('管理员验证通过');
}

function initNavigation() {
    const menuItems = document.querySelectorAll('.menu-item');
    const views = document.querySelectorAll('.content-view');

    menuItems.forEach(item => {
        item.addEventListener('click', function () {
            const view = this.getAttribute('data-view');
            switchView(view);
        });
    });
}

function switchView(view) {
    currentView = view;

    document.querySelectorAll('.menu-item').forEach(item => {
        item.classList.remove('active');
        if (item.getAttribute('data-view') === view) {
            item.classList.add('active');
        }
    });

    document.querySelectorAll('.content-view').forEach(v => {
        v.style.display = 'none';
    });

    const targetView = document.getElementById(`${view}-view`);
    if (targetView) {
        targetView.style.display = 'block';
    }

    switch (view) {
        case 'dashboard':
            loadDashboard();
            break;
        case 'tasks':
            loadTasks();
            break;
        case 'assign':
            //            resetAssignSteps();
            //            loadCourses();
            break;
        case 'members':
            loadMembers();
            break;
        case 'import':
            loadUploadedFiles();
            break;
        case 'statistics':
            loadStatistics();
            break;
        case 'notifications':
            loadNotifications();
            break;
    }
}

function initUserMenu() {
    const userAvatar = document.getElementById('userAvatar');
    const userDropdown = document.getElementById('userDropdown');
    const logoutBtn = document.getElementById('logoutBtn');

    if (userAvatar && userDropdown) {
        userAvatar.addEventListener('click', function (e) {
            e.stopPropagation();
            userDropdown.classList.toggle('show');
        });

        document.addEventListener('click', function (e) {
            if (!userDropdown.contains(e.target)) {
                userDropdown.classList.remove('show');
            }
        });
    }

    if (logoutBtn) {
        logoutBtn.addEventListener('click', function (e) {
            e.preventDefault();
            logout();
        });
    }
}

function logout() {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('userInfo');
    window.location.href = 'login.html';
}

async function loadDashboard() {
    try {
        const response = await fetch(`${API_BASE_URL}/admin/dashboard`);
        const data = await response.json();

        if (data.code === 200) {
            const dashboard = data.data;
            document.getElementById('totalTasks').textContent = dashboard.totalTasks || 0;
            document.getElementById('approvedTasks').textContent = dashboard.approvedTasks || 0;
            document.getElementById('pendingTasks').textContent = dashboard.pendingTasks || 0;

            updateStatusBars(dashboard);
        }
    } catch (error) {
        console.error('加载仪表盘数据失败:', error);
    }
}

function updateStatusBars(dashboard) {
    const total = dashboard.totalTasks || 1;
    const pending = dashboard.pendingTasks || 0;
    const submitted = dashboard.submittedTasks || 0;
    const reviewing = dashboard.reviewingTasks || 0;
    const needRevision = dashboard.needRevisionTasks || 0;
    const approved = dashboard.approvedTasks || 0;

    document.getElementById('pendingCount').textContent = pending;
    document.getElementById('submittedCount').textContent = submitted;
    document.getElementById('reviewingCount').textContent = reviewing;
    document.getElementById('needRevisionCount').textContent = needRevision;
    document.getElementById('approvedCount').textContent = approved;

    const pendingPercent = (pending / total) * 100;
    const submittedPercent = (submitted / total) * 100;
    const reviewingPercent = (reviewing / total) * 100;
    const needRevisionPercent = (needRevision / total) * 100;
    const approvedPercent = (approved / total) * 100;

    document.querySelector('.bar-fill.pending').style.width = `${pendingPercent}%`;
    document.querySelector('.bar-fill.submitted').style.width = `${submittedPercent}%`;
    document.querySelector('.bar-fill.reviewing').style.width = `${reviewingPercent}%`;
    document.querySelector('.bar-fill.need-revision').style.width = `${needRevisionPercent}%`;
    document.querySelector('.bar-fill.approved').style.width = `${approvedPercent}%`;
}

function initTaskFilters() {
    const filterSelect = document.getElementById('taskStatusFilter');
    const refreshBtn = document.getElementById('refreshTasksBtn');

    if (filterSelect) {
        filterSelect.addEventListener('change', loadTasks);
    }

    if (refreshBtn) {
        refreshBtn.addEventListener('click', loadTasks);
    }

    const exportCsvBtn = document.getElementById('exportTasksCsvBtn');
    if (exportCsvBtn) {
        exportCsvBtn.addEventListener('click', exportTasksToCsv);
    }
}

async function exportTasksToCsv() {
    const statusFilter = document.getElementById('taskStatusFilter').value;
    let url = `${API_BASE_URL}/admin/tasks`;
    if (statusFilter) {
        url += `?status=${statusFilter}`;
    }

    try {
        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200 && data.data) {
            const tasks = data.data;
            if (tasks.length === 0) {
                alert('当前筛选条件下无数据可导出');
                return;
            }

            // CSV 表头
            let csvContent = "\ufeff"; // BOM 解决 Excel 中文乱码
            csvContent += "课程名称,学期,教师,审核员,截止日期,状态,任务说明,材料要求\n";

            // 填充数据
            tasks.forEach(task => {
                const row = [
                    task.courseName || '-',
                    task.semester || '-',
                    task.teacherName || '-',
                    task.assessorName || '-',
                    formatDateTime(task.deadline),
                    task.statusDesc || task.status,
                    (task.description || '-').replace(/,/g, '，').replace(/\n/g, ' '),
                    (task.materialRequirements || '-').replace(/,/g, '，').replace(/\n/g, ' ')
                ];
                csvContent += row.join(",") + "\n";
            });

            // 下载 CSV 文件
            const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
            const link = document.createElement("a");
            const url = URL.createObjectURL(blob);
            const filename = `备案任务导出_${new Date().toLocaleDateString()}.csv`;

            link.setAttribute("href", url);
            link.setAttribute("download", filename);
            link.style.visibility = 'hidden';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
        } else {
            alert('导出数据获取失败: ' + data.message);
        }
    } catch (error) {
        console.error('导出失败:', error);
        alert('导出失败，网络错误');
    }
}

async function remindTask(id) {
    if (!confirm('确定要发送催办通知给该教师吗？')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/tasks/${id}/remind`, {
            method: 'POST'
        });
        const data = await response.json();

        if (data.code === 200) {
            alert('催办通知发送成功');
        } else {
            alert('发送失败: ' + data.message);
        }
    } catch (error) {
        console.error('催办失败:', error);
        alert('网络错误，请稍后重试');
    }
}

async function loadTasks() {
    const tbody = document.getElementById('tasksTableBody');
    const statusFilter = document.getElementById('taskStatusFilter').value;

    tbody.innerHTML = `
        <tr>
            <td colspan="6" class="loading-row">
                <i class="fas fa-spinner fa-spin"></i> 加载中...
            </td>
        </tr>
    `;

    try {
        let url = `${API_BASE_URL}/admin/tasks`;
        if (statusFilter) {
            url += `?status=${statusFilter}`;
        }

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            renderTasks(data.data);
        } else {
            tbody.innerHTML = `
                <tr>
                    <td colspan="6" class="loading-row">
                        <i class="fas fa-exclamation-circle"></i> 加载失败: ${data.message}
                    </td>
                </tr>
            `;
        }
    } catch (error) {
        console.error('加载任务列表失败:', error);
        tbody.innerHTML = `
            <tr>
                <td colspan="6" class="loading-row">
                    <i class="fas fa-exclamation-circle"></i> 网络错误，请稍后重试
                </td>
            </tr>
        `;
    }
}

function renderTasks(tasks) {
    const tbody = document.getElementById('tasksTableBody');

    if (!tasks || tasks.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="loading-row">
                    <i class="fas fa-inbox"></i> 暂无数据
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = tasks.map(task => {
        const showRemind = task.status !== 'APPROVED';
        return `
        <tr>
            <td>${task.courseName || '-'}</td>
            <td>${task.semester || '-'}</td>
            <td>${task.teacherName || '-'}</td>
            <td>${task.assessorName || '-'}</td>
            <td>${formatDateTime(task.deadline)}</td>
            <td><span class="status-badge ${getStatusClass(task.status)}">${task.statusDesc || task.status}</span></td>
            <td>
                <button class="btn secondary-btn" onclick="viewTaskDetail(${task.id})" style="padding: 4px 12px; font-size: 12px;">
                    <i class="fas fa-eye"></i> 详情
                </button>
                ${showRemind ? `
                <button class="btn secondary-btn" onclick="remindTask(${task.id})" style="padding: 4px 12px; font-size: 12px;">
                    <i class="fas fa-bell"></i> 催办
                </button>
                ` : ''}
                <button class="btn secondary-btn" onclick="openDeadlineModal(${task.id}, '${task.deadline}')" style="padding: 4px 12px; font-size: 12px;">
                    <i class="fas fa-clock"></i> 修改截止日期
                </button>
                <button class="btn danger-btn" onclick="deleteTask(${task.id})" style="padding: 4px 12px; font-size: 12px;">
                    <i class="fas fa-trash"></i> 删除
                </button>
            </td>
        </tr>
    `;
    }).join('');
}

function getStatusClass(status) {
    switch (status) {
        case 'PENDING_UPLOAD':
            return 'pending';
        case 'SUBMITTED':
            return 'submitted';
        case 'PENDING_REVIEW':
            return 'reviewing';
        case 'REVIEWING':
            return 'reviewing';
        case 'APPROVED':
            return 'approved';
        case 'NEED_REVISION':
            return 'need-revision';
        default:
            return '';
    }
}

// 获取状态中文描述
function getStatusDesc(status) {
    const map = {
        'PENDING_UPLOAD': '待上传',
        'SUBMITTED': '已提交',
        'PENDING_REVIEW': '审核中',   // 新增
        'REVIEWING': '审核中',
        'APPROVED': '审核通过',
        'NEED_REVISION': '需修改'
    };
    return map[status] || status;
}

function formatDateTime(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleString('zh-CN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    });
}

function initAssignSteps() {
    loadDepartments();
    loadGradeYears();
    loadSemesterOptions();
    initSearchTabs();
    initTaskConfigModal();
}

async function loadGradeOptions() {
    const gradeFilter = document.getElementById('courseGradeFilter');

    try {
        const response = await fetch(`${API_BASE_URL}/admin/grades`);
        const data = await response.json();

        if (data.code === 200) {
            const grades = data.data || [];
            gradeFilter.innerHTML = '<option value="">全部</option>';
            grades.forEach(grade => {
                const option = document.createElement('option');
                option.value = grade;
                option.textContent = grade;
                gradeFilter.appendChild(option);
            });
        }
    } catch (error) {
        console.error('加载年级选项失败:', error);
    }
}

async function loadSemesterOptions() {
    const semesterFilter = document.getElementById('courseSemesterFilter');
    const grade = document.getElementById('courseGradeFilter').value;

    try {
        let url = `${API_BASE_URL}/admin/semesters`;
        if (grade) {
            url += `?grade=${encodeURIComponent(grade)}`;
        }

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            const semesters = data.data || [];
            semesterFilter.innerHTML = '<option value="">全部</option>';
            semesters.forEach(semester => {
                const option = document.createElement('option');
                option.value = semester;
                option.textContent = semester;
                semesterFilter.appendChild(option);
            });
        }
    } catch (error) {
        console.error('加载学期选项失败:', error);
    }
}

async function loadMajorOptions() {
    const majorFilter = document.getElementById('courseMajorFilter');

    try {
        const response = await fetch(`${API_BASE_URL}/admin/majors`);
        const data = await response.json();

        if (data.code === 200) {
            const majors = data.data || [];
            majorFilter.innerHTML = '<option value="">全部</option>';
            majors.forEach(major => {
                const option = document.createElement('option');
                option.value = major;
                option.textContent = major;
                majorFilter.appendChild(option);
            });
        }
    } catch (error) {
        console.error('加载专业选项失败:', error);
    }
}

function initCourseFilters() {
    document.getElementById('courseSemesterFilter').addEventListener('change', () => loadCourses(1));
    document.getElementById('courseStatusFilter').addEventListener('change', () => loadCourses(1));
    document.getElementById('courseMajorFilter').addEventListener('change', () => loadCourses(1));

    const searchInput = document.getElementById('courseSearchInput');
    let searchTimeout;

    searchInput.addEventListener('input', () => {
        clearTimeout(searchTimeout);
        searchTimeout = setTimeout(() => {
            loadCourses(1);
        }, 500);
    });
}

function resetAssignSteps() {
    currentStep = 1;
    selectedCourses = [];
    selectedTeacher = null;
    selectedAssessor = null;
    courseTeacherMap = new Map();
    courseTeachersMap = new Map();
    document.getElementById('taskDeadline').value = '';

    document.querySelectorAll('.progress-step').forEach(step => {
        step.classList.remove('active', 'completed');
    });
    document.querySelector('.progress-step[data-step="1"]').classList.add('active');

    document.querySelectorAll('.step-panel').forEach(panel => {
        panel.classList.remove('active');
    });
    document.querySelector('.step-panel[data-step="1"]').classList.add('active');

    updateProgressBar(1);
    updateConfiguredCount();
    document.getElementById('nextStep1').disabled = true;
}

function updateProgressBar(step) {
    const progressFill = document.getElementById('progressFill');
    const percentage = (step / 4) * 100;
    progressFill.style.width = `${percentage}%`;

    document.querySelectorAll('.progress-step').forEach((s, index) => {
        if (index + 1 < step) {
            s.classList.add('completed');
            s.classList.remove('active');
        } else if (index + 1 === step) {
            s.classList.add('active');
            s.classList.remove('completed');
        } else {
            s.classList.remove('active', 'completed');
        }
    });
}

function goToStep(step) {
    currentStep = step;
    updateProgressBar(step);

    document.querySelectorAll('.step-panel').forEach(panel => {
        panel.classList.remove('active');
    });
    document.querySelector(`.step-panel[data-step="${step}"]`).classList.add('active');

    switch (step) {
        case 2:
            loadAssessors();
            break;
        case 4:
            generateTaskSummary();
            break;
    }
}

async function loadNotifications() {
    try {
        const typeFilter = document.getElementById('notificationTypeFilter');
        const statusFilter = document.getElementById('notificationStatusFilter');
        const userFilter = document.getElementById('notificationUserFilter');

        const type = typeFilter ? typeFilter.value : '';
        const status = statusFilter ? statusFilter.value : '';
        const userRole = userFilter ? userFilter.value : '';

        const response = await fetch(`${API_BASE_URL}/notifications/all`);
        const data = await response.json();

        if (data.code === 200) {
            let notifications = data.data || [];

            // 前端筛选
            if (type) {
                notifications = notifications.filter(n => n.type === type);
            }

            if (status !== '') {
                notifications = notifications.filter(n => n.isRead === parseInt(status));
            }

            if (userRole) {
                // 根据角色筛选通知
                notifications = notifications.filter(n => n.userRole === userRole);
            }

            renderNotifications(notifications);
        } else {
            alert('加载通知失败: ' + data.message);
        }
    } catch (error) {
        console.error('加载通知失败:', error);
        alert('网络错误，请稍后重试');
    }
}

function renderNotifications(notifications) {
    const tbody = document.getElementById('notificationsTableBody');

    if (!notifications || notifications.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="7" class="empty-row">
                    <i class="fas fa-inbox"></i>
                    <span>暂无通知</span>
                </td>
            </tr>
        `;
        return;
    }

    tbody.innerHTML = notifications.map(notification => `
        <tr class="notification-item" data-id="${notification.id}">
            <td>${notification.userName || '-'}</td>
            <td>
                <span class="badge ${getNotificationTypeClass(notification.type)}">
                    ${notification.typeDesc || notification.type}
                </span>
            </td>
            <td>${notification.title}</td>
            <td class="notification-content">${notification.content}</td>
            <td>
                <span class="badge ${notification.isRead === 0 ? 'unread' : 'read'}">
                    ${notification.isRead === 0 ? '未读' : '已读'}
                </span>
            </td>
            <td>${formatDateTime(notification.createTime)}</td>
            <td>
                <button class="btn-icon edit-notification-btn" data-id="${notification.id}" title="编辑">
                    <i class="fas fa-edit"></i>
                </button>
                <button class="btn-icon delete-notification-btn" data-id="${notification.id}" title="删除">
                    <i class="fas fa-trash"></i>
                </button>
            </td>
        </tr>
    `).join('');
}

function getNotificationTypeClass(type) {
    switch (type) {
        case 'NEW_TASK':
            return 'new-task';
        case 'REVIEW_RESULT':
            return 'review-result';
        case 'TASK_REMINDER':
            return 'task-reminder';
        default:
            return 'default';
    }
}

function openEditNotificationModal(notificationId) {
    const notifications = document.querySelectorAll('.notification-item');
    let notification = null;
    notifications.forEach(item => {
        if (parseInt(item.dataset.id) === notificationId) {
            const cells = item.querySelectorAll('td');
            if (cells.length >= 4) {
                notification = {
                    id: notificationId,
                    title: cells[2].textContent.trim(),
                    content: cells[3].textContent.trim()
                };
            }
        }
    });

    if (!notification) {
        console.error('未找到通知信息，notificationId:', notificationId);
        console.log('当前通知列表元素数量:', notifications.length);
        alert('未找到通知信息');
        return;
    }

    document.getElementById('editNotificationId').value = notification.id;
    document.getElementById('editNotificationTitle').value = notification.title;
    document.getElementById('editNotificationContent').value = notification.content;

    const modal = document.getElementById('editNotificationModal');
    modal.classList.add('show');
}

function closeEditNotificationModal() {
    const modal = document.getElementById('editNotificationModal');
    modal.classList.remove('show');
}

async function saveEditNotification() {
    const notificationId = document.getElementById('editNotificationId').value;
    const title = document.getElementById('editNotificationTitle').value.trim();
    const content = document.getElementById('editNotificationContent').value.trim();

    if (!title || !content) {
        alert('请填写标题和内容');
        return;
    }

    try {
        const userInfo = JSON.parse(localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo'));
        const response = await fetch(`${API_BASE_URL}/notifications/${notificationId}?userId=${userInfo.id}&title=${encodeURIComponent(title)}&content=${encodeURIComponent(content)}`, {
            method: 'PUT'
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('编辑成功！');
            closeEditNotificationModal();
            loadNotifications();
        } else {
            alert('编辑失败: ' + data.message);
        }
    } catch (error) {
        console.error('编辑通知失败:', error);
        alert('网络错误，请稍后重试');
    }
}

async function deleteNotification(notificationId) {
    try {
        const userInfo = JSON.parse(localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo'));
        const response = await fetch(`${API_BASE_URL}/notifications/${notificationId}?userId=${userInfo.id}`, {
            method: 'DELETE'
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('删除成功！');
            loadNotifications();
        } else {
            alert('删除失败: ' + data.message);
        }
    } catch (error) {
        console.error('删除通知失败:', error);
        alert('网络错误，请稍后重试');
    }
}

function initNotificationFilters() {
    const refreshBtn = document.getElementById('refreshNotificationsBtn');
    if (refreshBtn) {
        refreshBtn.addEventListener('click', loadNotifications);
    }

    const typeFilter = document.getElementById('notificationTypeFilter');
    const statusFilter = document.getElementById('notificationStatusFilter');
    const userFilter = document.getElementById('notificationUserFilter');

    if (typeFilter) {
        typeFilter.addEventListener('change', loadNotifications);
    }

    if (statusFilter) {
        statusFilter.addEventListener('change', loadNotifications);
    }

    if (userFilter) {
        userFilter.addEventListener('change', loadNotifications);

        // 为接收人筛选框添加特殊处理
        userFilter.addEventListener('focus', function () {
            this.style.zIndex = '99999';
            this.parentElement.style.zIndex = '99998';
        });

        userFilter.addEventListener('blur', function () {
            setTimeout(() => {
                this.style.zIndex = '';
                this.parentElement.style.zIndex = '';
            }, 200);
        });

        userFilter.addEventListener('mousedown', function () {
            this.style.zIndex = '99999';
            this.parentElement.style.zIndex = '99998';
        });
    }

    // 使用事件委托处理通知列表的编辑和删除按钮
    const notificationsTableBody = document.getElementById('notificationsTableBody');
    if (notificationsTableBody) {
        notificationsTableBody.removeEventListener('click', handleNotificationTableClick);
        notificationsTableBody.addEventListener('click', handleNotificationTableClick);
    }
}

function handleNotificationTableClick(event) {
    const editBtn = event.target.closest('.edit-notification-btn');
    const deleteBtn = event.target.closest('.delete-notification-btn');

    if (editBtn) {
        const notificationId = parseInt(editBtn.getAttribute('data-id'));
        openEditNotificationModal(notificationId);
        event.stopPropagation();
    } else if (deleteBtn) {
        const notificationId = parseInt(deleteBtn.getAttribute('data-id'));
        if (confirm('确定要删除这条通知吗？')) {
            deleteNotification(notificationId);
        }
        event.stopPropagation();
    }
}

async function loadCourses(page = 1) {
    currentCoursePage = page;
    const courseList = document.getElementById('courseList');
    const grade = document.getElementById('courseGradeFilter').value;
    const major = document.getElementById('courseMajorFilter').value;
    const semester = document.getElementById('courseSemesterFilter').value;
    const status = document.getElementById('courseStatusFilter').value;
    const search = document.getElementById('courseSearchInput').value.trim();

    const prevBtn = document.getElementById('prevPageBtn');
    const nextBtn = document.getElementById('nextPageBtn');

    if (prevBtn) {
        prevBtn.disabled = true;
        prevBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 加载中';
    }
    if (nextBtn) {
        nextBtn.disabled = true;
        nextBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 加载中';
    }

    window.scrollTo({ top: 0, behavior: 'smooth' });

    try {
        let url = `${API_BASE_URL}/admin/courses`;
        const params = new URLSearchParams();
        if (grade) params.append('grade', grade);
        if (major) params.append('major', major);
        if (semester) params.append('semester', semester);
        if (status && status !== "") params.append('status', status);
        if (search) params.append('search', search);
        params.append('page', page);
        params.append('pageSize', 10);
        url += '?' + params.toString();

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            console.log('课程API返回数据:', data.data);
            renderCourses(data.data.data, data.data.currentPage, data.data.totalPages);
        } else {
            courseList.innerHTML = `<div class="loading"><i class="fas fa-exclamation-circle"></i> 加载失败: ${data.message}</div>`;
        }
    } catch (error) {
        console.error('加载课程列表失败:', error);
        courseList.innerHTML = '<div class="loading"><i class="fas fa-exclamation-circle"></i> 网络错误，请稍后重试</div>';
    }
}

function renderCourses(courses, currentPage = 1, totalPages = 1) {
    const courseList = document.getElementById('courseList');

    if (!courses || courses.length === 0) {
        courseList.innerHTML = '<div class="loading"><i class="fas fa-inbox"></i> 暂无课程</div>';
        return;
    }

    console.log('课程数据:', courses);

    const coursesHtml = courses.map((course, index) => {
        console.log('单个课程数据:', course);
        const assignedTeachers = courseTeachersMap.get(course.id) || [];
        const isSelected = selectedCourses.includes(course.id);
        const serialNumber = (currentPage - 1) * 10 + index + 1;

        const teachersInfo = assignedTeachers.map(t => {
            const teacherName = t.teacherName || t.realName;
            const teachingClass = t.teachingClass ? `(${t.teachingClass})` : '';
            return `${teacherName}${teachingClass}`;
        }).join(', ');

        const courseTeachingClass = course.teachingClass || course.classNumber || course.className || course.classNo || course.班号 || course.班级 || '';
        const courseMajor = course.major || '';

        return `
        <div class="course-row ${isSelected ? 'selected' : ''}" data-id="${course.id}" data-name="${course.courseName}" data-semester="${course.semester}" data-grade="${course.grade}" data-major="${courseMajor}" data-teaching-class="${courseTeachingClass}" onclick="openTeacherSelector(${course.id}, '${course.courseName.replace(/'/g, "\\'")}', '${(course.semester || '').replace(/'/g, "\\'")}', '${(course.grade || '').replace(/'/g, "\\'")}', '${courseMajor.replace(/'/g, "\\'")}')">
            <div class="course-number">${serialNumber}</div>
            <div class="course-main">
                <div class="course-details">
                    <div class="course-name" title="${course.courseName}">${course.courseName}</div>
                    <div class="course-info">
                        <span class="course-grade">${course.grade || '-'}</span>
                        ${courseMajor ? `<span class="course-major">${courseMajor}</span>` : ''}
                        <span class="course-semester">${course.semester || '-'}</span>
                        ${courseTeachingClass ? `<span class="course-teaching-class">${courseTeachingClass}</span>` : ''}
                    </div>
                </div>
            </div>
        </div>
        `;
    }).join('');

    const paginationHtml = totalPages > 1 ? `
        <div class="pagination">
            <button class="pagination-btn" id="prevPageBtn" onclick="event.preventDefault(); event.stopPropagation(); loadCourses(${currentPage - 1})" ${currentPage <= 1 ? 'disabled' : ''}>
                <i class="fas fa-chevron-left"></i> 上一页
            </button>
            <span class="pagination-info">第 ${currentPage} / ${totalPages} 页</span>
            <button class="pagination-btn" id="nextPageBtn" onclick="event.preventDefault(); event.stopPropagation(); loadCourses(${currentPage + 1})" ${currentPage >= totalPages ? 'disabled' : ''}>
                下一页 <i class="fas fa-chevron-right"></i>
            </button>
        </div>
    ` : '';

    courseList.innerHTML = coursesHtml + paginationHtml;

    setTimeout(() => {
        const prevBtn = document.getElementById('prevPageBtn');
        const nextBtn = document.getElementById('nextPageBtn');
        if (prevBtn) {
            prevBtn.disabled = currentPage <= 1;
            prevBtn.innerHTML = '<i class="fas fa-chevron-left"></i> 上一页';
        }
        if (nextBtn) {
            nextBtn.disabled = currentPage >= totalPages;
            nextBtn.innerHTML = '下一页 <i class="fas fa-chevron-right"></i>';
        }
    }, 50);
}

function updateConfiguredCount() {
    const configuredCount = Array.from(courseTeachersMap.keys()).filter(courseId => {
        const teachers = courseTeachersMap.get(courseId);
        return teachers && teachers.length > 0;
    }).length;

    document.getElementById('configuredCourseCount').textContent = configuredCount;
    document.getElementById('nextStep1').disabled = configuredCount === 0;
}

function toggleCourseSelection(courseId) {
    const item = document.querySelector(`.course-row[data-id="${courseId}"]`);

    if (selectedCourses.includes(courseId)) {
        selectedCourses = selectedCourses.filter(id => id !== courseId);
        item.classList.remove('selected');
    } else {
        selectedCourses.push(courseId);
        item.classList.add('selected');
    }

    updateConfiguredCount();
}

let currentEditingCourseId = null;
let currentModalTeachers = [];
let currentCourseName = '';
let currentCourseGrade = '';
let currentCourseSemester = '';
let currentCourseMajor = '';
let currentTeacherPage = 1;
let teacherPageSize = 10;

async function openTeacherSelector(courseId, courseName, semester, grade, major) {
    currentEditingCourseId = courseId;
    currentCourseName = courseName;
    currentCourseGrade = grade || '';
    currentCourseSemester = semester || '';
    currentCourseMajor = major || '';
    currentTeacherPage = 1;

    savedScrollPosition = window.scrollY;

    $('#assign-view').hide();
    $('#teacher-list-view').show();

    document.getElementById('teacherListCourseName').textContent = courseName;
    document.getElementById('teacherListGrade').textContent = grade || '-';
    document.getElementById('teacherListSemester').textContent = semester || '-';

    window.scrollTo({ top: 0, behavior: 'smooth' });

    loadTeachersForPage(courseName, semester, grade, major);
}

async function loadTeachersForPage(courseName, semester, grade, major) {
    const teacherListContainer = document.getElementById('teacherListContainer');

    console.log('开始加载教师列表:', { courseName, semester, grade, major });

    teacherListContainer.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> 加载教师列表...</div>';

    try {
        const semesterMatch = semester.match(/第\s*(\d+)\s*学期/);
        let standardSemester = semester;

        if (semesterMatch && grade) {
            const semesterNum = parseInt(semesterMatch[1]);
            const baseYear = parseInt(grade);
            const yearOffset = Math.floor((semesterNum - 1) / 2);
            const academicYear = baseYear + yearOffset;
            const semesterFlag = (semesterNum % 2 === 1) ? '1' : '2';
            standardSemester = `${academicYear}-${academicYear + 1}-${semesterFlag}`;
        }

        let url = `${API_BASE_URL}/admin/fetch-teachers?courseName=${encodeURIComponent(courseName)}&semester=${encodeURIComponent(standardSemester)}`;
        if (grade) {
            url += `&grade=${encodeURIComponent(grade)}`;
        }
        if (major) {
            url += `&major=${encodeURIComponent(major)}`;
        }

        console.log('请求URL:', url);

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            let teachers = data.data;
            console.log('从后端获取的教师数据:', teachers);
            console.log('教师数量:', teachers.length);
            renderTeachersForPage(teachers);
        } else {
            teacherListContainer.innerHTML = `<div class="loading"><i class="fas fa-exclamation-circle"></i> 加载失败: ${data.message}</div>`;
        }
    } catch (error) {
        console.error('加载教师列表失败:', error);
        teacherListContainer.innerHTML = '<div class="loading"><i class="fas fa-exclamation-circle"></i> 网络错误，请稍后重试</div>';
    }
}

function renderTeachersForPage(teachers) {
    const teacherListContainer = document.getElementById('teacherListContainer');
    const currentTeachers = courseTeachersMap.get(currentEditingCourseId) || [];

    tempSelectedTeachers = currentTeachers.map(t => t.id);
    currentModalTeachers = teachers;

    if (!teachers || teachers.length === 0) {
        teacherListContainer.innerHTML = '<div class="loading"><i class="fas fa-inbox"></i> 暂无教师</div>';
        document.getElementById('teacherListPagination').innerHTML = '';
        return;
    }

    const totalPages = Math.ceil(teachers.length / teacherPageSize);
    const startIndex = (currentTeacherPage - 1) * teacherPageSize;
    const endIndex = startIndex + teacherPageSize;
    const pageTeachers = teachers.slice(startIndex, endIndex);

    teacherListContainer.innerHTML = pageTeachers.map(teacher => {
        const teacherName = teacher.teacherName || teacher.realName;
        const isSelected = currentTeachers.some(t =>
            t.id === teacher.id ||
            (t.realName === teacherName && t.teachingClass === teacher.teachingClass)
        );
        const escapedId = teacher.id;
        const preferred = teacher.preferred || '';
        return `
        <div class="teacher-card ${isSelected ? 'selected' : ''}" data-id="${escapedId}">
            <div class="teacher-card-header">
                <div class="teacher-name">${teacherName}</div>
                <div class="teacher-course-code">${teacher.courseCode || teacher.workId || '待生成'}</div>
            </div>
            <div class="teacher-card-body">
                <div class="teacher-info-row">
                    <span class="info-label">课程：</span>
                    <span class="info-value">${currentCourseName || '-'}</span>
                </div>
                <div class="teacher-info-row">
                    <span class="info-label">院系：</span>
                    <span class="info-value">${teacher.department || '-'}</span>
                </div>
                <div class="teacher-info-row">
                    <span class="info-label">教学班：</span>
                    <span class="info-value">${teacher.teachingClass || '-'}</span>
                </div>
                ${preferred ? `
                <div class="teacher-info-row">
                    <span class="info-label">优选：</span>
                    <span class="info-value preferred-text">${preferred}</span>
                </div>
                ` : ''}
            </div>
        </div>
        `;
    }).join('');

    teacherListContainer.querySelectorAll('.teacher-card').forEach(card => {
        card.addEventListener('click', function (e) {
            console.log('教师卡片被点击:', this);
            e.stopPropagation();
            const teacherId = parseInt(this.getAttribute('data-id'));
            console.log('教师ID:', teacherId);
            toggleTeacherSelection(teacherId);
        });

        card.addEventListener('mouseenter', function () {
            console.log('鼠标悬停:', this);
        });
    });

    renderTeacherPagination(totalPages);
}

function renderTeacherPagination(totalPages) {
    const paginationContainer = document.getElementById('teacherListPagination');

    if (totalPages <= 1) {
        paginationContainer.innerHTML = '';
        return;
    }

    paginationContainer.innerHTML = `
        <div class="pagination">
            <button class="pagination-btn" id="prevTeacherPageBtn" onclick="changeTeacherPage(${currentTeacherPage - 1})" ${currentTeacherPage <= 1 ? 'disabled' : ''}>
                <i class="fas fa-chevron-left"></i> 上一页
            </button>
            <span class="pagination-info">第 ${currentTeacherPage} / ${totalPages} 页</span>
            <button class="pagination-btn" id="nextTeacherPageBtn" onclick="changeTeacherPage(${currentTeacherPage + 1})" ${currentTeacherPage >= totalPages ? 'disabled' : ''}>
                下一页 <i class="fas fa-chevron-right"></i>
            </button>
        </div>
    `;
}

function changeTeacherPage(page) {
    if (page < 1 || page > Math.ceil(currentModalTeachers.length / teacherPageSize)) {
        return;
    }

    currentTeacherPage = page;
    renderTeachersForPage(currentModalTeachers);

    const teacherListContainer = document.getElementById('teacherListContainer');
    if (teacherListContainer) {
        teacherListContainer.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
}

let tempSelectedTeachers = [];

function toggleTeacherSelection(teacherId) {
    const teacherListContainer = document.getElementById('teacherListContainer');
    const item = teacherListContainer.querySelector(`.teacher-card[data-id="${teacherId}"]`);

    if (tempSelectedTeachers.includes(teacherId)) {
        tempSelectedTeachers = tempSelectedTeachers.filter(id => id !== teacherId);
        item.classList.remove('selected');
    } else {
        tempSelectedTeachers.push(teacherId);
        item.classList.add('selected');
    }
}

async function saveSelectedTeachers() {
    try {
        const selectedTeacherObjects = tempSelectedTeachers.map(id => currentModalTeachers.find(t => t.id === id)).filter(t => t);

        console.log('tempSelectedTeachers:', tempSelectedTeachers);
        console.log('currentModalTeachers:', currentModalTeachers);
        console.log('selectedTeacherObjects:', selectedTeacherObjects);

        if (selectedTeacherObjects.length > 0) {
            console.log('准备保存教师到user表:', selectedTeacherObjects);

            const teacherIds = selectedTeacherObjects.map(t => t.id);
            const response = await fetch(`${API_BASE_URL}/admin/teachers/save-to-user`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify(teacherIds)
            });

            const data = await response.json();
            if (data.code !== 200) {
                alert('保存教师失败: ' + data.message);
                return;
            }

            console.log('教师保存成功:', data.data);

            const savedTeachers = data.data.map(teacher => {
                const originalTeacher = selectedTeacherObjects.find(t => t.id === teacher.id);
                const modalTeacher = currentModalTeachers.find(t => t.id === teacher.id);
                console.log('原始教师数据:', originalTeacher);
                console.log('模态框教师数据:', modalTeacher);
                console.log('模态框教师数据是否有teachingClass:', modalTeacher?.teachingClass);
                console.log('后端返回教师数据:', teacher);

                const result = {
                    ...teacher,
                    ...originalTeacher,
                    courseName: currentCourseName,
                    teachingClass: modalTeacher?.teachingClass || teacher.teachingClass || '-'
                };

                console.log('保存的教师数据:', result);
                console.log('保存的教师数据是否有teachingClass:', result.teachingClass);
                return result;
            });

            courseTeachersMap.set(currentEditingCourseId, savedTeachers);
            console.log('courseTeachersMap更新后:', courseTeachersMap);
        } else {
            courseTeachersMap.set(currentEditingCourseId, []);
        }

        if (selectedTeacherObjects.length > 0) {
            if (!selectedCourses.includes(currentEditingCourseId)) {
                selectedCourses.push(currentEditingCourseId);
            }
            const courseRow = document.querySelector(`.course-row[data-id="${currentEditingCourseId}"]`);
            if (courseRow) {
                courseRow.classList.add('selected');
            }
        } else {
            selectedCourses = selectedCourses.filter(id => id !== currentEditingCourseId);
            const courseRow = document.querySelector(`.course-row[data-id="${currentEditingCourseId}"]`);
            if (courseRow) {
                courseRow.classList.remove('selected');
            }
        }

        tempSelectedTeachers = [];
        currentModalTeachers = [];

        $('#teacher-list-view').hide();
        $('#assign-view').show();

        await loadCourses(currentCoursePage);
        updateConfiguredCount();

        window.scrollTo({ top: savedScrollPosition, behavior: 'smooth' });
    } catch (error) {
        console.error('保存教师选择失败:', error);
        alert('保存失败，请稍后重试');
    }
}

function openManualAddModal() {
    const modal = document.getElementById('manualAddTaskModal');
    modal.classList.add('show');

    document.getElementById('manualCourseName').value = '';
    document.getElementById('manualTeachingClass').value = '';
    document.getElementById('manualTeacherName').value = '';
    document.getElementById('manualWorkId').value = '';

    document.getElementById('workIdGroup').style.display = 'none';
    document.getElementById('teacherCheckResult').innerHTML = '';
    document.getElementById('workIdHint').innerHTML = '';
}

function closeManualAddModal() {
    const modal = document.getElementById('manualAddTaskModal');
    modal.classList.remove('show');
}

async function checkTeacherByName() {
    const teacherName = document.getElementById('manualTeacherName').value.trim();
    const resultDiv = document.getElementById('teacherCheckResult');
    const workIdGroup = document.getElementById('workIdGroup');
    const workIdHint = document.getElementById('workIdHint');

    if (!teacherName) {
        resultDiv.innerHTML = '<span style="color: #f44336;">请输入教师姓名</span>';
        workIdGroup.style.display = 'none';
        return;
    }

    resultDiv.innerHTML = '<span style="color: #2196F3;">正在查询...</span>';

    try {
        const response = await fetch(`${API_BASE_URL}/admin/check-teacher-by-name?teacherName=${encodeURIComponent(teacherName)}`);
        const data = await response.json();

        if (data.code === 200) {
            const result = data.data;
            if (result.exists) {
                resultDiv.innerHTML = `<span style="color: #4CAF50;">✓ ${result.message}</span>`;
                workIdGroup.style.display = 'block';
                document.getElementById('manualWorkId').value = result.workId;
                document.getElementById('manualWorkId').readOnly = true;
                workIdHint.innerHTML = '该教师已存在，工号已自动填充';
            } else {
                resultDiv.innerHTML = `<span style="color: #FF9800;">⚠ ${result.message}</span>`;
                workIdGroup.style.display = 'none';
                document.getElementById('manualWorkId').value = '';
            }
        } else {
            resultDiv.innerHTML = `<span style="color: #f44336;">查询失败：${data.message}</span>`;
        }
    } catch (error) {
        console.error('查询教师失败:', error);
        resultDiv.innerHTML = '<span style="color: #f44336;">查询失败，请稍后重试</span>';
    }
}

async function saveManualTask() {
    const courseName = document.getElementById('manualCourseName').value.trim();
    const teachingClass = document.getElementById('manualTeachingClass').value.trim();
    const teacherName = document.getElementById('manualTeacherName').value.trim();
    const workIdInput = document.getElementById('manualWorkId');
    const workId = workIdInput.readOnly ? workIdInput.value.trim() : workIdInput.value.trim();

    console.log('表单数据:', { courseName, teachingClass, teacherName, workId });

    if (!courseName) {
        alert('请输入课程名称');
        return;
    }

    if (!teachingClass) {
        alert('请输入教学班号');
        return;
    }

    if (!teacherName) {
        alert('请输入教师姓名');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/manual-add-task`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                courseName,
                teachingClass,
                teacherName,
                workId
            })
        });

        const data = await response.json();
        if (data.code === 200) {
            const teacher = data.data;

            console.log('后端返回的教师数据:', teacher);

            const manualTask = {
                id: teacher.id,
                courseName: courseName,
                teachingClass: teachingClass,
                teacherName: teacherName,
                realName: teacherName,
                workId: workId,
                isManual: true,
                teacherId: teacher.teacherId
            };

            console.log('准备保存的手动任务:', manualTask);

            const courseId = 'manual_' + Date.now();
            courseTeachersMap.set(courseId, [manualTask]);

            if (!selectedCourses.includes(courseId)) {
                selectedCourses.push(courseId);
            }

            updateConfiguredCount();
            closeManualAddModal();
            loadTempTasks();

            if (teacher.isNewUser) {
                alert('新建教师用户成功！\n\n教师：' + teacher.realName + '\n工号：' + teacher.workId + '\n默认密码：123456\n\n请提醒教师及时修改密码。');
            } else {
                alert('手动添加任务成功！\n\n已关联现有教师：' + teacher.realName + '（工号：' + teacher.workId + '）');
            }
        } else {
            alert('添加失败: ' + data.message);
        }
    } catch (error) {
        console.error('手动添加任务失败:', error);
        alert('网络错误，请稍后重试');
    }
}

async function getCourses() {
    const grade = document.getElementById('courseGradeFilter').value;
    const semester = document.getElementById('courseSemesterFilter').value;
    const status = document.getElementById('courseStatusFilter').value;

    try {
        let url = `${API_BASE_URL}/admin/courses`;
        const params = new URLSearchParams();
        if (grade) params.append('grade', grade);
        if (semester) params.append('semester', semester);
        if (status) params.append('status', status);
        if (params.toString()) url += '?' + params.toString();

        const response = await fetch(url);
        const data = await response.json();
        return data.code === 200 ? data.data : [];
    } catch (error) {
        console.error('获取课程列表失败:', error);
        return [];
    }
}

async function loadTeachers() {
    const teacherList = document.getElementById('teacherList');
    const code = document.getElementById('teacherCodeFilter').value;
    const name = document.getElementById('teacherNameFilter').value;

    teacherList.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> 加载教师列表...</div>';

    try {
        let url = `${API_BASE_URL}/admin/teachers`;
        const params = new URLSearchParams();
        if (code) params.append('code', code);
        if (name) params.append('name', name);
        if (params.toString()) url += '?' + params.toString();

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            renderTeachers(data.data);
        } else {
            teacherList.innerHTML = `<div class="loading"><i class="fas fa-exclamation-circle"></i> 加载失败: ${data.message}</div>`;
        }
    } catch (error) {
        console.error('加载教师列表失败:', error);
        teacherList.innerHTML = '<div class="loading"><i class="fas fa-exclamation-circle"></i> 网络错误，请稍后重试</div>';
    }
}

function renderTeachers(teachers) {
    const teacherList = document.getElementById('teacherList');

    if (!teachers || teachers.length === 0) {
        teacherList.innerHTML = '<div class="loading"><i class="fas fa-inbox"></i> 暂无教师</div>';
        return;
    }

    const currentCourseId = getCurrentCourseId();

    teacherList.innerHTML = teachers.map(teacher => {
        const assignedCourseIds = Array.from(courseTeacherMap.entries())
            .filter(([courseId, teacherId]) => teacherId === teacher.id)
            .map(([courseId]) => courseId);

        const assignedCoursesText = assignedCourseIds.length > 0
            ? `<div class="assigned-courses">已分配: ${assignedCourseIds.length} 门课程</div>`
            : '';

        return `
        <div class="list-item" data-id="${teacher.id}" onclick="selectTeacher(${teacher.id})">
            <div class="item-main">
                <div class="item-name">${teacher.realName}</div>
                <div class="item-code">${teacher.workId}</div>
                ${assignedCoursesText}
            </div>
            <div class="item-info">${teacher.department || '-'}</div>
        </div>
        `;
    }).join('');
}

function selectTeacher(teacherId) {
    const currentCourseId = getCurrentCourseId();
    if (currentCourseId) {
        courseTeacherMap.set(currentCourseId, teacherId);
    }

    document.querySelectorAll('#teacherList .list-item').forEach(item => {
        item.classList.remove('selected');
    });

    const selectedItem = document.querySelector(`#teacherList .list-item[data-id="${teacherId}"]`);
    if (selectedItem) {
        selectedItem.classList.add('selected');
    }

    updateNextStep2Button();
}

function getCurrentCourseId() {
    const courseItems = document.querySelectorAll('#courseList .list-item.selected');
    if (courseItems.length === 0) return null;
    return parseInt(courseItems[courseItems.length - 1].dataset.id);
}

function updateNextStep2Button() {
    const currentCourseId = getCurrentCourseId();
    const hasTeacherForCurrentCourse = currentCourseId && courseTeacherMap.has(currentCourseId);
    document.getElementById('nextStep2').disabled = !hasTeacherForCurrentCourse;
}

async function loadAssessors() {
    const assessorList = document.getElementById('assessorList');
    assessorList.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> 加载审核员列表...</div>';

    try {
        let url = `${API_BASE_URL}/admin/assessors`;
        const currentCourseId = getCurrentCourseId();
        const teacherId = currentCourseId ? courseTeacherMap.get(currentCourseId) : null;
        if (teacherId) {
            url += `?excludeTeacherId=${teacherId}`;
        }

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            renderAssessors(data.data);
        } else {
            assessorList.innerHTML = `<div class="loading"><i class="fas fa-exclamation-circle"></i> 加载失败: ${data.message}</div>`;
        }
    } catch (error) {
        console.error('加载审核员列表失败:', error);
        assessorList.innerHTML = '<div class="loading"><i class="fas fa-exclamation-circle"></i> 网络错误，请稍后重试</div>';
    }
}

function renderAssessors(assessors) {
    const assessorList = document.getElementById('assessorList');

    if (!assessors || assessors.length === 0) {
        assessorList.innerHTML = '<div class="loading"><i class="fas fa-inbox"></i> 暂无审核员</div>';
        return;
    }

    assessorList.innerHTML = assessors.map(assessor => {
        const isSelected = selectedAssessor === assessor.id;
        return `
        <div class="assessor-card ${isSelected ? 'selected' : ''}" data-id="${assessor.id}" onclick="selectAssessor(${assessor.id})">
            <div class="assessor-icon">
                <i class="fas fa-user-shield"></i>
            </div>
            <div class="assessor-name">${assessor.realName}</div>
            <div class="assessor-info">
                <div>${assessor.workId || '-'}</div>
            </div>
        </div>
        `;
    }).join('');
}

function selectAssessor(assessorId) {
    selectedAssessor = assessorId;

    document.querySelectorAll('#assessorList .assessor-card').forEach(item => {
        item.classList.remove('selected');
    });

    const selectedItem = document.querySelector(`#assessorList .assessor-card[data-id="${assessorId}"]`);
    if (selectedItem) {
        selectedItem.classList.add('selected');
    }

    document.getElementById('nextStep2').disabled = false;
}

function validateStep3() {
    const deadline = document.getElementById('taskDeadline').value;
    document.getElementById('nextStep3').disabled = !deadline;
}

function generateTaskSummary() {
    const summary = document.getElementById('taskSummary');
    const deadline = document.getElementById('taskDeadline').value;

    const configuredCourses = Array.from(courseTeachersMap.keys()).filter(courseId => {
        const teachers = courseTeachersMap.get(courseId);
        return teachers && teachers.length > 0;
    });

    const totalTeachers = configuredCourses.reduce((sum, courseId) => {
        const teachers = courseTeachersMap.get(courseId) || [];
        return sum + teachers.length;
    }, 0);

    summary.innerHTML = `
        <div class="summary-item">
            <span class="summary-label">已配置课程数</span>
            <span class="summary-value">${configuredCourses.length} 门</span>
        </div>
        <div class="summary-item">
            <span class="summary-label">分配教师总数</span>
            <span class="summary-value">${totalTeachers} 人</span>
        </div>
        <div class="summary-item">
            <span class="summary-label">指定审核员</span>
            <span class="summary-value" id="summaryAssessor">加载中...</span>
        </div>
        <div class="summary-item">
            <span class="summary-label">截止日期</span>
            <span class="summary-value">${formatDateTime(deadline)}</span>
        </div>
    `;

    loadSummaryInfo();
}

async function loadSummaryInfo() {
    try {
        const assessorRes = await fetch(`${API_BASE_URL}/admin/assessors`);
        const assessorData = await assessorRes.json();

        if (assessorData.code === 200) {
            const assessor = assessorData.data.find(a => a.id === selectedAssessor);
            if (assessor) {
                document.getElementById('summaryAssessor').textContent = `${assessor.realName} (${assessor.workId})`;
            }
        }
    } catch (error) {
        console.error('加载摘要信息失败:', error);
    }
}

async function publishTasks() {
    const deadline = document.getElementById('taskDeadline').value;
    const description = document.getElementById('taskDescription').value.trim();
    const materialRequirements = document.getElementById('taskMaterialRequirements').value.trim();

    const configuredCourses = Array.from(courseTeachersMap.keys()).filter(courseId => {
        const teachers = courseTeachersMap.get(courseId);
        return teachers && teachers.length > 0;
    });

    if (configuredCourses.length === 0 || !deadline) {
        alert('请完成所有步骤后再发布任务');
        return;
    }

    const tasks = [];
    configuredCourses.forEach(courseId => {
        const teachers = courseTeachersMap.get(courseId) || [];
        teachers.forEach(teacher => {
            console.log('准备添加任务 - 课程ID:', courseId, '教师:', teacher);

            if (teacher.isManual) {
                tasks.push({
                    courseId: teacher.teacherId,
                    teacherId: teacher.id,
                    assessorId: selectedAssessor,
                    deadline: deadline,
                    description: description,
                    materialRequirements: materialRequirements,
                    courseName: teacher.courseName,
                    teachingClass: teacher.teachingClass,
                    isManual: true
                });
            } else {
                tasks.push({
                    courseId: teacher.teacherId,
                    teacherId: teacher.id,
                    assessorId: selectedAssessor,
                    deadline: deadline,
                    description: description,
                    materialRequirements: materialRequirements
                });
            }
        });
    });

    console.log('准备发布的任务列表:', tasks);

    if (tasks.length === 0) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/tasks/batch`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ tasks: tasks })
        });

        const data = await response.json();

        if (data.code === 200) {
            alert(`任务发布成功！共创建 ${tasks.length} 个任务`);
            resetAssignSteps();
            loadCourses(1);
        } else {
            alert('任务发布失败: ' + data.message);
        }
    } catch (error) {
        console.error('发布任务失败:', error);
        alert('网络错误，请稍后重试');
    }
}

function openAssignedTasksModal() {
    const modal = document.getElementById('assignedTasksModal');
    modal.classList.add('show');
    loadTempTasks();
}

function closeAssignedTasksModal() {
    const modal = document.getElementById('assignedTasksModal');
    modal.classList.remove('show');
}

function loadTempTasks() {
    const tbody = document.getElementById('assignedTasksTableBody');
    tbody.innerHTML = '';

    console.log('courseTeachersMap:', courseTeachersMap);

    if (!courseTeachersMap || courseTeachersMap.size === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="empty-row"><i class="fas fa-inbox"></i> 暂无暂存任务</td></tr>';
        return;
    }

    let taskCount = 0;
    courseTeachersMap.forEach((teachers, courseId) => {
        console.log(`课程ID ${courseId} 的教师:`, teachers);
        if (!teachers || teachers.length === 0) return;

        teachers.forEach(teacher => {
            console.log('单个教师数据:', teacher);
            console.log('teachingClass:', teacher.teachingClass);
            console.log('courseName:', teacher.courseName);
            taskCount++;
            tbody.innerHTML += `
                <tr>
                    <td>${teacher.courseName || '-'}</td>
                    <td>${teacher.teachingClass || '-'}</td>
                    <td>${teacher.teacherName || teacher.realName || '-'}</td>
                    <td>
                        <button class="btn-icon delete-temp-task-btn" data-course-id="${courseId}" data-teacher-id="${teacher.id}" title="删除">
                            <i class="fas fa-trash"></i>
                        </button>
                    </td>
                </tr>
            `;
        });
    });

    if (taskCount === 0) {
        tbody.innerHTML = '<tr><td colspan="4" class="empty-row"><i class="fas fa-inbox"></i> 暂无暂存任务</td></tr>';
    }

    initTempTaskButtons();
}

function initTempTaskButtons() {
    document.querySelectorAll('.delete-temp-task-btn').forEach(btn => {
        btn.addEventListener('click', function () {
            const courseId = parseInt(this.getAttribute('data-course-id'));
            const teacherId = parseInt(this.getAttribute('data-teacher-id'));

            if (confirm('确定要删除这个暂存任务吗？')) {
                deleteTempTask(courseId, teacherId);
            }
        });
    });
}

function deleteTempTask(courseId, teacherId) {
    const teachers = courseTeachersMap.get(courseId);
    if (teachers) {
        const index = teachers.findIndex(t => t.id === teacherId);
        if (index > -1) {
            teachers.splice(index, 1);

            if (teachers.length === 0) {
                courseTeachersMap.delete(courseId);
                // 从selectedCourses中移除该课程ID
                selectedCourses = selectedCourses.filter(id => id !== courseId);
                // 更新课程行的选中状态
                const courseRow = document.querySelector(`.course-row[data-id="${courseId}"]`);
                if (courseRow) {
                    courseRow.classList.remove('selected');
                }
            }

            updateConfiguredCount();
            loadTempTasks();
        }
    }
}

async function loadStatistics() {
    try {
        const response = await fetch(`${API_BASE_URL}/admin/statistics`);
        const data = await response.json();

        if (data.code === 200) {
            const stats = data.data;
            document.getElementById('statTotalTasks').textContent = stats.totalTasks || 0;
            document.getElementById('statApprovedTasks').textContent = stats.approvedTasks || 0;
            document.getElementById('statPendingTasks').textContent = stats.submittedTasks || 0;
            document.getElementById('statNeedRevisionTasks').textContent = stats.needRevisionTasks || 0;
            document.getElementById('statTotalCourses').textContent = stats.totalCourses || 0;
            document.getElementById('statTotalUsers').textContent = stats.totalUsers || 0;
            document.getElementById('statUploadedCourseFiles').textContent = stats.uploadedCourseFiles || 0;
        }
    } catch (error) {
        console.error('加载统计数据失败:', error);
    }
}

async function loadUploadedFiles() {
    try {
        const response = await fetch(`${API_BASE_URL}/admin/uploaded-course-files`);
        const data = await response.json();

        const filesListDiv = document.getElementById('uploadedFilesList');

        if (data.code === 200) {
            const files = data.data || [];

            if (files.length === 0) {
                filesListDiv.innerHTML = '<div class="no-files"><i class="fas fa-inbox"></i> 暂无上传文档</div>';
                return;
            }

            const filesHtml = files.map(file => `
                <div class="file-item">
                    <i class="fas fa-file-csv"></i>
                    <span class="file-name">${file}</span>
                </div>
            `).join('');

            filesListDiv.innerHTML = filesHtml;
        } else {
            filesListDiv.innerHTML = '<div class="error">加载失败</div>';
        }
    } catch (error) {
        console.error('加载上传文档列表失败:', error);
        document.getElementById('uploadedFilesList').innerHTML = '<div class="error">加载失败</div>';
    }
}

function initModal() {
    const modal = document.getElementById('deadlineModal');
    const closeBtn = document.getElementById('closeDeadlineModal');
    const cancelBtn = document.getElementById('cancelDeadline');
    const saveBtn = document.getElementById('saveDeadline');

    if (closeBtn) {
        closeBtn.addEventListener('click', closeModal);
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeModal);
    }

    if (saveBtn) {
        saveBtn.addEventListener('click', saveDeadline);
    }

    if (modal) {
        modal.addEventListener('click', function (e) {
            if (e.target === modal) {
                closeModal();
            }
        });
    }
}

function openDeadlineModal(taskId, currentDeadline) {
    currentEditingTaskId = taskId;
    const modal = document.getElementById('deadlineModal');
    const input = document.getElementById('newDeadline');

    if (currentDeadline) {
        const date = new Date(currentDeadline);
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        input.value = `${year}-${month}-${day}T${hours}:${minutes}`;
    }

    modal.classList.add('show');
}

function closeModal() {
    const modal = document.getElementById('deadlineModal');
    modal.classList.remove('show');
    currentEditingTaskId = null;
}

async function saveDeadline() {
    const newDeadline = document.getElementById('newDeadline').value;

    if (!newDeadline) {
        alert('请选择截止日期');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/tasks/${currentEditingTaskId}/deadline?deadline=${newDeadline}`, {
            method: 'PUT'
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('截止日期修改成功！');
            closeModal();
            loadTasks();
        } else {
            alert('修改失败: ' + data.message);
        }
    } catch (error) {
        console.error('修改截止日期失败:', error);
        alert('网络错误，请稍后重试');
    }
}

async function viewTaskDetail(taskId) {
    currentEditingTaskId = taskId;
    $('#tasks-view').hide();
    $('#task-detail-view').show();

    try {
        const timestamp = new Date().getTime();
        const response = await fetch(`${API_BASE_URL}/tasks/${taskId}?t=${timestamp}`, {
            method: 'GET',
            headers: {
                'Cache-Control': 'no-cache',
                'Pragma': 'no-cache'
            }
        });
        const data = await response.json();

        if (data.code === 200) {
            const task = data.data;
            console.log('获取到的任务详情:', task);
            console.log('更新前的description:', $('#detailDescription').text());
            console.log('更新前的materialRequirements:', $('#detailMaterialReq').text());

            $('#detailCourseName').text(task.courseName || '-');
            $('#detailTeachingClass').text(task.teachingClass || '-');
            $('#detailTeacher').text(`${task.teacherName || '-'} (${task.teacherWorkId || '-'})`);
            $('#detailAssessor').text(`${task.assessorName || '-'} (${task.assessorWorkId || '-'})`);
            $('#detailGrade').text(task.grade || '-');
            $('#detailSemester').text(task.semester || '-');
            $('#detailDeadline').text(formatDateTime(task.deadline));
            $('#detailStatus').text(task.statusDesc || task.status).attr('class', 'status-badge ' + getStatusClass(task.status));
            $('#detailCreateTime').text(formatDateTime(task.createTime));
            $('#detailUpdateTime').text(formatDateTime(task.updateTime));

            const newDescription = task.description || '无';
            const newMaterialReq = task.materialRequirements || '无';

            $('#detailDescription').html(task.description ? formatMaterialRequirements(task.description) : '无');
            $('#detailMaterialReq').html(task.materialRequirements ? formatMaterialRequirements(task.materialRequirements) : '无');

            setTimeout(() => {
                console.log('延迟检查 - 更新后的description:', $('#detailDescription').html());
                console.log('延迟检查 - 更新后的materialRequirements:', $('#detailMaterialReq').html());
            }, 100);

            loadTaskFilesForAdmin(taskId);
            loadLatestReviewForAdmin(taskId);
        } else {
            alert('加载任务详情失败');
            $('#tasks-view').show();
            $('#task-detail-view').hide();
        }
    } catch (error) {
        console.error('加载任务详情失败:', error);
        alert('网络错误，请稍后重试');
        $('#tasks-view').show();
        $('#task-detail-view').hide();
    }
}

$('#backToTasksBtn').on('click', function () {
    $('#task-detail-view').hide();
    $('#tasks-view').show();
    currentEditingTaskId = null;
});

$('#editTaskBtn').on('click', async function () {
    if (!currentEditingTaskId) return;

    try {
        const timestamp = new Date().getTime();
        const response = await fetch(`${API_BASE_URL}/tasks/${currentEditingTaskId}?t=${timestamp}`, {
            method: 'GET',
            headers: {
                'Cache-Control': 'no-cache',
                'Pragma': 'no-cache'
            }
        });
        const data = await response.json();

        if (data.code === 200) {
            const task = data.data;
            console.log('打开编辑弹窗时的任务数据:', task);
            $('#editTaskDescription').val(task.description || '');
            $('#editTaskMaterialReq').val(task.materialRequirements || '');
            $('#editTaskModal').addClass('show');
        } else {
            alert('加载任务信息失败');
        }
    } catch (error) {
        console.error('加载任务信息失败:', error);
        alert('网络错误，请稍后重试');
    }
});

$('#closeEditTaskModal').on('click', function () {
    $('#editTaskModal').removeClass('show');
});

$('#cancelEditTask').on('click', function () {
    $('#editTaskModal').removeClass('show');
});

$('#confirmEditTask').on('click', async function () {
    if (!currentEditingTaskId) return;

    const description = $('#editTaskDescription').val();
    const materialRequirements = $('#editTaskMaterialReq').val();

    console.log('准备更新任务:', {
        taskId: currentEditingTaskId,
        description: description,
        materialRequirements: materialRequirements
    });

    try {
        const response = await fetch(`${API_BASE_URL}/tasks/${currentEditingTaskId}`, {
            method: 'PUT',
            headers: {
                'Content-Type': 'application/json',
                'Cache-Control': 'no-cache',
                'Pragma': 'no-cache'
            },
            body: JSON.stringify({
                description: description,
                materialRequirements: materialRequirements
            })
        });

        const data = await response.json();
        console.log('更新任务响应:', data);

        if (data.code === 200) {
            $('#editTaskModal').removeClass('show');
            alert('任务信息更新成功');

            console.log('直接更新DOM元素');
            $('#detailDescription').html(description || '无');
            $('#detailMaterialReq').html(materialRequirements || '无');

            setTimeout(() => {
                console.log('延迟检查 - 直接更新后的description:', $('#detailDescription').html());
                console.log('延迟检查 - 直接更新后的materialRequirements:', $('#detailMaterialReq').html());
            }, 100);
        } else {
            alert('任务信息更新失败: ' + (data.message || '未知错误'));
        }
    } catch (error) {
        console.error('更新任务信息失败:', error);
        alert('网络错误，请稍后重试');
    }
});

async function loadTaskFilesForAdmin(taskId) {
    $('#filesTableBody').html('<tr><td colspan="4" class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载文件列表...</td></tr>');

    try {
        const response = await fetch(`${API_BASE_URL}/files/admin/task/${taskId}`);
        const data = await response.json();

        if (data.code === 200) {
            renderAdminFiles(data.data || []);
        } else {
            $('#filesTableBody').html('<tr><td colspan="4" class="loading-row"><i class="fas fa-exclamation-circle"></i> 加载失败</td></tr>');
        }
    } catch (error) {
        console.error('加载文件列表失败:', error);
        $('#filesTableBody').html('<tr><td colspan="4" class="loading-row"><i class="fas fa-exclamation-circle"></i> 网络错误</td></tr>');
    }
}

function renderAdminFiles(files) {
    const tbody = $('#filesTableBody');
    if (!files || files.length === 0) {
        tbody.html('<tr><td colspan="4" class="loading-row"><i class="fas fa-inbox"></i> 暂无文件</td></tr>');
        return;
    }

    let html = '';
    files.forEach(file => {
        html += `
            <tr>
                <td><i class="fas fa-file"></i> ${file.fileName}</td>
                <td>${file.fileSizeFormatted || formatFileSize(file.fileSize)}</td>
                <td>${formatDateTime(file.uploadTime)}</td>
                <td>
                    <button class="btn-icon" onclick="downloadAdminFile(${file.id}, '${file.fileName}')" title="下载">
                        <i class="fas fa-download"></i>
                    </button>
                </td>
            </tr>
        `;
    });
    tbody.html(html);
}

async function downloadAdminFile(fileId, fileName) {
    try {
        const response = await fetch(`${API_BASE_URL}/files/admin/${fileId}/download`);
        if (!response.ok) {
            throw new Error('下载失败');
        }

        const blob = await response.blob();
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
        alert('下载成功');
    } catch (error) {
        console.error('下载文件失败:', error);
        alert('下载失败，请稍后重试');
    }
}

async function loadLatestReviewForAdmin(taskId) {
    const reviewContainer = $('#latestReviewContent');
    if (!reviewContainer.length) return;

    reviewContainer.html('<div class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载审核记录...</div>');

    try {
        const response = await fetch(`${API_BASE_URL}/reviews/admin/task/${taskId}/latest`);
        const data = await response.json();

        if (data.code === 200) {
            const review = data.data;
            if (!review) {
                reviewContainer.html('<div class="no-data"><i class="fas fa-inbox"></i> 暂无审核记录</div>');
            } else {
                let html = `
                    <div class="review-info-row">
                        <span class="info-label">审核员：</span>
                        <span class="info-value">${review.assessorName || '-'}</span>
                    </div>
                    <div class="review-info-row">
                        <span class="info-label">审核状态：</span>
                        <span class="info-value"><span class="status-badge ${review.reviewStatus === 'APPROVED' ? 'status-approved' : 'status-rejected'}">${review.reviewStatusDesc || review.reviewStatus}</span></span>
                    </div>
                    <div class="review-info-row">
                        <span class="info-label">审核时间：</span>
                        <span class="info-value">${formatDateTime(review.reviewTime)}</span>
                    </div>
                `;
                if (review.score) {
                    html += `
                        <div class="review-info-row">
                            <span class="info-label">评分：</span>
                            <span class="info-value">${review.score}分</span>
                        </div>
                    `;
                }
                if (review.comment) {
                    html += `
                        <div class="review-info-row">
                            <span class="info-label">审核意见：</span>
                            <span class="info-value">${review.comment}</span>
                        </div>
                    `;
                }
                if (review.suggestions) {
                    html += `
                        <div class="review-info-row">
                            <span class="info-label">修改建议：</span>
                            <span class="info-value">${review.suggestions}</span>
                        </div>
                    `;
                }
                reviewContainer.html(html);
            }
        } else {
            reviewContainer.html(`<div class="error"><i class="fas fa-exclamation-circle"></i> 加载失败: ${data.msg}</div>`);
        }
    } catch (error) {
        console.error('加载审核记录失败:', error);
        reviewContainer.html('<div class="error"><i class="fas fa-exclamation-circle"></i> 网络错误</div>');
    }
}



async function remindTask(taskId) {
    if (!confirm('确认向该任务相关人员发送催办提醒吗？')) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/tasks/${taskId}/remind`, {
            method: 'POST'
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('催办成功，已发送提醒通知');
        } else {
            alert('催办失败: ' + data.message);
        }
    } catch (error) {
        console.error('催办失败:', error);
        alert('网络错误，请稍后重试');
    }
}

let currentDeletingTaskId = null;

function deleteTask(taskId) {
    currentDeletingTaskId = taskId;
    const modal = document.getElementById('deleteConfirmModal');
    modal.classList.add('show');
}

async function confirmDeleteTask() {
    if (!currentDeletingTaskId) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/tasks/${currentDeletingTaskId}`, {
            method: 'DELETE'
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('任务删除成功！');
            closeDeleteConfirmModal();
            loadTasks();
        } else {
            alert('删除失败: ' + data.message);
        }
    } catch (error) {
        console.error('删除任务失败:', error);
        alert('网络错误，请稍后重试');
    }
}

function closeDeleteConfirmModal() {
    const modal = document.getElementById('deleteConfirmModal');
    modal.classList.remove('show');
    currentDeletingTaskId = null;
}

function initDeleteModal() {
    const closeBtn = document.getElementById('closeDeleteConfirmModal');
    const cancelBtn = document.getElementById('cancelDelete');
    const confirmBtn = document.getElementById('confirmDelete');

    if (closeBtn) {
        closeBtn.addEventListener('click', closeDeleteConfirmModal);
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeDeleteConfirmModal);
    }

    if (confirmBtn) {
        confirmBtn.addEventListener('click', confirmDeleteTask);
    }

    const modal = document.getElementById('deleteConfirmModal');
    modal.addEventListener('click', function (e) {
        if (e.target === modal) {
            closeDeleteConfirmModal();
        }
    });
}

let currentDeletingMemberId = null;

function initMemberManagement() {
    const filterBtn = document.getElementById('filterMembersBtn');
    const addMemberBtn = document.getElementById('addMemberBtn');

    if (filterBtn) {
        filterBtn.addEventListener('click', loadMembers);
    }

    if (addMemberBtn) {
        addMemberBtn.addEventListener('click', openAddMemberModal);
    }

    loadMembers();
}

async function loadMembers() {
    const memberList = document.getElementById('memberList');
    const role = document.getElementById('memberRoleFilter').value;
    const code = document.getElementById('memberCodeFilter').value;
    const name = document.getElementById('memberNameFilter').value;

    memberList.innerHTML = '<div class="loading"><i class="fas fa-spinner fa-spin"></i> 加载成员列表...</div>';

    try {
        let url = `${API_BASE_URL}/admin/members`;
        const params = new URLSearchParams();
        if (role) params.append('role', role);
        if (code) params.append('code', code);
        if (name) params.append('name', name);
        if (params.toString()) url += '?' + params.toString();

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            renderMembers(data.data);
        } else {
            memberList.innerHTML = `<div class="loading"><i class="fas fa-exclamation-circle"></i> 加载失败: ${data.message}</div>`;
        }
    } catch (error) {
        console.error('加载成员列表失败:', error);
        memberList.innerHTML = '<div class="loading"><i class="fas fa-exclamation-circle"></i> 网络错误，请稍后重试</div>';
    }
}

function renderMembers(members) {
    const memberList = document.getElementById('memberList');

    if (!members || members.length === 0) {
        memberList.innerHTML = '<div class="loading"><i class="fas fa-inbox"></i> 暂无成员</div>';
        return;
    }

    memberList.innerHTML = members.map(member => `
        <div class="list-item member-item" data-id="${member.id}">
            <div class="item-main">
                <div class="item-name">${member.realName}</div>
                <div class="item-code">${member.workId}</div>
                <div class="item-role role-${member.role.toLowerCase()}">${getRoleText(member.role)}</div>
            </div>
            <div class="item-info">
                <div>${member.department || '-'}</div>
                <div>${member.title || '-'}</div>
            </div>
            <div class="item-actions">
                ${member.role !== 'ADMIN' ? `
                    <button class="btn-icon" onclick="editMemberRole(${member.id})" title="修改角色">
                        <i class="fas fa-user-edit"></i>
                    </button>
                    <button class="btn-icon" onclick="toggleMemberStatus(${member.id}, ${member.status === 1 ? 0 : 1})" title="${member.status === 1 ? '禁用' : '启用'}">
                        <i class="fas ${member.status === 1 ? 'fa-ban' : 'fa-check'}"></i>
                    </button>
                    <button class="btn-icon danger" onclick="deleteMember(${member.id})" title="删除">
                        <i class="fas fa-trash"></i>
                    </button>
                ` : ''}
            </div>
        </div>
    `).join('');
}

function getRoleText(role) {
    const roleMap = {
        'ADMIN': '管理员',
        'TEACHER': '教师',
        'ASSESSOR': '审核员'
    };
    return roleMap[role] || role;
}

function initAddMemberModal() {
    const closeBtn = document.getElementById('closeAddMemberModal');
    const cancelBtn = document.getElementById('cancelAddMember');
    const saveBtn = document.getElementById('saveAddMember');

    if (closeBtn) {
        closeBtn.addEventListener('click', closeAddMemberModal);
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeAddMemberModal);
    }

    if (saveBtn) {
        saveBtn.addEventListener('click', saveAddMember);
    }

    const modal = document.getElementById('addMemberModal');
    modal.addEventListener('click', function (e) {
        if (e.target === modal) {
            closeAddMemberModal();
        }
    });
}

function openAddMemberModal() {
    const modal = document.getElementById('addMemberModal');
    modal.classList.add('show');
}

function closeAddMemberModal() {
    const modal = document.getElementById('addMemberModal');
    modal.classList.remove('show');
    clearAddMemberForm();
}

function clearAddMemberForm() {
    document.getElementById('newUsername').value = '';
    document.getElementById('newWorkId').value = '';
    document.getElementById('newRealName').value = '';
    document.getElementById('newEmail').value = '';
    document.getElementById('newPhone').value = '';
    document.getElementById('newPassword').value = '';
    document.getElementById('newRole').value = '';
    document.getElementById('newDepartment').value = '';
    document.getElementById('newTitle').value = '';
}

async function saveAddMember() {
    const username = document.getElementById('newUsername').value.trim();
    const workId = document.getElementById('newWorkId').value.trim();
    const realName = document.getElementById('newRealName').value.trim();
    const email = document.getElementById('newEmail').value.trim();
    const phone = document.getElementById('newPhone').value.trim();
    const password = document.getElementById('newPassword').value;
    const role = document.getElementById('newRole').value;
    const department = document.getElementById('newDepartment').value.trim();
    const title = document.getElementById('newTitle').value.trim();

    if (!username || !workId || !realName || !email || !phone || !password || !role) {
        alert('请填写所有必填项');
        return;
    }

    if (username.length < 3 || username.length > 20) {
        alert('用户名长度为3-20个字符');
        return;
    }

    if (password.length < 6) {
        alert('密码长度至少6位');
        return;
    }

    const phoneRegex = /^1[3-9]\d{9}$/;
    if (!phoneRegex.test(phone)) {
        alert('手机号格式不正确，请输入正确的11位手机号');
        return;
    }

    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    if (!emailRegex.test(email)) {
        alert('邮箱格式不正确');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/members`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                username,
                workId,
                realName,
                email,
                phone,
                password,
                role,
                department,
                title
            })
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('添加成员成功');
            closeAddMemberModal();
            loadMembers();
        } else {
            if (data.errors) {
                const errorMessages = Object.values(data.errors).join('\n');
                alert('添加失败:\n' + errorMessages);
            } else {
                alert('添加失败: ' + data.message);
            }
        }
    } catch (error) {
        console.error('添加成员失败:', error);
        alert('网络错误，请稍后重试');
    }
}

function initDeleteMemberModal() {
    const closeBtn = document.getElementById('closeDeleteMemberConfirmModal');
    const cancelBtn = document.getElementById('cancelDeleteMember');
    const confirmBtn = document.getElementById('confirmDeleteMember');

    if (closeBtn) {
        closeBtn.addEventListener('click', closeDeleteMemberConfirmModal);
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeDeleteMemberConfirmModal);
    }

    if (confirmBtn) {
        confirmBtn.addEventListener('click', confirmDeleteMember);
    }

    const modal = document.getElementById('deleteMemberConfirmModal');
    modal.addEventListener('click', function (e) {
        if (e.target === modal) {
            closeDeleteMemberConfirmModal();
        }
    });
}

function deleteMember(memberId) {
    currentDeletingMemberId = memberId;
    const modal = document.getElementById('deleteMemberConfirmModal');
    modal.classList.add('show');
}

function closeDeleteMemberConfirmModal() {
    const modal = document.getElementById('deleteMemberConfirmModal');
    modal.classList.remove('show');
    currentDeletingMemberId = null;
}

function initImportFunctions() {
    const uploadBox = document.getElementById('uploadBox');
    const fileInput = document.getElementById('courseFileInput');

    if (uploadBox && fileInput) {
        // 移除之前的事件监听器（通过克隆节点）
        const newUploadBox = uploadBox.cloneNode(true);
        uploadBox.parentNode.replaceChild(newUploadBox, uploadBox);

        // 重新获取元素引用
        const freshUploadBox = document.getElementById('uploadBox');

        // 点击事件 - 使用 onclick 属性
        freshUploadBox.onclick = function () {
            fileInput.click();
        };

        // 拖拽事件
        freshUploadBox.addEventListener('dragover', (e) => {
            e.preventDefault();
            freshUploadBox.classList.add('dragover');
        });

        freshUploadBox.addEventListener('dragleave', () => {
            freshUploadBox.classList.remove('dragover');
        });

        freshUploadBox.addEventListener('drop', (e) => {
            e.preventDefault();
            freshUploadBox.classList.remove('dragover');
            const files = e.dataTransfer.files;
            if (files.length > 0) {
                handleFile(files[0]);
            }
        });

        // 文件选择变化
        fileInput.addEventListener('change', handleFileSelect);
    }
}

function handleFileSelect(e) {
    const file = e.target.files[0];
    if (file) {
        handleFile(file);
    }
}

function handleFile(file) {
    const uploadInfo = document.getElementById('uploadInfo');
    const fileName = document.getElementById('fileName');
    const fileSize = document.getElementById('fileSize');
    const uploadBox = document.getElementById('uploadBox');
    const fileInput = document.getElementById('courseFileInput');

    // 创建一个DataTransfer对象，用于设置fileInput的值
    const dataTransfer = new DataTransfer();
    dataTransfer.items.add(file);
    fileInput.files = dataTransfer.files;

    fileName.textContent = file.name;
    fileSize.textContent = formatFileSize(file.size);

    uploadBox.style.display = 'none';
    uploadInfo.style.display = 'flex';
}

function clearFile() {
    const fileInput = document.getElementById('courseFileInput');
    const uploadInfo = document.getElementById('uploadInfo');
    const uploadBox = document.getElementById('uploadBox');

    fileInput.value = '';
    uploadInfo.style.display = 'none';
    uploadBox.style.display = 'flex';
}

function formatFileSize(bytes) {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round(bytes / Math.pow(k, i) * 100) / 100 + ' ' + sizes[i];
}

async function uploadCourseFile() {
    const fileInput = document.getElementById('courseFileInput');
    const file = fileInput.files[0];
    const charset = document.getElementById('charsetSelect').value;

    if (!file) {
        alert('请选择文件');
        return;
    }

    const formData = new FormData();
    formData.append('file', file);
    formData.append('charset', charset);

    const uploadBtn = document.getElementById('uploadBtn');
    uploadBtn.disabled = true;
    uploadBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 上传中...';

    try {
        const response = await fetch(`${API_BASE_URL}/admin/upload/course-file`, {
            method: 'POST',
            body: formData
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('文件上传并解析成功');
            clearFile();
        } else {
            alert('上传失败: ' + data.message);
        }
    } catch (error) {
        console.error('上传文件失败:', error);
        alert('网络错误，请稍后重试');
    } finally {
        uploadBtn.disabled = false;
        uploadBtn.innerHTML = '<i class="fas fa-upload"></i> 上传并解析';
    }
}

async function fetchTeachers() {
    const courseName = document.getElementById('courseNameInput').value.trim();

    if (!courseName) {
        alert('请输入课程名称');
        return;
    }

    const fetchBtn = document.getElementById('fetchTeachersBtn');
    const teacherResult = document.getElementById('teacherResult');
    const fetchedTeacherList = document.getElementById('fetchedTeacherList');

    fetchBtn.disabled = true;
    fetchBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 获取中...';
    teacherResult.style.display = 'none';

    try {
        const response = await fetch(`${API_BASE_URL}/admin/fetch-teachers-current?courseName=${encodeURIComponent(courseName)}`);
        const data = await response.json();

        if (data.code === 200) {
            renderFetchedTeachers(data.data);
            teacherResult.style.display = 'block';
        } else {
            alert('获取教师列表失败: ' + data.message);
        }
    } catch (error) {
        console.error('获取教师列表失败:', error);
        alert('网络错误，请稍后重试');
    } finally {
        fetchBtn.disabled = false;
        fetchBtn.innerHTML = '<i class="fas fa-search"></i> 获取教师列表';
    }
}

function renderFetchedTeachers(teachers) {
    const fetchedTeacherList = document.getElementById('fetchedTeacherList');

    if (!teachers || teachers.length === 0) {
        fetchedTeacherList.innerHTML = '<div class="loading"><i class="fas fa-inbox"></i> 未找到相关教师</div>';
        return;
    }

    fetchedTeacherList.innerHTML = teachers.map(teacher => `
        <div class="list-item" data-id="${teacher.id}" onclick="toggleFetchedTeacherSelection(${teacher.id})">
            <div class="item-main">
                <div class="item-name">${teacher.teacherName}</div>
                <div class="item-code">${teacher.courseCode || '未设置'}</div>
            </div>
            <div class="item-info">
                <div>${teacher.department || '-'}</div>
                <div>${teacher.title || '-'}</div>
            </div>
        </div>
    `).join('');
}

let selectedFetchedTeachers = [];

function toggleFetchedTeacherSelection(teacherId) {
    const index = selectedFetchedTeachers.indexOf(teacherId);
    const item = document.querySelector(`#fetchedTeacherList .list-item[data-id="${teacherId}"]`);

    if (index > -1) {
        selectedFetchedTeachers.splice(index, 1);
        if (item) item.classList.remove('selected');
    } else {
        selectedFetchedTeachers.push(teacherId);
        if (item) item.classList.add('selected');
    }
}

async function saveFetchedTeachers() {
    if (selectedFetchedTeachers.length === 0) {
        alert('请至少选择一名教师');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/teachers/save-to-user`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(selectedFetchedTeachers)
        });

        const data = await response.json();

        if (data.code === 200) {
            alert(`成功添加 ${selectedFetchedTeachers.length} 名教师`);
            selectedFetchedTeachers = [];
            document.getElementById('teacherResult').style.display = 'none';
            document.getElementById('courseNameInput').value = '';
        } else {
            alert('添加失败: ' + data.message);
        }
    } catch (error) {
        console.error('添加教师失败:', error);
        alert('网络错误，请稍后重试');
    }
}

async function confirmDeleteMember() {
    if (!currentDeletingMemberId) {
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/members/${currentDeletingMemberId}`, {
            method: 'DELETE'
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('删除成功');
            closeDeleteMemberConfirmModal();
            loadMembers();
        } else {
            alert('删除失败: ' + data.message);
        }
    } catch (error) {
        console.error('删除成员失败:', error);
        alert('网络错误，请稍后重试');
    }
}

async function toggleMemberStatus(memberId, status) {
    try {
        const response = await fetch(`${API_BASE_URL}/admin/members/${memberId}/status?status=${status}`, {
            method: 'PUT'
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('更新状态成功');
            loadMembers();
        } else {
            alert('更新失败: ' + data.message);
        }
    } catch (error) {
        console.error('更新成员状态失败:', error);
        alert('网络错误，请稍后重试');
    }
}

function initEditRoleModal() {
    const closeBtn = document.getElementById('closeEditRoleModal');
    const cancelBtn = document.getElementById('cancelEditRole');
    const saveBtn = document.getElementById('saveEditRole');

    if (closeBtn) {
        closeBtn.addEventListener('click', closeEditRoleModal);
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeEditRoleModal);
    }

    if (saveBtn) {
        saveBtn.addEventListener('click', saveEditRole);
    }

    const modal = document.getElementById('editRoleModal');
    modal.addEventListener('click', function (e) {
        if (e.target === modal) {
            closeEditRoleModal();
        }
    });
}

function initEditNotificationModal() {
    const closeBtn = document.getElementById('closeEditNotificationModal');
    const cancelBtn = document.getElementById('cancelEditNotification');
    const saveBtn = document.getElementById('saveEditNotification');

    if (closeBtn) {
        closeBtn.addEventListener('click', closeEditNotificationModal);
    }

    if (cancelBtn) {
        cancelBtn.addEventListener('click', closeEditNotificationModal);
    }

    if (saveBtn) {
        saveBtn.addEventListener('click', saveEditNotification);
    }

    const modal = document.getElementById('editNotificationModal');
    modal.addEventListener('click', function (e) {
        if (e.target === modal) {
            closeEditNotificationModal();
        }
    });
}

function editMemberRole(memberId) {
    const members = document.querySelectorAll('.member-item');
    let member = null;
    members.forEach(item => {
        if (parseInt(item.dataset.id) === memberId) {
            member = {
                id: memberId,
                realName: item.querySelector('.item-name').textContent,
                role: item.querySelector('.item-role').textContent
            };
        }
    });

    if (!member) {
        return;
    }

    currentEditingMemberId = memberId;
    document.getElementById('editRoleMemberName').value = member.realName;
    document.getElementById('editRoleCurrentRole').value = getRoleText(member.role);
    document.getElementById('editRoleNewRole').value = '';

    const modal = document.getElementById('editRoleModal');
    modal.classList.add('show');
}

function closeEditRoleModal() {
    const modal = document.getElementById('editRoleModal');
    modal.classList.remove('show');
    currentEditingMemberId = null;
}

async function saveEditRole() {
    if (!currentEditingMemberId) {
        return;
    }

    const newRole = document.getElementById('editRoleNewRole').value;

    if (!newRole) {
        alert('请选择新角色');
        return;
    }

    try {
        const response = await fetch(`${API_BASE_URL}/admin/members/${currentEditingMemberId}/role?role=${newRole}`, {
            method: 'PUT'
        });

        const data = await response.json();

        if (data.code === 200) {
            alert('更新角色成功');
            closeEditRoleModal();
            loadMembers();
        } else {
            alert('更新失败: ' + data.message);
        }
    } catch (error) {
        console.error('更新成员角色失败:', error);
        alert('网络错误，请稍后重试');
    }
}

async function loadDepartments() {
    try {
        const res = await fetch(`${API_BASE_URL}/admin/departments`);
        const data = await res.json();
        if (data.code === 200) {
            const majors = data.data || [];
            const gradeMajor = $('#grade-major');
            const semesterMajor = $('#semester-major');
            gradeMajor.empty().append('<option value="">请选择专业</option>');
            semesterMajor.empty().append('<option value="">请选择专业</option>');
            majors.forEach(m => {
                gradeMajor.append(`<option value="${m}">${m}</option>`);
                semesterMajor.append(`<option value="${m}">${m}</option>`);
            });

            // 监听专业变化，加载培养方案列表
            gradeMajor.on('change', function () {
                const selectedMajor = $(this).val();
                if (selectedMajor) {
                    loadCoursePlanGrades(selectedMajor);
                } else {
                    $('#copy-plan-grade').empty().append('<option value="">请选择</option>');
                }
            });
        } else {
            console.error('获取专业列表失败', data.message);
        }
    } catch (error) {
        console.error('加载专业列表失败', error);
    }
}

async function loadCoursePlanGrades(major) {
    try {
        const res = await fetch(`${API_BASE_URL}/admin/course-plans?major=${encodeURIComponent(major)}`);
        const data = await res.json();
        if (data.code === 200) {
            const grades = data.data || [];
            const select = $('#copy-plan-grade');
            select.empty().append('<option value="">请选择</option>');
            grades.forEach(g => {
                select.append(`<option value="${g}">${g}级培养方案</option>`);
            });
        } else {
            console.error('获取培养方案列表失败', data.message);
        }
    } catch (error) {
        console.error('加载培养方案列表失败', error);
    }
}

// ==================== 审核任务分配功能 ====================

// 审核任务分配全局变量
let selectedTasks = [];
let selectedReviewProjects = [];
let selectedAssessors = [];
let assessorAssignments = {};

// 页面加载完成后初始化审核任务分配功能
$(document).ready(function () {
    // 原有初始化调用
    initAdminPage();

    // 监听侧边栏菜单点击
    $('.menu-item').on('click', function () {
        const view = $(this).data('view');
        if (view === 'review-assign') {
            initReviewAssignView();
        } else if (view === 'import') {
            initImportFunctions();
        }
    });

    // 步骤1：查询按钮
    $('#searchReviewTasksBtn').on('click', loadReviewTasks);

    // 点击任务行进行选择
    $('#reviewTasksTableBody').on('click', 'tr.task-row', function (e) {
        if ($(e.target).is('input[type="checkbox"]')) return;
        const taskId = parseInt($(this).data('task-id'));
        const checkbox = $(this).find('.task-checkbox');
        if ($(this).hasClass('selected')) {
            $(this).removeClass('selected');
            checkbox.prop('checked', false);
            selectedTasks = selectedTasks.filter(id => id !== taskId);
        } else {
            $(this).addClass('selected');
            checkbox.prop('checked', true);
            selectedTasks.push(taskId);
        }
    });

    // 复选框点击事件
    $('#reviewTasksTableBody').on('change', '.task-checkbox', function () {
        const taskId = parseInt($(this).val());
        const row = $(this).closest('tr');
        if ($(this).prop('checked')) {
            row.addClass('selected');
            if (!selectedTasks.includes(taskId)) selectedTasks.push(taskId);
        } else {
            row.removeClass('selected');
            selectedTasks = selectedTasks.filter(id => id !== taskId);
        }
    });

    // 步骤导航按钮
    $('#nextToReviewProjects').on('click', function () {
        if (selectedTasks.length === 0) {
            showToast('请至少选择一个任务', 'error');
            return;
        }
        goToStep(2);
    });

    $('#backToTaskSelection').on('click', () => goToStep(1));
    $('#nextToAssessorSelection').on('click', function () {
        selectedReviewProjects = [];
        $('#reviewProjectsGroup input[type="checkbox"]:checked').each(function () {
            selectedReviewProjects.push($(this).val());
        });
        if (selectedReviewProjects.length === 0) {
            showToast('请至少选择一个审核项目', 'error');
            return;
        }
        goToStep(3);
        loadAssessors();
    });
    $('#backToReviewProjects').on('click', () => goToStep(2));
    $('#nextToConfirmation').on('click', function () {
        if (Object.keys(assessorAssignments).length === 0) {
            showToast('请至少分配一个审核人', 'error');
            return;
        }
        showAssignmentSummary();
        goToStep(4);
    });
    $('#backToAssessorSelection').on('click', () => goToStep(3));
    $('#confirmAssignment').on('click', confirmAssignment);

    // 审核人搜索
    $('#assessorSearchInput').on('input', function () {
        const searchValue = $(this).val().toLowerCase();
        $('#availableAssessorsList .assessor-item').each(function () {
            const name = $(this).find('.assessor-name').text().toLowerCase();
            const workId = $(this).find('.assessor-work-id').text().toLowerCase();
            const match = name.includes(searchValue) || workId.includes(searchValue);
            $(this).toggle(match);
        });
    });

    // 穿梭框按钮
    $('#addToAssignment').on('click', addToAssignment);
    $('#removeFromAssignment').on('click', removeFromAssignment);

    // ---------- 新增：未来课程教师检索 ----------
    const $teacherInput = $('#futureCourseTeacher');
    // 防抖输入检索
    $teacherInput.on('input', debounce(function () {
        const keyword = $(this).val().trim();
        searchTeachers(keyword);
    }, 300));

    // 点击页面其他位置关闭建议下拉框
    $(document).on('click', function (e) {
        if (!$(e.target).closest('.teacher-search-wrapper').length) {
            $('#teacherSuggestions').hide();
        }
    });
    // -----------------------------------------
});

// 初始化审核任务分配视图
function initReviewAssignView() {
    // 重置所有状态
    selectedTasks = [];
    selectedReviewProjects = [];
    selectedAssessors = [];
    assessorAssignments = {};
    goToStep(1);

    // 重置表单
    $('#reviewTaskStatusFilter').val('');
    $('#assessorSearchInput').val('');
    $('#reviewProjectsGroup input[type="checkbox"]').prop('checked', false);

    // 设置默认截止日期为一周后
    const defaultDate = new Date();
    defaultDate.setDate(defaultDate.getDate() + 7);
    $('#reviewDeadline').val(formatDateForInput(defaultDate));

    // 清空表格和列表
    $('#reviewTasksTableBody').html('<tr><td colspan="6" class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载中...</td></tr>');
    $('#availableAssessorsList').empty();
    $('#assignedAssessorsList').empty();

    // 自动加载任务列表
    loadReviewTasks();
}

// 加载审核任务列表
async function loadReviewTasks() {
    const status = $('#reviewTaskStatusFilter').val();
    let url = `${API_BASE_URL}/admin/tasks/assignable`;
    if (status) {
        url += `?status=${encodeURIComponent(status)}`;
    }

    try {
        const res = await fetch(url);
        const data = await res.json();
        if (data.code === 200) {
            renderReviewTasksTable(data.data || []);
        } else {
            $('#reviewTasksTableBody').html(`<tr><td colspan="6" class="loading-row">加载失败：${data.msg || '未知错误'}</td></tr>`);
        }
    } catch (error) {
        console.error('加载审核任务失败', error);
        $('#reviewTasksTableBody').html('<tr><td colspan="6" class="loading-row">网络错误，请稍后重试</td></tr>');
    }
}

// 渲染审核任务表格
function renderReviewTasksTable(tasks) {
    const tbody = $('#reviewTasksTableBody');
    if (tasks.length === 0) {
        tbody.html('<tr><td colspan="6" class="loading-row">暂无符合条件的任务</td></tr>');
        return;
    }

    let html = '';
    tasks.forEach(task => {
        const statusMap = {
            'PENDING_UPLOAD': { text: '待上传', class: 'status-pending' },
            'SUBMITTED': { text: '已提交', class: 'status-submitted' },
            'REVIEWING': { text: '审核中', class: 'status-reviewing' },
            'APPROVED': { text: '已通过', class: 'status-approved' },
            'NEED_REVISION': { text: '需修改', class: 'status-revision' }
        };
        const statusInfo = statusMap[task.status] || { text: task.status, class: '' };

        html += `
            <tr class="task-row" data-task-id="${task.id}">
                <td>${task.courseName}</td>
                <td>${task.semester}</td>
                <td>${task.teacherName || '未分配'}</td>
                <td>${formatDate(task.deadline)}</td>
                <td><span class="status-badge ${statusInfo.class}">${statusInfo.text}</span></td>
                <td><input type="checkbox" class="task-checkbox" value="${task.id}"></td>
            </tr>
        `;
    });
    tbody.html(html);
}

// 更新选中的任务
function updateSelectedTasks() {
    // 点击行选择时直接更新selectedTasks，不需要此函数
}

// 跳转到指定步骤
function goToStep(stepNumber) {
    // 更新步骤指示器
    $('.step').removeClass('active completed');
    $('.step').each(function () {
        const step = $(this).data('step');
        if (step < stepNumber) {
            $(this).addClass('completed');
        } else if (step === stepNumber) {
            $(this).addClass('active');
        }
    });

    // 显示对应的内容
    $('.step-content').hide();
    $(`.step-content[data-step="${stepNumber}"]`).show();

    // 特殊处理：步骤2时更新选中项目
    if (stepNumber === 2) {
        selectedReviewProjects = [];
        $('#reviewProjectsGroup input[type="checkbox"]:checked').each(function () {
            selectedReviewProjects.push($(this).val());
        });
    }
}

// 加载审核人列表
async function loadAssessors() {
    try {
        const res = await fetch(`${API_BASE_URL}/admin/assessors`);
        const data = await res.json();
        if (data.code === 200) {
            renderAssessorsList(data.data || []);
        } else {
            $('#availableAssessorsList').html('<div class="loading-row">加载失败</div>');
        }
    } catch (error) {
        console.error('加载审核人失败', error);
        $('#availableAssessorsList').html('<div class="loading-row">网络错误</div>');
    }
}

// 渲染审核人列表
function renderAssessorsList(assessors) {
    const container = $('#availableAssessorsList');
    if (assessors.length === 0) {
        container.html('<div class="loading-row">暂无审核人</div>');
        return;
    }

    let html = '';
    assessors.forEach(assessor => {
        html += `
            <div class="assessor-item" data-assessor-id="${assessor.id}">
                <input type="checkbox" class="assessor-checkbox" value="${assessor.id}">
                <div class="assessor-info">
                    <div class="assessor-name">${assessor.realName}</div>
                    <div class="assessor-work-id">工号：${assessor.workId}</div>
                </div>
            </div>
        `;
    });
    container.html(html);

    // 绑定复选框事件
    $('.assessor-checkbox').on('change', function () {
        updateAssessorSelectionUI();
    });
}

// 更新审核人选择UI
function updateAssessorSelectionUI() {
    selectedAssessors = [];
    $('#availableAssessorsList .assessor-checkbox:checked').each(function () {
        selectedAssessors.push(parseInt($(this).val()));
    });
}

// 添加到分配
function addToAssignment() {
    const selectedIds = [];
    $('#availableAssessorsList .assessor-checkbox:checked').each(function () {
        const id = parseInt($(this).val());
        const name = $(this).siblings('.assessor-info').find('.assessor-name').text();
        const workId = $(this).siblings('.assessor-info').find('.assessor-work-id').text();

        if (!assessorAssignments[id]) {
            assessorAssignments[id] = {
                name: name,
                workId: workId,
                assignedTasks: []
            };
        }

        // 默认分配所有选中的任务
        selectedTasks.forEach(taskId => {
            if (!assessorAssignments[id].assignedTasks.includes(taskId)) {
                assessorAssignments[id].assignedTasks.push(taskId);
            }
        });

        selectedIds.push(id);
        $(this).prop('checked', false);
    });

    if (selectedIds.length > 0) {
        renderAssignedAssessors();
        showToast(`已添加 ${selectedIds.length} 个审核人`, 'success');
    } else {
        showToast('请先选择审核人', 'warning');
    }
}

// 从分配中移除
function removeFromAssignment() {
    const selectedIds = [];
    $('.assigned-assessor-checkbox:checked').each(function () {
        const id = parseInt($(this).val());
        delete assessorAssignments[id];
        selectedIds.push(id);
    });

    if (selectedIds.length > 0) {
        renderAssignedAssessors();
        showToast(`已移除 ${selectedIds.length} 个审核人`, 'success');
    } else {
        showToast('请先选择要移除的审核人', 'warning');
    }
}

// 渲染已分配审核人列表
function renderAssignedAssessors() {
    const container = $('#assignedAssessorsList');
    const assessorIds = Object.keys(assessorAssignments);

    if (assessorIds.length === 0) {
        container.html('<div class="assignment-tip">请从左侧选择审核人并分配课程</div>');
        return;
    }

    // 获取任务信息
    const tasks = [];
    $('#reviewTasksTableBody tr.task-row').each(function () {
        const taskId = $(this).data('task-id');
        const courseName = $(this).find('td:eq(0)').text();
        const semester = $(this).find('td:eq(1)').text();
        tasks.push({ id: taskId, courseName, semester });
    });

    let html = '';
    assessorIds.forEach(id => {
        const assessor = assessorAssignments[id];

        html += `
            <div class="assigned-assessor" data-assessor-id="${id}">
                <div class="assessor-header">
                    <input type="checkbox" class="assigned-assessor-checkbox" value="${id}">
                    <div class="assessor-title">
                        <span class="name">${assessor.name}</span>
                        <span class="work-id">(${assessor.workId})</span>
                    </div>
                </div>
                <div class="task-assignment">
                    ${assessor.assignedTasks.length > 0 ? assessor.assignedTasks.map(taskId => {
            const task = tasks.find(t => t.id === taskId);
            if (task) {
                return `
                                <div class="task-item">
                                    <input type="checkbox" class="task-select-checkbox" value="${task.id}" checked>
                                    <span class="task-name">${task.courseName}</span>
                                    <span class="task-semester">(${task.semester})</span>
                                </div>
                            `;
            }
            return '';
        }).join('') : '<div class="no-tasks">未分配任务</div>'}
                </div>
            </div>
        `;
    });
    container.html(html);

    // 绑定任务选择事件
    $('.task-select-checkbox').on('change', function () {
        const taskId = parseInt($(this).val());
        const assessorId = $(this).closest('.assigned-assessor').data('assessor-id');
        const assignedTasks = assessorAssignments[assessorId].assignedTasks;

        if ($(this).prop('checked')) {
            if (!assignedTasks.includes(taskId)) {
                assignedTasks.push(taskId);
            }
        } else {
            assessorAssignments[assessorId].assignedTasks = assignedTasks.filter(id => id !== taskId);
        }
    });
}

// 显示分配摘要
function showAssignmentSummary() {
    // 获取任务信息
    const taskInfoMap = {};
    $('#reviewTasksTableBody tr.task-row').each(function () {
        const taskId = $(this).data('task-id');
        const courseName = $(this).find('td:eq(0)').text();
        const semester = $(this).find('td:eq(1)').text();
        taskInfoMap[taskId] = `${courseName} (${semester})`;
    });

    // 选中的任务
    const tasksHtml = selectedTasks.map(id => {
        const taskInfo = taskInfoMap[id] || `任务 ${id}`;
        return `<div class="summary-item">${taskInfo}</div>`;
    }).join('');
    $('#selectedTasksSummary').html(tasksHtml || '<div class="empty-message">无</div>');

    // 审核项目名称映射
    const projectNameMap = {
        '1': '1-教材封面及目录',
        '2': '2-课程大纲',
        '3': '3-电子教案',
        '4': '4-课程评分标准',
        '5': '5-课程目标达成度评价表',
        '6': '6-空白试卷',
        '7': '7-试卷参考答案及评分标准',
        '8': '8-15份学生试卷',
        '9': '9-成绩单(平时成绩、总评成绩)',
        '10': '10-成绩分析表',
        '11': '11-课程设计报告',
        '12': '12-作业'
    };

    // 选中的审核项目
    const projectsHtml = selectedReviewProjects.map(project => {
        const projectName = projectNameMap[project] || project;
        return `<div class="summary-item">${projectName}</div>`;
    }).join('');
    $('#selectedProjectsSummary').html(projectsHtml || '<div class="empty-message">无</div>');

    // 审核分配
    const assignmentHtml = Object.entries(assessorAssignments).map(([id, assessor]) => {
        const tasksInfo = assessor.assignedTasks.map(taskId => {
            return taskInfoMap[taskId] || `任务 ${taskId}`;
        }).join('、');

        return `
            <div class="summary-assessor">
                <div class="assessor-name">${assessor.name} (工号：${assessor.workId})</div>
                <div class="assigned-tasks">负责课程：${tasksInfo || '无'}</div>
            </div>
        `;
    }).join('');
    $('#assessorAssignmentSummary').html(assignmentHtml || '<div class="empty-message">无</div>');
}

// 确认分配
async function confirmAssignment() {
    // 从日期输入组件获取截止日期
    const deadline = $('#reviewDeadline').val();
    if (!deadline) {
        showToast('请选择审核截止日期', 'error');
        return;
    }

    const assignmentData = {
        taskIds: selectedTasks,
        reviewProjects: selectedReviewProjects,
        assessorAssignments: {},
        deadline: deadline
    };

    // 转换 assessorAssignments 格式
    Object.entries(assessorAssignments).forEach(([id, assessor]) => {
        assignmentData.assessorAssignments[parseInt(id)] = assessor.assignedTasks;
    });

    try {
        const res = await fetch(`${API_BASE_URL}/admin/review-assignments`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(assignmentData)
        });

        const data = await res.json();
        if (data.code === 200) {
            showToast('审核任务分配成功！', 'success');
            // 重置并返回第一步
            initReviewAssignView();
        } else {
            showToast('分配失败：' + (data.msg || '未知错误'), 'error');
        }
    } catch (error) {
        console.error('分配失败', error);
        showToast('网络错误，请稍后重试', 'error');
    }
}

// 格式化日期
function formatDate(dateStr) {
    if (!dateStr) return '-';
    const date = new Date(dateStr);
    return date.toLocaleDateString('zh-CN');
}

// 格式化日期为输入格式
function formatDateForInput(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
}

// 显示提示信息
function showToast(message, type = 'info') {
    const toast = $(`<div class="toast toast-${type}">${message}</div>`);
    $('body').append(toast);

    setTimeout(() => {
        toast.fadeOut(function () {
            $(this).remove();
        });
    }, 3000);
}


function loadGradeYears() {
    const currentYear = new Date().getFullYear();
    const years = [];
    for (let i = 0; i < 6; i++) {
        years.push(currentYear - i);
    }
    const gradeYear = $('#grade-year');
    gradeYear.empty().append('<option value="">请选择年级</option>');
    years.sort((a, b) => b - a).forEach(y => {
        gradeYear.append(`<option value="${y}">${y}</option>`);
    });
}

function loadSemesterOptions() {
    // 生成最近 4 个学年（8 个学期）的标准格式和显示格式
    const semesters = [];
    const now = new Date();
    const year = now.getFullYear();
    for (let offset = 0; offset < 4; offset++) {
        const start = year - offset - 1;
        const end = year - offset;
        // 第一学期
        semesters.push({
            value: `${start}-${end}-1`,
            text: `${start}-${end}学年第一学期`
        });
        // 第二学期
        semesters.push({
            value: `${start}-${end}-2`,
            text: `${start}-${end}学年第二学期`
        });
    }
    const semesterSelect = $('#semester-select');
    semesterSelect.empty().append('<option value="">请选择学期</option>');
    semesters.forEach(s => {
        semesterSelect.append(`<option value="${s.value}">${s.text}</option>`);
    });
}

function initSearchTabs() {
    $('.search-tabs .tab-btn').click(function () {
        const tab = $(this).data('tab');
        $('.search-tabs .tab-btn').removeClass('active');
        $(this).addClass('active');
        $('.search-panel').removeClass('active');
        $(`#${tab}-panel`).addClass('active');
        // 隐藏表格
        $('#course-table-container').hide();
        $('#generate-task-btn').prop('disabled', true);
    });

    $('#search-by-grade').click(async function () {
        const major = $('#grade-major').val();
        const grade = $('#grade-year').val();
        if (!major || !grade) {
            alert('请选择专业和年级');
            return;
        }
        await fetchCourses('grade', { major, grade });
    });

    $('#search-by-semester').click(async function () {
        const major = $('#semester-major').val();
        const semester = $('#semester-select').val();
        if (!major || !semester) {
            alert('请选择专业和学期');
            return;
        }
        await fetchCourses('semester', { major, semester });
    });
}

let allCourses = []; // 存储所有原始课程数据

async function fetchCourses(type, params) {
    currentSearchType = type;
    $('#course-table-body').html('<tr><td colspan="11" class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载中...</td></tr>');
    $('#course-table-container').show();
    let url;
    if (type === 'grade') {
        currentGrade = params.grade;        // 保存当前年级
        url = `${API_BASE_URL}/admin/courses-by-grade?major=${encodeURIComponent(params.major)}&grade=${params.grade}`;
    } else {
        currentGrade = '';                  // 学期查询时清空
        url = `${API_BASE_URL}/admin/courses-by-semester?major=${encodeURIComponent(params.major)}&semester=${encodeURIComponent(params.semester)}`;
    }
    try {
        const res = await fetch(url);
        const data = await res.json();
        if (data.code === 200) {
            allCourses = data.data || [];
            renderCourseTable(allCourses, currentGrade);  // 传递年级参数
        } else {
            $('#course-table-body').html(`<tr><td colspan="11" class="error-row">${data.message}</td></tr>`);
        }
    } catch (error) {
        console.error('查询课程失败', error);
        $('#course-table-body').html('<tr><td colspan="11" class="error-row">网络错误</td></tr>');
    }
}

// 绑定搜索事件（在 initSearchTabs 或页面加载后执行）
function initCourseSearch() {
    const searchInput = $('#course-search-input');
    const debouncedSearch = debounce(function () {
        const keyword = searchInput.val().toLowerCase().trim();
        if (!keyword) {
            // 没有关键词时，显示所有原始数据
            renderCourseTable(allCourses, currentGrade);
        } else {
            const filtered = allCourses.filter(c =>
                c.courseName && c.courseName.toLowerCase().includes(keyword)
            );
            renderCourseTable(filtered, currentGrade);
        }
    }, 300);
    searchInput.off('input').on('input', debouncedSearch);
}

let currentCourses = [];

function renderCourseTable(items, gradeParam = '') {
    currentItems = items;
    const tbody = $('#course-table-body');
    if (!items.length) {
        tbody.html('<tr><td colspan="11" class="loading-row">暂无课程</td></tr>');
        $('#generate-task-btn').prop('disabled', true);
        return;
    }

    // 动态修改筛选器标签文字
    const label = $('#gradeFilterLabel');
    if (currentSearchType === 'grade') {
        label.text('学期：');
    } else {
        label.text('年级：');
    }

    // 动态生成筛选器选项
    const gradeFilterSelect = $('#grade-filter');
    gradeFilterSelect.empty();
    gradeFilterSelect.prop('disabled', false); // 确保下拉框可用

    if (currentSearchType === 'grade') {
        // 按年级检索：生成学期下拉框
        // 使用传入的 gradeParam（即 currentGrade）
        const semesterOptions = generateSemesterOptions(gradeParam);
        semesterOptions.forEach(opt => {
            gradeFilterSelect.append(`<option value="${opt.value}">${opt.text}</option>`);
        });
        // 重新绑定事件（先移除旧的，再绑定新的）
        gradeFilterSelect.off('change').on('change', function () {
            const selectedSemester = $(this).val();
            $('#course-table-body tr').each(function () {
                const rowSemester = $(this).data('semester');
                if (!selectedSemester || rowSemester === selectedSemester) {
                    $(this).show();
                } else {
                    $(this).hide();
                }
            });
            $('#select-all').prop('checked', false);
            updateGenerateButtonState();
        });
    } else {
        // 按学期检索：生成年级下拉框
        const grades = getGradeList();
        gradeFilterSelect.append('<option value="">全部</option>');
        grades.forEach(g => {
            gradeFilterSelect.append(`<option value="${g}">${g}级</option>`);
        });
        gradeFilterSelect.off('change').on('change', function () {
            const selectedGrade = $(this).val();
            $('#course-table-body tr').each(function () {
                const rowGrade = $(this).data('grade');
                if (!selectedGrade || (rowGrade && rowGrade.toString() === selectedGrade)) {
                    $(this).show();
                } else {
                    $(this).hide();
                }
            });
            $('#select-all').prop('checked', false);
            updateGenerateButtonState();
        });
    }

    // 生成表格行，并存储筛选所需的数据属性
    let html = '';
    items.forEach(item => {
        const filedClass = item.isFiled ? 'filed' : 'unfiled';
        const filedText = item.isFiled ? '已备案' : '未备案';
        const disabled = (!item.canFile || item.isFiled) ? 'disabled' : '';
        const title = !item.canFile ? 'title="该课程尚未开课，不可备案"' : '';

        // 提取年级（用于年级筛选）
        let grade = '';
        if (item.preferred) {
            const match = item.preferred.match(/\d{4}/);
            grade = match ? match[0] : '';
        }
        // 学期（用于学期筛选）
        const semester = item.semester || '';

        html += `
            <tr data-id="${item.id}" data-type="${item.type}" data-grade="${grade}" data-semester="${semester}" ${title}>
                <td><input type="checkbox" class="course-check" ${disabled}></td>
                <td>${item.courseCode || '-'}</td>
                <td>${item.selectCode || '-'}</td>
                <td>${item.courseName}</td>
                <td>${item.credits}</td>
                <td>${item.semester || '-'}</td>
                <td>${item.preferred || '-'}</td>
                <td>${item.teachingClass || '-'}</td>
                <td>${item.teacherName}</td>
                <td>${item.enrollCount}</td>
                <td><span class="status-badge ${filedClass}">${filedText}</span></td>
            </tr>
        `;
    });
    tbody.html(html);

    // 绑定复选框事件
    $('.course-check').off('change').on('change', function () {
        updateGenerateButtonState();
    });

    // 全选
    $('#select-all').off('change').on('change', function () {
        const checked = $(this).prop('checked');
        $('#course-table-body tr:visible').find('.course-check:not(:disabled)').prop('checked', checked);
        updateGenerateButtonState();
    });

    $('#generate-task-btn').prop('disabled', false);

    // 绑定开课状态筛选事件
    $('#course-status-filter').off('change').on('change', function () {
        filterCourseTable();
    });

    // 绑定备案状态筛选事件
    $('#filed-status-filter').off('change').on('change', function () {
        filterCourseTable();
    });

    // 应用默认筛选（开课课程 + 未备案）
    filterCourseTable();
}

function filterCourseTable() {
    const courseStatus = $('#course-status-filter').val();
    const filedStatus = $('#filed-status-filter').val();

    $('#course-table-body tr').each(function () {
        const row = $(this);
        const teacherName = row.find('td:nth-child(9)').text().trim();
        const isOpened = teacherName !== '未开课' && teacherName !== '';
        const isFiled = row.find('.status-badge').hasClass('filed');

        let showRow = true;

        // 开课状态筛选
        if (courseStatus === 'opened' && !isOpened) {
            showRow = false;
        } else if (courseStatus === 'unopened' && isOpened) {
            showRow = false;
        }

        // 备案状态筛选
        if (filedStatus === 'filed' && !isFiled) {
            showRow = false;
        } else if (filedStatus === 'unfiled' && isFiled) {
            showRow = false;
        }

        row.toggle(showRow);
    });

    $('#select-all').prop('checked', false);
    updateGenerateButtonState();
}

// 根据年级生成8个学期的选项列表
function generateSemesterOptions(grade) {
    if (!grade) return [];
    const startYear = parseInt(grade);
    const semesters = [];
    for (let i = 1; i <= 8; i++) {
        const yearOffset = Math.floor((i - 1) / 2);
        const academicYearStart = startYear + yearOffset;
        const academicYearEnd = academicYearStart + 1;
        const flag = (i % 2 === 1) ? '1' : '2';
        const semesterValue = `${academicYearStart}-${academicYearEnd}-${flag}`;
        let displayText = '';
        if (i === 1) displayText = '第一学期';
        else if (i === 2) displayText = '第二学期';
        else if (i === 3) displayText = '第三学期';
        else if (i === 4) displayText = '第四学期';
        else if (i === 5) displayText = '第五学期';
        else if (i === 6) displayText = '第六学期';
        else if (i === 7) displayText = '第七学期';
        else if (i === 8) displayText = '第八学期';
        semesters.push({
            value: semesterValue,
            text: `${displayText} (${semesterValue})`
        });
    }
    semesters.unshift({ value: '', text: '全部学期' });
    return semesters;
}

// 生成年级列表（复用原有逻辑，从当前年份取最近5个年级）
function getGradeList() {
    const currentDate = new Date();
    const currentYear = currentDate.getFullYear();
    const currentMonth = currentDate.getMonth() + 1;
    const grades = [];
    if (currentMonth >= 9) {
        for (let i = 0; i < 5; i++) {
            grades.push(currentYear - i);
        }
    } else {
        for (let i = 1; i <= 5; i++) {
            grades.push(currentYear - i);
        }
    }
    return grades;
}

function updateGenerateButtonState() {
    const anyChecked = $('.course-check:checked').length > 0;
    $('#generate-task-btn').prop('disabled', !anyChecked);
}

function initTaskConfigModal() {
    // 关闭弹窗
    $('#closeTaskConfigModal, #cancelTaskConfig').off('click').on('click', function () {
        $('#taskConfigModal').removeClass('show');
    });

    // 生成任务按钮点击
    $('#generate-task-btn').off('click').on('click', async function () {
        const selectedItems = [];
        $('.course-check:checked').each(function () {
            const tr = $(this).closest('tr');
            const id = tr.data('id');
            const type = tr.data('type');
            selectedItems.push({ id, type });
        });

        if (selectedItems.length === 0) {
            alert('请至少选择一门课程');
            return;
        }

        const hasFutureCourse = selectedItems.some(item => item.type === 'course');
        if (hasFutureCourse) {
            $('#futureCourseTeacherGroup').show();
        } else {
            $('#futureCourseTeacherGroup').hide();
        }

        // 加载审核员列表
        await loadAssessorsForModal();

        // 生成备案项目 HTML
        let taskItemsHtml;
        if (hasFutureCourse) {
            // 未来课程：只允许选大纲（2），其他禁用
            taskItemsHtml = `
                <div style="margin-bottom: 8px; color: #ff9800;">
                    <i class="fas fa-info-circle"></i> 未来课程只能备案“课程大纲”
                </div>
                <label><input type="checkbox" value="2" checked disabled> 2-课程大纲</label><br>
                <label><input type="checkbox" value="1" disabled> 1-教材封面及目录</label><br>
                <label><input type="checkbox" value="3" disabled> 3-电子教案</label><br>
                <label><input type="checkbox" value="4" disabled> 4-课程评分标准</label><br>
                <label><input type="checkbox" value="5" disabled> 5-课程目标达成度评价表</label><br>
                <label><input type="checkbox" value="6" disabled> 6-空白试卷</label><br>
                <label><input type="checkbox" value="7" disabled> 7-试卷参考答案及评分标准</label><br>
                <label><input type="checkbox" value="8" disabled> 8-15份学生试卷</label><br>
                <label><input type="checkbox" value="9" disabled> 9-成绩单(平时成绩、总评成绩)</label><br>
                <label><input type="checkbox" value="10" disabled> 10-成绩分析表</label><br>
                <label><input type="checkbox" value="11" disabled> 11-课程设计报告</label><br>
                <label><input type="checkbox" value="12" disabled> 12-作业</label><br>
            `;
        } else {
            // 正常课程：全部勾选
            taskItemsHtml = `
                    <div style="margin-bottom: 8px;">
                        <label><input type="checkbox" id="select-all-items"> 全选</label>
                    </div>
                    <label><input type="checkbox" value="1" checked> 1-教材封面及目录</label><br>
                    <label><input type="checkbox" value="2" checked> 2-课程大纲</label><br>
                    <label><input type="checkbox" value="3" checked> 3-电子教案</label><br>
                    <label><input type="checkbox" value="4" checked> 4-课程评分标准</label><br>
                    <label><input type="checkbox" value="5" checked> 5-课程目标达成度评价表</label><br>
                    <label><input type="checkbox" value="6" checked> 6-空白试卷</label><br>
                    <label><input type="checkbox" value="7" checked> 7-试卷参考答案及评分标准</label><br>
                    <label><input type="checkbox" value="8" checked> 8-15份学生试卷</label><br>
                    <label><input type="checkbox" value="9" checked> 9-成绩单(平时成绩、总评成绩)</label><br>
                    <label><input type="checkbox" value="10" checked> 10-成绩分析表</label><br>
                    <label><input type="checkbox" value="11" checked> 11-课程设计报告</label><br>
                    <label><input type="checkbox" value="12" checked> 12-作业</label><br>
                `;
        }
        $('#taskItems').html(taskItemsHtml);

        // 全选逻辑（仅在无未来课程时有效）
        if (!hasFutureCourse) {
            $('#select-all-items').off('change').on('change', function () {
                const checked = $(this).prop('checked');
                $('#taskItems input[type=checkbox]:not(#select-all-items)').prop('checked', checked);
            });
        }

        // 存储选中项并显示弹窗
        $('#taskConfigModal').data('selectedItems', selectedItems);
        console.log('弹窗元素是否存在:', $('#taskConfigModal').length);
        $('#taskConfigModal').addClass('show');
    });

    // 确认生成
    $('#confirmTaskConfig').off('click').on('click', async function() {
        const taskName = $('#taskName').val().trim();
        const startTime = $('#taskStartTime').val();
        const endTime = $('#taskEndTime').val();
        let assessorId = $('#taskAssessor').val();
        if (assessorId === "") assessorId = null;
        const futureCourseTeacherInput = $('#futureCourseTeacher').val().trim();
        const selectedItems = $('#taskConfigModal').data('selectedItems');

        if (!taskName || !startTime || !endTime) {
            alert('请填写完整信息');
            return;
        }

        // 收集备案项目
        const items = [];
        $('#taskItems input[type=checkbox]:checked').each(function () {
            items.push($(this).val());
        });

        const teacherIds = selectedItems.filter(item => item.type === 'teacher').map(item => item.id);
        const courseIds = selectedItems.filter(item => item.type === 'course').map(item => item.id);

        // 若包含未来课程（courseIds.length > 0），则教师信息为必填
        if (courseIds.length > 0) {
            if (!futureCourseTeacherInput) {
                alert('请为未来课程指定教师姓名或工号');
                return;
            }
            // 如果用户从下拉列表选择了已有教师，优先使用其工号，否则使用输入的文本
            let finalTeacher = futureCourseTeacherInput;
            const selectedWorkId = $('#futureCourseTeacher').data('selected-workid');
            if (selectedWorkId) {
                finalTeacher = selectedWorkId; // 后端可以同时处理工号或姓名
            }
            // 这里我们可以直接将教师标识传递给后端
            // 注意：后端 generateTasks 中 futureCourseTeacher 参数会调用 getOrCreateTeacherUser
            // 它会先按工号查找，再按姓名查找，所以传工号或姓名均可
            // 但为了一致性，我们传递用户实际输入的值
        }

        const payload = {
            taskName,
            startTime,
            endTime,
            assessorId: assessorId ? parseInt(assessorId) : null,
            teacherIds,
            courseIds,
            items,
            futureCourseTeacher: courseIds.length > 0 ? futureCourseTeacherInput : null
        };

        console.log('发送生成任务请求:', payload);

        try {
            const res = await fetch(`${API_BASE_URL}/admin/generate-tasks`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(payload)
            });
            const data = await res.json();
            if (data.code === 200) {
                const urls = data.data;
                const createdCourseIds = data.data.createdCourseIds || [];
                alert(`任务创建成功！\n新教师账号清单：${urls.newTeachers}\n已有教师账号清单：${urls.existingTeachers}`);
                $('#taskConfigModal').removeClass('show');

                // 将已创建任务的课程标记为已备案并禁用复选框
                createdCourseIds.forEach(courseId => {
                    const tr = $(`#course-table-body tr[data-id="${courseId}"]`);
                    if (tr.length) {
                        const checkbox = tr.find('.course-check');
                        const statusSpan = tr.find('.status-badge');
                        checkbox.prop('disabled', true);
                        checkbox.prop('checked', false);
                        statusSpan.removeClass('unfiled').addClass('filed').text('已备案');
                        // 更新 allCourses 中的状态
                        const index = allCourses.findIndex(c => c.id == courseId);
                        if (index !== -1) {
                            allCourses[index].isFiled = true;
                        }
                    }
                });
                // 清空全选状态
                $('#select-all').prop('checked', false);
                updateGenerateButtonState();
            } else {
                alert('生成失败：' + data.message);
            }
        } catch (error) {
            console.error('生成任务失败', error);
            alert('网络错误，请稍后重试');
        }
    });
}

async function loadAssessorsForModal() {
    try {
        const res = await fetch(`${API_BASE_URL}/admin/assessors`);
        const data = await res.json();
        if (data.code === 200) {
            const assessors = data.data || [];
            const select = $('#taskAssessor');
            select.empty().append('<option value="">请选择审核员</option>');
            assessors.forEach(a => {
                select.append(`<option value="${a.id}">${a.realName}</option>`);
            });
        } else {
            alert('加载审核员失败：' + data.message);
        }
    } catch (error) {
        console.error('加载审核员失败', error);
        alert('加载审核员网络错误');
    }
}

// ==================== 已备案档案库功能 ====================

let selectedArchiveTasks = [];

// 在页面初始化时加载档案库数据
function initArchiveView() {
    loadArchiveFilterOptions();
    loadArchiveList();
    initArchiveEventListeners();
}

// 加载筛选选项
async function loadArchiveFilterOptions() {
    try {
        // 加载学期选项
        const semesterRes = await fetch(`${API_BASE_URL}/admin/semesters`);
        const semesterData = await semesterRes.json();
        if (semesterData.code === 200) {
            const semesterSelect = $('#archiveSemesterFilter');
            semesterSelect.empty().append('<option value="">全部</option>');
            semesterData.data.forEach(semester => {
                semesterSelect.append(`<option value="${semester}">${semester}</option>`);
            });
        }

        // 加载专业选项
        const majorRes = await fetch(`${API_BASE_URL}/admin/majors`);
        const majorData = await majorRes.json();
        if (majorData.code === 200) {
            const majorSelect = $('#archiveMajorFilter');
            majorSelect.empty().append('<option value="">全部</option>');
            majorData.data.forEach(major => {
                majorSelect.append(`<option value="${major}">${major}</option>`);
            });
        }

        // 加载课程名称选项
        const coursesRes = await fetch(`${API_BASE_URL}/admin/courses`);
        const coursesData = await coursesRes.json();
        if (coursesData.code === 200) {
            const courses = coursesData.data.list || [];
            const courseNames = [...new Set(courses.map(c => c.courseName))].filter(name => name);
            const courseSelect = $('#archiveCourseNameFilter');
            courseSelect.empty().append('<option value="">全部</option>');
            courseNames.forEach(name => {
                courseSelect.append(`<option value="${name}">${name}</option>`);
            });
        }

        // 加载教师选项
        const teachersRes = await fetch(`${API_BASE_URL}/admin/teachers`);
        const teachersData = await teachersRes.json();
        if (teachersData.code === 200) {
            const teachers = teachersData.data || [];
            const teacherSelect = $('#archiveTeacherFilter');
            teacherSelect.empty().append('<option value="">全部</option>');
            teachers.forEach(teacher => {
                teacherSelect.append(`<option value="${teacher.realName}">${teacher.realName}</option>`);
            });
        }
    } catch (error) {
        console.error('加载筛选选项失败:', error);
    }
}

// 加载档案列表
async function loadArchiveList() {
    // 优先使用手动输入的值，如果没有则使用下拉框的值
    const semester = $('#archiveSemesterFilterInput').val().trim() || $('#archiveSemesterFilter').val();
    const major = $('#archiveMajorFilterInput').val().trim() || $('#archiveMajorFilter').val();
    const courseName = $('#archiveCourseNameFilterInput').val().trim() || $('#archiveCourseNameFilter').val();
    const teacherName = $('#archiveTeacherFilterInput').val().trim() || $('#archiveTeacherFilter').val();
    const status = $('#archiveStatusFilter').val();

    $('#archiveTableBody').html('<tr><td colspan="10" class="loading-row"><i class="fas fa-spinner fa-spin"></i> 加载中...</td></tr>');

    try {
        let url = `${API_BASE_URL}/admin/archive`;
        const params = new URLSearchParams();
        if (semester) params.append('semester', semester);
        if (major) params.append('major', major);
        if (courseName) params.append('courseName', courseName);
        if (teacherName) params.append('teacherName', teacherName);
        if (status) params.append('status', status);
        if (params.toString()) url += '?' + params.toString();

        const response = await fetch(url);
        const data = await response.json();

        if (data.code === 200) {
            renderArchiveList(data.data || []);
        } else {
            $('#archiveTableBody').html(`<tr><td colspan="10" class="loading-row">加载失败: ${data.message}</td></tr>`);
        }
    } catch (error) {
        console.error('加载档案列表失败:', error);
        $('#archiveTableBody').html('<tr><td colspan="10" class="loading-row">网络错误，请稍后重试</td></tr>');
    }
}

// 渲染档案列表
function renderArchiveList(archives) {
    const tbody = $('#archiveTableBody');

    if (archives.length === 0) {
        tbody.html('<tr><td colspan="10" class="loading-row"><i class="fas fa-inbox"></i> 暂无档案</td></tr>');
        return;
    }

    let html = '';
    archives.forEach(archive => {
        const statusClass = getStatusClass(archive.status);
        html += `
            <tr data-task-id="${archive.id}">
                <td><input type="checkbox" class="archive-checkbox" value="${archive.id}"></td>
                <td>${archive.courseName || '-'}</td>
                <td>${archive.teachingClass || '-'}</td>
                <td>${archive.teacherName || '-'}</td>
                <td>${archive.semester || '-'}</td>
                <td>${archive.major || '-'}</td>
                <td>${archive.grade || '-'}</td>
                <td><span class="status-badge ${getStatusClass(archive.status)}">${getStatusDesc(archive.status)}</span></td>
                <td>${archive.fileCount || 0}</td>
                <td>${formatDateTime(archive.submitTime)}</td>
            </tr>
        `;
    });
    tbody.html(html);

    // 更新批量导出按钮状态
    updateBatchExportButton();
}

// 初始化档案库事件监听
function initArchiveEventListeners() {
    // 搜索按钮
    $('#searchArchiveBtn').off('click').on('click', function () {
        loadArchiveList();
    });

    // 重置按钮
    $('#resetArchiveBtn').off('click').on('click', function () {
        $('#archiveSemesterFilter').val('');
        $('#archiveSemesterFilterInput').val('');
        $('#archiveMajorFilter').val('');
        $('#archiveMajorFilterInput').val('');
        $('#archiveCourseNameFilter').val('');
        $('#archiveCourseNameFilterInput').val('');
        $('#archiveTeacherFilter').val('');
        $('#archiveTeacherFilterInput').val('');
        $('#archiveStatusFilter').val('');
        loadArchiveList();
    });

    // 全选复选框
    $('#archiveSelectAll').off('change').on('change', function () {
        const checked = $(this).prop('checked');
        $('.archive-checkbox').prop('checked', checked);
        updateBatchExportButton();
    });

    // 单个复选框
    $(document).off('change', '.archive-checkbox').on('change', '.archive-checkbox', function () {
        updateBatchExportButton();
    });

    // 批量导出按钮
    $('#batchExportArchiveBtn').off('click').on('click', batchExportArchive);
}

// 更新批量导出按钮状态
function updateBatchExportButton() {
    const checkedCount = $('.archive-checkbox:checked').length;
    $('#batchExportArchiveBtn').prop('disabled', checkedCount === 0);
}

// 批量导出档案
async function batchExportArchive() {
    const selectedTaskIds = [];
    $('.archive-checkbox:checked').each(function () {
        selectedTaskIds.push(parseInt($(this).val()));
    });

    if (selectedTaskIds.length === 0) {
        alert('请至少选择一个档案');
        return;
    }

    const exportBtn = $('#batchExportArchiveBtn');
    exportBtn.prop('disabled', true).html('<i class="fas fa-spinner fa-spin"></i> 导出中...');

    try {
        const response = await fetch(`${API_BASE_URL}/admin/archive/export`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(selectedTaskIds)
        });

        const data = await response.json();

        if (data.code === 200) {
            // 下载ZIP文件
            const downloadUrl = `${API_BASE_URL}/admin/download/${encodeURIComponent(data.data)}`;
            console.log('下载URL:', downloadUrl);

            const a = document.createElement('a');
            a.href = downloadUrl;
            a.download = data.data;
            document.body.appendChild(a);
            a.click();
            document.body.removeChild(a);

            alert(`成功导出 ${selectedTaskIds.length} 个档案`);
        } else {
            alert('导出失败: ' + data.message);
        }
    } catch (error) {
        console.error('导出档案失败:', error);
        alert('网络错误，请稍后重试');
    } finally {
        exportBtn.prop('disabled', false).html('<i class="fas fa-download"></i> 批量导出');
    }
}

// 在菜单点击时，切换到档案库视图
$(document).on('click', '.menu-item[data-view="archive"]', function () {
    initArchiveView();
});

// 添加转换函数
function formatMaterialRequirements(materialReq) {
    if (!materialReq) return '无';
    // 如果包含中文描述则直接返回（兼容已存储为文字的情况）
    if (/[教材|大纲|教案|评分|达成|试卷|成绩|设计|作业]/.test(materialReq)) return materialReq;
    const items = materialReq.split(',').map(s => s.trim());
    const descs = items.map(item => {
        // 尝试提取数字编号，忽略可能的前缀（如“1-教材封面及目录”中的数字部分）
        const match = item.match(/(\d+)/);
        if (match) {
            const num = match[1];
            return ITEM_DESC_MAP[num] ? `${num}-${ITEM_DESC_MAP[num]}` : item;
        }
        return item;
    });
    return descs.join('；');
}

// 检索教师（供未来课程教师输入框调用）
async function searchTeachers(keyword) {
    if (!keyword || keyword.length < 2) {
        $('#teacherSuggestions').hide().empty();
        return;
    }
    try {
        const response = await fetch(`${API_BASE_URL}/admin/teachers?name=${encodeURIComponent(keyword)}`);
        const data = await response.json();
        if (data.code === 200 && data.data && data.data.length > 0) {
            renderTeacherSuggestions(data.data);
        } else {
            // 无匹配结果时显示提示
            $('#teacherSuggestions').html('<div class="suggestion-item" style="color:#999;">未找到匹配教师，将自动创建新账号</div>').show();
        }
    } catch (error) {
        console.error('检索教师失败:', error);
    }
}

// 渲染教师建议列表
function renderTeacherSuggestions(teachers) {
    const $dropdown = $('#teacherSuggestions');
    let html = '';
    teachers.forEach(t => {
        html += `
            <div class="suggestion-item" data-id="${t.id}" data-name="${t.realName}" data-workid="${t.workId}">
                <span class="suggestion-name">${t.realName}</span>
                <span class="suggestion-workid">工号：${t.workId}</span>
                <div class="suggestion-dept">${t.department || ''}</div>
            </div>
        `;
    });
    $dropdown.html(html).show();

    // 绑定点击选择事件
    $dropdown.find('.suggestion-item').on('click', function() {
        const name = $(this).data('name');
        const workId = $(this).data('workid');
        $('#futureCourseTeacher').val(name).data('selected-workid', workId);
        $dropdown.hide();
    });
}







