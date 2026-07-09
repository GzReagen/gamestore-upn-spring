const API_URL = '/api';

const state = {
  usuario: JSON.parse(localStorage.getItem('usuario') || localStorage.getItem('user') || 'null'),
  token: localStorage.getItem('token') || '',
  data: {}
};

function h(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;');
}

function money(value) {
  return Number(value || 0).toFixed(2);
}

function getImageUrl(imagen) {
  if (!imagen) return 'https://via.placeholder.com/300x420?text=Sin+imagen';
  const img = String(imagen).trim();
  if (img.startsWith('http') || img.startsWith('data:')) return img;
  return `/img/${img}`;
}

async function apiRequest(endpoint, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  if (state.token) headers.Authorization = `Bearer ${state.token}`;

  const config = { ...options, headers };
  if (config.body && typeof config.body !== 'string') config.body = JSON.stringify(config.body);

  const response = await fetch(`${API_URL}${endpoint}`, config);
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message || 'Error en la petición.');
  return data;
}

const api = {
  login: (datos) => apiRequest('/auth/login', { method: 'POST', body: datos }),
  registro: (datos) => apiRequest('/auth/registro', { method: 'POST', body: datos }),
  listarVideojuegos: () => apiRequest('/videojuegos'),
  listarVideojuegosAdmin: () => apiRequest('/videojuegos/admin/lista'),
  obtenerVideojuego: (id) => apiRequest(`/videojuegos/${id}`),
  crearVideojuego: (datos) => apiRequest('/videojuegos', { method: 'POST', body: datos }),
  actualizarVideojuego: (id, datos) => apiRequest(`/videojuegos/${id}`, { method: 'PUT', body: datos }),
  eliminarVideojuego: (id) => apiRequest(`/videojuegos/${id}`, { method: 'DELETE' }),
  restaurarVideojuego: (id) => apiRequest(`/videojuegos/restaurar/${id}`, { method: 'PUT' }),
  listarCategorias: () => apiRequest('/categorias'),
  crearCategoria: (nombre) => apiRequest('/categorias', { method: 'POST', body: { nombre } }),
  buscarRawg: (nombre) => apiRequest(`/rawg/buscar?nombre=${encodeURIComponent(nombre)}`),
  obtenerCarrito: () => apiRequest('/carrito'),
  agregarCarrito: (videojuegoId, cantidad = 1) => apiRequest(`/carrito/agregar/${videojuegoId}`, { method: 'POST', body: { cantidad } }),
  actualizarDetalleCarrito: (detalleId, cantidad) => apiRequest(`/carrito/detalle/${detalleId}`, { method: 'PUT', body: { cantidad } }),
  eliminarDetalleCarrito: (detalleId) => apiRequest(`/carrito/detalle/${detalleId}`, { method: 'DELETE' }),
  confirmarCompraCarrito: () => apiRequest('/carrito/comprar', { method: 'POST' }),
  historialCompras: () => apiRequest('/compras/historial'),
  dashboardAdmin: () => apiRequest('/dashboard/admin'),
  gerenteResumen: (filtros = {}) => {
    const params = new URLSearchParams();
    if (filtros.desde) params.append('desde', filtros.desde);
    if (filtros.hasta) params.append('hasta', filtros.hasta);
    return apiRequest(`/gerente/resumen${params.toString() ? `?${params}` : ''}`);
  },
  gerenteUsuarios: () => apiRequest('/gerente/usuarios')
};

function setSession(data) {
  state.token = data.token;
  state.usuario = data.usuario || data.user;
  localStorage.setItem('token', state.token);
  localStorage.setItem('usuario', JSON.stringify(state.usuario));
  localStorage.setItem('user', JSON.stringify(state.usuario));
}

function logout() {
  state.token = '';
  state.usuario = null;
  localStorage.removeItem('token');
  localStorage.removeItem('usuario');
  localStorage.removeItem('user');
  go('/login');
}

function rol() {
  return String(state.usuario?.rol || '').trim().toUpperCase();
}

function isCliente() { return rol() === 'CLIENTE'; }
function isAdmin() { return rol() === 'ADMIN' || rol() === 'GERENTE'; }
function isGerente() { return rol() === 'GERENTE'; }

function go(path) {
  location.hash = `#${path}`;
}

function currentPath() {
  return (location.hash.replace(/^#/, '') || '/');
}

function navbar() {
  const r = rol();
  return `
    <nav class="gx-navbar">
      <div class="gx-navbar-inner">
        <a class="gx-brand" href="#/">
          <div class="gx-brand-mark">G</div>
          <div>GameStore <span>UPN</span></div>
        </a>

        <div class="gx-nav-links">
          <a class="gx-nav-link" href="#/">Inicio</a>
          ${r === 'CLIENTE' ? `
            <a class="gx-nav-link" href="#/carrito">Carrito</a>
            <a class="gx-nav-link" href="#/historial">Mis compras</a>
          ` : ''}
          ${r === 'ADMIN' || r === 'GERENTE' ? `
            <a class="gx-nav-link" href="#/admin">Panel Admin</a>
            <a class="gx-nav-link" href="#/admin/dashboard">Dashboard</a>
          ` : ''}
          ${r === 'GERENTE' ? `<a class="gx-nav-link" href="#/gerente">Panel Gerente</a>` : ''}
        </div>

        <div class="gx-user-area">
          ${state.usuario ? `
            <span class="gx-user-name">Hola, ${h(state.usuario.nombre || state.usuario.usuario)}</span>
            <button class="gx-btn-logout" onclick="logout()">Cerrar sesión</button>
          ` : `<a href="#/login" class="gx-btn-login">Iniciar sesión</a>`}
        </div>
      </div>
    </nav>
  `;
}

function layout(content) {
  document.getElementById('root').innerHTML = `${navbar()}${content}`;
}

function redibujarManteniendoFoco(drawFn, inputId, cursorPos) {
  drawFn();
  if (!inputId) return;

  const input = document.getElementById(inputId);
  if (!input) return;

  input.focus();

  if (typeof cursorPos === 'number' && typeof input.setSelectionRange === 'function') {
    input.setSelectionRange(cursorPos, cursorPos);
  }
}

function actualizarVistaPreviaFormulario() {
  const data = state.data.formJuego;
  if (!data || !data.form) return;

  const form = data.form;

  const previewImagen = document.getElementById('previewImagen');
  if (previewImagen) previewImagen.src = getImageUrl(form.imagen);

  const previewNombre = document.getElementById('previewNombre');
  if (previewNombre) previewNombre.textContent = form.nombre || 'Nombre del videojuego';

  const previewDescripcion = document.getElementById('previewDescripcion');
  if (previewDescripcion) previewDescripcion.textContent = form.descripcion || 'Descripción del videojuego.';

  const previewPrecio = document.getElementById('previewPrecio');
  if (previewPrecio) previewPrecio.textContent = `S/ ${money(form.precio)}`;

  const previewStock = document.getElementById('previewStock');
  if (previewStock) {
    previewStock.textContent = form.stock || 0;
    previewStock.className = Number(form.stock || 0) <= 5 ? 'text-danger fw-bold' : 'fw-bold';
  }
}

function alertHtml(type, msg) {
  return msg ? `<div class="alert alert-${type} text-center">${h(msg)}</div>` : '';
}

function loading(title = 'Cargando...') {
  layout(`<div class="gx-panel-page"><div class="gx-panel-container text-center py-5"><h3 class="text-dark">${h(title)}</h3></div></div>`);
}

function requireLogin(path = '/login') {
  if (!state.usuario) {
    go(path);
    return false;
  }
  return true;
}

function obtenerCategoriaIds(juego) {
  if (Array.isArray(juego.categoria_ids)) return juego.categoria_ids.map(String);
  if (typeof juego.categoria_ids === 'string' && juego.categoria_ids.trim() !== '') {
    return juego.categoria_ids.split(',').map(x => x.trim());
  }
  if (juego.categoria_id) return [String(juego.categoria_id)];
  return [];
}

function obtenerNombresCategorias(juego) {
  const texto = juego.categorias_nombre || juego.categoria_nombre || juego.nombre_categoria || '';
  if (!texto) return ['Sin categoría'];
  return String(texto).split(',').map(x => x.trim()).filter(Boolean);
}

async function renderCatalogo(mensaje = '', error = '') {
  loading('Cargando catálogo...');
  try {
    const [videojuegos, categorias] = await Promise.all([api.listarVideojuegos(), api.listarCategorias()]);
    state.data.catalogo = { videojuegos, categorias, busqueda: '', categoria: '' };
    drawCatalogo(mensaje, error);
  } catch (e) {
    layout(`<div class="gx-catalog-page"><div class="container py-5">${alertHtml('danger', e.message)}</div></div>`);
  }
}

function drawCatalogo(mensaje = '', error = '') {
  const data = state.data.catalogo;
  const busqueda = data.busqueda || '';
  const categoriaFiltro = data.categoria || '';
  const filtrados = data.videojuegos.filter(juego => {
    const nombre = String(juego.nombre || '').toLowerCase();
    const categoriaIds = obtenerCategoriaIds(juego);
    return nombre.includes(busqueda.toLowerCase()) && (categoriaFiltro === '' || categoriaIds.includes(String(categoriaFiltro)) || String(juego.categoria_id) === String(categoriaFiltro));
  });

  layout(`
    <div class="gx-catalog-page">
      <div class="gx-catalog-shell">
        <aside class="gx-sidebar">
          <h2 class="gx-sidebar-title">MI TIENDA</h2>
          <div class="gx-sidebar-line"></div>

          <div class="gx-filter-box">
            <div class="gx-filter-label">Buscar videojuego</div>
            <input id="catalogBusquedaInput" type="text" class="form-control" placeholder="Buscar producto..." value="${h(busqueda)}" oninput="catalogSet('busqueda', this.value, this)">
          </div>

          <div class="gx-filter-box">
            <div class="gx-filter-label">Categoría</div>
            <select class="form-select" onchange="catalogSet('categoria', this.value)">
              <option value="">Todas las categorías</option>
              ${data.categorias.map(c => `<option value="${c.id}" ${String(categoriaFiltro) === String(c.id) ? 'selected' : ''}>${h(c.nombre)}</option>`).join('')}
            </select>
          </div>

          <button class="gx-btn-outline" onclick="catalogClear()">Limpiar filtros</button>

          ${rol() === 'ADMIN' ? `<div class="gx-filter-box mt-3"><p class="text-muted small mb-2">Estás conectado como administrador.</p><a href="#/admin" class="gx-btn-green d-block text-center">Panel Admin</a></div>` : ''}
          ${rol() === 'GERENTE' ? `<div class="gx-filter-box mt-3"><p class="text-muted small mb-2">Estás conectado como gerente.</p><div class="d-grid gap-2"><a href="#/gerente" class="gx-btn-green text-center">Panel Gerente</a><a href="#/admin" class="gx-btn-outline text-center">Panel Admin</a></div></div>` : ''}
        </aside>

        <main>
          <h1 class="gx-main-title">Catálogo de Videojuegos</h1>
          <p class="gx-main-subtitle">Explora los videojuegos disponibles en GameStore UPN.</p>
          ${alertHtml('success', mensaje)}
          ${alertHtml('danger', error)}
          <h3 class="gx-section-title">Recientes</h3>

          ${filtrados.length === 0 ? `<div class="gx-empty">No se encontraron videojuegos con esos filtros.</div>` : `
            <div class="gx-games-grid">
              ${filtrados.map(juego => `
                <div class="gx-game-card">
                  <img src="${h(getImageUrl(juego.imagen))}" class="gx-game-img" alt="${h(juego.nombre)}" onerror="this.src='https://via.placeholder.com/300x420?text=Sin+imagen'">
                  <div class="gx-game-body">
                    <h5 class="gx-game-title">${h(juego.nombre)}</h5>
                    <p class="gx-game-desc">${h(juego.descripcion || 'Sin descripción')}</p>
                    <div class="d-flex flex-wrap gap-2 mb-2">
                      ${obtenerNombresCategorias(juego).map(cat => `<span class="gx-badge">${h(cat)}</span>`).join('')}
                    </div>
                    <div class="d-flex justify-content-between align-items-center mb-3">
                      <span class="gx-price">S/ ${money(juego.precio)}</span>
                      <span class="gx-stock">Stock: ${h(juego.stock)}</span>
                    </div>
                    <button class="gx-btn-green" onclick="agregarAlCarrito(${juego.id})">Agregar al carrito</button>
                  </div>
                </div>
              `).join('')}
            </div>
          `}
        </main>
      </div>
    </div>
  `);
}

window.catalogSet = function(key, value, input = null) {
  const inputId = input?.id || '';
  const cursorPos = input?.selectionStart;
  state.data.catalogo[key] = value;
  redibujarManteniendoFoco(() => drawCatalogo(), inputId, cursorPos);
};

window.catalogClear = function() {
  state.data.catalogo.busqueda = '';
  state.data.catalogo.categoria = '';
  drawCatalogo();
};

window.agregarAlCarrito = async function(videojuegoId) {
  if (!state.usuario) return drawCatalogo('', 'Debes iniciar sesión como cliente para comprar.');
  if (!isCliente()) return drawCatalogo('', 'Solo los clientes pueden agregar productos al carrito.');
  try {
    await api.agregarCarrito(videojuegoId, 1);
    await renderCatalogo('Videojuego agregado al carrito correctamente.', '');
  } catch (e) {
    drawCatalogo('', e.message);
  }
};

function renderLogin(error = '') {
  layout(`
    <div class="gx-login-page">
      <div class="card gx-light-card gx-login-card">
        <div class="gx-login-top">
          <div class="gx-login-logo">G</div>
          <h1 class="gx-login-title">GameStore UPN</h1>
          <p class="gx-login-subtitle">Inicia sesión para continuar.</p>
        </div>
        <div class="gx-login-body">
          ${alertHtml('danger', error)}
          <form onsubmit="loginSubmit(event)">
            <div class="mb-3">
              <label class="gx-form-label">Usuario o correo</label>
              <input class="form-control gx-input" name="usuario" placeholder="admin, cliente o gerente" required>
            </div>
            <div class="mb-3">
              <label class="gx-form-label">Contraseña</label>
              <input type="password" class="form-control gx-input" name="password" placeholder="123456" required>
            </div>
            <button class="gx-btn-green-panel w-100" type="submit">Iniciar sesión</button>
          </form>
          <div class="gx-test-users mt-3">
            <strong>Usuarios de prueba:</strong><br>
            cliente / 123456<br>
            admin / 123456<br>
            gerente / 123456
          </div>
          <p class="text-center mt-3 mb-0"><a href="#/registro" class="text-success fw-bold">Crear cuenta cliente</a></p>
        </div>
      </div>
    </div>
  `);
}

window.loginSubmit = async function(e) {
  e.preventDefault();
  const form = Object.fromEntries(new FormData(e.target).entries());
  try {
    const data = await api.login(form);
    setSession(data);
    go('/');
  } catch (err) {
    renderLogin(err.message);
  }
};

function renderRegistro(error = '', mensaje = '') {
  layout(`
    <div class="gx-login-page">
      <div class="card gx-light-card gx-login-card">
        <div class="gx-login-top">
          <div class="gx-login-logo">G</div>
          <h1 class="gx-login-title">Crear cuenta</h1>
          <p class="gx-login-subtitle">Registro de cliente.</p>
        </div>
        <div class="gx-login-body">
          ${alertHtml('danger', error)}${alertHtml('success', mensaje)}
          <form onsubmit="registroSubmit(event)">
            <div class="mb-3"><label class="gx-form-label">Nombre</label><input class="form-control gx-input" name="nombre" required></div>
            <div class="mb-3"><label class="gx-form-label">Correo</label><input type="email" class="form-control gx-input" name="correo" required></div>
            <div class="mb-3"><label class="gx-form-label">Usuario</label><input class="form-control gx-input" name="usuario" required></div>
            <div class="mb-3"><label class="gx-form-label">Contraseña</label><input type="password" class="form-control gx-input" name="password" required></div>
            <button class="gx-btn-green-panel w-100" type="submit">Registrarme</button>
          </form>
          <p class="text-center mt-3 mb-0"><a href="#/login" class="text-success fw-bold">Volver al login</a></p>
        </div>
      </div>
    </div>
  `);
}

window.registroSubmit = async function(e) {
  e.preventDefault();
  const form = Object.fromEntries(new FormData(e.target).entries());
  try {
    await api.registro(form);
    renderRegistro('', 'Cliente registrado correctamente. Ya puedes iniciar sesión.');
  } catch (err) {
    renderRegistro(err.message, '');
  }
};

async function renderAdmin(mensaje = '', error = '') {
  if (!requireLogin()) return;
  if (!isAdmin()) return layout(`${navbar()}<div class="gx-panel-page"><div class="gx-panel-container">${alertHtml('danger', 'Acceso permitido solo para administrador o gerente.')}</div></div>`);
  loading('Cargando panel administrador...');
  try {
    const [videojuegos, categorias] = await Promise.all([api.listarVideojuegosAdmin(), api.listarCategorias()]);
    state.data.admin = { videojuegos, categorias, busqueda: '', categoria: '', estado: '' };
    drawAdmin(mensaje, error);
  } catch (e) {
    layout(`<div class="gx-panel-page"><div class="gx-panel-container">${alertHtml('danger', e.message)}</div></div>`);
  }
}

function drawAdmin(mensaje = '', error = '') {
  const data = state.data.admin;
  const filtrados = data.videojuegos.filter(juego => {
    const nombre = String(juego.nombre || '').toLowerCase();
    const activo = Number(juego.activo) === 1 ? 'visible' : 'oculto';
    const categoriaIds = obtenerCategoriaIds(juego);
    return nombre.includes((data.busqueda || '').toLowerCase()) &&
      (!data.categoria || categoriaIds.includes(String(data.categoria)) || String(juego.categoria_id) === String(data.categoria)) &&
      (!data.estado || data.estado === activo);
  });

  layout(`
    <div class="gx-panel-page">
      <div class="gx-panel-container">
        <div class="gx-page-header">
          <div class="gx-page-title-box"><div class="gx-page-icon">⚙️</div><div><h1 class="gx-page-title">Panel Administrador</h1><p class="gx-page-subtitle">Gestiona videojuegos, stock, estado del catálogo y varias categorías.</p></div></div>
          <div class="d-flex gap-2 flex-wrap">
            <a href="#/admin/nuevo" class="gx-btn-green-panel">➕ Agregar videojuego</a>
            <a href="#/admin/dashboard" class="gx-btn-primary">📊 Dashboard</a>
            <a href="#/" class="gx-btn-outline-panel">👁️ Ver catálogo</a>
          </div>
        </div>
        ${alertHtml('success', mensaje)}${alertHtml('danger', error)}
        <div class="card gx-light-card mb-4">
          <div class="card-header gx-dark-header">Filtros del panel</div>
          <div class="card-body">
            <div class="row g-3 align-items-end">
              <div class="col-md-5"><label class="gx-form-label">Buscar videojuego</label><input id="adminBusquedaInput" class="form-control gx-input" placeholder="Buscar por nombre..." value="${h(data.busqueda)}" oninput="adminSet('busqueda', this.value, this)"></div>
              <div class="col-md-3"><label class="gx-form-label">Categoría</label><select class="form-select gx-select" onchange="adminSet('categoria', this.value)"><option value="">Todas</option>${data.categorias.map(c => `<option value="${c.id}" ${String(data.categoria) === String(c.id) ? 'selected' : ''}>${h(c.nombre)}</option>`).join('')}</select></div>
              <div class="col-md-3"><label class="gx-form-label">Estado</label><select class="form-select gx-select" onchange="adminSet('estado', this.value)"><option value="">Todos</option><option value="visible" ${data.estado === 'visible' ? 'selected' : ''}>Visible</option><option value="oculto" ${data.estado === 'oculto' ? 'selected' : ''}>Oculto</option></select></div>
              <div class="col-md-1"><button class="gx-btn-outline-panel w-100" onclick="adminClear()">Limpiar</button></div>
            </div>
          </div>
        </div>

        <div class="card gx-light-card">
          <div class="card-header gx-dark-header d-flex justify-content-between align-items-center"><span>Lista de videojuegos registrados</span><span class="admin-total-badge">Total: ${filtrados.length}</span></div>
          <div class="card-body table-responsive">
            ${filtrados.length === 0 ? `<div class="alert alert-secondary text-center">No hay videojuegos con esos filtros.</div>` : `
              <table class="table table-bordered table-hover align-middle gx-table">
                <thead><tr><th>ID</th><th>Imagen</th><th>Nombre</th><th>Descripción</th><th>Categorías</th><th>Precio</th><th>Stock</th><th>Estado</th><th>Acciones</th></tr></thead>
                <tbody>
                  ${filtrados.map(juego => {
                    const activo = Number(juego.activo) === 1;
                    return `<tr class="${!activo ? 'table-secondary' : ''}">
                      <td class="fw-bold text-center">${juego.id}</td>
                      <td class="text-center"><img src="${h(getImageUrl(juego.imagen))}" alt="${h(juego.nombre)}" style="width:110px;height:65px;object-fit:cover;border-radius:8px;opacity:${activo ? 1 : 0.45}" onerror="this.src='https://via.placeholder.com/110x65?text=Sin+imagen'"></td>
                      <td class="fw-bold text-primary">${h(juego.nombre)}</td>
                      <td>${h(juego.descripcion || 'Sin descripción')}</td>
                      <td><div class="d-flex flex-wrap gap-2">${obtenerNombresCategorias(juego).map(cat => `<span class="badge bg-secondary">${h(cat)}</span>`).join('')}</div></td>
                      <td class="text-center fw-bold text-success">S/ ${money(juego.precio)}</td>
                      <td class="text-center"><span class="${Number(juego.stock) <= 5 ? 'badge bg-danger' : 'badge bg-info text-dark'}">${juego.stock}</span></td>
                      <td class="text-center">${activo ? `<span class="badge bg-success">Visible</span>` : `<span class="badge bg-secondary">Oculto</span>`}</td>
                      <td class="text-center"><div class="d-flex justify-content-center gap-2 flex-wrap"><a href="#/admin/editar/${juego.id}" class="btn btn-primary btn-sm fw-bold">✏️ Editar</a>${activo ? `<button class="btn btn-warning btn-sm fw-bold" onclick="quitarJuego(${juego.id})">🚫 Quitar</button>` : `<button class="btn btn-success btn-sm fw-bold" onclick="restaurarJuego(${juego.id})">♻️ Reincorporar</button>`}</div></td>
                    </tr>`;
                  }).join('')}
                </tbody>
              </table>
            `}
          </div>
        </div>
      </div>
    </div>
  `);
}

window.adminSet = function(key, value, input = null) {
  const inputId = input?.id || '';
  const cursorPos = input?.selectionStart;
  state.data.admin[key] = value;
  redibujarManteniendoFoco(() => drawAdmin(), inputId, cursorPos);
};
window.adminClear = function() { state.data.admin.busqueda = ''; state.data.admin.categoria = ''; state.data.admin.estado = ''; drawAdmin(); };
window.quitarJuego = async function(id) { if (!confirm('¿Quieres quitar este videojuego del catálogo? No se borrará el historial de ventas.')) return; try { await api.eliminarVideojuego(id); await renderAdmin('Videojuego quitado del catálogo. El historial de ventas se mantiene.', ''); } catch(e) { drawAdmin('', e.message); } };
window.restaurarJuego = async function(id) { try { await api.restaurarVideojuego(id); await renderAdmin('Videojuego reincorporado al catálogo correctamente.', ''); } catch(e) { drawAdmin('', e.message); } };
async function renderAdminForm(id = null, error = '', mensaje = '') {
  if (!requireLogin()) return;
  if (!isAdmin()) return renderAdmin('', 'Acceso permitido solo para administrador o gerente.');
  loading('Cargando formulario...');
  try {
    const categorias = await api.listarCategorias();
    let form = { nombre: '', descripcion: '', precio: '', stock: '', imagen: '', categoria_ids: [] };
    if (id) {
      const juego = await api.obtenerVideojuego(id);
      form = {
        nombre: juego.nombre || '',
        descripcion: juego.descripcion || '',
        precio: juego.precio || '',
        stock: juego.stock || '',
        imagen: juego.imagen || '',
        categoria_ids: obtenerCategoriaIds(juego).map(Number).filter(Boolean)
      };
    }
    state.data.formJuego = { id, categorias, form, nuevaCategoria: '' };
    drawAdminForm(error, mensaje);
  } catch (e) {
    layout(`<div class="gx-panel-page"><div class="gx-panel-container">${alertHtml('danger', e.message)}<a href="#/admin" class="gx-btn-outline-panel">Volver al panel</a></div></div>`);
  }
}

function drawAdminForm(error = '', mensaje = '') {
  const data = state.data.formJuego;
  const form = data.form;
  const modoEdicion = Boolean(data.id);
  const seleccionadas = data.categorias.filter(c => form.categoria_ids.includes(Number(c.id))).map(c => c.nombre);

  layout(`
    <div class="gx-panel-page">
      <div class="gx-panel-container">
        <div class="gx-page-header">
          <div class="gx-page-title-box"><div class="gx-page-icon">${modoEdicion ? '✏️' : '➕'}</div><div><h1 class="gx-page-title">${modoEdicion ? 'Editar videojuego' : 'Agregar videojuego'}</h1><p class="gx-page-subtitle">Puedes asignar categorías existentes o crear nuevas categorías.</p></div></div>
          <a href="#/admin" class="gx-btn-outline-panel">Volver al panel</a>
        </div>
        ${alertHtml('danger', error)}${alertHtml('success', mensaje)}

        <div class="row g-4">
          <div class="col-lg-8">
            <div class="card gx-light-card">
              <div class="card-header gx-dark-header">Datos del videojuego</div>
              <div class="card-body">
                <form onsubmit="guardarVideojuego(event)">
                  <div class="row g-3">
                    <div class="col-md-8"><label class="gx-form-label">Nombre del videojuego</label><input type="text" name="nombre" class="form-control gx-input" value="${h(form.nombre)}" oninput="formSet('nombre', this.value)" placeholder="Ejemplo: DOOM Eternal" required></div>
                    <div class="col-md-4 d-flex align-items-end"><button type="button" class="gx-btn-primary w-100" onclick="buscarRawgForm()">Buscar RAWG</button></div>
                    <div class="col-md-12"><label class="gx-form-label">Descripción</label><textarea name="descripcion" class="form-control gx-textarea" rows="4" oninput="formSet('descripcion', this.value)" placeholder="Descripción breve del videojuego...">${h(form.descripcion)}</textarea></div>
                    <div class="col-md-4"><label class="gx-form-label">Precio</label><input type="number" name="precio" class="form-control gx-input" value="${h(form.precio)}" oninput="formSet('precio', this.value)" placeholder="0.00" min="0" step="0.01" required></div>
                    <div class="col-md-4"><label class="gx-form-label">Stock</label><input type="number" name="stock" class="form-control gx-input" value="${h(form.stock)}" oninput="formSet('stock', this.value)" placeholder="0" min="0" required></div>
                    <div class="col-md-4"><label class="gx-form-label">Categorías seleccionadas</label><div class="form-control gx-input d-flex align-items-center">${form.categoria_ids.length} seleccionada(s)</div></div>
                    <div class="col-md-12"><label class="gx-form-label">Crear nueva categoría</label><div class="d-flex gap-2 flex-wrap"><input type="text" class="form-control gx-input" style="max-width:420px" value="${h(data.nuevaCategoria)}" oninput="formNewCat(this.value)" placeholder="Ejemplo: Terror, Fantasía, Cooperativo..." onkeydown="if(event.key==='Enter'){event.preventDefault(); crearCategoriaForm();}"><button type="button" class="gx-btn-green-panel" onclick="crearCategoriaForm()">Crear categoría</button></div><small class="text-muted d-block mt-2">Si la categoría ya existe, el sistema la seleccionará automáticamente.</small></div>
                    <div class="col-md-12"><label class="gx-form-label">Categorías del videojuego</label><div class="d-flex flex-wrap gap-2">${data.categorias.map(c => { const sel = form.categoria_ids.includes(Number(c.id)); return `<button type="button" onclick="toggleCategoria(${c.id})" class="${sel ? 'btn btn-success fw-bold' : 'btn btn-outline-secondary fw-bold'}">${sel ? '✅ ' : '➕ '}${h(c.nombre)}</button>`; }).join('')}</div><small class="text-muted d-block mt-2">Puedes seleccionar una, dos, tres o más categorías.</small></div>
                    <div class="col-md-12"><label class="gx-form-label">Imagen</label><input type="text" name="imagen" class="form-control gx-input" value="${h(form.imagen)}" oninput="formSet('imagen', this.value)" placeholder="doom.jpg o URL de imagen"><small class="text-muted">Puedes usar una imagen local dentro de frontend/img o una URL externa.</small></div>
                  </div>
                  <div class="d-flex gap-2 flex-wrap mt-4"><button type="submit" class="gx-btn-green-panel">${modoEdicion ? 'Guardar cambios' : 'Agregar videojuego'}</button><a href="#/admin" class="gx-btn-outline-panel">Cancelar</a></div>
                </form>
              </div>
            </div>
          </div>

          <div class="col-lg-4">
            <div class="card gx-light-card">
              <div class="card-header gx-green-header">Vista previa</div>
              <div class="card-body">
                <div class="card border-0 shadow-sm" style="overflow:hidden;border-radius:16px">
                  <img id="previewImagen" src="${h(getImageUrl(form.imagen))}" alt="${h(form.nombre || 'Vista previa')}" style="width:100%;height:230px;object-fit:cover;background:#e5e7eb" onerror="this.src='https://via.placeholder.com/400x260?text=Sin+imagen'">
                  <div class="card-body">
                    <h5 id="previewNombre" class="fw-bold">${h(form.nombre || 'Nombre del videojuego')}</h5>
                    <p id="previewDescripcion" class="text-muted small">${h(form.descripcion || 'Descripción del videojuego.')}</p>
                    <p class="mb-1"><strong>Precio:</strong> <span id="previewPrecio" class="text-success fw-bold">S/ ${money(form.precio)}</span></p>
                    <p class="mb-1"><strong>Stock:</strong> <span id="previewStock" class="${Number(form.stock || 0) <= 5 ? 'text-danger fw-bold' : 'fw-bold'}">${h(form.stock || 0)}</span></p>
                    <div><strong>Categorías:</strong><div class="d-flex flex-wrap gap-2 mt-2">${seleccionadas.length ? seleccionadas.map(n => `<span class="badge bg-secondary">${h(n)}</span>`).join('') : `<span class="badge bg-secondary">Sin categoría</span>`}</div></div>
                  </div>
                </div>
                <div class="alert alert-info mt-3 mb-0">Esta vista previa muestra cómo se verá el videojuego en el catálogo.</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  `);
}

window.formSet = function(key, value) {
  state.data.formJuego.form[key] = value;
  actualizarVistaPreviaFormulario();
};
window.formNewCat = function(value) { state.data.formJuego.nuevaCategoria = value; };
window.toggleCategoria = function(id) { const arr = state.data.formJuego.form.categoria_ids; const n = Number(id); state.data.formJuego.form.categoria_ids = arr.includes(n) ? arr.filter(x => x !== n) : [...arr, n]; drawAdminForm(); };
window.crearCategoriaForm = async function() { const nombre = (state.data.formJuego.nuevaCategoria || '').trim(); if (!nombre) return drawAdminForm('Escribe el nombre de la nueva categoría.', ''); try { const resp = await api.crearCategoria(nombre); const cat = resp.categoria; if (!state.data.formJuego.categorias.some(c => Number(c.id) === Number(cat.id))) state.data.formJuego.categorias.push(cat); state.data.formJuego.categorias.sort((a,b) => String(a.nombre).localeCompare(String(b.nombre))); if (!state.data.formJuego.form.categoria_ids.includes(Number(cat.id))) state.data.formJuego.form.categoria_ids.push(Number(cat.id)); state.data.formJuego.nuevaCategoria = ''; drawAdminForm('', resp.yaExistia ? 'La categoría ya existía y fue seleccionada.' : 'Categoría creada y seleccionada correctamente.'); } catch(e) { drawAdminForm(e.message, ''); } };
window.buscarRawgForm = async function() { const form = state.data.formJuego.form; if (!form.nombre.trim()) return drawAdminForm('Primero escribe el nombre del videojuego para buscarlo.', ''); try { const juego = await api.buscarRawg(form.nombre); form.nombre = juego.name || juego.nombre || form.nombre; form.descripcion = juego.description_raw || juego.descripcion || juego.description || form.descripcion; form.imagen = juego.background_image || juego.imagenUrl || juego.imagen || form.imagen; drawAdminForm('', 'Datos encontrados y cargados desde RAWG.'); } catch(e) { drawAdminForm(e.message, ''); } };
window.guardarVideojuego = async function(e) { e.preventDefault(); const data = state.data.formJuego; const form = data.form; if (!form.nombre || !form.precio || !form.stock || form.categoria_ids.length === 0) return drawAdminForm('Nombre, precio, stock y al menos una categoría son obligatorios.', ''); const payload = { nombre: form.nombre, descripcion: form.descripcion, precio: Number(form.precio), stock: Number(form.stock), imagen: form.imagen, categoria_ids: form.categoria_ids, categoria_id: form.categoria_ids[0] }; try { if (data.id) await api.actualizarVideojuego(data.id, payload); else await api.crearVideojuego(payload); await renderAdmin(data.id ? 'Videojuego actualizado correctamente.' : 'Videojuego agregado correctamente.', ''); } catch(e) { drawAdminForm(e.message, ''); } };

async function renderCarrito(error = '', mensaje = '') {
  if (!requireLogin()) return;
  if (!isCliente()) return renderCatalogo('', 'Solo los clientes pueden usar el carrito.');
  loading('Cargando carrito...');
  try {
    const carrito = await api.obtenerCarrito();
    layout(`
      <div class="gx-panel-page"><div class="gx-panel-container">
        <div class="gx-page-header"><div class="gx-page-title-box"><div class="gx-page-icon">🛒</div><div><h1 class="gx-page-title">Carrito</h1><p class="gx-page-subtitle">Revisa tus videojuegos antes de comprar.</p></div></div><a href="#/" class="gx-btn-outline-panel">Seguir comprando</a></div>
        ${alertHtml('danger', error)}${alertHtml('success', mensaje)}
        <div class="card gx-light-card"><div class="card-header gx-dark-header">Productos en carrito</div><div class="card-body table-responsive">
          ${!carrito.items || carrito.items.length === 0 ? `<div class="gx-empty">Tu carrito está vacío.</div>` : `
            <table class="table table-bordered table-hover align-middle gx-table"><thead><tr><th>Imagen</th><th>Videojuego</th><th>Precio</th><th>Cantidad</th><th>Subtotal</th><th>Acciones</th></tr></thead><tbody>
              ${carrito.items.map(item => `<tr><td><img src="${h(getImageUrl(item.imagen))}" style="width:90px;height:58px;object-fit:cover;border-radius:8px" onerror="this.src='https://via.placeholder.com/90x58?text=IMG'"></td><td><strong>${h(item.nombre)}</strong><br><small class="text-muted">${h(item.categoria_nombre || '')}</small></td><td class="text-success fw-bold">S/ ${money(item.precio)}</td><td><input type="number" class="form-control gx-input" min="1" value="${h(item.cantidad)}" style="width:100px" onchange="actualizarCarrito(${item.detalle_id || item.id}, this.value)"></td><td class="fw-bold">S/ ${money(item.subtotal || Number(item.precio) * Number(item.cantidad))}</td><td><button class="btn btn-danger btn-sm fw-bold" onclick="eliminarCarrito(${item.detalle_id || item.id})">Eliminar</button></td></tr>`).join('')}
            </tbody></table>
            <div class="d-flex justify-content-between align-items-center flex-wrap gap-2 mt-3"><h3 class="text-success fw-bold mb-0">Total: S/ ${money(carrito.total)}</h3><button class="gx-btn-green-panel" onclick="comprarCarrito()">Confirmar compra</button></div>
          `}
        </div></div>
      </div></div>
    `);
  } catch (e) { layout(`<div class="gx-panel-page"><div class="gx-panel-container">${alertHtml('danger', e.message)}</div></div>`); }
}
window.actualizarCarrito = async function(id, cantidad) { try { await api.actualizarDetalleCarrito(id, Number(cantidad)); await renderCarrito('', 'Cantidad actualizada.'); } catch(e) { await renderCarrito(e.message, ''); } };
window.eliminarCarrito = async function(id) { try { await api.eliminarDetalleCarrito(id); await renderCarrito('', 'Producto eliminado del carrito.'); } catch(e) { await renderCarrito(e.message, ''); } };
window.comprarCarrito = async function() { try { await api.confirmarCompraCarrito(); await renderCarrito('', 'Compra realizada correctamente.'); } catch(e) { await renderCarrito(e.message, ''); } };

async function renderHistorial() {
  if (!requireLogin()) return;
  loading('Cargando historial...');
  try {
    const compras = await api.historialCompras();
    layout(`<div class="gx-panel-page"><div class="gx-panel-container"><div class="gx-page-header"><div class="gx-page-title-box"><div class="gx-page-icon">🧾</div><div><h1 class="gx-page-title">Mis compras</h1><p class="gx-page-subtitle">Historial de videojuegos comprados.</p></div></div></div><div class="card gx-light-card"><div class="card-header gx-dark-header">Historial de compras</div><div class="card-body table-responsive">${compras.length === 0 ? `<div class="gx-empty">No tienes compras registradas.</div>` : `<table class="table table-bordered table-hover align-middle gx-table"><thead><tr><th>Venta</th><th>Imagen</th><th>Videojuego</th><th>Cantidad</th><th>Total</th><th>Fecha</th></tr></thead><tbody>${compras.map(c => `<tr><td>${h(c.venta_id)}</td><td><img src="${h(getImageUrl(c.videojuego_imagen))}" style="width:90px;height:58px;object-fit:cover;border-radius:8px"></td><td class="fw-bold">${h(c.videojuego_nombre)}</td><td>${h(c.cantidad)}</td><td class="text-success fw-bold">S/ ${money(c.total)}</td><td>${h(c.fecha)}</td></tr>`).join('')}</tbody></table>`}</div></div></div></div>`);
  } catch(e) { layout(`<div class="gx-panel-page"><div class="gx-panel-container">${alertHtml('danger', e.message)}</div></div>`); }
}
async function renderDashboard() {
  if (!requireLogin()) return;
  if (!isAdmin()) return renderAdmin('', 'Acceso permitido solo para administrador o gerente.');
  loading('Cargando dashboard...');
  try {
    const d = await api.dashboardAdmin();
    layout(`
      <div class="gx-panel-page"><div class="gx-panel-container">
        <div class="gx-page-header"><div class="gx-page-title-box"><div class="gx-page-icon">📊</div><div><h1 class="gx-page-title">Dashboard Administrador</h1><p class="gx-page-subtitle">Resumen general de ventas, stock y videojuegos.</p></div></div><a href="#/admin" class="gx-btn-outline-panel">Volver al panel</a></div>
        <div class="row g-4 mb-4">
          ${stat('Videojuegos', d.totalVideojuegos, 'text-primary')}
          ${stat('Visibles', d.videojuegosVisibles, 'text-success')}
          ${stat('Ocultos', d.videojuegosOcultos, 'text-secondary')}
          ${stat('Stock bajo', d.totalStockBajo, 'text-danger')}
        </div>
        <div class="row g-4 mb-4">
          ${stat('Ventas', d.totalVentas, 'text-info')}
          ${stat('Total vendido', `S/ ${money(d.totalVendido)}`, 'text-success')}
          ${stat('Ticket promedio', `S/ ${money(d.ticketPromedio)}`, 'text-warning')}
        </div>
        <div class="row g-4 mb-4">
          <div class="col-md-6">${tablaSimple('Últimas ventas', ['ID','Cliente','Fecha','Total'], (d.ultimasVentas||[]).map(v => [v.id, v.cliente, v.fecha, `S/ ${money(v.total)}`]))}</div>
          <div class="col-md-6">${tablaSimple('Juegos con stock bajo', ['Juego','Stock','Precio'], (d.juegosStockBajo||[]).map(j => [j.nombre, j.stock, `S/ ${money(j.precio)}`]))}</div>
        </div>
        <div class="row g-4">
          <div class="col-md-6">${tablaSimple('Videojuegos más vendidos', ['Juego','Cantidad','Total'], (d.videojuegosMasVendidos||[]).map(j => [j.videojuego, j.cantidadVendida, `S/ ${money(j.totalRecaudado)}`]))}</div>
          <div class="col-md-6">${tablaSimple('Ventas por categoría', ['Categoría','Cantidad','Total'], (d.ventasPorCategoria||[]).map(v => [v.categoria, v.cantidadVendida, `S/ ${money(v.totalRecaudado)}`]))}</div>
        </div>
      </div></div>
    `);
  } catch(e) { layout(`<div class="gx-panel-page"><div class="gx-panel-container">${alertHtml('danger', e.message)}</div></div>`); }
}

function stat(title, value, cls) {
  return `<div class="col-md-3"><div class="gx-stat-card"><h6>${h(title)}</h6><h2 class="${cls}">${h(value)}</h2></div></div>`;
}

function tablaSimple(titulo, headers, rows) {
  return `<div class="card gx-light-card h-100"><div class="card-header gx-dark-header">${h(titulo)}</div><div class="card-body table-responsive">${rows.length === 0 ? `<div class="gx-empty">No hay datos.</div>` : `<table class="table table-bordered table-hover gx-table align-middle"><thead><tr>${headers.map(x => `<th>${h(x)}</th>`).join('')}</tr></thead><tbody>${rows.map(r => `<tr>${r.map(c => `<td>${h(c)}</td>`).join('')}</tr>`).join('')}</tbody></table>`}</div></div>`;
}

async function renderGerente(error = '') {
  if (!requireLogin()) return;
  if (!isGerente()) return renderCatalogo('', 'Acceso permitido solo para gerente.');
  const desde = state.data.gerenteDesde || '';
  const hasta = state.data.gerenteHasta || '';
  loading('Cargando panel gerente...');
  try {
    const [resumen, usuarios] = await Promise.all([api.gerenteResumen({ desde, hasta }), api.gerenteUsuarios()]);
    layout(`
      <div class="gerente-page"><div class="container py-4">
        <div class="gerente-header"><div class="gerente-title-box"><div class="gerente-icon">📊</div><div><h1 class="gerente-title">Panel Gerente</h1><p class="gerente-subtitle">Vista superior del sistema, usuarios, ventas y reportes.</p></div></div><button class="btn-actualizar" onclick="renderGerente()">Actualizar</button></div>
        ${alertHtml('danger', error)}
        <div class="card gerente-card mb-4"><div class="card-header gerente-card-header">Filtro de ventas por fecha</div><div class="card-body"><div class="row g-3 align-items-end"><div class="col-md-5"><label class="form-label fw-bold text-dark">Desde</label><input type="date" class="form-control gerente-input" value="${h(desde)}" onchange="state.data.gerenteDesde=this.value"></div><div class="col-md-5"><label class="form-label fw-bold text-dark">Hasta</label><input type="date" class="form-control gerente-input" value="${h(hasta)}" onchange="state.data.gerenteHasta=this.value"></div><div class="col-md-2 d-flex gap-2"><button class="btn btn-success w-100 fw-bold" onclick="renderGerente()">Buscar</button><button class="btn btn-outline-secondary w-100 fw-bold" onclick="state.data.gerenteDesde='';state.data.gerenteHasta='';renderGerente()">Limpiar</button></div></div></div></div>
        <div class="row g-4 mb-4">${stat('Total usuarios', resumen.totalUsuarios, 'text-primary')}${stat('Clientes', resumen.totalClientes, 'text-success')}${stat('Administradores', resumen.totalAdministradores, 'text-warning')}${stat('Gerentes', resumen.totalGerentes, 'text-danger')}</div>
        <div class="row g-4 mb-4">${stat('Videojuegos visibles', resumen.videojuegosVisibles, 'text-primary')}${stat('Videojuegos ocultos', resumen.videojuegosOcultos, 'text-secondary')}${stat('Ventas', resumen.totalVentas, 'text-success')}${stat('Stock bajo', resumen.stockBajo, 'text-danger')}</div>
        <div class="gerente-total-card mb-4"><h5>Total vendido</h5><h1>S/ ${money(resumen.totalVendido)}</h1><p>Este monto corresponde al total acumulado de ventas según el filtro aplicado.</p></div>
        <div class="row g-4 mb-4"><div class="col-md-6">${tablaSimple('Últimas ventas', ['ID','Cliente','Fecha','Total'], (resumen.ultimasVentas||[]).map(v => [v.id, v.cliente, v.fecha, `S/ ${money(v.total)}`]))}</div><div class="col-md-6">${tablaSimple('Videojuegos más vendidos', ['Juego','Cantidad','Total'], (resumen.videojuegosMasVendidos||[]).map(j => [j.videojuego, j.cantidadVendida, `S/ ${money(j.totalRecaudado)}`]))}</div></div>
        <div class="row g-4"><div class="col-md-6">${tablaSimple('Ventas por cliente', ['Cliente','Ventas','Total'], (resumen.ventasPorCliente||[]).map(c => [c.cliente, c.cantidadVentas, `S/ ${money(c.totalComprado)}`]))}</div><div class="col-md-6">${tablaSimple('Usuarios registrados', ['ID','Nombre','Usuario','Rol'], (usuarios||[]).map(u => [u.id, u.nombre, u.usuario, u.rol]))}</div></div>
      </div></div>
    `);
  } catch(e) { layout(`<div class="gerente-page"><div class="container py-4">${alertHtml('danger', e.message)}</div></div>`); }
}
window.renderGerente = renderGerente;

async function router() {
  const path = currentPath();
  if (path === '/' || path === '') return renderCatalogo();
  if (path === '/login') return renderLogin();
  if (path === '/registro') return renderRegistro();
  if (path === '/carrito') return renderCarrito();
  if (path === '/historial') return renderHistorial();
  if (path === '/admin') return renderAdmin();
  if (path === '/admin/nuevo') return renderAdminForm(null);
  if (path.startsWith('/admin/editar/')) return renderAdminForm(path.split('/').pop());
  if (path === '/admin/dashboard') return renderDashboard();
  if (path === '/gerente') return renderGerente();
  return renderCatalogo();
}

window.logout = logout;
window.go = go;

window.addEventListener('hashchange', router);
window.addEventListener('load', router);
