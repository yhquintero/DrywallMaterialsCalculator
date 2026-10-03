/**
 * Señalización de la asistencia remota por WebRTC (móvil en obra ↔ oficina).
 *
 *   POST /api/ar/rooms                → crea una sala efímera
 *   GET  /api/ar/rooms/:room          → estado de la sala
 *   GET  /api/ar/signal/:room         → Server-Sent Events con los mensajes entrantes
 *   POST /api/ar/signal/:room         → publica oferta / respuesta / candidatos ICE
 *
 * El servidor es un simple buzón: nunca ve el vídeo (la conexión es P2P con
 * DTLS-SRTP) y descarta todo al expirar la sala.
 */
import crypto from 'node:crypto';
import { Router } from 'express';
import rateLimit from 'express-rate-limit';
import { z } from 'zod';
import config from '../config/index.js';
import { HttpError } from '../utils/errors.js';
import { sameOriginGuard } from '../security/httpsMiddleware.js';

const router = Router();

const arLimiter = rateLimit({
  windowMs: 60_000,
  limit: 120,
  standardHeaders: 'draft-7',
  legacyHeaders: false,
  message: { error: 'Demasiadas operaciones de asistencia remota.' }
});

router.use(sameOriginGuard);
router.use(arLimiter);

/** Salas activas: { room, createdAt, expiresAt, subscribers:Set, history:[] } */
const rooms = new Map();

const ROOM_ID = /^[a-z0-9][a-z0-9-]{3,39}$/;

function purgeExpired() {
  const now = Date.now();
  for (const [id, room] of rooms) {
    if (room.expiresAt <= now) {
      room.subscribers.forEach((res) => {
        try {
          res.write('event: expired\ndata: {}\n\n');
          res.end();
        } catch {
          /* el cliente ya cerró */
        }
      });
      rooms.delete(id);
    }
  }
}

function getRoom(id) {
  purgeExpired();
  return rooms.get(id);
}

setInterval(purgeExpired, 60_000).unref?.();

const createSchema = z.object({
  ttlSeconds: z.number().int().min(60).max(6 * 3600).default(config.ar.roomTtlSeconds),
  /** Etiqueta opcional de la obra, para mostrarla en la sala. */
  label: z.string().max(80).optional()
});

/** POST /api/ar/rooms */
router.post('/rooms', (req, res, next) => {
  try {
    const parsed = createSchema.safeParse(req.body ?? {});
    if (!parsed.success) throw new HttpError(400, 'Petición inválida.', parsed.error.issues);
    purgeExpired();
    if (rooms.size >= config.ar.maxRooms) {
      throw new HttpError(429, 'Se ha alcanzado el número máximo de salas simultáneas. Inténtalo más tarde.');
    }
    const suffix = crypto.randomBytes(3).toString('hex');
    const room = `obra-${suffix}`;
    const expiresAt = Date.now() + parsed.data.ttlSeconds * 1000;
    rooms.set(room, {
      room,
      label: parsed.data.label || 'Asistencia en obra',
      createdAt: Date.now(),
      expiresAt,
      subscribers: new Set(),
      history: []
    });
    res.status(201).json({ room, expiresAt, ttlSeconds: parsed.data.ttlSeconds });
  } catch (error) {
    next(error);
  }
});

/** GET /api/ar/rooms/:room */
router.get('/rooms/:room', (req, res) => {
  const room = getRoom(String(req.params.room));
  if (!room) return res.status(404).json({ error: 'La sala no existe o ha caducado.' });
  res.json({
    room: room.room,
    label: room.label,
    peers: room.subscribers.size,
    expiresAt: room.expiresAt,
    messages: room.history.length
  });
});

const signalSchema = z.object({
  type: z.enum(['offer', 'answer', 'candidate', 'hello', 'bye', 'data']),
  from: z.string().max(64).optional(),
  sdp: z.string().max(20000).optional(),
  candidate: z.any().optional(),
  payload: z.any().optional()
});

/**
 * GET /api/ar/signal/:room
 * Flujo SSE. Query: ?peer=<identificador> (se usa para hablar de "peers").
 */
router.get('/signal/:room', (req, res) => {
  const roomId = String(req.params.room);
  if (!ROOM_ID.test(roomId)) return res.status(400).json({ error: 'Identificador de sala inválido.' });
  const room = getRoom(roomId);
  if (!room) return res.status(404).json({ error: 'La sala no existe o ha caducado.' });

  const peer = String(req.query.peer || `peer-${crypto.randomBytes(3).toString('hex')}`).slice(0, 64);

  res.writeHead(200, {
    'Content-Type': 'text/event-stream; charset=utf-8',
    'Cache-Control': 'no-store, no-cache, must-revalidate',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no'
  });
  res.write(`event: ready\ndata: ${JSON.stringify({ room: roomId, peer })}\n\n`);

  room.subscribers.add(res);

  // Latido para que los proxies no corten la conexión.
  const heartbeat = setInterval(() => {
    try {
      res.write(': ping\n\n');
    } catch {
      /* ignore */
    }
  }, 20_000);
  heartbeat.unref?.();

  // Se reenvían los mensajes ya presentes (candidatos llegados antes de conectar).
  room.history.slice(-config.ar.maxSignalsPerRoom).forEach((message) => {
    if (message.from !== peer) res.write(`data: ${JSON.stringify(message)}\n\n`);
  });

  const cleanup = () => {
    clearInterval(heartbeat);
    room.subscribers.delete(res);
    // Si no queda nadie, la sala se marca para caducar en 2 minutos.
    if (room.subscribers.size === 0) room.expiresAt = Math.min(room.expiresAt, Date.now() + 120_000);
    try {
      res.end();
    } catch {
      /* ignore */
    }
  };
  req.on('close', cleanup);
  req.on('error', cleanup);
});

/** POST /api/ar/signal/:room */
router.post('/signal/:room', (req, res, next) => {
  try {
    const roomId = String(req.params.room);
    if (!ROOM_ID.test(roomId)) throw new HttpError(400, 'Identificador de sala inválido.');
    const room = getRoom(roomId);
    if (!room) throw new HttpError(404, 'La sala no existe o ha caducado.');

    const parsed = signalSchema.safeParse(req.body);
    if (!parsed.success) throw new HttpError(400, 'Mensaje de señalización inválido.', parsed.error.issues);

    const message = { ...parsed.data, at: Date.now() };
    room.history.push(message);
    if (room.history.length > config.ar.maxSignalsPerRoom) {
      room.history.splice(0, room.history.length - config.ar.maxSignalsPerRoom);
    }

    const payload = `data: ${JSON.stringify(message)}\n\n`;
    let delivered = 0;
    room.subscribers.forEach((subscriber) => {
      try {
        subscriber.write(payload);
        delivered += 1;
      } catch {
        room.subscribers.delete(subscriber);
      }
    });

    // La sala se mantiene viva mientras haya interlocución.
    room.expiresAt = Math.max(room.expiresAt, Date.now() + 120_000);
    res.json({ ok: true, delivered });
  } catch (error) {
    next(error);
  }
});

export default router;
