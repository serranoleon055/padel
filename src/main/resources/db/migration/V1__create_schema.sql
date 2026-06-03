CREATE TABLE lugares (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    direccion VARCHAR(200)
);

CREATE TABLE temporadas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE NOT NULL,
    activa BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE categorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    nivel INT NOT NULL,
    genero VARCHAR(20) NOT NULL
);

CREATE TABLE torneos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(150) NOT NULL,
    formato VARCHAR(30) NOT NULL,
    estado VARCHAR(30) NOT NULL DEFAULT 'BORRADOR',
    fecha_inicio DATE NOT NULL,
    fecha_fin DATE,
    es_mixto BOOLEAN NOT NULL DEFAULT FALSE,
    suma_puntos_ranking BOOLEAN NOT NULL DEFAULT TRUE,
    tipo_sorteo VARCHAR(30),
    lugar_id BIGINT,
    temporada_id BIGINT,
    CONSTRAINT fk_torneo_lugar FOREIGN KEY (lugar_id) REFERENCES lugares(id),
    CONSTRAINT fk_torneo_temporada FOREIGN KEY (temporada_id) REFERENCES temporadas(id)
);

CREATE TABLE torneo_categorias (
    torneo_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    PRIMARY KEY (torneo_id, categoria_id),
    CONSTRAINT fk_tc_torneo FOREIGN KEY (torneo_id) REFERENCES torneos(id),
    CONSTRAINT fk_tc_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE jugadores (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(80) NOT NULL,
    apellido VARCHAR(80) NOT NULL,
    genero VARCHAR(20) NOT NULL,
    foto_url VARCHAR(500),
    fecha_registro DATE NOT NULL,
    categoria_id BIGINT,
    CONSTRAINT fk_jugador_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE grupos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(20) NOT NULL,
    torneo_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    CONSTRAINT fk_grupo_torneo FOREIGN KEY (torneo_id) REFERENCES torneos(id),
    CONSTRAINT fk_grupo_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE parejas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    es_cabeza_de_serie BOOLEAN NOT NULL DEFAULT FALSE,
    estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    torneo_id BIGINT NOT NULL,
    jugador1_id BIGINT NOT NULL,
    jugador2_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    grupo_id BIGINT,
    CONSTRAINT fk_pareja_torneo FOREIGN KEY (torneo_id) REFERENCES torneos(id),
    CONSTRAINT fk_pareja_jugador1 FOREIGN KEY (jugador1_id) REFERENCES jugadores(id),
    CONSTRAINT fk_pareja_jugador2 FOREIGN KEY (jugador2_id) REFERENCES jugadores(id),
    CONSTRAINT fk_pareja_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    CONSTRAINT fk_pareja_grupo FOREIGN KEY (grupo_id) REFERENCES grupos(id)
);

CREATE TABLE rondas_eliminatorias (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre VARCHAR(50) NOT NULL,
    orden INT NOT NULL,
    torneo_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    CONSTRAINT fk_ronda_torneo FOREIGN KEY (torneo_id) REFERENCES torneos(id),
    CONSTRAINT fk_ronda_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id)
);

CREATE TABLE partidos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    marcador VARCHAR(50),
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fase VARCHAR(20) NOT NULL,
    fecha_hora DATETIME,
    torneo_id BIGINT NOT NULL,
    pareja_local_id BIGINT,
    pareja_visitante_id BIGINT,
    ganador_id BIGINT,
    grupo_id BIGINT,
    ronda_id BIGINT,
    CONSTRAINT fk_partido_torneo FOREIGN KEY (torneo_id) REFERENCES torneos(id),
    CONSTRAINT fk_partido_local FOREIGN KEY (pareja_local_id) REFERENCES parejas(id),
    CONSTRAINT fk_partido_visitante FOREIGN KEY (pareja_visitante_id) REFERENCES parejas(id),
    CONSTRAINT fk_partido_ganador FOREIGN KEY (ganador_id) REFERENCES parejas(id),
    CONSTRAINT fk_partido_grupo FOREIGN KEY (grupo_id) REFERENCES grupos(id),
    CONSTRAINT fk_partido_ronda FOREIGN KEY (ronda_id) REFERENCES rondas_eliminatorias(id)
);

CREATE TABLE configuracion_puntos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    nombre_ronda VARCHAR(50) NOT NULL,
    puntos_ganador INT NOT NULL DEFAULT 0,
    puntos_perdedor INT NOT NULL DEFAULT 0,
    orden INT NOT NULL,
    torneo_id BIGINT NOT NULL,
    CONSTRAINT fk_config_torneo FOREIGN KEY (torneo_id) REFERENCES torneos(id)
);

CREATE TABLE posiciones_grupo (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    posicion INT NOT NULL DEFAULT 0,
    pj INT NOT NULL DEFAULT 0,
    pg INT NOT NULL DEFAULT 0,
    pp INT NOT NULL DEFAULT 0,
    puntos INT NOT NULL DEFAULT 0,
    grupo_id BIGINT NOT NULL,
    pareja_id BIGINT NOT NULL,
    CONSTRAINT fk_pos_grupo FOREIGN KEY (grupo_id) REFERENCES grupos(id),
    CONSTRAINT fk_pos_pareja FOREIGN KEY (pareja_id) REFERENCES parejas(id),
    CONSTRAINT uq_posicion_grupo_pareja UNIQUE (grupo_id, pareja_id)
);

CREATE TABLE ranking_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    puntos_totales INT NOT NULL DEFAULT 0,
    torneos_jugados INT NOT NULL DEFAULT 0,
    victorias INT NOT NULL DEFAULT 0,
    derrotas INT NOT NULL DEFAULT 0,
    jugador_id BIGINT NOT NULL,
    categoria_id BIGINT NOT NULL,
    temporada_id BIGINT,
    CONSTRAINT fk_ranking_jugador FOREIGN KEY (jugador_id) REFERENCES jugadores(id),
    CONSTRAINT fk_ranking_categoria FOREIGN KEY (categoria_id) REFERENCES categorias(id),
    CONSTRAINT fk_ranking_temporada FOREIGN KEY (temporada_id) REFERENCES temporadas(id),
    CONSTRAINT uq_ranking_jugador_cat_temp UNIQUE (jugador_id, categoria_id, temporada_id)
);

CREATE TABLE admins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL
);