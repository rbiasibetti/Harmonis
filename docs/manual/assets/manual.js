/* Manual do Harmonis — navegação, sumário da página, busca, tema e zoom de imagens. */
(function () {
  'use strict';

  // Estrutura do manual (ordem = ordem de leitura e do "anterior/próximo").
  var NAV = [
    { group: 'Comece por aqui', items: [
      ['index.html', 'Início'],
      ['01-visao-geral.html', 'Visão geral e conceitos'],
      ['02-primeiros-passos.html', 'Primeiros passos'],
    ]},
    { group: 'Administração', items: [
      ['03-usuarios-papeis.html', 'Usuários, papéis e permissões'],
    ]},
    { group: 'Formulários e processos', items: [
      ['04-formularios.html', 'Construtor de formulários'],
      ['05-preenchimento-respostas.html', 'Preenchimento e respostas'],
      ['06-fluxo-aprovacao.html', 'Fluxo de aprovação'],
      ['07-processos.html', 'Processos e aprovações'],
      ['16-modelos-dados.html', 'Modelos de dados'],
    ]},
    { group: 'Conteúdo', items: [
      ['08-documentos.html', 'Documentos (GED)'],
      ['09-portal.html', 'Portal e páginas'],
    ]},
    { group: 'Integrações', items: [
      ['10-integracoes.html', 'Integrações (webhooks)'],
      ['11-logs-integracao.html', 'Logs de integração'],
    ]},
    { group: 'Consulta', items: [
      ['12-permissoes.html', 'Matriz de permissões'],
      ['13-glossario.html', 'Glossário'],
      ['14-faq.html', 'Dúvidas e solução de problemas'],
      ['15-implantacao.html', 'Implantação (para TI)'],
    ]},
  ];

  var body = document.body;
  var current = body.getAttribute('data-page') || location.pathname.split('/').pop() || 'index.html';
  var flat = [];
  NAV.forEach(function (g) { g.items.forEach(function (i) { flat.push(i); }); });

  // ----- tema -----
  var KEY = 'harmonis.manual.theme';
  function applyTheme(t) { document.documentElement.setAttribute('data-theme', t); }
  var saved = null;
  try { saved = localStorage.getItem(KEY); } catch (e) {}
  applyTheme(saved || (window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));

  // ----- casca -----
  var main = document.querySelector('main') || document.querySelector('article');
  var article = main.tagName === 'ARTICLE' ? main : main.querySelector('article');
  var title = (article.querySelector('h1') || {}).textContent || document.title;

  var top = document.createElement('header');
  top.className = 'topbar';
  top.innerHTML =
    '<button class="icon-btn menu-btn" aria-label="Menu" id="menuBtn">☰</button>' +
    '<a class="brand" href="index.html"><span class="logo">H</span><span>Harmonis <span class="sub">Manual do usuário</span></span></a>' +
    '<span class="spacer"></span>' +
    '<div class="search"><input id="q" type="search" placeholder="Buscar no manual…" autocomplete="off" aria-label="Buscar">' +
    '<div class="results" id="results"></div></div>' +
    '<button class="icon-btn" id="themeBtn" aria-label="Alternar tema">◐</button>';

  var sidebar = document.createElement('nav');
  sidebar.className = 'sidebar';
  sidebar.setAttribute('aria-label', 'Navegação do manual');
  NAV.forEach(function (g) {
    var h = document.createElement('h4'); h.textContent = g.group; sidebar.appendChild(h);
    g.items.forEach(function (it) {
      var a = document.createElement('a'); a.href = it[0]; a.textContent = it[1];
      if (it[0] === current) a.className = 'active';
      sidebar.appendChild(a);
    });
  });

  var shell = document.createElement('div');
  shell.className = 'shell';
  var content = document.createElement('div');
  content.className = 'content';
  main.parentNode.insertBefore(shell, main);
  shell.appendChild(sidebar);
  shell.appendChild(content);
  content.appendChild(main);

  // sumario da pagina (h2/h3)
  var heads = [].slice.call(article.querySelectorAll('h2[id], h3[id]'));
  var toc = document.createElement('aside');
  toc.className = 'toc';
  if (heads.length > 1) {
    toc.innerHTML = '<b>Nesta página</b>';
    heads.forEach(function (h) {
      var a = document.createElement('a');
      a.href = '#' + h.id; a.textContent = h.textContent;
      if (h.tagName === 'H3') a.className = 'sub';
      toc.appendChild(a);
    });
  }
  shell.appendChild(toc);

  var scrim = document.createElement('div'); scrim.className = 'scrim';
  document.body.insertBefore(top, shell);
  document.body.appendChild(scrim);

  // anterior / proximo
  var idx = flat.findIndex(function (i) { return i[0] === current; });
  if (idx >= 0) {
    var pager = document.createElement('nav');
    pager.className = 'pager';
    if (idx > 0) pager.innerHTML += '<a class="prev" href="' + flat[idx - 1][0] + '"><small>← Anterior</small>' + flat[idx - 1][1] + '</a>';
    else pager.innerHTML += '<span style="flex:1"></span>';
    if (idx < flat.length - 1) pager.innerHTML += '<a class="next" href="' + flat[idx + 1][0] + '"><small>Próximo →</small>' + flat[idx + 1][1] + '</a>';
    article.appendChild(pager);
  }

  // ----- menu movel e tema -----
  document.getElementById('menuBtn').onclick = function () { body.classList.toggle('menu-open'); };
  scrim.onclick = function () { body.classList.remove('menu-open'); };
  document.getElementById('themeBtn').onclick = function () {
    var next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    applyTheme(next);
    try { localStorage.setItem(KEY, next); } catch (e) {}
  };

  // ----- destaque do sumario ao rolar -----
  if (heads.length > 1 && 'IntersectionObserver' in window) {
    var links = {};
    [].forEach.call(toc.querySelectorAll('a'), function (a) { links[a.getAttribute('href').slice(1)] = a; });
    var io = new IntersectionObserver(function (entries) {
      entries.forEach(function (e) {
        if (e.isIntersecting) {
          [].forEach.call(toc.querySelectorAll('a.active'), function (a) { a.classList.remove('active'); });
          if (links[e.target.id]) links[e.target.id].classList.add('active');
        }
      });
    }, { rootMargin: '-70px 0px -70% 0px' });
    heads.forEach(function (h) { io.observe(h); });
  }

  // ----- zoom de imagens -----
  var lb = document.createElement('div');
  lb.className = 'lightbox';
  lb.innerHTML = '<img alt="">';
  document.body.appendChild(lb);
  lb.onclick = function () { lb.classList.remove('open'); };
  document.addEventListener('keydown', function (e) { if (e.key === 'Escape') lb.classList.remove('open'); });
  [].forEach.call(document.querySelectorAll('a.zoom'), function (a) {
    a.addEventListener('click', function (e) {
      e.preventDefault();
      var img = lb.querySelector('img');
      img.src = a.getAttribute('href'); img.alt = (a.querySelector('img') || {}).alt || '';
      lb.classList.add('open');
    });
  });

  // ----- busca -----
  var q = document.getElementById('q');
  var box = document.getElementById('results');
  function esc(s) { return s.replace(/[&<>"]/g, function (c) { return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]; }); }
  function norm(s) { return s.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, ''); }
  function search(term) {
    var data = window.MANUAL_INDEX || [];
    var words = norm(term).split(/\s+/).filter(function (w) { return w.length > 1; });
    if (!words.length) return [];
    var out = [];
    data.forEach(function (d) {
      var hay = norm(d.h + ' ' + d.t), score = 0;
      for (var i = 0; i < words.length; i++) {
        if (hay.indexOf(words[i]) < 0) return;
        score += norm(d.h).indexOf(words[i]) >= 0 ? 5 : 1;
      }
      out.push({ d: d, s: score });
    });
    out.sort(function (a, b) { return b.s - a.s; });
    return out.slice(0, 12).map(function (o) { return o.d; });
  }
  function snippet(d, words) {
    var t = d.t, low = norm(t), pos = -1;
    for (var i = 0; i < words.length && pos < 0; i++) pos = low.indexOf(words[i]);
    var s = pos < 0 ? t.slice(0, 110) : t.slice(Math.max(0, pos - 40), pos + 90);
    return (pos > 40 ? '…' : '') + s;
  }
  q.addEventListener('input', function () {
    var term = q.value.trim();
    if (term.length < 2) { box.classList.remove('open'); return; }
    var words = norm(term).split(/\s+/);
    var res = search(term);
    box.innerHTML = res.length ? res.map(function (d) {
      return '<a href="' + d.p + (d.a ? '#' + d.a : '') + '"><strong>' + esc(d.h) + '</strong><small>' + esc(d.pt) + ' — ' + esc(snippet(d, words)) + '</small></a>';
    }).join('') : '<a href="#" onclick="return false"><small>Nenhum resultado para “' + esc(term) + '”.</small></a>';
    box.classList.add('open');
  });
  document.addEventListener('click', function (e) { if (!e.target.closest('.search')) box.classList.remove('open'); });
  document.addEventListener('keydown', function (e) {
    if (e.key === '/' && document.activeElement !== q && !/input|textarea/i.test(document.activeElement.tagName)) { e.preventDefault(); q.focus(); }
  });
})();
