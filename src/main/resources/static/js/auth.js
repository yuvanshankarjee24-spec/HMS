/**
 * Smart Hostel Management - Auth & Role Guard System
 */
const Auth = {
  currentUser: null,

  async getMe() {
    try {
      const res = await fetch('/api/auth/me', {
        headers: { 'Accept': 'application/json' },
        credentials: 'same-origin'
      });
      if (res.status === 401) {
        return null;
      }
      if (!res.ok) {
        throw new Error('Failed to fetch user state');
      }
      return await res.json();
    } catch (e) {
      console.error('Auth.getMe error:', e);
      return null;
    }
  },

  async requireAuth(allowedRoles = []) {
    const user = await this.getMe();
    this.currentUser = user;

    if (!user) {
      window.location.replace('/login.html');
      return null;
    }

    if (user.mustChangePassword && !window.location.pathname.endsWith('/change-password.html')) {
      window.location.replace('/change-password.html');
      return null;
    }

    if (allowedRoles.length > 0 && !allowedRoles.includes(user.role)) {
      // Role mismatch - redirect user to their authorized dashboard
      this.redirectToDashboard(user.role);
      return null;
    }

    return user;
  },

  redirectToDashboard(role) {
    const roleRoutes = {
      'STUDENT': '/student/dashboard.html',
      'WARDEN': '/warden/dashboard.html',
      'ADMIN': '/admin/dashboard.html',
      'SECURITY': '/security/dashboard.html',
      'WORKER': '/worker/dashboard.html',
      'CLEANING_WORKER': '/worker/dashboard.html',
      'MAINTENANCE_STAFF': '/maintenance/dashboard.html',
      'MESS_STAFF': '/mess/dashboard.html',
      'VENDING_MANAGER': '/vending/dashboard.html'
    };
    const target = roleRoutes[role] || '/login.html';
    window.location.replace(target);
  },

  async logout() {
    try {
      await fetch('/api/auth/logout', {
        method: 'POST',
        credentials: 'same-origin'
      });
    } catch (e) {
      console.error('Logout error:', e);
    } finally {
      window.location.replace('/login.html');
    }
  }
};
