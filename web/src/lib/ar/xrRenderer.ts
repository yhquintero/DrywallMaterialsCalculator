/**
 * Renderizador WebGL mínimo para la sesión `immersive-ar`.
 *
 * Se evita a propósito cualquier motor 3D externo: el escáner sólo necesita
 * dibujar un retículo, marcadores de punto y cintas de medición, así que con
 * un único programa de shaders y dos buffers dinámicos basta (≈ 6 KB en el
 * bundle frente a los cientos de KB de un motor completo).
 */

export type PrimitiveMode = 'LINES' | 'TRIANGLES';

export interface RenderPrimitive {
  positions: Float32Array;
  colors: Float32Array;
  mode: PrimitiveMode;
}

const VERTEX_SHADER = `
attribute vec3 aPosition;
attribute vec3 aColor;
uniform mat4 uProjection;
uniform mat4 uView;
uniform mat4 uModel;
varying vec3 vColor;
void main() {
  gl_Position = uProjection * uView * uModel * vec4(aPosition, 1.0);
  vColor = aColor;
}
`;

const FRAGMENT_SHADER = `
precision mediump float;
varying vec3 vColor;
uniform float uOpacity;
void main() {
  gl_FragColor = vec4(vColor, uOpacity);
}
`;

function compile(gl: WebGLRenderingContext, type: number, source: string): WebGLShader {
  const shader = gl.createShader(type);
  if (!shader) throw new Error('No se pudo crear el shader WebGL.');
  gl.shaderSource(shader, source);
  gl.compileShader(shader);
  if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
    const log = gl.getShaderInfoLog(shader);
    gl.deleteShader(shader);
    throw new Error(`Error compilando shader: ${log}`);
  }
  return shader;
}

export class XrSceneRenderer {
  private readonly gl: WebGLRenderingContext;
  private readonly program: WebGLProgram;
  private readonly positionBuffer: WebGLBuffer;
  private readonly colorBuffer: WebGLBuffer;
  private readonly uniforms: {
    projection: WebGLUniformLocation | null;
    view: WebGLUniformLocation | null;
    model: WebGLUniformLocation | null;
    opacity: WebGLUniformLocation | null;
  };
  private readonly attributes: { position: number; color: number };

  constructor(gl: WebGLRenderingContext) {
    this.gl = gl;
    const program = gl.createProgram();
    if (!program) throw new Error('No se pudo crear el programa WebGL.');
    const vs = compile(gl, gl.VERTEX_SHADER, VERTEX_SHADER);
    const fs = compile(gl, gl.FRAGMENT_SHADER, FRAGMENT_SHADER);
    gl.attachShader(program, vs);
    gl.attachShader(program, fs);
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      throw new Error(`Error enlazando el programa: ${gl.getProgramInfoLog(program)}`);
    }
    gl.deleteShader(vs);
    gl.deleteShader(fs);
    this.program = program;

    const positionBuffer = gl.createBuffer();
    const colorBuffer = gl.createBuffer();
    if (!positionBuffer || !colorBuffer) throw new Error('No se pudieron crear los buffers WebGL.');
    this.positionBuffer = positionBuffer;
    this.colorBuffer = colorBuffer;

    this.uniforms = {
      projection: gl.getUniformLocation(program, 'uProjection'),
      view: gl.getUniformLocation(program, 'uView'),
      model: gl.getUniformLocation(program, 'uModel'),
      opacity: gl.getUniformLocation(program, 'uOpacity')
    };
    this.attributes = {
      position: gl.getAttribLocation(program, 'aPosition'),
      color: gl.getAttribLocation(program, 'aColor')
    };

    gl.enable(gl.BLEND);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    gl.enable(gl.DEPTH_TEST);
    gl.depthFunc(gl.LEQUAL);
  }

  /** Dibuja la lista de primitivas para cada vista de la sesión XR. */
  render(
    views: XRView[],
    layer: XRWebGLLayer,
    primitives: RenderPrimitive[],
    options: { opacity?: number; clearColor?: [number, number, number, number] } = {}
  ): void {
    const gl = this.gl;
    const framebuffer = layer.framebuffer;
    gl.bindFramebuffer(gl.FRAMEBUFFER, framebuffer);
    const [r, g, b, a] = options.clearColor ?? [0, 0, 0, 0];
    gl.clearColor(r, g, b, a);
    gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);

    if (!primitives.length) return;

    gl.useProgram(this.program);
    const model = new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]);
    gl.uniformMatrix4fv(this.uniforms.model, false, model);
    gl.uniform1f(this.uniforms.opacity, options.opacity ?? 1);

    views.forEach((view) => {
      const viewport = layer.getViewport(view);
      if (!viewport) return;
      gl.viewport(viewport.x, viewport.y, viewport.width, viewport.height);
      gl.uniformMatrix4fv(this.uniforms.projection, false, view.projectionMatrix);
      gl.uniformMatrix4fv(this.uniforms.view, false, view.transform.inverse.matrix);

      primitives.forEach((primitive) => {
        if (!primitive.positions.length) return;
        gl.bindBuffer(gl.ARRAY_BUFFER, this.positionBuffer);
        gl.bufferData(gl.ARRAY_BUFFER, primitive.positions, gl.DYNAMIC_DRAW);
        gl.enableVertexAttribArray(this.attributes.position);
        gl.vertexAttribPointer(this.attributes.position, 3, gl.FLOAT, false, 0, 0);

        gl.bindBuffer(gl.ARRAY_BUFFER, this.colorBuffer);
        gl.bufferData(gl.ARRAY_BUFFER, primitive.colors, gl.DYNAMIC_DRAW);
        gl.enableVertexAttribArray(this.attributes.color);
        gl.vertexAttribPointer(this.attributes.color, 3, gl.FLOAT, false, 0, 0);

        gl.drawArrays(primitive.mode === 'LINES' ? gl.LINES : gl.TRIANGLES, 0, primitive.positions.length / 3);
      });
    });
  }

  dispose(): void {
    const gl = this.gl;
    gl.deleteBuffer(this.positionBuffer);
    gl.deleteBuffer(this.colorBuffer);
    gl.deleteProgram(this.program);
  }
}

// ── Constructores de geometría ───────────────────────────────────────────────

/** Transforma vértices con una matriz 4×4 columna-mayor (formato WebXR). */
export function transformPositions(positions: Float32Array, matrix: Float32Array): Float32Array {
  const out = new Float32Array(positions.length);
  for (let i = 0; i < positions.length; i += 3) {
    const x = positions[i];
    const y = positions[i + 1];
    const z = positions[i + 2];
    out[i] = matrix[0] * x + matrix[4] * y + matrix[8] * z + matrix[12];
    out[i + 1] = matrix[1] * x + matrix[5] * y + matrix[9] * z + matrix[13];
    out[i + 2] = matrix[2] * x + matrix[6] * y + matrix[10] * z + matrix[14];
  }
  return out;
}

/** Retículo circular en el plano XZ + cruz central. */
export function buildReticleGeometry(radius = 0.08, segments = 40): RenderPrimitive {
  const positions: number[] = [];
  const colors: number[] = [];
  const push = (x: number, y: number, z: number, c: [number, number, number]): void => {
    positions.push(x, y, z);
    colors.push(c[0], c[1], c[2]);
  };
  const green: [number, number, number] = [0.22, 0.95, 0.55];
  for (let i = 0; i < segments; i += 1) {
    const a0 = (i / segments) * Math.PI * 2;
    const a1 = ((i + 1) / segments) * Math.PI * 2;
    push(Math.cos(a0) * radius, 0, Math.sin(a0) * radius, green);
    push(Math.cos(a1) * radius, 0, Math.sin(a1) * radius, green);
  }
  const inner = radius * 0.35;
  push(-inner, 0, 0, green);
  push(inner, 0, 0, green);
  push(0, 0, -inner, green);
  push(0, 0, inner, green);
  return { positions: new Float32Array(positions), colors: new Float32Array(colors), mode: 'LINES' };
}

/** Marcador en forma de cruz 3D para un punto capturado. */
export function buildMarkerGeometry(
  size = 0.05,
  color: [number, number, number] = [0.25, 0.75, 1]
): RenderPrimitive {
  const positions: number[] = [];
  const colors: number[] = [];
  const push = (x: number, y: number, z: number): void => {
    positions.push(x, y, z);
    colors.push(color[0], color[1], color[2]);
  };
  push(-size, 0, 0);
  push(size, 0, 0);
  push(0, -size, 0);
  push(0, size, 0);
  push(0, 0, -size);
  push(0, 0, size);
  return { positions: new Float32Array(positions), colors: new Float32Array(colors), mode: 'LINES' };
}

/**
 * Cinta (ribbon) entre dos puntos, orientada hacia la cámara, para que la
 * línea de medición tenga grosor visible en AR.
 */
export function buildRibbonGeometry(
  a: { x: number; y: number; z: number },
  b: { x: number; y: number; z: number },
  camera: { x: number; y: number; z: number },
  width = 0.02,
  color: [number, number, number] = [0.98, 0.75, 0.14]
): RenderPrimitive {
  const ax = { x: b.x - a.x, y: b.y - a.y, z: b.z - a.z };
  const len = Math.hypot(ax.x, ax.y, ax.z);
  if (len < 1e-6) return { positions: new Float32Array(0), colors: new Float32Array(0), mode: 'TRIANGLES' };
  const dir = { x: ax.x / len, y: ax.y / len, z: ax.z / len };
  const toCam = { x: camera.x - (a.x + b.x) / 2, y: camera.y - (a.y + b.y) / 2, z: camera.z - (a.z + b.z) / 2 };
  // side = normalize(dir × toCam)
  const cx = dir.y * toCam.z - dir.z * toCam.y;
  const cy = dir.z * toCam.x - dir.x * toCam.z;
  const cz = dir.x * toCam.y - dir.y * toCam.x;
  const cl = Math.hypot(cx, cy, cz) || 1;
  const half = width / 2;
  const sx = (cx / cl) * half;
  const sy = (cy / cl) * half;
  const sz = (cz / cl) * half;

  const p0 = [a.x - sx, a.y - sy, a.z - sz];
  const p1 = [a.x + sx, a.y + sy, a.z + sz];
  const p2 = [b.x + sx, b.y + sy, b.z + sz];
  const p3 = [b.x - sx, b.y - sy, b.z - sz];

  const positions = new Float32Array([...p0, ...p1, ...p2, ...p0, ...p2, ...p3]);
  const colors = new Float32Array(positions.length);
  for (let i = 0; i < colors.length; i += 3) {
    colors[i] = color[0];
    colors[i + 1] = color[1];
    colors[i + 2] = color[2];
  }
  return { positions, colors, mode: 'TRIANGLES' };
}

/** Línea vertical discontinua (referencia de altura). */
export function buildHeightBarGeometry(
  base: { x: number; y: number; z: number },
  height: number,
  color: [number, number, number] = [0.55, 0.9, 1]
): RenderPrimitive {
  const positions: number[] = [];
  const colors: number[] = [];
  const steps = 12;
  for (let i = 0; i < steps; i += 1) {
    if (i % 2 === 1) continue;
    const y0 = (i / steps) * height;
    const y1 = ((i + 1) / steps) * height;
    positions.push(base.x, base.y + y0, base.z, base.x, base.y + y1, base.z);
    colors.push(color[0], color[1], color[2], color[0], color[1], color[2]);
  }
  return { positions: new Float32Array(positions), colors: new Float32Array(colors), mode: 'LINES' };
}
