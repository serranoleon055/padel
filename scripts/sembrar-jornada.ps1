<#
    Llena la jornada de HOY de una instancia de demostración: los turnos que quedan por
    delante, algunas solicitudes sin confirmar, consumo anotado, cobros por los cuatro
    medios, ventas de mostrador y los gastos del día.

    Es el complemento de `sembrar-demo.ps1`, que siembra los próximos días pero deja el
    día en curso a medias: cuando se le muestra el sistema a alguien, lo primero que se
    abre es el Panel, y un Panel con la tarde vacía no cuenta nada.

    Uso:
        .\scripts\sembrar-jornada.ps1                       # Royal, en el 8080
        .\scripts\sembrar-jornada.ps1 -Api http://localhost:8085
        .\scripts\sembrar-jornada.ps1 -Todas                # las seis demos locales

    OJO — lo que NO puede hacer:

    `ReservaService` rechaza reservar un horario que ya pasó, así que este script solo
    siembra de la hora actual en adelante. Las tarjetas "Canchas ocupadas" y "Jugando
    ahora" del Panel se llenan recién cuando el reloj entra en el primer turno sembrado:
    si son las 15:40 y el primer turno vendible es el de las 16, hay que esperar a las 16
    para que el Panel muestre gente jugando. El horario de la noche es el que mejor se ve.
#>
param(
    [string]$Api = "http://localhost:8080",
    [string]$Usuario = "admin",
    [string]$Clave = "admin123",
    # Las seis demos que conviven en local, cada una con su base (ver CLAUDE.md §10).
    [switch]$Todas,
    # Proporción de los horarios libres que se ocupan. Llenarlo al 100% deja el sistema
    # sin un solo hueco que mostrar en la grilla pública.
    [double]$Ocupacion = 0.7
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------- helpers

$script:Base = $null
$script:Token = $null

function Llamar {
    param([string]$Metodo, [string]$Ruta, $Cuerpo = $null)
    $encabezados = @{}
    if ($script:Token) { $encabezados["Authorization"] = "Bearer $($script:Token)" }
    $parametros = @{ Method = $Metodo; Uri = "$($script:Base)$Ruta"; Headers = $encabezados }
    if ($null -ne $Cuerpo) {
        $parametros["Body"] = ($Cuerpo | ConvertTo-Json -Depth 6 -Compress)
        $parametros["ContentType"] = "application/json; charset=utf-8"
    }
    Invoke-RestMethod @parametros
}

# Windows PowerShell NO desenrolla el array que devuelve una función: `@(Llamar ...)` da
# un único elemento que es la lista entera, `.Count` vale 1 y los filtros no encuentran
# nada, sin un solo error a la vista. Todo lo que devuelva lista pasa por acá.
function ListaDe {
    param([string]$Ruta)
    $respuesta = Llamar GET $Ruta
    @($respuesta)
}

# Un horario que otro turno acaba de tomar no puede abortar la siembra entera.
function Intentar {
    param([string]$Que, [scriptblock]$Accion)
    try { & $Accion }
    catch { Write-Host "   . se saltea $Que : $($_.Exception.Message)" -ForegroundColor DarkYellow; $null }
}

$script:Clientes = @(
    @{ n = "Matias Gonzalez"; t = "3855123456" }, @{ n = "Lucia Fernandez"; t = "3854987654" },
    @{ n = "Grupo del jueves"; t = "3855222111" }, @{ n = "Pablo Herrera";  t = "3854556677" },
    @{ n = "Sofia Ledesma";   t = "3855889900" }, @{ n = "Nico Paz";       t = "3854332211" },
    @{ n = "Carla Juarez";    t = "3855667788" }, @{ n = "Diego Coronel";  t = "3854110099" },
    @{ n = "Ramiro Salvatierra"; t = "3855443322" }, @{ n = "Belen Acosta"; t = "3854778899" }
)

# ---------------------------------------------------------------- la jornada

function SembrarJornada {
    param([string]$Url)

    $script:Base = $Url.TrimEnd('/')
    $script:Token = $null

    Write-Host "`n=== $($script:Base) ===" -ForegroundColor Cyan
    $script:Token = (Llamar POST "/auth/login" @{ username = $Usuario; password = $Clave }).token
    if (-not $script:Token) { throw "No se pudo iniciar sesion en $($script:Base)." }

    # `/api/lugares` devuelve solo las sedes activas; las archivadas siguen teniendo
    # canchas cargadas y sembrar sobre ellas llenaría una grilla que nadie mira.
    $lugares = ListaDe "/api/lugares"
    if ($lugares.Count -eq 0) { throw "No hay ninguna sede activa." }
    $sede = $lugares[0]
    $canchas = @((ListaDe "/api/canchas?lugarId=$($sede.id)") | Where-Object { $_.activo })
    if ($canchas.Count -eq 0) { throw "La sede '$($sede.nombre)' no tiene canchas activas." }
    Write-Host "  Sede: $($sede.nombre) — $($canchas.Count) canchas" -ForegroundColor Gray

    # La jornada, no el día de calendario: a la 1 AM el club sigue atendiendo la noche de
    # ayer y los turnos se guardan con la fecha de anoche.
    $jornada = (Llamar GET "/api/reservas/jornada-actual").fecha
    Write-Host "  Jornada: $jornada" -ForegroundColor Gray

    # ---- turnos

    # Con scope de script: `Intentar` corre el bloque con `&`, y ahi una asignacion a
    # secas crearia una variable local que se pierde al salir (ver `sembrar-demo.ps1`).
    $script:Creados = @()
    $indice = 0
    foreach ($cancha in $canchas) {
        $slots = ListaDe "/api/reservas/disponibilidad?canchaId=$($cancha.id)&fecha=$jornada"
        $libres = @($slots | Where-Object { $_.disponible })
        if ($libres.Count -eq 0) { continue }

        $cuantos = [Math]::Max(1, [Math]::Round($libres.Count * $Ocupacion))
        # Al azar y no los primeros: si se toman en orden, todas las canchas quedan
        # ocupadas en el mismo bloque y libres en el mismo bloque.
        $elegidos = @($libres | Get-Random -Count ([Math]::Min($cuantos, $libres.Count)))
        foreach ($slot in $elegidos) {
            $cliente = $script:Clientes[$indice % $script:Clientes.Count]; $indice++
            Intentar "$($cancha.nombre) $($slot.horaInicio)" {
                $reserva = Llamar POST "/api/reservas" @{
                    canchaId        = $cancha.id
                    fecha           = $jornada
                    horaInicio      = $slot.horaInicio
                    duracionMin     = $slot.opciones[0].minutos
                    clienteNombre   = $cliente.n
                    clienteTelefono = $cliente.t
                }
                # Uno de cada seis queda sin confirmar: es lo que le da algo que resolver
                # a la tarjeta "Por confirmar" del Panel.
                if ($indice % 6 -ne 0) {
                    Llamar PATCH "/api/reservas/$($reserva.id)/confirmar" | Out-Null
                    $script:Creados += $reserva
                }
            }
        }
    }
    Write-Host "  $($script:Creados.Count) turnos confirmados" -ForegroundColor Gray

    # ---- kiosco anotado en el turno y ventas de mostrador

    $productos = @((ListaDe "/api/productos") | Where-Object { $_.activo })
    if ($productos.Count -gt 0) {
        foreach ($turno in ($script:Creados | Select-Object -First 5)) {
            $item = $productos | Get-Random
            Intentar "consumo de $($turno.clienteNombre)" {
                Llamar POST "/api/ventas" @{
                    items = @(@{ productoId = $item.id; cantidad = 2 }); medio = $null; reservaId = $turno.id
                } | Out-Null
            }
        }
        foreach ($medio in @("EFECTIVO", "EFECTIVO", "EFECTIVO", "TARJETA", "TRANSFERENCIA", "MERCADO_PAGO")) {
            $item = $productos | Get-Random
            Intentar "venta de mostrador" {
                Llamar POST "/api/ventas" @{ items = @(@{ productoId = $item.id; cantidad = 1 }); medio = $medio } | Out-Null
            }
        }
    }

    # ---- cobros: parte cobrada, parte con saldo

    # Se cobra poco más de la mitad a propósito: una caja con todo cobrado no deja ver
    # para qué sirve la pantalla, que justamente es saber qué falta cobrar.
    $delDia = ListaDe "/api/reservas/del-dia"
    $cobrables = @($delDia | Where-Object { $_.estado -eq "CONFIRMADA" -and $_.saldoPendiente -gt 0 })
    $aCobrar = [Math]::Round($cobrables.Count * 0.6)
    $medios = @("EFECTIVO", "EFECTIVO", "EFECTIVO", "TRANSFERENCIA", "TARJETA", "MERCADO_PAGO")
    $i = 0
    foreach ($turno in ($cobrables | Select-Object -First $aCobrar)) {
        Intentar "cobro de $($turno.clienteNombre)" {
            Llamar POST "/api/reservas/$($turno.id)/cobros" `
                @{ monto = $turno.saldoPendiente; medio = $medios[$i % $medios.Count]; notas = $null } | Out-Null
        }
        $i++
    }
    Write-Host "  $aCobrar turnos cobrados de $($cobrables.Count) con saldo" -ForegroundColor Gray

    # ---- gastos del día

    foreach ($gasto in @(
        @{ fecha = $jornada; categoria = "INSUMOS";       descripcion = "Pelotas para el club";   monto = 54000; medio = "EFECTIVO" },
        @{ fecha = $jornada; categoria = "MANTENIMIENTO"; descripcion = "Arreglo de una luminaria"; monto = 32000; medio = "EFECTIVO" }
    )) {
        Intentar $gasto.descripcion { Llamar POST "/api/gastos" $gasto | Out-Null }
    }

    $panel = Llamar GET "/api/home/admin-dashboard"
    Write-Host "  Panel: jornada $($panel.fechaJornada)" -ForegroundColor Green
}

# ---------------------------------------------------------------- main

$objetivos = if ($Todas) {
    # Royal 8080, UPCN 8085, Padel Mania 8086, Oma Sur 8087, Oma Norte 8088, La Costa 8089.
    @(8080, 8085, 8086, 8087, 8088, 8089) | ForEach-Object { "http://localhost:$_" }
} else { @($Api) }

foreach ($url in $objetivos) {
    try { SembrarJornada $url }
    catch { Write-Host "  ERROR en $url : $($_.Exception.Message)" -ForegroundColor Red }
}

Write-Host "`nListo. Abri el Panel y revisa que el dia se vea cargado.`n" -ForegroundColor Green
