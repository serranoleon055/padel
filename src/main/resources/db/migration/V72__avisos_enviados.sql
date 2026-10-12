-- A quién ya se le avisó por WhatsApp y con qué mensaje (su huella). Si el horario cambia,
-- el mensaje que correspondería es otro y el panel avisa que hay que reenviarlo.
CREATE TABLE avisos_enviados (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    torneo_id BIGINT NOT NULL,
    jugador_id BIGINT NOT NULL,
    tema VARCHAR(60) NOT NULL,
    clave VARCHAR(64) NOT NULL,
    enviado_en DATETIME NOT NULL,
    enviado_por VARCHAR(100) NULL,
    CONSTRAINT fk_aviso_torneo FOREIGN KEY (torneo_id) REFERENCES torneos (id),
    CONSTRAINT fk_aviso_jugador FOREIGN KEY (jugador_id) REFERENCES jugadores (id),
    CONSTRAINT uk_aviso UNIQUE (torneo_id, jugador_id, tema)
);
