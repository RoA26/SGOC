// Recorrido de integración REAL de Nexora: navegador (Chromium) → frontend (Vite, proxy /api) →
// Spring Boot → PostgreSQL. Sin respuestas simuladas.
//
// SOLO para un entorno local desechable con una base de datos VACÍA: crea empresas, usuarios,
// un proveedor, un producto y una solicitud ficticios. Se niega a ejecutarse contra una URL que no
// sea localhost. Instrucciones y variables en el README de esta carpeta.

const BASE = process.env.NEXORA_BASE_URL ?? 'http://localhost:5173'
const { NEXORA_ROOT_USER: ROOT_USER, NEXORA_ROOT_PASSWORD: ROOT_PASS, NEXORA_CLAVE_CUENTAS: CLAVE } = process.env
const SALIDA = process.env.NEXORA_SALIDA ?? null // carpeta opcional para las capturas

if (!['localhost', '127.0.0.1'].includes(new URL(BASE).hostname)) {
  console.error(`Rechazado: ${BASE} no es local. Este recorrido crea datos y solo se ejecuta contra localhost.`)
  process.exit(2)
}
if (!ROOT_USER || !ROOT_PASS || !CLAVE) {
  console.error('Faltan NEXORA_ROOT_USER, NEXORA_ROOT_PASSWORD o NEXORA_CLAVE_CUENTAS (ver README).')
  process.exit(2)
}

const { chromium } = await import(process.env.PLAYWRIGHT_MODULE ?? 'playwright')

const resultados = []
function comprobar(nombre, ok, detalle = '') {
  resultados.push({ nombre, ok: Boolean(ok) })
  console.log(`${ok ? 'OK  ' : 'FALLO'} ${nombre}${detalle ? ' — ' + detalle : ''}`)
}

async function api(metodo, ruta, { token, body, tenant } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers.Authorization = `Bearer ${token}`
  if (tenant !== undefined) headers['X-Tenant-ID'] = String(tenant)
  const r = await fetch(`${BASE}/api${ruta}`, { method: metodo, headers, body: body ? JSON.stringify(body) : undefined })
  const texto = await r.text()
  let json = null
  try {
    json = texto ? JSON.parse(texto) : null
  } catch {
    json = texto
  }
  return { status: r.status, json }
}
const login = (username, password) => api('POST', '/auth/login', { body: { username, password } })

const errores = []
const browser = await chromium.launch(process.env.PLAYWRIGHT_CHROMIUM ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM } : {})
async function nuevaPagina(nombre, opciones = {}) {
  const ctx = await browser.newContext({ viewport: { width: 1366, height: 860 }, ...opciones })
  const page = await ctx.newPage()
  page.on('pageerror', (e) => errores.push(`${nombre}: ${e.message}`))
  page.on('console', (m) => {
    // Los 4xx esperados del propio recorrido aparecen como "Failed to load resource".
    if (m.type() === 'error' && !/Failed to load resource/.test(m.text())) errores.push(`${nombre}: ${m.text()}`)
  })
  return { ctx, page }
}
async function captura(page, nombre) {
  if (SALIDA) await page.screenshot({ path: `${SALIDA}/${nombre}.png` })
}
async function entrarPorFormulario(page, username, password) {
  await page.goto(`${BASE}/login`)
  await page.getByLabel('Usuario', { exact: true }).fill(username)
  await page.getByLabel('Contraseña', { exact: true }).fill(password)
  await page.getByRole('button', { name: 'Ingresar' }).click()
}
async function registrarPorFormulario(page, { via, codigo, username, apellidos }) {
  await page.goto(`${BASE}/registro`)
  await page.getByRole('radio', { name: via === 'empresa' ? /Tengo el código de mi empresa/ : /Tengo una invitación/ }).check()
  await page.getByLabel(via === 'empresa' ? /^Código de tu empresa/ : /^Código de invitación/).fill(codigo)
  await page.getByLabel(/^Nombres/).fill('Trabajador')
  await page.getByLabel(/^Apellidos/).fill(apellidos)
  await page.getByLabel(/^Usuario/).fill(username)
  await page.getByLabel(/^Correo electrónico/).fill(`${username}@ejemplo.test`)
  await page.getByLabel(/^Contraseña/).fill(CLAVE)
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
}
const tokenGuardado = (page) => page.evaluate(() => JSON.parse(localStorage.getItem('sgp.session') ?? '{}')?.state?.accessToken ?? null)

// ---------------------------------------------------------------- 1. Autenticación
comprobar('Login con contraseña incorrecta → 401', (await login(ROOT_USER, 'contraseña-incorrecta')).status === 401)
const root = await login(ROOT_USER, ROOT_PASS)
comprobar('Login del SUPER_ADMIN → 200 con token', root.status === 200 && root.json?.accessToken, `rol ${root.json?.usuario?.rol}`)
const rootToken = root.json?.accessToken
comprobar('Sin token → 401', (await api('GET', '/auth/me')).status === 401)
{
  const { ctx, page } = await nuevaPagina('superadmin')
  await entrarPorFormulario(page, ROOT_USER, ROOT_PASS)
  await page.waitForURL((u) => u.pathname === '/', { timeout: 15000 })
  await page.getByRole('link', { name: 'Empresas', exact: true }).waitFor()
  comprobar('UI: el SUPER_ADMIN entra por el formulario; ve «Empresas» y no «Personal»',
    (await page.getByRole('link', { name: 'Personal', exact: true }).count()) === 0)
  await ctx.close()
}

// ---------------------------------------------------------------- 2. Dos empresas con su administrador
async function crearEmpresa(nombre, nit, codigo, adminUser) {
  return api('POST', '/v1/empresas', { token: rootToken, tenant: 'global', body: {
    nombre, nit, codigo,
    administrador: { nombres: 'Admin', apellidos: nombre, username: adminUser, email: `${adminUser}@ejemplo.test`, password: CLAVE },
  } })
}
const ea = await crearEmpresa('Ferretería Prueba A', '900100001-1', 'FPRA01', 'admin.a')
const eb = await crearEmpresa('Papelería Prueba B', '900100002-2', 'PPRB01', 'admin.b')
comprobar('Alta de las empresas A y B con su administrador → 201', ea.status === 201 && eb.status === 201, `${ea.status}/${eb.status}`)
if (ea.status !== 201 || eb.status !== 201) {
  console.error('No se pudieron crear las empresas: ¿la base de datos no estaba vacía?')
  await browser.close()
  process.exit(1)
}
const empresaA = ea.json
const empresaB = eb.json
const adminA = (await login('admin.a', CLAVE)).json.accessToken
const adminB = (await login('admin.b', CLAVE)).json.accessToken

// ---------------------------------------------------------------- 3. «Personal» muestra solo el código de la propia empresa
{
  const { ctx, page } = await nuevaPagina('admin-a-codigo', { permissions: ['clipboard-read', 'clipboard-write'] })
  await entrarPorFormulario(page, 'admin.a', CLAVE)
  await page.waitForURL((u) => u.pathname === '/', { timeout: 15000 })
  await page.getByRole('link', { name: 'Personal', exact: true }).click()
  const seccion = page.getByRole('region', { name: 'Código de registro de tu empresa' })
  await seccion.getByText(empresaA.codigoEmpresa).waitFor()
  const texto = await seccion.innerText()
  comprobar('UI Personal (A): muestra el código de registro de A', texto.includes(empresaA.codigoEmpresa))
  comprobar('UI Personal (A): no muestra el código de B', !(await page.locator('body').innerText()).includes(empresaB.codigoEmpresa))
  await seccion.getByRole('button', { name: 'Copiar el código de registro' }).click()
  await seccion.getByText('Copiado').waitFor({ timeout: 5000 }).catch(() => {})
  const portapapeles = await page.evaluate(() => navigator.clipboard.readText()).catch(() => null)
  comprobar('UI Personal (A): «Copiar» deja el código en el portapapeles', portapapeles === empresaA.codigoEmpresa)
  await captura(page, 'registro-01-personal-codigo')
  await ctx.close()
}
const actualCruzada = await api('GET', '/v1/empresas/actual', { token: adminA, tenant: empresaB.id })
comprobar('API /empresas/actual (A) con X-Tenant-ID de B: devuelve A, nunca B',
  actualCruzada.status === 200 && actualCruzada.json?.id === empresaA.id && actualCruzada.json?.codigoEmpresa === empresaA.codigoEmpresa)

// ---------------------------------------------------------------- 4. Registro con el código de empresa (UI)
{
  const { ctx, page } = await nuevaPagina('registro-invalido')
  await registrarPorFormulario(page, { via: 'empresa', codigo: 'ZZZZ-ZZZZ-ZZZZ-ZZZZ', username: 'trab.invalido', apellidos: 'Invalido' })
  await page.getByText('El código de empresa no es válido.').waitFor({ timeout: 10000 })
  comprobar('UI /registro: código de empresa inexistente → error junto al campo, sin cuenta',
    new URL(page.url()).pathname === '/registro' && (await login('trab.invalido', CLAVE)).status === 401)
  await ctx.close()
}
{
  const { ctx, page } = await nuevaPagina('registro-empresa')
  await registrarPorFormulario(page, { via: 'empresa', codigo: empresaA.codigoEmpresa.toLowerCase().replaceAll('-', ' '), username: 'trab.a1', apellidos: 'Uno' })
  await page.getByRole('heading', { name: 'Cuenta pendiente de aprobación' }).waitFor({ timeout: 10000 })
  const estadoTexto = await page.getByRole('status').innerText()
  comprobar('UI /registro con código de empresa: aviso de aprobación pendiente con la empresa',
    estadoTexto.includes(`Solicitud enviada a ${empresaA.nombre}`) && estadoTexto.includes('trab.a1'))
  comprobar('UI /registro con código de empresa: no inicia sesión', (await tokenGuardado(page)) === null)
  await captura(page, 'registro-02-pendiente')
  await page.goto(`${BASE}/`)
  await page.waitForURL((u) => u.pathname === '/login', { timeout: 10000 })
  comprobar('Tras registrarse, abrir la aplicación lleva al login (no hay sesión)', true)
  await ctx.close()
}
const rb1 = await api('POST', '/auth/registro', { body: {
  nombres: 'Trabajador', apellidos: 'B', username: 'trab.b1', email: 'trab.b1@ejemplo.test', password: CLAVE, codigoEmpresa: empresaB.codigoEmpresa } })
comprobar('Registro con el código de B (API) → 201 PENDIENTE en B', rb1.status === 201 && rb1.json?.estado === 'PENDIENTE' && rb1.json?.empresaId === empresaB.id)
const ambos = await api('POST', '/auth/registro', { body: {
  nombres: 'Trabajador', apellidos: 'X', username: 'trab.ambos', email: 'trab.ambos@ejemplo.test', password: CLAVE,
  codigoEmpresa: empresaA.codigoEmpresa, codigoInvitacion: 'AAAA-BBBB-CCCC-DDDD' } })
comprobar('Registro con los dos códigos a la vez → 400 (exactamente uno)', ambos.status === 400 && ambos.json?.errors?.codigoEmpresa, ambos.json?.errors?.codigoEmpresa)

// Invitación de A canjeada por el formulario (vía que ya existía).
const inv = await api('POST', '/auth/invitaciones', { token: adminA, body: {} })
{
  const { ctx, page } = await nuevaPagina('registro-invitacion')
  await page.goto(`${BASE}/registro?codigo=${encodeURIComponent(inv.json.codigo)}`)
  comprobar('UI /registro?codigo=…: preselecciona «Tengo una invitación»', await page.getByRole('radio', { name: /Tengo una invitación/ }).isChecked())
  await page.getByLabel(/^Nombres/).fill('Trabajador')
  await page.getByLabel(/^Apellidos/).fill('Dos')
  await page.getByLabel(/^Usuario/).fill('trab.a2')
  await page.getByLabel(/^Correo electrónico/).fill('trab.a2@ejemplo.test')
  await page.getByLabel(/^Contraseña/).fill(CLAVE)
  await page.getByRole('button', { name: 'Crear cuenta' }).click()
  await page.waitForURL((u) => u.pathname === '/', { timeout: 15000 })
  comprobar('UI /registro con invitación: cuenta ACTIVA y sesión iniciada', (await tokenGuardado(page)) !== null)
  await ctx.close()
}

// ---------------------------------------------------------------- 5. Cuenta pendiente: sin acceso
const pend = await login('trab.a1', CLAVE)
comprobar('Login de la cuenta PENDIENTE → 403 con motivo', pend.status === 403 && pend.json?.motivo === 'PENDIENTE', `motivo ${pend.json?.motivo}`)
{
  const { ctx, page } = await nuevaPagina('login-pendiente')
  await entrarPorFormulario(page, 'trab.a1', CLAVE)
  const alerta = page.getByRole('alert')
  await alerta.waitFor({ timeout: 10000 })
  comprobar('UI login: muestra el motivo a la cuenta pendiente y no entra', new URL(page.url()).pathname === '/login', (await alerta.innerText()).slice(0, 90))
  await ctx.close()
}

// ---------------------------------------------------------------- 6. Aprobación en «Personal» (A)
{
  const { ctx, page } = await nuevaPagina('admin-a-personal')
  await entrarPorFormulario(page, 'admin.a', CLAVE)
  await page.waitForURL((u) => u.pathname === '/', { timeout: 15000 })
  await page.goto(`${BASE}/admin/personal?estado=PENDIENTE`)
  const tabla = page.getByRole('table', { name: 'Trabajadores de la empresa' })
  await tabla.getByText('@trab.a1').waitFor()
  const filas = await page.locator('table tbody tr p.font-mono').allInnerTexts()
  comprobar('UI Personal (A) «Pendientes»: solo el registrado con el código de A', JSON.stringify(filas) === '["@trab.a1"]', filas.join(', '))
  await page.getByRole('button', { name: 'Aprobar a Trabajador Uno' }).click()
  await page.getByText('Trabajador Uno ya puede iniciar sesión y operar.').waitFor()
  comprobar('UI Personal (A): aprobar al pendiente', true)
  await ctx.close()
}
const trasAprobar = await login('trab.a1', CLAVE)
comprobar('Tras la aprobación, el trabajador inicia sesión → 200 en la empresa A',
  trasAprobar.status === 200 && trasAprobar.json?.usuario?.empresaId === empresaA.id)
const trabA1 = trasAprobar.json?.accessToken

// ---------------------------------------------------------------- 7. Actividad real
const prov = await api('POST', '/v1/proveedores', { token: adminA, body: { nit: '800200300-1', razonSocial: 'Proveedor Prueba', email: 'ventas@proveedor.test' } })
const prod = await api('POST', '/v1/productos', { token: adminA, body: { sku: 'TOR-001', nombre: 'Tornillo de prueba', precio: 1200, proveedorId: prov.json?.id } })
const sol = await api('POST', '/v1/solicitudes', { token: trabA1, body: { justificacion: 'Reposición de tornillería para el taller.', detalles: [{ productoId: prod.json?.id, cantidad: 3 }] } })
comprobar('El trabajador aprobado crea una solicitud → 201', prov.status === 201 && prod.status === 201 && sol.status === 201)
const act = await api('GET', '/v1/trabajadores/actividad', { token: adminA })
const filaA1 = act.json?.content?.find((t) => t.username === 'trab.a1')
comprobar('Actividad (A): la solicitud aparece como última operación con su fecha real, sin correo',
  filaA1?.actividad?.ultimaOperacion?.tipo === 'SOLICITUD' && Date.parse(filaA1.actividad.ultimaOperacion.en) === Date.parse(sol.json?.fecha) &&
  filaA1.actividad.solicitudes === 1 && !('email' in filaA1))

// ---------------------------------------------------------------- 8. Desactivación con efecto inmediato
const trabA2 = (await login('trab.a2', CLAVE)).json?.accessToken
{
  const { ctx, page } = await nuevaPagina('admin-a-desactivar')
  await entrarPorFormulario(page, 'admin.a', CLAVE)
  await page.waitForURL((u) => u.pathname === '/', { timeout: 15000 })
  await page.goto(`${BASE}/admin/personal`)
  await page.getByRole('table', { name: 'Trabajadores de la empresa' }).getByText('@trab.a2').waitFor()
  await page.getByRole('button', { name: 'Desactivar a Trabajador Dos' }).click()
  await page.getByRole('dialog').getByRole('button', { name: 'Desactivar' }).click()
  await page.getByText('Trabajador Dos ya no puede operar.').waitFor()
  comprobar('UI Personal (A): desactivar con confirmación', true)
  await ctx.close()
}
const meA2 = await api('GET', '/auth/me', { token: trabA2 })
comprobar('El token vigente del desactivado deja de servir → 403 INACTIVO', meA2.status === 403 && meA2.json?.motivo === 'INACTIVO')

// ---------------------------------------------------------------- 9. Aislamiento entre empresas
const actB = await api('GET', '/v1/trabajadores/actividad', { token: adminB, tenant: empresaA.id })
comprobar('Personal (B), aun con X-Tenant-ID de A: solo su trabajador', actB.json?.content?.length === 1 && actB.json.content[0].username === 'trab.b1')
comprobar('B no cambia el estado de un trabajador de A → 404',
  (await api('PATCH', `/v1/trabajadores/${filaA1?.id}/estado`, { token: adminB, body: { estado: 'INACTIVO' } })).status === 404)
comprobar('B no ve la solicitud de A → 404', (await api('GET', `/v1/solicitudes/${sol.json?.id}`, { token: adminB })).status === 404)
const actualB = await api('GET', '/v1/empresas/actual', { token: adminB })
comprobar('/empresas/actual (B): su propio código, distinto del de A',
  actualB.json?.codigoEmpresa === empresaB.codigoEmpresa && actualB.json?.codigoEmpresa !== empresaA.codigoEmpresa)
{
  const { ctx, page } = await nuevaPagina('admin-b')
  await entrarPorFormulario(page, 'admin.b', CLAVE)
  await page.waitForURL((u) => u.pathname === '/', { timeout: 15000 })
  await page.goto(`${BASE}/admin/personal`)
  await page.getByRole('table', { name: 'Trabajadores de la empresa' }).getByText('@trab.b1').waitFor()
  const cuerpo = await page.locator('body').innerText()
  comprobar('UI Personal (B): su código y su trabajador; nada de A',
    cuerpo.includes(empresaB.codigoEmpresa) && !cuerpo.includes(empresaA.codigoEmpresa) && !cuerpo.includes('@trab.a1'))
  await ctx.close()
}

// ---------------------------------------------------------------- 10. Permisos por rol
comprobar('Trabajador → /empresas/actual → 403 (no ve el código)', (await api('GET', '/v1/empresas/actual', { token: trabA1 })).status === 403)
comprobar('Trabajador → actividad → 403', (await api('GET', '/v1/trabajadores/actividad', { token: trabA1 })).status === 403)
comprobar('Trabajador → cambiar estados → 403',
  (await api('PATCH', `/v1/trabajadores/${filaA1?.id}/estado`, { token: trabA1, body: { estado: 'INACTIVO' } })).status === 403)
comprobar('SUPER_ADMIN en modo global → /empresas/actual 400', (await api('GET', '/v1/empresas/actual', { token: rootToken, tenant: 'global' })).status === 400)
comprobar('SUPER_ADMIN supervisando A → /empresas/actual 200 con A',
  (await api('GET', '/v1/empresas/actual', { token: rootToken, tenant: empresaA.id })).json?.id === empresaA.id)
{
  const { ctx, page } = await nuevaPagina('trabajador')
  await entrarPorFormulario(page, 'trab.a1', CLAVE)
  await page.waitForURL((u) => u.pathname === '/', { timeout: 15000 })
  comprobar('UI: el trabajador no ve «Personal»', (await page.getByRole('link', { name: 'Personal', exact: true }).count()) === 0)
  await page.goto(`${BASE}/admin/personal`)
  await page.waitForURL((u) => u.pathname === '/', { timeout: 10000 })
  comprobar('UI: el trabajador que abre /admin/personal vuelve a /', true)
  await ctx.close()
}

// ---------------------------------------------------------------- 11. Empresa desactivada: su código deja de servir
const desact = await api('PATCH', `/v1/empresas/${empresaB.id}/estado`, { token: rootToken, tenant: 'global', body: { activa: false } })
const conCodigoInactivo = await api('POST', '/auth/registro', { body: {
  nombres: 'Trabajador', apellidos: 'B2', username: 'trab.b2', email: 'trab.b2@ejemplo.test', password: CLAVE, codigoEmpresa: empresaB.codigoEmpresa } })
comprobar('Con la empresa B desactivada, su código → 400 sin crear cuenta',
  desact.status === 200 && conCodigoInactivo.status === 400 && (await login('trab.b2', CLAVE)).status === 401, conCodigoInactivo.json?.errors?.codigoEmpresa)

await browser.close()
comprobar('Sin errores de JavaScript ni de consola inesperados en el navegador', errores.length === 0, errores.slice(0, 3).join(' | '))
const fallos = resultados.filter((r) => !r.ok)
console.log(`\nRESUMEN: ${resultados.length - fallos.length}/${resultados.length} comprobaciones superadas`)
process.exit(fallos.length ? 1 : 0)
