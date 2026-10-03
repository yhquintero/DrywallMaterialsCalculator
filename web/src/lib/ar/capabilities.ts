/**
 * Detección de capacidades del dispositivo para el escaneo de estancias.
 *
 * La app elige automáticamente el mejor modo disponible:
 *   1. WebXR `immersive-ar` con hit-test  → medición 3D real en metros.
 *   2. Cámara (getUserMedia / WebRTC)     → fotogrametría asistida con
 *      referencia de escala u homografía de suelo.
 *   3. Sin cámara                          → captura manual (modo clásico).
 */

export interface ArCapabilities {
  secureContext: boolean;
  hasWebXR: boolean;
  immersiveAr: boolean;
  hitTest: boolean;
  domOverlay: boolean;
  anchors: boolean;
  lightEstimation: boolean;
  hasCamera: boolean;
  hasWebGL: boolean;
  hasWebRTC: boolean;
  recommendedMode: 'webxr' | 'photo' | 'manual';
  reasons: string[];
}

const FEATURES = {
  hitTest: 'hit-test',
  domOverlay: 'dom-overlay',
  anchors: 'anchors',
  lightEstimation: 'light-estimation'
};

/** Comprueba si una sesión `immersive-ar` admite una característica concreta. */
async function supportsArFeature(feature: string): Promise<boolean> {
  if (typeof navigator === 'undefined' || !navigator.xr) return false;
  try {
    // `isSessionSupported` no informa de features: se prueba con una sesión
    // efímera que se cierra de inmediato. Es la técnica recomendada por la
    // especificación para detectar capacidades opcionales.
    const session = await navigator.xr.requestSession('immersive-ar', { optionalFeatures: [feature] });
    const granted =
      feature === 'hit-test'
        ? typeof session.requestHitTestSource === 'function'
        : feature === 'dom-overlay'
          ? Boolean(session.domOverlayState)
          : true;
    await session.end().catch(() => undefined);
    return granted;
  } catch {
    return false;
  }
}

export async function detectArCapabilities(): Promise<ArCapabilities> {
  const reasons: string[] = [];
  const secureContext =
    typeof window !== 'undefined' && (window.isSecureContext || window.location.protocol === 'https:');
  const hasWebXR = typeof navigator !== 'undefined' && Boolean(navigator.xr);
  const hasCamera =
    typeof navigator !== 'undefined' && Boolean(navigator.mediaDevices?.getUserMedia);
  const hasWebRTC = typeof window !== 'undefined' && typeof window.RTCPeerConnection === 'function';

  let hasWebGL = false;
  try {
    const canvas = document.createElement('canvas');
    hasWebGL = Boolean(canvas.getContext('webgl2') || canvas.getContext('webgl'));
  } catch {
    hasWebGL = false;
  }

  let immersiveAr = false;
  if (hasWebXR && secureContext) {
    try {
      immersiveAr = await navigator.xr!.isSessionSupported('immersive-ar');
    } catch {
      immersiveAr = false;
    }
  }

  if (!secureContext) {
    reasons.push('La cámara y WebXR requieren HTTPS (o localhost). Sirve la app con `npm run dev:https`.');
  }
  if (!hasWebXR) reasons.push('Este navegador no expone la WebXR Device API.');
  else if (!immersiveAr) reasons.push('El dispositivo no declara soporte de `immersive-ar`.');
  if (!hasCamera) reasons.push('No hay API de cámara disponible (navigator.mediaDevices).');

  let hitTest = false;
  let domOverlay = false;
  let anchors = false;
  let lightEstimation = false;

  if (immersiveAr && secureContext) {
    [hitTest, domOverlay, anchors, lightEstimation] = await Promise.all([
      supportsArFeature(FEATURES.hitTest),
      supportsArFeature(FEATURES.domOverlay),
      supportsArFeature(FEATURES.anchors),
      supportsArFeature(FEATURES.lightEstimation)
    ]);
    if (!hitTest) reasons.push('Sin hit-test: se usará el modo foto con escala de referencia.');
  }

  let recommendedMode: ArCapabilities['recommendedMode'] = 'manual';
  if (immersiveAr && hitTest && hasWebGL && secureContext) recommendedMode = 'webxr';
  else if (hasCamera && secureContext) recommendedMode = 'photo';

  return {
    secureContext,
    hasWebXR,
    immersiveAr,
    hitTest,
    domOverlay,
    anchors,
    lightEstimation,
    hasCamera,
    hasWebGL,
    hasWebRTC,
    recommendedMode,
    reasons
  };
}

/** Traduce el modo recomendado a una etiqueta legible. */
export function modeLabel(mode: ArCapabilities['recommendedMode']): string {
  switch (mode) {
    case 'webxr':
      return 'Realidad aumentada (WebXR)';
    case 'photo':
      return 'Cámara / fotogrametría asistida';
    default:
      return 'Captura manual';
  }
}
