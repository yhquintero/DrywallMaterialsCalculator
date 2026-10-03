/** Errores de dominio con código HTTP asociado. */
export class HttpError extends Error {
  constructor(status, message, details = undefined) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.details = details;
  }
}

export const badRequest = (msg, details) => new HttpError(400, msg, details);
export const unauthorized = (msg = 'No autenticado') => new HttpError(401, msg);
export const forbidden = (msg = 'No tienes permisos para esta operación') => new HttpError(403, msg);
export const notFound = (msg = 'Recurso no encontrado') => new HttpError(404, msg);
export const conflict = (msg) => new HttpError(409, msg);
export const tooMany = (msg = 'Demasiadas solicitudes') => new HttpError(429, msg);

/** Wrapper para handlers async que delega errores a Express. */
export const asyncHandler = (fn) => (req, res, next) =>
  Promise.resolve(fn(req, res, next)).catch(next);
