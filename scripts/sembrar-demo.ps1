<#
    Siembra la operación diaria de una instancia de DEMOSTRACIÓN: productos del kiosco,
    una promoción, un abono, turnos de los próximos días, consumo anotado, cobros y
    gastos. Es lo que hace que el Panel, la Caja y las Estadísticas no se vean vacíos
    cuando se le muestra el sistema a un cliente.

    Los torneos, jugadores y el ranking NO los toca: eso se siembra aparte.

    Uso:
        .\scripts\sembrar-demo.ps1 -Api "https://rankpadel-demo.onrender.com" -Clave "..."

    OJO — dos cosas antes de correrlo:

    1. La sede tiene que tener canchas Y horario de atención cargado. El script pide la
       disponibilidad real de cada cancha y reserva sobre los huecos que devuelve, así
       que si no hay horario configurado no encuentra ningún slot y no siembra nada.

    2. NO siembra días pasados, a propósito: `ReservaService` rechaza reservar un horario
       que ya pasó, y forzarlo con INSERT directo dejaría turnos sin su clave de slot y
       sin precio congelado, que es justo lo que mantiene los números coherentes. El
       historial se llena solo con los días que van pasando; si querés más, volvé a
       correr el script dentro de una semana.
#>
param(
    [string]$Api = "http://localhost:8080",
    [string]$Usuario = "admin",
    [Parameter(Mandatory = $true)][string]$Clave,
    [int]$Dias = 10
)

$ErrorActionPreference = "Stop"
$Api = $Api.TrimEnd('/')

# ---------------------------------------------------------------- helpers

$script:Token = $null

# OJO: Windows PowerShell NO desenrolla el array que devuelve una función. `@(Llamar ...)`
# daba un único elemento que era la lista entera, así que `.Count` siempre valía 1 y
# filtrar con Where-Object no encontraba nada (el primer intento de este script sembró
# cero turnos sin dar un solo error). Hay que pasar por una variable: para eso está
# `ListaDe`, y todo lo que devuelva una lista tiene que ir por ahí.
function ListaDe {
    param([string]$Ruta)
    $respuesta = Llamar GET $Ruta
    @($respuesta)
}

function Llamar {
    param([string]$Metodo, [string]$Ruta, $Cuerpo = $null)
    $encabezados = @{}
    if ($script:Token) { $encabezados["Authorization"] = "Bearer $($script:Token)" }
    $parametros = @{ Method = $Metodo; Uri = "$Api$Ruta"; Headers = $encabezados }
    if ($null -ne $Cuerpo) {
        $parametros["Body"] = ($Cuerpo | ConvertTo-Json -Depth 6 -Compress)
        $parametros["ContentType"] = "application/json; charset=utf-8"
    }
    Invoke-RestMethod @parametros
}

# Los pasos que pueden fallar sin arruinar la siembra (un producto que ya existe, un
# horario que otro turno acaba de tomar) se avisan y se sigue. Si abortara en el primero,
# correr el script dos veces sería imposible.
function Intentar {
    param([string]$Que, [scriptblock]$Accion)
    try { & $Accion }
    catch { Write-Host "   . se saltea $Que : $($_.Exception.Message)" -ForegroundColor DarkYellow; $null }
}

function Fecha([int]$masDias) { (Get-Date).AddDays($masDias).ToString('yyyy-MM-dd') }

# ---------------------------------------------------------------- 1. entrar

Write-Host "`n[1/7] Entrando a $Api" -ForegroundColor Cyan
$script:Token = (Llamar POST "/auth/login" @{ username = $Usuario; password = $Clave }).token
if (-not $script:Token) { throw "No se pudo iniciar sesion." }

$canchas = ListaDe "/api/canchas"
if ($canchas.Count -eq 0) { throw "La sede no tiene canchas cargadas: no hay donde sembrar turnos." }
Write-Host "      $($canchas.Count) canchas" -ForegroundColor Gray

# ---------------------------------------------------------------- 2. kiosco

Write-Host "[2/7] Productos del kiosco" -ForegroundColor Cyan
$catalogo = @(
    @{ nombre = "Agua 500ml";            categoria = "BEBIDAS";   precioVenta = 1500; costo = 800;   stockInicial = 48; stockMinimo = 12 },
    @{ nombre = "Gatorade";              categoria = "BEBIDAS";   precioVenta = 2800; costo = 1700;  stockInicial = 24; stockMinimo = 8 },
    @{ nombre = "Gaseosa linea";         categoria = "BEBIDAS";   precioVenta = 2500; costo = 1500;  stockInicial = 30; stockMinimo = 10 },
    @{ nombre = "Barra de cereal";       categoria = "KIOSCO";    precioVenta = 1200; costo = 700;   stockInicial = 40; stockMinimo = 15 },
    @{ nombre = "Tubo de pelotas";       categoria = "PELOTAS";   precioVenta = 18000; costo = 12000; stockInicial = 6;  stockMinimo = 4 },
    @{ nombre = "Grip";                  categoria = "ACCESORIOS"; precioVenta = 4500; costo = 2600;  stockInicial = 14; stockMinimo = 6 },
    # Sin control de stock: no se agota nunca y no descuenta al venderse.
    @{ nombre = "Alquiler de paleta";    categoria = "ALQUILER";  precioVenta = 3000; costo = 0;     controlaStock = $false }
)
$existentes = ListaDe "/api/productos"
foreach ($producto in $catalogo) {
    if ($existentes.nombre -contains $producto.nombre) { continue }
    Intentar $producto.nombre { Llamar POST "/api/productos" $producto | Out-Null }
}
$productos = ListaDe "/api/productos"
Write-Host "      $($productos.Count) productos en la lista" -ForegroundColor Gray

# ---------------------------------------------------------------- 3. promocion

Write-Host "[3/7] Promocion de las horas flojas" -ForegroundColor Cyan
Intentar "la promocion" {
    Llamar POST "/api/promociones-cancha" @{
        canchaId    = $canchas[0].id
        nombre      = "Promo mediodia"
        diasSemana  = "1,2,3,4,5"
        horaDesde   = "12:00"
        horaHasta   = "17:00"
        precioPorHora = [math]::Round($canchas[0].precioPorHora * 0.75)
        vigenteDesde  = (Fecha 0)
        vigenteHasta  = (Fecha 90)
    } | Out-Null
}

# ---------------------------------------------------------------- 4. abono

Write-Host "[4/7] Abono del grupo de siempre" -ForegroundColor Cyan
Intentar "el abono" {
    $abono = Llamar POST "/api/turnos-fijos" @{
        canchaId       = $canchas[-1].id
        diaSemana      = 2
        horaInicio     = "21:00"
        duracionMin    = 120
        clienteNombre  = "Grupo del martes"
        clienteTelefono = "3855100200"
        vigenteDesde   = (Fecha 0)
        precioPactado  = [math]::Round($canchas[-1].precioPorHora * 2)
    }
    Llamar POST "/api/turnos-fijos/$($abono.id)/generar" | Out-Null
}

# ---------------------------------------------------------------- 5. turnos

Write-Host "[5/7] Turnos de los proximos $Dias dias" -ForegroundColor Cyan
$clientes = @(
    @{ n = "Matias Gonzalez"; t = "3855123456" }, @{ n = "Lucia Fernandez"; t = "3854987654" },
    @{ n = "Grupo del jueves"; t = "3855222111" }, @{ n = "Pablo Herrera";  t = "3854556677" },
    @{ n = "Sofia Ledesma";   t = "3855889900" }, @{ n = "Nico Paz";       t = "3854332211" },
    @{ n = "Carla Juarez";    t = "3855667788" }, @{ n = "Diego Coronel";  t = "3854110099" }
)
$creados = @()
$indice = 0
for ($dia = 0; $dia -lt $Dias; $dia++) {
    $fecha = Fecha $dia
    foreach ($cancha in $canchas) {
        $slots = ListaDe "/api/reservas/disponibilidad?canchaId=$($cancha.id)&fecha=$fecha"
        $libres = @($slots | Where-Object { $_.disponible })
        if ($libres.Count -eq 0) { continue }
        # Los turnos que se venden son los de la noche: se siembra desde el final de la
        # jornada hacia atras para que la grilla se vea como un dia real y no con la
        # mañana llena y la noche vacia.
        $elegidos = @($libres | Select-Object -Last 4 | Get-Random -Count ([Math]::Min(3, $libres.Count)))
        foreach ($slot in $elegidos) {
            $cliente = $clientes[$indice % $clientes.Count]; $indice++
            Intentar "$fecha $($slot.horaInicio)" {
                $reserva = Llamar POST "/api/reservas" @{
                    canchaId        = $cancha.id
                    fecha           = $fecha
                    horaInicio      = $slot.horaInicio
                    duracionMin     = $slot.opciones[0].minutos
                    clienteNombre   = $cliente.n
                    clienteTelefono = $cliente.t
                }
                # Uno de cada cinco queda sin confirmar, para que la tarjeta "Por
                # confirmar" del panel tenga algo que resolver.
                if ($indice % 5 -ne 0) {
                    Llamar PATCH "/api/reservas/$($reserva.id)/confirmar" | Out-Null
                    $script:creados += $reserva
                }
            }
        }
    }
}
Write-Host "      $($creados.Count) turnos confirmados" -ForegroundColor Gray

# ---------------------------------------------------------------- 6. kiosco y caja

Write-Host "[6/7] Consumo, ventas de mostrador y cobros" -ForegroundColor Cyan
$hoy = Fecha 0
$deHoy = @($creados | Where-Object { $_.fecha -eq $hoy })
$conStock = @($productos | Where-Object { $_.activo })

# Consumo anotado en el turno: se cobra al final, junto con la cancha.
foreach ($turno in ($deHoy | Select-Object -First 4)) {
    $item = $conStock | Get-Random
    Intentar "consumo de $($turno.clienteNombre)" {
        Llamar POST "/api/ventas" @{
            items = @(@{ productoId = $item.id; cantidad = 2 }); medio = $null; reservaId = $turno.id
        } | Out-Null
    }
}

# Ventas sueltas: el que entra, compra y se va sin pisar la cancha.
foreach ($medio in @("EFECTIVO", "EFECTIVO", "TARJETA", "TRANSFERENCIA")) {
    $item = $conStock | Get-Random
    Intentar "venta de mostrador" {
        Llamar POST "/api/ventas" @{ items = @(@{ productoId = $item.id; cantidad = 1 }); medio = $medio } | Out-Null
    }
}

# Se cobra parte de lo de hoy y se deja parte con saldo: un cierre de caja en el que ya
# esta todo cobrado no muestra para que sirve la pantalla.
foreach ($turno in ($deHoy | Select-Object -First 3)) {
    Intentar "cobro de $($turno.clienteNombre)" {
        $delDia = ListaDe "/api/reservas/del-dia"
        $saldo = ($delDia | Where-Object { $_.id -eq $turno.id }).saldoPendiente
        if ($saldo -gt 0) {
            Llamar POST "/api/reservas/$($turno.id)/cobros" @{ monto = $saldo; medio = "EFECTIVO"; notas = $null } | Out-Null
        }
    }
}

# ---------------------------------------------------------------- 7. gastos

Write-Host "[7/7] Gastos del club" -ForegroundColor Cyan
# Los gastos SI aceptan fecha pasada (pesan en el mes del gasto, no en el de la carga),
# asi que son los unicos que pueden darle historia a la rentabilidad de entrada.
$gastos = @(
    @{ fecha = (Fecha -3);  categoria = "LUZ";           descripcion = "Factura de luz";            monto = 185000; medio = "TRANSFERENCIA" },
    @{ fecha = (Fecha -12); categoria = "MANTENIMIENTO"; descripcion = "Cambio de red cancha 2";    monto = 95000;  medio = "EFECTIVO" },
    @{ fecha = (Fecha -20); categoria = "SUELDOS";       descripcion = "Sueldo mostrador";          monto = 420000; medio = "TRANSFERENCIA" },
    @{ fecha = (Fecha -34); categoria = "LUZ";           descripcion = "Factura de luz";            monto = 172000; medio = "TRANSFERENCIA" },
    @{ fecha = (Fecha -45); categoria = "MARKETING";     descripcion = "Publicidad del torneo";     monto = 60000;  medio = "EFECTIVO" },
    @{ fecha = (Fecha -60); categoria = "IMPUESTOS";     descripcion = "Municipal";                 monto = 78000;  medio = "TRANSFERENCIA" }
)
foreach ($gasto in $gastos) { Intentar $gasto.descripcion { Llamar POST "/api/gastos" $gasto | Out-Null } }

Write-Host "`nListo. Entra al panel y revisa que el dia se vea cargado.`n" -ForegroundColor Green
