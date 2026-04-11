// 登录/注册页面交互逻辑

// API基础URL（根据实际后端地址修改）
const API_BASE_URL = 'http://localhost:8080/api';

console.log('========== auth.js 文件已加载 ==========');
console.log('API_BASE_URL:', API_BASE_URL);

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', function () {
    console.log('========== auth.js 已加载 ==========');
    console.log('DOM内容加载完成');
    initAuthPage();
});

// 初始化页面
function initAuthPage() {
    console.log('========== initAuthPage 被调用 ==========');

    // 标签切换
    const tabButtons = document.querySelectorAll('.tab-btn');
    const forms = document.querySelectorAll('.auth-form');

    console.log('找到的标签按钮数量:', tabButtons.length);
    console.log('找到的表单数量:', forms.length);

    tabButtons.forEach(btn => {
        btn.addEventListener('click', function () {
            const targetTab = this.getAttribute('data-tab');

            // 切换标签状态
            tabButtons.forEach(b => b.classList.remove('active'));
            this.classList.add('active');

            // 切换表单显示
            forms.forEach(form => {
                form.classList.remove('active');
                if (form.id === `${targetTab}-form`) {
                    form.classList.add('active');
                }
            });

            // 清除所有错误信息
            clearAllErrors();
            clearMessages();
        });
    });

    // 密码显示/隐藏切换
    initPasswordToggle();

    // 表单提交
    initFormSubmit();
    console.log('========== initAuthPage 完成 ==========');
}

// 初始化密码显示/隐藏功能
function initPasswordToggle() {
    const toggleButtons = document.querySelectorAll('.toggle-password');

    toggleButtons.forEach(btn => {
        btn.addEventListener('click', function () {
            const input = this.previousElementSibling;
            const icon = this.querySelector('i');

            if (input.type === 'password') {
                input.type = 'text';
                icon.classList.remove('fa-eye');
                icon.classList.add('fa-eye-slash');
            } else {
                input.type = 'password';
                icon.classList.remove('fa-eye-slash');
                icon.classList.add('fa-eye');
            }
        });
    });
}

// 初始化表单提交
function initFormSubmit() {
    // 登录表单
    const loginForm = document.getElementById('loginForm');
    if (loginForm) {
        loginForm.addEventListener('submit', handleLogin);
    }

    // 注册表单
    const registerForm = document.getElementById('registerForm');
    if (registerForm) {
        registerForm.addEventListener('submit', handleRegister);
    }
}

// 处理登录
async function handleLogin(e) {
    console.log('========== handleLogin 被调用 ==========');
    console.log('事件对象:', e);
    e.preventDefault();
    console.log('已阻止默认行为');
    clearAllErrors();
    clearMessages();

    const formData = {
        username: document.getElementById('loginUsername').value.trim(),
        password: document.getElementById('loginPassword').value,
        rememberMe: document.getElementById('rememberMe').checked
    };

    // 前端验证
    if (!validateLoginForm(formData)) {
        return;
    }

    // 显示加载状态
    const submitBtn = loginForm.querySelector('.submit-btn');
    const originalText = submitBtn.innerHTML;
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 登录中...';

    console.log('========== 开始登录 ==========');
    console.log('登录表单数据:', formData);
    console.log('API地址:', `${API_BASE_URL}/auth/login`);
    console.log('============================');

    try {
        const response = await fetch(`${API_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });

        console.log('========== 收到响应 ==========');
        console.log('响应状态码:', response.status);
        console.log('响应ok:', response.ok);

        const data = await response.json();
        console.log('登录响应完整数据:', data);
        console.log('响应code:', data.code);
        console.log('响应data:', data.data);

        if (response.ok && data.code === 200) {
            showMessage('loginMessage', '登录成功！正在跳转...', 'success');

            const userInfo = data.data.userInfo;

            let redirectUrl = '';
            const role = (userInfo.role || '').toUpperCase();
            if (role === 'ADMIN') {
                redirectUrl = 'admin.html';
            } else if (role === 'TEACHER') {
                redirectUrl = 'teacher.html';
            } else if (role === 'ASSESSOR') {
                redirectUrl = 'assessor.html';
            } else {
                redirectUrl = 'index.html'; // 其他角色（如普通用户）跳转到首页
            }

            console.log('用户角色:', role);
            console.log('跳转路径:', redirectUrl);

            if (formData.rememberMe) {
                localStorage.setItem('token', data.data.token);
                localStorage.setItem('userInfo', JSON.stringify(userInfo));
                console.log('已保存到localStorage');
            } else {
                sessionStorage.setItem('token', data.data.token);
                sessionStorage.setItem('userInfo', JSON.stringify(userInfo));
                console.log('已保存到sessionStorage');
            }

            setTimeout(() => {
                console.log('跳转到:', redirectUrl);
                window.location.href = redirectUrl;
            }, 1500);
        } else {
            // 登录失败
            showMessage('loginMessage', data.message || '登录失败，请检查用户名和密码', 'error');
            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
        }
    } catch (error) {
        console.error('登录错误:', error);
        showMessage('loginMessage', '网络错误，请稍后重试', 'error');
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalText;
    }
}

// 处理注册
async function handleRegister(e) {
    e.preventDefault();
    clearAllErrors();
    clearMessages();

    const formData = {
        username: document.getElementById('registerUsername').value.trim(),
        workId: document.getElementById('registerWorkId').value.trim(),
        realName: document.getElementById('registerRealName').value.trim(),
        email: document.getElementById('registerEmail').value.trim(),
        phone: document.getElementById('registerPhone').value.trim(),
        password: document.getElementById('registerPassword').value,
        confirmPassword: document.getElementById('registerConfirmPassword').value,
        role: document.getElementById('registerRole').value    // 新增角色字段
    };

    // 前端验证
    if (!validateRegisterForm(formData)) {
        return;
    }

    // 检查是否同意协议
    if (!document.getElementById('agreeTerms').checked) {
        showError('agreeTermsError', '请先阅读并同意用户协议和隐私政策');
        return;
    }

    // 显示加载状态
    const submitBtn = registerForm.querySelector('.submit-btn');
    const originalText = submitBtn.innerHTML;
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<i class="fas fa-spinner fa-spin"></i> 注册中...';

    try {
        const response = await fetch(`${API_BASE_URL}/auth/register`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(formData)
        });

        const data = await response.json();

        if (response.ok && data.code === 200) {
            // 注册成功
            showMessage('registerMessage', '注册成功！正在跳转到登录页面...', 'success');

            // 延迟跳转到登录标签
            setTimeout(() => {
                document.querySelector('.tab-btn[data-tab="login"]').click();
                registerForm.reset();
            }, 2000);
        } else {
            // 注册失败
            showMessage('registerMessage', data.message || '注册失败，请检查输入信息', 'error');

            // 显示具体错误信息
            if (data.data && data.data.errors) {
                Object.keys(data.data.errors).forEach(field => {
                    showError(`register${field.charAt(0).toUpperCase() + field.slice(1)}Error`,
                        data.data.errors[field]);
                });
            }

            submitBtn.disabled = false;
            submitBtn.innerHTML = originalText;
        }
    } catch (error) {
        console.error('注册错误:', error);
        showMessage('registerMessage', '网络错误，请稍后重试', 'error');
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalText;
    }
}

// 验证登录表单
function validateLoginForm(data) {
    let isValid = true;

    if (!data.username) {
        showError('loginUsernameError', '请输入用户名或工号');
        isValid = false;
    }

    if (!data.password) {
        showError('loginPasswordError', '请输入密码');
        isValid = false;
    } else if (data.password.length < 6) {
        showError('loginPasswordError', '密码长度至少6位');
        isValid = false;
    }

    return isValid;
}

// 验证注册表单
function validateRegisterForm(data) {
    let isValid = true;

    // 验证用户名
    if (!data.username) {
        showError('registerUsernameError', '请输入用户名');
        isValid = false;
    } else if (data.username.length < 3 || data.username.length > 20) {
        showError('registerUsernameError', '用户名长度为3-20个字符');
        isValid = false;
    }

    // 验证工号
    if (!data.workId) {
        showError('registerWorkIdError', '请输入工号');
        isValid = false;
    }

    // 验证真实姓名
    if (!data.realName) {
        showError('registerRealNameError', '请输入真实姓名');
        isValid = false;
    }

    // 验证邮箱
    if (!data.email) {
        showError('registerEmailError', '请输入邮箱');
        isValid = false;
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(data.email)) {
        showError('registerEmailError', '请输入有效的邮箱地址');
        isValid = false;
    }

    // 验证手机号
    if (!data.phone) {
        showError('registerPhoneError', '请输入手机号');
        isValid = false;
    } else if (!/^1[3-9]\d{9}$/.test(data.phone)) {
        showError('registerPhoneError', '请输入有效的手机号');
        isValid = false;
    }

    // 验证密码
    if (!data.password) {
        showError('registerPasswordError', '请输入密码');
        isValid = false;
    } else if (data.password.length < 6) {
        showError('registerPasswordError', '密码长度至少6位');
        isValid = false;
    }

    // 验证确认密码
    if (!data.confirmPassword) {
        showError('registerConfirmPasswordError', '请再次输入密码');
        isValid = false;
    } else if (data.password !== data.confirmPassword) {
        showError('registerConfirmPasswordError', '两次输入的密码不一致');
        isValid = false;
    }

    // 验证角色
    if (!data.role) {
        showError('registerRoleError', '请选择注册身份');
        isValid = false;
    }

    return isValid;
}

// 显示错误信息
function showError(elementId, message) {
    const errorElement = document.getElementById(elementId);
    if (errorElement) {
        errorElement.textContent = message;
        const input = errorElement.previousElementSibling;
        if (input && input.tagName === 'INPUT') {
            input.classList.add('error');
        } else if (input && input.classList.contains('password-input')) {
            input.querySelector('input').classList.add('error');
        }
    }
}

// 清除所有错误信息
function clearAllErrors() {
    document.querySelectorAll('.error-msg').forEach(el => {
        el.textContent = '';
    });
    document.querySelectorAll('input.error').forEach(el => {
        el.classList.remove('error');
    });
}

// 显示消息
function showMessage(elementId, message, type) {
    const messageElement = document.getElementById(elementId);
    if (messageElement) {
        messageElement.textContent = message;
        messageElement.className = `form-message show ${type}`;

        // 3秒后自动隐藏
        setTimeout(() => {
            messageElement.classList.remove('show');
        }, 3000);
    }
}

// 清除消息
function clearMessages() {
    document.querySelectorAll('.form-message').forEach(el => {
        el.classList.remove('show');
    });
}

