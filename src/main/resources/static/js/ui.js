/**
 * Smart Hostel Management - Shared UI Components & Utilities
 * Enforces textContent for all user data (strictly no XSS via innerHTML).
 */
const UI = {
  // DOM element creation helper with textContent support
  createElement(tag, attrs = {}, ...children) {
    const element = document.createElement(tag);
    for (const [key, val] of Object.entries(attrs)) {
      if (key === 'className') {
        element.className = val;
      } else if (key.startsWith('on') && typeof val === 'function') {
        element.addEventListener(key.substring(2).toLowerCase(), val);
      } else if (key === 'dataset') {
        for (const [dataKey, dataVal] of Object.entries(val)) {
          element.dataset[dataKey] = dataVal;
        }
      } else {
        element.setAttribute(key, val);
      }
    }
    for (const child of children) {
      if (typeof child === 'string' || typeof child === 'number') {
        element.appendChild(document.createTextNode(String(child)));
      } else if (child instanceof Node) {
        element.appendChild(child);
      }
    }
    return element;
  },

  // Toast notifications
  toastContainer: null,
  showToast(message, type = 'info') {
    if (!this.toastContainer) {
      this.toastContainer = this.createElement('div', { className: 'toast-container' });
      document.body.appendChild(this.toastContainer);
    }
    const toast = this.createElement('div', { className: `toast ${type}` }, message);
    this.toastContainer.appendChild(toast);
    setTimeout(() => {
      toast.style.opacity = '0';
      toast.style.transform = 'translateY(20px)';
      toast.style.transition = 'all 0.3s ease';
      setTimeout(() => toast.remove(), 300);
    }, 4000);
  },

  // Confirmation dialog modal
  showConfirm({ title = 'Confirm Action', message, confirmText = 'Confirm', cancelText = 'Cancel' }) {
    return new Promise((resolve) => {
      const overlay = this.createElement('div', { className: 'modal-overlay active' });
      const dialog = this.createElement('div', { className: 'modal-dialog' });
      
      const header = this.createElement('div', { className: 'modal-header' },
        this.createElement('h3', { className: 'modal-title' }, title)
      );

      const body = this.createElement('div', { className: 'modal-body' },
        this.createElement('p', {}, message)
      );

      const btnCancel = this.createElement('button', {
        className: 'btn btn-secondary',
        onclick: () => { overlay.remove(); resolve(false); }
      }, cancelText);

      const btnConfirm = this.createElement('button', {
        className: 'btn btn-primary',
        onclick: () => { overlay.remove(); resolve(true); }
      }, confirmText);

      const footer = this.createElement('div', { className: 'modal-footer' }, btnCancel, btnConfirm);

      dialog.append(header, body, footer);
      overlay.appendChild(dialog);
      document.body.appendChild(overlay);
    });
  },

  // Modal helpers
  openModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.add('active');
  },

  closeModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) modal.classList.remove('active');
  },

  // Shared Top Navbar & Sidebar Layout
  renderLayout(currentUser, activePageKey) {
    const appContainer = document.getElementById('app-layout');
    if (!appContainer) return;

    // --- TOP NAVBAR ---
    const brandLogo = this.createElement('img', {
      src: '/img/psgitech_logo.png',
      alt: 'PSG iTech Logo',
      className: 'navbar-brand-logo'
    });

    const brand = this.createElement('a', { className: 'navbar-brand', href: '#' },
      brandLogo,
      this.createElement('span', {}, 'PSG iTech Hostel')
    );

    const hamburgerBtn = this.createElement('button', {
      className: 'btn-hamburger',
      onclick: () => {
        const sidebar = document.getElementById('sidebar');
        if (sidebar) sidebar.classList.toggle('open');
      }
    }, '☰');

    const leftNav = this.createElement('div', { style: 'display: flex; align-items: center; gap: 1rem;' },
      hamburgerBtn,
      brand
    );

    const userInitial = currentUser.name ? currentUser.name.charAt(0).toUpperCase() : 'U';
    const avatarCircle = this.createElement('div', { className: 'user-avatar-circle' }, userInitial);

    const userInfoName = this.createElement('span', { className: 'user-info-name' });
    userInfoName.textContent = currentUser.name;

    const roleBadge = this.createElement('span', { className: `role-badge ${currentUser.role}` });
    roleBadge.textContent = currentUser.role.replace('_', ' ');

    const userInfo = this.createElement('div', { className: 'user-info-text' },
      userInfoName,
      roleBadge
    );

    const userBadge = this.createElement('div', { className: 'user-profile-badge' },
      avatarCircle,
      userInfo
    );

    const logoutBtn = this.createElement('button', {
      className: 'btn-logout',
      onclick: () => Auth.logout()
    }, 'Sign out');

    const rightNav = this.createElement('div', { className: 'navbar-user' },
      userBadge,
      logoutBtn
    );

    const navbar = this.createElement('nav', { className: 'top-navbar' },
      leftNav,
      rightNav
    );

    // --- SIDEBAR BY ROLE ---
    const sidebar = this.createElement('aside', { className: 'app-sidebar', id: 'sidebar' });
    const navList = this.createElement('ul', { className: 'sidebar-nav' });

    const menuItemsByRole = {
      'STUDENT': [
        { key: 'dashboard', label: 'My Dashboard', href: '/student/dashboard.html' },
        { key: 'booking', label: 'Online Room Booking', href: '/student/booking.html' },
        { key: 'outpasses', label: 'My Outpasses', href: '/student/outpasses.html' },
        { key: 'profile', label: 'Profile & Photo', href: '/student/profile.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'WARDEN': [
        { key: 'dashboard', label: 'Warden Overview', href: '/warden/dashboard.html' },
        { key: 'attendance', label: 'Active Attendance', href: '/warden/attendance.html' },
        { key: 'outpasses', label: 'Outpass Requests', href: '/warden/outpasses.html' },
        { key: 'whitelist', label: 'Online Allocation Whitelist', href: '/warden/whitelist.html' },
        { key: 'rooms', label: 'Room Management', href: '/warden/rooms.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'ADMIN': [
        { key: 'warden-overview', label: 'Hostel Overview', href: '/warden/dashboard.html' },
        { key: 'rooms', label: 'Room Management', href: '/warden/rooms.html' },
        { key: 'admin-dashboard', label: 'Admin Dashboard', href: '/admin/dashboard.html' },
        { key: 'users', label: 'User Directory', href: '/admin/users.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'SECURITY': [
        { key: 'dashboard', label: 'Security Desk', href: '/security/dashboard.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'CLEANING_WORKER': [
        { key: 'dashboard', label: 'Cleaning Tasks', href: '/worker/dashboard.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'MAINTENANCE_STAFF': [
        { key: 'dashboard', label: 'Maintenance Log', href: '/maintenance/dashboard.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'MESS_STAFF': [
        { key: 'dashboard', label: 'Mess Operations', href: '/mess/dashboard.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'VENDING_MANAGER': [
        { key: 'dashboard', label: 'Vending Machines', href: '/vending/dashboard.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ]
    };

    const items = menuItemsByRole[currentUser.role] || [];
    items.forEach(item => {
      const link = this.createElement('a', {
        className: `sidebar-link ${item.key === activePageKey ? 'active' : ''}`,
        href: item.href
      }, item.label);
      navList.appendChild(this.createElement('li', {}, link));
    });

    sidebar.appendChild(navList);

    // Insert navbar at top of page, sidebar inside container
    const existingNav = document.querySelector('.top-navbar');
    if (existingNav) existingNav.remove();
    document.body.insertBefore(navbar, document.body.firstChild);

    appContainer.insertBefore(sidebar, appContainer.firstChild);
  }
};
