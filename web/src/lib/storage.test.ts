import { beforeEach, describe, expect, it, vi } from 'vitest';
import {
  DEFAULT_CONFIG,
  DEFAULT_ROOMS,
  SavedProject,
  deleteProjectFromStorage,
  getActiveProjectId,
  getSavedProjects,
  loadProjectsWithIntegrity,
  saveProjectToStorage,
  verifyProjectChecksum
} from './storage';

/** LocalStorage en memoria para el entorno Node de Vitest. */
class MemoryStorage {
  private store = new Map<string, string>();
  get length(): number {
    return this.store.size;
  }
  getItem(key: string): string | null {
    return this.store.has(key) ? (this.store.get(key) as string) : null;
  }
  setItem(key: string, value: string): void {
    this.store.set(key, String(value));
  }
  removeItem(key: string): void {
    this.store.delete(key);
  }
  clear(): void {
    this.store.clear();
  }
  key(index: number): string | null {
    return Array.from(this.store.keys())[index] ?? null;
  }
}

const buildProject = (id: string, name: string): SavedProject => ({
  id,
  updatedAt: new Date().toISOString(),
  config: { ...DEFAULT_CONFIG, projectName: name },
  rooms: DEFAULT_ROOMS,
  customPrices: {},
  customStock: {}
});

describe('Persistencia de proyectos (LocalStorage + SHA-256)', () => {
  beforeEach(() => {
    vi.stubGlobal('localStorage', new MemoryStorage());
  });

  it('guarda un proyecto con checksum y lo verifica como íntegro', async () => {
    const result = await saveProjectToStorage(buildProject('p1', 'Obra Uno'));
    expect(result.ok).toBe(true);

    const [stored] = getSavedProjects();
    expect(stored.config.projectName).toBe('Obra Uno');
    expect(stored.checksum).toBeTruthy();
    expect(getActiveProjectId()).toBe('p1');

    const integrity = await verifyProjectChecksum(stored);
    expect(integrity.status).toBe('verified');
  });

  it('detecta manipulación externa del contenido (checksum divergente)', async () => {
    await saveProjectToStorage(buildProject('p2', 'Obra Dos'));

    // Se altera el contenido directamente en el almacenamiento, sin recalcular
    // el checksum (simula una edición manual del JSON).
    const raw = JSON.parse(localStorage.getItem('drywall_calculator_projects_v2')!);
    raw[0].config.profitPercentage = 999;
    localStorage.setItem('drywall_calculator_projects_v2', JSON.stringify(raw));

    const integrity = await verifyProjectChecksum(getSavedProjects()[0]);
    expect(integrity.status).toBe('mismatch');
    expect(integrity.expected).not.toBe(integrity.current);

    const [annotated] = await loadProjectsWithIntegrity();
    expect(annotated.integrity.status).toBe('mismatch');
  });

  it('marca como no verificable un proyecto sin checksum (formato antiguo)', async () => {
    localStorage.setItem(
      'drywall_calculator_projects_v2',
      JSON.stringify([{ ...buildProject('p3', 'Legacy'), checksum: undefined }])
    );
    const [annotated] = await loadProjectsWithIntegrity();
    expect(annotated.integrity.status).toBe('unverified');
    expect(annotated.config.projectName).toBe('Legacy');
  });

  it('descarta entradas corruptas y JSON inválido sin lanzar excepciones', () => {
    localStorage.setItem('drywall_calculator_projects_v2', '{esto no es json]');
    expect(getSavedProjects()).toEqual([]);

    localStorage.setItem(
      'drywall_calculator_projects_v2',
      JSON.stringify([{ id: 'sin-config' }, { ...buildProject('ok', 'Válido') }])
    );
    const projects = getSavedProjects();
    expect(projects).toHaveLength(1);
    expect(projects[0].id).toBe('ok');
  });

  it('elimina un proyecto y repara el puntero de proyecto activo', async () => {
    await saveProjectToStorage(buildProject('a', 'Obra A'));
    await saveProjectToStorage(buildProject('b', 'Obra B'));
    expect(getActiveProjectId()).toBe('b');

    const remaining = deleteProjectFromStorage('b');
    expect(remaining.map((p) => p.id)).toEqual(['a']);
    expect(getSavedProjects().map((p) => p.id)).toEqual(['a']);
    // El puntero no puede quedar apuntando a un proyecto inexistente.
    expect(getActiveProjectId()).toBe('a');

    deleteProjectFromStorage('a');
    expect(getActiveProjectId()).toBeNull();
  });

  it('recupera el guardado cuando la cuota de almacenamiento está llena', async () => {
    const storage = localStorage as unknown as MemoryStorage & {
      setItem: (key: string, value: string) => void;
    };
    const original = storage.setItem.bind(storage);
    let failures = 1;

    const spy = vi.spyOn(storage, 'setItem').mockImplementation((key: string, value: string) => {
      if (failures > 0) {
        failures -= 1;
        const error = new Error('QuotaExceededError');
        error.name = 'QuotaExceededError';
        throw error;
      }
      return original(key, value);
    });

    const result = await saveProjectToStorage(buildProject('q1', 'Cuota'));
    expect(result.ok).toBe(true);
    expect(failures).toBe(0); // el segundo intento (1 solo proyecto) sí escribió
    spy.mockRestore();

    expect(getSavedProjects().map((p) => p.id)).toEqual(['q1']);
  });
});
