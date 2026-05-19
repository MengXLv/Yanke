// ==================== 状态 ====================
const state = {
  token: localStorage.getItem('forum_token') || '',
  user: JSON.parse(localStorage.getItem('forum_user') || 'null'),
  captchaKey: 'ck-' + Date.now(),
  page: 1,
  size: 20
};

// ==================== 工具函数 ====================
function $(id) { return document.getElementById(id); }
function toast(msg, isError) {
  const t = $('toast'); t.textContent = msg; t.className = 'toast show' + (isError ? ' error' : '');
  setTimeout(() => t.className = 'toast', 2000);
}
function fmtTime(t) { if(!t) return ''; var d=new Date(t); return d.getFullYear()+'-'+(d.getMonth()+1)+'-'+d.getDate()+' '+String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0'); }
function fmtShort(t) { if(!t) return ''; var d=new Date(t); return (d.getMonth()+1)+'/'+d.getDate()+' '+String(d.getHours()).padStart(2,'0')+':'+String(d.getMinutes()).padStart(2,'0'); }
function showLoading() { $('loading').style.display = 'flex'; }
function hideLoading() { $('loading').style.display = 'none'; }

function api(path, opts) {
  opts = opts || {};
  const headers = { 'Content-Type': 'application/json' };
  if (state.token) headers['Authorization'] = 'Bearer ' + state.token;
  return fetch(path, { headers, ...opts }).then(r => r.json()).then(d => {
    if (d.code === 401) {
      logout();
      toast('登录已过期，请重新登录', true);
      showLogin();
      throw new Error('请先登录');
    }
    if (d.code !== 200) throw new Error(d.message || '请求失败');
    return d;
  });
}

// ==================== SSE 实时通信 ====================
var sseSource = null;

function connectSSE() {
  if (!state.token || !state.user) return;
  try {
    sseSource = new EventSource('/api/sse/connect?token=' + encodeURIComponent(state.token));
    sseSource.addEventListener('notification', function(e) {
      var data = JSON.parse(e.data);
      toast('🔔 ' + (data.content || '新通知'));
      updateBadgeCount();
    });
    sseSource.addEventListener('message', function(e) {
      var data = JSON.parse(e.data);
      toast('✉ 新私信');
      updateMsgCount();
    });
    sseSource.onerror = function() { /* SSE断开，轮询兜底 */ };
  } catch(e) { /* 浏览器不支持SSE，使用轮询兜底 */ }
}

function disconnectSSE() {
  if (sseSource) { try { sseSource.close(); } catch(e) {} sseSource = null; }
}

function updateBadgeCount() {
  if (!state.token) return;
  api('/api/notification/unread-count').then(function(d) {
    var cnt = d.data && d.data.count ? d.data.count : 0;
    $('unreadCount').textContent = cnt;
    $('unreadCount').style.display = cnt > 0 ? 'inline' : 'none';
  }).catch(function() {});
}

function updateMsgCount() {
  if (!state.token) return;
  api('/api/message/unread').then(function(d) {
    var cnt = d.data && d.data.count ? d.data.count : 0;
    $('msgCount').textContent = cnt;
    $('msgCount').style.display = cnt > 0 ? 'inline' : 'none';
  }).catch(function() {});
}

// 轮询兜底（每60s同步一次徽章数）
setInterval(function() {
  if (!sseSource || sseSource.readyState !== EventSource.OPEN) {
    updateBadgeCount();
    updateMsgCount();
  }
}, 60000);

function refreshNav() {
  if (state.token && state.user) {
    $('guestBtns').style.display = 'none';
    $('userBtns').style.display = 'flex';
    $('navUsername').textContent = state.user.username;
    $('notifyBadge').style.display = 'inline';
    $('msgBadge').style.display = 'inline';
    $('pointsBtn').style.display = 'inline';
    if (state.user && (state.user.role === 'admin' || state.user.role === 'moderator')) {
      $('adminBtns').style.display = 'inline';
    }
    updateBadgeCount();
    updateMsgCount();
    api('/api/points/me').then(r => {
      $('pointsSpan').textContent = r.data ? r.data.totalPoints : 0;
    }).catch(() => {});
  } else {
    $('guestBtns').style.display = 'inline';
    $('userBtns').style.display = 'none';
    $('notifyBadge').style.display = 'none';
    $('msgBadge').style.display = 'none';
    $('pointsBtn').style.display = 'none';
  }
}

// ==================== Toast提示 ====================
// (toast function is already defined above via the Utils section)

// ==================== 验证码 ====================
function refreshCaptcha(prefix) {
  state.captchaKey = 'ck-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8);
  api('/api/auth/captcha?uuid=' + state.captchaKey).then(d => {
    const img = prefix === 'login' ? $('loginCaptchaImg') : $('regCaptchaImg');
    img.src = d.data;
  }).catch(() => {});
}

// ==================== 登录/注册/退出 ====================
function showLogin() { refreshCaptcha('login'); $('loginModal').classList.add('active'); }
function showRegister() { refreshCaptcha('reg'); $('registerModal').classList.add('active'); }
function closeModal(id) { $(id).classList.remove('active'); }

function doLogin() {
  const body = {
    account: $('loginAccount').value,
    password: $('loginPassword').value,
    captcha: $('loginCaptcha').value,
    captchaKey: state.captchaKey
  };
  api('/api/auth/login', { method: 'POST', body: JSON.stringify(body) })
    .then(d => {
      state.token = d.data.token;
      state.user = d.data;
      localStorage.setItem('forum_token', state.token);
      localStorage.setItem('forum_user', JSON.stringify(state.user));
      closeModal('loginModal');
      refreshNav();
      connectSSE();
      showHome();
      toast('登录成功');
    }).catch(e => toast(e.message, true));
}

function doRegister() {
  const body = {
    username: $('regUsername').value,
    email: $('regEmail').value,
    password: $('regPassword').value,
    captcha: $('regCaptcha').value,
    captchaKey: state.captchaKey
  };
  api('/api/auth/register', { method: 'POST', body: JSON.stringify(body) })
    .then(() => { closeModal('registerModal'); toast('注册成功，请前往邮箱激活账户'); })
    .catch(e => toast(e.message, true));
}

function logout() {
  disconnectSSE();
  state.token = '';
  state.user = null;
  localStorage.removeItem('forum_token');
  localStorage.removeItem('forum_user');
  refreshNav();
  showHome();
  toast('已退出');
}

// ==================== 首页 ====================
function showHome(page) {
  page = page || 1; state.page = page;
  api('/api/post?page=' + page + '&size=' + state.size + '&orderBy=latest')
    .then(d => renderPostList(d.data, '最新帖子', page, showHome))
    .catch(e => toast(e.message, true));
}

function showHotPosts() {
  api('/api/post/hot?limit=20')
    .then(d => {
      let html = '<h2 style="margin-bottom:16px">🔥 热帖排行</h2>';
      if (!d.data || d.data.length === 0) {
        html += '<div class="empty"><h3>暂无热帖</h3></div>';
      } else {
        d.data.forEach((p, i) => {
          const rankCls = i < 3 ? ' r' + (i + 1) : '';
          html += '<div class="post-card" onclick="showPostDetail(' + p.id + ')">' +
            '<span class="hot-rank' + rankCls + '">' + (i + 1) + '</span>' +
            (p.isHot ? '<span class="tag-hot">热</span>' : '') +
            (p.isTop ? '<span class="tag-top">置顶</span>' : '') +
            '<span class="post-title">' + escHtml(p.title) + '</span>' +
            '<div class="post-summary">' + escHtml(p.summary || '') + '</div>' +
            '<div class="post-meta">' +
            '<span>' + (p.username || '用户#' + p.userId) + '</span>' +
            '<span>👁 ' + p.viewCount + '</span>' +
            '<span>👍 ' + p.likeCount + '</span>' +
            '<span>💬 ' + p.commentCount + '</span>' +
            '<span>' + fmtTime(p.createTime) + '</span>' +
            '</div></div>';
        });
      }
      $('mainContent').innerHTML = html;
    }).catch(e => toast(e.message, true));
}

function renderPostList(pageData, title, curPage, reloadFn) {
  let html = '<h2 style="margin-bottom:16px">' + title + '</h2>';
  if (!pageData.records || pageData.records.length === 0) {
    html += '<div class="empty"><h3>暂无帖子</h3><p>成为第一个发帖的人吧</p></div>';
  } else {
    pageData.records.forEach(p => {
      html += '<div class="post-card" onclick="showPostDetail(' + p.id + ')">' +
        (p.isHot ? '<span class="tag-hot">热</span>' : '') +
        (p.isTop ? '<span class="tag-top">置顶</span>' : '') +
        '<span class="post-title">' + escHtml(p.title) + '</span>' +
        '<div class="post-summary">' + escHtml(p.summary || '') + '</div>' +
        '<div class="post-meta">' +
        '<span>' + (p.username || '用户#' + p.userId) + '</span>' +
        '<span>👁 ' + p.viewCount + '</span>' +
        '<span>👍 ' + p.likeCount + '</span>' +
        '<span>💬 ' + p.commentCount + '</span>' +
        '<span>' + fmtTime(p.createTime) + '</span>' +
        '</div></div>';
    });
    if (pageData.total > state.size) {
      const totalPages = Math.ceil(pageData.total / state.size);
      html += '<div class="pagination">';
      for (let i = 1; i <= Math.min(totalPages, 10); i++) {
        html += '<button class="' + (i === curPage ? 'active' : '') + '" onclick="event.stopPropagation();(' + reloadFn.name + ')(' + i + ')">' + i + '</button>';
      }
      html += '</div>';
    }
  }
  $('mainContent').innerHTML = html;
}

// ==================== 帖子详情 ====================
function showPostDetail(postId) {
  api('/api/post/' + postId).then(d => {
    const p = d.data;
    let html = '<div class="detail-card">' +
      '<h1 class="detail-title">' + escHtml(p.title) + '</h1>' +
      '<div class="detail-meta">' +
      '<span>作者：<a href="#" onclick="event.stopPropagation();showUserProfile(' + p.userId + ')" style="color:#1a73e8">' + (p.username || '用户#' + p.userId) + '</a></span>' +
      (p.categoryName ? '<span>📂 ' + escHtml(p.categoryName) + '</span>' : '') +
      '<span>👁 ' + p.viewCount + '</span>' +
      '<span id="likeCount" style="cursor:pointer" title="点击查看点赞用户" onclick="event.stopPropagation();showLikeUsers(' + postId + ')">👍 ' + p.likeCount + '</span>' +
      '<span>💬 ' + p.commentCount + '</span>' +
      '<span>' + fmtTime(p.createTime) + '</span>' +
      '</div>' +
      '<div class="detail-content md-content">' + mdToHtml(p.content) + '</div>';

    // 操作按钮
    if (state.token && state.user) {
      var isMe = (state.user.id === p.userId);
      html += '<div style="margin-bottom:16px">' +
        '<button class="btn btn-outline btn-small" onclick="event.stopPropagation();toggleLike(' + postId + ')">👍 点赞</button> ' +
        '<button class="btn btn-outline btn-small" onclick="event.stopPropagation();showCommentBox(' + postId + ')">💬 评论</button> ';
      if (!isMe) {
        html += '<button class="btn btn-outline btn-small" onclick="event.stopPropagation();toggleFollow(' + p.userId + ',this)">👤 关注</button> ' +
          '<button class="btn btn-outline btn-small" onclick="event.stopPropagation();sendMsgTo(' + p.userId + ')">✉ 私信</button> ' +
          '<button class="btn btn-outline btn-small" onclick="event.stopPropagation();reportTarget(' + postId + ',1)" style="color:#999">🚩 举报</button> ';
      }
      if (isMe || (state.user && (state.user.role === 'admin' || state.user.role === 'moderator'))) {
        html += '<button class="btn btn-outline btn-small" onclick="event.stopPropagation();deletePost(' + postId + ')" style="color:#e74c3c">🗑 删除</button>';
      }
      html += '</div>';
    }

    // 评论区
    html += '<div class="comment-section"><h3>评论</h3><div id="commentList">';
    if (p.comments && p.comments.length > 0) {
      html += renderComments(p.comments);
    } else {
      html += '<p class="empty" style="padding:20px">暂无评论</p>';
    }
    html += '</div></div>';
    html += '<div id="commentBox" style="margin-top:12px;display:none">' +
      '<textarea id="commentText" rows="3" placeholder="写下你的评论..." style="width:100%;padding:10px;border:1px solid #ddd;border-radius:6px;resize:vertical"></textarea>' +
      '<button class="btn btn-primary" style="margin-top:8px" onclick="doComment(' + postId + ')">发表</button>' +
      '</div>';

    html += '</div>';
    $('mainContent').innerHTML = html;
  }).catch(e => toast(e.message, true));
}

function renderComments(comments) {
  let html = '';
  comments.forEach(c => {
    html += '<div class="comment-item">' +
      '<div class="author">' + (c.username || ('用户#' + c.userId)) + '</div>' +
      '<div class="text">' + escHtml(c.content) + '</div>' +
      '<div class="time">' + fmtTime(c.createTime);
    if (state.token) {
      html += ' <a href="#" onclick="event.preventDefault();replyTo(' + c.id + ',' + c.userId + ')" style="font-size:12px;color:#1a73e8">回复</a>';
      html += ' <a href="#" onclick="event.preventDefault();reportTarget(' + c.id + ',2)" style="font-size:12px;color:#999;margin-left:8px">🚩举报</a>';
      if (state.user && (state.user.id === c.userId || state.user.role === 'admin' || state.user.role === 'moderator')) {
        html += ' <a href="#" onclick="event.preventDefault();deleteComment(' + c.id + ')" style="font-size:12px;color:#e74c3c;margin-left:8px">删除</a>';
      }
    }
    html += '</div>';
    if (c.replies && c.replies.length > 0) {
      html += '<div class="comment-reply">' + renderComments(c.replies) + '</div>';
    }
    html += '</div>';
  });
  return html;
}

function replyTo(commentId, replyToUserId) {
  state.replyTo = commentId;
  state.replyToUser = replyToUserId;
  $('commentBox').style.display = 'block';
  $('commentText').placeholder = '回复评论...';
  $('commentText').focus();
}

function showCommentBox(postId) {
  $('commentBox').style.display = 'block';
}

function doComment(postId) {
  if (!state.token) { toast('请先登录', true); return; }
  const content = $('commentText').value;
  const body = {
    postId: postId,
    content: content,
    parentId: state.replyTo || null,
    replyToUserId: state.replyToUser || null
  };
  api('/api/comment', { method: 'POST', body: JSON.stringify(body) })
    .then(() => {
      state.replyTo = null;
      state.replyToUser = null;
      $('commentText').value = '';
      $('commentText').placeholder = '写下你的评论...';
      $('commentBox').style.display = 'none';
      toast('评论成功');
      showPostDetail(postId);
    })
    .catch(e => toast(e.message, true));
}

function toggleLike(postId) {
  if (!state.token) { toast('请先登录', true); return; }
  api('/api/social/like/' + postId, { method: 'POST' })
    .then(d => { toast(d.data.liked ? '已点赞' : '已取消点赞'); showPostDetail(postId); })
    .catch(e => toast(e.message, true));
}

function blockUser(userId) {
  if (!confirm('确定封禁该用户？封禁后该用户将无法登录。')) return;
  api('/api/admin/users/' + userId + '/status?status=2', { method: 'PUT' })
    .then(() => { toast('用户已封禁'); showProfile(); })
    .catch(e => toast(e.message, true));
}

function deletePost(postId) {
  if (!confirm('确定删除这篇帖子？此操作不可恢复。')) return;
  api('/api/post/' + postId, { method: 'DELETE' })
    .then(() => { toast('帖子已删除'); showHome(); })
    .catch(e => toast(e.message, true));
}

function deleteComment(commentId) {
  if (!confirm('确定删除这条评论？')) return;
  api('/api/comment/' + commentId, { method: 'DELETE' })
    .then(() => { toast('评论已删除'); var m = $('mainContent').innerHTML.match(/showPostDetail\((\d+)\)/); if (m) showPostDetail(parseInt(m[1])); })
    .catch(e => toast(e.message, true));
}

function showLikeUsers(postId) {
  api('/api/social/like/' + postId + '/users')
    .then(d => {
      if (!d.data || d.data.length === 0) {
        toast('暂无用户点赞');
      } else {
        var names = d.data.map(function(u) { return u.username || ('用户#' + u.userId); });
        toast('点赞用户：' + names.join(', '));
      }
    })
    .catch(e => toast(e.message, true));
}

// ==================== 搜索 ====================
function toggleSearch() { const s = $('searchBar'); s.style.display = s.style.display === 'none' ? 'flex' : 'none'; }
function doSearch() {
  const kw = $('searchInput').value;
  if (!kw) return;
  api('/api/search/posts?keyword=' + encodeURIComponent(kw) + '&page=1&size=20')
    .then(d => renderPostList(d.data, '搜索："' + escHtml(kw) + '"', 1, function(){ doSearch(); }))
    .catch(e => toast(e.message, true));
}

// ==================== 个人中心 ====================
function showProfile() {
  if (state.user) showUserProfile(state.user.id);
}

function showUserProfile(uid) {
  api('/api/user/' + uid).then(d => {
    var u = d.data;
    var html = '<div class="detail-card">' +
      '<h2>' + escHtml(u.username || '用户#' + u.id) + ' 的主页</h2>' +
      '<div style="margin:16px 0"><strong>简介：</strong>' + escHtml(u.bio || '暂无') + '</div>' +
      '<div style="margin:16px 0"><strong>角色：</strong>' + (u.role || 'user') + '</div>' +
      '<div style="margin:16px 0"><strong>注册时间：</strong>' + fmtTime(u.createTime) + '</div>';

    if (state.user && state.user.id !== u.id) {
      html += '<div style="margin:16px 0;display:flex;gap:8px">' +
        '<button class="btn btn-outline btn-small" onclick="toggleFollow(' + uid + ',this)">👤 关注</button>' +
        '<button class="btn btn-outline btn-small" onclick="sendMsgTo(' + uid + ')">✉ 私信</button>' +
        (state.user.role === 'admin' || state.user.role === 'moderator' ?
         '<button class="btn btn-outline btn-small" style="color:#e74c3c" onclick="blockUser(' + uid + ')">🚫 封禁</button>' : '') +
        '</div>';
    }

    if (state.user && state.user.id === u.id) {
      html += '<div style="margin:16px 0;display:flex;gap:8px">' +
        '<a href="#" onclick="event.preventDefault();showFollowing(' + uid + ')" class="btn btn-outline btn-small">关注</a>' +
        '<a href="#" onclick="event.preventDefault();showFans(' + uid + ')" class="btn btn-outline btn-small">粉丝</a>' +
        '</div>';
    }

    html += '</div>';

    // 用户帖子列表
    api('/api/user/' + uid + '/posts?page=1&size=20').then(r => {
      html += '<h3 style="margin-top:20px">TA的帖子</h3>';
      if (r.data.records && r.data.records.length > 0) {
        r.data.records.forEach(p => {
          html += '<div class="post-card" onclick="showPostDetail(' + p.id + ')" style="cursor:pointer">' +
            '<span class="post-title">' + escHtml(p.title) + '</span>' +
            '<div class="post-summary">' + escHtml(p.summary || '') + '</div>' +
            '<div class="post-meta"><span>👍 ' + p.likeCount + '</span><span>💬 ' + p.commentCount + '</span><span>' + fmtTime(p.createTime) + '</span></div>' +
            '</div>';
        });
      } else { html += '<div class="empty"><p>暂无帖子</p></div>'; }
      $('mainContent').innerHTML = html;
    });
  }).catch(e => toast(e.message, true));
}

// ==================== 发帖 ====================
function showCreatePost() {
  if (!state.token) { toast('请先登录', true); return; }
  // 加载分类列表
  api('/api/categories').then(r => {
    var cats = r.data || [];
    var catHtml = '<select id="postCategory" style="width:100%;padding:8px 12px;border:1px solid #ddd;border-radius:6px;margin-bottom:8px">';
    catHtml += '<option value="">请选择分类</option>';
    cats.forEach(function(c) { catHtml += '<option value="' + c.id + '">' + escHtml(c.name) + '</option>'; });
    catHtml += '</select>';
    document.getElementById('catSelectContainer').innerHTML = catHtml;
  }).catch(function() { document.getElementById('catSelectContainer').innerHTML = ''; });
  $('createPostModal').classList.add('active');
}

function doCreatePost() {
  var catEl = document.getElementById('postCategory');
  var catId = catEl && catEl.value ? parseInt(catEl.value) : null;
  const body = { title: $('postTitle').value, content: $('postContent').value, categoryId: catId };
  api('/api/post', { method: 'POST', body: JSON.stringify(body) })
    .then(d => { closeModal('createPostModal'); $('postTitle').value = ''; $('postContent').value = ''; toast('发帖成功'); showHome(); })
    .catch(e => toast(e.message, true));
}

// ==================== 点击模态框外部关闭 ====================
window.onclick = function(e) {
  if (e.target.classList.contains('modal')) e.target.classList.remove('active');
};

// ==================== 图片上传 ====================
function uploadPostImage() {
  var file = document.getElementById('imageFile').files[0];
  if (!file) return;
  if (file.size > 10 * 1024 * 1024) { toast('图片最大10MB', true); return; }
  var formData = new FormData();
  formData.append('file', file);
  var hint = document.getElementById('uploadHint');
  hint.textContent = '上传中...';
  fetch('/api/upload/image', { method: 'POST', headers: { 'Authorization': 'Bearer ' + state.token }, body: formData })
    .then(function(r) { return r.json(); })
    .then(function(d) {
      hint.textContent = '上传成功';
      if (d.code === 200 && d.data && d.data.url) {
        var imgMd = '\n![' + (file.name || '图片') + '](' + d.data.url + ')\n';
        document.getElementById('postContent').value += imgMd;
      } else {
        toast(d.message || '上传失败', true);
      }
    })
    .catch(function() { hint.textContent = '上传失败'; toast('上传失败', true); })
    .finally(function() { document.getElementById('imageFile').value = ''; });
}

// ==================== Markdown 工具栏 ====================
function insertMd(before, after) {
  var ta = $('postContent');
  var start = ta.selectionStart, end = ta.selectionEnd;
  var text = ta.value;
  var sel = text.substring(start, end);
  ta.value = text.substring(0, start) + before + sel + after + text.substring(end);
  ta.focus();
  ta.selectionStart = start + before.length;
  ta.selectionEnd = start + before.length + sel.length;
  if (document.getElementById('mdPreview').style.display !== 'none') renderPreview();
}

var previewOn = false;
function togglePreview() {
  previewOn = !previewOn;
  var btn = document.getElementById('previewBtn');
  btn.textContent = previewOn ? '\u{1F441} 编辑' : '\u{1F441} 预览';
  $('postContent').style.display = previewOn ? 'none' : '';
  document.getElementById('mdPreview').style.display = previewOn ? '' : 'none';
  if (previewOn) renderPreview();
}

function renderPreview() {
  document.getElementById('mdPreview').innerHTML = mdToHtml($('postContent').value);
}

// 简易 Markdown → HTML（无外部依赖）
function mdToHtml(md) {
  if (!md) return '';
  var html = escHtml(md);
  // 代码块（```...```）
  html = html.replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>');
  // 行内代码 `...`
  html = html.replace(/`([^`]+)`/g, '<code>$1</code>');
  // 图片 ![alt](url)
  html = html.replace(/!\[([^\]]*)\]\(([^)]+)\)/g, '<img src="$2" alt="$1">');
  // 链接 [text](url)
  html = html.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');
  // 标题
  html = html.replace(/^### (.+)$/gm, '<h3>$1</h3>');
  html = html.replace(/^## (.+)$/gm, '<h2>$1</h2>');
  html = html.replace(/^# (.+)$/gm, '<h1>$1</h1>');
  // 粗体+斜体
  html = html.replace(/\*\*\*(.+?)\*\*\*/g, '<b><i>$1</i></b>');
  html = html.replace(/\*\*(.+?)\*\*/g, '<b>$1</b>');
  html = html.replace(/\*(.+?)\*/g, '<i>$1</i>');
  // 删除线
  html = html.replace(/~~(.+?)~~/g, '<s>$1</s>');
  // 引用
  html = html.replace(/^&gt; (.+)$/gm, '<blockquote>$1</blockquote>');
  // 无序列表
  html = html.replace(/^- (.+)$/gm, '<li>$1</li>');
  html = html.replace(/(<li>.*<\/li>)/s, '<ul>$1</ul>');
  // 分隔线
  html = html.replace(/^---$/gm, '<hr>');
  // 段落（连续换行）
  html = html.replace(/\n\n/g, '</p><p>');
  html = '<p>' + html + '</p>';
  // 清理空段落
  html = html.replace(/<p><\/p>/g, '');
  html = html.replace(/<p>(<h[123]|<ul|<blockquote|<pre|<hr)/g, '$1');
  return html;
}

// ==================== HTML 转义 ====================
function escHtml(s) {
  if (!s) return '';
  const div = document.createElement('div');
  div.textContent = s;
  return div.innerHTML;
}

// ==================== 草稿箱 ====================
function showDrafts() {
  if (!state.token) { toast('请先登录', true); return; }
  api('/api/post/draft?page=1&size=50').then(d => {
    let html = '<h2>📝 草稿箱</h2>';
    if (!d.data.records || d.data.records.length === 0) {
      html += '<div class="empty"><h3>暂无草稿</h3></div>';
    } else {
      d.data.records.forEach(p => {
        html += '<div class="post-card">' +
          '<span class="post-title">' + escHtml(p.title || '无标题') + '</span>' +
          '<div class="post-summary">' + escHtml(p.summary || '') + '</div>' +
          '<div style="margin-top:8px">' +
          '<button class="btn btn-primary btn-small" onclick="publishDraft(' + p.id + ')">发布</button> ' +
          '<button class="btn btn-outline btn-small" onclick="editDraft(' + p.id + ')">编辑</button>' +
          '</div></div>';
      });
    }
    $('mainContent').innerHTML = html;
  }).catch(e => toast(e.message, true));
}

function publishDraft(draftId) {
  if (!confirm('确定发布此草稿？')) return;
  api('/api/post/draft/' + draftId + '/publish', { method: 'POST' })
    .then(() => { toast('发布成功'); showHome(); })
    .catch(e => toast(e.message, true));
}

// ==================== 好友 ====================
function showFriends() {
  if (!state.token) { toast('请先登录', true); return; }
  api('/api/social/friends?page=1&size=50').then(d => {
    let html = '<h2>👥 我的好友</h2>';
    if (!d.data.records || d.data.records.length === 0) {
      html += '<div class="empty"><h3>暂无好友</h3><p>互相关注后即成为好友</p></div>';
    } else {
      d.data.records.forEach(u => {
        html += '<div class="friend-item">' +
          '<div class="friend-avatar"></div>' +
          '<div class="friend-name">' + escHtml(u.username) + '</div>' +
          '<div style="color:#999;font-size:13px;margin-left:8px">' + escHtml(u.bio || '') + '</div>' +
          '</div>';
      });
    }
    $('mainContent').innerHTML = html;
  }).catch(e => toast(e.message, true));
}

// ==================== 通知 ====================
function showNotifications() {
  if (!state.token) return;
  api('/api/notification/unread?page=1&size=50').then(d => {
    let html = '<h2>🔔 消息通知</h2>';
    if (!d.data || d.data.length === 0) {
      html += '<div class="empty"><h3>暂无新通知</h3></div>';
    } else {
      d.data.forEach(n => {
        html += '<div class="post-card" style="cursor:default;padding:12px">' +
          '<div style="font-size:14px">' + escHtml(n.content) + '</div>' +
          '<div style="font-size:12px;color:#999;margin-top:4px">' + fmtTime(n.createTime) + '</div>' +
          '</div>';
      });
      html += '<button class="btn btn-outline" onclick="markAllRead()">全部已读</button>';
    }
    $('mainContent').innerHTML = html;
  }).catch(() => {});
}

function markAllRead() {
  api('/api/notification/read-all', { method: 'PUT' })
    .then(() => { toast('已全部标记已读'); updateBadgeCount(); showNotifications(); })
    .catch(e => toast(e.message, true));
}

// ==================== 发帖时可选存草稿 ====================
function doCreatePost() {
  const body = { title: $('postTitle').value, content: $('postContent').value };
  api('/api/post', { method: 'POST', body: JSON.stringify(body) })
    .then(d => { closeModal('createPostModal'); $('postTitle').value = ''; $('postContent').value = ''; toast('发帖成功'); showHome(); })
    .catch(e => { toast(e.message, true); });
}

function saveAsDraft() {
  const title = $('postTitle').value || '未命名草稿';
  const content = $('postContent').value;
  if (!content) { toast('请先输入内容', true); return; }
  api('/api/post/draft', { method: 'POST', body: JSON.stringify({ title: title, content: content }) })
    .then(() => { closeModal('createPostModal'); $('postTitle').value = ''; $('postContent').value = ''; toast('草稿已保存'); })
    .catch(e => toast(e.message, true));
}

// ==================== 关注/取关 ====================
function toggleFollow(targetUserId, btn) {
  if (!state.token) { toast('请先登录', true); return; }
  api('/api/social/follow/' + targetUserId, { method: 'POST' })
    .then(d => { btn.textContent = d.data.following ? '👤 已关注' : '👤 关注'; toast(d.data.following ? '已关注' : '已取消'); })
    .catch(e => toast(e.message, true));
}

function showFollowing(userId) {
  userId = userId || (state.user ? state.user.id : null);
  if (!userId) return;
  api('/api/social/following/' + userId).then(d => {
    var html = '<h2>👤 关注列表</h2>';
    if (!d.data || d.data.length === 0) html += '<div class="empty"><p>暂未关注任何人</p></div>';
    else d.data.forEach(function(u) {
      html += '<div class="friend-item">' +
        '<span style="font-weight:600">' + escHtml(u.username) + '</span>' +
        ' <a href="#" onclick="event.preventDefault();sendMsgTo(' + u.userId + ')" style="font-size:12px">私信</a>' +
        '</div>';
    });
    $('mainContent').innerHTML = html;
  });
}

function showFans(userId) {
  userId = userId || (state.user ? state.user.id : null);
  api('/api/social/fans/' + userId).then(d => {
    var html = '<h2>👥 粉丝列表</h2>';
    if (!d.data || d.data.length === 0) html += '<div class="empty"><p>暂无粉丝</p></div>';
    else d.data.forEach(function(u) {
      html += '<div class="friend-item"><span style="font-weight:600">' + escHtml(u.username) + '</span></div>';
    });
    $('mainContent').innerHTML = html;
  });
}

// ==================== 私信 ====================
function sendMsgTo(receiverId) {
  if (!state.token) { toast('请先登录', true); return; }
  var content = prompt('输入私信内容：');
  if (!content) return;
  api('/api/message', { method: 'POST', body: JSON.stringify({ receiverId: receiverId, content: content }) })
    .then(() => toast('发送成功'))
    .catch(e => toast(e.message, true));
}

function showConversations() {
  if (!state.token) { toast('请先登录', true); return; }
  api('/api/message/conversations').then(d => {
    var html = '<h2>✉ 私信</h2>';
    if (!d.data || d.data.length === 0) html += '<div class="empty"><p>暂无消息</p></div>';
    else {
      d.data.forEach(function(c) {
        html += '<div class="post-card" onclick="showChatWith(' + c.userId + ')" style="cursor:pointer">' +
          '<div style="display:flex;justify-content:space-between">' +
          '<strong>' + escHtml(c.username) + '</strong>' +
          '<span style="font-size:12px;color:#999">' + fmtTime(c.lastTime) + '</span>' +
          '</div>' +
          '<div style="margin-top:4px;color:#666">' + escHtml(c.lastContent) +
          (c.unread > 0 ? ' <span style="color:#e74c3c">(' + c.unread + '条新消息)</span>' : '') +
          '</div></div>';
      });
    }
    $('mainContent').innerHTML = html;
  }).catch(e => toast(e.message, true));
}

function showChatWith(otherUserId) {
  if (!state.token) return;
  api('/api/message/with/' + otherUserId).then(d => {
    api('/api/message/read/' + otherUserId, { method: 'PUT' }).then(() => updateMsgCount());
    var html = '<h2>💬 对话</h2><div style="max-height:400px;overflow-y:auto">';
    if (!d.data || d.data.length === 0) html += '<p class="empty">暂无消息</p>';
    else d.data.forEach(function(m) {
      var isMe = (m.senderId === state.user.id);
      html += '<div style="padding:6px 0;text-align:' + (isMe ? 'right' : 'left') + '">' +
        '<span style="display:inline-block;padding:8px 12px;border-radius:10px;max-width:70%;' +
        (isMe ? 'background:#1a73e8;color:#fff' : 'background:#f0f0f0;color:#333') + '">' +
        escHtml(m.content) + '</span>' +
        '<div style="font-size:11px;color:#aaa;margin-top:2px">' + fmtTime(m.createTime) + '</div>' +
        '</div>';
    });
    html += '</div><div style="display:flex;gap:8px;margin-top:10px">' +
      '<input id="chatInput" style="flex:1;padding:8px 12px;border:1px solid #ddd;border-radius:6px" placeholder="输入消息...">' +
      '<button class="btn btn-primary" onclick="sendInChat(' + otherUserId + ')">发送</button>' +
      '</div>';
    $('mainContent').innerHTML = html;
  }).catch(e => toast(e.message, true));
}

function sendInChat(otherUserId) {
  var content = $('chatInput').value;
  if (!content) return;
  api('/api/message', { method: 'POST', body: JSON.stringify({ receiverId: otherUserId, content: content }) })
    .then(() => { $('chatInput').value = ''; showChatWith(otherUserId); })
    .catch(e => toast(e.message, true));
}

// ==================== 秒杀 ====================
function showSeckillPage(){api('/api/points/seckill').then(r=>{var items=r.data||[];var h='<h2>⚡ 积分秒杀</h2>';if(items.length===0){h+='<div class="empty"><h3>暂无秒杀活动</h3><p>敬请期待下一场</p></div>';}items.forEach(function(item){var now=new Date();var start=new Date(item.seckillStart);var end=new Date(item.seckillEnd);var started=now>=start;var ended=now>=end;var label='';var btn='';var color='';if(!started){label='⏳ 即将开始 '+fmtShort(item.seckillStart);btn='<button class="btn btn-outline btn-small" disabled>敬请期待</button>';color='#ff9800';}else if(!ended){label='🔥 秒杀中！原价'+item.price+'积分';btn='<button class="btn btn-primary btn-small" onclick="doSeckill('+item.id+')">秒杀价 '+item.seckillPrice+'积分</button>';color='#e74c3c';}else{label='已结束';btn='<button class="btn btn-outline btn-small" disabled>已结束</button>';color='#999';}h+='<div class="post-card"><div style="display:flex;justify-content:space-between;align-items:center"><div><strong>'+escHtml(item.name)+'</strong><div style="font-size:13px;color:'+color+'">'+label+'</div><div style="font-size:12px;color:#999">'+escHtml(item.description||'')+'</div></div><div>'+btn+'</div></div></div>';});$('mainContent').innerHTML=h;}).catch(e=>toast(e.message,true));}
function doSeckill(itemId){if(!state.token){toast('请先登录',true);return;}if(!confirm('确认参与秒杀？'))return;api('/api/points/redeem/'+itemId,{method:'POST'}).then(()=>{toast('秒杀成功！');showSeckillPage();}).catch(e=>toast(e.message,true));}

// ==================== 积分 ====================
function showPointsPage() {
  if (!state.token) return;
  api('/api/points/me').then(r => {
    var p = r.data || {};
    var html = '<div class="detail-card">' +
      '<h2>💰 我的积分：' + (p.totalPoints || 0) + '</h2>' +
      '<button class="btn btn-primary" onclick="doCheckin()">📅 每日签到 (+1)</button>' +
      '<p style="color:#999;font-size:12px;margin-top:4px">' + (p.lastCheckin ? '上次签到：' + p.lastCheckin : '今天还没签到') + '</p>' +
      '</div>';

    // 签到日历
    api('/api/points/checkin-history').then(r3 => {
      state.checkinDates = {};
      if (r3.data) r3.data.forEach(function(d) { state.checkinDates[d] = true; });
      setTimeout(function(){renderCalendar();},100);
      html += '<h3 style="margin-top:16px">📅 签到日历</h3>';
      html += '<div style="text-align:center">';
      html += '<button onclick="event.preventDefault();calPrevMonth()" style="border:none;background:none;cursor:pointer;font-size:16px">◀</button> ';
      html += '<span id="calTitle" style="font-weight:600;margin:0 12px"></span> ';
      html += '<button onclick="event.preventDefault();calNextMonth()" style="border:none;background:none;cursor:pointer;font-size:16px">▶</button>';
      html += '</div>';
      html += '<div id="calGrid" style="display:grid;grid-template-columns:repeat(7,1fr);gap:2px;text-align:center;font-size:12px;margin-top:8px"></div>';
    // 积分排行

    // 积分排行
    api('/api/points/top?limit=10').then(r2 => {
      html += '<h3 style="margin-top:16px">🏆 积分排行</h3>';
      if (r2.data && r2.data.length > 0) {
        r2.data.forEach(function(u, i) {
          html += '<div class="friend-item">' +
            '<span style="font-weight:600;margin-right:8px">#' + (i+1) + '</span>' +
            '<span>' + escHtml(u.username || '用户#' + u.userId) + '</span>' +
            '<span style="margin-left:auto;color:#1a73e8">💰 ' + u.points + '</span>' +
            '</div>';
        });
      }
      html += '<h3 style="margin-top:16px">🎁 积分商城</h3><div id="shopList" style="color:#999;padding:12px">加载中...</div>';

      $('mainContent').innerHTML = html;

      // 加载商城商品列表
      api('/api/points/shop').then(r4 => {
        var shopHtml = '';
        if (r4.data && r4.data.length > 0) {
          r4.data.forEach(function(item) {
            shopHtml += '<div class="post-card" style="display:flex;justify-content:space-between;align-items:center">' +
              '<div><strong>' + escHtml(item.name) + '</strong>' +
              '<div style="font-size:12px;color:#999">' + escHtml(item.description || '') + '</div></div>' +
              '<div style="text-align:right"><span style="font-weight:600;color:#1a73e8">' + item.price + '积分</span>' +
              (item.stock > 0 ? '<div style="font-size:11px;color:#999">库存：' + (item.stock - item.sold) + '</div>' : '') +
              '<button class="btn btn-outline btn-small" onclick="redeemItem(' + item.id + ')" style="margin-left:12px">兑换</button></div>' +
              '</div>';
          });
        } else {
          shopHtml = '<p class="empty">暂无商品</p>';
        }
        document.getElementById('shopList').innerHTML = shopHtml;
      }).catch(function() { document.getElementById('shopList').innerHTML = '<p class="empty">加载失败</p>'; });
    });
    });
  }).catch(e => toast(e.message, true));
}

function doCheckin() {
  api('/api/points/checkin', { method: 'POST' })
    .then(r => { toast('签到成功！积分+1，当前：' + r.data); showPointsPage(); })
    .catch(e => toast(e.message, true));
}

function redeemItem(itemId) {
  api('/api/points/redeem/' + itemId, { method: 'POST' })
    .then(() => { toast('兑换成功'); showPointsPage(); })
    .catch(e => toast(e.message, true));
}

// ==================== 初始化 ====================
document.addEventListener('DOMContentLoaded', function() {
  refreshNav();
  if (state.token && state.user) connectSSE();
  showHome();
});
function calPrevMonth(){state.calMonth--;if(state.calMonth<1){state.calMonth=12;state.calYear--;}renderCalendar();}
function calNextMonth(){state.calMonth++;if(state.calMonth>12){state.calMonth=1;state.calYear++;}renderCalendar();}
function renderCalendar(){var el=document.getElementById('calTitle');if(!el)return;el.textContent=state.calYear+'年'+state.calMonth+'月';var grid=document.getElementById('calGrid');if(!grid)return;var h='<span style="color:#999">一</span><span style="color:#999">二</span><span style="color:#999">三</span><span style="color:#999">四</span><span style="color:#999">五</span><span style="color:#e74c3c">六</span><span style="color:#e74c3c">日</span>';var fd=new Date(state.calYear,state.calMonth-1,1).getDay()||7;var dim=new Date(state.calYear,state.calMonth,0).getDate();for(var i=1;i<fd;i++)h+='<span></span>';var td=new Date().toISOString().slice(0,10);for(var d=1;d<=dim;d++){var k=state.calYear+'-'+String(state.calMonth).padStart(2,'0')+'-'+String(d).padStart(2,'0');var c=state.checkinDates[k];var it=(k===td)?'border:2px solid #1a73e8;':'';var bg=c?'#1a73e8':'#f5f5f5';var cl=c?'white':'#999';h+='<span style="background:'+bg+';'+it+'border-radius:4px;padding:4px 2px;color:'+cl+'">'+d+'</span>';}grid.innerHTML=h;}
state.calYear=new Date().getFullYear();state.calMonth=new Date().getMonth()+1;

// ==================== 举报 ====================
function reportTarget(targetId, targetType) {
  if (!state.token) { toast('请先登录', true); return; }
  var reason = prompt('请输入举报原因：');
  if (!reason) return;
  api('/api/report', { method: 'POST', body: JSON.stringify({ targetId: targetId, targetType: targetType, reason: reason }) })
    .then(function() { toast('举报已提交，管理员将尽快处理'); })
    .catch(function(e) { toast(e.message, true); });
}

// ==================== 管理后台 ====================
function showAdminPage() {
  if (!state.token || (state.user.role !== 'admin' && state.user.role !== 'moderator')) { toast('无权访问', true); return; }
  api('/api/admin/dashboard').then(function(d) {
    var db = d.data || {};
    var html = '<h2>📊 管理面板</h2>' +
      '<div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-bottom:20px">' +
      '<div class="post-card" style="text-align:center"><strong>今日UV</strong><br><span style="font-size:24px;color:#1a73e8">' + (db.todayUV || 0) + '</span></div>' +
      '<div class="post-card" style="text-align:center"><strong>今日DAU</strong><br><span style="font-size:24px;color:#1a73e8">' + (db.todayDAU || 0) + '</span></div>' +
      '<div class="post-card" style="text-align:center"><strong>总用户</strong><br><span style="font-size:24px">' + (db.totalUsers || 0) + '</span></div>' +
      '<div class="post-card" style="text-align:center"><strong>总帖子</strong><br><span style="font-size:24px">' + (db.totalPosts || 0) + '</span></div>' +
      '</div>' +
      '<div style="display:flex;gap:10px;margin-bottom:16px">' +
      '<button class="btn btn-outline btn-small" onclick="showAdminCategories()">📂 分类管理</button>' +
      '<button class="btn btn-outline btn-small" onclick="showAdminReports()" style="color:' + (db.pendingReports > 0 ? '#e74c3c' : '#999') + '">🚩 举报处理 (' + (db.pendingReports || 0) + ')</button>' +
      '</div>';
    $('mainContent').innerHTML = html;
  }).catch(function(e) { toast(e.message, true); });
}

// ==================== 分类管理 ====================
function showAdminCategories() {
  api('/api/admin/categories').then(function(r) {
    var cats = r.data || [];
    var html = '<h2>📂 分类管理</h2>' +
      '<div style="margin-bottom:12px"><input id="newCatName" placeholder="分类名称" style="padding:6px 10px;border:1px solid #ddd;border-radius:4px;margin-right:8px">' +
      '<input id="newCatDesc" placeholder="描述(可选)" style="padding:6px 10px;border:1px solid #ddd;border-radius:4px;margin-right:8px;width:200px">' +
      '<button class="btn btn-primary btn-small" onclick="createCategory()">新增</button></div>';
    cats.forEach(function(c) {
      html += '<div class="post-card" style="display:flex;justify-content:space-between;align-items:center">' +
        '<div><strong>' + escHtml(c.name) + '</strong>' +
        '<span style="font-size:12px;color:#999;margin-left:8px">排序:' + (c.sortOrder || 0) + ' ' + (c.status === 1 ? '✅启用' : '❌禁用') + '</span>' +
        '<div style="font-size:12px;color:#999">' + escHtml(c.description || '') + '</div></div>' +
        '<div><button class="btn btn-outline btn-small" onclick="toggleCategory(' + c.id + ',' + (c.status === 1 ? 0 : 1) + ')">' + (c.status === 1 ? '禁用' : '启用') + '</button> ' +
        '<button class="btn btn-outline btn-small" onclick="deleteCategory(' + c.id + ')" style="color:#e74c3c">删除</button></div>' +
        '</div>';
    });
    html += '<button class="btn btn-outline btn-small" onclick="showAdminPage()" style="margin-top:12px">← 返回</button>';
    $('mainContent').innerHTML = html;
  });
}

function createCategory() {
  var name = document.getElementById('newCatName').value;
  if (!name) { toast('请输入分类名称', true); return; }
  api('/api/admin/categories', { method: 'POST', body: JSON.stringify({ name: name, description: document.getElementById('newCatDesc').value, status: 1 }) })
    .then(function() { toast('分类已创建'); showAdminCategories(); })
    .catch(function(e) { toast(e.message, true); });
}

function toggleCategory(id, newStatus) {
  api('/api/admin/categories/' + id, { method: 'PUT', body: JSON.stringify({ status: newStatus }) })
    .then(function() { toast('已更新'); showAdminCategories(); })
    .catch(function(e) { toast(e.message, true); });
}

function deleteCategory(id) {
  if (!confirm('确定删除此分类？')) return;
  api('/api/admin/categories/' + id, { method: 'DELETE' })
    .then(function() { toast('已删除'); showAdminCategories(); })
    .catch(function(e) { toast(e.message, true); });
}

// ==================== 举报处理 ====================
function showAdminReports() {
  api('/api/admin/reports?page=1&size=50').then(function(r) {
    var reports = r.data.records || [];
    var html = '<h2>🚩 举报处理</h2>';
    if (reports.length === 0) {
      html += '<div class="empty"><p>暂无待处理举报</p></div>';
    } else {
      reports.forEach(function(rp) {
        html += '<div class="post-card">' +
          '<div style="font-size:12px;color:#999">举报#' + rp.id + ' | 目标:' + (rp.targetType === 1 ? '帖子' : '评论') + '#' + rp.targetId + ' | ' + fmtTime(rp.createTime) + '</div>' +
          '<div style="margin:8px 0">' + escHtml(rp.reason) + '</div>' +
          '<div style="display:flex;gap:8px">' +
          '<button class="btn btn-primary btn-small" onclick="handleReport(' + rp.id + ',1)">✓ 通过处理</button>' +
          '<button class="btn btn-outline btn-small" onclick="handleReport(' + rp.id + ',2)" style="color:#e74c3c">✗ 驳回</button>' +
          '</div></div>';
      });
    }
    html += '<button class="btn btn-outline btn-small" onclick="showAdminPage()" style="margin-top:12px">← 返回</button>';
    $('mainContent').innerHTML = html;
  });
}

function handleReport(reportId, status) {
  var note = prompt(status === 1 ? '处理备注(可选)：' : '驳回原因(可选)：');
  api('/api/admin/reports/' + reportId, { method: 'PUT', body: JSON.stringify({ status: status, note: note || '' }) })
    .then(function() { toast('已处理'); showAdminReports(); })
    .catch(function(e) { toast(e.message, true); });
}
