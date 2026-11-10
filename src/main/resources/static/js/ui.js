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

    // Notification Bell + Dropdown
    const notifBadge = this.createElement('span', { className: 'notif-badge-count', id: 'notifBadgeCount' }, '0');
    const bellSvg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    bellSvg.setAttribute('width', '18');
    bellSvg.setAttribute('height', '18');
    bellSvg.setAttribute('viewBox', '0 0 24 24');
    bellSvg.setAttribute('fill', 'none');
    bellSvg.setAttribute('stroke', 'currentColor');
    bellSvg.setAttribute('stroke-width', '2');
    const path1 = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path1.setAttribute('d', 'M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9');
    const path2 = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path2.setAttribute('d', 'M13.73 21a2 2 0 0 1-3.46 0');
    bellSvg.append(path1, path2);

    const notifBtn = this.createElement('button', {
      className: 'notif-bell-btn',
      type: 'button',
      title: 'Notifications',
      id: 'notifBellBtn'
    }, bellSvg, notifBadge);

    const notifList = this.createElement('div', { className: 'notif-list', id: 'notifListContainer' },
      this.createElement('div', { style: 'padding: 1rem; color: var(--text-muted); font-size: 0.85rem; text-align: center;' }, 'Loading notifications...')
    );

    const markAllBtn = this.createElement('button', {
      className: 'btn btn-secondary btn-sm',
      type: 'button',
      style: 'font-size: 0.72rem; padding: 0.25rem 0.5rem; min-height: auto;',
      onclick: async (e) => {
        e.stopPropagation();
        await fetch('/api/notifications/read-all', { method: 'POST', credentials: 'same-origin' });
        await this.refreshNotifications();
      }
    }, 'Mark all read');

    const notifDropdown = this.createElement('div', { className: 'notif-dropdown', id: 'notifDropdown' },
      this.createElement('div', { className: 'notif-header' },
        this.createElement('strong', { style: 'font-size: 0.88rem;' }, 'Notifications'),
        markAllBtn
      ),
      notifList
    );

    const notifWrapper = this.createElement('div', { className: 'notif-wrapper' }, notifBtn, notifDropdown);

    notifBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
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

    const rightNav = this.createElement('div', { className: 'navbar-user' },
      currentUser.role === 'STUDENT'
        ? this.createElement('a', {
            href: '/student/circulars.html',
            className: 'nav-parcel-pill',
            id: 'navCircularBadge',
            style: 'display:none;background:#fee2e2;color:#b91c1c;border-color:#fca5a5;'
          }, '📢 0 Unread')
        : null,
      currentUser.role === 'STUDENT'
        ? this.createElement('a', {
            href: '/student/my-parcels.html',
            className: 'nav-parcel-pill',
            id: 'navParcelBadge',
            style: 'display:none;'
          }, '📦 0 Parcels')
        : null,
      notifWrapper,
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
        { key: 'dashboard', label: 'Dashboard', href: '/warden/dashboard.html' },
        { key: 'outpasses', label: 'Outpass', href: '/warden/outpasses.html' },
        { key: 'digital-attendance', label: 'Attendance', href: '/warden/digital-attendance.html' },
        { key: 'attendance', label: 'Roll Call Register', href: '/warden/attendance.html' },
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
        pill.textContent = `📢 ${c} Unread Circular${c === 1 ? '' : 's'}`;
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
        pill.textContent = `📦 ${c} Waiting Parcel${c === 1 ? '' : 's'}`;
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
        listEl.appendChild(this.createElement('div', { style: 'padding: 1.25rem; text-align: center; color: var(--text-muted); font-size: 0.85rem;' }, 'No notifications yet.'));
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

  formatDateTime(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' });
  },

  formatDate(iso) {
    if (!iso) return '—';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return iso;
    return d.toLocaleDateString('en-IN', { dateStyle: 'medium' });
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
      if (rt === 'DIGITAL_ATTENDANCE') return '/warden/digital-attendance.html';
      if (rt === 'OUTPASS') return '/warden/outpasses.html';
      return '/warden/dashboard.html';
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
            chartTitle ? UI.createElement('div', { style: 'font-size:0.75rem;font-weight:700;color:var(--text-muted);margin-bottom:0.35rem;text-transform:uppercase;' }, chartTitle) : null,
            bar,
            legend
          );
          host.appendChild(chartBox);
        }
      }
    }
  }
};
