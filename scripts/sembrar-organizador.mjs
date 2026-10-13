// Siembra la parte de plata de una instancia de DEMOSTRACION en modo organizador: lo
// acordado y pagado a cada club, los días y horarios que cede, premios, gastos, aportes
// de sponsors y cobros de inscripción por jugador.
//
// Va después de sembrar-torneos.mjs (o sobre un clon con torneos ya cargados): trabaja
// sobre los torneos que encuentra. Los torneos tienen que tener costo de inscripción para
// que haya algo que cobrar.
//
// Uso:
//   node scripts/sembrar-organizador.mjs --api http://localhost:8096 --usuario admin --clave "..."
//   (opcional) --club-extra <lugarId>  segundo club para el torneo más grande
//
// Va todo por la API, igual que lo haría el organizador desde el panel. No es idempotente:
// correrlo dos veces duplica premios, gastos, aportes y pagos.

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, v, i, a) =>
    v.startsWith('--') ? [...acc, [v.slice(2), a[i + 1]]] : acc, []));
const API = (args.api ?? 'http://localhost:8080').replace(/\/$/, '');
const USUARIO = args.usuario ?? 'admin';
const CLAVE = args.clave;
const CLUB_EXTRA = args['club-extra'] ? Number(args['club-extra']) : null;
if (!CLAVE) throw new Error('Falta --clave');

let token;
async function llamar(metodo, ruta, cuerpo) {
    const r = await fetch(API + ruta, {
        method: metodo,
        headers: {
            'Content-Type': 'application/json; charset=utf-8',
            ...(token ? { Authorization: 'Bearer ' + token } : {}),
        },
        body: cuerpo === undefined ? undefined : JSON.stringify(cuerpo),
    });
    const texto = await r.text();
    if (!r.ok) throw new Error(`${metodo} ${ruta} -> ${r.status}: ${texto.slice(0, 300)}`);
    return texto ? JSON.parse(texto) : null;
}

// Semilla fija: correrlo sobre el mismo clon da siempre lo mismo.
let semilla = 20261012;
const azar = () => (semilla = (semilla * 1103515245 + 12345) % 2147483648) / 2147483648;
const entre = (a, b) => a + Math.floor(azar() * (b - a + 1));
const redondear = (n, paso = 5000) => Math.round(n / paso) * paso;
const MEDIOS = ['EFECTIVO', 'TRANSFERENCIA', 'TRANSFERENCIA', 'MERCADO_PAGO'];
const medio = () => MEDIOS[entre(0, MEDIOS.length - 1)];

const sumarDias = (iso, dias) => {
    const d = new Date(iso + 'T12:00:00'); d.setDate(d.getDate() + dias);
    return d.toISOString().slice(0, 10);
};
const hoy = new Date().toISOString().slice(0, 10);
// Nada con fecha futura: un pago adelantado no existe.
const noFutura = (iso) => (iso > hoy ? hoy : iso);

/** Los días que el club cede: el fin de semana largo del torneo, de tarde y noche. */
function franjasDe(torneo) {
    const dias = Math.min(4, Math.max(2, torneo.fechaFin
        ? Math.round((new Date(torneo.fechaFin) - new Date(torneo.fechaInicio)) / 86400000) + 1
        : 2));
    return Array.from({ length: dias }, (_, i) => {
        const fecha = sumarDias(torneo.fechaInicio, i);
        const finde = [0, 6].includes(new Date(fecha + 'T12:00:00').getDay());
        return { fecha, horaDesde: finde ? '09:00' : '17:00', horaHasta: '23:30' };
    });
}

async function main() {
    ({ token } = await llamar('POST', '/auth/login', { username: USUARIO, password: CLAVE }));

    const torneos = (await llamar('GET', '/api/torneos'))
        .filter((t) => t.estado !== 'BORRADOR')
        .sort((a, b) => a.fechaInicio.localeCompare(b.fechaInicio));
    const sponsors = await llamar('GET', '/api/sponsors').catch(() => []);
    const cobros = Object.fromEntries(await Promise.all(torneos.map(async (t) =>
        [t.id, await llamar('GET', `/api/torneos/${t.id}/cobros-inscripcion`)])));
    const masGrande = [...torneos].sort((a, b) => cobros[b.id].parejas.length - cobros[a.id].parejas.length)[0];

    for (const t of torneos) {
        const c = cobros[t.id];
        const parejas = c.parejas.length;
        const terminado = t.estado === 'FINALIZADO';
        const enCurso = t.estado === 'EN_CURSO';
        const categorias = [...new Map(c.parejas.map((p) => [p.categoriaId, p.categoriaNombre])).entries()];
        const resumen = [];

        // Lo que se le paga al club principal, y lo que cede.
        const [principal] = await llamar('GET', `/api/torneos/${t.id}/sedes`);
        const acordado = Math.max(60000, redondear(parejas * 6000, 10000));
        await llamar('PUT', `/api/torneos/${t.id}/sedes/${principal.id}`, {
            lugarId: principal.lugarId,
            canchaIds: principal.canchas.map((x) => x.id),
            franjas: franjasDe(t),
            montoAcordado: acordado,
            notas: 'Canchas, luz y pelotas de la zona a cargo del club.',
        });
        const pagadoClub = terminado ? acordado : enCurso ? acordado / 2 : 0;
        if (pagadoClub > 0) {
            await llamar('POST', `/api/torneos/${t.id}/sedes/${principal.id}/pagos`, {
                fecha: noFutura(t.fechaInicio), monto: pagadoClub, medio: 'TRANSFERENCIA',
                notas: terminado ? 'Pago total' : 'Adelanto del 50%',
            });
        }
        resumen.push(`club $${acordado}`);

        // El torneo más grande se juega en dos clubes.
        if (CLUB_EXTRA && t.id === masGrande.id) {
            const lugares = await llamar('GET', '/api/lugares');
            const extra = lugares.find((l) => l.id === CLUB_EXTRA);
            const canchas = (await llamar('GET', `/api/canchas?lugarId=${CLUB_EXTRA}`).catch(() => []))
                .slice(0, 3).map((x) => x.id);
            const sede = await llamar('POST', `/api/torneos/${t.id}/sedes`, {
                lugarId: CLUB_EXTRA, canchaIds: canchas, franjas: franjasDe(t).slice(0, 2),
                montoAcordado: 120000, notas: 'Cede tres canchas el viernes y el sábado.',
            });
            await llamar('POST', `/api/torneos/${t.id}/sedes/${sede.id}/pagos`, {
                fecha: noFutura(t.fechaInicio), monto: 60000, medio: 'EFECTIVO', notas: 'Adelanto',
            });
            resumen.push(`+ ${extra?.nombre ?? 'club extra'}`);
        }

        // Premios: campeón y subcampeón de cada categoría.
        for (const [categoriaId, nombre] of categorias) {
            const premios = [
                { puesto: 'Campeón', descripcion: 'Trofeo + paletas', monto: redondear(parejas * 2500 / categorias.length + 30000, 10000) },
                { puesto: 'Subcampeón', descripcion: 'Trofeo + pelotas', monto: redondear(parejas * 1000 / categorias.length + 15000, 5000) },
            ];
            for (const p of premios) {
                // La respuesta es el estado entero de las finanzas, no el premio creado.
                const finanzas = await llamar('POST', `/api/torneos/${t.id}/finanzas/premios`, { categoriaId, ...p });
                if (terminado) {
                    const id = finanzas.premios
                        .filter((x) => x.categoriaId === categoriaId && x.puesto === p.puesto && !x.entregado)
                        .map((x) => x.id).sort((a, b) => b - a)[0];
                    if (id) await llamar('PATCH', `/api/torneos/${t.id}/finanzas/premios/${id}/entrega`, {
                        entregado: true, entregadoA: `${p.puesto} ${nombre}`,
                    });
                }
            }
        }
        resumen.push(`${categorias.length * 2} premios`);

        // Gastos: pelotas, trofeos y la difusión.
        const gastos = [
            { categoria: 'PUBLICIDAD', descripcion: 'Flyer y publicación paga en Instagram', monto: 15000 },
            ...(parejas > 0 ? [
                { categoria: 'PELOTAS', descripcion: `${Math.ceil(parejas / 2)} tubos de pelotas`, monto: redondear(parejas * 2500, 1000) },
                { categoria: 'TROFEOS', descripcion: 'Trofeos y medallas', monto: categorias.length * 25000 },
            ] : []),
            ...(parejas >= 24 ? [{ categoria: 'FOTOGRAFIA', descripcion: 'Fotógrafo de las finales', monto: 30000 }] : []),
        ];
        for (const g of gastos) {
            await llamar('POST', `/api/torneos/${t.id}/finanzas/gastos`, {
                ...g, fecha: noFutura(sumarDias(t.fechaInicio, -3)), medio: medio(),
            });
        }

        // Aportes de sponsors.
        for (const s of sponsors.slice(entre(0, 2), entre(3, 5))) {
            await llamar('POST', `/api/torneos/${t.id}/finanzas/aportes`, {
                sponsorId: s.id, nombre: s.nombre, monto: entre(3, 8) * 10000,
                descripcion: 'Logo en el flyer y en la lona de la cancha central',
                cobrado: terminado || (enCurso && azar() < 0.5),
                cobradoEn: terminado || enCurso ? noFutura(t.fechaInicio) : null,
                medio: terminado || enCurso ? 'TRANSFERENCIA' : null,
            });
        }

        // Inscripciones: casi todo cobrado en lo terminado, la mitad en lo que está abierto.
        const proporcion = terminado ? 0.95 : enCurso ? 0.7 : 0.4;
        let pagos = 0;
        for (const p of c.parejas) {
            for (const j of p.jugadores) {
                if (j.saldo <= 0 || azar() > proporcion) continue;
                // Uno de cada cinco paga en dos partes.
                const enPartes = azar() < 0.2 && !terminado;
                await llamar('POST', `/api/torneos/${t.id}/cobros-inscripcion/parejas/${p.parejaId}`, {
                    jugadorId: j.jugadorId,
                    fecha: noFutura(sumarDias(t.fechaInicio, -entre(1, 6))),
                    monto: enPartes ? j.saldo / 2 : j.saldo,
                    medio: medio(),
                });
                pagos++;
            }
        }
        resumen.push(`${pagos} cobros`);

        console.log(`${t.nombre}: ${resumen.join(', ')}`);
    }
}

main().catch((e) => { console.error(e.message); process.exit(1); });
