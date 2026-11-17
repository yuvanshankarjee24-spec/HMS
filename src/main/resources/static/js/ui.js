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

  // Helper to generate consistent Lucide outline SVGs (22px)
  _getSidebarIcon(iconKey) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('width', '22');
    svg.setAttribute('height', '22');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '2');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.setAttribute('aria-hidden', 'true');

    let paths = '';
    switch (iconKey) {
      case 'dashboard':
      case 'warden-overview':
      case 'admin-dashboard':
        paths = '<rect width="7" height="9" x="3" y="3" rx="1"/><rect width="7" height="5" x="14" y="3" rx="1"/><rect width="7" height="9" x="14" y="12" rx="1"/><rect width="7" height="5" x="3" y="16" rx="1"/>';
        break;
      case 'outpasses':
      case 'verify-outpass':
        paths = '<path d="M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4"/><polyline points="10 17 15 12 10 7"/><line x1="15" y1="12" x2="3" y2="12"/>';
        break;
      case 'attendance':
      case 'digital-attendance':
      case 'admin-attendance':
        paths = '<rect x="3" y="4" width="18" height="18" rx="2" ry="2"/><line x1="16" y1="2" x2="16" y2="6"/><line x1="8" y1="2" x2="8" y2="6"/><line x1="3" y1="10" x2="21" y2="10"/><path d="m9 16 2 2 4-4"/>';
        break;
      case 'parcels':
      case 'my-parcels':
      case 'admin-parcels':
        paths = '<path d="m16.5 9.4-9-5.19M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.29 7 12 12.5 20.71 7"/><line x1="12" y1="22.5" x2="12" y2="12"/>';
        break;
      case 'mess':
      case 'mess-tokens':
      case 'admin-mess':
        paths = '<path d="M18 8h1a4 4 0 0 1 0 8h-1"/><path d="M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z"/><line x1="6" y1="1" x2="6" y2="4"/><line x1="10" y1="1" x2="10" y2="4"/><line x1="14" y1="1" x2="14" y2="4"/>';
        break;
      case 'maintenance':
      case 'admin-maintenance':
      case 'workers':
        paths = '<path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z"/>';
        break;
      case 'circulars':
      case 'admin-circulars':
        paths = '<path d="m3 11 18-5v12L3 14v-3z"/><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6"/>';
        break;
      case 'profile':
        paths = '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>';
        break;
      case 'students':
      case 'users':
        paths = '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>';
        break;
      case 'change-password':
        paths = '<rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/>';
        break;
      default:
        paths = '<circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/>';
    }
    svg.innerHTML = paths;
    return svg;
  },

  // Helper for Topbar icons
  _getPillIcon(type) {
    const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    svg.setAttribute('width', '18');
    svg.setAttribute('height', '18');
    svg.setAttribute('viewBox', '0 0 24 24');
    svg.setAttribute('fill', 'none');
    svg.setAttribute('stroke', 'currentColor');
    svg.setAttribute('stroke-width', '2');
    svg.setAttribute('stroke-linecap', 'round');
    svg.setAttribute('stroke-linejoin', 'round');
    svg.setAttribute('aria-hidden', 'true');

    let paths = '';
    switch (type) {
      case 'megaphone':
        paths = '<path d="m3 11 18-5v12L3 14v-3z"/><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6"/>';
        break;
      case 'package':
        paths = '<path d="m16.5 9.4-9-5.19M21 16V8a2 2 0 0 0-1-1.73l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3 8v8a2 2 0 0 0 1 1.73l7 4a2 2 0 0 0 2 0l7-4A2 2 0 0 0 21 16z"/><polyline points="3.29 7 12 12.5 20.71 7"/><line x1="12" y1="22.5" x2="12" y2="12"/>';
        break;
      case 'bell':
        paths = '<path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.73 21a2 2 0 0 1-3.46 0"/>';
        break;
      case 'user':
        paths = '<path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>';
        break;
      case 'logout':
        paths = '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>';
        break;
      case 'chevron-down':
        svg.setAttribute('width', '14');
        svg.setAttribute('height', '14');
        paths = '<polyline points="6 9 12 15 18 9"/>';
        break;
    }
    svg.innerHTML = paths;
    return svg;
  },

  // Shared Top Navbar & Sidebar Layout (AppShell)
  renderLayout(currentUser, activePageKey) {
    const appContainer = document.getElementById('app-layout');
    if (!appContainer) return;

    // --- SIDEBAR (Fixed Full Height, Dark Navy) ---
    const sidebar = this.createElement('aside', { className: 'app-sidebar', id: 'sidebar', 'aria-label': 'Main Navigation' });

    // Sidebar Header: Logo + "PSG iTech Hostel" + Divider
    const logoImg = this.createElement('img', {
      src: '/img/psgitech_logo.png',
      alt: 'PSG iTech Logo',
      className: 'sidebar-logo'
    });

    const sidebarTitle = this.createElement('span', { className: 'sidebar-title' }, 'PSG iTech Hostel');
    const sidebarHeader = this.createElement('div', { className: 'sidebar-header' }, logoImg, sidebarTitle);
    sidebar.appendChild(sidebarHeader);

    // Sidebar Menu Items
    const navList = this.createElement('ul', { className: 'sidebar-nav' });

    const menuItemsByRole = {
      'STUDENT': [
        { key: 'dashboard', label: 'Dashboard', href: '/student/dashboard.html' },
        { key: 'outpasses', label: 'Outpass', href: '/student/outpasses.html' },
        { key: 'attendance', label: 'Attendance', href: '/student/attendance.html' },
        { key: 'my-parcels', label: 'Parcels', href: '/student/my-parcels.html' },
        { key: 'mess-tokens', label: 'Mess Tokens', href: '/student/mess-tokens.html' },
        { key: 'maintenance', label: 'Maintenance Requests', href: '/student/maintenance.html' },
        { key: 'circulars', label: 'Circulars & Events', href: '/student/circulars.html' },
        { key: 'profile', label: 'My Profile', href: '/student/profile.html' }
      ],
      'WARDEN': [
        { key: 'dashboard', label: 'Dashboard', href: '/warden/dashboard' },
        { key: 'outpasses', label: 'Outpass', href: '/warden/outpasses.html' },
        { key: 'digital-attendance', label: 'Attendance', href: '/warden/attendance' },
        { key: 'parcels', label: 'Parcels', href: '/warden/parcels.html' },
        { key: 'mess', label: 'Mess', href: '/warden/mess.html' },
        { key: 'maintenance', label: 'Maintenance', href: '/warden/maintenance.html' },
        { key: 'circulars', label: 'Circulars & Events', href: '/warden/circulars.html' },
        { key: 'students', label: 'Students', href: '/warden/students.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'ADMIN': [
        { key: 'warden-overview', label: 'Hostel Overview', href: '/warden/dashboard.html' },
        { key: 'admin-dashboard', label: 'Admin Dashboard', href: '/admin/dashboard.html' },
        { key: 'admin-circulars', label: 'Circulars & Events', href: '/admin/circulars.html' },
        { key: 'admin-mess', label: 'Mess Reports', href: '/admin/mess.html' },
        { key: 'admin-attendance', label: 'Digital Attendance', href: '/admin/attendance.html' },
        { key: 'admin-parcels', label: 'Parcels & Reports', href: '/admin/parcels.html' },
        { key: 'admin-maintenance', label: 'Maintenance & Reports', href: '/admin/maintenance.html' },
        { key: 'workers', label: 'Maintenance & Workers', href: '/warden/maintenance.html?tab=workers' },
        { key: 'users', label: 'User Directory', href: '/admin/users.html' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'SECURITY': [
        { key: 'dashboard', label: 'Security Desk', href: '/security/dashboard.html' },
        { key: 'verify-outpass', label: 'Verify Outpass', href: '/security/dashboard.html?tab=verify' },
        { key: 'change-password', label: 'Change Password', href: '/change-password.html' }
      ],
      'WORKER': [
        { key: 'dashboard', label: 'My Tasks', href: '/worker/dashboard.html' },
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
    this._currentMenuItems = items;
    this._activePageKeyFallback = activePageKey;

    items.forEach(item => {
      const icon = this._getSidebarIcon(item.key);
      const labelSpan = this.createElement('span', {}, item.label);
      const link = this.createElement('a', {
        className: 'sidebar-link',
        href: item.href,
        dataset: { navKey: item.key }
      }, icon, labelSpan);

      link.addEventListener('click', () => {
        const sb = document.getElementById('sidebar');
        const bd = document.getElementById('sidebarBackdrop');
        if (sb) sb.classList.remove('open');
        if (bd) bd.classList.remove('open');
      });
      navList.appendChild(this.createElement('li', {}, link));
    });

    sidebar.appendChild(navList);

    // --- TOP BAR (White, 70px height, Soft bottom shadow) ---
    const hamburgerBtn = this.createElement('button', {
      className: 'btn-hamburger',
      type: 'button',
      'aria-label': 'Toggle navigation menu',
      onclick: () => {
        const sb = document.getElementById('sidebar');
        const bd = document.getElementById('sidebarBackdrop');
        if (sb) sb.classList.toggle('open');
        if (bd) bd.classList.toggle('open');
      }
    }, '☰');

    const mobileBrand = this.createElement('div', { className: 'topbar-mobile-brand' },
      this.createElement('img', { src: '/img/psgitech_logo.png', alt: 'Logo', className: 'topbar-logo' }),
      this.createElement('span', { className: 'topbar-title' }, 'PSG iTech Hostel')
    );

    const leftNav = this.createElement('div', { className: 'topbar-left' }, hamburgerBtn, mobileBrand);

    // Notification Bell + Dropdown
    const notifBadge = this.createElement('span', { className: 'notif-badge-count', id: 'notifBadgeCount', style: 'display:none;' }, '0');
    const bellSvg = this._getPillIcon('bell');
    const notifBtn = this.createElement('button', {
      className: 'notif-bell-btn',
      type: 'button',
      title: 'Notifications',
      'aria-label': 'Notifications',
      id: 'notifBellBtn'
    }, bellSvg, notifBadge);

    const notifList = this.createElement('div', { className: 'notif-list', id: 'notifListContainer' },
      this.createElement('div', { style: 'padding: 1.25rem; color: var(--text-muted); font-size: var(--font-size-secondary); text-align: center;' }, 'Loading notifications...')
    );

    const markAllBtn = this.createElement('button', {
      className: 'btn btn-secondary btn-sm',
      type: 'button',
      style: 'font-size: var(--font-size-helper); padding: 0.25rem 0.65rem; min-height: auto;',
      onclick: async (e) => {
        e.stopPropagation();
        await fetch('/api/notifications/read-all', { method: 'POST', credentials: 'same-origin' });
        await this.refreshNotifications();
      }
    }, 'Mark all read');

    const notifDropdown = this.createElement('div', { className: 'notif-dropdown', id: 'notifDropdown' },
      this.createElement('div', { className: 'notif-header' },
        this.createElement('strong', { style: 'font-size: var(--font-size-secondary); font-weight: 700; color: var(--navy-900);' }, 'Notifications'),
        markAllBtn
      ),
      notifList
    );

    const notifWrapper = this.createElement('div', { className: 'notif-wrapper' }, notifBtn, notifDropdown);

    notifBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const userDd = document.getElementById('userDropdownMenu');
      if (userDd) userDd.classList.remove('open');
      const isOpen = notifDropdown.classList.toggle('open');
      if (isOpen) {
        await this.refreshNotifications();
      }
    });

    document.addEventListener('click', (e) => {
      if (!notifWrapper.contains(e.target)) {
        notifDropdown.classList.remove('open');
      }
    });

    // Circular pill (STUDENT only)
    let circularPill = null;
    if (currentUser.role === 'STUDENT') {
      circularPill = this.createElement('a', {
        href: '/student/circulars.html',
        className: 'topbar-pill topbar-pill-circular',
        id: 'navCircularBadge',
        style: 'display:none;'
      },
        this._getPillIcon('megaphone'),
        this.createElement('span', { className: 'pill-text' }, '0 Unread Circular')
      );
    }

    // Parcel pill (STUDENT only)
    let parcelPill = null;
    if (currentUser.role === 'STUDENT') {
      parcelPill = this.createElement('a', {
        href: '/student/my-parcels.html',
        className: 'topbar-pill topbar-pill-parcel',
        id: 'navParcelBadge',
        style: 'display:none;'
      },
        this._getPillIcon('package'),
        this.createElement('span', { className: 'pill-text' }, '0 Waiting Parcels')
      );
    }

    // User Chip with Dropdown
    const userInitial = currentUser.name ? currentUser.name.charAt(0).toUpperCase() : 'U';
    const userAvatar = this.createElement('div', { className: 'user-chip-avatar' });
    if (currentUser.photoUrl) {
      const img = document.createElement('img');
      img.src = currentUser.photoUrl;
      img.alt = currentUser.name || 'User';
      userAvatar.appendChild(img);
    } else {
      userAvatar.textContent = userInitial;
    }

    const userChipName = this.createElement('div', { className: 'user-chip-name' }, currentUser.name || 'User');
    const userChipRole = this.createElement('div', { className: 'user-chip-role' }, (currentUser.role || '').replace(/_/g, ' '));
    const userChipMeta = this.createElement('div', { className: 'user-chip-meta' }, userChipName, userChipRole);
    const chevronSvg = this._getPillIcon('chevron-down');

    const userChipBtn = this.createElement('button', {
      className: 'user-chip-btn',
      type: 'button',
      'aria-haspopup': 'true',
      'aria-expanded': 'false',
      id: 'userChipBtn'
    }, userAvatar, userChipMeta, chevronSvg);

    const profileHref = currentUser.role === 'STUDENT' ? '/student/profile.html' : '/change-password.html';
    const dropdownProfile = this.createElement('a', {
      className: 'user-dropdown-item',
      href: profileHref
    }, this._getPillIcon('user'), 'My Profile');

    const dropdownLogout = this.createElement('button', {
      className: 'user-dropdown-item',
      type: 'button',
      onclick: () => Auth.logout()
    }, this._getPillIcon('logout'), 'Sign out');

    const userDropdownMenu = this.createElement('div', { className: 'user-dropdown-menu', id: 'userDropdownMenu' },
      dropdownProfile,
      this.createElement('div', { className: 'user-dropdown-divider' }),
      dropdownLogout
    );

    const userChipWrapper = this.createElement('div', { className: 'user-chip-wrapper' }, userChipBtn, userDropdownMenu);

    userChipBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      if (notifDropdown) notifDropdown.classList.remove('open');
      const isOpen = userDropdownMenu.classList.toggle('open');
      userChipBtn.setAttribute('aria-expanded', String(isOpen));
    });

    document.addEventListener('click', (e) => {
      if (!userChipWrapper.contains(e.target)) {
        userDropdownMenu.classList.remove('open');
        userChipBtn.setAttribute('aria-expanded', 'false');
      }
    });

    // Sign out outline button
    const logoutBtn = this.createElement('button', {
      className: 'btn-signout-outline',
      type: 'button',
      title: 'Sign out',
      onclick: () => Auth.logout()
    }, this._getPillIcon('logout'), 'Sign out');

    const rightNav = this.createElement('div', { className: 'topbar-right' },
      circularPill,
      parcelPill,
      notifWrapper,
      userChipWrapper,
      logoutBtn
    );

    const navbar = this.createElement('nav', { className: 'top-navbar', 'aria-label': 'Top Navigation' },
      leftNav,
      rightNav
    );

    // Backdrop for mobile off-canvas drawer
    let backdrop = document.getElementById('sidebarBackdrop');
    if (!backdrop) {
      backdrop = this.createElement('div', {
        className: 'sidebar-backdrop',
        id: 'sidebarBackdrop',
        onclick: () => {
          const sb = document.getElementById('sidebar');
          if (sb) sb.classList.remove('open');
          backdrop.classList.remove('open');
        }
      });
      document.body.appendChild(backdrop);
    }

    // Keyboard support: Escape closes drawers and dropdowns
    if (!this._escListenerBound) {
      window.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
          const sb = document.getElementById('sidebar');
          const bd = document.getElementById('sidebarBackdrop');
          if (sb) sb.classList.remove('open');
          if (bd) bd.classList.remove('open');
          const userDd = document.getElementById('userDropdownMenu');
          if (userDd) userDd.classList.remove('open');
          const notifDd = document.getElementById('notifDropdown');
          if (notifDd) notifDd.classList.remove('open');
        }
      });
      this._escListenerBound = true;
    }

    // Insert fresh navbar & sidebar into DOM
    const existingNav = document.querySelector('.top-navbar');
    if (existingNav) existingNav.remove();
    const existingSidebar = document.getElementById('sidebar');
    if (existingSidebar) existingSidebar.remove();

    appContainer.insertBefore(sidebar, appContainer.firstChild);

    const mainContent = appContainer.querySelector('.main-content');
    if (mainContent) {
      mainContent.insertBefore(navbar, mainContent.firstChild);
    } else {
      appContainer.insertBefore(navbar, appContainer.children[1] || null);
    }

    this.syncActiveSidebarFromUrl();
    if (!this._sidebarPopListenerBound) {
      window.addEventListener('popstate', () => this.syncActiveSidebarFromUrl());
      this._sidebarPopListenerBound = true;
    }

    this._currentUserRole = currentUser.role;
    this.refreshUnreadCount();
    if (currentUser.role === 'STUDENT') {
      this.refreshParcelBadge();
      this.refreshCircularBadge();
    }
    if (!this._notifPoller) {
      this._notifPoller = setInterval(() => this.refreshUnreadCount(), 30000);
    }
  },

  async refreshCircularBadge() {
    try {
      const res = await fetch('/api/student/circulars/banner-summary', { credentials: 'same-origin' });
      if (!res.ok) return;
      const data = await res.json();
      const pill = document.getElementById('navCircularBadge');
      if (pill) {
        const c = data.unreadCount || 0;
        const textSpan = pill.querySelector('.pill-text');
        const label = `${c} Unread Circular${c === 1 ? '' : 's'}`;
        if (textSpan) textSpan.textContent = label;
        else pill.textContent = label;
        pill.style.display = c > 0 ? 'inline-flex' : 'none';
      }
    } catch (e) {}
  },

  async refreshParcelBadge() {
    try {
      const res = await fetch('/api/student/parcels/waiting-count', { credentials: 'same-origin' });
      if (!res.ok) return;
      const data = await res.json();
      const pill = document.getElementById('navParcelBadge');
      if (pill) {
        const c = data.waitingCount || 0;
        const textSpan = pill.querySelector('.pill-text');
        const label = `${c} Waiting Parcel${c === 1 ? '' : 's'}`;
        if (textSpan) textSpan.textContent = label;
        else pill.textContent = label;
        pill.style.display = c > 0 ? 'inline-flex' : 'none';
      }
    } catch (e) {}
  },

  async refreshUnreadCount() {
    try {
      const res = await fetch('/api/notifications/unread-count', { credentials: 'same-origin' });
      if (!res.ok) return;
      const data = await res.json();
      const badge = document.getElementById('notifBadgeCount');
      if (badge) {
        const count = data.unreadCount || 0;
        badge.textContent = count > 99 ? '99+' : String(count);
        badge.style.display = count > 0 ? 'inline-flex' : 'none';
      }
    } catch (e) {}
  },

  async refreshNotifications() {
    const listEl = document.getElementById('notifListContainer');
    if (!listEl) return;
    try {
      const res = await fetch('/api/notifications?page=0&size=15', { credentials: 'same-origin' });
      if (!res.ok) return;
      const data = await res.json();
      const items = data.content || [];
      listEl.innerHTML = '';
      if (items.length === 0) {
        listEl.appendChild(this.createElement('div', { style: 'padding: 1.25rem; text-align: center; color: var(--text-muted); font-size: var(--font-size-secondary);' }, 'No notifications yet.'));
      } else {
        items.forEach(n => {
          const item = this.createElement('div', {
            className: `notif-item ${n.read ? '' : 'unread'}`,
            style: 'cursor:pointer;',
            onclick: async () => {
              if (!n.read) {
                await fetch(`/api/notifications/${n.id}/read`, { method: 'POST', credentials: 'same-origin' });
                item.classList.remove('unread');
                await this.refreshUnreadCount();
              }
              const target = this.resolveNotificationUrl(n, this._currentUserRole);
              if (target) window.location.href = target;
            }
          },
            this.createElement('div', { className: 'notif-item-title' }, n.title),
            this.createElement('div', { className: 'notif-item-msg' }, n.message),
            this.createElement('div', { className: 'notif-item-time' }, this.formatDateTime(n.createdAt))
          );
          listEl.appendChild(item);
        });
      }
      await this.refreshUnreadCount();
    } catch (e) {}
  },

  _parseUtcAwareDate(iso) {
    if (!iso) return null;
    const raw = String(iso).trim();
    // Append Z when naive ISO timestamp (stored in UTC) lacks offset
    const normalized = (/^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}/.test(raw) && !/[Zz]|[+-]\d{2}:?\d{2}$/.test(raw))
      ? raw.replace(' ', 'T') + 'Z'
      : raw;
    const d = new Date(normalized);
    return isNaN(d.getTime()) ? null : d;
  },

  formatDateTime(iso) {
    if (!iso) return '—';
    const d = this._parseUtcAwareDate(iso);
    if (!d) return iso;
    return d.toLocaleString('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium', timeStyle: 'short' });
  },

  formatDate(iso) {
    if (!iso) return '—';
    if (/^\d{4}-\d{2}-\d{2}$/.test(String(iso).trim())) {
      const [y, m, day] = String(iso).trim().split('-').map(Number);
      return new Date(Date.UTC(y, m - 1, day)).toLocaleDateString('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium' });
    }
    const d = this._parseUtcAwareDate(iso);
    if (!d) return iso;
    return d.toLocaleDateString('en-IN', { timeZone: 'Asia/Kolkata', dateStyle: 'medium' });
  },

  categoryLabel(cat) {
    const map = {
      'ROOM_CLEANING': 'Room Cleaning',
      'ELECTRICAL': 'Electrical',
      'PLUMBING': 'Plumbing',
      'FURNITURE_CARPENTRY': 'Furniture / Carpentry',
      'INTERNET_WIFI': 'Internet / WiFi',
      'BATHROOM_WASHROOM': 'Bathroom / Washroom',
      'OTHER': 'Other'
    };
    return map[cat] || cat;
  },

  workerSkillLabel(skill) {
    const map = {
      'CLEANING': 'Cleaner / Housekeeping',
      'ELECTRICAL': 'Electrician',
      'PLUMBING': 'Plumber',
      'CARPENTRY': 'Carpenter',
      'IT_NETWORK': 'IT / WiFi Support',
      'GENERAL': 'General Maintenance'
    };
    return map[skill] || skill;
  },

  statusBadge(status) {
    const labelMap = {
      'PENDING': 'Pending',
      'ASSIGNED': 'Assigned',
      'IN_PROGRESS': 'In Progress',
      'COMPLETED': 'Completed',
      'RESOLVED': 'Resolved',
      'REJECTED': 'Rejected',
      'CANCELLED': 'Cancelled',
      'REOPENED': 'Reopened',
      'ON_HOLD': 'On Hold (Materials)'
    };
    const cls = `status-badge badge-maint-${(status || '').toLowerCase()}`;
    return this.createElement('span', { className: cls }, labelMap[status] || status);
  },

  priorityBadge(prio) {
    const cls = `status-badge badge-prio-${(prio || '').toLowerCase()}`;
    return this.createElement('span', { className: cls }, prio);
  },

  digitalAttendanceBadge(status) {
    const labelMap = {
      'MARKED': 'Marked',
      'PENDING': 'Pending',
      'MISSED': 'Not Pressed / Missed',
      'ON_OUTPASS': 'On Outpass',
      'MANUAL_PRESENT': 'Manually Marked',
      'LOCKED': 'Locked'
    };
    const cls = `status-badge badge-da-${(status || '').toLowerCase()}`;
    return this.createElement('span', { className: cls }, labelMap[status] || status);
  },

  parcelStatusBadge(status) {
    const labelMap = {
      'RECEIVED': 'Received',
      'NOTIFIED': 'Waiting for Pickup',
      'DELIVERED': 'Delivered',
      'RETURNED_TO_SENDER': 'Returned to Sender',
      'CANCELLED': 'Cancelled',
      'UNCLAIMED': 'Unclaimed'
    };
    const cls = `status-badge badge-prc-${(status || '').toLowerCase()}`;
    return this.createElement('span', { className: cls }, labelMap[status] || status);
  },

  tokenStatusBadge(status) {
    const labelMap = {
      'BOOKED': 'Booked',
      'QR_ACTIVE': 'QR Active',
      'DELIVERED': 'Delivered',
      'PARTIALLY_DELIVERED': 'Partially Delivered',
      'CANCELLED': 'Cancelled',
      'EXPIRED': 'Expired'
    };
    const cls = `status-badge badge-tkn-${(status || '').toLowerCase()}`;
    return this.createElement('span', { className: cls }, labelMap[status] || status);
  },

  syncActiveSidebarFromUrl() {
    const navItems = this._currentMenuItems || [];
    const links = Array.from(document.querySelectorAll('#sidebar .sidebar-link'));
    if (links.length === 0) return;

    const normalizePath = (p) => {
      const clean = (p || '/').replace(/\/+$/, '') || '/';
      if (
        clean === '/warden/attendance' ||
        clean === '/warden/attendance.html' ||
        clean === '/warden/digital-attendance' ||
        clean === '/warden/digital-attendance.html'
      ) {
        return '/warden/attendance';
      }
      return clean.replace(/\.html$/, '');
    };

    const curPath = normalizePath(window.location.pathname);
    const curParams = new URLSearchParams(window.location.search || '');
    const curTab = curParams.get('tab');

    const matchesPath = (itemPath) => itemPath === curPath;

    let matched = navItems.find(item => {
      const u = new URL(item.href, window.location.origin);
      const itemPath = normalizePath(u.pathname);
      const itemTab = u.searchParams.get('tab');
      return matchesPath(itemPath) && itemTab !== null && itemTab === curTab;
    });

    if (!matched) {
      matched = navItems.find(item => {
        const u = new URL(item.href, window.location.origin);
        return matchesPath(normalizePath(u.pathname)) && !u.searchParams.has('tab');
      });
    }

    if (!matched) {
      matched = navItems.find(item => {
        const u = new URL(item.href, window.location.origin);
        return matchesPath(normalizePath(u.pathname));
      });
    }

    if (!matched && this._activePageKeyFallback) {
      matched = navItems.find(item => item.key === this._activePageKeyFallback) || null;
    }

    links.forEach(link => {
      const isMatch = matched && link.dataset.navKey === matched.key;
      link.classList.toggle('active', Boolean(isMatch));
      if (isMatch) {
        link.setAttribute('aria-current', 'page');
      } else {
        link.removeAttribute('aria-current');
      }
    });
  },

  resolveNotificationUrl(n, role) {
    const rt = (n && n.referenceType) ? String(n.referenceType).toUpperCase() : '';
    const refId = n && n.referenceId ? n.referenceId : '';
    if (role === 'STUDENT') {
      if (rt === 'CIRCULAR') return `/student/circulars.html${refId ? '?id=' + encodeURIComponent(refId) : ''}`;
      if (rt === 'MESS_TOKEN') return '/student/mess-tokens.html?tab=my-tokens';
      if (rt === 'PARCEL') return '/student/my-parcels.html';
      if (rt === 'MAINTENANCE_REQUEST') return '/student/maintenance.html?tab=list';
      if (rt === 'DIGITAL_ATTENDANCE') return '/student/attendance.html';
      if (rt === 'OUTPASS') return '/student/outpasses.html';
      return '/student/dashboard.html';
    }
    if (role === 'WARDEN' || role === 'ADMIN') {
      if (rt === 'CIRCULAR') return '/warden/circulars.html';
      if (rt === 'MESS_TOKEN') return '/warden/mess.html?tab=tokens';
      if (rt === 'PARCEL') return '/warden/parcels.html?tab=register';
      if (rt === 'MAINTENANCE_REQUEST') return '/warden/maintenance.html?tab=requests';
      if (rt === 'DIGITAL_ATTENDANCE') return '/warden/attendance';
      if (rt === 'OUTPASS') return '/warden/outpasses.html';
      return role === 'ADMIN' ? '/admin/dashboard.html' : '/warden/dashboard';
    }
    if (role === 'WORKER') return '/worker/dashboard.html';
    return null;
  },

  Tabs: {
    init({ barSelector = '.portal-tabs', paramName = 'tab', defaultTab, onChange }) {
      const bar = document.querySelector(barSelector);
      if (!bar) return null;
      const buttons = Array.from(bar.querySelectorAll('[data-tab]'));
      const panels = {};
      buttons.forEach(b => {
        const key = b.dataset.tab;
        const p = document.getElementById(`panel-${key}`) || document.querySelector(`[data-tab-panel="${key}"]`);
        if (p) panels[key] = p;
      });
      const activate = (key, updateUrl = true) => {
        const chosen = panels[key] ? key : (defaultTab || buttons[0]?.dataset.tab);
        buttons.forEach(b => b.classList.toggle('active', b.dataset.tab === chosen));
        Object.entries(panels).forEach(([k, p]) => {
          p.style.display = (k === chosen) ? '' : 'none';
        });
        if (updateUrl && chosen) {
          const url = new URL(window.location.href);
          url.searchParams.set(paramName, chosen);
          history.replaceState(null, '', url.toString());
        }
        UI.syncActiveSidebarFromUrl();
        if (typeof onChange === 'function' && chosen) onChange(chosen);
      };
      buttons.forEach(b => b.addEventListener('click', () => activate(b.dataset.tab, true)));
      const initial = new URLSearchParams(window.location.search).get(paramName) || defaultTab;
      activate(initial, false);
      return { switchTo: (k) => activate(k, true) };
    }
  },

  SummaryCards: {
    render(containerId, { cards = [], activeKey = null, onSelect = null, chartSegments = null, chartTitle = '' }) {
      const host = document.getElementById(containerId);
      if (!host) return;
      host.innerHTML = '';
      host.className = 'summary-strip-wrap';

      const grid = UI.createElement('div', { className: 'summary-strip-grid' });
      cards.forEach(c => {
        const isClickable = typeof onSelect === 'function' && c.filterable !== false;
        const card = UI.createElement('div', {
          className: `summary-strip-card${activeKey !== null && activeKey === c.key ? ' active' : ''}`,
          style: `border-left: 4px solid ${c.accentColor || '#2563eb'};${isClickable ? '' : 'cursor:default;'}`,
          onclick: isClickable ? () => onSelect(c.key) : undefined
        },
          UI.createElement('div', { className: 'summary-strip-label' }, c.label),
          UI.createElement('div', { className: 'summary-strip-val', style: c.valColor ? `color:${c.valColor};` : '' }, String(c.value ?? 0)),
          c.subtext ? UI.createElement('div', { className: 'summary-strip-sub' }, c.subtext) : null
        );
        grid.appendChild(card);
      });
      host.appendChild(grid);

      if (Array.isArray(chartSegments) && chartSegments.length > 0) {
        const total = chartSegments.reduce((s, x) => s + (Number(x.value) || 0), 0);
        if (total > 0) {
          const bar = UI.createElement('div', { className: 'summary-chart-bar' });
          const legend = UI.createElement('div', { className: 'summary-chart-legend' });
          chartSegments.forEach(seg => {
            const val = Number(seg.value) || 0;
            const pct = (val / total) * 100;
            if (pct > 0) {
              bar.appendChild(UI.createElement('div', {
                title: `${seg.label}: ${val} (${pct.toFixed(1)}%)`,
                style: `width:${pct.toFixed(2)}%;background:${seg.color};`
              }));
            }
            const dot = UI.createElement('span', { className: 'summary-legend-dot', style: `background:${seg.color};` });
            legend.appendChild(UI.createElement('span', {}, dot, `${seg.label}: ${val}`));
          });
          const chartBox = UI.createElement('div', { className: 'summary-mini-chart' },
            chartTitle ? UI.createElement('div', { className: 'type-eyebrow', style: 'margin-bottom:0.35rem;' }, chartTitle) : null,
            bar,
            legend
          );
          host.appendChild(chartBox);
        }
      }
    }
  }
};
