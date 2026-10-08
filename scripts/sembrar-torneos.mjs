// Siembra la parte deportiva de una instancia de DEMOSTRACION: categorias, temporada,
// plantilla de puntos, jugadores y tres torneos (uno terminado, que arma el ranking;
// uno en curso con la primera ronda jugada; uno con la inscripcion abierta).
//
// Es el complemento de sembrar-demo.ps1, que siembra turnos, kiosco y caja.
//
// Uso:
//   node scripts/sembrar-torneos.mjs --api https://... --usuario admin --clave "..."
//
// Va todo por la API, igual que lo haria el club desde el panel: asi el ranking y los
// cuadros quedan calculados por el sistema y no escritos a mano.

const args = Object.fromEntries(process.argv.slice(2).reduce((acc, v, i, a) =>
    v.startsWith('--') ? [...acc, [v.slice(2), a[i + 1]]] : acc, []));
const API = (args.api ?? 'http://localhost:8080').replace(/\/$/, '');
const USUARIO = args.usuario ?? 'admin';
const CLAVE = args.clave;
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

// Semilla fija: correrlo dos veces en bases vacias da lo mismo.
let semilla = 20261008;
const azar = () => (semilla = (semilla * 1103515245 + 12345) % 2147483648) / 2147483648;
const entre = (a, b) => a + Math.floor(azar() * (b - a + 1));
const fecha = (dias) => {
    const d = new Date(); d.setDate(d.getDate() + dias);
    return d.toLocaleDateString('sv-SE', { timeZone: 'America/Argentina/Buenos_Aires' });
};

const NOMBRES_H = ['Martín', 'Facundo', 'Joaquín', 'Nicolás', 'Matías', 'Agustín', 'Lucas', 'Gonzalo',
    'Santiago', 'Franco', 'Ezequiel', 'Diego', 'Federico', 'Rodrigo', 'Pablo', 'Leandro', 'Emiliano',
    'Sebastián', 'Mauricio', 'Ignacio', 'Tomás', 'Hernán', 'Cristian', 'Javier', 'Maximiliano',
    'Germán', 'Lautaro', 'Ramiro', 'Damián', 'Marcos', 'Iván', 'Claudio'];
const NOMBRES_M = ['Florencia', 'Agustina', 'Camila', 'Valentina', 'Lucía', 'Micaela', 'Sofía',
    'Julieta', 'Carolina', 'Paula', 'Romina', 'Daniela', 'Victoria', 'Belén', 'Natalia', 'Rocío'];
const APELLIDOS = ['Gómez', 'Juárez', 'Paz', 'Ledesma', 'Coronel', 'Ibarra', 'Herrera', 'Suárez',
    'Sosa', 'Díaz', 'Acosta', 'Ruiz', 'Carabajal', 'Salvatierra', 'Gerez', 'Montenegro', 'Luna',
    'Figueroa', 'Rojas', 'Bravo', 'Peralta', 'Vera', 'Barraza', 'Santillán', 'Alderete', 'Medina',
    'Olivera', 'Cáceres', 'Ávila', 'Iturre', 'Leguizamón', 'Navarro', 'Correa', 'Villalba'];

const MARCADORES = ['6-3 6-4', '6-4 6-2', '7-5 6-3', '6-2 6-1', '6-4 3-6 6-3', '7-6 6-4',
    '4-6 6-3 7-5', '6-1 6-4', '6-3 7-5', '6-4 4-6 6-2'].map(m => m.replace(/ /g, ' / '));

async function main() {
    token = (await llamar('POST', '/auth/login', { username: USUARIO, password: CLAVE })).token;
    const lugar = (await llamar('GET', '/api/lugares'))[0];
    if (!lugar) throw new Error('Primero hay que crear la sede (ver DEPLOY.md).');
    console.log('[1/5] Categorias, temporada y puntos');

    // Reusa las que ya existan: el script se puede volver a correr si se corto a mitad.
    const existentes = await llamar('GET', '/api/categorias');
    const categorias = [];
    for (const c of [
        { nombre: 'Cuarta', nivel: 4, genero: 'MASCULINO' },
        { nombre: 'Quinta', nivel: 5, genero: 'MASCULINO' },
        { nombre: 'Damas C', nivel: 6, genero: 'FEMENINO' },
    ]) categorias.push(existentes.find(e => e.nombre === c.nombre) ?? await llamar('POST', '/api/categorias', c));

    const temporada = (await llamar('GET', '/api/temporadas')).find(t => t.activa) ?? await llamar('POST', '/api/temporadas',
        { nombre: `Temporada ${new Date().getFullYear()}`, fechaInicio: `${new Date().getFullYear()}-03-01`,
          // fechaFin es opcional en el request pero NOT NULL en la base: sin ella responde 500.
          fechaFin: `${new Date().getFullYear()}-12-20`, activa: true });

    const plantilla = (await llamar('GET', '/api/plantillas-puntos'))[0] ?? await llamar('POST', '/api/plantillas-puntos', {
        nombre: 'Eliminación directa del club',
        descripcion: 'Puntos por ronda alcanzada',
        formatoTorneo: 'ELIMINACION_DIRECTA',
        activo: true,
        rondas: [
            { nombreRonda: 'Octavos de final', puntosGanador: 20, puntosPerdedor: 10, orden: 1 },
            { nombreRonda: 'Cuartos de final', puntosGanador: 35, puntosPerdedor: 20, orden: 2 },
            { nombreRonda: 'Semifinales', puntosGanador: 60, puntosPerdedor: 35, orden: 3 },
            { nombreRonda: 'Final', puntosGanador: 100, puntosPerdedor: 60, orden: 4 },
        ],
    });

    console.log('[2/5] Jugadores');
    const usados = new Set();
    const jugadoresPor = {};
    const yaCargados = await llamar('GET', '/api/jugadores');
    for (const cat of categorias) {
        const deLaCategoria = yaCargados.filter(j => j.categoriaId === cat.id).map(j => j.id);
        if (deLaCategoria.length >= 16) { jugadoresPor[cat.id] = deLaCategoria.slice(0, 16); continue; }
        const fem = cat.genero === 'FEMENINO';
        jugadoresPor[cat.id] = [];
        for (let i = 0; i < 16; i++) {
            let nombre, apellido;
            do {
                nombre = (fem ? NOMBRES_M : NOMBRES_H)[entre(0, (fem ? NOMBRES_M : NOMBRES_H).length - 1)];
                apellido = APELLIDOS[entre(0, APELLIDOS.length - 1)];
            } while (usados.has(nombre + apellido));
            usados.add(nombre + apellido);
            const j = await llamar('POST', '/api/jugadores', {
                nombre, apellido,
                genero: cat.genero,
                categoriaId: cat.id,
                telefono: `385${entre(4000000, 6999999)}`,
                fechaNacimiento: `${entre(1978, 2004)}-${String(entre(1, 12)).padStart(2, '0')}-${String(entre(1, 28)).padStart(2, '0')}`,
                posicionJuego: i % 2 === 0 ? 'DRIVE' : 'REVES',
            });
            jugadoresPor[cat.id].push(j.id);
        }
    }

    // Cada torneo arma sus propias parejas: en un club real la pareja cambia de torneo a torneo.
    const parejasDe = (catId, cuantas, corrimiento) => {
        const js = jugadoresPor[catId];
        const r = [];
        for (let i = 0; i < cuantas; i++) {
            r.push([js[(2 * i + corrimiento) % js.length], js[(2 * i + 1 + corrimiento * 3) % js.length]]);
        }
        return r.filter(([a, b]) => a !== b);
    };

    const torneosExistentes = await llamar('GET', '/api/torneos');
    async function crearTorneo(nombre, descripcion, inicio, fin, costo, premio) {
        const previo = torneosExistentes.find(t => t.nombre === nombre);
        if (previo) return previo;
        return llamar('POST', '/api/torneos', {
            nombre, descripcion,
            formato: 'ELIMINACION_DIRECTA',
            tipoSorteo: 'ALEATORIO',
            fechaInicio: inicio, fechaFin: fin,
            cupoMaximoParejas: 24,
            costoInscripcionJugador: costo,
            premioAcumulado: premio,
            sumaPuntosRanking: true,
            incluyeEliminacion: true,
            mejorDeSets: 3,
            temporadaId: temporada.id,
            lugarId: lugar.id,
            plantillaPuntosId: plantilla.id,
            categoriaIds: categorias.map(c => c.id),
        });
    }

    async function inscribir(torneo, parejasPorCat) {
        if (torneo.estado && torneo.estado !== 'BORRADOR') return;
        await llamar('PATCH', `/api/torneos/${torneo.id}/estado`, { estado: 'INSCRIPCION' });
        for (const cat of categorias) {
            const parejas = parejasPorCat(cat.id);
            for (let i = 0; i < parejas.length; i++) {
                const [jugador1Id, jugador2Id] = parejas[i];
                try {
                    await llamar('POST', `/api/torneos/${torneo.id}/parejas`,
                        { jugador1Id, jugador2Id, categoriaId: cat.id, esCabezaDeSerie: i < 2 });
                } catch (e) {
                    console.log('   . se saltea una pareja:', e.message.slice(0, 120));
                }
            }
        }
    }

    // Carga resultados ronda por ronda: cada ronda cerrada genera la siguiente.
    async function jugar(torneo, rondasMax) {
        for (let ronda = 0; ronda < rondasMax; ronda++) {
            const partidos = (await llamar('GET', `/api/torneos/${torneo.id}/partidos`))
                .filter(p => !p.marcador && p.parejaLocalId && p.parejaVisitanteId);
            if (partidos.length === 0) break;
            for (const p of partidos) {
                await llamar('PUT', `/api/torneos/${torneo.id}/partidos/${p.id}/resultado`,
                    { marcador: MARCADORES[entre(0, MARCADORES.length - 1)] });
            }
        }
    }

    console.log('[3/5] Torneo terminado (arma el ranking)');
    const apertura = await crearTorneo('Torneo Apertura', 'Primer torneo de la temporada. Eliminación directa, al mejor de tres sets.',
        fecha(-60), fecha(-57), 20000, 600000);
    await inscribir(apertura, id => parejasDe(id, 8, 0));
    if (['BORRADOR', 'INSCRIPCION'].includes(apertura.estado ?? 'BORRADOR')) {
        await llamar('POST', `/api/torneos/${apertura.id}/sorteo`);
        await llamar('PATCH', `/api/torneos/${apertura.id}/estado`, { estado: 'EN_CURSO' });
    }
    await jugar(apertura, 6);
    // Cargar la final suele cerrar el torneo solo: se mira el estado actual.
    if ((await llamar('GET', `/api/torneos/${apertura.id}`)).estado !== 'FINALIZADO') {
        await llamar('PATCH', `/api/torneos/${apertura.id}/estado`, { estado: 'FINALIZADO' });
    }

    console.log('[4/5] Torneo en curso');
    const primavera = await crearTorneo('Torneo de la Primavera', 'Se juega este fin de semana. Cuadro de eliminación directa.',
        fecha(-1), fecha(2), 22000, 750000);
    await inscribir(primavera, id => parejasDe(id, 8, 1));
    if (['BORRADOR', 'INSCRIPCION'].includes(primavera.estado ?? 'BORRADOR')) {
        await llamar('POST', `/api/torneos/${primavera.id}/sorteo`);
        await llamar('PATCH', `/api/torneos/${primavera.id}/estado`, { estado: 'EN_CURSO' });
        await jugar(primavera, 1);
    }

    console.log('[5/5] Torneo con inscripcion abierta');
    const clausura = await crearTorneo('Copa Clausura', 'Inscripción abierta hasta el jueves previo. Cupo de 24 parejas.',
        fecha(25), fecha(28), 25000, 900000);
    await inscribir(clausura, id => parejasDe(id, 5, 2));

    console.log('\nListo: 48 jugadores, 3 torneos y el ranking de la temporada armado.');
}

main().catch(e => { console.error(e.message); process.exit(1); });
