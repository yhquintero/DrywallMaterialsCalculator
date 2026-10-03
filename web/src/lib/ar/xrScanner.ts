/**
 * Controlador de la sesión WebXR `immersive-ar` para medir estancias.
 *
 * Flujo operativo:
 *   1. Se solicita la sesión con `hit-test` (obligatorio) y `dom-overlay`,
 *      `anchors`, `light-estimation` (opcionales).
 *   2. Cada fotograma se lanza un rayo desde el centro de la pantalla; el
 *      hit-test devuelve la pose 3D real (en metros) del punto de impacto.
 *   3. El usuario fija puntos con el evento `select` (toque en pantalla o
 *      disparador del mando). Cada punto se almacena en el espacio de
 *      referencia, de modo que las distancias son directamente métricas.
 *   4. Al cerrar el recorrido se calculan perímetro, área, rectángulo
 *      envolvente y altura con `measurement.ts`.
 */

import { RoomMeasurement, RoomScan, ScanPoint, ScanPointKind, Vec3 } from './types';
import { distance2D, estimateHeight, measureRoom, vec } from './measurement';
import {
  XrSceneRenderer,
  RenderPrimitive,
  buildHeightBarGeometry,
  buildMarkerGeometry,
  buildReticleGeometry,
  buildRibbonGeometry,
  transformPositions
} from './xrRenderer';

export type ScannerStatus = 'idle' | 'starting' | 'running' | 'stopping' | 'ended' | 'error';

export interface ScannerState {
  status: ScannerStatus;
  message: string;
  points: ScanPoint[];
  reticle: { x: number; y: number; z: number } | null;
  /** Distancia desde el último punto fijado hasta el retículo (m). */
  liveDistance: number;
  /** Perímetro acumulado con el tramo en vuelo (m). */
  livePerimeter: number;
  /** Área aproximada en vivo (m²). */
  liveArea: number;
  captureMode: ScanPointKind;
  supportsDomOverlay: boolean;
  supportsHitTest: boolean;
}

export interface ScannerCallbacks {
  onState?: (state: ScannerState) => void;
  onError?: (error: Error) => void;
  onEnded?: (scan: RoomScan | null) => void;
}

function uuid(): string {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return `p_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
}

export class ArRoomScanner {
  private session: XRSession | null = null;
  private renderer: XrSceneRenderer | null = null;
  private gl: WebGLRenderingContext | null = null;
  private refSpace: XRReferenceSpace | null = null;
  private viewerSpace: XRReferenceSpace | null = null;
  private hitTestSource: XRHitTestSource | null = null;
  private frameHandle: number | null = null;
  private points: ScanPoint[] = [];
  private reticlePose: Vec3 | null = null;
  private hitMatrix: Float32Array | null = null;
  private captureMode: ScanPointKind = 'floor';
  private readonly canvas: HTMLCanvasElement;
  private readonly callbacks: ScannerCallbacks;
  private state: ScannerState;
  private reticleGeometry = buildReticleGeometry();
  private lastEmit = 0;

  constructor(canvas: HTMLCanvasElement, callbacks: ScannerCallbacks = {}) {
    this.canvas = canvas;
    this.callbacks = callbacks;
    this.state = {
      status: 'idle',
      message: 'Listo para iniciar el escaneo en Realidad Aumentada.',
      points: [],
      reticle: null,
      liveDistance: 0,
      livePerimeter: 0,
      liveArea: 0,
      captureMode: 'floor',
      supportsDomOverlay: false,
      supportsHitTest: false
    };
  }

  get currentState(): ScannerState {
    return { ...this.state, points: [...this.points] };
  }

  setCaptureMode(mode: ScanPointKind): void {
    this.captureMode = mode;
    this.state.captureMode = mode;
    this.emit(true);
  }

  async start(overlayRoot?: HTMLElement): Promise<void> {
    if (typeof navigator === 'undefined' || !navigator.xr) {
      throw new Error('Este navegador no expone la WebXR Device API.');
    }
    this.setState({ status: 'starting', message: 'Solicitando sesión de Realidad Aumentada…' });

    const session = await navigator.xr.requestSession('immersive-ar', {
      requiredFeatures: ['hit-test'],
      optionalFeatures: ['dom-overlay', 'anchors', 'light-estimation', 'local-floor'],
      ...(overlayRoot ? { domOverlay: { root: overlayRoot } } : {})
    });
    this.session = session;
    this.state.supportsHitTest = typeof session.requestHitTestSource === 'function';
    this.state.supportsDomOverlay = Boolean(session.domOverlayState);

    const gl = (this.canvas.getContext('webgl', { xrCompatible: true, alpha: true }) ||
      this.canvas.getContext('webgl2', { xrCompatible: true, alpha: true })) as WebGLRenderingContext | null;
    if (!gl) {
      await session.end().catch(() => undefined);
      throw new Error('No se pudo crear el contexto WebGL necesario para el modo AR.');
    }
    this.gl = gl;
    this.renderer = new XrSceneRenderer(gl);

    await session.updateRenderState({
      baseLayer: new XRWebGLLayer(session, gl, { alpha: true, antialias: true })
    });

    this.refSpace = await session.requestReferenceSpace('local-floor').catch(() =>
      session.requestReferenceSpace('viewer')
    );
    this.viewerSpace = await session.requestReferenceSpace('viewer');
    if (session.requestHitTestSource && this.viewerSpace) {
      this.hitTestSource = await session.requestHitTestSource({ space: this.viewerSpace });
    }

    session.addEventListener('select', () => this.addPointAtReticle());
    session.addEventListener('end', () => this.handleSessionEnd());

    this.setState({
      status: 'running',
      message: this.state.supportsHitTest
        ? 'Apunta al suelo y toca la pantalla para fijar cada esquina de la estancia.'
        : 'Sesión activa sin hit-test: usa el modo foto para medir.'
    });

    const loop = (time: number, frame: XRFrame): void => {
      if (!this.session) return;
      this.frameHandle = this.session.requestAnimationFrame(loop);
      this.onFrame(time, frame);
    };
    this.frameHandle = session.requestAnimationFrame(loop);
  }

  private onFrame(_time: number, frame: XRFrame): void {
    const session = this.session;
    const refSpace = this.refSpace;
    if (!session || !refSpace || !this.renderer) return;

    const pose = frame.getViewerPose(refSpace);
    const baseLayer = session.renderState.baseLayer;
    if (!pose || !baseLayer) return;

    // Hit-test: posición real del punto de impacto en metros.
    this.reticlePose = null;
    this.hitMatrix = null;
    if (this.hitTestSource) {
      const results = frame.getHitTestResults(this.hitTestSource);
      if (results.length) {
        const hitPose = results[0].getPose(refSpace);
        if (hitPose) {
          const p = hitPose.transform.position;
          this.reticlePose = vec(p.x, p.y, p.z);
          this.hitMatrix = hitPose.transform.matrix;
        }
      }
    }

    const camera = pose.transform.position;
    const primitives: RenderPrimitive[] = [];

    if (this.reticlePose && this.hitMatrix) {
      const transformed = transformPositions(this.reticleGeometry.positions, this.hitMatrix);
      primitives.push({ ...this.reticleGeometry, positions: transformed });
    }

    const ring = this.points.filter((p) => p.kind === 'floor' || p.kind === 'reference');
    ring.forEach((p) => {
      primitives.push({
        ...buildMarkerGeometry(0.045, p.kind === 'reference' ? [1, 0.55, 0.1] : [0.25, 0.75, 1]),
        positions: transformPositions(
          buildMarkerGeometry(0.045).positions,
          translationMatrix(p.position)
        )
      });
    });

    for (let i = 0; i < ring.length - 1; i += 1) {
      primitives.push(buildRibbonGeometry(ring[i].position, ring[i + 1].position, camera, 0.018));
    }
    if (ring.length > 0 && this.reticlePose) {
      primitives.push(
        buildRibbonGeometry(ring[ring.length - 1].position, this.reticlePose, camera, 0.012, [0.4, 0.9, 0.7])
      );
    }

    const ceiling = this.points.find((p) => p.kind === 'ceiling');
    if (ceiling && ring.length) {
      primitives.push(buildHeightBarGeometry(ring[0].position, Math.max(0, ceiling.position.y - ring[0].position.y)));
    }

    this.renderer.render(pose.views, baseLayer, primitives, { clearColor: [0, 0, 0, 0] });

    // Métricas en vivo (se emiten como máximo 10 veces por segundo).
    const liveDistance =
      ring.length && this.reticlePose ? distance2D(ring[ring.length - 1].position, this.reticlePose) : 0;
    const livePerimeter =
      ring.slice(0, -1).reduce((sum, p, i) => sum + distance2D(p.position, ring[i + 1].position), 0) +
      liveDistance;
    const measurement = measureRoom(
      ring.length + (this.reticlePose ? 1 : 0) >= 3
        ? [...ring.map((p) => p.position), ...(this.reticlePose ? [this.reticlePose] : [])]
        : [],
      this.estimatedHeight()
    );

    const now = performance.now();
    if (now - this.lastEmit > 100) {
      this.lastEmit = now;
      this.setState({
        reticle: this.reticlePose,
        liveDistance: Number(liveDistance.toFixed(3)),
        livePerimeter: Number(livePerimeter.toFixed(3)),
        liveArea: measurement.floorArea
      });
    }
  }

  /** Fija un punto en la posición actual del retículo. */
  addPointAtReticle(): ScanPoint | null {
    if (!this.reticlePose) return null;
    const point: ScanPoint = {
      id: uuid(),
      position: { ...this.reticlePose },
      kind: this.captureMode,
      timestamp: Date.now(),
      source: 'webxr'
    };
    this.points.push(point);
    this.setState({
      message: `Punto ${this.points.length} fijado (${this.captureMode === 'floor' ? 'suelo' : 'altura'}).`
    });
    return point;
  }

  /** Añade un punto manualmente (útil en escritorio o sin hit-test). */
  addManualPoint(position: Vec3, kind: ScanPointKind = this.captureMode): ScanPoint {
    const point: ScanPoint = {
      id: uuid(),
      position,
      kind,
      timestamp: Date.now(),
      source: 'webxr'
    };
    this.points.push(point);
    this.setState({ message: `Punto ${this.points.length} añadido manualmente.` });
    return point;
  }

  undo(): void {
    this.points.pop();
    this.setState({ message: `Quedan ${this.points.length} punto(s).` });
  }

  clear(): void {
    this.points = [];
    this.setState({ message: 'Puntos borrados. Vuelve a fijar las esquinas.' });
  }

  estimatedHeight(): number {
    const floor = this.points.filter((p) => p.kind === 'floor' || p.kind === 'reference').map((p) => p.position);
    const ceiling = this.points
      .filter((p) => p.kind === 'ceiling' || p.kind === 'wall-top')
      .map((p) => p.position);
    return estimateHeight(floor, ceiling);
  }

  /** Calcula la medición con la altura indicada (o la estimada). */
  computeMeasurement(height?: number): RoomMeasurement {
    const ring = this.points
      .filter((p) => p.kind === 'floor' || p.kind === 'reference')
      .map((p) => p.position);
    const h = height && height > 0 ? height : this.estimatedHeight() || 2.6;
    return measureRoom(ring, h);
  }

  /** Construye el objeto `RoomScan` final. */
  buildScan(name: string, height?: number, notes?: string): RoomScan | null {
    const ring = this.points
      .filter((p) => p.kind === 'floor' || p.kind === 'reference')
      .map((p) => p.position);
    if (ring.length < 3) return null;
    const measured = height && height > 0 ? height : this.estimatedHeight();
    const h = measured > 0 ? measured : 2.6;
    return {
      id: uuid(),
      name,
      createdAt: new Date().toISOString(),
      source: 'webxr',
      points: [...this.points],
      polygon: ring,
      measurement: measureRoom(ring, h),
      measuredHeight: measured > 0 ? Number(measured.toFixed(3)) : undefined,
      notes
    };
  }

  async stop(): Promise<void> {
    if (!this.session) return;
    this.setState({ status: 'stopping', message: 'Cerrando la sesión de Realidad Aumentada…' });
    if (this.frameHandle !== null) this.session.cancelAnimationFrame(this.frameHandle);
    this.frameHandle = null;
    try {
      this.hitTestSource?.cancel();
    } catch {
      /* ignore */
    }
    await this.session.end().catch(() => undefined);
  }

  private handleSessionEnd(): void {
    this.session = null;
    this.hitTestSource = null;
    this.renderer?.dispose();
    this.renderer = null;
    this.gl = null;
    this.setState({ status: 'ended', message: 'Sesión finalizada. Revisa las medidas obtenidas.' });
    const scan = this.buildScan('Estancia escaneada (AR)');
    this.callbacks.onEnded?.(scan);
  }

  private setState(patch: Partial<ScannerState>): void {
    this.state = { ...this.state, ...patch };
    this.emit(true);
  }

  private emit(force = false): void {
    if (!force && !this.callbacks.onState) return;
    this.callbacks.onState?.({ ...this.state, points: [...this.points] });
  }
}

/** Matriz de traslación 4×4 en formato columna-mayor (WebXR). */
function translationMatrix(p: Vec3): Float32Array {
  return new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, p.x, p.y, p.z, 1]);
}
