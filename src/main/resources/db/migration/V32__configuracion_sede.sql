CREATE TABLE configuracion_sede (
    id BIGINT PRIMARY KEY,
    email VARCHAR(255),
    telefono VARCHAR(60),
    whatsapp VARCHAR(60),
    instagram VARCHAR(255),
    facebook VARCHAR(255),
    direccion VARCHAR(255),
    maps_embed_url TEXT,
    texto_precios TEXT,
    texto_sede TEXT,
    texto_contacto TEXT,
    horarios_json TEXT,
    servicios_json TEXT,
    galeria_json TEXT,
    formas_pago_json TEXT
);

INSERT INTO configuracion_sede (
    id, email, telefono, whatsapp, instagram, facebook, direccion, maps_embed_url,
    texto_precios, texto_sede, texto_contacto,
    horarios_json, servicios_json, galeria_json, formas_pago_json
) VALUES (
    1,
    'info@rankpadel.com',
    '+54 9 385 689-4061',
    '5493856894061',
    'https://instagram.com/',
    'https://facebook.com/',
    'Av. del Padel 123, Santiago del Estero',
    'https://www.google.com/maps?q=Santiago%20del%20Estero,%20Argentina&output=embed',
    'Reservá tu cancha online y pagá la seña para asegurar el turno. El precio es por hora; el valor final depende de la duración del turno que elijas.',
    'Mucho más que canchas: un lugar para venir a jugar, comer algo y compartir.',
    'Querés reservar una cancha o tenés una consulta sobre torneos e inscripciones? Escribinos.',
    '[{"dias":"Lunes a Viernes","horas":"08:00 - 00:00"},{"dias":"Sabados","horas":"09:00 - 01:00"},{"dias":"Domingos y feriados","horas":"09:00 - 23:00"}]',
    '[{"icono":"beer","titulo":"Bar y cantina","descripcion":"Bebidas, snacks y comidas antes y despues de jugar."},{"icono":"shower","titulo":"Vestuarios","descripcion":"Vestuarios con duchas para hombres y mujeres."},{"icono":"dumbbell","titulo":"Alquiler de paletas","descripcion":"Paletas y pelotas disponibles en recepcion."},{"icono":"car","titulo":"Estacionamiento","descripcion":"Espacio para dejar tu auto mientras jugas."},{"icono":"wifi","titulo":"WiFi","descripcion":"Conexion libre en todo el complejo."},{"icono":"shirt","titulo":"Pro shop","descripcion":"Indumentaria y accesorios de padel."}]',
    '[{"url":"/images/fondo cancha.jpg","alt":"Cancha de padel"},{"url":"/images/tapia.png","alt":"Jugador en accion"},{"url":"/images/galan.jpg","alt":"Partido en el complejo"},{"url":"/images/coello.jpg","alt":"Vista del complejo"}]',
    '["Efectivo","Transferencia","Mercado Pago (sena online)"]'
);
