/* Local preview controls only. No API, storage, or application authentication. */
(() => {
  const auth = document.querySelector('#preview-auth');
  const feature = document.querySelector('#preview-feature');
  const result = document.querySelector('#prototype-result');
  const resultText = result.querySelector('p');
  let opener;
  function update() {
    const signedIn = auth.value === 'member';
    const enabled = feature.value === 'enabled';
    document.querySelectorAll('[data-guest]').forEach(el => { el.hidden = signedIn; });
    document.querySelectorAll('[data-member]').forEach(el => { el.hidden = !signedIn; });
    document.querySelectorAll('[data-enabled]').forEach(el => { el.hidden = !enabled; });
    document.querySelectorAll('[data-disabled]').forEach(el => { el.hidden = enabled; });
    document.querySelectorAll('[data-primary]').forEach(el => {
      el.textContent = signedIn ? '进入空间' : '注册';
      el.dataset.route = signedIn ? '/space/my' : '/register';
    });
    document.querySelectorAll('[data-companion-entry]').forEach(el => {
      el.hidden = !enabled;
      el.textContent = signedIn ? '进入伙伴' : '登录后查看伙伴';
      el.dataset.route = signedIn ? '/companion' : '/login?redirect=/companion';
    });
    document.querySelector('#preview-state').textContent =
      '设计预览：' + (signedIn ? '已登录' : '访客') + ' / ' + (enabled ? '伙伴开放' : '伙伴未开放');
  }
  function show(target, source) {
    opener = source;
    resultText.textContent = '静态原型：正式页面将前往 ' + target + '。这里不登录、不上传，也不请求接口。';
    result.hidden = false;
  }
  document.addEventListener('click', event => {
    const link = event.target.closest('[data-route]');
    if (!link) return;
    event.preventDefault();
    show(link.dataset.route, link);
  });
  document.querySelectorAll('[data-search]').forEach(form => form.addEventListener('submit', event => {
    event.preventDefault();
    const query = new FormData(form).get('q').trim();
    show('/gallery' + (query ? '?q=' + encodeURIComponent(query) : ''), form.querySelector('button'));
  }));
  function close() { result.hidden = true; opener?.focus(); }
  result.querySelector('button').addEventListener('click', close);
  document.addEventListener('keydown', event => { if (event.key === 'Escape' && !result.hidden) close(); });
  auth.addEventListener('change', update);
  feature.addEventListener('change', update);
  update();
})();
