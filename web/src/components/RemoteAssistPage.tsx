import React, { useCallback, useEffect, useRef, useState } from 'react';
import {
  Video,
  VideoOff,
  Link2,
  Copy,
  CheckCircle2,
  AlertTriangle,
  Loader2,
  Users,
  ShieldCheck,
  Mic,
  MicOff,
  PhoneOff
} from 'lucide-react';
import {
  AssistStatus,
  RemoteAssistSession,
  createAssistRoom,
  openCamera,
  stopCamera
} from '../lib/ar';

/**
 * Sala de asistencia remota: comparte la cámara del móvil con la oficina
 * técnica para validar medidas y replanteo en directo.
 *
 * La conexión es P2P (WebRTC con DTLS-SRTP); el servidor sólo retransmite la
 * señalización (oferta/respuesta/ICE) y no ve ni graba el vídeo.
 */
export const RemoteAssistPage: React.FC = () => {
  const [status, setStatus] = useState<AssistStatus>('idle');
  const [room, setRoom] = useState<string>(() => {
    const params = new URLSearchParams(window.location.search);
    return params.get('sala') ?? '';
  });
  const [role, setRole] = useState<'host' | 'guest' | null>(null);
  const [messages, setMessages] = useState<string[]>([]);
  const [localStream, setLocalStream] = useState<MediaStream | null>(null);
  const [remoteReady, setRemoteReady] = useState(false);
  const [withAudio, setWithAudio] = useState(false);
  const [copied, setCopied] = useState(false);

  const sessionRef = useRef<RemoteAssistSession | null>(null);
  const localVideoRef = useRef<HTMLVideoElement | null>(null);
  const remoteVideoRef = useRef<HTMLVideoElement | null>(null);

  const log = useCallback((text: string) => {
    setMessages((prev) => [...prev.slice(-6), `${new Date().toLocaleTimeString()} · ${text}`]);
  }, []);

  const cleanup = useCallback(() => {
    sessionRef.current?.close();
    sessionRef.current = null;
    stopCamera(localStream);
    setLocalStream(null);
    setRemoteReady(false);
    setStatus('idle');
  }, [localStream]);

  useEffect(() => () => cleanup(), [cleanup]);

  /** El móvil publica la cámara (host). */
  const startHost = async (): Promise<void> => {
    try {
      setStatus('waiting');
      const created = await createAssistRoom(1800);
      setRoom(created.room);
      setRole('host');
      const stream = await openCamera({ facingMode: 'environment' });
      setLocalStream(stream);
      if (localVideoRef.current) {
        localVideoRef.current.srcObject = stream;
        await localVideoRef.current.play().catch(() => undefined);
      }
      const session = RemoteAssistSession.host({
        room: created.room,
        stream,
        onStatus: (s) => setStatus(s),
        onRemoteStream: (remote) => {
          setRemoteReady(true);
          if (remoteVideoRef.current) {
            remoteVideoRef.current.srcObject = remote;
            void remoteVideoRef.current.play().catch(() => undefined);
          }
        },
        onError: (error) => log(`Error: ${error.message}`)
      });
      sessionRef.current = session;
      log(`Sala ${created.room} creada. Envía el enlace al técnico.`);
    } catch (error) {
      setStatus('error');
      log(error instanceof Error ? error.message : 'No se pudo iniciar la emisión.');
    }
  };

  /** La oficina técnica se conecta a la sala existente (guest). */
  const joinAsGuest = async (roomId: string): Promise<void> => {
    if (!roomId) {
      log('Falta el identificador de la sala.');
      return;
    }
    try {
      setStatus('connecting');
      setRole('guest');
      const session = RemoteAssistSession.join(roomId, {
        onStatus: (s) => setStatus(s),
        onRemoteStream: (remote) => {
          setRemoteReady(true);
          if (remoteVideoRef.current) {
            remoteVideoRef.current.srcObject = remote;
            void remoteVideoRef.current.play().catch(() => undefined);
          }
        },
        onData: (payload) => log(`Dato recibido: ${JSON.stringify(payload).slice(0, 120)}`),
        onError: (error) => log(`Error: ${error.message}`)
      });
      sessionRef.current = session;
      log(`Conectando a la sala ${roomId}…`);
    } catch (error) {
      setStatus('error');
      log(error instanceof Error ? error.message : 'No se pudo conectar.');
    }
  };

  const shareUrl = room ? `${window.location.origin}/asistencia?sala=${encodeURIComponent(room)}` : '';

  const statusColor: Record<AssistStatus, string> = {
    idle: 'text-slate-400',
    waiting: 'text-amber-400',
    connecting: 'text-sky-400',
    connected: 'text-emerald-400',
    closed: 'text-slate-500',
    error: 'text-rose-400'
  };

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 px-4 sm:px-6 lg:px-8 py-8">
      <div className="max-w-5xl mx-auto space-y-6">
        <header className="flex items-center gap-3">
          <div className="w-11 h-11 rounded-xl bg-gradient-to-tr from-violet-600 to-fuchsia-400 flex items-center justify-center text-white shadow-lg shadow-violet-500/20">
            <Users className="w-5 h-5" />
          </div>
          <div>
            <h1 className="text-lg font-bold">Asistencia remota en obra</h1>
            <p className="text-xs text-slate-400">
              Comparte la cámara del móvil con la oficina técnica para validar mediciones y replanteo en directo.
            </p>
          </div>
          <a
            href="/"
            className="ml-auto px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-xs font-semibold text-slate-200"
          >
            Volver a la calculadora
          </a>
        </header>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
          {/* Vídeo local */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden">
            <div className="px-4 py-2.5 border-b border-slate-800 flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-300">Tu cámara</span>
              <span className={`text-[11px] font-mono ${statusColor[status]}`}>
                {status === 'waiting'
                  ? 'esperando al técnico…'
                  : status === 'connected'
                    ? 'conectado'
                    : status === 'connecting'
                      ? 'conectando…'
                      : status === 'error'
                        ? 'error'
                        : 'inactivo'}
              </span>
            </div>
            <div className="aspect-video bg-slate-950 flex items-center justify-center">
              <video ref={localVideoRef} playsInline muted className="w-full h-full object-contain" />
              {!localStream && <VideoOff className="w-8 h-8 text-slate-700" />}
            </div>
          </div>

          {/* Vídeo remoto */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl overflow-hidden">
            <div className="px-4 py-2.5 border-b border-slate-800 flex items-center justify-between">
              <span className="text-xs font-semibold text-slate-300">Vídeo recibido</span>
              {remoteReady ? (
                <span className="text-[11px] font-mono text-emerald-400 flex items-center gap-1">
                  <CheckCircle2 className="w-3 h-3" /> en directo
                </span>
              ) : (
                <span className="text-[11px] font-mono text-slate-500">sin señal</span>
              )}
            </div>
            <div className="aspect-video bg-slate-950 flex items-center justify-center">
              <video ref={remoteVideoRef} playsInline className="w-full h-full object-contain" />
              {!remoteReady && <Video className="w-8 h-8 text-slate-700" />}
            </div>
          </div>
        </div>

        {/* Controles */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-4 space-y-4">
          <div className="flex flex-wrap items-center gap-2">
            {role !== 'host' && (
              <button
                onClick={() => void startHost()}
                disabled={status === 'waiting' || status === 'connected'}
                className="px-4 py-2 rounded-lg bg-violet-600 hover:bg-violet-500 disabled:opacity-40 text-white text-xs font-semibold flex items-center gap-2"
              >
                <Video className="w-4 h-4" /> Emitir cámara (móvil en obra)
              </button>
            )}

            {role !== 'guest' && (
              <div className="flex items-center gap-2">
                <input
                  value={room}
                  onChange={(e) => setRoom(e.target.value.trim())}
                  placeholder="código de sala (obra-ab12cd)"
                  className="bg-slate-950 border border-slate-700 rounded-lg px-2.5 py-2 text-xs text-slate-200 w-52"
                />
                <button
                  onClick={() => void joinAsGuest(room)}
                  disabled={status === 'connected' || status === 'connecting'}
                  className="px-4 py-2 rounded-lg bg-sky-600 hover:bg-sky-500 disabled:opacity-40 text-white text-xs font-semibold flex items-center gap-2"
                >
                  <Link2 className="w-4 h-4" /> Conectar a la sala
                </button>
              </div>
            )}

            <button
              onClick={() => setWithAudio((v) => !v)}
              className="px-3 py-2 rounded-lg bg-slate-800 hover:bg-slate-700 border border-slate-700 text-slate-200 text-xs font-semibold flex items-center gap-2"
              title="El audio se desactiva por defecto para ahorrar datos"
            >
              {withAudio ? <Mic className="w-3.5 h-3.5" /> : <MicOff className="w-3.5 h-3.5" />}
              {withAudio ? 'Audio activado' : 'Audio desactivado'}
            </button>

            {(status === 'connected' || status === 'waiting' || status === 'connecting') && (
              <button
                onClick={cleanup}
                className="px-3 py-2 rounded-lg bg-rose-600/90 hover:bg-rose-500 text-white text-xs font-semibold flex items-center gap-2"
              >
                <PhoneOff className="w-3.5 h-3.5" /> Finalizar
              </button>
            )}

            {status === 'waiting' && <Loader2 className="w-4 h-4 text-amber-400 animate-spin" />}
          </div>

          {shareUrl && (
            <div className="flex items-center gap-2 bg-slate-950 border border-slate-800 rounded-lg px-3 py-2">
              <code className="text-[11px] text-slate-300 truncate flex-1">{shareUrl}</code>
              <button
                onClick={() => {
                  void navigator.clipboard?.writeText(shareUrl).then(
                    () => {
                      setCopied(true);
                      setTimeout(() => setCopied(false), 2000);
                    },
                    () => log('El navegador bloqueó el portapapeles; copia el enlace manualmente.')
                  );
                }}
                className="px-2.5 py-1 rounded-md bg-slate-800 hover:bg-slate-700 text-[11px] text-slate-200 flex items-center gap-1.5"
              >
                {copied ? <CheckCircle2 className="w-3 h-3 text-emerald-400" /> : <Copy className="w-3 h-3" />}
                {copied ? 'Copiado' : 'Copiar enlace'}
              </button>
            </div>
          )}

          <div className="flex items-start gap-2 text-[11px] text-slate-400">
            <ShieldCheck className="w-3.5 h-3.5 mt-0.5 shrink-0 text-emerald-400" />
            <span>
              El vídeo viaja cifrado punto a punto (DTLS-SRTP). El servidor de DrywallPro sólo intercambia los mensajes
              de señalización; no almacena ni graba imágenes. La sala caduca automáticamente a los 30 minutos.
            </span>
          </div>

          {status === 'error' && (
            <div className="flex items-start gap-2 text-[11px] text-rose-300 bg-rose-500/10 border border-rose-500/25 rounded-lg px-3 py-2">
              <AlertTriangle className="w-3.5 h-3.5 mt-0.5 shrink-0" />
              <span>
                No se pudo establecer la conexión. Comprueba que ambos dispositivos tienen red y que el backend está en
                marcha (`npm run dev:api`), pues la señalización pasa por `/api/ar/signal`.
              </span>
            </div>
          )}

          {messages.length > 0 && (
            <div className="space-y-1">
              {messages.map((message, i) => (
                <p key={i} className="text-[11px] font-mono text-slate-400">
                  {message}
                </p>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default RemoteAssistPage;
