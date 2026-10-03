/**
 * Asistencia remota por WebRTC.
 *
 * Permite compartir el flujo de la cámara del móvil con un técnico de obra o
 * con la oficina técnica para validar medidas en vivo, sin salir del navegador.
 *
 * La señalización usa el backend de DrywallPro (SSE + POST) en lugar de abrir
 * un WebSocket propio, de modo que no se añaden dependencias ni puertos:
 *   GET  /api/ar/signal/:room      → flujo de mensajes entrantes (SSE)
 *   POST /api/ar/signal/:room      → publicar oferta / respuesta / ICE
 *   POST /api/ar/rooms             → crear sala efímera con TTL
 */

export interface SignalingMessage {
  type: 'offer' | 'answer' | 'candidate' | 'hello' | 'bye' | 'data';
  from?: string;
  sdp?: string;
  candidate?: RTCIceCandidateInit | null;
  payload?: unknown;
}

export interface SignalingTransport {
  send(message: SignalingMessage): Promise<void>;
  onMessage(handler: (message: SignalingMessage) => void): void;
  close(): void;
  readonly connected: boolean;
}

const ICE_SERVERS: RTCIceServer[] = [
  { urls: ['stun:stun.l.google.com:19302', 'stun:stun1.l.google.com:19302'] }
];

/**
 * Transporte de señalización sobre HTTP: Server-Sent Events para recibir y
 * POST para enviar. Funciona detrás de cualquier proxy corporativo.
 */
export function createHttpSignaling(room: string, selfId: string): SignalingTransport {
  let handler: ((message: SignalingMessage) => void) | null = null;
  let source: EventSource | null = null;
  let connected = false;

  const connect = (): void => {
    if (typeof EventSource === 'undefined') return;
    source = new EventSource(`/api/ar/signal/${encodeURIComponent(room)}?peer=${encodeURIComponent(selfId)}`);
    source.onopen = () => {
      connected = true;
    };
    source.onmessage = (event) => {
      try {
        const parsed = JSON.parse(event.data) as SignalingMessage;
        if (parsed.from === selfId) return; // no se procesan los propios mensajes
        handler?.(parsed);
      } catch {
        /* Se ignoran mensajes mal formados. */
      }
    };
    source.onerror = () => {
      connected = false;
    };
  };
  connect();

  return {
    get connected() {
      return connected;
    },
    onMessage(fn) {
      handler = fn;
    },
    async send(message) {
      await fetch(`/api/ar/signal/${encodeURIComponent(room)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ ...message, from: selfId })
      });
    },
    close() {
      source?.close();
      source = null;
      connected = false;
    }
  };
}

export interface RemoteAssistOptions {
  room: string;
  /** Flujo local (cámara) que se comparte. */
  stream: MediaStream;
  onRemoteStream?: (stream: MediaStream) => void;
  onData?: (payload: unknown) => void;
  onStatus?: (status: AssistStatus) => void;
  onError?: (error: Error) => void;
}

export type AssistStatus = 'idle' | 'waiting' | 'connecting' | 'connected' | 'closed' | 'error';

/**
 * Sesión de asistencia remota: el móvil publica la cámara y un segundo
 * dispositivo (ordenador de obra) la visualiza para validar la medición.
 */
export class RemoteAssistSession {
  readonly room: string;
  readonly selfId: string;
  private pc: RTCPeerConnection;
  private signaling: SignalingTransport;
  private channel: RTCDataChannel | null = null;
  private status: AssistStatus = 'idle';
  private readonly opts: RemoteAssistOptions;
  /** `true` si este extremo inicia la llamada (crea la oferta). */
  private readonly initiator: boolean;

  private constructor(opts: RemoteAssistOptions, initiator: boolean, selfId: string) {
    this.opts = opts;
    this.room = opts.room;
    this.selfId = selfId;
    this.initiator = initiator;
    this.pc = new RTCPeerConnection({ iceServers: ICE_SERVERS });
    this.signaling = createHttpSignaling(this.room, this.selfId);
    this.setup();
  }

  static host(options: Omit<RemoteAssistOptions, 'room'> & { room?: string }): RemoteAssistSession {
    const room = options.room ?? `obra-${Math.random().toString(36).slice(2, 8)}`;
    const selfId = `host-${Math.random().toString(36).slice(2, 8)}`;
    return new RemoteAssistSession({ ...options, room }, true, selfId);
  }

  static join(room: string, options: Omit<RemoteAssistOptions, 'room' | 'stream'>): RemoteAssistSession {
    const selfId = `guest-${Math.random().toString(36).slice(2, 8)}`;
    const stream = new MediaStream();
    return new RemoteAssistSession({ ...options, room, stream }, false, selfId);
  }

  private setup(): void {
    const { pc } = this;

    pc.onicecandidate = (event) => {
      if (event.candidate) {
        void this.signaling.send({ type: 'candidate', candidate: event.candidate.toJSON() });
      }
    };
    pc.onconnectionstatechange = () => {
      const state = pc.connectionState;
      if (state === 'connected') this.setStatus('connected');
      else if (state === 'failed') {
        this.setStatus('error');
        this.opts.onError?.(new Error('La conexión WebRTC ha fallado.'));
      } else if (state === 'closed') this.setStatus('closed');
      else if (state === 'connecting') this.setStatus('connecting');
    };
    pc.ontrack = (event) => {
      if (event.streams?.[0]) this.opts.onRemoteStream?.(event.streams[0]);
    };

    this.signaling.onMessage(async (message) => {
      try {
        switch (message.type) {
          case 'hello':
            if (this.initiator) await this.createOffer();
            break;
          case 'offer':
            if (!this.initiator) {
              await pc.setRemoteDescription({ type: 'offer', sdp: message.sdp });
              const answer = await pc.createAnswer();
              await pc.setLocalDescription(answer);
              await this.signaling.send({ type: 'answer', sdp: answer.sdp });
              this.setStatus('connecting');
            }
            break;
          case 'answer':
            if (pc.signalingState !== 'stable') {
              await pc.setRemoteDescription({ type: 'answer', sdp: message.sdp });
              this.setStatus('connecting');
            }
            break;
          case 'candidate':
            if (message.candidate) await pc.addIceCandidate(message.candidate).catch(() => undefined);
            break;
          case 'data':
            this.opts.onData?.(message.payload);
            break;
          case 'bye':
            this.setStatus('closed');
            this.close();
            break;
          default:
            break;
        }
      } catch (error) {
        this.opts.onError?.(error instanceof Error ? error : new Error(String(error)));
      }
    });

    // Canal de datos para intercambiar medidas y anotaciones.
    if (this.initiator) {
      this.channel = pc.createDataChannel('drywall-metrics');
      this.bindChannel(this.channel);
    } else {
      pc.ondatachannel = (event) => {
        this.channel = event.channel;
        this.bindChannel(event.channel);
      };
    }

    this.opts.stream.getTracks().forEach((track) => {
      const sender = pc.getSenders().find((s) => s.track?.kind === track.kind);
      if (sender) void sender.replaceTrack(track);
      else pc.addTrack(track, this.opts.stream);
    });

    this.setStatus('waiting');
    void this.signaling.send({ type: 'hello' });
  }

  private bindChannel(channel: RTCDataChannel): void {
    channel.onmessage = (event) => {
      try {
        this.opts.onData?.(JSON.parse(event.data));
      } catch {
        this.opts.onData?.(event.data);
      }
    };
  }

  private async createOffer(): Promise<void> {
    const offer = await this.pc.createOffer({ offerToReceiveAudio: false, offerToReceiveVideo: !this.initiator });
    await this.pc.setLocalDescription(offer);
    await this.signaling.send({ type: 'offer', sdp: offer.sdp });
    this.setStatus('connecting');
  }

  /** Envía medidas o anotaciones al extremo remoto. */
  send(payload: unknown): void {
    if (this.channel && this.channel.readyState === 'open') {
      this.channel.send(JSON.stringify(payload));
      return;
    }
    void this.signaling.send({ type: 'data', payload });
  }

  getStatus(): AssistStatus {
    return this.status;
  }

  private setStatus(status: AssistStatus): void {
    this.status = status;
    this.opts.onStatus?.(status);
  }

  close(): void {
    try {
      this.pc.getSenders().forEach((sender) => sender.track?.stop());
    } catch {
      /* ignore */
    }
    this.pc.close();
    this.signaling.close();
    this.setStatus('closed');
  }
}

/** Crea una sala efímera en el servidor y devuelve su identificador. */
export async function createAssistRoom(ttlSeconds = 1800): Promise<{ room: string; expiresAt: number }> {
  const res = await fetch('/api/ar/rooms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ ttlSeconds })
  });
  if (!res.ok) throw new Error('No se pudo crear la sala de asistencia remota.');
  return (await res.json()) as { room: string; expiresAt: number };
}
