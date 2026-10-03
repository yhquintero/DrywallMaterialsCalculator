/**
 * Tipados mínimos de la WebXR Device API (subconjunto usado por el escáner).
 *
 * El proyecto no incorpora `@types/webxr` completo para no inflar el bundle de
 * tipos; aquí se declara únicamente la superficie que consume el módulo AR.
 */

type XRSessionMode = 'inline' | 'immersive-vr' | 'immersive-ar';

interface XRRigidTransform {
  readonly matrix: Float32Array;
  readonly inverse: XRRigidTransform;
  readonly position: DOMPointReadOnly;
  readonly orientation: DOMPointReadOnly;
}

interface XRPose {
  readonly transform: XRRigidTransform;
  readonly emulatedPosition: boolean;
}

interface XRView {
  readonly eye: 'none' | 'left' | 'right';
  readonly projectionMatrix: Float32Array;
  readonly transform: XRRigidTransform;
  readonly recommendedViewportScale?: number;
}

interface XRViewerPose extends XRPose {
  readonly views: XRView[];
}

interface XRSpace {
  // Marcador de tipo.
}

interface XRReferenceSpace extends XRSpace {
  getOffsetReferenceSpace(originOffset: XRRigidTransform): XRReferenceSpace;
  addEventListener(type: string, listener: (event: Event) => void): void;
  removeEventListener(type: string, listener: (event: Event) => void): void;
}

interface XRHitTestSource {
  cancel(): void;
}

interface XRHitTestResult {
  getPose(baseSpace: XRSpace): XRPose | undefined;
}

interface XRTransientInputHitTestResult {
  readonly inputSource: XRInputSource;
  readonly results: XRHitTestResult[];
}

interface XRInputSource {
  readonly targetRayMode: 'gaze' | 'tracked-pointer' | 'screen';
  readonly targetRaySpace: XRSpace;
  readonly handedness: 'none' | 'left' | 'right';
  readonly gamepad?: Gamepad;
}

interface XRWebGLLayerInit {
  antialias?: boolean;
  depth?: boolean;
  stencil?: boolean;
  alpha?: boolean;
  ignoreDepthValues?: boolean;
  framebufferScaleFactor?: number;
}

interface XRViewport {
  readonly x: number;
  readonly y: number;
  readonly width: number;
  readonly height: number;
}

declare class XRWebGLLayer {
  constructor(session: XRSession, context: WebGLRenderingContext | WebGL2RenderingContext, options?: XRWebGLLayerInit);
  readonly framebuffer: WebGLFramebuffer | null;
  readonly framebufferWidth: number;
  readonly framebufferHeight: number;
  readonly antialias: boolean;
  getViewport(view: XRView): XRViewport | undefined;
}

interface XRRenderState {
  readonly depthNear: number;
  readonly depthFar: number;
  readonly baseLayer?: XRWebGLLayer;
}

interface XRSessionInit {
  requiredFeatures?: string[];
  optionalFeatures?: string[];
  domOverlay?: { root: Element; suppressSelectEvents?: boolean };
}

interface XRFrame {
  readonly session: XRSession;
  getViewerPose(referenceSpace: XRReferenceSpace): XRViewerPose | undefined;
  getPose(space: XRSpace, baseSpace: XRSpace): XRPose | undefined;
  getHitTestResults(hitTestSource: XRHitTestSource): XRHitTestResult[];
  getHitTestResultsForTransientInput?(source: unknown): XRTransientInputHitTestResult[];
}

interface XRSession {
  readonly renderState: XRRenderState;
  readonly visibilityState: 'visible' | 'visible-blurred' | 'hidden';
  readonly inputSources: XRInputSource[];
  readonly domOverlayState?: { type: string };
  updateRenderState(state?: Partial<XRRenderState>): Promise<void>;
  requestReferenceSpace(type: 'viewer' | 'local' | 'local-floor' | 'bounded-floor' | 'unbounded'): Promise<XRReferenceSpace>;
  requestHitTestSource?(options: { space: XRSpace; entityTypes?: string[]; offsetRay?: unknown }): Promise<XRHitTestSource>;
  requestAnimationFrame(callback: (time: number, frame: XRFrame) => void): number;
  cancelAnimationFrame(handle: number): void;
  end(): Promise<void>;
  addEventListener(type: string, listener: (event: Event) => void): void;
  removeEventListener(type: string, listener: (event: Event) => void): void;
}

interface XRSystem {
  isSessionSupported(mode: XRSessionMode): Promise<boolean>;
  requestSession(mode: XRSessionMode, options?: XRSessionInit): Promise<XRSession>;
  addEventListener(type: 'devicechange', listener: (event: Event) => void): void;
}

interface Navigator {
  xr?: XRSystem;
}

interface XRSessionEvent extends Event {
  readonly session: XRSession;
}
