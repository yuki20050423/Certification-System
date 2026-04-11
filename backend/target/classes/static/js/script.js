// 用户认证相关函数
function checkLoginStatus() {
    // 检查localStorage和sessionStorage中是否有token
    const token = localStorage.getItem('token') || sessionStorage.getItem('token');
    const userInfoStr = localStorage.getItem('userInfo') || sessionStorage.getItem('userInfo');

    const loginBtnItem = document.getElementById('loginBtnItem');
    const userMenuItem = document.getElementById('userMenuItem');

    if (token && userInfoStr) {
        // 已登录，显示用户菜单
        try {
            const userInfo = JSON.parse(userInfoStr);
            if (loginBtnItem) loginBtnItem.style.display = 'none';
            if (userMenuItem) {
                userMenuItem.style.display = 'block';
                // 更新用户信息显示
            const userNameEl = document.getElementById('userName');
            const userRoleEl = document.getElementById('userRole');
            const adminLink = document.getElementById('adminLink');
            const teacherLink = document.getElementById('teacherLink');
            const assessorLink = document.getElementById('assessorLink');
            if (userNameEl) {
                userNameEl.textContent = userInfo.realName || userInfo.username || '用户';
            }
//                if (userRoleEl) {
//                    const roleText = userInfo.role === 'ADMIN' ? '管理员' : '普通用户';
//                    userRoleEl.textContent = roleText;
//                }
                if (userRoleEl) {
                    // 新增：教师角色文本显示
                    let roleText = '';
                    if (userInfo.role === 'ADMIN') {
                        roleText = '管理员';
                    } else if (userInfo.role === 'TEACHER') { // 新增：识别教师角色
                        roleText = '普通教师';
                    } else if (userInfo.role === 'ASSESSOR') {
                        roleText = '审核员';
                    } else {
                        roleText = '普通用户';
                    }
                    userRoleEl.textContent = roleText;
                }

                if (adminLink) {
                    adminLink.style.display = userInfo.role && userInfo.role.toUpperCase() === 'ADMIN' ? 'block' : 'none';
                }
                if (teacherLink) {
                    teacherLink.style.display = userInfo.role && userInfo.role.toUpperCase() === 'TEACHER' ? 'block' : 'none';
                }
                if (assessorLink) {
                    assessorLink.style.display = userInfo.role && userInfo.role.toUpperCase() === 'ASSESSOR' ? 'block' : 'none';
                }
            }
        } catch (e) {
            console.error('解析用户信息失败:', e);
            // 解析失败，清除无效数据
            localStorage.removeItem('token');
            localStorage.removeItem('userInfo');
            sessionStorage.removeItem('token');
            sessionStorage.removeItem('userInfo');
            if (loginBtnItem) loginBtnItem.style.display = 'block';
            if (userMenuItem) userMenuItem.style.display = 'none';
        }
    } else {
        // 未登录，显示登录按钮
        if (loginBtnItem) loginBtnItem.style.display = 'block';
        if (userMenuItem) userMenuItem.style.display = 'none';
    }
}

function logout() {
    // 清除登录信息
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('userInfo');

    // 刷新页面显示登录按钮
    checkLoginStatus();

    // 可以添加提示
    alert('已退出登录');
}

// 等待页面加载完成
document.addEventListener('DOMContentLoaded', function () {
    // 检查登录状态
    checkLoginStatus();

    // 退出登录按钮事件
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', function (e) {
            e.preventDefault();
            logout();
        });
    }

    // 用户头像点击显示/隐藏下拉菜单
    const userAvatar = document.getElementById('userAvatar');
    const userDropdown = document.getElementById('userDropdown');
    if (userAvatar && userDropdown) {
        userAvatar.addEventListener('click', function (e) {
            e.stopPropagation();
            userDropdown.classList.toggle('show');
        });

        // 点击其他地方关闭下拉菜单
        document.addEventListener('click', function (e) {
            if (!userAvatar.contains(e.target) && !userDropdown.contains(e.target)) {
                userDropdown.classList.remove('show');
            }
        });
    }

    // 0. Banner图片轮播
    const slides = document.querySelectorAll('.banner-slideshow .slide');
    let currentSlide = 0;

    if (slides.length > 0) {
        function showNextSlide() {
            slides[currentSlide].classList.remove('active');
            currentSlide = (currentSlide + 1) % slides.length;
            slides[currentSlide].classList.add('active');
        }

        // 每5秒切换一次
        setInterval(showNextSlide, 5000);
    }

    // 1. 移动端菜单切换
    const menuBtn = document.querySelector('.menu-btn');
    const navMenu = document.querySelector('.nav-menu');

    menuBtn.addEventListener('click', function () {
        navMenu.classList.toggle('show');
    });

    // 2. 导航栏滚动高亮
    const navLinks = document.querySelectorAll('.nav-menu a');
    const sections = document.querySelectorAll('section');

    window.addEventListener('scroll', function () {
        let current = '';
        sections.forEach(section => {
            const sectionTop = section.offsetTop;
            const sectionHeight = section.clientHeight;
            if (pageYOffset >= (sectionTop - 100)) {
                current = section.getAttribute('id');
            }
        });

        navLinks.forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('href') === `#${current}`) {
                link.classList.add('active');
            }
        });
    });

    // 3. 平滑滚动（点击导航跳转）
    navLinks.forEach(link => {
        link.addEventListener('click', function (e) {
            // 如果是外部链接（如login.html），不阻止默认行为
            const href = this.getAttribute('href');
            if (href && href.startsWith('#') && href.length > 1) {
                e.preventDefault();
                const targetElement = document.querySelector(href);
                if (targetElement) {
                    window.scrollTo({
                        top: targetElement.offsetTop - 80,
                        behavior: 'smooth'
                    });
                    // 移动端点击后关闭菜单
                    navMenu.classList.remove('show');
                }
            }
        });
    });

    // 4. 常见问题折叠展开
    const faqQuestions = document.querySelectorAll('.faq-question');

    faqQuestions.forEach(question => {
        question.addEventListener('click', function () {
            const faqItem = this.parentElement;
            faqItem.classList.toggle('active');
        });
    });

    // 5. 回到顶部按钮
    const backToTopBtn = document.querySelector('#back-to-top');

    window.addEventListener('scroll', function () {
        if (pageYOffset > 300) {
            backToTopBtn.classList.add('show');
        } else {
            backToTopBtn.classList.remove('show');
        }
    });

    backToTopBtn.addEventListener('click', function () {
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
    });
});