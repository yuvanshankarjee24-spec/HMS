/**
 * Shared UI helpers for Maintenance Requests (Timeline, Attachments, Pagination, Detail Modal)
 */
const MaintUI = {
  renderAttachments(attachments = []) {
    if (!attachments || attachments.length === 0) {
      return UI.createElement('p', { style: 'color: var(--text-muted); font-size: var(--font-size-secondary);' }, 'No photos attached.');
    }
    const grid = UI.createElement('div', { className: 'photo-thumb-grid' });
    attachments.forEach(att => {
      const wrap = UI.createElement('div', { style: 'display: flex; flex-direction: column; align-items: center; gap: 0.25rem;' });
      const img = UI.createElement('img', {
        src: att.url,
        alt: att.originalFilename || 'Attachment',
        className: 'photo-thumb',
        title: 'Click to open full image',
        onclick: () => window.open(att.url, '_blank')
      });
      const tag = UI.createElement('span', {
        style: 'font-size: var(--font-size-eyebrow); color: var(--text-muted); font-weight: var(--font-weight-eyebrow); text-transform: uppercase; letter-spacing: var(--letter-spacing-eyebrow);'
      }, att.attachmentType === 'WORKER_AFTER' ? 'After Work' : 'Reported');
      wrap.append(img, tag);
      grid.appendChild(wrap);
    });
    return grid;
  },

  renderTimeline(history = []) {
    if (!history || history.length === 0) {
      return UI.createElement('p', { style: 'color: var(--text-muted); font-size: var(--font-size-secondary);' }, 'No history recorded.');
    }
    const container = UI.createElement('div', { className: 'maint-timeline' });
    history.forEach(h => {
      const statusText = h.oldStatus ? `${h.oldStatus} \u2192 ${h.newStatus}` : h.newStatus;
      const item = UI.createElement('div', { className: 'maint-timeline-item' },
        UI.createElement('div', { style: 'display: flex; justify-content: space-between; gap: 0.5rem; flex-wrap: wrap;' },
          UI.createElement('strong', { style: 'font-size: var(--font-size-secondary);' }, statusText),
          UI.createElement('span', { style: 'font-size: var(--font-size-helper); color: var(--text-muted);' }, UI.formatDateTime(h.createdAt))
        ),
        UI.createElement('div', { style: 'font-size: var(--font-size-helper); color: var(--text-muted);' },
          `By ${h.changedByName || 'System'} (${h.changedByRole || ''})`
        ),
        h.remarks
          ? UI.createElement('div', { style: 'font-size: var(--font-size-secondary); color: var(--text-primary); margin-top: 0.2rem;' }, h.remarks)
          : null
      );
      container.appendChild(item);
    });
    return container;
  },

  renderPagination(containerEl, pageData, onPageChange) {
    containerEl.innerHTML = '';
    if (!pageData || pageData.totalPages <= 1) {
      const info = UI.createElement('span', { style: 'font-size: var(--font-size-secondary); color: var(--text-secondary);' },
        `Showing ${pageData ? pageData.numberOfElements : 0} of ${pageData ? pageData.totalElements : 0} item(s)`);
      containerEl.appendChild(info);
      return;
    }
    const info = UI.createElement('span', { style: 'font-size: var(--font-size-secondary); color: var(--text-secondary);' },
      `Page ${pageData.number + 1} of ${pageData.totalPages} (${pageData.totalElements} total)`);
    const controls = UI.createElement('div', { style: 'display: flex; gap: 0.5rem;' });
    const prevBtn = UI.createElement('button', {
      className: 'btn btn-secondary btn-sm',
      type: 'button',
      onclick: () => onPageChange(pageData.number - 1)
    }, 'Previous');
    prevBtn.disabled = pageData.first;

    const nextBtn = UI.createElement('button', {
      className: 'btn btn-secondary btn-sm',
      type: 'button',
      onclick: () => onPageChange(pageData.number + 1)
    }, 'Next');
    nextBtn.disabled = pageData.last;

    controls.append(prevBtn, nextBtn);
    containerEl.append(info, controls);
  }
};

